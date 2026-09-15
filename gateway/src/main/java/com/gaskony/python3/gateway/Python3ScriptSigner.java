package com.gaskony.python3.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Provides HMAC-based script signing and verification.
 * Prevents tampering with saved Python scripts.
 *
 * v1.17.0: Script signing & verification for tamper protection
 */
public class Python3ScriptSigner {

    private static final Logger logger = LoggerFactory.getLogger(Python3ScriptSigner.class);

    // Secret key for signing (configured via system property)
    // Generate with: openssl rand -hex 32
    private static final String SECRET_KEY = System.getProperty(
            "ignition.python3.signing.key",
            generateDefaultKey()  // Fallback to generated key
    );

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    /**
     * Signs a script code using HMAC-SHA256.
     *
     * @param code the Python code to sign
     * @return Base64-encoded signature
     * @throws RuntimeException if signing fails
     */
    public static String signScript(String code) {
        if (code == null) {
            code = "";
        }

        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec secretKey = new SecretKeySpec(
                    SECRET_KEY.getBytes(StandardCharsets.UTF_8),
                    HMAC_ALGORITHM
            );
            mac.init(secretKey);

            byte[] signature = mac.doFinal(code.getBytes(StandardCharsets.UTF_8));
            String encoded = Base64.getEncoder().encodeToString(signature);

            logger.debug("Script signed, signature length: {} bytes", signature.length);
            return encoded;

        } catch (Exception e) {
            logger.error("Failed to sign script", e);
            throw new RuntimeException("Script signing failed", e);
        }
    }

    /**
     * Verifies a script signature.
     *
     * @param code the Python code
     * @param providedSignature the signature to verify
     * @return true if signature is valid, false otherwise
     */
    public static boolean verifyScript(String code, String providedSignature) {
        if (code == null) {
            code = "";
        }

        if (providedSignature == null || providedSignature.trim().isEmpty()) {
            logger.warn("Script verification failed: no signature provided");
            return false;
        }

        try {
            String expectedSignature = signScript(code);

            // Constant-time comparison to prevent timing attacks
            boolean valid = secureEquals(expectedSignature, providedSignature);

            if (!valid) {
                logger.warn("Script signature verification FAILED - possible tampering detected");
            } else {
                logger.debug("Script signature verified successfully");
            }

            return valid;

        } catch (Exception e) {
            logger.error("Script verification error", e);
            return false;
        }
    }

    /**
     * Constant-time string comparison to prevent timing attacks.
     */
    private static boolean secureEquals(String a, String b) {
        if (a == null || b == null) {
            return a == b;
        }

        if (a.length() != b.length()) {
            return false;
        }

        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }

        return result == 0;
    }

    /**
     * Generate a default signing key from system properties.
     * In production, always configure via: -Dignition.python3.signing.key=<your-key>
     */
    private static String generateDefaultKey() {
        logger.warn("Using auto-generated signing key. For production, configure: -Dignition.python3.signing.key=<your-key>");

        try {
            // Generate from Gateway installation path + hostname for consistency
            String gatewayPath = System.getProperty("user.dir", "ignition");
            String hostname = java.net.InetAddress.getLocalHost().getHostName();
            String combined = gatewayPath + ":" + hostname + ":python3-integration-v1.17.0";

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(combined.getBytes(StandardCharsets.UTF_8));

            return Base64.getEncoder().encodeToString(hash);

        } catch (Exception e) {
            logger.error("Failed to generate default key, using fallback", e);
            return "python3-integration-default-signing-key-v1.17.0-CHANGE-IN-PRODUCTION";
        }
    }

    /**
     * Get the configured signing key (for verification purposes only).
     * Do NOT log or expose this value!
     */
    static String getSigningKeyHash() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(SECRET_KEY.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash).substring(0, 16);
        } catch (Exception e) {
            return "unknown";
        }
    }
}
