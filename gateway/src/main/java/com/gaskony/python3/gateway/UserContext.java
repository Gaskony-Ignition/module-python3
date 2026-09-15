package com.gaskony.python3.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * User context for Python execution tracking.
 *
 * Captures user information for:
 * - Audit logging
 * - User-based rate limiting
 * - Execution history per user
 * - Security and compliance
 *
 * User context can be extracted from:
 * - Designer IDE (via DesignerContext)
 * - REST API (via headers or authentication)
 * - Gateway scripts (via session context)
 *
 * @since v2.14.0 (Phase 2 Week 5-6: Security Enhancements)
 */
public class UserContext {

    private static final Logger logger = LoggerFactory.getLogger(UserContext.class);

    // User identification
    private final String username;
    private final String userId;  // Optional: unique user ID
    private final String sessionId;
    private final String ipAddress;

    // Source information
    private final ExecutionSource source;
    private final String sourceDetails;

    // Timestamps
    private final long createdAt;

    public enum ExecutionSource {
        DESIGNER,           // Designer IDE
        REST_API,          // REST API endpoint
        GATEWAY_SCRIPT,    // Gateway script (e.g., Timer script)
        PERSPECTIVE,       // Perspective session
        VISION,            // Vision client
        UNKNOWN            // Unknown source
    }

    /**
     * Create user context with all details.
     */
    public UserContext(String username, String userId, String sessionId, String ipAddress,
                      ExecutionSource source, String sourceDetails) {
        this.username = username != null ? username : "anonymous";
        this.userId = userId;
        this.sessionId = sessionId;
        this.ipAddress = ipAddress;
        this.source = source != null ? source : ExecutionSource.UNKNOWN;
        this.sourceDetails = sourceDetails;
        this.createdAt = System.currentTimeMillis();
    }

    /**
     * Create simplified user context with username only.
     */
    public UserContext(String username, ExecutionSource source) {
        this(username, null, null, null, source, null);
    }

    /**
     * Create anonymous user context.
     */
    public static UserContext anonymous(ExecutionSource source) {
        return new UserContext("anonymous", null, null, null, source, null);
    }

    /**
     * Create user context from Designer.
     */
    public static UserContext fromDesigner(String username, String ipAddress) {
        return new UserContext(username, null, null, ipAddress, ExecutionSource.DESIGNER, "Python3 IDE");
    }

    /**
     * Create user context from REST API.
     */
    public static UserContext fromRestApi(String username, String sessionId, String ipAddress, String endpoint) {
        return new UserContext(username, null, sessionId, ipAddress, ExecutionSource.REST_API, endpoint);
    }

    /**
     * Create user context from Gateway script.
     */
    public static UserContext fromGatewayScript(String scriptName) {
        return new UserContext("system", null, null, "127.0.0.1", ExecutionSource.GATEWAY_SCRIPT, scriptName);
    }

    /**
     * Extract user context from request headers.
     * Looks for common headers like X-Username, X-Session-ID, etc.
     */
    public static UserContext fromRequestHeaders(java.util.Map<String, String> headers, String ipAddress) {
        String username = headers.getOrDefault("X-Username",
                         headers.getOrDefault("X-User", "anonymous"));
        String sessionId = headers.getOrDefault("X-Session-ID",
                          headers.getOrDefault("X-Session", null));
        String source = headers.getOrDefault("X-Source", "REST_API");

        ExecutionSource execSource;
        try {
            execSource = ExecutionSource.valueOf(source.toUpperCase());
        } catch (IllegalArgumentException e) {
            execSource = ExecutionSource.UNKNOWN;
        }

        return new UserContext(username, null, sessionId, ipAddress, execSource, null);
    }

    // Getters

    public String getUsername() {
        return username;
    }

    public String getUserId() {
        return userId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public ExecutionSource getSource() {
        return source;
    }

    public String getSourceDetails() {
        return sourceDetails;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public boolean isAnonymous() {
        return "anonymous".equals(username);
    }

    public boolean isFromDesigner() {
        return source == ExecutionSource.DESIGNER;
    }

    public boolean isFromRestApi() {
        return source == ExecutionSource.REST_API;
    }

    public boolean isFromGateway() {
        return source == ExecutionSource.GATEWAY_SCRIPT;
    }

    /**
     * Get display name for logging.
     */
    public String getDisplayName() {
        if (userId != null) {
            return String.format("%s (%s)", username, userId);
        }
        return username;
    }

    /**
     * Get full context description for audit logs.
     */
    public String getFullDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append("user=").append(username);

        if (userId != null) {
            sb.append(", userId=").append(userId);
        }

        if (sessionId != null) {
            sb.append(", session=").append(sessionId);
        }

        if (ipAddress != null) {
            sb.append(", ip=").append(ipAddress);
        }

        sb.append(", source=").append(source);

        if (sourceDetails != null) {
            sb.append(", details=").append(sourceDetails);
        }

        return sb.toString();
    }

    @Override
    public String toString() {
        return String.format("UserContext{user=%s, source=%s, ip=%s}",
            username, source, ipAddress != null ? ipAddress : "unknown");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        UserContext that = (UserContext) o;

        if (!username.equals(that.username)) return false;
        if (userId != null ? !userId.equals(that.userId) : that.userId != null) return false;
        return sessionId != null ? sessionId.equals(that.sessionId) : that.sessionId == null;
    }

    @Override
    public int hashCode() {
        int result = username.hashCode();
        result = 31 * result + (userId != null ? userId.hashCode() : 0);
        result = 31 * result + (sessionId != null ? sessionId.hashCode() : 0);
        return result;
    }
}
