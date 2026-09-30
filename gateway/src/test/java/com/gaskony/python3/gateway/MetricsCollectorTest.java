package com.gaskony.python3.gateway;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for MetricsCollector.
 *
 * Tests counter tracking, rate calculations, percentile computation,
 * and reset behavior. No Ignition SDK required — pure Java logic.
 */
class MetricsCollectorTest {

    // ===== Initial state =====

    @Test
    void defaultConstructor_allCountersZero() {
        MetricsCollector mc = new MetricsCollector();
        assertThat(mc.getTotalExecutions()).isEqualTo(0);
        assertThat(mc.getSuccessfulExecutions()).isEqualTo(0);
        assertThat(mc.getFailedExecutions()).isEqualTo(0);
        assertThat(mc.getSyntaxErrors()).isEqualTo(0);
        assertThat(mc.getRuntimeErrors()).isEqualTo(0);
        assertThat(mc.getTimeoutErrors()).isEqualTo(0);
        assertThat(mc.getTotalBorrows()).isEqualTo(0);
        assertThat(mc.getTotalReturns()).isEqualTo(0);
        assertThat(mc.getTimeoutWaits()).isEqualTo(0);
    }

    @Test
    void defaultConstructor_ratesReturnZeroWhenNoData() {
        MetricsCollector mc = new MetricsCollector();
        assertThat(mc.getSuccessRate()).isEqualTo(0.0);
        assertThat(mc.getErrorRate()).isEqualTo(0.0);
    }

