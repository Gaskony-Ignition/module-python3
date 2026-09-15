package com.gaskony.python3.gateway;

import com.gaskony.python3.PoolConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Manages a pool of Python 3 processes for efficient execution.
 * Processes are kept alive and reused across multiple script executions.
 *
 * <p>v4.1.0: dropped the {@code ResourceLimits}/{@code InputValidator}/{@code EnhancedAuditLogger}/
 * {@code RateLimiter} injection. Input validation never ran on the live path and the
 * {@code InputValidator} sandbox was removed (it blocked legitimate code; OS isolation + the
 * Java-side role gate are the real boundary). Audit logging happens at the REST/scripting entry
 * points via {@code Python3AuditLogger}; per-IP rate limiting is enforced in the REST layer.
 */
public class Python3ProcessPool {

    private static final Logger logger = LoggerFactory.getLogger(Python3ProcessPool.class);

    private final String pythonPath;
    private volatile int poolSize;  // Changed to volatile for dynamic resizing (v1.17.2)
    private final BlockingQueue<Python3Executor> availableExecutors;
    private final CopyOnWriteArrayList<Python3Executor> allExecutors;
    private final ScheduledExecutorService healthCheckExecutor;
    private volatile boolean isShutdown = false;
    private final AtomicInteger executorIdCounter = new AtomicInteger(0);

    // Monitoring components (v2.14.0 - Phase 2 Week 3-4)
    private MetricsCollector metricsCollector;
    private CircuitBreaker circuitBreaker;
    private AlertManager alertManager;

