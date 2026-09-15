package com.gaskony.python3.gateway;

import java.util.Map;

/**
 * Prometheus Exporter - Converts metrics to Prometheus text format.
 *
 * Prometheus expects metrics in this format:
 * <pre>
 * # HELP metric_name Description of the metric
 * # TYPE metric_name gauge|counter|histogram|summary
 * metric_name{label1="value1",label2="value2"} value timestamp
 * </pre>
 *
 * Supported metric types:
 * - gauge: Value that can go up or down (e.g., pool size, memory usage)
 * - counter: Value that only increases (e.g., total executions, total errors)
 * - histogram: Distribution of values (e.g., response times)
 * - summary: Similar to histogram but with quantiles
 *
 * @since v2.15.0 (Phase 3 Week 1-2: Advanced Monitoring)
 */
public class PrometheusExporter {

    private static final String METRIC_PREFIX = "python3_";

    /**
     * Export all metrics in Prometheus text format.
     *
     * @param poolStats Pool statistics
     * @param metricsCollector Metrics collector with execution stats
     * @return Prometheus-formatted metrics as plain text
     */
    public static String exportMetrics(Python3ProcessPool.PoolStats poolStats,
                                      MetricsCollector metricsCollector) {
        StringBuilder sb = new StringBuilder();

        // Pool metrics
        if (poolStats != null) {
            exportPoolMetrics(sb, poolStats);
        }

        // Execution metrics
        if (metricsCollector != null) {
            exportExecutionMetrics(sb, metricsCollector);
        }

        return sb.toString();
    }

    /**
     * Export pool-related metrics.
     */
    private static void exportPoolMetrics(StringBuilder sb, Python3ProcessPool.PoolStats stats) {
        // Pool size (gauge)
        sb.append("# HELP python3_pool_size_total Total number of executors in the pool\n");
        sb.append("# TYPE python3_pool_size_total gauge\n");
        sb.append("python3_pool_size_total ").append(stats.totalSize).append("\n");
        sb.append("\n");

        // Available executors (gauge)
        sb.append("# HELP python3_pool_available Number of available executors\n");
        sb.append("# TYPE python3_pool_available gauge\n");
        sb.append("python3_pool_available ").append(stats.available).append("\n");
        sb.append("\n");

        // In-use executors (gauge)
        sb.append("# HELP python3_pool_in_use Number of executors currently in use\n");
        sb.append("# TYPE python3_pool_in_use gauge\n");
        sb.append("python3_pool_in_use ").append(stats.inUse).append("\n");
        sb.append("\n");

        // Pool utilization (gauge, 0-100%)
        double utilization = stats.totalSize > 0 ?
            (stats.inUse * 100.0 / stats.totalSize) : 0.0;
        sb.append("# HELP python3_pool_utilization_percent Pool utilization percentage\n");
        sb.append("# TYPE python3_pool_utilization_percent gauge\n");
        sb.append(String.format("python3_pool_utilization_percent %.2f\n", utilization));
        sb.append("\n");

        // Healthy executors (gauge)
        sb.append("# HELP python3_pool_healthy_executors Number of healthy executors\n");
        sb.append("# TYPE python3_pool_healthy_executors gauge\n");
        sb.append("python3_pool_healthy_executors ").append(stats.healthy).append("\n");
        sb.append("\n");

        // Unhealthy executors (gauge)
        int unhealthy = stats.totalSize - stats.healthy;
        sb.append("# HELP python3_pool_unhealthy_executors Number of unhealthy executors\n");
        sb.append("# TYPE python3_pool_unhealthy_executors gauge\n");
        sb.append("python3_pool_unhealthy_executors ").append(unhealthy).append("\n");
        sb.append("\n");
    }

    /**
     * Export execution-related metrics.
     */
    private static void exportExecutionMetrics(StringBuilder sb, MetricsCollector collector) {
        // Total executions (counter)
        sb.append("# HELP python3_executions_total Total number of Python executions\n");
        sb.append("# TYPE python3_executions_total counter\n");
        sb.append("python3_executions_total ")
            .append(collector.getTotalExecutions()).append("\n");
        sb.append("\n");

        // Successful executions (counter)
        sb.append("# HELP python3_executions_success_total Total number of successful executions\n");
        sb.append("# TYPE python3_executions_success_total counter\n");
        sb.append("python3_executions_success_total ")
            .append(collector.getSuccessfulExecutions()).append("\n");
        sb.append("\n");

        // Failed executions (counter)
        sb.append("# HELP python3_executions_failed_total Total number of failed executions\n");
        sb.append("# TYPE python3_executions_failed_total counter\n");
        sb.append("python3_executions_failed_total ")
            .append(collector.getFailedExecutions()).append("\n");
        sb.append("\n");

        // Error rate (gauge, 0-100%)
        sb.append("# HELP python3_error_rate_percent Percentage of failed executions\n");
        sb.append("# TYPE python3_error_rate_percent gauge\n");
        sb.append(String.format("python3_error_rate_percent %.2f\n", collector.getErrorRate()));
        sb.append("\n");

        // Average response time (gauge, milliseconds)
        sb.append("# HELP python3_response_time_ms_avg Average execution response time in milliseconds\n");
        sb.append("# TYPE python3_response_time_ms_avg gauge\n");
        sb.append(String.format("python3_response_time_ms_avg %.2f\n", (double) collector.getAverageResponseTime()));
        sb.append("\n");

        // Response time percentiles (gauge, milliseconds)
        sb.append("# HELP python3_response_time_ms_p50 50th percentile response time in milliseconds\n");
        sb.append("# TYPE python3_response_time_ms_p50 gauge\n");
        sb.append(String.format("python3_response_time_ms_p50 %.2f\n", (double) collector.getP50ResponseTime()));
        sb.append("\n");

        sb.append("# HELP python3_response_time_ms_p95 95th percentile response time in milliseconds\n");
        sb.append("# TYPE python3_response_time_ms_p95 gauge\n");
        sb.append(String.format("python3_response_time_ms_p95 %.2f\n", (double) collector.getP95ResponseTime()));
        sb.append("\n");

        sb.append("# HELP python3_response_time_ms_p99 99th percentile response time in milliseconds\n");
        sb.append("# TYPE python3_response_time_ms_p99 gauge\n");
        sb.append(String.format("python3_response_time_ms_p99 %.2f\n", (double) collector.getP99ResponseTime()));
        sb.append("\n");

        // Uptime and timing metrics
        long uptimeSeconds = collector.getUptimeMs() / 1000;
        sb.append("# HELP python3_uptime_seconds Time since metrics collector started\n");
        sb.append("# TYPE python3_uptime_seconds counter\n");
        sb.append("python3_uptime_seconds ").append(uptimeSeconds).append("\n");
        sb.append("\n");
    }

}
