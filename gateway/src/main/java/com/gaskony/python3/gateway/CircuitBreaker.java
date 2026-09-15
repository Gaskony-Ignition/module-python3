package com.gaskony.python3.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Circuit breaker pattern implementation for Python3 execution.
 *
 * Prevents cascading failures by detecting repeated failures and opening
 * the circuit to block further executions temporarily.
 *
 * States:
 * - CLOSED: Normal operation, all requests allowed
 * - OPEN: Circuit open, all requests rejected immediately
 * - HALF_OPEN: Testing recovery, limited requests allowed
 *
 * Transition logic:
 * - CLOSED → OPEN: After threshold failures in time window
 * - OPEN → HALF_OPEN: After timeout period
 * - HALF_OPEN → CLOSED: After success threshold
 * - HALF_OPEN → OPEN: After any failure
 *
 * @since v2.13.0 (Phase 2 Week 3-4: Enhanced Monitoring)
 */
public class CircuitBreaker {

    private static final Logger logger = LoggerFactory.getLogger(CircuitBreaker.class);

    public enum State {
        CLOSED,     // Normal operation
        OPEN,       // Circuit open, rejecting requests
        HALF_OPEN   // Testing recovery
    }

    // Configuration
    private final int failureThreshold;           // Number of failures to open circuit
    private final long failureWindowMs;           // Time window for counting failures
    private final long openStateTimeoutMs;        // How long to stay open before trying half-open
    private final int halfOpenSuccessThreshold;   // Successes needed in half-open to close

    // State tracking
    private final AtomicReference<State> state;
    private final AtomicInteger failureCount;
    private final AtomicInteger halfOpenSuccessCount;
    private final AtomicLong lastFailureTime;
    private final AtomicLong stateChangeTime;

    // Statistics
    private final AtomicLong totalOpens;
    private final AtomicLong totalCloses;
    private final AtomicLong totalRejections;

    /**
     * Create circuit breaker with default settings.
     * - Failure threshold: 5 failures
     * - Failure window: 60 seconds
     * - Open timeout: 30 seconds
     * - Half-open success threshold: 3 successes
     */
    public CircuitBreaker() {
        this(5, 60000, 30000, 3);
    }

    /**
     * Create circuit breaker with custom settings.
     */
    public CircuitBreaker(int failureThreshold, long failureWindowMs,
                          long openStateTimeoutMs, int halfOpenSuccessThreshold) {
        this.failureThreshold = failureThreshold;
        this.failureWindowMs = failureWindowMs;
        this.openStateTimeoutMs = openStateTimeoutMs;
        this.halfOpenSuccessThreshold = halfOpenSuccessThreshold;

        this.state = new AtomicReference<>(State.CLOSED);
        this.failureCount = new AtomicInteger(0);
        this.halfOpenSuccessCount = new AtomicInteger(0);
        this.lastFailureTime = new AtomicLong(0);
        this.stateChangeTime = new AtomicLong(System.currentTimeMillis());

        this.totalOpens = new AtomicLong(0);
        this.totalCloses = new AtomicLong(0);
        this.totalRejections = new AtomicLong(0);

        logger.info("CircuitBreaker initialized: failureThreshold={}, failureWindowMs={}, openTimeoutMs={}, halfOpenSuccessThreshold={}",
            failureThreshold, failureWindowMs, openStateTimeoutMs, halfOpenSuccessThreshold);
    }

    /**
     * Check if request is allowed.
     * @return true if request should proceed, false if circuit is open
     */
    public boolean allowRequest() {
        State currentState = state.get();

        switch (currentState) {
            case CLOSED:
                // Normal operation - allow request
                return true;

            case OPEN:
                // Check if timeout expired, transition to HALF_OPEN
                long now = System.currentTimeMillis();
                long timeSinceOpen = now - stateChangeTime.get();

                if (timeSinceOpen >= openStateTimeoutMs) {
                    if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                        stateChangeTime.set(now);
                        halfOpenSuccessCount.set(0);
                        logger.info("Circuit breaker transitioned OPEN → HALF_OPEN after {}ms", timeSinceOpen);
                        return true; // Allow test request
                    }
                }

                // Circuit still open - reject
                totalRejections.incrementAndGet();
                return false;

            case HALF_OPEN:
                // Allow limited requests to test recovery
                return true;

            default:
                return true;
        }
    }

    /**
     * Record successful execution.
     */
    public void recordSuccess() {
        State currentState = state.get();

        if (currentState == State.HALF_OPEN) {
            int successes = halfOpenSuccessCount.incrementAndGet();

            if (successes >= halfOpenSuccessThreshold) {
                if (state.compareAndSet(State.HALF_OPEN, State.CLOSED)) {
                    stateChangeTime.set(System.currentTimeMillis());
                    failureCount.set(0);
                    totalCloses.incrementAndGet();
                    logger.info("Circuit breaker CLOSED after {} successful test requests", successes);
                }
            }
        } else if (currentState == State.CLOSED) {
            // Reset failure count on success in closed state
            failureCount.set(0);
        }
    }

    /**
     * Record failed execution.
     */
    public void recordFailure() {
        long now = System.currentTimeMillis();
        State currentState = state.get();

        if (currentState == State.HALF_OPEN) {
            // Any failure in half-open → back to open
            if (state.compareAndSet(State.HALF_OPEN, State.OPEN)) {
                stateChangeTime.set(now);
                totalOpens.incrementAndGet();
                logger.warn("Circuit breaker RE-OPENED due to failure during recovery test");
            }
            return;
        }

        if (currentState == State.CLOSED) {
            // Check if last failure was within window
            long lastFailure = lastFailureTime.get();
            if (now - lastFailure > failureWindowMs) {
                // Outside window - reset counter
                failureCount.set(1);
            } else {
                // Within window - increment
                int failures = failureCount.incrementAndGet();

                if (failures >= failureThreshold) {
                    // Threshold reached - open circuit
                    if (state.compareAndSet(State.CLOSED, State.OPEN)) {
                        stateChangeTime.set(now);
                        totalOpens.incrementAndGet();
                        logger.error("Circuit breaker OPENED after {} failures in {}ms window",
                            failures, failureWindowMs);
                    }
                }
            }

            lastFailureTime.set(now);
        }
    }

    /**
     * Manually reset circuit breaker to CLOSED state.
     */
    public void reset() {
        state.set(State.CLOSED);
        failureCount.set(0);
        halfOpenSuccessCount.set(0);
        stateChangeTime.set(System.currentTimeMillis());
        logger.info("Circuit breaker manually reset to CLOSED state");
    }

    // Getters

    public State getState() {
        return state.get();
    }

    public int getFailureCount() {
        return failureCount.get();
    }

    public long getTotalOpens() {
        return totalOpens.get();
    }

    public long getTotalCloses() {
        return totalCloses.get();
    }

    public long getTotalRejections() {
        return totalRejections.get();
    }

    public long getTimeSinceStateChange() {
        return System.currentTimeMillis() - stateChangeTime.get();
    }

    public boolean isOpen() {
        return state.get() == State.OPEN;
    }

    public boolean isClosed() {
        return state.get() == State.CLOSED;
    }

    public boolean isHalfOpen() {
        return state.get() == State.HALF_OPEN;
    }

    @Override
    public String toString() {
        return String.format(
            "CircuitBreaker{state=%s, failures=%d/%d, opens=%d, closes=%d, rejections=%d, timeSinceChange=%dms}",
            state.get(),
            failureCount.get(),
            failureThreshold,
            totalOpens.get(),
            totalCloses.get(),
            totalRejections.get(),
            getTimeSinceStateChange()
        );
    }
}
