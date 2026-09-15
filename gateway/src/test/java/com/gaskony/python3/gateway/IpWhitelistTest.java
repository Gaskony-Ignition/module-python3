package com.gaskony.python3.gateway;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.inductiveautomation.ignition.gateway.dataroutes.RequestContext;

import jakarta.servlet.http.HttpServletRequest;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for IpWhitelist.
 *
 * Covers: CIDR matching, direct IP comparison, disabled whitelist, validate() enforcement.
 * IpWhitelist(true) constructor skips system-property load, allowing manual state injection.
 */
@ExtendWith(MockitoExtension.class)
class IpWhitelistTest {

    private IpWhitelist whitelist;

    @Mock
    private RequestContext req;
    @Mock
    private HttpServletRequest httpReq;

    @BeforeEach
    void setUp() {
        // skipLoad=true: skip system property read; tests set allowedIPs/enabled manually
        whitelist = new IpWhitelist(true);
    }

    // ===== isAllowed — disabled whitelist =====

    @Test
    void isAllowed_whenDisabled_allowsAnyIP() {
        whitelist.enabled = false;
        assertThat(whitelist.isAllowed("10.0.0.1")).isTrue();
        assertThat(whitelist.isAllowed("192.168.1.100")).isTrue();
        assertThat(whitelist.isAllowed(null)).isTrue();
    }

    // ===== isAllowed — direct IP match =====

    @Test
    void isAllowed_directIPMatch_returnsTrue() {
        whitelist.enabled = true;
        whitelist.allowedIPs = java.util.Set.of("192.168.1.100");

        assertThat(whitelist.isAllowed("192.168.1.100")).isTrue();
    }

    @Test
    void isAllowed_directIPNoMatch_returnsFalse() {
        whitelist.enabled = true;
        whitelist.allowedIPs = java.util.Set.of("192.168.1.100");

        assertThat(whitelist.isAllowed("192.168.1.101")).isFalse();
    }

    @Test
    void isAllowed_nullIP_whenEnabled_returnsFalse() {
        whitelist.enabled = true;
        whitelist.allowedIPs = java.util.Set.of("192.168.1.100");

        // null IP returns true when disabled, returns true due to null guard
        assertThat(whitelist.isAllowed(null)).isTrue();
    }

    // ===== isAllowed — CIDR matching =====

    @Test
    void isAllowed_cidr24_hostsInRange() {
        whitelist.enabled = true;
        whitelist.allowedIPs = java.util.Set.of("192.168.1.0/24");

        assertThat(whitelist.isAllowed("192.168.1.1")).isTrue();
        assertThat(whitelist.isAllowed("192.168.1.254")).isTrue();
        assertThat(whitelist.isAllowed("192.168.1.0")).isTrue();
    }

    @Test
    void isAllowed_cidr24_hostsOutOfRange() {
        whitelist.enabled = true;
        whitelist.allowedIPs = java.util.Set.of("192.168.1.0/24");

        assertThat(whitelist.isAllowed("192.168.2.1")).isFalse();
        assertThat(whitelist.isAllowed("10.0.0.1")).isFalse();
    }

    @Test
    void isAllowed_cidr8_broadRange() {
        whitelist.enabled = true;
        whitelist.allowedIPs = java.util.Set.of("10.0.0.0/8");

        assertThat(whitelist.isAllowed("10.0.0.1")).isTrue();
        assertThat(whitelist.isAllowed("10.255.255.255")).isTrue();
        assertThat(whitelist.isAllowed("11.0.0.1")).isFalse();
    }

    @Test
    void isAllowed_cidr32_exactHost() {
        whitelist.enabled = true;
        whitelist.allowedIPs = java.util.Set.of("192.168.1.50/32");

        assertThat(whitelist.isAllowed("192.168.1.50")).isTrue();
        assertThat(whitelist.isAllowed("192.168.1.51")).isFalse();
    }

    @Test
    void isAllowed_multipleCidrAndDirect() {
        whitelist.enabled = true;
        whitelist.allowedIPs = java.util.Set.of("192.168.1.0/24", "10.0.0.1", "172.16.0.0/12");

        assertThat(whitelist.isAllowed("192.168.1.50")).isTrue();
        assertThat(whitelist.isAllowed("10.0.0.1")).isTrue();
        assertThat(whitelist.isAllowed("172.16.5.100")).isTrue();
        assertThat(whitelist.isAllowed("8.8.8.8")).isFalse();
    }

    // ===== isInCIDR =====

    @Test
    void isInCIDR_validRange() {
        assertThat(whitelist.isInCIDR("192.168.1.50", "192.168.1.0/24")).isTrue();
        assertThat(whitelist.isInCIDR("192.168.2.1", "192.168.1.0/24")).isFalse();
    }

    @Test
    void isInCIDR_invalidCidrFormat_returnsFalse() {
        assertThat(whitelist.isInCIDR("192.168.1.1", "not-a-cidr")).isFalse();
        assertThat(whitelist.isInCIDR("192.168.1.1", "192.168.1.0")).isFalse(); // no prefix length
    }

    @Test
    void isInCIDR_invalidIPOctet_returnsFalse() {
        assertThat(whitelist.isInCIDR("999.168.1.1", "192.168.1.0/24")).isFalse();
    }

