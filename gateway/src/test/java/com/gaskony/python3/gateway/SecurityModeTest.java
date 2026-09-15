package com.gaskony.python3.gateway;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SecurityMode}.
 *
 * <p>Updated for security review C13 (May 2026): the {@code RESTRICTED} mode and
 * its (bypassable) sandbox were removed. Only {@link SecurityMode#DESIGNER_ADMIN}
 * and {@link SecurityMode#ADMIN} remain; both grant full Python capabilities and
 * are distinguished only for audit-log clarity. Access control is now enforced
 * on the Java side via {@link RoleResolver#requireAdministrator}.</p>
 */
class SecurityModeTest {

    @Test
    void testSecurityModeValues() {
        // C13: only two modes after RESTRICTED removal.
        assertThat(SecurityMode.values()).hasSize(2);
        assertThat(SecurityMode.values()).containsExactlyInAnyOrder(
            SecurityMode.DESIGNER_ADMIN,
            SecurityMode.ADMIN
        );
    }

    @Test
    void testGetValue() {
        assertThat(SecurityMode.DESIGNER_ADMIN.getValue()).isEqualTo("DESIGNER_ADMIN");
        assertThat(SecurityMode.ADMIN.getValue()).isEqualTo("ADMIN");
    }

    @Test
    void testFromString_DesignerAdmin() {
        assertThat(SecurityMode.fromString("DESIGNER_ADMIN")).isEqualTo(SecurityMode.DESIGNER_ADMIN);
        assertThat(SecurityMode.fromString("designer_admin")).isEqualTo(SecurityMode.DESIGNER_ADMIN);
        assertThat(SecurityMode.fromString("Designer_Admin")).isEqualTo(SecurityMode.DESIGNER_ADMIN);
    }

    @Test
    void testFromString_Admin() {
        assertThat(SecurityMode.fromString("ADMIN")).isEqualTo(SecurityMode.ADMIN);
        assertThat(SecurityMode.fromString("admin")).isEqualTo(SecurityMode.ADMIN);
        assertThat(SecurityMode.fromString("Admin")).isEqualTo(SecurityMode.ADMIN);
    }

    @Test
    void testFromString_LegacyRestrictedMapsToDesignerAdmin() {
        // C13: the removed "RESTRICTED" wire-value safely maps to DESIGNER_ADMIN
        // (both are admin-equivalent now; the actual access gate is the Java-side
        // role check).
        assertThat(SecurityMode.fromString("RESTRICTED")).isEqualTo(SecurityMode.DESIGNER_ADMIN);
        assertThat(SecurityMode.fromString("restricted")).isEqualTo(SecurityMode.DESIGNER_ADMIN);
    }

    @Test
    void testFromString_Invalid_DefaultsToDesignerAdmin() {
        // C13: unknown / empty / null all map to DESIGNER_ADMIN (the only safe
        // default once RESTRICTED is gone).
        assertThat(SecurityMode.fromString("INVALID")).isEqualTo(SecurityMode.DESIGNER_ADMIN);
        assertThat(SecurityMode.fromString("")).isEqualTo(SecurityMode.DESIGNER_ADMIN);
        assertThat(SecurityMode.fromString(null)).isEqualTo(SecurityMode.DESIGNER_ADMIN);
        assertThat(SecurityMode.fromString("UNKNOWN")).isEqualTo(SecurityMode.DESIGNER_ADMIN);
    }

    @Test
    void testIsAdminMode() {
        // C13: every remaining mode is an admin mode.
        assertThat(SecurityMode.DESIGNER_ADMIN.isAdminMode()).isTrue();
        assertThat(SecurityMode.ADMIN.isAdminMode()).isTrue();
    }

    @Test
    void testIsDesignerMode() {
        assertThat(SecurityMode.DESIGNER_ADMIN.isDesignerMode()).isTrue();
        assertThat(SecurityMode.ADMIN.isDesignerMode()).isFalse();
    }

    @Test
    void testToString() {
        assertThat(SecurityMode.DESIGNER_ADMIN.toString()).isEqualTo("DESIGNER_ADMIN");
        assertThat(SecurityMode.ADMIN.toString()).isEqualTo("ADMIN");
    }

    @Test
    void testEnumEquality() {
        SecurityMode mode1 = SecurityMode.fromString("DESIGNER_ADMIN");
        SecurityMode mode2 = SecurityMode.DESIGNER_ADMIN;

        assertThat(mode1).isEqualTo(mode2);
        assertThat(mode1).isSameAs(mode2); // Same instance (enum)
    }

    @Test
    void testSecurityModeInSwitch() {
        // Verify can be used in switch statements
        SecurityMode mode = SecurityMode.DESIGNER_ADMIN;

        String result = switch (mode) {
            case DESIGNER_ADMIN -> "Full access";
            case ADMIN -> "Extended access";
        };

        assertThat(result).isEqualTo("Full access");
    }
}
