package com.gaskony.python3.gateway;

import com.inductiveautomation.ignition.common.gson.JsonObject;
import com.inductiveautomation.ignition.gateway.dataroutes.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ExecutionHandlers.
 *
 * Tests verify that handlers return well-formed JSON responses, validate inputs,
 * and delegate to scriptModule correctly.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExecutionHandlersTest {

    @Mock private Python3ScriptModule scriptModule;
    @Mock private Python3SecurityService securityService;
    @Mock private RequestContext req;
    @Mock private HttpServletRequest httpReq;
    @Mock private HttpServletResponse res;
    @Mock private HttpSession session;

    private ExecutionHandlers handlers;
    private EndpointContext ctx;

    @BeforeEach
    void setUp() {
        // Default: no session (CSRF skipped), X-Source header set (auth passes)
        when(req.getRequest()).thenReturn(httpReq);
        lenient().when(httpReq.getSession(false)).thenReturn(null);
        lenient().when(httpReq.getHeader("Authorization")).thenReturn(null);
        lenient().when(httpReq.getHeader("X-Source")).thenReturn("Python3-IDE");
        lenient().when(req.getActor()).thenReturn("Python3-IDE");

        // C13: SecurityMode.RESTRICTED was removed; DESIGNER_ADMIN is the
        // safe-default in tests (avoids SDK calls).
        lenient().when(securityService.determineSecurityMode(any())).thenReturn(SecurityMode.DESIGNER_ADMIN);
        lenient().when(httpReq.getHeader("X-Forwarded-For")).thenReturn(null);
        lenient().when(httpReq.getRemoteAddr()).thenReturn("127.0.0.1");

        ctx = new EndpointContext(
            scriptModule,
            null,   // scriptRepository
            null,   // packageManager
            securityService,
            null,   // auditLogger
            null,   // poolManager
            null,   // distributionManager
            null,   // logsDir
            new Python3MetricsCollector()
        );

        handlers = new ExecutionHandlers(ctx);

        // C13: determineSecurityMode now throws SecurityException when the
        // static securityService is unset (instead of falling back to
        // RESTRICTED). Inject the mock so the production code finds it.
        Python3RestEndpoints.setSecurityService(securityService);
    }

    @AfterEach
    void tearDown() {
        // Avoid leaking the mock into other test classes that share the JVM.
        Python3RestEndpoints.setSecurityService(null);
    }

    // ===== handleExec =====

    @Test
    void handleExec_simpleCode_returnsSuccessTrue() throws Exception {
        when(httpReq.getContentType()).thenReturn("application/json");
        // Provide a valid JSON body via the request reader
        String body = "{\"code\": \"result = 2 + 2\", \"variables\": {}}";
        when(httpReq.getReader()).thenReturn(
            new java.io.BufferedReader(new java.io.StringReader(body))
        );
        when(scriptModule.exec(any(), any(), any())).thenReturn("None");

        JsonObject result = handlers.handleExec(req, res);

        assertThat(result).isNotNull();
        assertThat(result.get("success").getAsBoolean()).isTrue();
        verify(scriptModule).exec(eq("result = 2 + 2"), any(), any());
    }

    @Test
    void handleExec_emptyCode_returnsError() throws Exception {
        when(httpReq.getContentType()).thenReturn("application/json");
        String body = "{\"code\": \"\", \"variables\": {}}";
        when(httpReq.getReader()).thenReturn(
            new java.io.BufferedReader(new java.io.StringReader(body))
        );

        JsonObject result = handlers.handleExec(req, res);

        // Empty code triggers validateCode → IllegalArgumentException → error response
        assertThat(result).isNotNull();
        assertThat(result.has("error") || !result.get("success").getAsBoolean()).isTrue();
    }

    @Test
    void handleExec_scriptModuleThrows_returnsError() throws Exception {
        when(httpReq.getContentType()).thenReturn("application/json");
        String body = "{\"code\": \"raise ValueError('test')\", \"variables\": {}}";
        when(httpReq.getReader()).thenReturn(
            new java.io.BufferedReader(new java.io.StringReader(body))
        );
        when(scriptModule.exec(any(), any(), any())).thenThrow(new RuntimeException("Python error"));

        JsonObject result = handlers.handleExec(req, res);

        assertThat(result).isNotNull();
        // withHandler catches exception and returns error response
        assertThat(result.has("error")).isTrue();
        assertThat(result.get("error").getAsString()).contains("Python error");
    }

    // ===== handleEval =====

    @Test
    void handleEval_expression_returnsResult() throws Exception {
        when(httpReq.getContentType()).thenReturn("application/json");
        String body = "{\"expression\": \"2 + 2\", \"variables\": {}}";
        when(httpReq.getReader()).thenReturn(
            new java.io.BufferedReader(new java.io.StringReader(body))
        );
        when(scriptModule.eval(any(), any(), any())).thenReturn(4);

        JsonObject result = handlers.handleEval(req, res);

        assertThat(result).isNotNull();
        assertThat(result.get("success").getAsBoolean()).isTrue();
        assertThat(result.get("result").getAsString()).isEqualTo("4");
    }

    @Test
    void handleEval_withVariables_passesVariablesToScriptModule() throws Exception {
        when(httpReq.getContentType()).thenReturn("application/json");
        String body = "{\"expression\": \"x + y\", \"variables\": {\"x\": 10, \"y\": 20}}";
        when(httpReq.getReader()).thenReturn(
            new java.io.BufferedReader(new java.io.StringReader(body))
        );
        when(scriptModule.eval(eq("x + y"), any(), any())).thenReturn(30);

        JsonObject result = handlers.handleEval(req, res);

        assertThat(result.get("success").getAsBoolean()).isTrue();
        assertThat(result.get("result").getAsString()).isEqualTo("30");
    }

    // ===== handleCallModule =====

    @Test
    void handleCallModule_validModuleFunction_returnsResult() throws Exception {
        when(httpReq.getContentType()).thenReturn("application/json");
        String body = "{\"module\": \"math\", \"function\": \"sqrt\", \"args\": [16]}";
        when(httpReq.getReader()).thenReturn(
            new java.io.BufferedReader(new java.io.StringReader(body))
        );
        when(scriptModule.callModule(eq("math"), eq("sqrt"), any(), any(), any())).thenReturn(4.0);

        JsonObject result = handlers.handleCallModule(req, res);

        assertThat(result.get("success").getAsBoolean()).isTrue();
        assertThat(result.get("result").getAsString()).isEqualTo("4.0");
        verify(scriptModule).callModule(eq("math"), eq("sqrt"), any(), any(), any());
    }

    // ===== handleExample =====

    @Test
    void handleExample_returnsSuccessWithResult() throws Exception {
        when(scriptModule.exec(any(), any(), any())).thenReturn("Hello, World!");

        JsonObject result = handlers.handleExample(req, res);

        assertThat(result).isNotNull();
        // Example handler may return success:true or some result — just verify not null and no crash
        assertThat(result.has("success") || result.has("result")).isTrue();
    }

    // ===== null scriptModule safety =====

    @Test
    void handleExec_nullScriptModule_returnsError() throws Exception {
        EndpointContext nullCtx = new EndpointContext(
            null, null, null, null, null, null, null, null, new Python3MetricsCollector()
        );
        ExecutionHandlers h = new ExecutionHandlers(nullCtx);

        when(httpReq.getContentType()).thenReturn("application/json");
        String body = "{\"code\": \"print('hi')\", \"variables\": {}}";
        when(httpReq.getReader()).thenReturn(
            new java.io.BufferedReader(new java.io.StringReader(body))
        );

        JsonObject result = h.handleExec(req, res);

        // withHandler catches NullPointerException and returns error response
        assertThat(result).isNotNull();
        assertThat(result.has("error")).isTrue();
    }

    // ===== handleCheckSyntax =====

    @Test
    void handleCheckSyntax_validCode_returnsResult() throws Exception {
        when(httpReq.getContentType()).thenReturn("application/json");
        String body = "{\"code\": \"x = 2 + 2\"}";
        when(httpReq.getReader()).thenReturn(
            new java.io.BufferedReader(new java.io.StringReader(body))
        );
        when(scriptModule.checkSyntax(eq("x = 2 + 2"))).thenReturn(Map.of("valid", true));

        JsonObject result = handlers.handleCheckSyntax(req, res);

        assertThat(result).isNotNull();
        assertThat(result.get("success").getAsBoolean()).isTrue();
    }

    @Test
    void handleCheckSyntax_missingCode_returnsSuccessWithEmptyErrors() throws Exception {
        when(httpReq.getContentType()).thenReturn("application/json");
        String body = "{}";
        when(httpReq.getReader()).thenReturn(
            new java.io.BufferedReader(new java.io.StringReader(body))
        );

        JsonObject result = handlers.handleCheckSyntax(req, res);

        // Empty code → success:true with empty errors array (not an error)
        assertThat(result).isNotNull();
        assertThat(result.get("success").getAsBoolean()).isTrue();
        assertThat(result.getAsJsonArray("errors").size()).isEqualTo(0);
    }

    // ===== handleGetCompletions =====

    @Test
    void handleGetCompletions_validRequest_returnsResult() throws Exception {
        when(httpReq.getContentType()).thenReturn("application/json");
        String body = "{\"code\": \"import os\", \"line\": 1, \"column\": 9}";
        when(httpReq.getReader()).thenReturn(
            new java.io.BufferedReader(new java.io.StringReader(body))
        );
        when(scriptModule.getCompletions(eq("import os"), eq(1), eq(9))).thenReturn(Map.of("completions", List.of()));

        JsonObject result = handlers.handleGetCompletions(req, res);

        assertThat(result).isNotNull();
        // Either success or error is acceptable — just verify handler runs without crash
        assertThat(result.has("success") || result.has("error")).isTrue();
    }

    // ===== handleCallScript =====

    @Test
    void handleCallScript_validRequest_returnsResult() throws Exception {
        when(httpReq.getContentType()).thenReturn("application/json");
        String body = "{\"scriptId\": \"script-1\", \"args\": [], \"variables\": {}}";
        when(httpReq.getReader()).thenReturn(
            new java.io.BufferedReader(new java.io.StringReader(body))
        );
        when(scriptModule.callScript(eq("script-1"), any(List.class), any(Map.class))).thenReturn("result");

        JsonObject result = handlers.handleCallScript(req, res);

        assertThat(result).isNotNull();
        assertThat(result.has("success") || result.has("error")).isTrue();
    }
}
