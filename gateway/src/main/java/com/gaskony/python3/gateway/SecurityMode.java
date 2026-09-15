package com.gaskony.python3.gateway;

/**
 * Security modes for Python code execution.
 *
 * <h3>Trust model (May 2026, security review C13)</h3>
 *
 * <p>The previous {@code RESTRICTED} mode purported to confine untrusted callers
 * to a whitelist of "safe" Python modules via AST validation and string-match
 * filters in {@code python_bridge.py}. The check was trivially bypassable — see
 * the C13 finding in {@code /modules/.review/FINAL_REVIEW.md}. The mode and its
 * sandbox have been deleted; access control is now enforced on the Java side
 * via {@link RoleResolver#requireAdministrator} before any Python source
 * reaches the bridge subprocess.</p>
 *
 * <p>The two remaining modes are equivalent in capability — both grant full
 * Python 3 capabilities — and are distinguished only for audit-log clarity:</p>
 *
 * <ul>
 *   <li>{@link #DESIGNER_ADMIN}: caller authenticated as a Designer or
 *       Administrator via the Ignition session/role chain.</li>
 *   <li>{@link #ADMIN}: caller authenticated via the legacy admin API key /
 *       {@code X-Python3-Admin-Key} header (kept for backward compatibility).</li>
 * </ul>
 *
 * <p>For real isolation between users and Gateway-host privileges, deploy the
 * Gateway in a container or VM whose blast radius matches your trust
 * requirements.</p>
 *
 * @since v2.6.0; sandbox removed in v3.13.0 (C13)
 */
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

public enum SecurityMode {
    /**
     * Full Python capabilities for Designer-class users.
     *
     * <p>Issued to callers who hold the Ignition {@code Designer} or
     * {@code Administrator} role at the moment of authentication.</p>
     */
    DESIGNER_ADMIN("DESIGNER_ADMIN"),

    /**
     * Full Python capabilities, granted via the legacy admin API key path.
     *
     * <p>Functionally equivalent to {@link #DESIGNER_ADMIN}; preserved as a
     * separate value so audit logs can distinguish browser/Designer logins
     * from headless API-key callers.</p>
     */
    ADMIN("ADMIN");

    private final String value;

    SecurityMode(String value) {
        this.value = value;
    }

    /**
     * Get the string value of this security mode.
     * Used for communication with Python bridge and audit logs.
     *
     * @return The string representation (e.g., "DESIGNER_ADMIN")
     */
    public String getValue() {
        return value;
    }

    private static final Logger LEGACY_LOGGER =
        LoggerFactory.getLogger(SecurityMode.class);

    /**
     * Latch so the legacy-RESTRICTED deprecation warning is emitted at most
     * once per JVM run. The first hit gets a loud WARN; subsequent hits go
     * silent so the gateway log isn't flooded by a hot-loop caller.
     */
    private static final AtomicBoolean LEGACY_RESTRICTED_WARNED =
        new AtomicBoolean(false);

    /**
     * Parse a security mode from string value.
     * Case-insensitive matching.
     *
     * <p>Unknown values (including the now-removed legacy {@code "RESTRICTED"})
     * map to {@link #DESIGNER_ADMIN} — both remaining modes grant the same
     * capability, so this is a strictly safer default than throwing.</p>
     *
     * <p>v4.0.0: the legacy {@code "RESTRICTED"} wire value is detected
     * explicitly and emits a one-time {@code WARN} log. The acceptance is
     * preserved (returns {@link #DESIGNER_ADMIN}) for back-compat, but the
     * warning makes the deprecation visible to operators — otherwise old
     * clients that thought they were sandboxed would silently run with
     * admin capability and the migration would slip past unnoticed.</p>
     *
     * @param value The string value to parse
     * @return The corresponding SecurityMode (defaults to DESIGNER_ADMIN)
     */
    public static SecurityMode fromString(String value) {
        if (value == null) {
            return DESIGNER_ADMIN;
        }

        for (SecurityMode mode : values()) {
            if (mode.value.equalsIgnoreCase(value)) {
                return mode;
            }
        }

        // Unknown / legacy value (e.g. "RESTRICTED" from older clients) →
        // default to DESIGNER_ADMIN. Audit logs will record the actual mode,
        // and the Java-side role check has already gated the call.
        if ("RESTRICTED".equalsIgnoreCase(value)
                && LEGACY_RESTRICTED_WARNED.compareAndSet(false, true)) {
            LEGACY_LOGGER.warn(
                "Received legacy securityMode=\"RESTRICTED\" wire value. This "
                + "mode was removed in v4.0.0 (security review C13) and now "
                + "maps silently to DESIGNER_ADMIN with full Python capability. "
                + "If your caller previously relied on RESTRICTED for "
                + "sandboxing, it is no longer sandboxed — update the caller "
                + "to authenticate as ADMIN (and rely on OS-level isolation "
                + "for trust boundaries). This warning is emitted once per JVM."
            );
        }
        return DESIGNER_ADMIN;
    }

    /**
     * Check if this mode allows admin-level operations.
     *
     * <p>Always {@code true} after the C13 cleanup — every remaining mode is
     * an admin mode. Retained for source compatibility with callers from
     * before the cleanup.</p>
     *
     * @return {@code true}
     */
    public boolean isAdminMode() {
        return true;
    }

    /**
     * Check if this mode allows full capabilities (Designer users).
     *
     * @return true if DESIGNER_ADMIN mode
     */
    public boolean isDesignerMode() {
        return this == DESIGNER_ADMIN;
    }

    @Override
    public String toString() {
        return value;
    }
}
