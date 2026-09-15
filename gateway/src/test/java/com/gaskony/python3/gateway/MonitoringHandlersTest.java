package com.gaskony.python3.gateway;

import com.inductiveautomation.ignition.common.gson.JsonObject;
import com.inductiveautomation.ignition.gateway.dataroutes.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
 * Unit tests for MonitoringHandlers.
 *
 * Tests verify that handlers return well-formed JSON responses and correctly
 * delegate to their service dependencies. Ignition API classes are mocked.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MonitoringHandlersTest {

    @Mock private Python3ScriptModule scriptModule;
    @Mock private PoolManager poolManager;
    @Mock private RequestContext req;
    @Mock private HttpServletRequest httpReq;
    @Mock private HttpServletResponse res;

    private MonitoringHandlers handlers;

    @BeforeEach
    void setUp() {
        // Reset CSRF state to skip CSRF validation (no session by default)
        when(req.getRequest()).thenReturn(httpReq);
        lenient().when(httpReq.getSession(false)).thenReturn(null);
        lenient().when(httpReq.getHeader("Authorization")).thenReturn(null);
        lenient().when(httpReq.getHeader("X-Source")).thenReturn("Python3-IDE");

        EndpointContext ctx = new EndpointContext(
            scriptModule,
            null,   // scriptRepository
            null,   // packageManager
            null,   // securityService
            null,   // auditLogger
            poolManager,
            null,   // distributionManager
            null,   // logsDir
            new Python3MetricsCollector()
        );

        handlers = new MonitoringHandlers(ctx);
    }

    // ===== handleGetVersion =====

    @Test
    void handleGetVersion_returnsVersionInfo() {
        Map<String, Object> versionInfo = Map.of("version", "3.11.0", "available", true);
        when(scriptModule.getVersion()).thenReturn(versionInfo);

        JsonObject result = handlers.handleGetVersion(req, res);

        assertThat(result).isNotNull();
        assertThat(result.get("version").getAsString()).isEqualTo("3.11.0");
        assertThat(result.get("available").getAsBoolean()).isTrue();
    }

    @Test
    void handleGetVersion_addsPythonVersionFieldAlias() {
        // When server returns python_version but not pythonVersion, we alias it
        Map<String, Object> versionInfo = Map.of("python_version", "3.11.0", "available", true);
        when(scriptModule.getVersion()).thenReturn(versionInfo);

        JsonObject result = handlers.handleGetVersion(req, res);

        assertThat(result.has("pythonVersion")).isTrue();
        assertThat(result.get("pythonVersion").getAsString()).isEqualTo("3.11.0");
    }

    @Test
    void handleGetVersion_scriptModuleNull_returnsError() {
        // Rebuild handlers with null scriptModule
        EndpointContext ctx = new EndpointContext(
            null, null, null, null, null, null, null, null, new Python3MetricsCollector()
        );
        MonitoringHandlers h = new MonitoringHandlers(ctx);

        JsonObject result = h.handleGetVersion(req, res);

        // Should return an error response (withHandler catches NullPointerException)
        assertThat(result).isNotNull();
        assertThat(result.has("success") || result.has("error")).isTrue();
    }

    // ===== handleGetPoolStats =====

    @Test
    void handleGetPoolStats_returnsStats() {
        Map<String, Object> stats = Map.of(
            "poolSize", 3,
            "available", 2,
            "inUse", 1,
            "healthy", 3
        );
        when(scriptModule.getPoolStats()).thenReturn(stats);
        when(scriptModule.isAvailable()).thenReturn(true);

        JsonObject result = handlers.handleGetPoolStats(req, res);

        assertThat(result).isNotNull();
        assertThat(result.get("poolSize").getAsInt()).isEqualTo(3);
        assertThat(result.get("available").getAsInt()).isEqualTo(2);
    }

    @Test
    void handleGetPoolStats_addsHealthCheckStatusWhenMissing() {
        Map<String, Object> stats = Map.of("poolSize", 3);
        when(scriptModule.getPoolStats()).thenReturn(stats);
        when(scriptModule.isAvailable()).thenReturn(true);

        JsonObject result = handlers.handleGetPoolStats(req, res);

        assertThat(result.has("healthCheckStatus")).isTrue();
        assertThat(result.get("healthCheckStatus").getAsString()).isEqualTo("Healthy");
    }

    @Test
    void handleGetPoolStats_addsDownStatusWhenUnavailable() {
        Map<String, Object> stats = Map.of("poolSize", 0);
        when(scriptModule.getPoolStats()).thenReturn(stats);
        when(scriptModule.isAvailable()).thenReturn(false);

        JsonObject result = handlers.handleGetPoolStats(req, res);

        assertThat(result.get("healthCheckStatus").getAsString()).isEqualTo("Down");
    }

    // ===== handleHealthCheck =====

    @Test
    void handleHealthCheck_whenAvailable_returnsHealthy() {
        when(scriptModule.isAvailable()).thenReturn(true);

        JsonObject result = handlers.handleHealthCheck(req, res);

        assertThat(result).isNotNull();
        assertThat(result.get("status").getAsString()).isEqualTo("HEALTHY");
        assertThat(result.get("available").getAsBoolean()).isTrue();
        assertThat(result.get("healthy").getAsBoolean()).isTrue();
    }

    @Test
    void handleHealthCheck_whenUnavailable_returnsDown() {
        when(scriptModule.isAvailable()).thenReturn(false);

        JsonObject result = handlers.handleHealthCheck(req, res);

        assertThat(result).isNotNull();
        assertThat(result.get("status").getAsString()).isEqualTo("DOWN");
        assertThat(result.get("available").getAsBoolean()).isFalse();
        assertThat(result.get("healthy").getAsBoolean()).isFalse();
    }

    // ===== handleDiagnostics =====

    @Test
    void handleDiagnostics_returnsAvailableAndStats() {
        when(scriptModule.isAvailable()).thenReturn(true);
        when(scriptModule.getPoolStats()).thenReturn(Map.of("poolSize", 3, "available", 2));
        when(scriptModule.getVersion()).thenReturn(Map.of("version", "3.11.0"));
        when(scriptModule.getDistributionInfo()).thenReturn(Map.of("name", "default"));

        JsonObject result = handlers.handleDiagnostics(req, res);

        assertThat(result).isNotNull();
        assertThat(result.get("available").getAsBoolean()).isTrue();
        assertThat(result.has("poolStats")).isTrue();
        assertThat(result.has("versionInfo")).isTrue();
        assertThat(result.has("timestamp")).isTrue();
    }

    @Test
    void handleDiagnostics_whenUnavailable_stillReturnsResponse() {
        when(scriptModule.isAvailable()).thenReturn(false);
        when(scriptModule.getPoolStats()).thenReturn(Collections.emptyMap());
        when(scriptModule.getVersion()).thenReturn(Collections.emptyMap());
        when(scriptModule.getDistributionInfo()).thenReturn(Collections.emptyMap());

        JsonObject result = handlers.handleDiagnostics(req, res);

        assertThat(result).isNotNull();
        assertThat(result.get("available").getAsBoolean()).isFalse();
    }

    // ===== handleGetMetrics =====

    @Test
    void handleGetMetrics_returnsMetricsFromCollector() {
        JsonObject result = handlers.handleGetMetrics(req, res);

        // metricsCollector is a real Python3MetricsCollector (starts empty)
        // mapToJson(emptyMap) returns an empty JsonObject — not null
        assertThat(result).isNotNull();
    }

    // ===== handleGetGatewayImpact =====

    @Test
    void handleGetGatewayImpact_returnsImpactData() {
        JsonObject result = handlers.handleGetGatewayImpact(req, res);

        assertThat(result).isNotNull();
    }

    // ===== handleGetScriptMetrics =====

    @Test
    void handleGetScriptMetrics_returnsEmptyList() {
        JsonObject result = handlers.handleGetScriptMetrics(req, res);

        assertThat(result).isNotNull();
        assertThat(result.get("success").getAsBoolean()).isTrue();
        assertThat(result.has("script_metrics")).isTrue();
        assertThat(result.get("count").getAsInt()).isEqualTo(0);
    }

    // ===== handleGetHistoricalMetrics =====

    @Test
    void handleGetHistoricalMetrics_returnsEmptyHistory() {
        JsonObject result = handlers.handleGetHistoricalMetrics(req, res);

        assertThat(result).isNotNull();
        assertThat(result.get("success").getAsBoolean()).isTrue();
        assertThat(result.has("historical_metrics")).isTrue();
    }

    // ===== handleGetHealthAlerts =====

    @Test
    void handleGetHealthAlerts_returnsEmptyAlerts() {
        JsonObject result = handlers.handleGetHealthAlerts(req, res);

        assertThat(result).isNotNull();
        assertThat(result.get("success").getAsBoolean()).isTrue();
        assertThat(result.has("alerts")).isTrue();
    }

    // ===== handleGetVersions (with null poolManager — uses scriptModule fallback) =====

    @Test
    void handleGetVersions_nullPoolManager_fallsBackToScriptModule() {
        // Build handlers with null poolManager to exercise the else branch
        EndpointContext noPoolCtx = new EndpointContext(
            scriptModule, null, null, null, null, null, null, null, new Python3MetricsCollector()
        );
        MonitoringHandlers h = new MonitoringHandlers(noPoolCtx);
        when(scriptModule.getAvailableVersions()).thenReturn(List.of("3.11.0"));

        JsonObject result = h.handleGetVersions(req, res);

        assertThat(result).isNotNull();
        assertThat(result.get("success").getAsBoolean()).isTrue();
        assertThat(result.has("versions")).isTrue();
        assertThat(result.get("count").getAsInt()).isEqualTo(1);
    }

    @Test
    void handleGetVersions_emptyVersionList_returnsDefault() {
        EndpointContext noPoolCtx = new EndpointContext(
            scriptModule, null, null, null, null, null, null, null, new Python3MetricsCollector()
        );
        MonitoringHandlers h = new MonitoringHandlers(noPoolCtx);
        when(scriptModule.getAvailableVersions()).thenReturn(Collections.emptyList());

        JsonObject result = h.handleGetVersions(req, res);

        assertThat(result).isNotNull();
        assertThat(result.get("count").getAsInt()).isEqualTo(1); // adds "default"
    }

    // ===== handleGetEnhancedMetrics — null processPool path =====

    @Test
    void handleGetEnhancedMetrics_nullProcessPool_returnsError() {
        when(scriptModule.getProcessPool()).thenReturn(null);

        JsonObject result = handlers.handleGetEnhancedMetrics(req, res);

        assertThat(result).isNotNull();
        assertThat(result.has("error")).isTrue();
    }

    // ===== handleGetCircuitBreakerStatus — null processPool path =====

    @Test
    void handleGetCircuitBreakerStatus_nullProcessPool_returnsError() {
        when(scriptModule.getProcessPool()).thenReturn(null);

        JsonObject result = handlers.handleGetCircuitBreakerStatus(req, res);

        assertThat(result).isNotNull();
        assertThat(result.has("error")).isTrue();
    }

    // ===== handleGetAlertManagerStatus — null processPool path =====

    @Test
    void handleGetAlertManagerStatus_nullProcessPool_returnsError() {
        when(scriptModule.getProcessPool()).thenReturn(null);

        JsonObject result = handlers.handleGetAlertManagerStatus(req, res);

        assertThat(result).isNotNull();
        assertThat(result.has("error")).isTrue();
    }

    // ===== handleSetPoolSize =====

    @Test
    void handleSetPoolSize_missingSizeField_returnsError() throws Exception {
        when(httpReq.getContentType()).thenReturn("application/json");
        when(httpReq.getReader()).thenReturn(
            new java.io.BufferedReader(new java.io.StringReader("{}"))
        );

        JsonObject result = handlers.handleSetPoolSize(req, res);

        assertThat(result).isNotNull();
        assertThat(result.has("error")).isTrue();
    }

    @Test
    void handleSetPoolSize_invalidSize_returnsError() throws Exception {
        when(httpReq.getContentType()).thenReturn("application/json");
        when(httpReq.getReader()).thenReturn(
            new java.io.BufferedReader(new java.io.StringReader("{\"size\": 999}"))
        );

        JsonObject result = handlers.handleSetPoolSize(req, res);

        assertThat(result).isNotNull();
        assertThat(result.has("error")).isTrue();
    }

    @Test
    void handleSetPoolSize_validSize_returnsSuccess() throws Exception {
        when(httpReq.getContentType()).thenReturn("application/json");
        when(httpReq.getReader()).thenReturn(
            new java.io.BufferedReader(new java.io.StringReader("{\"size\": 5}"))
        );

        JsonObject result = handlers.handleSetPoolSize(req, res);

        assertThat(result).isNotNull();
        assertThat(result.get("success").getAsBoolean()).isTrue();
        assertThat(result.get("poolSize").getAsInt()).isEqualTo(5);
        verify(scriptModule).resizePool(5);
    }

    // ===== handleGetLogs =====

    @Test
    void handleGetLogs_returnsResponse() {
        lenient().when(httpReq.getParameter("lines")).thenReturn("50");
        lenient().when(httpReq.getParameter("level")).thenReturn(null);
        lenient().when(httpReq.getParameter("filter")).thenReturn(null);
        lenient().when(httpReq.getParameter("after")).thenReturn(null);

        // logsDir is null in setUp — Python3LogsHandler.readLogs handles null logsDir gracefully
        JsonObject result = handlers.handleGetLogs(req, res);

        assertThat(result).isNotNull();
    }

    // ===== handleGetDistributions =====

    @Test
    void handleGetDistributions_nullDistributionManager_returnsError() {
        // distributionManager is null in setUp EndpointContext
        JsonObject result = handlers.handleGetDistributions(req, res);

        assertThat(result).isNotNull();
        assertThat(result.has("error")).isTrue();
    }

    // ===== handleInstallDistribution =====

    @Test
    void handleInstallDistribution_nullDistributionManager_returnsError() throws Exception {
        when(httpReq.getContentType()).thenReturn("application/json");
        when(httpReq.getReader()).thenReturn(
            new java.io.BufferedReader(new java.io.StringReader("{\"version\": \"3.12\"}"))
        );

        JsonObject result = handlers.handleInstallDistribution(req, res);

        assertThat(result).isNotNull();
        assertThat(result.has("error")).isTrue();
    }

    // ===== handleUninstallDistribution =====

    @Test
    void handleUninstallDistribution_nullDistributionManager_returnsError() throws Exception {
        when(httpReq.getContentType()).thenReturn("application/json");
        when(httpReq.getReader()).thenReturn(
            new java.io.BufferedReader(new java.io.StringReader("{\"version\": \"3.12\"}"))
        );

        JsonObject result = handlers.handleUninstallDistribution(req, res);

        assertThat(result).isNotNull();
        assertThat(result.has("error")).isTrue();
    }
}
