package com.gaskony.python3.gateway;

import com.inductiveautomation.ignition.gateway.dataroutes.RequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Security service for Python 3 code execution.
 * Handles authentication, authorization, and security mode determination.
 *
 * Security Model:
 * - DESIGNER_ADMIN: Designer IDE users (trusted, authenticated via Designer login)
 * - ADMIN: REST API users with admin key (requires HTTPS)
 * - RESTRICTED: Unauthenticated REST API users (safe modules only)
 *
 * @since v2.6.0
 */
public class Python3SecurityService {
    private static final Logger logger = LoggerFactory.getLogger(Python3SecurityService.class);

    private final GatewayHook gatewayHook;
    private final Map<String, ApiToken> activeTokens = new ConcurrentHashMap<>();
    private String adminApiKey;
    private final SecureRandom secureRandom = new SecureRandom();
    private byte[] tokenSigningKey;

    /**
     * API token data structure.
     */
    private static class ApiToken {
        final String token;
        final SecurityMode securityMode;
        final Instant createdAt;
        final Instant expiresAt;

        ApiToken(String token, SecurityMode securityMode, Instant createdAt, Instant expiresAt) {
            this.token = token;
            this.securityMode = securityMode;
            this.createdAt = createdAt;
            this.expiresAt = expiresAt;
        }

        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    public Python3SecurityService(GatewayHook gatewayHook) {
        this.gatewayHook = gatewayHook;
        loadConfiguration();
    }

    /**
     * Load security configuration from system properties.
     */
    private void loadConfiguration() {
        adminApiKey = System.getProperty("ignition.python3.admin.apikey");

        if (adminApiKey != null) {
            // v2.15.9: Strengthened validation - minimum 64 chars, entropy check
            if (adminApiKey.length() < 64) {
                logger.error("CRITICAL SECURITY ERROR: Admin API key is too short.");
                logger.error("Minimum 64 characters required for production use.");
                logger.error("Generate a secure key: openssl rand -hex 32");
                throw new IllegalStateException(
                    "Admin API key must be at least 64 characters for production security."
                );
            }

            // Check for minimum complexity/entropy (v2.15.9)
            if (!hasMinimumEntropy(adminApiKey)) {
                logger.error("CRITICAL SECURITY ERROR: Admin API key has insufficient entropy.");
                logger.error("Key must contain mix of uppercase, lowercase, numbers, and symbols.");
                throw new IllegalStateException(
                    "Admin API key must have sufficient complexity (mixed case, numbers, symbols)."
                );
            }

            logger.info("Admin API key configured (meets security requirements)");
            logger.info("ADMIN mode available via: Authorization: Bearer <admin-key>");
            logger.warn("ADMIN mode should ONLY be used over HTTPS!");
            logger.warn("Rotate API keys regularly (recommended: every 90 days)");
        } else {
            logger.info("No admin API key configured. ADMIN mode unavailable via REST API.");
            logger.info("Designer IDE users can obtain session tokens via /auth/session endpoint.");
        }

        // v2.15.9: Load or generate persistent signing key for session tokens
        loadOrGenerateSigningKey();
    }

