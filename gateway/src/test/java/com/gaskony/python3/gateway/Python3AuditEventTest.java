package com.gaskony.python3.gateway;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for Python3AuditEvent.
 * Tests audit event creation, validation, and data integrity.
 */
class Python3AuditEventTest {

    @Test
    void testCreateAuditEvent_Success() {
        Instant now = Instant.now();
        Python3AuditEvent event = new Python3AuditEvent(
            now,
            "admin",
            "192.168.1.100",
            SecurityMode.ADMIN,
            "abc123def456",
            true,
            150L,
            null,
            "REST:/api/v1/exec"
        );

        assertThat(event.getTimestamp()).isEqualTo(now);
        assertThat(event.getUser()).isEqualTo("admin");
        assertThat(event.getSourceIP()).isEqualTo("192.168.1.100");
        assertThat(event.getSecurityMode()).isEqualTo(SecurityMode.ADMIN);
        assertThat(event.getCodeHash()).isEqualTo("abc123def456");
        assertThat(event.isSuccess()).isTrue();
        assertThat(event.getDurationMs()).isEqualTo(150L);
        assertThat(event.getErrorMessage()).isNull();
        assertThat(event.getEndpoint()).isEqualTo("REST:/api/v1/exec");
    }

    @Test
    void testCreateAuditEvent_Failure() {
        Instant now = Instant.now();
        Python3AuditEvent event = new Python3AuditEvent(
            now,
            "user1",
            "10.0.0.5",
            SecurityMode.ADMIN,
            "xyz789",
            false,
            50L,
            "NameError: name 'x' is not defined",
            "SCRIPT:system.python3.exec"
        );

        assertThat(event.isSuccess()).isFalse();
        assertThat(event.getErrorMessage()).isEqualTo("NameError: name 'x' is not defined");
    }

    @Test
    void testCreateAuditEvent_Unauthenticated() {
        Instant now = Instant.now();
        Python3AuditEvent event = new Python3AuditEvent(
            now,
            null, // Unauthenticated
            null, // Local execution
            SecurityMode.ADMIN,
            "hash123",
            true,
            100L,
            null,
            "SCRIPT:system.python3.eval"
        );

        assertThat(event.getUser()).isNull();
        assertThat(event.getSourceIP()).isNull();
    }

    @Test
    void testCreateAuditEvent_NullTimestamp() {
        assertThatThrownBy(() -> new Python3AuditEvent(
            null, // Null timestamp
            "admin",
            "192.168.1.1",
            SecurityMode.ADMIN,
            "hash",
            true,
            100L,
            null,
            "REST:/api/v1/exec"
        )).isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Timestamp cannot be null");
    }

    @Test
    void testCreateAuditEvent_NullSecurityMode() {
        assertThatThrownBy(() -> new Python3AuditEvent(
            Instant.now(),
            "admin",
            "192.168.1.1",
            null, // Null security mode
            "hash",
            true,
            100L,
            null,
            "REST:/api/v1/exec"
        )).isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Security mode cannot be null");
    }

    @Test
    void testCreateAuditEvent_NullCodeHash() {
        assertThatThrownBy(() -> new Python3AuditEvent(
            Instant.now(),
            "admin",
            "192.168.1.1",
            SecurityMode.ADMIN,
            null, // Null code hash
            true,
            100L,
            null,
            "REST:/api/v1/exec"
        )).isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Code hash cannot be null");
    }

    @Test
    void testCreateAuditEvent_NullEndpoint() {
        assertThatThrownBy(() -> new Python3AuditEvent(
            Instant.now(),
            "admin",
            "192.168.1.1",
            SecurityMode.ADMIN,
            "hash",
            true,
            100L,
            null,
            null // Null endpoint
        )).isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Endpoint cannot be null");
    }

    @Test
    void testIsAdminOperation() {
        // C13: every remaining mode is an admin mode after RESTRICTED removal.
        Python3AuditEvent adminEvent = createTestEvent(SecurityMode.ADMIN);
        Python3AuditEvent designerEvent = createTestEvent(SecurityMode.DESIGNER_ADMIN);

        assertThat(adminEvent.isAdminOperation()).isTrue();
        assertThat(designerEvent.isAdminOperation()).isTrue();
    }

