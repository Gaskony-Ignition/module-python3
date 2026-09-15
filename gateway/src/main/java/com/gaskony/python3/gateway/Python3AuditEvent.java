package com.gaskony.python3.gateway;

import java.time.Instant;
import java.util.Objects;

/**
 * Audit event data for Python code execution.
 * Immutable record of a single Python execution for compliance and security monitoring.
 * <p>
 * Converted to Java 17 record in v2.10.0 (eliminated 87 lines of boilerplate).
 *
 * @param timestamp The time the execution occurred
 * @param user The username who executed the code (null if unauthenticated)
 * @param sourceIP The source IP address (null if local)
 * @param securityMode The security mode used (DESIGNER_ADMIN or ADMIN; legacy RESTRICTED removed in C13)
 * @param codeHash SHA-256 hash of the executed code
 * @param success Whether the execution succeeded
 * @param durationMs Execution duration in milliseconds
 * @param errorMessage Error message if execution failed (null if success)
 * @param endpoint The endpoint that triggered execution
 *
 * @since v2.6.0 (as class)
 * @since v2.10.0 (as record)
 */
public record Python3AuditEvent(
    Instant timestamp,
    String user,
    String sourceIP,
    SecurityMode securityMode,
    String codeHash,
    boolean success,
    long durationMs,
    String errorMessage,
    String endpoint
) {
    /**
     * Compact constructor with validation.
     */
    public Python3AuditEvent {
        Objects.requireNonNull(timestamp, "Timestamp cannot be null");
        Objects.requireNonNull(securityMode, "Security mode cannot be null");
        Objects.requireNonNull(codeHash, "Code hash cannot be null");
        Objects.requireNonNull(endpoint, "Endpoint cannot be null");
        // user, sourceIP, errorMessage can be null
    }

    // Legacy JavaBeans-style getters for backward compatibility
    public Instant getTimestamp() { return timestamp; }
    public String getUser() { return user; }
    public String getSourceIP() { return sourceIP; }
    public SecurityMode getSecurityMode() { return securityMode; }
    public String getCodeHash() { return codeHash; }
    public boolean isSuccess() { return success; }
    public long getDurationMs() { return durationMs; }
    public String getErrorMessage() { return errorMessage; }
    public String getEndpoint() { return endpoint; }

    /**
     * Check if this event represents an admin-level operation.
     * @return true if security mode is DESIGNER_ADMIN or ADMIN
     */
    public boolean isAdminOperation() {
        return securityMode.isAdminMode();
    }

    /**
     * Check if this event represents a Designer IDE operation.
     * @return true if security mode is DESIGNER_ADMIN
     */
    public boolean isDesignerOperation() {
        return securityMode.isDesignerMode();
    }

    /**
     * Get a single-line summary of this audit event for logging.
     * @return Human-readable audit log line
     */
    public String toLogLine() {
        return String.format("AUDIT: timestamp=%s, user=%s, ip=%s, mode=%s, codeHash=%s, success=%s, duration=%dms, endpoint=%s%s",
            timestamp,
            user != null ? user : "UNAUTHENTICATED",
            sourceIP != null ? sourceIP : "LOCAL",
            securityMode,
            codeHash.substring(0, Math.min(12, codeHash.length())), // First 12 chars of hash
            success,
            durationMs,
            endpoint,
            errorMessage != null ? ", error=" + errorMessage : ""
        );
    }

    @Override
    public String toString() {
        return toLogLine();
    }
}
