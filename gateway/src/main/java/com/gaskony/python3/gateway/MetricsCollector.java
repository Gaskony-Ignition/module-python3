package com.gaskony.python3.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Collects and tracks metrics for the Python3ProcessPool.
 *
 * Metrics tracked:
 * - Execution counts (total, success, failure)
 * - Response times (p50, p95, p99)
 * - Pool utilization
 * - Queue depth and wait times
 * - Error rates by type
 *
 * Thread-safe implementation using atomic operations.
 *
 * @since v2.13.0 (Phase 2 Week 3-4: Enhanced Monitoring)
 */
public class MetricsCollector {

    private static final Logger logger = LoggerFactory.getLogger(MetricsCollector.class);

    // Execution counts
    private final AtomicLong totalExecutions;
    private final AtomicLong successfulExecutions;
    private final AtomicLong failedExecutions;

    // Error counts by type
    private final AtomicLong syntaxErrors;
    private final AtomicLong runtimeErrors;
    private final AtomicLong timeoutErrors;

    // Pool utilization
    private final AtomicLong totalBorrows;
    private final AtomicLong totalReturns;
    private final AtomicLong timeoutWaits;

    // Response time tracking (circular buffer for percentiles)
    private final List<Long> responseTimes;
    private final int maxResponseTimesSamples;
    private int responseTimeIndex;

    // Queue tracking
    private final AtomicLong totalQueueWaitTimeMs;
    private final AtomicLong maxQueueWaitTimeMs;

    // Timestamps
    private final long startTime;
    private volatile long lastResetTime;

    public MetricsCollector() {
        this(1000); // Keep last 1000 response times for percentile calculation
    }

    public MetricsCollector(int maxResponseTimesSamples) {
        this.totalExecutions = new AtomicLong(0);
        this.successfulExecutions = new AtomicLong(0);
        this.failedExecutions = new AtomicLong(0);

        this.syntaxErrors = new AtomicLong(0);
        this.runtimeErrors = new AtomicLong(0);
        this.timeoutErrors = new AtomicLong(0);

        this.totalBorrows = new AtomicLong(0);
        this.totalReturns = new AtomicLong(0);
        this.timeoutWaits = new AtomicLong(0);

        this.maxResponseTimesSamples = maxResponseTimesSamples;
        this.responseTimes = new ArrayList<>(maxResponseTimesSamples);
        this.responseTimeIndex = 0;

        this.totalQueueWaitTimeMs = new AtomicLong(0);
        this.maxQueueWaitTimeMs = new AtomicLong(0);

        this.startTime = System.currentTimeMillis();
        this.lastResetTime = this.startTime;
    }

    // Recording methods

    public void recordExecution(boolean success, long responseTimeMs) {
        totalExecutions.incrementAndGet();
        if (success) {
            successfulExecutions.incrementAndGet();
        } else {
            failedExecutions.incrementAndGet();
        }
        recordResponseTime(responseTimeMs);
    }

    public void recordSyntaxError() {
        syntaxErrors.incrementAndGet();
    }

    public void recordRuntimeError() {
        runtimeErrors.incrementAndGet();
    }

    public void recordTimeoutError() {
        timeoutErrors.incrementAndGet();
    }

    public void recordBorrow() {
        totalBorrows.incrementAndGet();
    }

    public void recordReturn() {
        totalReturns.incrementAndGet();
    }

    public void recordTimeoutWait() {
        timeoutWaits.incrementAndGet();
    }

    public void recordQueueWait(long waitTimeMs) {
        totalQueueWaitTimeMs.addAndGet(waitTimeMs);

        // Update max if needed (thread-safe)
        long currentMax = maxQueueWaitTimeMs.get();
        while (waitTimeMs > currentMax) {
            if (maxQueueWaitTimeMs.compareAndSet(currentMax, waitTimeMs)) {
                break;
            }
            currentMax = maxQueueWaitTimeMs.get();
        }
    }

    private synchronized void recordResponseTime(long responseTimeMs) {
        if (responseTimes.size() < maxResponseTimesSamples) {
            responseTimes.add(responseTimeMs);
        } else {
            // Circular buffer - overwrite oldest
            responseTimes.set(responseTimeIndex, responseTimeMs);
            responseTimeIndex = (responseTimeIndex + 1) % maxResponseTimesSamples;
        }
    }

    // Getter methods

    public long getTotalExecutions() {
        return totalExecutions.get();
    }

    public long getSuccessfulExecutions() {
        return successfulExecutions.get();
    }

    public long getFailedExecutions() {
        return failedExecutions.get();
    }

    public double getSuccessRate() {
        long total = totalExecutions.get();
        return total == 0 ? 0.0 : (double) successfulExecutions.get() / total;
    }

    public double getErrorRate() {
        long total = totalExecutions.get();
        return total == 0 ? 0.0 : (double) failedExecutions.get() / total;
    }

    public long getSyntaxErrors() {
        return syntaxErrors.get();
    }

    public long getRuntimeErrors() {
        return runtimeErrors.get();
    }

    public long getTimeoutErrors() {
        return timeoutErrors.get();
    }

    public long getTotalBorrows() {
        return totalBorrows.get();
    }

    public long getTotalReturns() {
        return totalReturns.get();
    }

    public long getTimeoutWaits() {
        return timeoutWaits.get();
    }

    public long getAverageQueueWaitTimeMs() {
        long borrows = totalBorrows.get();
        return borrows == 0 ? 0 : totalQueueWaitTimeMs.get() / borrows;
    }

    public long getMaxQueueWaitTimeMs() {
        return maxQueueWaitTimeMs.get();
    }

    public long getUptimeMs() {
        return System.currentTimeMillis() - startTime;
    }

    public long getTimeSinceLastResetMs() {
        return System.currentTimeMillis() - lastResetTime;
    }

