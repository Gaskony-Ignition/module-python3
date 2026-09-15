package com.gaskony.python3.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Alert manager for Python3 process pool monitoring.
 *
 * Configurable alert thresholds:
 * - High error rate (default: > 5%)
 * - Pool exhaustion (all executors borrowed)
 * - Executor crashes
 * - Circuit breaker opens
 * - Slow response times (default: > 5 seconds)
 *
 * Alerts are logged to Gateway logs. Future versions can add email/Slack.
 *
 * Alert suppression prevents spam:
 * - Same alert not repeated within cooldown period (default: 5 minutes)
 *
 * @since v2.13.0 (Phase 2 Week 3-4: Enhanced Monitoring)
 */
public class AlertManager {

    private static final Logger logger = LoggerFactory.getLogger(AlertManager.class);

    // Alert thresholds (configurable)
    private double errorRateThreshold;
    private long slowResponseThresholdMs;
    private long alertCooldownMs;

    // Alert state tracking (for suppression)
    private final AtomicLong lastErrorRateAlert;
    private final AtomicLong lastPoolExhaustionAlert;
    private final AtomicLong lastExecutorCrashAlert;
    private final AtomicLong lastCircuitBreakerAlert;
    private final AtomicLong lastSlowResponseAlert;

    // Alert counters
    private final AtomicLong totalAlerts;
    private final AtomicLong suppressedAlerts;

    /**
     * Create alert manager with default thresholds.
     */
    public AlertManager() {
        this(0.05, 5000, 300000); // 5% error rate, 5s response time, 5min cooldown
    }

    /**
     * Create alert manager with custom thresholds.
     *
     * @param errorRateThreshold Error rate threshold (0.0-1.0, e.g., 0.05 = 5%)
     * @param slowResponseThresholdMs Response time threshold in milliseconds
     * @param alertCooldownMs Minimum time between same alerts in milliseconds
     */
    public AlertManager(double errorRateThreshold, long slowResponseThresholdMs, long alertCooldownMs) {
        this.errorRateThreshold = errorRateThreshold;
        this.slowResponseThresholdMs = slowResponseThresholdMs;
        this.alertCooldownMs = alertCooldownMs;

        this.lastErrorRateAlert = new AtomicLong(0);
        this.lastPoolExhaustionAlert = new AtomicLong(0);
        this.lastExecutorCrashAlert = new AtomicLong(0);
        this.lastCircuitBreakerAlert = new AtomicLong(0);
        this.lastSlowResponseAlert = new AtomicLong(0);

        this.totalAlerts = new AtomicLong(0);
        this.suppressedAlerts = new AtomicLong(0);

        logger.info("AlertManager initialized: errorRateThreshold={}%, slowResponseMs={}, cooldownMs={}",
            errorRateThreshold * 100, slowResponseThresholdMs, alertCooldownMs);
    }

    /**
     * Check and alert on high error rate.
     */
    public void checkErrorRate(double currentErrorRate, long totalExecutions) {
        if (totalExecutions < 10) {
            // Need minimum sample size
            return;
        }

        if (currentErrorRate > errorRateThreshold) {
            if (shouldAlert(lastErrorRateAlert)) {
                logger.error("ALERT: High error rate detected: {:.2f}% (threshold: {:.2f}%) over {} executions",
                    currentErrorRate * 100,
                    errorRateThreshold * 100,
                    totalExecutions);
                totalAlerts.incrementAndGet();
            } else {
                suppressedAlerts.incrementAndGet();
            }
        }
    }

    /**
     * Alert on pool exhaustion.
     */
    public void alertPoolExhaustion(int poolSize, int available) {
        if (available == 0) {
            if (shouldAlert(lastPoolExhaustionAlert)) {
                logger.warn("ALERT: Pool exhausted - all {} executors are in use. Requests may be queued or timing out.",
                    poolSize);
                totalAlerts.incrementAndGet();
            } else {
                suppressedAlerts.incrementAndGet();
            }
        }
    }

    /**
     * Alert on executor crash.
     */
    public void alertExecutorCrash(String reason) {
        if (shouldAlert(lastExecutorCrashAlert)) {
            logger.error("ALERT: Python executor crashed: {}", reason);
            totalAlerts.incrementAndGet();
        } else {
            suppressedAlerts.incrementAndGet();
        }
    }

