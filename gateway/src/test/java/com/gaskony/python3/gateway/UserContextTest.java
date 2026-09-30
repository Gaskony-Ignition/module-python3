package com.gaskony.python3.gateway;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for UserContext.
 *
 * UserContext has no Ignition SDK dependencies — pure unit tests.
 */
class UserContextTest {

    // ===== Constructor =====

    @Test
    void constructor_setsUsername() {
        UserContext ctx = new UserContext("alice", null, null, null,
            UserContext.ExecutionSource.REST_API, null);
        assertThat(ctx.getUsername()).isEqualTo("alice");
    }

    @Test
    void constructor_nullUsername_becomesAnonymous() {
        UserContext ctx = new UserContext(null, null, null, null,
            UserContext.ExecutionSource.REST_API, null);
        assertThat(ctx.getUsername()).isEqualTo("anonymous");
    }

    @Test
    void constructor_nullSource_becomesUnknown() {
        UserContext ctx = new UserContext("bob", null, null, null, null, null);
        assertThat(ctx.getSource()).isEqualTo(UserContext.ExecutionSource.UNKNOWN);
    }

    @Test
    void constructor_setsAllFields() {
        UserContext ctx = new UserContext("alice", "uid-1", "sess-1", "10.0.0.1",
            UserContext.ExecutionSource.DESIGNER, "Python3 IDE");
        assertThat(ctx.getUsername()).isEqualTo("alice");
        assertThat(ctx.getUserId()).isEqualTo("uid-1");
        assertThat(ctx.getSessionId()).isEqualTo("sess-1");
        assertThat(ctx.getIpAddress()).isEqualTo("10.0.0.1");
        assertThat(ctx.getSource()).isEqualTo(UserContext.ExecutionSource.DESIGNER);
        assertThat(ctx.getSourceDetails()).isEqualTo("Python3 IDE");
    }

    @Test
    void constructor_twoArg_setsUsernameAndSource() {
        UserContext ctx = new UserContext("carol", UserContext.ExecutionSource.GATEWAY_SCRIPT);
        assertThat(ctx.getUsername()).isEqualTo("carol");
        assertThat(ctx.getSource()).isEqualTo(UserContext.ExecutionSource.GATEWAY_SCRIPT);
        assertThat(ctx.getUserId()).isNull();
        assertThat(ctx.getSessionId()).isNull();
    }

    // ===== Static factory methods =====

    @Test
    void anonymous_createsAnonymousContext() {
        UserContext ctx = UserContext.anonymous(UserContext.ExecutionSource.REST_API);
        assertThat(ctx.getUsername()).isEqualTo("anonymous");
        assertThat(ctx.getSource()).isEqualTo(UserContext.ExecutionSource.REST_API);
        assertThat(ctx.isAnonymous()).isTrue();
    }

    @Test
    void fromDesigner_setsDesignerSource() {
        UserContext ctx = UserContext.fromDesigner("dave", "192.168.1.1");
        assertThat(ctx.getUsername()).isEqualTo("dave");
        assertThat(ctx.getIpAddress()).isEqualTo("192.168.1.1");
        assertThat(ctx.getSource()).isEqualTo(UserContext.ExecutionSource.DESIGNER);
        assertThat(ctx.getSourceDetails()).isEqualTo("Python3 IDE");
    }

    @Test
    void fromRestApi_setsRestApiSource() {
        UserContext ctx = UserContext.fromRestApi("eve", "sess-123", "10.0.0.5", "/api/v1/exec");
        assertThat(ctx.getUsername()).isEqualTo("eve");
        assertThat(ctx.getSessionId()).isEqualTo("sess-123");
        assertThat(ctx.getIpAddress()).isEqualTo("10.0.0.5");
        assertThat(ctx.getSource()).isEqualTo(UserContext.ExecutionSource.REST_API);
        assertThat(ctx.getSourceDetails()).isEqualTo("/api/v1/exec");
    }

    @Test
    void fromGatewayScript_setsGatewayScriptSource() {
        UserContext ctx = UserContext.fromGatewayScript("myScript.py");
        assertThat(ctx.getUsername()).isEqualTo("system");
        assertThat(ctx.getIpAddress()).isEqualTo("127.0.0.1");
        assertThat(ctx.getSource()).isEqualTo(UserContext.ExecutionSource.GATEWAY_SCRIPT);
        assertThat(ctx.getSourceDetails()).isEqualTo("myScript.py");
    }

    // ===== fromRequestHeaders =====

    @Test
    void fromRequestHeaders_withUsernameHeader_setsUsername() {
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Username", "frank");
        headers.put("X-Source", "DESIGNER");
        UserContext ctx = UserContext.fromRequestHeaders(headers, "1.2.3.4");
        assertThat(ctx.getUsername()).isEqualTo("frank");
        assertThat(ctx.getSource()).isEqualTo(UserContext.ExecutionSource.DESIGNER);
    }

    @Test
    void fromRequestHeaders_withXUserHeader_setsUsername() {
        Map<String, String> headers = new HashMap<>();
        headers.put("X-User", "grace");
        UserContext ctx = UserContext.fromRequestHeaders(headers, "1.2.3.4");
        assertThat(ctx.getUsername()).isEqualTo("grace");
    }