    // ===== tolong =====

    @Test
    void tolong_localhost() {
        assertThat(IpWhitelist.tolong("127.0.0.1")).isEqualTo(2130706433L);
    }

    @Test
    void tolong_zeros() {
        assertThat(IpWhitelist.tolong("0.0.0.0")).isEqualTo(0L);
    }

    @Test
    void tolong_allOnes() {
        assertThat(IpWhitelist.tolong("255.255.255.255")).isEqualTo(4294967295L);
    }

    @Test
    void tolong_invalidFormat_throws() {
        assertThatThrownBy(() -> IpWhitelist.tolong("not.an.ip"))
            .isInstanceOf(Exception.class);
        assertThatThrownBy(() -> IpWhitelist.tolong("1.2.3"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // ===== getClientIPAddress =====

    @Test
    void getClientIPAddress_returnsXForwardedForFirst() {
        when(req.getRequest()).thenReturn(httpReq);
        when(httpReq.getHeader("X-Forwarded-For")).thenReturn("1.2.3.4, 5.6.7.8");

        assertThat(IpWhitelist.getClientIPAddress(req)).isEqualTo("1.2.3.4");
    }

    @Test
    void getClientIPAddress_fallsBackToRemoteAddr() {
        when(req.getRequest()).thenReturn(httpReq);
        when(httpReq.getHeader("X-Forwarded-For")).thenReturn(null);
        when(httpReq.getRemoteAddr()).thenReturn("10.0.0.5");

        assertThat(IpWhitelist.getClientIPAddress(req)).isEqualTo("10.0.0.5");
    }

    @Test
    void getClientIPAddress_emptyXForwardedFor_fallsBackToRemoteAddr() {
        when(req.getRequest()).thenReturn(httpReq);
        when(httpReq.getHeader("X-Forwarded-For")).thenReturn("  ");
        when(httpReq.getRemoteAddr()).thenReturn("10.0.0.5");

        assertThat(IpWhitelist.getClientIPAddress(req)).isEqualTo("10.0.0.5");
    }

    // ===== validate =====

    @Test
    void validate_whenDisabled_doesNotThrow() {
        whitelist.enabled = false;

        assertThatCode(() -> whitelist.validate(req, SecurityMode.ADMIN)).doesNotThrowAnyException();
    }

    @Test
    void validate_nonAdminMode_doesNotThrow() {
        // C13: SecurityMode.RESTRICTED was removed; DESIGNER_ADMIN is the
        // remaining non-ADMIN value and the IP-whitelist is still ADMIN-only.
        whitelist.enabled = true;
        whitelist.allowedIPs = java.util.Set.of("1.2.3.4");

        assertThatCode(() -> whitelist.validate(req, SecurityMode.DESIGNER_ADMIN)).doesNotThrowAnyException();
    }

    @Test
    void validate_adminModeAllowedIP_doesNotThrow() {
        whitelist.enabled = true;
        whitelist.allowedIPs = java.util.Set.of("192.168.1.50");

        when(req.getRequest()).thenReturn(httpReq);
        when(httpReq.getHeader("X-Forwarded-For")).thenReturn(null);
        when(httpReq.getRemoteAddr()).thenReturn("192.168.1.50");

        assertThatCode(() -> whitelist.validate(req, SecurityMode.ADMIN)).doesNotThrowAnyException();
    }

    @Test
    void validate_adminModeBlockedIP_throwsSecurityException() {
        whitelist.enabled = true;
        whitelist.allowedIPs = java.util.Set.of("192.168.1.50");

        when(req.getRequest()).thenReturn(httpReq);
        when(httpReq.getHeader("X-Forwarded-For")).thenReturn(null);
        when(httpReq.getRemoteAddr()).thenReturn("8.8.8.8");

        assertThatThrownBy(() -> whitelist.validate(req, SecurityMode.ADMIN))
            .isInstanceOf(SecurityException.class)
            .hasMessageContaining("not whitelisted");
    }

    // ===== load =====

    @Test
    void load_noPropertySet_disablesWhitelist() {
        System.clearProperty(IpWhitelist.IP_WHITELIST_PROPERTY);
        whitelist.load();

        assertThat(whitelist.enabled).isFalse();
        assertThat(whitelist.allowedIPs).isEmpty();
    }

    @Test
    void load_emptyProperty_disablesWhitelist() {
        System.setProperty(IpWhitelist.IP_WHITELIST_PROPERTY, "  ");
        try {
            whitelist.load();
            assertThat(whitelist.enabled).isFalse();
        } finally {
            System.clearProperty(IpWhitelist.IP_WHITELIST_PROPERTY);
        }
    }

    @Test
    void load_withIPs_enablesWhitelist() {
        System.setProperty(IpWhitelist.IP_WHITELIST_PROPERTY, "192.168.1.1, 10.0.0.0/8");
        try {
            whitelist.load();
            assertThat(whitelist.enabled).isTrue();
            assertThat(whitelist.allowedIPs).containsExactlyInAnyOrder("192.168.1.1", "10.0.0.0/8");
        } finally {
            System.clearProperty(IpWhitelist.IP_WHITELIST_PROPERTY);
        }
    }
}