    /**
     * Create a new process pool
     *
     * @param pythonPath Path to Python 3 executable
     * @param poolSize   Number of processes to maintain
     * @throws IOException if processes cannot be started
     */
    public Python3ProcessPool(String pythonPath, int poolSize) throws IOException {
        this.pythonPath = pythonPath;
        this.poolSize = poolSize;
        this.availableExecutors = new LinkedBlockingQueue<>(poolSize);
        this.allExecutors = new CopyOnWriteArrayList<>();

        // Initialize monitoring components (v2.14.0 Phase 2 Week 3-4)
        this.metricsCollector = new MetricsCollector();
        this.circuitBreaker = new CircuitBreaker();
        this.alertManager = new AlertManager();

        logger.info("Initializing Python 3 process pool with {} processes", poolSize);
        logger.info("Monitoring: MetricsCollector, CircuitBreaker, AlertManager initialized");

        // Create initial pool
        for (int i = 0; i < poolSize; i++) {
            Python3Executor executor = createExecutor();
            allExecutors.add(executor);
            if (!availableExecutors.offer(executor)) {
                logger.warn("Failed to add executor to available queue during pool initialization");
            }
        }

        // Start health check scheduler
        healthCheckExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Python3-HealthCheck");
            t.setDaemon(true);
            return t;
        });

        healthCheckExecutor.scheduleAtFixedRate(
                this::performHealthCheck,
                PoolConfig.HEALTH_CHECK_INITIAL_DELAY_SECONDS,
                PoolConfig.HEALTH_CHECK_INTERVAL_SECONDS,
                TimeUnit.SECONDS
        );

        logger.info("Python 3 process pool initialized successfully");
    }

    /**
     * Create a new executor instance
     */
    private Python3Executor createExecutor() throws IOException {
        int id = executorIdCounter.incrementAndGet();
        logger.debug("Creating Python executor #{}", id);

        try {
            Python3Executor executor = new Python3Executor(pythonPath);
            logger.info("Python executor #{} created successfully", id);
            return executor;
        } catch (IOException e) {
            logger.error("Failed to create Python executor #{}", id, e);
            throw e;
        }
    }

    /**
     * Borrow an executor from the pool
     *
     * @param timeout  Maximum time to wait
     * @param timeUnit Time unit
     * @return An available executor
     * @throws InterruptedException if interrupted while waiting
     * @throws TimeoutException     if no executor becomes available in time
     * @throws IllegalStateException if pool is shutdown
     */
    public Python3Executor borrowExecutor(long timeout, TimeUnit timeUnit)
            throws InterruptedException, TimeoutException {

        if (isShutdown) {
            throw new IllegalStateException("Process pool is shutdown");
        }

        // Check circuit breaker before borrowing (v2.14.0)
        if (!circuitBreaker.allowRequest()) {
            metricsCollector.recordTimeoutWait();
            throw new IllegalStateException("Circuit breaker is OPEN - too many recent failures");
        }

        long startWait = System.currentTimeMillis();
        Python3Executor executor = availableExecutors.poll(timeout, timeUnit);
        long waitTime = System.currentTimeMillis() - startWait;

        // Record metrics (v2.14.0)
        metricsCollector.recordBorrow();
        if (waitTime > 1000) { // If waited more than 1 second
            metricsCollector.recordQueueWait(waitTime);
        }

        if (executor == null) {
            metricsCollector.recordTimeoutWait();
            // Alert on pool exhaustion
            alertManager.alertPoolExhaustion(poolSize, availableExecutors.size());
            throw new TimeoutException("No Python executor available within " + timeout + " " + timeUnit);
        }

        // Double-check executor is healthy
        if (!executor.isHealthy()) {
            logger.warn("Borrowed executor is unhealthy, attempting to replace");
            try {
                replaceExecutor(executor);
                // Try to borrow again (non-blocking)
                executor = availableExecutors.poll();
                if (executor == null) {
                    metricsCollector.recordTimeoutWait();
                    throw new TimeoutException("No healthy executor available");
                }
            } catch (IOException e) {
                logger.error("Failed to replace unhealthy executor", e);
                alertManager.alertExecutorCrash("Failed to replace unhealthy executor: " + e.getMessage());
                throw new TimeoutException("No healthy executor available");
            }
        }

        logger.debug("Executor borrowed, {} available", availableExecutors.size());
        return executor;
    }

    /**
     * Return an executor to the pool
     *
     * @param executor The executor to return
     */
    public void returnExecutor(Python3Executor executor) {
        if (executor == null || isShutdown) {
            return;
        }

        // Record metrics (v2.14.0)
        metricsCollector.recordReturn();

        // Check if executor is still healthy
        if (!executor.isHealthy()) {
            logger.warn("Returned executor is unhealthy, will be replaced");
            try {
                replaceExecutor(executor);
            } catch (IOException e) {
                logger.error("Failed to replace unhealthy executor", e);
                alertManager.alertExecutorCrash("Failed to replace unhealthy executor: " + e.getMessage());
            }
        } else {
            if (!availableExecutors.offer(executor)) {
                logger.warn("Failed to return executor to available queue (queue full)");
            }
            logger.debug("Executor returned, {} available", availableExecutors.size());
        }
    }

    /**
     * Execute code using a pooled executor
     *
     * @param code      Python code to execute
     * @param variables Variables to pass
     * @return Result
     * @throws Python3Exception if execution fails
     */
    public Python3Result execute(String code, java.util.Map<String, Object> variables) throws Python3Exception {
        // C13: legacy "RESTRICTED" wire-value retained for protocol back-compat;
        // python_bridge.py ignores security_mode and the Java-side role check
        // (RoleResolver.requireAdministrator) is the actual gate.
        return execute(code, variables, "RESTRICTED");
    }

    /**
     * Execute code using a pooled executor with security mode
     *
     * @param code         Python code to execute
     * @param variables    Variables to pass
     * @param securityMode Security mode: "RESTRICTED" or "ADMIN"
     * @return Result
     * @throws Python3Exception if execution fails
     */
    public Python3Result execute(String code, java.util.Map<String, Object> variables, String securityMode) throws Python3Exception {
        Python3Executor executor = null;
        long startTime = System.currentTimeMillis();
        try {
            executor = borrowExecutor(PoolConfig.BORROW_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            Python3Result result = executor.execute(code, variables, securityMode);

            // Record successful execution metrics (v2.14.0 Phase 2 Week 3-4)
            long responseTime = System.currentTimeMillis() - startTime;
            metricsCollector.recordExecution(true, responseTime);
            circuitBreaker.recordSuccess();

            // Check for slow execution warnings
            if (responseTime > 5000) {
                logger.warn("Slow execution detected: {}ms", responseTime);
            }

            return result;
        } catch (InterruptedException | TimeoutException e) {
            // Record failure metrics (v2.14.0 Phase 2 Week 3-4)
            long responseTime = System.currentTimeMillis() - startTime;
            metricsCollector.recordExecution(false, responseTime);
            circuitBreaker.recordFailure();
            throw new Python3Exception("Failed to acquire executor: " + e.getMessage(), e);
        } catch (Python3Exception e) {
            // Record failure metrics (v2.14.0 Phase 2 Week 3-4)
            long responseTime = System.currentTimeMillis() - startTime;
            metricsCollector.recordExecution(false, responseTime);
            circuitBreaker.recordFailure();

            // Alert on circuit breaker state change
            if (circuitBreaker.getState() == CircuitBreaker.State.OPEN) {
                alertManager.alertCircuitBreakerOpened(
                    circuitBreaker.getFailureCount(),
                    60 // 60 second window (from CircuitBreaker default)
                );
            }
            throw e;
        } finally {
            if (executor != null) {
                returnExecutor(executor);
            }
        }
    }

    /**
     * Evaluate expression using a pooled executor
     */
    public Python3Result evaluate(String expression, java.util.Map<String, Object> variables) throws Python3Exception {
        return evaluate(expression, variables, "RESTRICTED");
    }

    /**
     * Evaluate expression using a pooled executor with security mode
     *
     * @param expression   Python expression to evaluate
     * @param variables    Variables to pass
     * @param securityMode Security mode: "RESTRICTED" or "ADMIN"
     * @return Result
     * @throws Python3Exception if evaluation fails
     */
    public Python3Result evaluate(String expression, java.util.Map<String, Object> variables, String securityMode) throws Python3Exception {
        Python3Executor executor = null;
        long startTime = System.currentTimeMillis();
        try {
            executor = borrowExecutor(PoolConfig.BORROW_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            Python3Result result = executor.evaluate(expression, variables, securityMode);

            // Record successful execution metrics (v2.14.0 Phase 2 Week 3-4)
            long responseTime = System.currentTimeMillis() - startTime;
            metricsCollector.recordExecution(true, responseTime);
            circuitBreaker.recordSuccess();

            return result;
        } catch (InterruptedException | TimeoutException e) {
            // Record failure metrics (v2.14.0 Phase 2 Week 3-4)
            long responseTime = System.currentTimeMillis() - startTime;
            metricsCollector.recordExecution(false, responseTime);
            circuitBreaker.recordFailure();
            throw new Python3Exception("Failed to acquire executor: " + e.getMessage(), e);
        } catch (Python3Exception e) {
            // Record failure metrics (v2.14.0 Phase 2 Week 3-4)
            long responseTime = System.currentTimeMillis() - startTime;
            metricsCollector.recordExecution(false, responseTime);
            circuitBreaker.recordFailure();

            // Alert on circuit breaker state change
            if (circuitBreaker.getState() == CircuitBreaker.State.OPEN) {
                alertManager.alertCircuitBreakerOpened(
                    circuitBreaker.getFailureCount(),
                    60 // 60 second window (from CircuitBreaker default)
                );
            }
            throw e;
        } finally {
            if (executor != null) {
                returnExecutor(executor);
            }
        }
    }

    /**
     * Call module function using a pooled executor
     */
    public Python3Result callModule(String moduleName, String functionName,
                                     java.util.List<Object> args,
                                     java.util.Map<String, Object> kwargs) throws Python3Exception {
        return callModule(moduleName, functionName, args, kwargs, "RESTRICTED");
    }

    /**
     * Call module function using a pooled executor with security mode
     *
     * @param moduleName   Module name
     * @param functionName Function name
     * @param args         Arguments
     * @param kwargs       Keyword arguments
     * @param securityMode Security mode: "RESTRICTED" or "ADMIN"
     * @return Result
     * @throws Python3Exception if call fails
     */
    public Python3Result callModule(String moduleName, String functionName,
                                     java.util.List<Object> args,
                                     java.util.Map<String, Object> kwargs,
                                     String securityMode) throws Python3Exception {
        Python3Executor executor = null;
        long startTime = System.currentTimeMillis();
        try {
            executor = borrowExecutor(PoolConfig.BORROW_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            Python3Result result = executor.callModule(moduleName, functionName, args, kwargs, securityMode);

            // Record successful execution metrics (v2.14.0 Phase 2 Week 3-4)
            long responseTime = System.currentTimeMillis() - startTime;
            metricsCollector.recordExecution(true, responseTime);
            circuitBreaker.recordSuccess();

            return result;
        } catch (InterruptedException | TimeoutException e) {
            // Record failure metrics (v2.14.0 Phase 2 Week 3-4)
            long responseTime = System.currentTimeMillis() - startTime;
            metricsCollector.recordExecution(false, responseTime);
            circuitBreaker.recordFailure();
            throw new Python3Exception("Failed to acquire executor: " + e.getMessage(), e);
        } catch (Python3Exception e) {
            // Record failure metrics (v2.14.0 Phase 2 Week 3-4)
            long responseTime = System.currentTimeMillis() - startTime;
            metricsCollector.recordExecution(false, responseTime);
            circuitBreaker.recordFailure();

            // Alert on circuit breaker state change
            if (circuitBreaker.getState() == CircuitBreaker.State.OPEN) {
                alertManager.alertCircuitBreakerOpened(
                    circuitBreaker.getFailureCount(),
                    60 // 60 second window (from CircuitBreaker default)
                );
            }
            throw e;
        } finally {
            if (executor != null) {
                returnExecutor(executor);
            }
        }
    }

    /**
     * Check Python code syntax using a pooled executor
     *
     * @param code Python code to check
     * @return Result containing list of syntax errors
     * @throws Python3Exception if syntax check fails
     */
    public Python3Result checkSyntax(String code) throws Python3Exception {
        Python3Executor executor = null;
        try {
            executor = borrowExecutor(PoolConfig.BORROW_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return executor.checkSyntax(code);
        } catch (InterruptedException | TimeoutException e) {
            throw new Python3Exception("Failed to acquire executor: " + e.getMessage(), e);
        } finally {
            if (executor != null) {
                returnExecutor(executor);
            }
        }
    }

    /**
     * Get code completions using a pooled executor
     *
     * @param code   Python code
     * @param line   Line number (1-based)
     * @param column Column number (0-based)
     * @return Result containing list of completions
     * @throws Python3Exception if completions request fails
     */
    public Python3Result getCompletions(String code, int line, int column) throws Python3Exception {
        Python3Executor executor = null;
        try {
            executor = borrowExecutor(PoolConfig.BORROW_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return executor.getCompletions(code, line, column);
        } catch (InterruptedException | TimeoutException e) {
            throw new Python3Exception("Failed to acquire executor: " + e.getMessage(), e);
        } finally {
            if (executor != null) {
                returnExecutor(executor);
            }
        }
    }

    /**
     * Replace an unhealthy executor with a new one
     */
    private synchronized void replaceExecutor(Python3Executor oldExecutor) throws IOException {
        logger.info("Replacing unhealthy executor");

        // Shutdown old executor
        try {
            oldExecutor.shutdown();
        } catch (Exception e) {
            logger.error("Error shutting down old executor", e);
        }

        // Remove from all executors list
        allExecutors.remove(oldExecutor);

        // Create new executor
        Python3Executor newExecutor = createExecutor();
        allExecutors.add(newExecutor);
        if (!availableExecutors.offer(newExecutor)) {
            logger.warn("Failed to add replacement executor to available queue");
        }

        logger.info("Executor replaced successfully");
    }

    /**
     * Perform health check on all executors.
     * v2.15.9: Enhanced with runtime resource monitoring.
     */
    private void performHealthCheck() {
        if (isShutdown) {
            return;
        }

        logger.debug("Performing health check on {} executors", allExecutors.size());

        // v2.15.9: Runtime resource monitoring
        monitorRuntimeResources();

        for (Python3Executor executor : allExecutors) {
            if (!executor.isHealthy()) {
                logger.warn("Executor failed health check, attempting to replace");
                try {
                    replaceExecutor(executor);
                } catch (IOException e) {
                    logger.error("Failed to replace unhealthy executor during health check", e);
                }
            }
        }
    }

    /**
     * Monitor runtime resources and log warnings if approaching limits.
     * v2.15.9: Added for production visibility and alerting.
     */
    private void monitorRuntimeResources() {
        try {
            // Get current pool statistics
            int totalSize = poolSize;
            int availableCount = availableExecutors.size();
            int inUseCount = totalSize - availableCount;
            long healthyCount = allExecutors.stream().filter(Python3Executor::isHealthy).count();

            // Calculate utilization percentages
            double utilizationPercent = (inUseCount * 100.0) / totalSize;
            double healthPercent = (healthyCount * 100.0) / totalSize;

            // Log resource usage periodically (every 5th health check = ~2.5 minutes)
            if (System.currentTimeMillis() % (5 * 30000) < 30000) {
                logger.info("Process pool resource usage: {}% utilized ({}/{}), {}% healthy ({}/{})",
                    String.format("%.1f", utilizationPercent), inUseCount, totalSize,
                    String.format("%.1f", healthPercent), healthyCount, totalSize);
            }

            // Warn if pool utilization is high (>80%)
            if (utilizationPercent > 80.0) {
                logger.warn("HIGH UTILIZATION: Process pool is {}% utilized ({}/{} executors in use). " +
                    "Consider increasing pool size if this persists.",
                    String.format("%.1f", utilizationPercent), inUseCount, totalSize);

                if (alertManager != null) {
                    alertManager.alertPoolExhaustion(totalSize, availableCount);
                }
            }

            // Warn if health is degraded (<90%)
            if (healthPercent < 90.0 && healthyCount < totalSize) {
                logger.warn("DEGRADED HEALTH: Only {}% of executors are healthy ({}/{} healthy). " +
                    "Unhealthy executors will be replaced.",
                    String.format("%.1f", healthPercent), healthyCount, totalSize);

                if (alertManager != null) {
                    alertManager.alertExecutorHealthDegraded((int) healthPercent,
                        String.format("%d/%d executors healthy", healthyCount, totalSize));
                }
            }

            // Critical: All executors unhealthy
            if (healthyCount == 0) {
                logger.error("CRITICAL: All executors are unhealthy! Python execution may fail.");
                if (alertManager != null) {
                    alertManager.alertExecutorCrash("All executors unhealthy - pool non-functional");
                }
            }

        } catch (Exception e) {
            // Don't let monitoring failures crash health checks
            logger.debug("Error during runtime resource monitoring: {}", e.getMessage());
        }
    }

    /**
     * Get pool statistics
     */
    public PoolStats getStats() {
        return new PoolStats(
                poolSize,
                availableExecutors.size(),
                poolSize - availableExecutors.size(),
                (int) allExecutors.stream().filter(Python3Executor::isHealthy).count()
        );
    }

    /**
     * Get all subprocess PIDs for monitoring.
     * v2.15.5: Required for Python3-specific memory/CPU tracking
     *
     * @return list of subprocess PIDs (empty if pool is shutdown or no processes)
     */
    public List<Long> getSubprocessPids() {
        if (isShutdown) {
            return Collections.emptyList();
        }

        return allExecutors.stream()
                .filter(Python3Executor::isHealthy)
                .map(Python3Executor::getProcessPid)
                .filter(pid -> pid > 0)
                .collect(Collectors.toList());
    }

    /**
     * Resize the process pool to a new size (1-20).
     * If increasing, new executors are created.
     * If decreasing, excess executors are gracefully shut down.
     *
     * @param newSize the new pool size (1-20)
     * @throws IllegalArgumentException if newSize is out of range
     * @throws IllegalStateException if pool is already shutdown
     *
     * v1.17.2: Added for dynamic pool size adjustment
     */
    public synchronized void resizePool(int newSize) {
        if (newSize < 1 || newSize > 20) {
            throw new IllegalArgumentException("Pool size must be between 1 and 20");
        }

        if (isShutdown) {
            throw new IllegalStateException("Cannot resize shutdown pool");
        }

        int currentSize = poolSize;
        if (newSize == currentSize) {
            logger.info("Pool size already {}, no resize needed", newSize);
            return;
        }

        logger.info("Resizing pool from {} to {}", currentSize, newSize);

        if (newSize > currentSize) {
            // Increase pool size - create new executors
            int toAdd = newSize - currentSize;
            for (int i = 0; i < toAdd; i++) {
                try {
                    Python3Executor executor = createExecutor();
                    allExecutors.add(executor);
                    if (!availableExecutors.offer(executor)) {
                        logger.warn("Failed to add executor to available queue during resize");
                    }
                    logger.info("Added executor {} of {}", i + 1, toAdd);
                } catch (IOException e) {
                    logger.error("Failed to create executor during pool resize", e);
                    // Continue trying to create remaining executors
                }
            }
        } else {
            // Decrease pool size - remove excess executors
            int toRemove = currentSize - newSize;
            for (int i = 0; i < toRemove; i++) {
                // Try to remove from available executors first (not currently in use)
                Python3Executor executor = availableExecutors.poll();
                if (executor != null) {
                    allExecutors.remove(executor);
                    try {
                        executor.shutdown();
                        logger.info("Removed available executor {} of {}", i + 1, toRemove);
                    } catch (Exception e) {
                        logger.error("Error shutting down executor during resize", e);
                    }
                } else {
                    logger.warn("No available executors to remove, {} executors currently in use",
                            currentSize - availableExecutors.size());
                    break;
                }
            }
        }

        poolSize = newSize;
        logger.info("Pool resized to {} (healthy: {}, available: {})",
                newSize, allExecutors.stream().filter(Python3Executor::isHealthy).count(), availableExecutors.size());
    }

    /**
     * Get the current pool size.
     *
     * @return the current pool size
     *
     * v1.17.2: Added for dynamic pool size querying
     */
    public int getPoolSize() {
        return poolSize;
    }

    /**
     * Shutdown the process pool
     */
    public void shutdown() {
        logger.info("Shutting down Python 3 process pool");
        isShutdown = true;

        // Stop health check
        healthCheckExecutor.shutdownNow();

        // Shutdown all executors
        for (Python3Executor executor : allExecutors) {
            try {
                executor.shutdown();
            } catch (Exception e) {
                logger.error("Error shutting down executor", e);
            }
        }

        allExecutors.clear();
        availableExecutors.clear();

        logger.info("Python 3 process pool shutdown complete");
    }

    /**
     * Check if pool is shutdown
     */
    public boolean isShutdown() {
        return isShutdown;
    }

    /**
     * Pool statistics
     */
    public static class PoolStats {
        public final int totalSize;
        public final int available;
        public final int inUse;
        public final int healthy;

        public PoolStats(int totalSize, int available, int inUse, int healthy) {
            this.totalSize = totalSize;
            this.available = available;
            this.inUse = inUse;
            this.healthy = healthy;
        }

        @Override
        public String toString() {
            return String.format("PoolStats{total=%d, available=%d, inUse=%d, healthy=%d}",
                    totalSize, available, inUse, healthy);
        }
    }

    /**
     * Get metrics collector.
     * @return Metrics collector
     * @since v2.14.0 Phase 2 Week 3-4
     */
    public MetricsCollector getMetricsCollector() {
        return metricsCollector;
    }

    /**
     * Get circuit breaker.
     * @return Circuit breaker
     * @since v2.14.0 Phase 2 Week 3-4
     */
    public CircuitBreaker getCircuitBreaker() {
        return circuitBreaker;
    }

    /**
     * Get alert manager.
     * @return Alert manager
     * @since v2.14.0 Phase 2 Week 3-4
     */
    public AlertManager getAlertManager() {
        return alertManager;
    }
}
