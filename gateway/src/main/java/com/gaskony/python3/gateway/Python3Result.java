package com.gaskony.python3.gateway;

/**
 * Result of a Python 3 execution.
 * <p>
 * Converted to Java 17 record in v2.10.0 (eliminated 28 lines of boilerplate).
 *
 * @param success Whether the execution succeeded
 * @param result The result value (null if failed)
 * @param error Error message (null if succeeded)
 * @param traceback Python stack trace (null if succeeded)
 * @param output What the script printed (null if nothing)
 *
 * @since v1.0.0 (as class)
 * @since v2.10.0 (as record)
 */
public record Python3Result(
    boolean success,
    Object result,
    String error,
    String traceback,
    String output
) {
    /**
     * Compact constructor with validation.
     */
    public Python3Result {
        // All fields can be null except success
    }

    public Python3Result(boolean success, Object result, String error, String traceback) {
        this(success, result, error, traceback, null);
    }

    /** Printed output, or null when the script printed nothing or printed only its result. */
    public String printedOutput() {
        if (output == null || output.isEmpty()) {
            return null;
        }
        // With no `result` variable the bridge returns the printed text as the result.
        return result != null && output.equals(result.toString()) ? null : output;
    }

    // Legacy getter methods for backward compatibility with existing code
    public boolean isSuccess() { return success; }
    public Object getResult() { return result; }
    public String getError() { return error; }
    public String getTraceback() { return traceback; }

    /**
     * Get result or throw exception if failed.
     * @return the result value
     * @throws Python3Exception if execution failed
     */
    public Object getResultOrThrow() throws Python3Exception {
        if (!success) {
            throw new Python3Exception(error, traceback);
        }
        return result;
    }

    @Override
    public String toString() {
        if (success) {
            return "Python3Result{success=true, result=" + result + "}";
        } else {
            return "Python3Result{success=false, error=" + error + "}";
        }
    }
}
