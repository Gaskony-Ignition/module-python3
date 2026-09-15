package com.gaskony.python3.gateway;

import com.gaskony.python3.Python3Rpc;
import com.inductiveautomation.ignition.common.gson.JsonArray;
import com.inductiveautomation.ignition.common.gson.JsonObject;
import com.inductiveautomation.ignition.common.gson.JsonParser;
import com.inductiveautomation.ignition.gateway.clientcomm.ClientReqSession;
import com.inductiveautomation.ignition.gateway.rpc.RpcDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Gateway-side implementation of {@link Python3Rpc}.
 *
 * <p>Serves the Designer over the platform's authenticated module-RPC channel,
 * replacing the Designer's old cold-HTTP REST client (which could not
 * authenticate to the Gateway after the C13/C14 security hardening). Because the
 * RPC channel is only reachable by an authenticated Gateway client, and the
 * caller identity is available via {@link RpcDelegate#session()}, no session
 * token is required.</p>
 *
 * <p>Each method mirrors the JSON produced by the equivalent REST handler so the
 * Designer parsing is unchanged. Business logic is delegated to the same
 * services the REST handlers use ({@link Python3ScriptRepository},
 * {@link Python3ScriptModule}); the tested REST path is left untouched.</p>
 *
 * @since v4.2.0
 */
public class Python3RpcHandler implements Python3Rpc {

    private static final Logger logger = LoggerFactory.getLogger(Python3RpcHandler.class);

    private final GatewayHook hook;

    public Python3RpcHandler(GatewayHook hook) {
        this.hook = hook;
    }

    // -------------------------------------------------------------------------
    // Script management
    // -------------------------------------------------------------------------

    @Override
    public String listScripts() throws Exception {
        requireDesignerSession();
        Python3ScriptRepository repo = hook.getScriptRepository();
        if (repo == null) {
            return ApiResponse.error("Script repository not initialized").toString();
        }

        List<Python3ScriptRepository.ScriptMetadata> scripts = repo.listScripts();
        JsonObject response = new JsonObject();
        response.addProperty("success", true);

        JsonArray scriptsArray = new JsonArray();
        for (Python3ScriptRepository.ScriptMetadata s : scripts) {
            JsonObject o = new JsonObject();
            o.addProperty("id", s.getId());
            o.addProperty("name", s.getName());
            o.addProperty("description", s.getDescription());
            o.addProperty("author", s.getAuthor());
            o.addProperty("createdDate", s.getCreatedDate());
            o.addProperty("lastModified", s.getLastModified());
            o.addProperty("folderPath", s.getFolderPath());
            o.addProperty("version", s.getVersion());
            scriptsArray.add(o);
        }
        response.add("scripts", scriptsArray);
        logger.debug("RPC: listed {} scripts", scripts.size());
        return response.toString();
    }

    @Override
    public String loadScript(String name) throws Exception {
        requireDesignerSession();
        Python3ScriptRepository repo = hook.getScriptRepository();
        if (repo == null) {
            return ApiResponse.error("Script repository not initialized").toString();
        }

        Python3ScriptRepository.SavedScript script = repo.loadScript(name);
        JsonObject response = new JsonObject();
        if (script == null) {
            response.addProperty("success", false);
            response.addProperty("message", "Script not found: " + name);
            return response.toString();
        }

        response.addProperty("success", true);
        JsonObject o = new JsonObject();
        o.addProperty("id", script.getId());
        o.addProperty("name", script.getName());
        o.addProperty("code", script.getCode());
        o.addProperty("description", script.getDescription());
        o.addProperty("author", script.getAuthor());
        o.addProperty("createdDate", script.getCreatedDate());
        o.addProperty("lastModified", script.getLastModified());
        o.addProperty("folderPath", script.getFolderPath());
        o.addProperty("version", script.getVersion());
        response.add("script", o);
        return response.toString();
    }

    @Override
    public String saveScript(String name, String code, String description,
                             String author, String folderPath, String version) throws Exception {
        requireDesignerSession();
        Python3ScriptRepository repo = hook.getScriptRepository();
        if (repo == null) {
            return ApiResponse.error("Script repository not initialized").toString();
        }

        repo.saveScript(name, code, description, author, folderPath, version);
        Python3RestEndpoints.auditLog("SCRIPT_SAVE", "name=" + name + " folder=" + folderPath);

        JsonObject response = new JsonObject();
        response.addProperty("success", true);
        logger.info("RPC: saved script {} in folder {}", name, folderPath);
        return response.toString();
    }

    @Override
    public String deleteScript(String name) throws Exception {
        requireDesignerSession();
        Python3ScriptRepository repo = hook.getScriptRepository();
        if (repo == null) {
            return ApiResponse.error("Script repository not initialized").toString();
        }

        Python3RestEndpoints.auditLog("SCRIPT_DELETE", "name=" + name);
        boolean deleted = repo.deleteScript(name);

        JsonObject response = new JsonObject();
        response.addProperty("success", deleted);
        response.addProperty("message", deleted ? "Script deleted successfully" : "Script not found: " + name);
        logger.info("RPC: script deletion {} - {}", name, deleted ? "success" : "not found");
        return response.toString();
    }

    // -------------------------------------------------------------------------
    // Execution
    // -------------------------------------------------------------------------

    @Override
    public String exec(String code, String variablesJson, String pythonVersion) throws Exception {
        requireDesignerSession();
        Map<String, Object> variables = parseVariables(variablesJson);
        Python3RestEndpoints.validateCode(code);
        Python3RestEndpoints.auditLog("PYTHON_EXEC", code);

        JsonObject response = new JsonObject();
        try {
            Python3ScriptModule sm = hook.getScriptModule();
            // Per-execution structured audit is emitted by Python3ScriptModule.execTrusted()
            // via Python3AuditLogger. The runtime scripting opt-out gate is deliberately NOT
            // enforced here: per charter §2 (2026-07-02), an authenticated Designer session
            // must always be able to develop/test, independent of that gateway-wide opt-out.
            Object result = sm.execTrusted(code, variables, pythonVersion);
            response.addProperty("success", true);
            response.addProperty("result", result != null ? result.toString() : null);
        } catch (Exception e) {
            response.addProperty("success", false);
            response.addProperty("error", e.getMessage());
        }
        return response.toString();
    }

    @Override
    public String eval(String expression, String variablesJson, String pythonVersion) throws Exception {
        requireDesignerSession();
        Map<String, Object> variables = parseVariables(variablesJson);
        Python3RestEndpoints.validateCode(expression);
        Python3RestEndpoints.auditLog("PYTHON_EVAL", expression);

        JsonObject response = new JsonObject();
        try {
            Python3ScriptModule sm = hook.getScriptModule();
            // See exec() above: the trusted path skips the runtime scripting opt-out gate,
            // which does not apply to authenticated Designer sessions (charter §2, 2026-07-02).
            Object result = sm.evalTrusted(expression, variables, pythonVersion);
            response.addProperty("success", true);
            response.addProperty("result", result != null ? result.toString() : null);
        } catch (Exception e) {
            response.addProperty("success", false);
            response.addProperty("error", e.getMessage());
        }
        return response.toString();
    }

    // -------------------------------------------------------------------------
    // Monitoring
    // -------------------------------------------------------------------------

    @Override
    public String getVersion() throws Exception {
        requireDesignerSession();
        Python3ScriptModule sm = hook.getScriptModule();
        JsonObject response = Python3RestEndpoints.mapToJson(sm.getVersion());
        if (response.has("python_version") && !response.has("pythonVersion")) {
            response.addProperty("pythonVersion", response.get("python_version").getAsString());
        }
        if (response.has("version") && !response.has("pythonVersion")) {
            response.addProperty("pythonVersion", response.get("version").getAsString());
        }
        return response.toString();
    }

    @Override
    public String getPoolStats() throws Exception {
        requireDesignerSession();
        Python3ScriptModule sm = hook.getScriptModule();
        JsonObject response = Python3RestEndpoints.mapToJson(sm.getPoolStats());
        if (!response.has("healthCheckStatus")) {
            response.addProperty("healthCheckStatus", sm.isAvailable() ? "Healthy" : "Down");
        }
        return response.toString();
    }

    @Override
    public String health() throws Exception {
        requireDesignerSession();
        boolean available = hook.getScriptModule().isAvailable();
        JsonObject response = new JsonObject();
        response.addProperty("healthy", available);
        response.addProperty("available", available);
        response.addProperty("status", available ? "HEALTHY" : "DOWN");
        response.addProperty("timestamp", System.currentTimeMillis());
        return response.toString();
    }

    // -------------------------------------------------------------------------
    // Diagnostics, environment visibility (v4.3.0 — charter workflows 4/5)
    // -------------------------------------------------------------------------

    /**
     * Replicates {@link MonitoringHandlers#handleDiagnostics}.
     */
    @Override
    public String getDiagnostics() throws Exception {
        requireDesignerSession();
        Python3ScriptModule sm = hook.getScriptModule();
        JsonObject response = new JsonObject();

        boolean available = sm.isAvailable();
        response.addProperty("available", available);

        Map<String, Object> poolStats = sm.getPoolStats();
        response.add("poolStats", Python3RestEndpoints.mapToJson(poolStats));

        Map<String, Object> versionInfo = sm.getVersion();
        response.add("versionInfo", Python3RestEndpoints.mapToJson(versionInfo));

        Map<String, Object> distributionInfo = sm.getDistributionInfo();
        response.add("distributionInfo", Python3RestEndpoints.mapToJson(distributionInfo));

        // v4.4.0: real execution stats promoted to top level, where the Designer's
        // ExecutionMetrics.fromJson reads them (totalExecutions, successfulExecutions,
        // averageExecutionTime, ...). Previously absent from this payload, so Total
        // Executions / Success Rate / Avg Time were structurally always zero.
        JsonObject execStats = Python3RestEndpoints.mapToJson(sm.getExecutionStats());
        for (String key : execStats.keySet()) {
            response.add(key, execStats.get(key));
        }

        response.addProperty("timestamp", System.currentTimeMillis());
        return response.toString();
    }

    /**
     * Replicates {@link MonitoringHandlers#handleGetLogs}, hardcoding {@code filter=Python3}
     * and no level/pagination filter, matching the Designer's prior REST call
     * ({@code /logs?lines=N&filter=Python3}).
     */
    @Override
    public String getModuleLogs(int maxLines) throws Exception {
        requireDesignerSession();
        int lines = Math.min(500, Math.max(1, maxLines));
        return Python3LogsHandler.readLogs(hook.getLogsDir(), lines, null, "Python3", 0).toString();
    }

    /**
     * Replicates {@link MonitoringHandlers#handleGetGatewayImpact}. The metrics collector
     * ({@link Python3RestEndpoints#metricsCollector}) is package-private static state that
     * is eagerly constructed, so it is never {@code null}.
     */
    @Override
    public String getGatewayImpact() throws Exception {
        requireDesignerSession();
        Map<String, Object> impact = Python3RestEndpoints.metricsCollector.getGatewayImpact();
        return Python3RestEndpoints.mapToJson(impact).toString();
    }

    /**
     * Replicates {@link MonitoringHandlers#handleGetVersions}, including its fallback to
     * single-version mode when no {@link PoolManager} is registered yet.
     */
    @Override
    public String getVersions() throws Exception {
        requireDesignerSession();
        JsonObject response = new JsonObject();
        PoolManager pm = hook.getPoolManager();

        if (pm != null) {
            List<String> versions = pm.getAvailableVersions();

            JsonArray versionArray = new JsonArray();
            for (String v : versions) {
                versionArray.add(v);
            }
            response.add("versions", versionArray);
            response.addProperty("default", pm.getDefaultVersion());
            response.addProperty("count", versions.size());

            JsonObject details = new JsonObject();
            for (String v : versions) {
                JsonObject versionDetail = new JsonObject();
                String path = pm.getPythonPath(v);
                versionDetail.addProperty("path", path != null ? path : "unknown");
                versionDetail.addProperty("isDefault", v.equals(pm.getDefaultVersion()));
                try {
                    Python3ProcessPool pool = pm.getPool(v);
                    Python3ProcessPool.PoolStats stats = pool.getStats();
                    versionDetail.addProperty("poolSize", stats.totalSize);
                    versionDetail.addProperty("available", stats.available);
                    versionDetail.addProperty("healthy", stats.healthy);
                } catch (PoolManager.VersionNotAvailableException e) {
                    versionDetail.addProperty("error", e.getMessage());
                }
                details.add(v, versionDetail);
            }
            response.add("details", details);
        } else {
            JsonArray versionArray = new JsonArray();
            List<String> versions = hook.getScriptModule().getAvailableVersions();
            if (versions.isEmpty()) {
                versionArray.add("default");
            } else {
                for (String v : versions) {
                    versionArray.add(v);
                }
            }
            response.add("versions", versionArray);
            response.addProperty("default", "default");
            response.addProperty("count", versionArray.size());
        }

        response.addProperty("success", true);
        response.addProperty("timestamp", System.currentTimeMillis());
        return response.toString();
    }

    /**
     * Replicates {@link MonitoringHandlers#handleGetDistributions}.
     */
    @Override
    public String getDistributions() throws Exception {
        requireDesignerSession();
        PythonDistributionManager dm = hook.getDistributionManager();
        if (dm == null) {
            return ApiResponse.error("Distribution manager not initialized").toString();
        }

        JsonObject response = new JsonObject();
        JsonArray distributions = new JsonArray();
        PoolManager pm = hook.getPoolManager();

        for (PythonDistributionManager.VersionStatus status : dm.getAllVersionStatuses()) {
            JsonObject dist = new JsonObject();
            dist.addProperty("version", status.version);
            dist.addProperty("fullVersion", status.fullVersion);
            dist.addProperty("installed", status.installed);
            dist.addProperty("available", status.available);
            if (status.pythonPath != null) {
                dist.addProperty("pythonPath", status.pythonPath);
            }
            if (status.installSizeBytes > 0) {
                dist.addProperty("installSizeBytes", status.installSizeBytes);
                dist.addProperty("installSizeMB", status.installSizeBytes / (1024 * 1024));
            }

            if (status.installed && pm != null && pm.isVersionAvailable(status.version)) {
                dist.addProperty("poolActive", true);
            } else {
                dist.addProperty("poolActive", false);
            }

            distributions.add(dist);
        }

        response.add("distributions", distributions);
        response.addProperty("os", dm.detectOS());
        response.addProperty("installedCount", dm.getInstalledVersions().size());
        response.addProperty("success", true);
        response.addProperty("timestamp", System.currentTimeMillis());
        return response.toString();
    }

    /**
     * Replicates {@link ScriptAndPackageHandlers#handleGetPackageCatalog}, including the
     * installed-flags merge for packages installed straight from PyPI (not in the bundled
     * catalog). This is the read-only environment view — charter &sect;3.
     */
    @Override
    public String getPackageCatalog() throws Exception {
        requireDesignerSession();
        Python3PackageManager pkgMgr = hook.getPackageManager();
        if (pkgMgr == null) {
            return ApiResponse.error("Package manager not initialized").toString();
        }

        Map<String, Python3PackageManager.PackageInfo> catalog = pkgMgr.getPackageCatalog();

        JsonObject response = new JsonObject();
        response.addProperty("success", true);

        Set<String> installedPackages = pkgMgr.getInstalledPackages();

        JsonObject packagesJson = new JsonObject();
        for (Map.Entry<String, Python3PackageManager.PackageInfo> entry : catalog.entrySet()) {
            String packageName = entry.getKey();
            Python3PackageManager.PackageInfo info = entry.getValue();

            JsonObject packageJson = new JsonObject();
            packageJson.addProperty("version", info.version);
            packageJson.addProperty("description", info.description);
            packageJson.addProperty("sizeMb", info.sizeMb);
            packageJson.addProperty("importName", info.importName);
            packageJson.addProperty("installed", installedPackages.contains(packageName));

            JsonArray wheelsArray = new JsonArray();
            for (String wheel : info.wheels) {
                wheelsArray.add(wheel);
            }
            packageJson.add("wheels", wheelsArray);

            JsonArray pipPackagesArray = new JsonArray();
            for (String pipPkg : info.pipPackages) {
                pipPackagesArray.add(pipPkg);
            }
            packageJson.add("pipPackages", pipPackagesArray);

            JsonArray requiredForArray = new JsonArray();
            for (String usage : info.requiredFor) {
                requiredForArray.add(usage);
            }
            packageJson.add("requiredFor", requiredForArray);

            packagesJson.add(packageName, packageJson);
        }

        // Include packages installed from PyPI that aren't in the catalog
        for (String installedPkg : installedPackages) {
            if (!catalog.containsKey(installedPkg)) {
                JsonObject packageJson = new JsonObject();
                packageJson.addProperty("version", "");
                packageJson.addProperty("description", "Installed from PyPI");
                packageJson.addProperty("sizeMb", 0.0);
                packageJson.addProperty("importName", installedPkg);
                packageJson.addProperty("installed", true);
                packageJson.add("wheels", new JsonArray());
                packageJson.add("pipPackages", new JsonArray());
                packageJson.add("requiredFor", new JsonArray());
                packagesJson.add(installedPkg, packageJson);
            }
        }

        response.add("packages", packagesJson);
        response.addProperty("count", packagesJson.size());
        return response.toString();
    }

    /**
     * Replicates {@link ExecutionHandlers#handleCheckSyntax}.
     */
    @Override
    public String checkSyntax(String code) throws Exception {
        requireDesignerSession();
        String safeCode = code != null ? code : "";

        if (safeCode.isEmpty()) {
            JsonObject empty = new JsonObject();
            empty.addProperty("success", true);
            empty.add("errors", new JsonArray());
            return empty.toString();
        }

        Python3RestEndpoints.validateCode(safeCode);
        Python3RestEndpoints.auditLog("PYTHON_CHECK_SYNTAX", safeCode);

        Map<String, Object> result = hook.getScriptModule().checkSyntax(safeCode);

        JsonObject response = new JsonObject();
        response.addProperty("success", true);

        JsonArray errorsArray = new JsonArray();
        if (result.containsKey("errors") && result.get("errors") instanceof List) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> errors = (List<Map<String, Object>>) result.get("errors");
            for (Map<String, Object> error : errors) {
                errorsArray.add(Python3RestEndpoints.mapToJson(error));
            }
        }
        response.add("errors", errorsArray);
        return response.toString();
    }

    /**
     * Replicates {@link ExecutionHandlers#handleGetCompletions}.
     */
    @Override
    public String getCompletions(String code, int line, int column) throws Exception {
        requireDesignerSession();
        String safeCode = code != null ? code : "";

        if (safeCode.isEmpty()) {
            JsonObject empty = new JsonObject();
            empty.addProperty("success", true);
            empty.add("completions", new JsonArray());
            empty.addProperty("count", 0);
            return empty.toString();
        }

        Map<String, Object> result = hook.getScriptModule().getCompletions(safeCode, line, column);

        JsonObject response = new JsonObject();
        response.addProperty("success", true);

        JsonArray completionsArray = new JsonArray();
        if (result.containsKey("completions") && result.get("completions") instanceof List) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> completions = (List<Map<String, Object>>) result.get("completions");
            for (Map<String, Object> completion : completions) {
                completionsArray.add(Python3RestEndpoints.mapToJson(completion));
            }
        }
        response.add("completions", completionsArray);
        response.addProperty("count", completionsArray.size());

        if (result.containsKey("message")) {
            response.addProperty("message", result.get("message").toString());
        }
        return response.toString();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * The module-RPC channel is reachable by any authenticated Gateway client,
     * including Vision runtime clients (a Vision {@link ClientReqSession} is
     * just as real as a Designer one). Script authoring/exec/eval on this RPC
     * surface is a Designer-authoring capability (charter &sect;3 — "Script
     * authoring, testing, organisation" is Designer-owned), so a valid session
     * alone is not enough: the session must also report
     * {@link ClientReqSession#isDesigner()}. This keeps Vision runtime clients
     * (and any other non-Designer client session) out of script CRUD/exec,
     * independent of the {@code ignition.python3.scriptingFunctions.allowed}
     * runtime opt-out, which governs {@code system.python3.*} calls from
     * project Jython, not this RPC surface.
     *
     * @throws SecurityException if there is no valid, Designer-flagged session
     * @since v4.2.0; Designer-only requirement added v4.3.0 (charter &sect;2, 2026-07-02)
     */
    void requireDesignerSession() {
        ClientReqSession session = RpcDelegate.session();
        if (session == null || !session.isValid()) {
            throw new SecurityException("Authenticated Gateway session required");
        }
        if (!session.isDesigner()) {
            throw new SecurityException("Designer session required");
        }
    }

    private Map<String, Object> parseVariables(String variablesJson) {
        Map<String, Object> variables = new HashMap<>();
        if (variablesJson == null || variablesJson.isBlank()) {
            return variables;
        }
        JsonObject obj = JsonParser.parseString(variablesJson).getAsJsonObject();
        for (String key : obj.keySet()) {
            variables.put(key, Python3RestEndpoints.jsonElementToObject(obj.get(key)));
        }
        return variables;
    }
}
