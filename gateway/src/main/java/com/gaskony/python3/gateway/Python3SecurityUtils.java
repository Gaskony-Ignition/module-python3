package com.gaskony.python3.gateway;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Security utilities for Python code execution.
 * Provides code hashing and other security-related helper functions.
 *
 * @since v2.6.0
 */
public class Python3SecurityUtils {

    /**
     * Compute SHA-256 hash of Python code for audit logging.
     * <p>
     * Hash is used instead of storing full code to:
     * - Protect sensitive information in code
     * - Reduce audit log size
     * - Enable duplicate detection
     *
     * @param code The Python code to hash
     * @return Hex-encoded SHA-256 hash (64 characters)
     */
    public static String hashCode(String code) {
        if (code == null) {
            code = "";
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(code.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 should always be available
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Convert byte array to hex string.
     *
     * @param bytes The bytes to convert
     * @return Hex-encoded string (lowercase)
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder(2 * bytes.length);
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    /**
     * Extract username from request context.
     * Returns null if unauthenticated.
     *
     * @return Username or null
     */
    public static String getCurrentUser() {
        // TODO: Implement when Ignition context API is integrated
        // For now, return null (unauthenticated)
        return null;
    }

    /**
     * Extract source IP address from request context.
     * Returns null if not available (e.g., local Script Console).
     *
     * @return IP address or null
     */
    public static String getSourceIP() {
        // TODO: Implement when REST API integration is complete
        // For now, return null (local execution)
        return null;
    }

    /**
     * Determine endpoint that triggered execution.
     *
     * @param source The source of execution (e.g., "REST", "SCRIPT", "DESIGNER")
     * @param method The method called (e.g., "exec", "eval", "callModule")
     * @return Endpoint identifier (e.g., "REST:/api/v1/exec")
     */
    public static String getEndpoint(String source, String method) {
        if (source == null) {
            source = "UNKNOWN";
        }
        if (method == null) {
            method = "unknown";
        }
        return source + ":" + method;
    }
}
