package com.gaskony.python3.gateway;

import com.inductiveautomation.ignition.gateway.dataroutes.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IP whitelist enforcement for ADMIN mode requests.
 *
 * Instance-based (not static) so it can be independently tested without Ignition SDK.
 * A single instance is held as a static field in Python3RestEndpoints and delegated to.
 * The constructor calls {@link #load()} to read the system property on startup.
 *
 * v3.7.1: Extracted from Python3RestEndpoints for independent testability.
 */
class IpWhitelist {

    private static final Logger logger = LoggerFactory.getLogger(IpWhitelist.class);

    static final String IP_WHITELIST_PROPERTY = "ignition.python3.admin.ip.whitelist";

    // Package-private for test access
    Set<String> allowedIPs = Collections.emptySet();
    boolean enabled = false;

    /**
     * Creates a new IpWhitelist and loads the allowed IP set from the system property.
     *
     * v2.15.9: IP whitelisting for production security
     */
    IpWhitelist() {
        load();
    }

    /**
     * Package-private constructor for tests — skips automatic load.
     */
    IpWhitelist(boolean skipLoad) {
        // used by unit tests to inject their own allowedIPs/enabled state
    }

    /**
     * Load allowed IPs from the {@code ignition.python3.admin.ip.whitelist} system property.
     *
     * Format: comma-separated list of IPs or CIDR ranges.
     * Example: {@code "192.168.1.100,10.0.0.0/8,172.16.0.0/12"}
     *
     * If not set or empty, all IPs are allowed (backward compatible).
     */
    final void load() {
        String whitelist = System.getProperty(IP_WHITELIST_PROPERTY);

        if (whitelist == null || whitelist.trim().isEmpty()) {
            logger.info("IP whitelist not configured - all IPs allowed for ADMIN mode");
            enabled = false;
            allowedIPs = Collections.emptySet();
            return;
        }

        // Parse comma-separated IPs/CIDR ranges
        Set<String> ips = ConcurrentHashMap.newKeySet();
        for (String ip : whitelist.split(",")) {
            String trimmed = ip.trim();
            if (!trimmed.isEmpty()) {
                ips.add(trimmed);
                logger.info("Added IP to whitelist: {}", trimmed);
            }
        }

        if (ips.isEmpty()) {
            logger.warn("IP whitelist configured but empty - all IPs allowed");
            enabled = false;
            allowedIPs = Collections.emptySet();
        } else {
            allowedIPs = ips;
            enabled = true;
            logger.info("IP whitelist enabled with {} entries", ips.size());
            logger.warn("ADMIN mode access restricted to whitelisted IPs only");
        }
    }

    /**
     * Validate request IP against the whitelist.
     * Only enforced when the whitelist is enabled AND the security mode is {@link SecurityMode#ADMIN}.
     *
     * @param req          The request context
     * @param securityMode The security mode for this request
     * @throws SecurityException if the IP is not whitelisted for ADMIN mode
     */
    void validate(RequestContext req, SecurityMode securityMode) throws SecurityException {
        if (!enabled || securityMode != SecurityMode.ADMIN) {
            return;
        }

        String clientIP = getClientIPAddress(req);

        if (!isAllowed(clientIP)) {
            logger.warn("SECURITY: ADMIN mode request rejected from non-whitelisted IP: {}", clientIP);
            throw new SecurityException(
                "Access denied: Your IP address (" + clientIP + ") is not whitelisted for ADMIN mode. " +
                "Contact your administrator to add your IP to: " + IP_WHITELIST_PROPERTY
            );
        }

        logger.debug("IP whitelist check passed for: {}", clientIP);
    }

    /**
     * Check if an IP address is in the whitelist.
     * Supports individual IPs and CIDR ranges.
     *
     * @param clientIP The client IP address (may be null)
     * @return {@code true} if the IP is allowed (or whitelist is disabled)
     */
    boolean isAllowed(String clientIP) {
        if (!enabled || clientIP == null) {
            return true;  // Whitelist disabled or no IP
        }

        // Direct IP match
        if (allowedIPs.contains(clientIP)) {
            return true;
        }

        // CIDR range match
        for (String allowedEntry : allowedIPs) {
            if (allowedEntry.contains("/")) {
                if (isInCIDR(clientIP, allowedEntry)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Check if an IPv4 address falls within a CIDR range.
     *
     * @param ip   The IP to test (e.g., {@code "192.168.1.100"})
     * @param cidr The CIDR range (e.g., {@code "192.168.1.0/24"})
     * @return {@code true} if the IP is within the CIDR range
     */
    boolean isInCIDR(String ip, String cidr) {
        try {
            String[] cidrParts = cidr.split("/");
            if (cidrParts.length != 2) {
                return false;
            }

            String networkIP = cidrParts[0];
            int prefixLength = Integer.parseInt(cidrParts[1]);

            long ipLong = tolong(ip);
            long networkLong = tolong(networkIP);

            long mask = ((1L << prefixLength) - 1) << (32 - prefixLength);

            return (ipLong & mask) == (networkLong & mask);

        } catch (Exception e) {
            logger.warn("Error parsing CIDR range: {} - {}", cidr, e.getMessage());
            return false;
        }
    }

    /**
     * Convert an IPv4 address string to a long integer for CIDR comparison.
     *
     * @param ip IPv4 address (e.g., {@code "192.168.1.100"})
     * @return Long representation
     * @throws IllegalArgumentException if the address is not a valid IPv4 address
     */
    static long tolong(String ip) {
        String[] octets = ip.split("\\.");
        if (octets.length != 4) {
            throw new IllegalArgumentException("Invalid IPv4 address: " + ip);
        }

        long result = 0;
        for (int i = 0; i < 4; i++) {
            int octet = Integer.parseInt(octets[i]);
            if (octet < 0 || octet > 255) {
                throw new IllegalArgumentException("Invalid IPv4 octet: " + octet);
            }
            result = (result << 8) | octet;
        }
        return result;
    }

    /**
     * Get the client IP address from a request, honouring the {@code X-Forwarded-For} header.
     *
     * @param req The request context
     * @return Client IP address string
     */
    static String getClientIPAddress(RequestContext req) {
        HttpServletRequest httpReq = req.getRequest();

        String xForwardedFor = httpReq.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.trim().isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        return httpReq.getRemoteAddr();
    }
}
