package com.gaskony.python3.gateway;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PrometheusExporter.
 *
 * Tests the Prometheus text format export functionality for pool and execution metrics.
 *
 * @since v2.15.0 (Phase 3 Week 1-2)
 */
public class PrometheusExporterTest {

    @Test
    public void testExportMetrics_WithPoolStatsOnly() {
        // Create pool stats
        Python3ProcessPool.PoolStats poolStats = new Python3ProcessPool.PoolStats(5, 3, 2, 5);

        // Export metrics (no MetricsCollector)
        String output = PrometheusExporter.exportMetrics(poolStats, null);

        // Verify Prometheus format
        assertNotNull(output);
        assertTrue(output.contains("# HELP python3_pool_size_total"));
        assertTrue(output.contains("# TYPE python3_pool_size_total gauge"));
        assertTrue(output.contains("python3_pool_size_total 5"));
        assertTrue(output.contains("python3_pool_available 3"));
        assertTrue(output.contains("python3_pool_in_use 2"));
        assertTrue(output.contains("python3_pool_healthy_executors 5"));
        assertTrue(output.contains("python3_pool_unhealthy_executors 0"));
        assertTrue(output.contains("python3_pool_utilization_percent 40.00"));
    }

    @Test
    public void testExportMetrics_WithMetricsCollector() {
        // Create pool stats
        Python3ProcessPool.PoolStats poolStats = new Python3ProcessPool.PoolStats(5, 3, 2, 5);

        // Create metrics collector
        MetricsCollector collector = new MetricsCollector();

        // Record some metrics
        collector.recordExecution(true, 100L);
        collector.recordExecution(true, 150L);
        collector.recordExecution(false, 200L);

        // Export metrics
        String output = PrometheusExporter.exportMetrics(poolStats, collector);

        // Verify pool metrics
        assertTrue(output.contains("python3_pool_size_total 5"));

        // Verify execution metrics
        assertTrue(output.contains("# HELP python3_executions_total"));
        assertTrue(output.contains("# TYPE python3_executions_total counter"));
        assertTrue(output.contains("python3_executions_total 3"));
        assertTrue(output.contains("python3_executions_success_total 2"));
        assertTrue(output.contains("python3_executions_failed_total 1"));
        assertTrue(output.contains("python3_error_rate_percent"));
        assertTrue(output.contains("python3_response_time_ms_avg"));
        assertTrue(output.contains("python3_response_time_ms_p50"));
        assertTrue(output.contains("python3_response_time_ms_p95"));
        assertTrue(output.contains("python3_response_time_ms_p99"));
        assertTrue(output.contains("python3_uptime_seconds"));
    }

    @Test
    public void testExportMetrics_UtilizationCalculation() {
        // Test 0% utilization
        Python3ProcessPool.PoolStats stats1 = new Python3ProcessPool.PoolStats(5, 5, 0, 5);
        String output1 = PrometheusExporter.exportMetrics(stats1, null);
        assertTrue(output1.contains("python3_pool_utilization_percent 0.00"));

        // Test 100% utilization
        Python3ProcessPool.PoolStats stats2 = new Python3ProcessPool.PoolStats(5, 0, 5, 5);
        String output2 = PrometheusExporter.exportMetrics(stats2, null);
        assertTrue(output2.contains("python3_pool_utilization_percent 100.00"));

        // Test 50% utilization
        Python3ProcessPool.PoolStats stats3 = new Python3ProcessPool.PoolStats(10, 5, 5, 10);
        String output3 = PrometheusExporter.exportMetrics(stats3, null);
        assertTrue(output3.contains("python3_pool_utilization_percent 50.00"));
    }

    @Test
    public void testExportMetrics_UnhealthyExecutors() {
        // Pool with 2 unhealthy executors
        Python3ProcessPool.PoolStats stats = new Python3ProcessPool.PoolStats(5, 3, 2, 3);
        String output = PrometheusExporter.exportMetrics(stats, null);

        assertTrue(output.contains("python3_pool_healthy_executors 3"));
        assertTrue(output.contains("python3_pool_unhealthy_executors 2"));
    }

    @Test
    public void testExportMetrics_EmptyPool() {
        // Empty pool
        Python3ProcessPool.PoolStats stats = new Python3ProcessPool.PoolStats(0, 0, 0, 0);
        String output = PrometheusExporter.exportMetrics(stats, null);

        assertTrue(output.contains("python3_pool_size_total 0"));
        assertTrue(output.contains("python3_pool_available 0"));
        assertTrue(output.contains("python3_pool_in_use 0"));
        assertTrue(output.contains("python3_pool_utilization_percent 0.00"));
    }

    @Test
    public void testExportMetrics_NullPoolStats() {
        // Null pool stats should not cause exception
        MetricsCollector collector = new MetricsCollector();
        collector.recordExecution(true, 100L);

        String output = PrometheusExporter.exportMetrics(null, collector);

        // Should still have execution metrics
        assertNotNull(output);
        assertTrue(output.contains("python3_executions_total 1"));

        // But no pool metrics
        assertFalse(output.contains("python3_pool_size_total"));
    }

    @Test
    public void testPrometheusFormatStructure() {
        Python3ProcessPool.PoolStats stats = new Python3ProcessPool.PoolStats(5, 3, 2, 5);
        String output = PrometheusExporter.exportMetrics(stats, null);

        // Verify each metric has HELP and TYPE
        String[] lines = output.split("\n");
        boolean foundHelp = false;
        boolean foundType = false;
        boolean foundValue = false;

        for (String line : lines) {
            if (line.startsWith("# HELP python3_pool_size_total")) {
                foundHelp = true;
            } else if (line.startsWith("# TYPE python3_pool_size_total")) {
                foundType = true;
            } else if (line.startsWith("python3_pool_size_total ")) {
                foundValue = true;
            }
        }

        assertTrue(foundHelp, "Should have HELP comment");
        assertTrue(foundType, "Should have TYPE comment");
        assertTrue(foundValue, "Should have metric value");
    }
}
