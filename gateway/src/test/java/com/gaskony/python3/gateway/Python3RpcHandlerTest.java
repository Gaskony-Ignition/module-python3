package com.gaskony.python3.gateway;

import com.inductiveautomation.ignition.common.gson.JsonObject;
import com.inductiveautomation.ignition.common.gson.JsonParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link Python3RpcHandler}.
 *
 * <p>Verifies the JSON shapes returned to the Designer match what the Designer's
 * {@code Python3RestClient} parses, plus null-safety guards and error handling.
 * The authenticated-session gate is overridden to a no-op in the functional
 * tests (the real gate is covered separately), mirroring the module's existing
 * pattern of injecting test seams for security checks.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class Python3RpcHandlerTest {

    @Mock private GatewayHook hook;
    @Mock private Python3ScriptRepository repository;
    @Mock private Python3ScriptModule scriptModule;
    @Mock private PoolManager poolManager;
    @Mock private PythonDistributionManager distributionManager;
    @Mock private Python3PackageManager packageManager;
    @Mock private Python3ProcessPool processPool;

    private Python3RpcHandler handler;

    /** Handler variant whose session gate is a no-op, so functional logic is exercised. */
    private static class TestableHandler extends Python3RpcHandler {
        TestableHandler(GatewayHook hook) {
            super(hook);
        }

        @Override
        void requireDesignerSession() {
            // no-op: transport authentication is not under test here
        }
    }

    @BeforeEach
    void setUp() {
        when(hook.getScriptRepository()).thenReturn(repository);
        when(hook.getScriptModule()).thenReturn(scriptModule);
        handler = new TestableHandler(hook);
    }

    // -------------------------------------------------------------------------
    // Script management
    // -------------------------------------------------------------------------

    @Test
    void listScripts_returnsSuccessWithScriptArray() throws Exception {
        Python3ScriptRepository.ScriptMetadata meta = new Python3ScriptRepository.ScriptMetadata(
            "id-1", "hello", "desc", "Designer", "2026-07-01", "2026-07-01", "folder/sub", "1.0");
        when(repository.listScripts()).thenReturn(List.of(meta));

        JsonObject json = parse(handler.listScripts());

        assertThat(json.get("success").getAsBoolean()).isTrue();
        assertThat(json.getAsJsonArray("scripts")).hasSize(1);
        JsonObject s = json.getAsJsonArray("scripts").get(0).getAsJsonObject();
        assertThat(s.get("name").getAsString()).isEqualTo("hello");
        assertThat(s.get("folderPath").getAsString()).isEqualTo("folder/sub");
        assertThat(s.get("version").getAsString()).isEqualTo("1.0");
    }

    @Test
    void listScripts_nullRepository_returnsError() throws Exception {
        when(hook.getScriptRepository()).thenReturn(null);

        JsonObject json = parse(handler.listScripts());

        assertThat(json.get("success").getAsBoolean()).isFalse();
    }

    @Test
    void loadScript_found_returnsScriptWithCode() throws Exception {
        Python3ScriptRepository.SavedScript saved = new Python3ScriptRepository.SavedScript(
            "id-1", "hello", "print('hi')", "desc", "Designer",
            "2026-07-01", "2026-07-01", "", "1.0");
        when(repository.loadScript("hello")).thenReturn(saved);

        JsonObject json = parse(handler.loadScript("hello"));

        assertThat(json.get("success").getAsBoolean()).isTrue();
        JsonObject script = json.getAsJsonObject("script");
        assertThat(script.get("code").getAsString()).isEqualTo("print('hi')");
        assertThat(script.get("name").getAsString()).isEqualTo("hello");
    }

    @Test
    void loadScript_notFound_returnsFailureWithMessage() throws Exception {
        when(repository.loadScript("missing")).thenReturn(null);

        JsonObject json = parse(handler.loadScript("missing"));

        assertThat(json.get("success").getAsBoolean()).isFalse();
        assertThat(json.get("message").getAsString()).contains("missing");
    }

    @Test
    void saveScript_delegatesToRepositoryAndReturnsSuccess() throws Exception {
        JsonObject json = parse(handler.saveScript(
            "hello", "print('hi')", "desc", "Designer", "folder", "1.0"));

        assertThat(json.get("success").getAsBoolean()).isTrue();
        verify(repository).saveScript("hello", "print('hi')", "desc", "Designer", "folder", "1.0");
    }

    @Test
    void saveScript_nullRepository_returnsError() throws Exception {
        when(hook.getScriptRepository()).thenReturn(null);

        JsonObject json = parse(handler.saveScript("n", "c", "d", "a", "f", "v"));

        assertThat(json.get("success").getAsBoolean()).isFalse();
    }

    @Test
    void deleteScript_deleted_returnsSuccess() throws Exception {
        when(repository.deleteScript("hello")).thenReturn(true);

        JsonObject json = parse(handler.deleteScript("hello"));

        assertThat(json.get("success").getAsBoolean()).isTrue();
        assertThat(json.get("message").getAsString()).contains("deleted");
    }

    @Test
    void deleteScript_notFound_returnsFailure() throws Exception {
        when(repository.deleteScript("missing")).thenReturn(false);

        JsonObject json = parse(handler.deleteScript("missing"));

        assertThat(json.get("success").getAsBoolean()).isFalse();
        assertThat(json.get("message").getAsString()).contains("missing");
    }

    // -------------------------------------------------------------------------
    // Execution
    // -------------------------------------------------------------------------

    @Test
    void exec_success_returnsResult() throws Exception {
        // Handler calls the trusted (ungated) core directly (v4.3.0): the Designer
        // RPC path does not depend on the runtime scripting opt-out.
        when(scriptModule.execTrusted(eq("2+2"), anyMap(), eq(""))).thenReturn("4");

        JsonObject json = parse(handler.exec("2+2", "{}", ""));

        assertThat(json.get("success").getAsBoolean()).isTrue();
        assertThat(json.get("result").getAsString()).isEqualTo("4");
    }

    @Test
    void exec_withVersion_usesVersionedOverload() throws Exception {
        when(scriptModule.execTrusted(eq("x=1"), anyMap(), eq("3.11"))).thenReturn("ok");

        JsonObject json = parse(handler.exec("x=1", "{\"a\":1}", "3.11"));

        assertThat(json.get("success").getAsBoolean()).isTrue();
        assertThat(json.get("result").getAsString()).isEqualTo("ok");
        verify(scriptModule).execTrusted(eq("x=1"), anyMap(), eq("3.11"));
    }

    @Test
    void exec_failure_returnsErrorMessage() throws Exception {
        when(scriptModule.execTrusted(anyString(), anyMap(), anyString()))
            .thenThrow(new RuntimeException("boom"));

        JsonObject json = parse(handler.exec("bad", "{}", ""));

        assertThat(json.get("success").getAsBoolean()).isFalse();
        assertThat(json.get("error").getAsString()).isEqualTo("boom");
    }

    /**
     * Regression test: the trusted exec path must remain reachable even when a
     * gateway administrator has opted OUT of runtime {@code system.python3.*}
     * scripting (charter &sect;2, 2026-07-02) — the Designer session gate
     * ({@link Python3RpcHandler#requireDesignerSession()}), not the scripting
     * opt-out property, is what authorises this RPC surface.
     */
    @Test
    void exec_succeedsWhenRuntimeScriptingOptedOut() throws Exception {
        String prop = "ignition.python3.scriptingFunctions.allowed";
        String previous = System.getProperty(prop);
        System.setProperty(prop, "false");
        try {
            when(scriptModule.execTrusted(eq("2+2"), anyMap(), eq(""))).thenReturn("4");

            JsonObject json = parse(handler.exec("2+2", "{}", ""));

            assertThat(json.get("success").getAsBoolean()).isTrue();
            assertThat(json.get("result").getAsString()).isEqualTo("4");
        } finally {
            if (previous == null) {
                System.clearProperty(prop);
            } else {
                System.setProperty(prop, previous);
            }
        }
    }

    @Test
    void eval_success_returnsResult() throws Exception {
        when(scriptModule.evalTrusted(eq("1+1"), anyMap(), isNull())).thenReturn("2");

        JsonObject json = parse(handler.eval("1+1", null, null));

        assertThat(json.get("success").getAsBoolean()).isTrue();
        assertThat(json.get("result").getAsString()).isEqualTo("2");
    }

    @Test
    void eval_failure_returnsErrorMessage() throws Exception {
        when(scriptModule.evalTrusted(anyString(), anyMap(), anyString()))
            .thenThrow(new RuntimeException("nope"));

        JsonObject json = parse(handler.eval("bad", "{}", ""));

        assertThat(json.get("success").getAsBoolean()).isFalse();
        assertThat(json.get("error").getAsString()).isEqualTo("nope");
    }

    // -------------------------------------------------------------------------
    // Monitoring
    // -------------------------------------------------------------------------

    @Test
    void getVersion_derivesPythonVersionField() throws Exception {
        Map<String, Object> version = new HashMap<>();
        version.put("version", "3.11.2");
        when(scriptModule.getVersion()).thenReturn(version);

        JsonObject json = parse(handler.getVersion());

        assertThat(json.get("pythonVersion").getAsString()).isEqualTo("3.11.2");
    }

    @Test
    void getPoolStats_addsHealthCheckStatusWhenMissing() throws Exception {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalSize", 3);
        when(scriptModule.getPoolStats()).thenReturn(stats);
        when(scriptModule.isAvailable()).thenReturn(true);

        JsonObject json = parse(handler.getPoolStats());

        assertThat(json.get("totalSize").getAsInt()).isEqualTo(3);
        assertThat(json.get("healthCheckStatus").getAsString()).isEqualTo("Healthy");
    }

    @Test
    void health_reportsAvailability() throws Exception {
        when(scriptModule.isAvailable()).thenReturn(true);

        JsonObject json = parse(handler.health());

        assertThat(json.get("healthy").getAsBoolean()).isTrue();
        assertThat(json.get("available").getAsBoolean()).isTrue();
        assertThat(json.get("status").getAsString()).isEqualTo("HEALTHY");
    }

    // -------------------------------------------------------------------------
    // Diagnostics, environment visibility (v4.3.0)
    // -------------------------------------------------------------------------

    @Test
    void getDiagnostics_returnsComprehensiveResponse() throws Exception {
        when(scriptModule.isAvailable()).thenReturn(true);
        when(scriptModule.getPoolStats()).thenReturn(Map.of("totalSize", 3));
        when(scriptModule.getVersion()).thenReturn(Map.of("version", "3.11.0"));
        when(scriptModule.getDistributionInfo()).thenReturn(Map.of("name", "default"));

        JsonObject json = parse(handler.getDiagnostics());

        assertThat(json.get("available").getAsBoolean()).isTrue();
        assertThat(json.has("poolStats")).isTrue();
        assertThat(json.has("versionInfo")).isTrue();
        assertThat(json.has("distributionInfo")).isTrue();
        assertThat(json.has("timestamp")).isTrue();
    }

    @Test
    void getModuleLogs_delegatesToLogsHandlerWithHardcodedFilter() throws Exception {
        // No system_logs.idb at this path, so Python3LogsHandler.readLogs degrades
        // gracefully (success=true, empty entries, warning set) — exercising the
        // real delegation without needing a live SQLite log database.
        when(hook.getLogsDir()).thenReturn(new java.io.File("/nonexistent/logs/dir"));

        JsonObject json = parse(handler.getModuleLogs(50));

        assertThat(json.get("success").getAsBoolean()).isTrue();
        assertThat(json.has("entries")).isTrue();
        assertThat(json.get("count").getAsInt()).isZero();
    }

    @Test
    void getModuleLogs_clampsLineCountToValidRange() throws Exception {
        when(hook.getLogsDir()).thenReturn(null);

        // Should not throw despite an out-of-range request; clamped internally to [1,500].
        JsonObject json = parse(handler.getModuleLogs(10_000));

        assertThat(json.get("success").getAsBoolean()).isTrue();
    }

    @Test
    void getGatewayImpact_returnsImpactData() throws Exception {
        JsonObject json = parse(handler.getGatewayImpact());

        assertThat(json.has("executions_per_minute")).isTrue();
        assertThat(json.has("cpuUsagePercent")).isTrue();
    }

    @Test
    void getVersions_nullPoolManager_fallsBackToScriptModule() throws Exception {
        when(hook.getPoolManager()).thenReturn(null);
        when(scriptModule.getAvailableVersions()).thenReturn(List.of("3.11.0"));

        JsonObject json = parse(handler.getVersions());

        assertThat(json.get("success").getAsBoolean()).isTrue();
        assertThat(json.get("count").getAsInt()).isEqualTo(1);
        assertThat(json.get("default").getAsString()).isEqualTo("default");
    }

    @Test
    void getVersions_emptyVersionList_returnsDefaultPlaceholder() throws Exception {
        when(hook.getPoolManager()).thenReturn(null);
        when(scriptModule.getAvailableVersions()).thenReturn(List.of());

        JsonObject json = parse(handler.getVersions());

        assertThat(json.getAsJsonArray("versions")).hasSize(1);
        assertThat(json.getAsJsonArray("versions").get(0).getAsString()).isEqualTo("default");
    }

    @Test
    void getVersions_withPoolManager_returnsVersionDetails() throws Exception {
        when(hook.getPoolManager()).thenReturn(poolManager);
        when(poolManager.getAvailableVersions()).thenReturn(List.of("3.10", "3.11"));
        when(poolManager.getDefaultVersion()).thenReturn("3.11");
        when(poolManager.getPythonPath("3.10")).thenReturn("/usr/bin/python3.10");
        when(poolManager.getPythonPath("3.11")).thenReturn("/usr/bin/python3.11");
        when(poolManager.getPool("3.11")).thenReturn(processPool);
        when(processPool.getStats()).thenReturn(new Python3ProcessPool.PoolStats(3, 3, 0, 3));
        when(poolManager.getPool("3.10")).thenThrow(new PoolManager.VersionNotAvailableException("no pool"));

        JsonObject json = parse(handler.getVersions());

        assertThat(json.get("success").getAsBoolean()).isTrue();
        assertThat(json.get("default").getAsString()).isEqualTo("3.11");
        JsonObject details = json.getAsJsonObject("details");
        assertThat(details.getAsJsonObject("3.11").get("healthy").getAsInt()).isEqualTo(3);
        assertThat(details.getAsJsonObject("3.10").get("error").getAsString()).isEqualTo("no pool");
    }

    @Test
    void getDistributions_nullDistributionManager_returnsError() throws Exception {
        when(hook.getDistributionManager()).thenReturn(null);

        JsonObject json = parse(handler.getDistributions());

        assertThat(json.get("success").getAsBoolean()).isFalse();
    }

    @Test
    void getDistributions_returnsDistributionArrayWithPoolActiveFlag() throws Exception {
        when(hook.getDistributionManager()).thenReturn(distributionManager);
        when(hook.getPoolManager()).thenReturn(poolManager);
        PythonDistributionManager.VersionStatus status = new PythonDistributionManager.VersionStatus(
            "3.11", "3.11.6", true, true, "/usr/bin/python3.11", 50_000_000L);
        when(distributionManager.getAllVersionStatuses()).thenReturn(List.of(status));
        when(distributionManager.detectOS()).thenReturn("linux");
        when(distributionManager.getInstalledVersions()).thenReturn(List.of("3.11"));
        when(poolManager.isVersionAvailable("3.11")).thenReturn(true);

        JsonObject json = parse(handler.getDistributions());

        assertThat(json.get("success").getAsBoolean()).isTrue();
        assertThat(json.get("os").getAsString()).isEqualTo("linux");
        JsonObject dist = json.getAsJsonArray("distributions").get(0).getAsJsonObject();
        assertThat(dist.get("version").getAsString()).isEqualTo("3.11");
        assertThat(dist.get("poolActive").getAsBoolean()).isTrue();
        assertThat(dist.get("installSizeMB").getAsLong()).isEqualTo(50_000_000L / (1024 * 1024));
    }

    @Test
    void getPackageCatalog_nullPackageManager_returnsError() throws Exception {
        when(hook.getPackageManager()).thenReturn(null);

        JsonObject json = parse(handler.getPackageCatalog());

        assertThat(json.get("success").getAsBoolean()).isFalse();
    }

    @Test
    void getPackageCatalog_marksInstalledFlagsAndMergesPyPIOnlyPackages() throws Exception {
        when(hook.getPackageManager()).thenReturn(packageManager);

        Python3PackageManager.PackageInfo jedi = new Python3PackageManager.PackageInfo();
        jedi.version = "0.19.0";
        jedi.description = "Autocompletion";
        jedi.sizeMb = 2.5;
        jedi.importName = "jedi";

        when(packageManager.getPackageCatalog()).thenReturn(Map.of("jedi", jedi));
        when(packageManager.getInstalledPackages()).thenReturn(Set.of("jedi", "requests"));

        JsonObject json = parse(handler.getPackageCatalog());

        assertThat(json.get("success").getAsBoolean()).isTrue();
        JsonObject packages = json.getAsJsonObject("packages");

        JsonObject jediJson = packages.getAsJsonObject("jedi");
        assertThat(jediJson.get("installed").getAsBoolean()).isTrue();
        assertThat(jediJson.get("importName").getAsString()).isEqualTo("jedi");

        // "requests" is installed but not in the catalog — merged in as a PyPI-only entry.
        JsonObject requestsJson = packages.getAsJsonObject("requests");
        assertThat(requestsJson.get("installed").getAsBoolean()).isTrue();
        assertThat(requestsJson.get("description").getAsString()).isEqualTo("Installed from PyPI");
    }

    @Test
    void checkSyntax_emptyCode_returnsEmptyErrorsWithoutCallingScriptModule() throws Exception {
        JsonObject json = parse(handler.checkSyntax(""));

        assertThat(json.get("success").getAsBoolean()).isTrue();
        assertThat(json.getAsJsonArray("errors")).isEmpty();
    }

    @Test
    void checkSyntax_nullCode_treatedAsEmpty() throws Exception {
        JsonObject json = parse(handler.checkSyntax(null));

        assertThat(json.get("success").getAsBoolean()).isTrue();
        assertThat(json.getAsJsonArray("errors")).isEmpty();
    }

    @Test
    void checkSyntax_returnsErrorsFromScriptModule() throws Exception {
        Map<String, Object> error = new HashMap<>();
        error.put("line", 2);
        error.put("column", 5);
        error.put("message", "invalid syntax");
        error.put("severity", "error");
        when(scriptModule.checkSyntax("def f(:")).thenReturn(Map.of("errors", List.of(error)));

        JsonObject json = parse(handler.checkSyntax("def f(:"));

        assertThat(json.get("success").getAsBoolean()).isTrue();
        JsonObject errJson = json.getAsJsonArray("errors").get(0).getAsJsonObject();
        assertThat(errJson.get("message").getAsString()).isEqualTo("invalid syntax");
        assertThat(errJson.get("line").getAsInt()).isEqualTo(2);
    }

    @Test
    void getCompletions_emptyCode_returnsEmptyCompletionsWithoutCallingScriptModule() throws Exception {
        JsonObject json = parse(handler.getCompletions("", 1, 0));

        assertThat(json.get("success").getAsBoolean()).isTrue();
        assertThat(json.get("count").getAsInt()).isZero();
        assertThat(json.getAsJsonArray("completions")).isEmpty();
    }

    @Test
    void getCompletions_returnsCompletionsFromScriptModule() throws Exception {
        Map<String, Object> completion = new HashMap<>();
        completion.put("text", "append");
        completion.put("type", "function");
        when(scriptModule.getCompletions("x.", 1, 2)).thenReturn(Map.of("completions", List.of(completion)));

        JsonObject json = parse(handler.getCompletions("x.", 1, 2));

        assertThat(json.get("success").getAsBoolean()).isTrue();
        assertThat(json.get("count").getAsInt()).isEqualTo(1);
        JsonObject c = json.getAsJsonArray("completions").get(0).getAsJsonObject();
        assertThat(c.get("text").getAsString()).isEqualTo("append");
    }

    // -------------------------------------------------------------------------
    // Session gate
    // -------------------------------------------------------------------------

    @Test
    void requireDesignerSession_withoutSession_throws() {
        // The real gate (not the test override): outside an RPC dispatch there is no
        // ClientReqSession bound, so it must refuse.
        Python3RpcHandler realHandler = new Python3RpcHandler(hook);
        assertThatThrownBy(realHandler::requireDesignerSession)
            .isInstanceOf(RuntimeException.class);
    }

    private static JsonObject parse(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }
}
