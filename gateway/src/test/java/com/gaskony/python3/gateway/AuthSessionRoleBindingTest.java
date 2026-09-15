package com.gaskony.python3.gateway;

import com.inductiveautomation.ignition.common.gson.JsonObject;
import com.inductiveautomation.ignition.gateway.dataroutes.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression tests for the C14 fix: {@code /auth/session} must bind the issued
 * security mode to the caller's actual Ignition role membership rather than to
 * a self-asserted {@code client_id} field.
 *
 * <p>Pre-fix vulnerability: any authenticated client posting
 * {@code {"client_id":"ignition-designer-x"}} received a {@link SecurityMode#DESIGNER_ADMIN}
 * HMAC token (8h expiry) which bypasses CSRF and unlocks unrestricted Python execution.
 *
 * <p>Post-fix behaviour:
 * <ul>
 *   <li>caller has the {@code Administrator} role → {@code DESIGNER_ADMIN} token issued;
 *   <li>caller has the {@code Designer} role → {@code DESIGNER_ADMIN} token issued;
 *   <li>caller is authenticated but lacks both roles → 403 (no token);
 *   <li>caller is unauthenticated → 403 (no token);
 *   <li>{@code client_id} is informational only and never affects privilege.
 * </ul>
 *
 * @since v3.13.0 (C14 fix)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthSessionRoleBindingTest {

    @Mock private Python3ScriptModule scriptModule;
    @Mock private Python3SecurityService securityService;
    @Mock private RequestContext req;
    @Mock private HttpServletRequest httpReq;
    @Mock private HttpServletResponse res;
    @Mock private HttpSession session;

    private ExecutionHandlers handlers;

    @BeforeEach
    void setUp() {
        when(req.getRequest()).thenReturn(httpReq);
        // session must exist for CSRF token generation in handleCreateSession
        lenient().when(httpReq.getSession(true)).thenReturn(session);
        lenient().when(session.getId()).thenReturn("test-session-id");
        // and authentication path resolves via session.getAttribute("user") != null
        lenient().when(httpReq.getSession(false)).thenReturn(session);
        lenient().when(session.getAttribute("user")).thenReturn("alice");
        lenient().when(httpReq.getRemoteAddr()).thenReturn("127.0.0.1");

        // Bearer token / actor paths default to no
        lenient().when(httpReq.getHeader("Authorization")).thenReturn(null);
        lenient().when(req.getActor()).thenReturn(null);

        // Default: securityService can issue a token
        lenient().when(securityService.generateApiToken(any(SecurityMode.class), anyLong()))
            .thenReturn("payload.signature");

        EndpointContext ctx = new EndpointContext(
            scriptModule, null, null, securityService, null, null, null, null,
            new Python3MetricsCollector()
        );
        handlers = new ExecutionHandlers(ctx);
    }

    @AfterEach
    void tearDown() {
        // Always restore the production resolver after each test.
        ExecutionHandlers.setRoleResolverForTesting(null);
    }

    // ---- mapRolesToSecurityMode unit tests (pure function) -------------------

    static Stream<org.junit.jupiter.params.provider.Arguments> roleMappings() {
        return Stream.of(
            arguments(Collections.emptySet(), null, "empty role set → no token"),
            arguments(Set.of("Operator"), null, "Operator-only → no token"),
            arguments(Set.of("Operator", "Reader"), null, "non-privileged roles → no token"),
            arguments(Set.of("Administrator"), SecurityMode.DESIGNER_ADMIN, "Administrator → DESIGNER_ADMIN"),
            arguments(Set.of("Designer"), SecurityMode.DESIGNER_ADMIN, "Designer → DESIGNER_ADMIN"),
            arguments(Set.of("administrator"), SecurityMode.DESIGNER_ADMIN, "lower-case Administrator → DESIGNER_ADMIN"),
            arguments(Set.of("DESIGNER"), SecurityMode.DESIGNER_ADMIN, "upper-case Designer → DESIGNER_ADMIN"),
            arguments(Set.of("Operator", "Designer"), SecurityMode.DESIGNER_ADMIN, "Designer + Operator → DESIGNER_ADMIN")
        );
    }

    @ParameterizedTest(name = "[{index}] {2}")
    @MethodSource("roleMappings")
    void mapRolesToSecurityMode_isCorrect(Set<String> roles, SecurityMode expected, String description) {
        SecurityMode actual = ExecutionHandlers.mapRolesToSecurityMode(roles);
        assertThat(actual).as(description).isEqualTo(expected);
    }

    @Test
    void mapRolesToSecurityMode_nullRoles_returnsNull() {
        assertThat(ExecutionHandlers.mapRolesToSecurityMode(null)).isNull();
    }

    // ---- handleCreateSession integration tests -------------------------------

    @Test
    @DisplayName("Pre-fix attack: client_id alone no longer mints DESIGNER_ADMIN")
    void clientIdAlone_doesNotMintToken_whenNoRoles() throws Exception {
        // Caller is authenticated (session has "user" attribute) but has zero roles —
        // exactly the pre-fix attack scenario. The handler MUST NOT issue a privileged token.
        ExecutionHandlers.setRoleResolverForTesting(stubResolver(Collections.emptySet()));

        when(httpReq.getContentType()).thenReturn("application/json");
        when(httpReq.getReader()).thenReturn(reader("{\"client_id\":\"ignition-designer-x\"}"));

        JsonObject result = handlers.handleCreateSession(req, res);

        assertThat(result).isNotNull();
        assertThat(result.has("error")).isTrue();
        assertThat(result.get("error").getAsString().toLowerCase())
            .contains("forbidden");
        verify(securityService, never()).generateApiToken(any(SecurityMode.class), anyLong());
    }

    @Test
    @DisplayName("Authenticated Administrator → DESIGNER_ADMIN token issued")
    void administratorRole_mintsDesignerAdmin() throws Exception {
        ExecutionHandlers.setRoleResolverForTesting(stubResolver(Set.of("Administrator")));

        when(httpReq.getContentType()).thenReturn("application/json");
        when(httpReq.getReader()).thenReturn(reader("{\"client_id\":\"gateway-web-ui\"}"));

        JsonObject result = handlers.handleCreateSession(req, res);

        assertThat(result.get("success").getAsBoolean()).isTrue();
        assertThat(result.get("security_mode").getAsString())
            .isEqualTo(SecurityMode.DESIGNER_ADMIN.toString());
        verify(securityService, times(1))
            .generateApiToken(SecurityMode.DESIGNER_ADMIN, 28800L);
    }

    @Test
    @DisplayName("Authenticated Designer → DESIGNER_ADMIN token issued")
    void designerRole_mintsDesignerAdmin() throws Exception {
        ExecutionHandlers.setRoleResolverForTesting(stubResolver(Set.of("Designer")));

        when(httpReq.getContentType()).thenReturn("application/json");
        when(httpReq.getReader()).thenReturn(reader("{\"client_id\":\"ignition-designer-8.3.0\"}"));

        JsonObject result = handlers.handleCreateSession(req, res);

        assertThat(result.get("success").getAsBoolean()).isTrue();
        assertThat(result.get("security_mode").getAsString())
            .isEqualTo(SecurityMode.DESIGNER_ADMIN.toString());
        verify(securityService, times(1))
            .generateApiToken(SecurityMode.DESIGNER_ADMIN, 28800L);
    }

    @Test
    @DisplayName("Authenticated Operator → 403, no token issued")
    void operatorRole_isForbidden() throws Exception {
        ExecutionHandlers.setRoleResolverForTesting(stubResolver(Set.of("Operator")));

        when(httpReq.getContentType()).thenReturn("application/json");
        when(httpReq.getReader()).thenReturn(reader("{\"client_id\":\"ignition-designer-x\"}"));

        JsonObject result = handlers.handleCreateSession(req, res);

        assertThat(result.has("error")).isTrue();
        verify(securityService, never()).generateApiToken(any(SecurityMode.class), anyLong());
    }

    @Test
    @DisplayName("Unauthenticated request → 403, no token issued")
    void unauthenticated_isForbidden() throws Exception {
        // Strip every auth signal: no session, no Bearer, no actor.
        when(httpReq.getSession(false)).thenReturn(null);
        when(httpReq.getHeader("Authorization")).thenReturn(null);
        when(req.getActor()).thenReturn(null);
        ExecutionHandlers.setRoleResolverForTesting(stubResolver(Set.of("Designer")));

        when(httpReq.getContentType()).thenReturn("application/json");
        when(httpReq.getReader()).thenReturn(reader("{\"client_id\":\"ignition-designer-x\"}"));

        JsonObject result = handlers.handleCreateSession(req, res);

        assertThat(result.has("error")).isTrue();
        assertThat(result.get("error").getAsString().toLowerCase())
            .contains("authentication");
        verify(securityService, never()).generateApiToken(any(SecurityMode.class), anyLong());
    }

    @Test
    @DisplayName("client_id is ignored: spoofed designer client_id without role still 403")
    void spoofedClientId_doesNotElevate() throws Exception {
        // Authenticated Operator posts a spoofed Designer client_id — pre-fix this would have
        // succeeded. Post-fix it must be rejected.
        ExecutionHandlers.setRoleResolverForTesting(stubResolver(Set.of("Operator")));

        when(httpReq.getContentType()).thenReturn("application/json");
        when(httpReq.getReader()).thenReturn(reader(
            "{\"client_id\":\"ignition-designer-evil.exploit.6.6.6\"}"));

        JsonObject result = handlers.handleCreateSession(req, res);

        assertThat(result.has("error")).isTrue();
        verify(securityService, never()).generateApiToken(any(SecurityMode.class), anyLong());
    }

    @Test
    @DisplayName("Multiple roles incl. Administrator → DESIGNER_ADMIN even with Operator present")
    void multipleRoles_administratorWins() throws Exception {
        Set<String> roles = new LinkedHashSet<>();
        roles.add("Operator");
        roles.add("Administrator");
        ExecutionHandlers.setRoleResolverForTesting(stubResolver(roles));

        when(httpReq.getContentType()).thenReturn("application/json");
        when(httpReq.getReader()).thenReturn(reader("{\"client_id\":\"gateway-web-ui\"}"));

        JsonObject result = handlers.handleCreateSession(req, res);

        assertThat(result.get("success").getAsBoolean()).isTrue();
        assertThat(result.get("security_mode").getAsString())
            .isEqualTo(SecurityMode.DESIGNER_ADMIN.toString());
    }

    // ---- helpers -------------------------------------------------------------

    private static java.io.BufferedReader reader(String body) {
        return new java.io.BufferedReader(new java.io.StringReader(body));
    }

    /** Returns a {@link RoleResolver} that always reports the given role set. */
    private static RoleResolver stubResolver(Set<String> roles) {
        return new RoleResolver() {
            @Override
            Set<String> getRoles(RequestContext req) {
                return Collections.unmodifiableSet(new LinkedHashSet<>(roles));
            }
        };
    }
}