    /**
     * Load HMAC signing key from file, or generate and save a new one.
     * Persisting the key prevents session token invalidation on Gateway restart.
     *
     * Key storage location: <user.home>/.ignition-python3/hmac-signing.key
     *
     * v2.15.9: Persistent signing key for production reliability
     */
    private void loadOrGenerateSigningKey() {
        try {
            // Determine key storage location
            String homeDir = System.getProperty("user.home", ".");
            Path keyDir = Paths.get(homeDir, ".ignition-python3");
            Path keyFile = keyDir.resolve("hmac-signing.key");

            // Create directory if it doesn't exist
            if (!Files.exists(keyDir)) {
                Files.createDirectories(keyDir);
                logger.info("Created directory for security keys: {}", keyDir);
            }

            // Try to load existing key
            if (Files.exists(keyFile)) {
                try {
                    String encodedKey = new String(Files.readAllBytes(keyFile), StandardCharsets.UTF_8).trim();
                    tokenSigningKey = Base64.getDecoder().decode(encodedKey);

                    if (tokenSigningKey.length == 32) {
                        logger.info("Loaded persisted session token signing key from: {}", keyFile);
                        logger.info("Session tokens will remain valid across Gateway restarts");
                        return;
                    } else {
                        logger.warn("Persisted signing key has invalid length ({}), regenerating", tokenSigningKey.length);
                    }
                } catch (Exception e) {
                    logger.error("Failed to load persisted signing key, regenerating: {}", e.getMessage());
                }
            }

            // Generate new key
            tokenSigningKey = new byte[32]; // 256-bit key
            secureRandom.nextBytes(tokenSigningKey);
            logger.info("Generated new session token signing key (256-bit)");

            // Save key to file for persistence
            try {
                String encodedKey = Base64.getEncoder().encodeToString(tokenSigningKey);
                Files.write(keyFile, encodedKey.getBytes(StandardCharsets.UTF_8));

                // Set file permissions to owner-only (Unix systems)
                try {
                    keyFile.toFile().setReadable(false, false);  // Remove all read perms
                    keyFile.toFile().setWritable(false, false);  // Remove all write perms
                    keyFile.toFile().setReadable(true, true);    // Owner read only
                    keyFile.toFile().setWritable(true, true);    // Owner write only
                } catch (Exception e) {
                    logger.warn("Could not set restrictive file permissions on signing key: {}", e.getMessage());
                }

                logger.info("Persisted session token signing key to: {}", keyFile);
                logger.info("Session tokens will remain valid across Gateway restarts");
            } catch (IOException e) {
                logger.error("Failed to persist signing key to file: {}", e.getMessage());
                logger.warn("Session tokens will be invalidated on Gateway restart");
            }

        } catch (Exception e) {
            // Fallback: generate non-persistent key
            logger.error("Failed to load/save persistent signing key: {}", e.getMessage());
            tokenSigningKey = new byte[32];
            secureRandom.nextBytes(tokenSigningKey);
            logger.warn("Using non-persistent signing key - session tokens will be invalidated on restart");
        }
    }

    /**
     * Determine security mode for a request.
     * <p>
     * Decision flow (post-C13, May 2026):
     * <ol>
     *   <li>Valid session token (Designer IDE / API) → token's security mode</li>
     *   <li>Admin API key in {@code Authorization: Bearer ...} → ADMIN</li>
     *   <li>Legacy {@code X-Python3-Admin-Key} → ADMIN</li>
     *   <li>No authentication / invalid token → throws {@link SecurityException}</li>
     * </ol>
     * <p>The previous "fall through to RESTRICTED" branch was removed when
     * RESTRICTED was deleted (security review C13). Callers that previously
     * silently demoted to RESTRICTED must now handle the SecurityException as
     * "401 Unauthorized" / "403 Forbidden".
     *
     * @param req The request context
     * @return The security mode to use
     * @throws SecurityException if no valid authentication is presented
     */
    public SecurityMode determineSecurityMode(RequestContext req) {
        // 1. Check for Bearer token (session tokens or admin API key)
        String authHeader = req.getRequest().getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                return validateApiToken(token);
            } catch (SecurityException e) {
                logger.warn("Invalid API token: {}", e.getMessage());
                // Fall through to legacy admin-key check, then deny.
            }
        }

        // 2. Legacy support: Check X-Python3-Admin-Key header
        String adminKeyHeader = req.getRequest().getHeader("X-Python3-Admin-Key");
        if (adminKeyHeader != null && isValidAdminKey(adminKeyHeader)) {
            logger.debug("Valid admin key provided via X-Python3-Admin-Key header - granting ADMIN mode");
            return SecurityMode.ADMIN;
        }

