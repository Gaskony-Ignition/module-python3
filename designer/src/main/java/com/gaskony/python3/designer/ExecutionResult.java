package com.gaskony.python3.designer;

/**
 * Represents the result of a Python code execution via REST API.
 * This class models the JSON response from the Gateway's REST API endpoints.
 * <p>
 * Converted to Java 17 record in v2.10.0 (eliminated 49 lines of boilerplate).
 *
 * <p>Example JSON response:</p>
 * <pre>{
 *   "success": true,
 *   "result": "42",
 *   "executionTimeMs": 15,
 *   "timestamp": 1760571024031,
 *   "error": null
 * }</pre>
 *
 * @param success true if execution succeeded
 * @param result the result value as a string (null if failed)
 * @param error the error message (null if succeeded)
 * @param executionTimeMs execution time in milliseconds (optional)
 * @param timestamp execution timestamp (optional)
 *
 * @since v1.0.0 (as class)
 * @since v2.10.0 (as record)
 */
public record ExecutionResult(
    boolean success,
    String result,
    String error,
    Long executionTimeMs,
    Long timestamp
) {
    /**
     * Compact constructor with validation.
     */
    public ExecutionResult {
        // All fields can be null except success
    }

    /**
     * Constructor for successful execution.
     *
     * @param success true if execution succeeded
     * @param result the result value as a string
     * @param executionTimeMs execution time in milliseconds (optional)
     * @param timestamp execution timestamp (optional)
     */
    public ExecutionResult(boolean success, String result, Long executionTimeMs, Long timestamp) {
        this(success, result, null, executionTimeMs, timestamp);
    }

    /**
     * Constructor for failed execution.
     *
     * @param success false for failed execution
     * @param error the error message
     */
    public ExecutionResult(boolean success, String error) {
        this(success, null, error, null, null);
    }

    // Legacy getter methods for backward compatibility with existing code
    public boolean isSuccess() { return success; }
    public String getResult() { return result; }
    public String getError() { return error; }
    public Long getExecutionTimeMs() { return executionTimeMs; }
    public Long getTimestamp() { return timestamp; }

    @Override
    public String toString() {
        if (success) {
            return String.format("ExecutionResult{success=true, result='%s', time=%dms}",
                    result, executionTimeMs != null ? executionTimeMs : 0);
        } else {
            return String.format("ExecutionResult{success=false, error='%s'}", error);
        }
    }
}
