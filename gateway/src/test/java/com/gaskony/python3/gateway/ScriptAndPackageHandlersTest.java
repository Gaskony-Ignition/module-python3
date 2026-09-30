package com.gaskony.python3.gateway;

import com.inductiveautomation.ignition.common.gson.JsonObject;
import com.inductiveautomation.ignition.gateway.dataroutes.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ScriptAndPackageHandlers.
 *
 * Verifies null-safety guards, input validation rejection, and delegation to
 * the script repository mock.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScriptAndPackageHandlersTest {

    @Mock private Python3ScriptRepository scriptRepository;
    @Mock private Python3PackageManager packageManager;
    @Mock private Python3SecurityService securityService;
    @Mock private RequestContext req;
    @Mock private HttpServletRequest httpReq;
    @Mock private HttpServletResponse res;

    private ScriptAndPackageHandlers handlers;

    @BeforeEach
    void setUp() {
        when(req.getRequest()).thenReturn(httpReq);
        lenient().when(httpReq.getSession(false)).thenReturn(null);
        lenient().when(httpReq.getHeader("Authorization")).thenReturn(null);
        lenient().when(httpReq.getHeader("X-Source")).thenReturn("Python3-IDE");

        EndpointContext ctx = new EndpointContext(
            null,            // scriptModule
            scriptRepository,
            packageManager,
            null,            // securityService
            null,            // auditLogger
            null,            // poolManager
            null,            // distributionManager
            null,            // logsDir
            new Python3MetricsCollector()
        );

        handlers = new ScriptAndPackageHandlers(ctx);

        // Package install/uninstall are code-execution endpoints and are gated
        // by determineSecurityMode(), exactly like /exec. Tests must therefore
        // present an authenticated caller — see the denial tests below.
        lenient().when(securityService.determineSecurityMode(any()))
            .thenReturn(SecurityMode.ADMIN);
        Python3RestEndpoints.setSecurityService(securityService);
    }

    @AfterEach
    void tearDown() {
        Python3RestEndpoints.setSecurityService(null);
    }

    // ===== handleSaveScript =====

    @Test
    void handleSaveScript_missingName_returnsError() throws Exception {
        when(httpReq.getContentType()).thenReturn("application/json");
        String body = "{\"code\": \"print('hi')\"}"; // No "name" field
        when(httpReq.getReader()).thenReturn(
            new java.io.BufferedReader(new java.io.StringReader(body))
        );

        JsonObject result = handlers.handleSaveScript(req, res);

        assertThat(result).isNotNull();
        assertThat(result.has("error")).isTrue();
        assertThat(result.get("error").getAsString()).contains("name");
    }

    @Test
    void handleSaveScript_invalidNameChars_returnsError() throws Exception {
        when(httpReq.getContentType()).thenReturn("application/json");
        String body = "{\"name\": \"my/script!\", \"code\": \"print('hi')\"}";
        when(httpReq.getReader()).thenReturn(
            new java.io.BufferedReader(new java.io.StringReader(body))
        );

        JsonObject result = handlers.handleSaveScript(req, res);

        assertThat(result.has("error")).isTrue();
    }

    @Test
    void handleSaveScript_nullRepository_returnsError() throws Exception {
        EndpointContext noRepoCtx = new EndpointContext(
            null, null, null, null, null, null, null, null, new Python3MetricsCollector()
        );
        ScriptAndPackageHandlers h = new ScriptAndPackageHandlers(noRepoCtx);

        when(httpReq.getContentType()).thenReturn("application/json");
        String body = "{\"name\": \"myscript\", \"code\": \"print('hi')\"}";
        when(httpReq.getReader()).thenReturn(
            new java.io.BufferedReader(new java.io.StringReader(body))
        );

        JsonObject result = h.handleSaveScript(req, res);

        assertThat(result.has("error")).isTrue();
        assertThat(result.get("error").getAsString()).contains("repository");
    }

    @Test
    void handleSaveScript_validRequest_callsRepository() throws Exception {
        when(httpReq.getContentType()).thenReturn("application/json");
        String body = "{\"name\": \"myscript\", \"code\": \"print('hello')\", \"folderPath\": \"\"}";
        when(httpReq.getReader()).thenReturn(
            new java.io.BufferedReader(new java.io.StringReader(body))
        );

        // Mock the saved script return
        Python3ScriptRepository.SavedScript savedScript = mock(Python3ScriptRepository.SavedScript.class);
        when(savedScript.getId()).thenReturn("id-1");
        when(savedScript.getName()).thenReturn("myscript");
        when(savedScript.getDescription()).thenReturn(null);
        when(savedScript.getAuthor()).thenReturn("Unknown");
        when(savedScript.getCreatedDate()).thenReturn("2026-02-22");
        when(savedScript.getLastModified()).thenReturn("2026-02-22");
        when(savedScript.getFolderPath()).thenReturn("");
        when(savedScript.getVersion()).thenReturn("1.0");

        when(scriptRepository.saveScript(
            eq("myscript"), eq("print('hello')"), any(), any(), any(), any()
        )).thenReturn(savedScript);

        JsonObject result = handlers.handleSaveScript(req, res);

        assertThat(result.get("success").getAsBoolean()).isTrue();
        assertThat(result.has("script")).isTrue();
        assertThat(result.getAsJsonObject("script").get("name").getAsString()).isEqualTo("myscript");

        verify(scriptRepository).saveScript(
            eq("myscript"), eq("print('hello')"), any(), any(), any(), any()
        );
    }

    // ===== handleListScripts =====

    @Test
    void handleListScripts_returnsEmptyList() throws Exception {
        when(scriptRepository.listScripts()).thenReturn(Collections.emptyList());

        JsonObject result = handlers.handleListScripts(req, res);

        assertThat(result.get("success").getAsBoolean()).isTrue();
        assertThat(result.has("scripts")).isTrue();
        assertThat(result.getAsJsonArray("scripts").size()).isEqualTo(0);
    }

    @Test
    void handleListScripts_returnsScripts() throws Exception {
        Python3ScriptRepository.ScriptMetadata metadata = mock(Python3ScriptRepository.ScriptMetadata.class);
        when(metadata.getName()).thenReturn("myscript");
        when(metadata.getId()).thenReturn("id-1");
        when(metadata.getDescription()).thenReturn("A test script");
        when(metadata.getAuthor()).thenReturn("Test");
        when(metadata.getCreatedDate()).thenReturn("2026-02-22");
        when(metadata.getLastModified()).thenReturn("2026-02-22");
        when(metadata.getFolderPath()).thenReturn("");
        when(metadata.getVersion()).thenReturn("1.0");

        when(scriptRepository.listScripts()).thenReturn(List.of(metadata));

        JsonObject result = handlers.handleListScripts(req, res);

        assertThat(result.get("success").getAsBoolean()).isTrue();
        assertThat(result.getAsJsonArray("scripts").size()).isEqualTo(1);
        assertThat(result.getAsJsonArray("scripts").get(0).getAsJsonObject()
            .get("name").getAsString()).isEqualTo("myscript");
    }

    @Test
    void handleListScripts_nullRepository_returnsError() throws Exception {
        EndpointContext noRepoCtx = new EndpointContext(
            null, null, null, null, null, null, null, null, new Python3MetricsCollector()
        );
        ScriptAndPackageHandlers h = new ScriptAndPackageHandlers(noRepoCtx);

        JsonObject result = h.handleListScripts(req, res);

        assertThat(result.has("error")).isTrue();
    }

    // ===== handleDeleteScript =====

    @Test
    void handleDeleteScript_nullRepository_returnsError() throws Exception {
        EndpointContext noRepoCtx = new EndpointContext(
            null, null, null, null, null, null, null, null, new Python3MetricsCollector()
        );
        ScriptAndPackageHandlers h = new ScriptAndPackageHandlers(noRepoCtx);

        when(httpReq.getRequestURI()).thenReturn("/api/v1/scripts/delete/myscript");

        JsonObject result = h.handleDeleteScript(req, res);

        assertThat(result.has("error")).isTrue();
    }

    // ===== handleGetPackageCatalog =====

    @Test
    void handleGetPackageCatalog_nullPackageManager_returnsError() throws Exception {
        EndpointContext noPkgCtx = new EndpointContext(
            null, scriptRepository, null, null, null, null, null, null, new Python3MetricsCollector()
        );
        ScriptAndPackageHandlers h = new ScriptAndPackageHandlers(noPkgCtx);

        JsonObject result = h.handleGetPackageCatalog(req, res);

        assertThat(result.has("error")).isTrue();
    }

    @Test
    void handleGetPackageCatalog_withPackageManager_returnsSuccess() throws Exception {
        // getPackageCatalog() returns empty Map by Mockito default (Map return type)
        JsonObject result = handlers.handleGetPackageCatalog(req, res);

        assertThat(result).isNotNull();
        assertThat(result.get("success").getAsBoolean()).isTrue();
    }

    // ===== handleLoadScript =====

    @Test
    void handleLoadScript_nullRepository_returnsError() throws Exception {
        EndpointContext noRepoCtx = new EndpointContext(
            null, null, null, null, null, null, null, null, new Python3MetricsCollector()
        );
        ScriptAndPackageHandlers h = new ScriptAndPackageHandlers(noRepoCtx);

        when(httpReq.getRequestURI()).thenReturn("/api/v1/scripts/load/myscript");

        JsonObject result = h.handleLoadScript(req, res);

        assertThat(result.has("error")).isTrue();
    }

    @Test
    void handleLoadScript_scriptNotFound_returnsError() throws Exception {
        when(httpReq.getRequestURI()).thenReturn("/api/v1/scripts/load/myscript");
        when(scriptRepository.loadScript("myscript")).thenReturn(null);

        JsonObject result = handlers.handleLoadScript(req, res);

        assertThat(result.has("error")).isTrue();
        assertThat(result.get("error").getAsString()).contains("not found");
    }

    @Test
    void handleLoadScript_validRequest_returnsScript() throws Exception {
        when(httpReq.getRequestURI()).thenReturn("/api/v1/scripts/load/myscript");

        Python3ScriptRepository.SavedScript savedScript = mock(Python3ScriptRepository.SavedScript.class);
        when(savedScript.getId()).thenReturn("id-1");
        when(savedScript.getName()).thenReturn("myscript");
        when(savedScript.getCode()).thenReturn("print('hello')");
        when(savedScript.getDescription()).thenReturn("A test script");
        when(savedScript.getAuthor()).thenReturn("Test");
        when(savedScript.getCreatedDate()).thenReturn("2026-02-22");
        when(savedScript.getLastModified()).thenReturn("2026-02-22");
        when(savedScript.getFolderPath()).thenReturn("");
        when(savedScript.getVersion()).thenReturn("1.0");
        when(scriptRepository.loadScript("myscript")).thenReturn(savedScript);

        JsonObject result = handlers.handleLoadScript(req, res);

        assertThat(result.get("success").getAsBoolean()).isTrue();
        assertThat(result.has("script")).isTrue();
        assertThat(result.getAsJsonObject("script").get("name").getAsString()).isEqualTo("myscript");
        assertThat(result.getAsJsonObject("script").get("code").getAsString()).isEqualTo("print('hello')");
    }

    // ===== handleDeleteScript =====

    @Test
    void handleDeleteScript_scriptFound_returnsSuccessTrue() throws Exception {
        when(httpReq.getRequestURI()).thenReturn("/api/v1/scripts/delete/myscript");
        when(scriptRepository.deleteScript("myscript")).thenReturn(true);

        JsonObject result = handlers.handleDeleteScript(req, res);

        assertThat(result.get("success").getAsBoolean()).isTrue();
        assertThat(result.get("message").getAsString()).contains("deleted");
    }

    @Test
    void handleDeleteScript_scriptNotFound_returnsSuccessFalse() throws Exception {
        when(httpReq.getRequestURI()).thenReturn("/api/v1/scripts/delete/myscript");
        when(scriptRepository.deleteScript("myscript")).thenReturn(false);

        JsonObject result = handlers.handleDeleteScript(req, res);

        assertThat(result.get("success").getAsBoolean()).isFalse();
        assertThat(result.get("message").getAsString()).contains("not found");
    }

    // ===== handleGetPackageStatus =====

    @Test
    void handleGetPackageStatus_withPackageManager_returnsStatus() throws Exception {
        // getStatus() returns empty Map (Mockito default for Map), getInstalledPackages() returns empty Set
        // getPackageCatalog() returns empty Map — all handled gracefully
        JsonObject result = handlers.handleGetPackageStatus(req, res);

        assertThat(result).isNotNull();
        assertThat(result.get("success").getAsBoolean()).isTrue();
        assertThat(result.has("installed")).isTrue();
        assertThat(result.has("available")).isTrue();
    }

    @Test
    void handleGetPackageStatus_nullPackageManager_returnsError() throws Exception {
        EndpointContext noPkgCtx = new EndpointContext(
            null, scriptRepository, null, null, null, null, null, null, new Python3MetricsCollector()
        );
        ScriptAndPackageHandlers h = new ScriptAndPackageHandlers(noPkgCtx);

        JsonObject result = h.handleGetPackageStatus(req, res);

        assertThat(result.has("error")).isTrue();
    }

    // ===== handleGetAvailableScripts =====

    @Test
    void handleGetAvailableScripts_nullScriptModule_returnsError() {
        // Default handlers has null scriptModule in EndpointContext — should return error
        JsonObject result = handlers.handleGetAvailableScripts(req, res);

        assertThat(result).isNotNull();
        assertThat(result.has("error")).isTrue();
    }

    @Test
    void handleGetAvailableScripts_withScripts_returnsSuccess() {
        Python3ScriptModule scriptModule = mock(Python3ScriptModule.class);
        when(scriptModule.getAvailableScripts()).thenReturn(List.of(
            Map.of("name", "script1", "id", "id-1")
        ));
        EndpointContext ctx = new EndpointContext(
            scriptModule, scriptRepository, packageManager, null, null, null, null, null, new Python3MetricsCollector()
        );
        ScriptAndPackageHandlers h = new ScriptAndPackageHandlers(ctx);

        JsonObject result = h.handleGetAvailableScripts(req, res);

        assertThat(result.get("success").getAsBoolean()).isTrue();
        assertThat(result.get("count").getAsInt()).isEqualTo(1);
    }

    // ===== handleInstallPackage =====

    @Test
    void handleInstallPackage_nullPackageManager_returnsError() {
        EndpointContext noPkgCtx = new EndpointContext(
            null, scriptRepository, null, null, null, null, null, null, new Python3MetricsCollector()
        );
        ScriptAndPackageHandlers h = new ScriptAndPackageHandlers(noPkgCtx);
        when(httpReq.getRequestURI()).thenReturn("/api/v1/packages/install/jedi");

        JsonObject result = h.handleInstallPackage(req, res);

        assertThat(result.has("error")).isTrue();
    }

    @Test
    void handleInstallPackage_successfulInstall_returnsSuccess() {
        when(httpReq.getRequestURI()).thenReturn("/api/v1/packages/install/jedi");
        Python3PackageManager.InstallResult installResult =
            new Python3PackageManager.InstallResult(true, "Installed successfully", List.of("jedi.whl"));
        when(packageManager.installPackage("jedi")).thenReturn(installResult);

        JsonObject result = handlers.handleInstallPackage(req, res);

        assertThat(result.get("success").getAsBoolean()).isTrue();
        assertThat(result.get("message").getAsString()).contains("Installed");
        verify(packageManager).installPackage("jedi");
    }

    @Test
    void handleInstallPackage_unauthenticated_isDeniedAndInstallsNothing() {
        // Regression test for the gap found in the 10/08/2026 security review:
        // this route carried only checkManagePermission ("is anyone logged
        // in?"), so any authenticated Gateway user — no Administrator or
        // Designer role needed — could install an arbitrary PyPI package,
        // which runs that package's build hooks as the Gateway's OS user.
        when(httpReq.getRequestURI()).thenReturn("/api/v1/packages/install/evil");
        when(securityService.determineSecurityMode(any()))
            .thenThrow(new SecurityException("Authentication required to execute Python"));

        JsonObject result = handlers.handleInstallPackage(req, res);

        assertThat(result.has("error")).isTrue();
        verify(packageManager, never()).installPackage(anyString());
        verify(packageManager, never()).pipInstallFromPyPI(anyString());
    }

    @Test
    void handleUninstallPackage_unauthenticated_isDeniedAndRemovesNothing() {
        when(httpReq.getRequestURI()).thenReturn("/api/v1/packages/uninstall/jedi");
        when(securityService.determineSecurityMode(any()))
            .thenThrow(new SecurityException("Authentication required to execute Python"));

        JsonObject result = handlers.handleUninstallPackage(req, res);

        assertThat(result.has("error")).isTrue();
        verify(packageManager, never()).uninstallPackage(anyString());
    }

    // ===== handleUninstallPackage =====

    @Test
    void handleUninstallPackage_nullPackageManager_returnsError() {
        EndpointContext noPkgCtx = new EndpointContext(
            null, scriptRepository, null, null, null, null, null, null, new Python3MetricsCollector()
        );
        ScriptAndPackageHandlers h = new ScriptAndPackageHandlers(noPkgCtx);
        when(httpReq.getRequestURI()).thenReturn("/api/v1/packages/uninstall/jedi");

        JsonObject result = h.handleUninstallPackage(req, res);

        assertThat(result.has("error")).isTrue();
    }

    @Test
    void handleUninstallPackage_successfulUninstall_returnsSuccess() {
        when(httpReq.getRequestURI()).thenReturn("/api/v1/packages/uninstall/jedi");
        when(packageManager.uninstallPackage("jedi")).thenReturn(true);

        JsonObject result = handlers.handleUninstallPackage(req, res);

        assertThat(result.get("success").getAsBoolean()).isTrue();
        verify(packageManager).uninstallPackage("jedi");
    }

    // ===== handleVerifyPackages =====

    @Test
    void handleVerifyPackages_nullPackageManager_returnsError() {
        EndpointContext noPkgCtx = new EndpointContext(
            null, scriptRepository, null, null, null, null, null, null, new Python3MetricsCollector()
        );
        ScriptAndPackageHandlers h = new ScriptAndPackageHandlers(noPkgCtx);

        JsonObject result = h.handleVerifyPackages(req, res);

        assertThat(result.has("error")).isTrue();
    }

    @Test
    void handleVerifyPackages_returnsVerificationResult() {
        when(packageManager.verifyPackages()).thenReturn(Map.of("jedi", true, "pandas", false));

        JsonObject result = handlers.handleVerifyPackages(req, res);

        assertThat(result.get("success").getAsBoolean()).isTrue();
        assertThat(result.has("verification")).isTrue();
        assertThat(result.getAsJsonObject("verification").get("jedi").getAsBoolean()).isTrue();
    }
}
