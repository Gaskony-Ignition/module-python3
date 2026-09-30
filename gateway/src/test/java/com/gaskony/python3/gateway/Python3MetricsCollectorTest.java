package com.gaskony.python3.gateway;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for Python3MetricsCollector.
 *
 * Tests execution recording, metric retrieval, script-level metrics,
 * and gateway impact reporting. No Ignition SDK required — pure Java.
 */
class Python3MetricsCollectorTest {

    // ===== Initial state =====

    @Test
    void defaultConstructor_getMetricsReturnsMap() {
        Python3MetricsCollector mc = new Python3MetricsCollector();
        Map<String, Object> metrics = mc.getMetrics();
        assertThat(metrics).isNotNull();
        assertThat(metrics).isNotEmpty();
    }

    @Test
    void defaultConstructor_getScriptMetrics_returnsEmptyList() {
        Python3MetricsCollector mc = new Python3MetricsCollector();
        List<Map<String, Object>> scripts = mc.getScriptMetrics();
        assertThat(scripts).isNotNull();
        assertThat(scripts).isEmpty();
    }

    @Test
    void defaultConstructor_getHistoricalMetrics_returnsList() {
        Python3MetricsCollector mc = new Python3MetricsCollector();
        List<Map<String, Object>> history = mc.getHistoricalMetrics();
        assertThat(history).isNotNull();
    }

    @Test
    void defaultConstructor_getHealthAlerts_returnsList() {
        Python3MetricsCollector mc = new Python3MetricsCollector();
        List<Map<String, Object>> alerts = mc.getHealthAlerts();
        assertThat(alerts).isNotNull();
    }

    @Test
    void getGatewayImpact_returnsNonNullMap() {
        Python3MetricsCollector mc = new Python3MetricsCollector();
        Map<String, Object> impact = mc.getGatewayImpact();
        assertThat(impact).isNotNull();
        assertThat(impact).isNotEmpty();
    }

    // ===== recordExecution =====

    @Test
    void recordExecution_incrementsTotalExecutions() {
        Python3MetricsCollector mc = new Python3MetricsCollector();
        mc.recordExecution(100);
        mc.recordExecution(200);

        Map<String, Object> metrics = mc.getMetrics();
        // Field names use snake_case
        assertThat(metrics).containsKey("total_executions");
        assertThat(((Number) metrics.get("total_executions")).longValue()).isEqualTo(2);
    }

    @Test
    void recordExecution_withScriptId_appearsInScriptMetrics() {
        Python3MetricsCollector mc = new Python3MetricsCollector();
        mc.recordExecution(150, "my-script");

        List<Map<String, Object>> scripts = mc.getScriptMetrics();
        assertThat(scripts).hasSize(1);
        assertThat(scripts.get(0)).containsKey("script_identifier");
    }

    // ===== recordFailure =====

    @Test
    void recordFailure_incrementsFailureCount() {
        Python3MetricsCollector mc = new Python3MetricsCollector();
        mc.recordExecution(100);
        mc.recordFailure("SyntaxError", 50);

        Map<String, Object> metrics = mc.getMetrics();
        assertThat(metrics).containsKey("failed_executions");
        assertThat(((Number) metrics.get("failed_executions")).longValue()).isEqualTo(1);
    }

    @Test
    void recordFailure_withScriptId_tracked() {
        Python3MetricsCollector mc = new Python3MetricsCollector();
        mc.recordFailure("RuntimeError", 200, "error-script");

        List<Map<String, Object>> scripts = mc.getScriptMetrics();
        assertThat(scripts).hasSize(1);
    }

    // ===== recordPoolWait / setPoolSize / activeExecutions =====

    @Test
    void recordPoolWait_doesNotThrow() {
        Python3MetricsCollector mc = new Python3MetricsCollector();
        assertThatCode(() -> mc.recordPoolWait(500)).doesNotThrowAnyException();
    }

    @Test
    void setPoolSize_doesNotThrow() {
        Python3MetricsCollector mc = new Python3MetricsCollector();
        assertThatCode(() -> mc.setPoolSize(5)).doesNotThrowAnyException();
    }

    @Test
    void incrementAndDecrementActiveExecutions_doesNotThrow() {
        Python3MetricsCollector mc = new Python3MetricsCollector();
        mc.incrementActiveExecutions();
        mc.incrementActiveExecutions();
        mc.decrementActiveExecutions();
        // Should not throw or go negative
        assertThat(mc).isNotNull();
    }

    // ===== reset =====

    @Test
    void reset_clearsExecutionCounts() {
        Python3MetricsCollector mc = new Python3MetricsCollector();
        mc.recordExecution(100);
        mc.recordExecution(200);

        mc.reset();

        Map<String, Object> metrics = mc.getMetrics();
        assertThat(((Number) metrics.get("total_executions")).longValue()).isEqualTo(0);
    }
}