    @Test
    void fromRequestHeaders_noUsernameHeader_usesAnonymous() {
        Map<String, String> headers = new HashMap<>();
        UserContext ctx = UserContext.fromRequestHeaders(headers, "1.2.3.4");
        assertThat(ctx.getUsername()).isEqualTo("anonymous");
    }

    @Test
    void fromRequestHeaders_unknownSource_becomesUnknown() {
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Source", "NOT_A_SOURCE");
        UserContext ctx = UserContext.fromRequestHeaders(headers, "1.2.3.4");
        assertThat(ctx.getSource()).isEqualTo(UserContext.ExecutionSource.UNKNOWN);
    }

    @Test
    void fromRequestHeaders_withSessionId_setsSessionId() {
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Session-ID", "sess-abc");
        UserContext ctx = UserContext.fromRequestHeaders(headers, "1.2.3.4");
        assertThat(ctx.getSessionId()).isEqualTo("sess-abc");
    }

    // ===== Boolean checks =====

    @Test
    void isAnonymous_namedUser_returnsFalse() {
        UserContext ctx = new UserContext("alice", UserContext.ExecutionSource.REST_API);
        assertThat(ctx.isAnonymous()).isFalse();
    }

    @Test
    void isFromDesigner_designerSource_returnsTrue() {
        UserContext ctx = UserContext.fromDesigner("alice", "1.2.3.4");
        assertThat(ctx.isFromDesigner()).isTrue();
        assertThat(ctx.isFromRestApi()).isFalse();
        assertThat(ctx.isFromGateway()).isFalse();
    }

    @Test
    void isFromRestApi_restApiSource_returnsTrue() {
        UserContext ctx = UserContext.fromRestApi("alice", null, "1.2.3.4", "/exec");
        assertThat(ctx.isFromRestApi()).isTrue();
        assertThat(ctx.isFromDesigner()).isFalse();
    }

    @Test
    void isFromGateway_gatewaySource_returnsTrue() {
        UserContext ctx = UserContext.fromGatewayScript("myScript");
        assertThat(ctx.isFromGateway()).isTrue();
        assertThat(ctx.isFromDesigner()).isFalse();
    }

    // ===== Display methods =====

    @Test
    void getDisplayName_withUserId_includesBothParts() {
        UserContext ctx = new UserContext("alice", "uid-1", null, null,
            UserContext.ExecutionSource.REST_API, null);
        assertThat(ctx.getDisplayName()).contains("alice").contains("uid-1");
    }

    @Test
    void getDisplayName_withoutUserId_returnsUsername() {
        UserContext ctx = new UserContext("alice", null, null, null,
            UserContext.ExecutionSource.REST_API, null);
        assertThat(ctx.getDisplayName()).isEqualTo("alice");
    }

    @Test
    void getFullDescription_containsAllPresentFields() {
        UserContext ctx = new UserContext("alice", "uid-1", "sess-1", "10.0.0.1",
            UserContext.ExecutionSource.REST_API, "endpoint");
        String desc = ctx.getFullDescription();
        assertThat(desc).contains("alice").contains("uid-1").contains("sess-1")
            .contains("10.0.0.1").contains("REST_API").contains("endpoint");
    }

    @Test
    void getFullDescription_minimalContext_containsUser() {
        UserContext ctx = new UserContext("bob", null, null, null,
            UserContext.ExecutionSource.UNKNOWN, null);
        String desc = ctx.getFullDescription();
        assertThat(desc).contains("bob").contains("UNKNOWN");
    }

    // ===== toString =====

    @Test
    void toString_containsUsername() {
        UserContext ctx = new UserContext("alice", UserContext.ExecutionSource.REST_API);
        assertThat(ctx.toString()).contains("alice").contains("REST_API");
    }

    // ===== equals and hashCode =====

    @Test
    void equals_sameUsernameSameId_returnsTrue() {
        UserContext a = new UserContext("alice", "uid-1", "sess-1", "10.0.0.1",
            UserContext.ExecutionSource.REST_API, null);
        UserContext b = new UserContext("alice", "uid-1", "sess-1", "10.0.0.2",
            UserContext.ExecutionSource.DESIGNER, null); // different IP/source don't matter
        assertThat(a).isEqualTo(b);
    }

    @Test
    void equals_differentUsername_returnsFalse() {
        UserContext a = new UserContext("alice", UserContext.ExecutionSource.REST_API);
        UserContext b = new UserContext("bob", UserContext.ExecutionSource.REST_API);
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void equals_nullAndSelf() {
        UserContext ctx = new UserContext("alice", UserContext.ExecutionSource.REST_API);
        assertThat(ctx).isNotEqualTo(null);
        assertThat(ctx).isEqualTo(ctx);
    }

    @Test
    void hashCode_equalContexts_sameHash() {
        UserContext a = new UserContext("alice", "uid-1", "sess-1", null,
            UserContext.ExecutionSource.REST_API, null);
        UserContext b = new UserContext("alice", "uid-1", "sess-1", null,
            UserContext.ExecutionSource.GATEWAY_SCRIPT, null);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    // ===== createdAt =====

    @Test
    void getCreatedAt_isRecentTimestamp() {
        long before = System.currentTimeMillis();
        UserContext ctx = new UserContext("alice", UserContext.ExecutionSource.REST_API);
        long after = System.currentTimeMillis();
        assertThat(ctx.getCreatedAt()).isBetween(before, after);
    }
}