    @Test
    void testIsDesignerOperation() {
        Python3AuditEvent adminEvent = createTestEvent(SecurityMode.ADMIN);
        Python3AuditEvent designerEvent = createTestEvent(SecurityMode.DESIGNER_ADMIN);

        assertThat(adminEvent.isDesignerOperation()).isFalse();
        assertThat(designerEvent.isDesignerOperation()).isTrue();
    }

    @Test
    void testToLogLine_Success() {
        Instant now = Instant.parse("2025-01-15T10:30:00Z");
        Python3AuditEvent event = new Python3AuditEvent(
            now,
            "admin",
            "192.168.1.100",
            SecurityMode.ADMIN,
            "abc123def456789",
            true,
            150L,
            null,
            "REST:/api/v1/exec"
        );

        String logLine = event.toLogLine();

        assertThat(logLine).contains("AUDIT:");
        assertThat(logLine).contains("timestamp=2025-01-15T10:30:00Z");
        assertThat(logLine).contains("user=admin");
        assertThat(logLine).contains("ip=192.168.1.100");
        assertThat(logLine).contains("mode=ADMIN");
        assertThat(logLine).contains("codeHash=abc123def456"); // First 12 chars
        assertThat(logLine).contains("success=true");
        assertThat(logLine).contains("duration=150ms");
        assertThat(logLine).contains("endpoint=REST:/api/v1/exec");
        assertThat(logLine).doesNotContain("error=");
    }

    @Test
    void testToLogLine_Failure() {
        Instant now = Instant.parse("2025-01-15T10:30:00Z");
        Python3AuditEvent event = new Python3AuditEvent(
            now,
            "user1",
            "10.0.0.5",
            SecurityMode.ADMIN,
            "xyz789",
            false,
            50L,
            "NameError: name 'x' is not defined",
            "SCRIPT:system.python3.exec"
        );

        String logLine = event.toLogLine();

        assertThat(logLine).contains("success=false");
        assertThat(logLine).contains("error=NameError: name 'x' is not defined");
    }

    @Test
    void testToLogLine_Unauthenticated() {
        Python3AuditEvent event = new Python3AuditEvent(
            Instant.now(),
            null, // Unauthenticated
            null, // Local
            SecurityMode.ADMIN,
            "hash123",
            true,
            100L,
            null,
            "SCRIPT:system.python3.eval"
        );

        String logLine = event.toLogLine();

        assertThat(logLine).contains("user=UNAUTHENTICATED");
        assertThat(logLine).contains("ip=LOCAL");
    }

    @Test
    void testToString() {
        Python3AuditEvent event = createTestEvent(SecurityMode.ADMIN);

        // toString() should delegate to toLogLine()
        assertThat(event.toString()).isEqualTo(event.toLogLine());
    }

    @Test
    void testEquals_SameValues() {
        Instant now = Instant.now();
        Python3AuditEvent event1 = createTestEvent(now, SecurityMode.ADMIN);
        Python3AuditEvent event2 = createTestEvent(now, SecurityMode.ADMIN);

        assertThat(event1).isEqualTo(event2);
        assertThat(event1.hashCode()).isEqualTo(event2.hashCode());
    }

    @Test
    void testEquals_DifferentValues() {
        Python3AuditEvent event1 = createTestEvent(SecurityMode.ADMIN);
        Python3AuditEvent event2 = createTestEvent(SecurityMode.DESIGNER_ADMIN);

        assertThat(event1).isNotEqualTo(event2);
    }

    @Test
    void testEquals_SameInstance() {
        Python3AuditEvent event = createTestEvent(SecurityMode.ADMIN);

        assertThat(event).isEqualTo(event);
    }

    @Test
    void testEquals_Null() {
        Python3AuditEvent event = createTestEvent(SecurityMode.ADMIN);

        assertThat(event).isNotEqualTo(null);
    }

    @Test
    void testEquals_DifferentClass() {
        Python3AuditEvent event = createTestEvent(SecurityMode.ADMIN);

        assertThat(event).isNotEqualTo("not an audit event");
    }

    // Helper methods

    private Python3AuditEvent createTestEvent(SecurityMode mode) {
        return createTestEvent(Instant.now(), mode);
    }

    private Python3AuditEvent createTestEvent(Instant timestamp, SecurityMode mode) {
        return new Python3AuditEvent(
            timestamp,
            "testuser",
            "127.0.0.1",
            mode,
            "testhash123",
            true,
            100L,
            null,
            "TEST:test"
        );
    }
}
