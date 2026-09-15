package com.gaskony.python3.gateway;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for CircuitBreaker.
 *
 * Tests state transitions: CLOSED → OPEN → HALF_OPEN → CLOSED.
 * No Ignition SDK required — pure Java logic.
 */
class CircuitBreakerTest {

    // ===== Initial state =====

    @Test
    void defaultConstructor_startsInClosedState() {
        CircuitBreaker cb = new CircuitBreaker();
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(cb.isClosed()).isTrue();
        assertThat(cb.isOpen()).isFalse();
        assertThat(cb.isHalfOpen()).isFalse();
    }

    @Test
    void customConstructor_startsInClosedState() {
        CircuitBreaker cb = new CircuitBreaker(3, 10000, 5000, 2);
        assertThat(cb.isClosed()).isTrue();
    }

    @Test
    void allowRequest_inClosedState_returnsTrue() {
        CircuitBreaker cb = new CircuitBreaker(3, 10000, 5000, 2);
        assertThat(cb.allowRequest()).isTrue();
    }

    // ===== Counters start at zero =====

    @Test
    void totalOpens_startsAtZero() {
        CircuitBreaker cb = new CircuitBreaker();
        assertThat(cb.getTotalOpens()).isEqualTo(0);
    }

    @Test
    void totalCloses_startsAtZero() {
        CircuitBreaker cb = new CircuitBreaker();
        assertThat(cb.getTotalCloses()).isEqualTo(0);
    }

    @Test
    void totalRejections_startsAtZero() {
        CircuitBreaker cb = new CircuitBreaker();
        assertThat(cb.getTotalRejections()).isEqualTo(0);
    }

    @Test
    void failureCount_startsAtZero() {
        CircuitBreaker cb = new CircuitBreaker();
        assertThat(cb.getFailureCount()).isEqualTo(0);
    }

    // ===== CLOSED → OPEN transition =====

    @Test
    void recordFailure_belowThreshold_staysInClosed() {
        // threshold=3, window=10s (large window so failures accumulate)
        CircuitBreaker cb = new CircuitBreaker(3, 10000, 5000, 2);
        cb.recordFailure();
        cb.recordFailure();
        assertThat(cb.isClosed()).isTrue();
    }

    @Test
    void recordFailure_atThreshold_opensCircuit() {
        CircuitBreaker cb = new CircuitBreaker(3, 10000, 5000, 2);
        cb.recordFailure();
        cb.recordFailure();
        cb.recordFailure();
        assertThat(cb.isOpen()).isTrue();
        assertThat(cb.getTotalOpens()).isEqualTo(1);
    }

    @Test
    void allowRequest_whenOpen_returnsFalse() {
        CircuitBreaker cb = new CircuitBreaker(2, 10000, 60000, 2);
        cb.recordFailure();
        cb.recordFailure();
        assertThat(cb.isOpen()).isTrue();
        assertThat(cb.allowRequest()).isFalse();
    }

    @Test
    void allowRequest_whenOpen_incrementsRejections() {
        CircuitBreaker cb = new CircuitBreaker(2, 10000, 60000, 2);
        cb.recordFailure();
        cb.recordFailure();
        cb.allowRequest(); // rejected
        cb.allowRequest(); // rejected
        assertThat(cb.getTotalRejections()).isEqualTo(2);
    }

    // ===== recordSuccess in CLOSED state =====

    @Test
    void recordSuccess_inClosedState_resetsFailureCount() {
        CircuitBreaker cb = new CircuitBreaker(5, 10000, 5000, 2);
        cb.recordFailure();
        cb.recordFailure();
        assertThat(cb.getFailureCount()).isGreaterThan(0);
        cb.recordSuccess();
        assertThat(cb.getFailureCount()).isEqualTo(0);
    }

    // ===== OPEN → HALF_OPEN transition (timeout expired) =====

    @Test
    void allowRequest_openWithExpiredTimeout_transitionsToHalfOpen() throws InterruptedException {
        // Very short timeout so we can test without long wait
        CircuitBreaker cb = new CircuitBreaker(2, 10000, 50, 2);
        cb.recordFailure();
        cb.recordFailure();
        assertThat(cb.isOpen()).isTrue();

        // intentional fixed delay — circuit breaker reads System.currentTimeMillis()
        // when deciding whether the open-state timeout has expired. Awaitility cannot
        // virtualise wall-clock time without a Clock seam on CircuitBreaker.
        Thread.sleep(100);

        boolean allowed = cb.allowRequest();
        assertThat(allowed).isTrue(); // Should be allowed (now HALF_OPEN)
        assertThat(cb.isHalfOpen()).isTrue();
    }

    // ===== HALF_OPEN → OPEN (failure during recovery) =====

    @Test
    void recordFailure_inHalfOpen_reopensCircuit() throws InterruptedException {
        CircuitBreaker cb = new CircuitBreaker(2, 10000, 50, 2);
        cb.recordFailure();
        cb.recordFailure();
        // intentional fixed delay — wait past the 50ms open-state timeout so the next
        // allowRequest() flips the breaker to HALF_OPEN.
        Thread.sleep(100);
        cb.allowRequest(); // transitions to HALF_OPEN
        assertThat(cb.isHalfOpen()).isTrue();

        cb.recordFailure();
        assertThat(cb.isOpen()).isTrue();
        assertThat(cb.getTotalOpens()).isEqualTo(2); // opened again
    }

    // ===== HALF_OPEN → CLOSED (success threshold reached) =====

    @Test
    void recordSuccess_inHalfOpen_atThreshold_closesCircuit() throws InterruptedException {
        CircuitBreaker cb = new CircuitBreaker(2, 10000, 50, 2);
        cb.recordFailure();
        cb.recordFailure();
        // intentional fixed delay — wait past the 50ms open-state timeout to allow
        // the breaker to transition to HALF_OPEN on the next allowRequest().
        Thread.sleep(100);
        cb.allowRequest(); // transitions to HALF_OPEN

        cb.recordSuccess(); // 1 success
        assertThat(cb.isHalfOpen()).isTrue(); // still half-open

        cb.recordSuccess(); // 2 successes (threshold)
        assertThat(cb.isClosed()).isTrue();
        assertThat(cb.getTotalCloses()).isEqualTo(1);
    }

    // ===== reset =====

    @Test
    void reset_resetsToClosedState() {
        CircuitBreaker cb = new CircuitBreaker(2, 10000, 5000, 2);
        cb.recordFailure();
        cb.recordFailure();
        assertThat(cb.isOpen()).isTrue();

        cb.reset();

        assertThat(cb.isClosed()).isTrue();
        assertThat(cb.getFailureCount()).isEqualTo(0);
    }

    // ===== toString and timeSinceStateChange =====

    @Test
    void toString_returnsNonEmpty() {
        CircuitBreaker cb = new CircuitBreaker();
        assertThat(cb.toString()).isNotEmpty();
    }

    @Test
    void getTimeSinceStateChange_returnsNonNegative() {
        CircuitBreaker cb = new CircuitBreaker();
        assertThat(cb.getTimeSinceStateChange()).isGreaterThanOrEqualTo(0);
    }
}
