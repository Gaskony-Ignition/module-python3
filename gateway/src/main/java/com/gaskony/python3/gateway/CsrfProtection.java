package com.gaskony.python3.gateway;

import com.inductiveautomation.ignition.gateway.dataroutes.RequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CSRF token generation, validation, and cleanup.
 *
 * Instance-based (not static) so it can be independently tested without Ignition SDK.
 * A single instance is held as a static field in Python3RestEndpoints and delegated to.
 *
 * v3.7.1: Extracted from Python3RestEndpoints for independent testability.
 */
class CsrfProtection {

    private static final Logger logger = LoggerFactory.getLogger(CsrfProtection.class);

    // CSRF token store: sessionId → token
    final Map<String, String> csrfTokens = new ConcurrentHashMap<>();
    // CSRF token timestamps for expiry: sessionId → creation time (ms)
    final Map<String, Long> csrfTokenTimestamps = new ConcurrentHashMap<>();

    static final long CSRF_TOKEN_EXPIRY_MS = 28800000L; // 8 hours (match session token expiry)

    /**
     * Generate a CSRF token for the given session ID.
     * Performs lazy cleanup of expired tokens before creating the new one.
     *
     * v1.17.0: CSRF protection for state-changing operations
     * v2.15.9: Added timestamp tracking and lazy cleanup to prevent memory leak
     */
    String generateToken(String sessionId) {
        cleanupExpired();

        String token = java.util.UUID.randomUUID().toString();
        csrfTokens.put(sessionId, token);
        csrfTokenTimestamps.put(sessionId, System.currentTimeMillis());
        return token;
    }

    /**
     * Validate the CSRF token in the request against the stored token for the session.
     *
     * v1.17.0: CSRF protection
     * v2.15.9: Now called from all state-changing endpoints
     */
    boolean validateToken(RequestContext req) {
        try {
            String sessionId = req.getRequest().getSession(false) != null ?
                    req.getRequest().getSession(false).getId() : null;

            if (sessionId == null) {
                logger.warn("CSRF validation failed: no session");
                return false;
            }

            String providedToken = req.getRequest().getHeader("X-CSRF-Token");
            if (providedToken == null || providedToken.trim().isEmpty()) {
                logger.warn("CSRF validation failed: no token provided");
                return false;
            }

            String expectedToken = csrfTokens.get(sessionId);
            if (expectedToken == null) {
                logger.warn("CSRF validation failed: no token for session");
                return false;
            }

            boolean valid = secureEquals(providedToken, expectedToken);
            if (!valid) {
                logger.warn("CSRF validation failed: token mismatch");
            }

            return valid;

        } catch (Exception e) {
            logger.error("CSRF validation error", e);
            return false;
        }
    }

    /**
     * Validate CSRF token only if the request is a browser session request.
     * Skips validation for Bearer token authenticated requests (Designer client).
     *
     * v2.15.9: Added for conditional CSRF validation
     * v3.5.3: Skip CSRF for requests with valid Bearer token (Designer client)
     * v3.8.2: Removed X-Source header bypass (CSRF bypass vulnerability)
     */
    void validateIfSession(RequestContext req) {
        // Skip CSRF for requests authenticated via Bearer token (Designer REST client)
        String authHeader = req.getRequest().getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return;
        }

        // Require CSRF for browser-based session requests (Gateway Web UI)
        if (req.getRequest().getSession(false) != null) {
            if (!validateToken(req)) {
                throw new SecurityException("CSRF token validation failed. Include X-CSRF-Token header.");
            }
        }
    }

    /**
     * Remove expired CSRF tokens to prevent memory leak.
     * v2.15.9: Memory leak fix
     */
    void cleanupExpired() {
        long now = System.currentTimeMillis();
        csrfTokenTimestamps.entrySet().removeIf(entry -> {
            if (now - entry.getValue() > CSRF_TOKEN_EXPIRY_MS) {
                csrfTokens.remove(entry.getKey());
                return true;
            }
            return false;
        });
    }

    /**
     * Constant-time string comparison to prevent timing attacks.
     * Used for comparing CSRF tokens and sensitive tokens.
     *
     * v1.17.0: Security enhancement - prevents timing-based credential enumeration
     * v2.15.9: Fixed timing leak in length comparison
     */
    static boolean secureEquals(String a, String b) {
        if (a == null || b == null) {
            return a == b;
        }

        // Constant-time length comparison (v2.15.9 fix)
        int lengthA = a.length();
        int lengthB = b.length();
        int result = lengthA ^ lengthB;  // Will be non-zero if lengths differ

        // Always compare up to the minimum length to avoid timing leaks
        int minLength = Math.min(lengthA, lengthB);
        for (int i = 0; i < minLength; i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }

        // XOR with dummy value for remaining bytes if lengths differ
        // This ensures timing is constant regardless of length difference
        for (int i = minLength; i < Math.max(lengthA, lengthB); i++) {
            result |= 0xFF;  // Ensure result is non-zero if lengths differ
        }

        return result == 0;
    }
}