        // 3. No authentication — deny. (The previous RESTRICTED fallback was
        // removed in C13 because the underlying sandbox was bypassable.)
        logger.debug("No valid authentication provided — denying access");
        throw new SecurityException(
            "Authentication required to execute Python");
    }

    /**
     * Validate API token for REST API access.
     * Supports both session tokens (with HMAC signature) and admin API keys.
     *
     * @param token The API token to validate
     * @return SecurityMode granted (RESTRICTED, ADMIN, or DESIGNER_ADMIN)
     * @throws SecurityException if token is invalid
     */
    public SecurityMode validateApiToken(String token) throws SecurityException {
        if (token == null || token.trim().isEmpty()) {
            throw new SecurityException("API token required");
        }

        // Check if token is the admin API key (simple string comparison)
        if (isValidAdminKey(token)) {
            logger.debug("Admin API key validated - granting ADMIN mode");
            return SecurityMode.ADMIN;
        }

        // Check if token has signature format (payload.signature)
        if (token.contains(".")) {
            return validateSignedToken(token);
        }

        throw new SecurityException("Invalid API token format");
    }

    /**
     * Validate a signed session token with HMAC verification.
     *
     * @param token The signed token (payload.signature)
     * @return SecurityMode granted by the token
     * @throws SecurityException if token is invalid or expired
     */
    private SecurityMode validateSignedToken(String token) throws SecurityException {
        try {
            // Split token into payload and signature
            String[] parts = token.split("\\.", 2);
            if (parts.length != 2) {
                throw new SecurityException("Invalid token format");
            }

            String payload = parts[0];
            String providedSignature = parts[1];

            // Verify HMAC signature
            Mac hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(tokenSigningKey, "HmacSHA256");
            hmac.init(secretKey);
            byte[] expectedSignatureBytes = hmac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String expectedSignature = Base64.getEncoder().encodeToString(expectedSignatureBytes);

            // Constant-time comparison to prevent timing attacks
            if (!MessageDigest.isEqual(
                    providedSignature.getBytes(StandardCharsets.UTF_8),
                    expectedSignature.getBytes(StandardCharsets.UTF_8))) {
                throw new SecurityException("Invalid token signature");
            }

            // Parse payload to extract security mode
            String[] payloadParts = payload.split("\\|", 3);
            if (payloadParts.length != 3) {
                throw new SecurityException("Invalid token payload");
            }

            long timestamp = Long.parseLong(payloadParts[0]);
            String securityModeStr = payloadParts[1];

            // Check if token exists in active tokens and is not expired
            ApiToken apiToken = activeTokens.get(token);
            if (apiToken == null) {
                throw new SecurityException("Token not found or has been revoked");
            }

            if (apiToken.isExpired()) {
                activeTokens.remove(token);
                throw new SecurityException("Token has expired");
            }

            logger.debug("Signed token validated - granting {} mode", apiToken.securityMode);
            return apiToken.securityMode;

        } catch (NumberFormatException e) {
            throw new SecurityException("Invalid token timestamp");
        } catch (Exception e) {
            logger.error("Token validation failed", e);
            throw new SecurityException("Token validation failed: " + e.getMessage());
        }
    }

    /**
     * Check if provided key matches the configured admin API key.
     * Uses constant-time comparison to prevent timing attacks.
     *
     * @param providedKey The key to validate
     * @return true if valid admin key
     */
    private boolean isValidAdminKey(String providedKey) {
        if (adminApiKey == null || providedKey == null) {
            return false;
        }

        // Constant-time comparison to prevent timing attacks
        try {
            return MessageDigest.isEqual(
                providedKey.getBytes(StandardCharsets.UTF_8),
                adminApiKey.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            logger.error("Error validating admin key", e);
            return false;
        }
    }

    /**
     * Generate a new session token with HMAC signature.
     * <p>
     * Tokens are cryptographically secure and include:
     * - 128 bits of random data
     * - HMAC-SHA256 signature
     * - Base64 encoding
     *
     * @param securityMode The security mode to grant
     * @param durationSeconds Token lifetime in seconds
     * @return The generated signed token
     */
    public String generateApiToken(SecurityMode securityMode, long durationSeconds) {
        // Generate cryptographically secure random token (16 bytes = 128 bits)
        byte[] randomBytes = new byte[16];
        secureRandom.nextBytes(randomBytes);

        // Create token payload: timestamp|securityMode|randomData
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(durationSeconds);
        long timestamp = now.toEpochMilli();

        String payload = timestamp + "|" + securityMode.name() + "|" + Base64.getEncoder().encodeToString(randomBytes);

        // Generate HMAC signature
        String signature;
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(tokenSigningKey, "HmacSHA256");
            hmac.init(secretKey);
            byte[] signatureBytes = hmac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            signature = Base64.getEncoder().encodeToString(signatureBytes);
        } catch (Exception e) {
            logger.error("Failed to generate HMAC signature", e);
            throw new RuntimeException("Token generation failed", e);
        }

        // Final token format: payload.signature (similar to JWT)
        String token = payload + "." + signature;

        // Store token metadata
        ApiToken apiToken = new ApiToken(token, securityMode, now, expiresAt);
        activeTokens.put(token, apiToken);

        logger.info("Generated signed session token with {} mode (expires in {} seconds)", securityMode, durationSeconds);

        return token;
    }

    /**
     * Revoke an API token.
     *
     * @param token The token to revoke
     * @return true if token was revoked, false if not found
     */
    public boolean revokeApiToken(String token) {
        ApiToken removed = activeTokens.remove(token);
        if (removed != null) {
            logger.info("Revoked API token with {} mode", removed.securityMode);
            return true;
        }
        return false;
    }

    /**
     * Check if HTTPS is required for the given security mode.
     *
     * @param securityMode The security mode
     * @param req The request context
     * @throws SecurityException if HTTPS is required but not used
     */
    public void enforceHttpsRequirement(SecurityMode securityMode, RequestContext req) throws SecurityException {
        // ADMIN mode requires HTTPS (unless disabled for development)
        if (securityMode == SecurityMode.ADMIN) {
            boolean requireHttps = Boolean.parseBoolean(
                System.getProperty("ignition.python3.admin.requirehttps", "true")
            );

            if (requireHttps && !req.getRequest().isSecure()) {
                throw new SecurityException(
                    "ADMIN mode requires HTTPS. Use 'https://' or disable with -Dignition.python3.admin.requirehttps=false (NOT recommended for production)"
                );
            }
        }
    }

    /**
     * Check if API key has minimum entropy/complexity.
     * v2.15.9: Added for stronger API key validation
     *
     * @param key The API key to validate
     * @return true if key has sufficient entropy
     */
    private boolean hasMinimumEntropy(String key) {
        if (key == null || key.length() < 64) {
            return false;
        }

        boolean hasUppercase = false;
        boolean hasLowercase = false;
        boolean hasDigit = false;
        boolean hasSpecial = false;

        for (char c : key.toCharArray()) {
            if (Character.isUpperCase(c)) hasUppercase = true;
            else if (Character.isLowerCase(c)) hasLowercase = true;
            else if (Character.isDigit(c)) hasDigit = true;
            else if (!Character.isLetterOrDigit(c)) hasSpecial = true;
        }

        // Require at least 3 of 4 character types
        int types = (hasUppercase ? 1 : 0) + (hasLowercase ? 1 : 0) +
                   (hasDigit ? 1 : 0) + (hasSpecial ? 1 : 0);

        return types >= 3;
    }

    /**
     * Get the admin API key (for testing purposes only).
     * DO NOT expose this in production API.
     *
     * @return The admin API key or null if not configured
     */
    String getAdminApiKey() {
        return adminApiKey;
    }

    /**
     * Get count of active tokens (for metrics).
     *
     * @return Number of active tokens
     */
    public int getActiveTokenCount() {
        // Clean up expired tokens
        activeTokens.entrySet().removeIf(entry -> entry.getValue().isExpired());
        return activeTokens.size();
    }
}
