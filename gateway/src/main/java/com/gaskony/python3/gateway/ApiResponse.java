package com.gaskony.python3.gateway;

import com.inductiveautomation.ignition.common.gson.JsonObject;

/**
 * Factory methods for building standard JSON API response objects.
 *
 * <p>Centralises the success/error response pattern used throughout
 * {@link Python3RestEndpoints} so every handler returns consistent JSON.</p>
 *
 * <p>Usage:</p>
 * <pre>
 *   // Success with no body
 *   return ApiResponse.success();
 *
 *   // Success with a result value
 *   return ApiResponse.success(result.toString());
 *
 *   // Error
 *   return ApiResponse.error(e.getMessage());
 * </pre>
 */
public final class ApiResponse {

    private ApiResponse() {
        // Utility class — no instances
    }

    // -------------------------------------------------------------------------
    // Success responses
    // -------------------------------------------------------------------------

    /**
     * Returns {@code {"success": true}} with no additional fields.
     */
    public static JsonObject success() {
        JsonObject r = new JsonObject();
        r.addProperty("success", true);
        return r;
    }

    /**
     * Returns {@code {"success": true, "result": value}}.
     *
     * @param result string result value (may be {@code null})
     */
    public static JsonObject success(String result) {
        JsonObject r = new JsonObject();
        r.addProperty("success", true);
        r.addProperty("result", result);
        return r;
    }

    /**
     * Returns {@code {"success": true, "result": value}}.
     *
     * @param result numeric result value
     */
    public static JsonObject success(Number result) {
        JsonObject r = new JsonObject();
        r.addProperty("success", true);
        r.addProperty("result", result);
        return r;
    }

    /**
     * Returns {@code {"success": true, "result": value}}.
     *
     * @param result boolean result value
     */
    public static JsonObject success(Boolean result) {
        JsonObject r = new JsonObject();
        r.addProperty("success", true);
        r.addProperty("result", result);
        return r;
    }

    // -------------------------------------------------------------------------
    // Error responses
    // -------------------------------------------------------------------------

    /**
     * Returns {@code {"success": false, "error": message}}.
     *
     * @param message human-readable error description (may be {@code null})
     */
    public static JsonObject error(String message) {
        JsonObject r = new JsonObject();
        r.addProperty("success", false);
        r.addProperty("error", message);
        return r;
    }

    /**
     * Returns {@code {"success": false, "error": e.getMessage()}}.
     * Convenience overload that extracts the message from a throwable.
     *
     * @param e the throwable whose message is used
     */
    public static JsonObject error(Throwable e) {
        return error(e != null ? e.getMessage() : "Unknown error");
    }
}