    /**
     * Calculate response time percentile.
     * @param percentile Value between 0.0 and 1.0 (e.g., 0.95 for p95)
     */
    public synchronized long getResponseTimePercentile(double percentile) {
        if (responseTimes.isEmpty()) {
            return 0;
        }

        List<Long> sorted = new ArrayList<>(responseTimes);
        sorted.sort(Long::compareTo);

        int index = (int) Math.ceil(percentile * sorted.size()) - 1;
        index = Math.max(0, Math.min(index, sorted.size() - 1));

        return sorted.get(index);
    }

    public long getP50ResponseTime() {
        return getResponseTimePercentile(0.50);
    }

    public long getP95ResponseTime() {
        return getResponseTimePercentile(0.95);
    }

    public long getP99ResponseTime() {
        return getResponseTimePercentile(0.99);
    }

    public synchronized long getAverageResponseTime() {
        if (responseTimes.isEmpty()) {
            return 0;
        }
        return responseTimes.stream().mapToLong(Long::longValue).sum() / responseTimes.size();
    }

    /**
     * Reset all metrics.
     */
    public synchronized void reset() {
        totalExecutions.set(0);
        successfulExecutions.set(0);
        failedExecutions.set(0);

        syntaxErrors.set(0);
        runtimeErrors.set(0);
        timeoutErrors.set(0);

        totalBorrows.set(0);
        totalReturns.set(0);
        timeoutWaits.set(0);

        responseTimes.clear();
        responseTimeIndex = 0;

        totalQueueWaitTimeMs.set(0);
        maxQueueWaitTimeMs.set(0);

        lastResetTime = System.currentTimeMillis();

        logger.info("Metrics reset");
    }

    /**
     * Export metrics in Prometheus format.
     */
    public String toPrometheusFormat() {
        StringBuilder sb = new StringBuilder();

        // Execution metrics
        sb.append("# HELP python3_executions_total Total number of Python3 executions\n");
        sb.append("# TYPE python3_executions_total counter\n");
        sb.append("python3_executions_total ").append(totalExecutions.get()).append("\n\n");

        sb.append("# HELP python3_executions_successful Total number of successful executions\n");
        sb.append("# TYPE python3_executions_successful counter\n");
        sb.append("python3_executions_successful ").append(successfulExecutions.get()).append("\n\n");

        sb.append("# HELP python3_executions_failed Total number of failed executions\n");
        sb.append("# TYPE python3_executions_failed counter\n");
        sb.append("python3_executions_failed ").append(failedExecutions.get()).append("\n\n");

        // Error metrics
        sb.append("# HELP python3_errors_syntax Total number of syntax errors\n");
        sb.append("# TYPE python3_errors_syntax counter\n");
        sb.append("python3_errors_syntax ").append(syntaxErrors.get()).append("\n\n");

        sb.append("# HELP python3_errors_runtime Total number of runtime errors\n");
        sb.append("# TYPE python3_errors_runtime counter\n");
        sb.append("python3_errors_runtime ").append(runtimeErrors.get()).append("\n\n");

        sb.append("# HELP python3_errors_timeout Total number of timeout errors\n");
        sb.append("# TYPE python3_errors_timeout counter\n");
        sb.append("python3_errors_timeout ").append(timeoutErrors.get()).append("\n\n");

        // Response time metrics
        sb.append("# HELP python3_response_time_p50 50th percentile response time in milliseconds\n");
        sb.append("# TYPE python3_response_time_p50 gauge\n");
        sb.append("python3_response_time_p50 ").append(getP50ResponseTime()).append("\n\n");

        sb.append("# HELP python3_response_time_p95 95th percentile response time in milliseconds\n");
        sb.append("# TYPE python3_response_time_p95 gauge\n");
        sb.append("python3_response_time_p95 ").append(getP95ResponseTime()).append("\n\n");

        sb.append("# HELP python3_response_time_p99 99th percentile response time in milliseconds\n");
        sb.append("# TYPE python3_response_time_p99 gauge\n");
        sb.append("python3_response_time_p99 ").append(getP99ResponseTime()).append("\n\n");

        // Pool metrics
        sb.append("# HELP python3_pool_borrows_total Total number of executor borrows\n");
        sb.append("# TYPE python3_pool_borrows_total counter\n");
        sb.append("python3_pool_borrows_total ").append(totalBorrows.get()).append("\n\n");

        sb.append("# HELP python3_pool_timeout_waits Total number of timeout waits\n");
        sb.append("# TYPE python3_pool_timeout_waits counter\n");
        sb.append("python3_pool_timeout_waits ").append(timeoutWaits.get()).append("\n\n");

        sb.append("# HELP python3_queue_wait_time_avg Average queue wait time in milliseconds\n");
        sb.append("# TYPE python3_queue_wait_time_avg gauge\n");
        sb.append("python3_queue_wait_time_avg ").append(getAverageQueueWaitTimeMs()).append("\n\n");

        sb.append("# HELP python3_uptime_seconds Uptime in seconds\n");
        sb.append("# TYPE python3_uptime_seconds gauge\n");
        sb.append("python3_uptime_seconds ").append(getUptimeMs() / 1000).append("\n\n");

        return sb.toString();
    }

    @Override
    public String toString() {
        return String.format(
            "MetricsCollector{executions=%d, success=%d, failed=%d, errorRate=%.2f%%, p50=%dms, p95=%dms, p99=%dms, avgQueueWait=%dms}",
            getTotalExecutions(),
            getSuccessfulExecutions(),
            getFailedExecutions(),
            getErrorRate() * 100,
            getP50ResponseTime(),
            getP95ResponseTime(),
            getP99ResponseTime(),
            getAverageQueueWaitTimeMs()
        );
    }
}