    @Test
    void getUptimeMs_returnsPositiveValue() {
        MetricsCollector mc = new MetricsCollector();
        assertThat(mc.getUptimeMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void getTimeSinceLastResetMs_returnsNonNegative() {
        MetricsCollector mc = new MetricsCollector();
        assertThat(mc.getTimeSinceLastResetMs()).isGreaterThanOrEqualTo(0);
    }

    // ===== recordExecution =====

    @Test
    void recordExecution_success_incrementsSuccessfulAndTotal() {
        MetricsCollector mc = new MetricsCollector();
        mc.recordExecution(true, 100);
        assertThat(mc.getTotalExecutions()).isEqualTo(1);
        assertThat(mc.getSuccessfulExecutions()).isEqualTo(1);
        assertThat(mc.getFailedExecutions()).isEqualTo(0);
    }

    @Test
    void recordExecution_failure_incrementsFailedAndTotal() {
        MetricsCollector mc = new MetricsCollector();
        mc.recordExecution(false, 200);
        assertThat(mc.getTotalExecutions()).isEqualTo(1);
        assertThat(mc.getSuccessfulExecutions()).isEqualTo(0);
        assertThat(mc.getFailedExecutions()).isEqualTo(1);
    }

    @Test
    void recordExecution_mixed_computesRatesCorrectly() {
        MetricsCollector mc = new MetricsCollector();
        mc.recordExecution(true, 50);
        mc.recordExecution(true, 60);
        mc.recordExecution(false, 500);
        assertThat(mc.getSuccessRate()).isCloseTo(2.0 / 3.0, within(0.001));
        assertThat(mc.getErrorRate()).isCloseTo(1.0 / 3.0, within(0.001));
    }

    // ===== Error type counters =====

    @Test
    void recordSyntaxError_incrementsCounter() {
        MetricsCollector mc = new MetricsCollector();
        mc.recordSyntaxError();
        mc.recordSyntaxError();
        assertThat(mc.getSyntaxErrors()).isEqualTo(2);
    }

    @Test
    void recordRuntimeError_incrementsCounter() {
        MetricsCollector mc = new MetricsCollector();
        mc.recordRuntimeError();
        assertThat(mc.getRuntimeErrors()).isEqualTo(1);
    }

    @Test
    void recordTimeoutError_incrementsCounter() {
        MetricsCollector mc = new MetricsCollector();
        mc.recordTimeoutError();
        assertThat(mc.getTimeoutErrors()).isEqualTo(1);
    }

    // ===== Borrow/Return =====

    @Test
    void recordBorrow_incrementsBorrowCounter() {
        MetricsCollector mc = new MetricsCollector();
        mc.recordBorrow();
        mc.recordBorrow();
        assertThat(mc.getTotalBorrows()).isEqualTo(2);
    }

    @Test
    void recordReturn_incrementsReturnCounter() {
        MetricsCollector mc = new MetricsCollector();
        mc.recordReturn();
        assertThat(mc.getTotalReturns()).isEqualTo(1);
    }

    @Test
    void recordTimeoutWait_incrementsCounter() {
        MetricsCollector mc = new MetricsCollector();
        mc.recordTimeoutWait();
        assertThat(mc.getTimeoutWaits()).isEqualTo(1);
    }

    // ===== Queue wait time =====

    @Test
    void recordQueueWait_updatesMaxQueueWait() {
        MetricsCollector mc = new MetricsCollector();
        mc.recordQueueWait(100);
        mc.recordQueueWait(500);
        mc.recordQueueWait(200);
        assertThat(mc.getMaxQueueWaitTimeMs()).isEqualTo(500);
    }

    @Test
    void getAverageQueueWaitTimeMs_withNoBorrows_returnsZero() {
        MetricsCollector mc = new MetricsCollector();
        assertThat(mc.getAverageQueueWaitTimeMs()).isEqualTo(0);
    }

    // ===== Response time percentiles =====

    @Test
    void getP50ResponseTime_withSingleExecution_returnsValue() {
        MetricsCollector mc = new MetricsCollector();
        mc.recordExecution(true, 100);
        assertThat(mc.getP50ResponseTime()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void getP95ResponseTime_withNoData_returnsZero() {
        MetricsCollector mc = new MetricsCollector();
        assertThat(mc.getP95ResponseTime()).isEqualTo(0);
    }

    @Test
    void getP99ResponseTime_withNoData_returnsZero() {
        MetricsCollector mc = new MetricsCollector();
        assertThat(mc.getP99ResponseTime()).isEqualTo(0);
    }

    @Test
    void getAverageResponseTime_withSamples_returnsAverage() {
        MetricsCollector mc = new MetricsCollector();
        mc.recordExecution(true, 100);
        mc.recordExecution(true, 200);
        mc.recordExecution(true, 300);
        assertThat(mc.getAverageResponseTime()).isEqualTo(200);
    }

    @Test
    void getAverageResponseTime_withNoData_returnsZero() {
        MetricsCollector mc = new MetricsCollector();
        assertThat(mc.getAverageResponseTime()).isEqualTo(0);
    }

    // ===== reset =====

    @Test
    void reset_clearsAllCounters() {
        MetricsCollector mc = new MetricsCollector();
        mc.recordExecution(true, 100);
        mc.recordExecution(false, 200);
        mc.recordSyntaxError();
        mc.recordBorrow();

        mc.reset();

        assertThat(mc.getTotalExecutions()).isEqualTo(0);
        assertThat(mc.getSuccessfulExecutions()).isEqualTo(0);
        assertThat(mc.getFailedExecutions()).isEqualTo(0);
        assertThat(mc.getSyntaxErrors()).isEqualTo(0);
        assertThat(mc.getTotalBorrows()).isEqualTo(0);
    }

    // ===== Prometheus format =====

    @Test
    void toPrometheusFormat_returnsNonEmpty() {
        MetricsCollector mc = new MetricsCollector();
        mc.recordExecution(true, 100);
        String prometheus = mc.toPrometheusFormat();
        assertThat(prometheus).isNotEmpty();
        assertThat(prometheus).contains("python3");
    }

    // ===== toString =====

    @Test
    void toString_returnsNonEmpty() {
        MetricsCollector mc = new MetricsCollector();
        assertThat(mc.toString()).isNotEmpty();
    }
}
