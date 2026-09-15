package com.gaskony.python3.designer;

import com.inductiveautomation.ignition.common.gson.JsonArray;
import com.inductiveautomation.ignition.common.gson.JsonElement;
import com.inductiveautomation.ignition.common.gson.JsonObject;
import com.inductiveautomation.ignition.common.gson.JsonParser;
import com.inductiveautomation.ignition.client.gateway_interface.GatewayConnection;
import com.inductiveautomation.ignition.common.rpc.proto.ProtoRpcSerializer;
import com.inductiveautomation.ignition.designer.model.DesignerContext;
import com.gaskony.python3.Constants;
import com.gaskony.python3.JsonFields;
import com.gaskony.python3.Python3Rpc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Designer's Gateway client for the Python 3 Integration module.
 *
 * <p>Despite the historical class name ({@code Python3RestClient} — kept as-is to
 * avoid renaming churn across ~70 call sites), this class no longer speaks HTTP.
 * As of v4.3.0 every remaining method is a thin wrapper around the authenticated
 * module-RPC channel ({@link Python3Rpc}, obtained via
 * {@link GatewayConnection#getRpcInterface}), which travels over the Designer's
 * existing authenticated Gateway connection. The cold-HTTP REST plumbing this
 * class used prior to v4.2.0 could not authenticate after the C13/C14 hardening
 * and has been fully removed along with the Designer-only write surfaces
 * (package management, Python version install/uninstall, shell execution, pool
 * resize) that the project charter reserves for the Gateway web UI.</p>
 *
 * <p>The Gateway's REST API (used by the browser Web UI) is unaffected by this
 * change; it is implemented independently in the gateway scope.</p>
 *
 * <p>Example usage:</p>
 * <pre>
 * Python3RestClient client = new Python3RestClient(designerContext);
 * ExecutionResult result = client.executeCode("result = 2 + 2", new HashMap&lt;&gt;());
 * </pre>
 */
public class Python3RestClient {
    private static final Logger logger = LoggerFactory.getLogger(Python3RestClient.class);

    // v4.2.0: authenticated Designer -> Gateway transport. Module RPC travels over the
    // Designer's existing authenticated Gateway channel, so it works where the cold-HTTP
    // REST client cannot (the C13/C14 hardening left the REST client with no way to
    // authenticate). All methods on this class are routed through this proxy.
    private volatile Python3Rpc rpc;

    /** Functional handle for an RPC invocation that may throw the interface's checked {@code Exception}. */
    @FunctionalInterface
    private interface RpcInvocation<T> {
        T invoke() throws Exception;
    }

    /** Lazily create the module-RPC proxy bound to this module and the default proto serializer. */
    private Python3Rpc rpc() {
        Python3Rpc local = rpc;
        if (local == null) {
            synchronized (this) {
                local = rpc;
                if (local == null) {
                    local = GatewayConnection.getRpcInterface(
                        ProtoRpcSerializer.DEFAULT_INSTANCE, Constants.MODULE_ID, Python3Rpc.class);
                    rpc = local;
                }
            }
        }
        return local;
    }

    /** Invoke an RPC call, normalising any non-IOException failure into an IOException for callers. */
    private <T> T callRpc(RpcInvocation<T> call) throws IOException {
        try {
            return call.invoke();
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Gateway RPC call failed: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a new Gateway client.
     *
     * <p>The {@code gatewayUrl} parameter is retained for source compatibility
     * with existing callers only. Module RPC resolves the Gateway via the
     * Designer's own authenticated connection ({@link GatewayConnection}), so
     * no URL is needed or used here (v4.3.0 — the Gateway URL override setting
     * was removed from the Designer for the same reason).</p>
     *
     * @param gatewayUrl unused; retained for source compatibility
     */
    public Python3RestClient(String gatewayUrl) {
        logger.debug("Python3RestClient created (module RPC transport)");
    }

    /**
     * Creates a new Gateway client using the Designer context.
     *
     * @param context the Designer context (unused; retained for source compatibility)
     */
    public Python3RestClient(DesignerContext context) {
        this((String) null);
    }

    /**
     * Executes Python code on the Gateway.
     *
     * @param code the Python code to execute
     * @param variables variables to pass to the Python environment
     * @return execution result with output or error
     * @throws IOException if the HTTP request fails
     */
    public ExecutionResult executeCode(String code, Map<String, Object> variables) throws IOException {
        return executeCode(code, variables, null);
    }

    /**
     * Executes Python code on the Gateway with a specific Python version.
     *
     * @param code the Python code to execute
     * @param variables variables to pass to the Python environment
     * @param pythonVersion Python version to use (e.g., "3.11"), null for default
     * @return execution result with output or error
     * @throws IOException if the HTTP request fails
     * @since v3.1.0
     */
    public ExecutionResult executeCode(String code, Map<String, Object> variables, String pythonVersion) throws IOException {
        logger.info("Executing Python code via Gateway RPC (code length: {} chars, version: {})",
            code.length(), pythonVersion != null ? pythonVersion : "default");

        // Build variables JSON object
        JsonObject varsJson = new JsonObject();
        if (variables != null) {
            for (Map.Entry<String, Object> entry : variables.entrySet()) {
                addToJson(varsJson, entry.getKey(), entry.getValue());
            }
        }

        // Execute via authenticated Gateway RPC (v4.2.0).
        // Coerce a null version to "" — the proto RPC serializer rejects null arguments,
        // and the Gateway treats a blank version as "use the default pool".
        final String version = pythonVersion != null ? pythonVersion : "";
        String response = callRpc(() -> rpc().exec(code, varsJson.toString(), version));
        logger.info("Received exec response via RPC (length: {} chars)", response.length());

        // Parse response
        return parseExecutionResult(response);
    }

    /**
     * Evaluates a Python expression on the Gateway.
     *
     * @param expression the Python expression to evaluate
     * @param variables variables to pass to the Python environment
     * @return execution result with the expression value or error
     * @throws IOException if the HTTP request fails
     */
    public ExecutionResult evaluateExpression(String expression, Map<String, Object> variables) throws IOException {
        return evaluateExpression(expression, variables, null);
    }

    /**
     * Evaluates a Python expression on the Gateway with a specific Python version.
     *
     * @param expression the Python expression to evaluate
     * @param variables variables to pass to the Python environment
     * @param pythonVersion Python version to use (e.g., "3.11"), null for default
     * @return execution result with the expression value or error
     * @throws IOException if the HTTP request fails
     * @since v3.1.0
     */
    public ExecutionResult evaluateExpression(String expression, Map<String, Object> variables, String pythonVersion) throws IOException {
        logger.debug("Evaluating Python expression via Gateway RPC: {} (version: {})",
            expression, pythonVersion != null ? pythonVersion : "default");

        // Build variables JSON object
        JsonObject varsJson = new JsonObject();
        if (variables != null) {
            for (Map.Entry<String, Object> entry : variables.entrySet()) {
                addToJson(varsJson, entry.getKey(), entry.getValue());
            }
        }

        // Evaluate via authenticated Gateway RPC (v4.2.0). Coerce null version to ""
        // (the proto RPC serializer rejects null arguments; blank = default pool).
        final String version = pythonVersion != null ? pythonVersion : "";
        String response = callRpc(() -> rpc().eval(expression, varsJson.toString(), version));

        // Parse response
        return parseExecutionResult(response);
    }

    /**
     * Gets the current Python process pool statistics.
     *
     * @return pool statistics
     * @throws IOException if the HTTP request fails
     */
    public PoolStats getPoolStats() throws IOException {
        logger.debug("Getting pool stats via Gateway RPC");

        String response = callRpc(() -> rpc().getPoolStats());

        // Parse JSON response
        JsonObject json = JsonParser.parseString(response).getAsJsonObject();

        // Gateway sends "poolSize" (Python3ScriptModule.getPoolStats); accept the
        // legacy "totalSize" too for forward/backward safety (v4.4.0 fix — the
        // key mismatch made this silently read 0).
        int totalSize = json.has("poolSize") ? json.get("poolSize").getAsInt()
                : (json.has("totalSize") ? json.get("totalSize").getAsInt() : 0);
        int healthy = json.has(JsonFields.HEALTHY) ? json.get(JsonFields.HEALTHY).getAsInt() : 0;
        int available = json.has(JsonFields.AVAILABLE) ? json.get(JsonFields.AVAILABLE).getAsInt() : 0;
        int inUse = json.has(JsonFields.IN_USE) ? json.get(JsonFields.IN_USE).getAsInt() : 0;

        return new PoolStats(totalSize, healthy, available, inUse);
    }

    /**
     * Checks if the Python 3 module is available and healthy.
     *
     * @return true if the module is healthy and available
     * @throws IOException if the HTTP request fails
     */
    public boolean isHealthy() throws IOException {
        try {
            String response = callRpc(() -> rpc().health());
            JsonObject json = JsonParser.parseString(response).getAsJsonObject();
            return json.has(JsonFields.HEALTHY) && json.get(JsonFields.HEALTHY).getAsBoolean();
        } catch (Exception e) {
            logger.warn("Health check failed", e);
            return false;
        }
    }

    /**
     * Gets comprehensive diagnostic information from the Gateway.
     *
     * @return JSON string with diagnostics
     * @throws IOException if the HTTP request fails
     */
    public String getDiagnostics() throws IOException {
        return callRpc(() -> rpc().getDiagnostics());
    }

    /**
     * Gets module-specific log entries from the Gateway.
     * Fetches recent log entries filtered to Python3 module messages.
     *
     * @param maxLines maximum number of log lines to return
     * @return raw JSON response string with log entries
     * @throws IOException if the HTTP request fails
     * @since v3.6.8
     */
    public String getModuleLogs(int maxLines) throws IOException {
        logger.debug("Getting module logs via Gateway RPC (max {} lines)", maxLines);
        return callRpc(() -> rpc().getModuleLogs(maxLines));
    }

    /**
     * Gets the Python version from the Gateway.
     *
     * @return Python version string (e.g., "3.11.2")
     * @throws IOException if the HTTP request fails
     *
     * v2.0.14: Enhanced logging to debug version detection issues
     */
    public String getPythonVersion() throws IOException {
        logger.info("getPythonVersion() - Getting Python version via REST API");

        try {
            String response = callRpc(() -> rpc().getVersion());
            logger.info("getPythonVersion() - Raw response: {}", response);

            JsonObject json = JsonParser.parseString(response).getAsJsonObject();
            logger.info("getPythonVersion() - Parsed JSON object, keys: {}", json.keySet());

            if (json.has(JsonFields.PYTHON_VERSION)) {
                String version = json.get(JsonFields.PYTHON_VERSION).getAsString();
                logger.info("getPythonVersion() - Found 'pythonVersion' key: {}", version);
                return version;
            } else if (json.has(JsonFields.VERSION)) {
                String version = json.get(JsonFields.VERSION).getAsString();
                logger.info("getPythonVersion() - Found 'version' key: {}", version);
                return version;
            } else {
                logger.warn("getPythonVersion() - Neither 'pythonVersion' nor 'version' key found in response");
                logger.warn("getPythonVersion() - Available keys: {}", json.keySet());
            }

        } catch (Exception e) {
            logger.error("getPythonVersion() - Exception occurred", e);
            throw e;
        }

        logger.warn("getPythonVersion() - Returning 'Unknown' (no version found)");
        return "Unknown";
    }

    /**
     * Gets available Python versions from the Gateway (v3.1.0).
     *
     * @return list of available version strings (e.g., ["3.10", "3.11", "3.12"])
     * @throws IOException if the HTTP request fails
     */
    public java.util.List<String> getAvailableVersions() throws IOException {
        logger.info("Getting available Python versions via Gateway RPC");

        try {
            String response = callRpc(() -> rpc().getVersions());
            JsonObject json = JsonParser.parseString(response).getAsJsonObject();

            java.util.List<String> versions = new java.util.ArrayList<>();
            if (json.has(JsonFields.VERSIONS) && json.get(JsonFields.VERSIONS).isJsonArray()) {
                for (var element : json.getAsJsonArray(JsonFields.VERSIONS)) {
                    versions.add(element.getAsString());
                }
            }

            logger.info("Available Python versions: {}", versions);
            return versions;

        } catch (Exception e) {
            logger.warn("Failed to get available versions: {}", e.getMessage());
            return java.util.Collections.emptyList();
        }
    }

    /**
     * Gets the default Python version from the Gateway (v3.1.0).
     *
     * @return default version string, or null
     * @throws IOException if the HTTP request fails
     */
    public String getDefaultPythonVersion() throws IOException {
        logger.debug("Getting default Python version via Gateway RPC");

        try {
            String response = callRpc(() -> rpc().getVersions());
            JsonObject json = JsonParser.parseString(response).getAsJsonObject();

            if (json.has(JsonFields.DEFAULT)) {
                return json.get(JsonFields.DEFAULT).getAsString();
            }
        } catch (Exception e) {
            logger.warn("Failed to get default version: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Checks Python code for syntax errors using AST parser and pyflakes.
     *
     * @param code the Python code to check
     * @return Map containing "errors" list with syntax error details
     * @throws IOException if the HTTP request fails
     */
    public Map<String, Object> checkSyntax(String code) throws IOException {
        logger.debug("Checking syntax via Gateway RPC");

        // The proto RPC serializer rejects null arguments; coerce to "".
        final String safeCode = code != null ? code : "";
        String response = callRpc(() -> rpc().checkSyntax(safeCode));

        // Parse response
        JsonObject json = JsonParser.parseString(response).getAsJsonObject();

        Map<String, Object> result = new HashMap<>();
        result.put(JsonFields.SUCCESS, json.has(JsonFields.SUCCESS) && json.get(JsonFields.SUCCESS).getAsBoolean());

        // Parse errors array
        if (json.has(JsonFields.ERRORS) && json.get(JsonFields.ERRORS).isJsonArray()) {
            JsonArray errorsArray = json.getAsJsonArray(JsonFields.ERRORS);
            List<Map<String, Object>> errorsList = new ArrayList<>();

            for (int i = 0; i < errorsArray.size(); i++) {
                JsonObject errorJson = errorsArray.get(i).getAsJsonObject();

                Map<String, Object> error = new HashMap<>();
                error.put("line", errorJson.has("line") ? errorJson.get("line").getAsInt() : 1);
                error.put("column", errorJson.has("column") ? errorJson.get("column").getAsInt() : 0);
                error.put("message", errorJson.has("message") ? errorJson.get("message").getAsString() : "Syntax error");
                error.put("severity", errorJson.has("severity") ? errorJson.get("severity").getAsString() : "error");

                errorsList.add(error);
            }

            result.put("errors", errorsList);
        } else {
            result.put("errors", new ArrayList<>());
        }

        return result;
    }

    /**
     * Gets code completions at cursor position.
     *
     * @param code the Python code
     * @param line the line number (1-based)
     * @param column the column number (0-based)
     * @return list of completion results
     * @throws IOException if the HTTP request fails
     */
    public List<CompletionResult> getCompletions(String code, int line, int column) throws IOException {
        logger.debug("Getting completions at line {}, column {} via Gateway RPC", line, column);

        // The proto RPC serializer rejects null arguments; coerce to "".
        final String safeCode = code != null ? code : "";
        String response = callRpc(() -> rpc().getCompletions(safeCode, line, column));

        // Parse response
        JsonObject json = JsonParser.parseString(response).getAsJsonObject();

        List<CompletionResult> completions = new ArrayList<>();

        // Parse completions array
        if (json.has(JsonFields.COMPLETIONS) && json.get(JsonFields.COMPLETIONS).isJsonArray()) {
            JsonArray completionsArray = json.getAsJsonArray(JsonFields.COMPLETIONS);

            for (int i = 0; i < completionsArray.size(); i++) {
                JsonObject compJson = completionsArray.get(i).getAsJsonObject();

                CompletionResult completion = new CompletionResult(
                    getJsonString(compJson, "text"),
                    getJsonString(compJson, "type"),
                    getJsonString(compJson, "complete"),
                    getJsonString(compJson, "description"),
                    getJsonString(compJson, "docstring"),
                    getJsonString(compJson, "signature")
                );

                completions.add(completion);
            }
        }

        logger.debug("Retrieved {} completions", completions.size());
        return completions;
    }

    /**
     * Parses an execution result from JSON response.
     *
     * @param jsonResponse the JSON response string
     * @return parsed ExecutionResult
     */
    private ExecutionResult parseExecutionResult(String jsonResponse) {
        try {
            logger.info("Parsing execution result from JSON response");
            JsonObject json = JsonParser.parseString(jsonResponse).getAsJsonObject();

            boolean success = json.has(JsonFields.SUCCESS) && json.get(JsonFields.SUCCESS).getAsBoolean();
            String result = json.has(JsonFields.RESULT) ? json.get(JsonFields.RESULT).getAsString() : null;
            String error = json.has(JsonFields.ERROR) ? json.get(JsonFields.ERROR).getAsString() : null;
            Long executionTimeMs = json.has(JsonFields.EXECUTION_TIME_MS) ? json.get(JsonFields.EXECUTION_TIME_MS).getAsLong() : null;
            Long timestamp = json.has(JsonFields.TIMESTAMP) ? json.get(JsonFields.TIMESTAMP).getAsLong() : null;

            logger.info("Parsed execution result: success={}, hasResult={}, hasError={}", success, result != null, error != null);
            if (error != null) {
                logger.warn("Execution error: {}", error);
            }

            return new ExecutionResult(success, result, error, executionTimeMs, timestamp);

        } catch (Exception e) {
            logger.error("Failed to parse execution result from JSON: {}", jsonResponse, e);
            return new ExecutionResult(false, "Failed to parse response: " + e.getMessage());
        }
    }

    /**
     * Adds a value to a JSON object with appropriate type handling.
     *
     * @param json the JSON object to add to
     * @param key the key
     * @param value the value (String, Number, Boolean, or other)
     */
    private void addToJson(JsonObject json, String key, Object value) {
        if (value == null) {
            json.add(key, null);
        } else if (value instanceof String) {
            json.addProperty(key, (String) value);
        } else if (value instanceof Number) {
            json.addProperty(key, (Number) value);
        } else if (value instanceof Boolean) {
            json.addProperty(key, (Boolean) value);
        } else {
            // For other types, convert to string
            json.addProperty(key, value.toString());
        }
    }

    /**
     * Safely gets a string value from a JSON object, handling null values.
     *
     * @param json the JSON object
     * @param key the key to look up
     * @return the string value, or null if the key doesn't exist or value is null
     */
    private String getJsonString(JsonObject json, String key) {
        if (json.has(key) && !json.get(key).isJsonNull()) {
            return json.get(key).getAsString();
        }
        return null;
    }

    // Script Management Methods

    /**
     * Lists all saved scripts from the Gateway.
     *
     * @return list of script metadata
     * @throws IOException if the HTTP request fails
     */
    public List<ScriptMetadata> listScripts() throws IOException {
        logger.debug("Listing saved scripts via Gateway RPC");

        String response = callRpc(() -> rpc().listScripts());
        JsonObject json = JsonParser.parseString(response).getAsJsonObject();

        List<ScriptMetadata> scripts = new ArrayList<>();

        if (json.has(JsonFields.SCRIPTS) && json.get(JsonFields.SCRIPTS).isJsonArray()) {
            JsonArray scriptsArray = json.getAsJsonArray(JsonFields.SCRIPTS);
            for (int i = 0; i < scriptsArray.size(); i++) {
                JsonObject scriptJson = scriptsArray.get(i).getAsJsonObject();
                ScriptMetadata metadata = new ScriptMetadata(
                    getJsonString(scriptJson, "id"),
                    getJsonString(scriptJson, "name"),
                    getJsonString(scriptJson, "description"),
                    getJsonString(scriptJson, "author"),
                    getJsonString(scriptJson, "createdDate"),
                    getJsonString(scriptJson, "lastModified"),
                    getJsonString(scriptJson, "folderPath"),
                    getJsonString(scriptJson, "version")
                );
                scripts.add(metadata);
            }
        }

        logger.debug("Loaded {} scripts", scripts.size());
        return scripts;
    }

    /**
     * Loads a saved script from the Gateway.
     *
     * @param name the script name
     * @return the saved script with code
     * @throws IOException if the HTTP request fails
     */
    public SavedScript loadScript(String name) throws IOException {
        logger.debug("Loading script: {}", name);

        String response = callRpc(() -> rpc().loadScript(name));
        JsonObject json = JsonParser.parseString(response).getAsJsonObject();

        if (json.has("script") && json.get("script").isJsonObject()) {
            JsonObject scriptJson = json.getAsJsonObject("script");
            // v2.10.0: SavedScript is now a record, use constructor instead of setters
            SavedScript script = new SavedScript(
                getJsonString(scriptJson, "id"),
                getJsonString(scriptJson, "name"),
                getJsonString(scriptJson, "code"),
                getJsonString(scriptJson, "description"),
                getJsonString(scriptJson, "author"),
                getJsonString(scriptJson, "createdDate"),
                getJsonString(scriptJson, "lastModified"),
                getJsonString(scriptJson, "folderPath"),
                getJsonString(scriptJson, "version")
            );
            return script;
        }

        throw new IOException("Failed to load script: " + name);
    }

    /**
     * Saves a script to the Gateway.
     *
     * @param name the script name
     * @param code the Python code
     * @param description optional description
     * @param author the script author
     * @param folderPath the folder path (e.g., "My Scripts/Utils")
     * @param version the script version (e.g., "1.0")
     * @throws IOException if the HTTP request fails
     */
    public void saveScript(String name, String code, String description,
                          String author, String folderPath, String version) throws IOException {
        logger.debug("Saving script: {} in folder: {}", name, folderPath);

        // The proto RPC serializer rejects null arguments; normalise optional fields to "".
        final String desc = description != null ? description : "";
        final String auth = author != null ? author : "";
        final String folder = folderPath != null ? folderPath : "";
        final String ver = version != null ? version : "";
        String response = callRpc(() -> rpc().saveScript(name, code, desc, auth, folder, ver));
        JsonObject json = JsonParser.parseString(response).getAsJsonObject();

        if (!json.has(JsonFields.SUCCESS) || !json.get(JsonFields.SUCCESS).getAsBoolean()) {
            throw new IOException("Failed to save script: " + name);
        }

        logger.info("Script saved successfully: {} in folder: {}", name, folderPath);
    }

    /**
     * Saves a script to the Gateway (simplified overload for backward compatibility).
     *
     * @param name the script name
     * @param code the Python code
     * @param description optional description
     * @throws IOException if the HTTP request fails
     */
    public void saveScript(String name, String code, String description) throws IOException {
        saveScript(name, code, description, "Unknown", "", "1.0");
    }

    /**
     * Deletes a saved script from the Gateway.
     * v3.6.5: Changed from HTTP DELETE to POST for broader servlet compatibility.
     *
     * @param name the script name
     * @throws IOException if the HTTP request fails
     */
    public void deleteScript(String name) throws IOException {
        logger.info("Deleting script: {}", name);

        String response = callRpc(() -> rpc().deleteScript(name));
        JsonObject json = JsonParser.parseString(response).getAsJsonObject();

        if (!json.has(JsonFields.SUCCESS) || !json.get(JsonFields.SUCCESS).getAsBoolean()) {
            String msg = json.has(JsonFields.MESSAGE) ? json.get(JsonFields.MESSAGE).getAsString() : "Unknown error";
            throw new IOException("Failed to delete script '" + name + "': " + msg);
        }

        logger.info("Script deleted successfully: {}", name);
    }

    /**
     * Gets the Gateway impact assessment from Python 3 module.
     *
     * @return gateway impact with level and health score
     * @throws IOException if the HTTP request fails
     */
    public GatewayImpact getGatewayImpact() throws IOException {
        logger.debug("Getting Gateway impact via Gateway RPC");

        try {
            String response = callRpc(() -> rpc().getGatewayImpact());
            JsonObject json = JsonParser.parseString(response).getAsJsonObject();

            GatewayImpact impact = new GatewayImpact();
            impact.setImpactLevel(getJsonString(json, "impactLevel"));
            impact.setHealthScore(json.has("healthScore") ? json.get("healthScore").getAsInt() : 0);
            impact.setRecommendation(getJsonString(json, "recommendation"));

            // v2.5.20: Parse RAM and CPU metrics (added in v2.5.19) - Legacy fields
            if (json.has("memoryUsageMb")) {
                impact.setMemoryUsageMb(json.get("memoryUsageMb").getAsDouble());
            }
            if (json.has("averageCpuTimeMs")) {
                impact.setAverageCpuTimeMs(json.get("averageCpuTimeMs").getAsDouble());
            }
            // v2.5.21: Parse CPU usage percentage
            if (json.has("cpuUsagePercent")) {
                impact.setCpuUsagePercent(json.get("cpuUsagePercent").getAsDouble());
            }

            // v2.15.5: Parse Python3-specific and system-wide metrics
            if (json.has("python3MemoryMb")) {
                impact.setPython3MemoryMb(json.get("python3MemoryMb").getAsDouble());
            }
            if (json.has("python3CpuPercent")) {
                impact.setPython3CpuPercent(json.get("python3CpuPercent").getAsDouble());
            }
            if (json.has("gatewayMemoryMb")) {
                impact.setGatewayMemoryMb(json.get("gatewayMemoryMb").getAsDouble());
            }
            if (json.has("gatewayCpuPercent")) {
                impact.setGatewayCpuPercent(json.get("gatewayCpuPercent").getAsDouble());
            }
            if (json.has("maxMemoryMb")) {
                impact.setMaxMemoryMb(json.get("maxMemoryMb").getAsDouble());
            }
            if (json.has("availableCores")) {
                impact.setAvailableCores(json.get("availableCores").getAsInt());
            }

            return impact;

        } catch (Exception e) {
            logger.warn("Failed to get Gateway impact, returning default", e);
            // Return default "healthy" impact if endpoint not available
            return new GatewayImpact("LOW", 100, "All systems operational");
        }
    }

    // =========================================================================
    // Python Distribution Management (v3.1.0) — read-only in the Designer
    // (write/manage operations moved to the Gateway web UI in v4.3.0, §3 of
    // the project charter)
    // =========================================================================

    /**
     * Status information for a Python distribution version.
     */
    public static class DistributionInfo {
        public final String version;
        public final String fullVersion;
        public final boolean installed;
        public final boolean available;
        public final String pythonPath;
        public final long installSizeMB;
        public final boolean poolActive;

        public DistributionInfo(String version, String fullVersion, boolean installed,
                                boolean available, String pythonPath, long installSizeMB,
                                boolean poolActive) {
            this.version = version;
            this.fullVersion = fullVersion;
            this.installed = installed;
            this.available = available;
            this.pythonPath = pythonPath;
            this.installSizeMB = installSizeMB;
            this.poolActive = poolActive;
        }
    }

    /**
     * Gets all available Python distributions with their install status (v3.1.0).
     * Read-only: the Designer only displays what versions/packages the admin has
     * provided (§3 of the project charter); installing/uninstalling distributions
     * is a Gateway web UI (Administrator) function.
     *
     * @return list of distribution info objects
     * @throws IOException if the HTTP request fails
     */
    public List<DistributionInfo> getDistributions() throws IOException {
        logger.info("Getting Python distributions via Gateway RPC");

        List<DistributionInfo> distributions = new ArrayList<>();

        try {
            String response = callRpc(() -> rpc().getDistributions());
            JsonObject json = JsonParser.parseString(response).getAsJsonObject();

            if (json.has(JsonFields.DISTRIBUTIONS) && json.get(JsonFields.DISTRIBUTIONS).isJsonArray()) {
                for (var element : json.getAsJsonArray(JsonFields.DISTRIBUTIONS)) {
                    JsonObject dist = element.getAsJsonObject();
                    distributions.add(new DistributionInfo(
                            dist.get("version").getAsString(),
                            dist.has("fullVersion") ? dist.get("fullVersion").getAsString() : dist.get("version").getAsString(),
                            dist.has("installed") && dist.get("installed").getAsBoolean(),
                            dist.has("available") && dist.get("available").getAsBoolean(),
                            dist.has("pythonPath") ? dist.get("pythonPath").getAsString() : null,
                            dist.has("installSizeMB") ? dist.get("installSizeMB").getAsLong() : 0,
                            dist.has("poolActive") && dist.get("poolActive").getAsBoolean()
                    ));
                }
            }

            logger.info("Found {} Python distributions", distributions.size());
            return distributions;

        } catch (Exception e) {
            logger.warn("Failed to get distributions: {}", e.getMessage());
            return distributions;
        }
    }

    // =========================================================================
    // Package catalog (v4.3.0) — read-only environment view (§3 of the project
    // charter: "Environment visibility ... Designer read-only"; installing or
    // removing packages remains a Gateway web UI / Administrator function).
    // =========================================================================

    /**
     * One entry from the Gateway's package catalog: a package the admin has made
     * available, whether or not it is currently installed.
     */
    public static final class PackageCatalogEntry {
        public final String name;
        public final String version;
        public final String description;
        public final boolean installed;

        public PackageCatalogEntry(String name, String version, String description, boolean installed) {
            this.name = name;
            this.version = version;
            this.description = description;
            this.installed = installed;
        }
    }

    /**
     * Gets the read-only package catalog (name, version, description, installed
     * flag) from the Gateway, sorted by package name.
     *
     * @return list of catalog entries, empty if none are available
     * @throws IOException if the RPC call fails
     */
    public List<PackageCatalogEntry> getPackageCatalog() throws IOException {
        logger.debug("Getting package catalog via Gateway RPC");
        String response = callRpc(() -> rpc().getPackageCatalog());
        return parsePackageCatalog(response);
    }

    /**
     * Parses the {@code getPackageCatalog()} RPC response
     * ({@code {"success":..,"packages":{name:{version,description,installed,...}},"count":..}})
     * into a flat, name-sorted list.
     *
     * <p>Package-visible (not private) so it can be unit-tested directly without
     * standing up an RPC proxy.</p>
     *
     * @param jsonResponse the raw JSON response string
     * @return parsed, name-sorted list of entries; empty if the response has no
     *         {@code packages} object
     */
    static List<PackageCatalogEntry> parsePackageCatalog(String jsonResponse) {
        List<PackageCatalogEntry> entries = new ArrayList<>();

        JsonObject json = JsonParser.parseString(jsonResponse).getAsJsonObject();
        if (!json.has("packages") || !json.get("packages").isJsonObject()) {
            return entries;
        }

        JsonObject packages = json.getAsJsonObject("packages");
        for (Map.Entry<String, JsonElement> entry : packages.entrySet()) {
            String name = entry.getKey();
            if (!entry.getValue().isJsonObject()) {
                continue;
            }
            JsonObject pkg = entry.getValue().getAsJsonObject();

            String version = pkg.has(JsonFields.VERSION) ? pkg.get(JsonFields.VERSION).getAsString() : "";
            String description = pkg.has(JsonFields.DESCRIPTION) ? pkg.get(JsonFields.DESCRIPTION).getAsString() : "";
            boolean installed = pkg.has("installed") && pkg.get("installed").getAsBoolean();

            entries.add(new PackageCatalogEntry(name, version, description, installed));
        }

        entries.sort(Comparator.comparing(e -> e.name, String.CASE_INSENSITIVE_ORDER));
        return entries;
    }
}