    /**
     * Alert when circuit breaker opens.
     */
    public void alertCircuitBreakerOpened(int failureCount, long failureWindowMs) {
        if (shouldAlert(lastCircuitBreakerAlert)) {
            logger.error("ALERT: Circuit breaker OPENED after {} failures in {}ms. " +
                "Python executions are now rejected to prevent cascading failures.",
                failureCount, failureWindowMs);
            totalAlerts.incrementAndGet();
        } else {
            suppressedAlerts.incrementAndGet();
        }
    }

    /**
     * Alert on circuit breaker close (recovery).
     */
    public void alertCircuitBreakerClosed() {
        logger.info("Circuit breaker CLOSED - Python executions resumed after successful recovery tests");
        // Don't count info alerts in total
    }

    /**
     * Check and alert on slow response times.
     */
    public void checkResponseTime(long p95ResponseTimeMs) {
        if (p95ResponseTimeMs > slowResponseThresholdMs) {
            if (shouldAlert(lastSlowResponseAlert)) {
                logger.warn("ALERT: Slow response times detected: p95={}ms (threshold: {}ms)",
                    p95ResponseTimeMs, slowResponseThresholdMs);
                totalAlerts.incrementAndGet();
            } else {
                suppressedAlerts.incrementAndGet();
            }
        }
    }

    /**
     * Alert on executor health degradation.
     */
    public void alertExecutorHealthDegraded(int healthScore, String healthStatus) {
        // Only alert on critical health
        if (healthScore < 25) {
            logger.error("ALERT: Executor health CRITICAL - score: {}, status: {}. Executor should be replaced.",
                healthScore, healthStatus);
            totalAlerts.incrementAndGet();
        }
    }

    /**
     * Check if alert should be sent (not in cooldown period).
     */
    private boolean shouldAlert(AtomicLong lastAlertTime) {
        long now = System.currentTimeMillis();
        long last = lastAlertTime.get();

        if (now - last >= alertCooldownMs) {
            return lastAlertTime.compareAndSet(last, now);
        }

        return false;
    }

    // Configuration setters

    public void setErrorRateThreshold(double threshold) {
        this.errorRateThreshold = Math.max(0.0, Math.min(1.0, threshold));
        logger.info("Error rate threshold updated to {}%", threshold * 100);
    }

    public void setSlowResponseThresholdMs(long thresholdMs) {
        this.slowResponseThresholdMs = thresholdMs;
        logger.info("Slow response threshold updated to {}ms", thresholdMs);
    }

    public void setAlertCooldownMs(long cooldownMs) {
        this.alertCooldownMs = cooldownMs;
        logger.info("Alert cooldown updated to {}ms", cooldownMs);
    }

    // Getters

    public double getErrorRateThreshold() {
        return errorRateThreshold;
    }

    public long getSlowResponseThresholdMs() {
        return slowResponseThresholdMs;
    }

    public long getAlertCooldownMs() {
        return alertCooldownMs;
    }

    public long getTotalAlerts() {
        return totalAlerts.get();
    }

    public long getSuppressedAlerts() {
        return suppressedAlerts.get();
    }

    /**
     * Reset alert cooldowns (for testing or manual override).
     */
    public void resetCooldowns() {
        lastErrorRateAlert.set(0);
        lastPoolExhaustionAlert.set(0);
        lastExecutorCrashAlert.set(0);
        lastCircuitBreakerAlert.set(0);
        lastSlowResponseAlert.set(0);
        logger.info("Alert cooldowns reset");
    }

    /**
     * Get alert summary.
     */
    public String getAlertSummary() {
        return String.format(
            "AlertManager{totalAlerts=%d, suppressedAlerts=%d, errorRateThreshold=%.2f%%, slowResponseMs=%d, cooldownMs=%d}",
            totalAlerts.get(),
            suppressedAlerts.get(),
            errorRateThreshold * 100,
            slowResponseThresholdMs,
            alertCooldownMs
        );
    }

    @Override
    public String toString() {
        return getAlertSummary();
    }
}
