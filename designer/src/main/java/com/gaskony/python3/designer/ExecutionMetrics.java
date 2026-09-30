package com.gaskony.python3.designer;

import com.inductiveautomation.ignition.common.gson.JsonObject;
import com.inductiveautomation.ignition.common.gson.JsonParser;

/**
 * Represents execution metrics from the Gateway diagnostics endpoint.
 * <p>
 * Converted to Java 17 record in v2.10.0 (eliminated 39 lines of boilerplate).
 *
 * @param totalExecutions Total number of executions
 * @param successfulExecutions Number of successful executions
 * @param failedExecutions Number of failed executions
 * @param averageExecutionTime Average execution time in milliseconds
 * @param successRate Success rate percentage (0-100)
 *
 * @since v2.0.8 (as class)
 * @since v2.10.0 (as record)
 */
public record ExecutionMetrics(
    long totalExecutions,
    long successfulExecutions,
    long failedExecutions,
    double averageExecutionTime,
    double successRate
) {
    /**
     * Compact constructor with validation.
     */
    public ExecutionMetrics {
        // All fields are primitives, no null checks needed
    }

    /**
     * Creates ExecutionMetrics from JSON response with calculated success rate.
     *
     * @param json the JSON object from diagnostics endpoint
     */
    public ExecutionMetrics(JsonObject json) {
        this(
            json.has("totalExecutions") ? json.get("totalExecutions").getAsLong() : 0,
            json.has("successfulExecutions") ? json.get("successfulExecutions").getAsLong() : 0,
            json.has("failedExecutions") ? json.get("failedExecutions").getAsLong() : 0,
            json.has("averageExecutionTime") ? json.get("averageExecutionTime").getAsDouble() : 0.0,
            calculateSuccessRate(
                json.has("totalExecutions") ? json.get("totalExecutions").getAsLong() : 0,
                json.has("successfulExecutions") ? json.get("successfulExecutions").getAsLong() : 0
            )
        );
    }

    /**
     * Helper method to calculate success rate.
     */
    private static double calculateSuccessRate(long total, long successful) {
        if (total > 0) {
            return (successful * 100.0) / total;
        } else {
            return 0.0;
        }
    }

    /**
     * Parses JSON string to create ExecutionMetrics.
     *
     * @param jsonString the JSON string from diagnostics endpoint
     * @return ExecutionMetrics instance
     */
    public static ExecutionMetrics fromJson(String jsonString) {
        JsonObject json = JsonParser.parseString(jsonString).getAsJsonObject();
        return new ExecutionMetrics(json);
    }

    // Legacy getter methods for backward compatibility with existing code
    public long getTotalExecutions() {
        return totalExecutions;
    }

    public long getSuccessfulExecutions() {
        return successfulExecutions;
    }

    public long getFailedExecutions() {
        return failedExecutions;
    }

    public double getAverageExecutionTime() {
        return averageExecutionTime;
    }

    public double getSuccessRate() {
        return successRate;
    }

    @Override
    public String toString() {
        return String.format("ExecutionMetrics{total=%d, successful=%d, failed=%d, avgTime=%.2fms, successRate=%.1f%%}",
                totalExecutions, successfulExecutions, failedExecutions, averageExecutionTime, successRate);
    }
}
