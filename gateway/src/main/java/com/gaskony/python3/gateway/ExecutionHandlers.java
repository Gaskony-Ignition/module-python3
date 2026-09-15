package com.gaskony.python3.gateway;

import com.inductiveautomation.ignition.common.gson.JsonArray;
import com.inductiveautomation.ignition.common.gson.JsonElement;
import com.inductiveautomation.ignition.common.gson.JsonObject;
import com.inductiveautomation.ignition.gateway.dataroutes.RequestContext;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Handles REST API endpoints related to Python code execution and interactive shell sessions.
 *
 * Covers: exec, eval, call-module, call-script, check-syntax, completions, example,
 * shell-interactive/create, shell-interactive/exec, shell-interactive/close, auth/session.
 *
 * Created in v3.6.15 as part of Phase 2 refactoring of Python3RestEndpoints.
 */
class ExecutionHandlers {

    private static final Logger logger = LoggerFactory.getLogger(ExecutionHandlers.class);

    private final EndpointContext ctx;

    /**
     * Resolves the actual Ignition roles bound to a request — used by the C14 fix for
     * {@code /auth/session} token issuance. Static so a single test override applies to
     * any handler instance.
     */
    private static volatile RoleResolver roleResolver = RoleResolver.getDefault();

    ExecutionHandlers(EndpointContext ctx) {
        this.ctx = ctx;
    }

    /**
     * Test hook: replaces the default {@link RoleResolver} with a stub.
     *
     * <p>Production code should never call this. Pass {@code null} to revert to the
     * default resolver after a test.
     */
    static void setRoleResolverForTesting(RoleResolver resolver) {
        roleResolver = (resolver != null) ? resolver : RoleResolver.getDefault();
    }

    // -------------------------------------------------------------------------
    // Auth
    // -------------------------------------------------------------------------

    /**
     * Handle POST /auth/session - Create a new session token for Designer IDE / Web UI.
     *
     * <p>Request body: {@code {"client_id": "ignition-designer-{version}"}} (or {@code "gateway-web-ui"}).
     *
     * <p>Response: {@code {"success": true, "token": "...", "expires_in": 28800, "security_mode": "..."}}.
     *
     * <h3>Security model (C14 fix)</h3>
     * Prior to v3.13.0 this handler trusted the {@code client_id} body field: any caller posting
     * {@code "ignition-designer-anything"} received a {@link SecurityMode#DESIGNER_ADMIN} HMAC token
     * (8h expiry) which bypasses CSRF and unlocks unrestricted Python execution.
     *
     * <p>The handler now binds the issued security mode to the caller's <em>actual</em>
     * Ignition role membership (resolved via {@link RoleResolver#getRoles}):
     * <ul>
     *   <li>caller has the {@code Administrator} role → {@link SecurityMode#DESIGNER_ADMIN};
     *   <li>caller has the {@code Designer} role → {@link SecurityMode#DESIGNER_ADMIN};
     *   <li>caller is authenticated but lacks both roles → {@code 403 Forbidden};
     *   <li>caller is unauthenticated → {@code 403} (handled via {@link ApiResponse#error}).
     * </ul>
     *
     * <p>The {@code client_id} field is now informational only (used in audit logs to track
     * which client requested the token). It is no longer used to determine privilege.
     *
     * @since v2.9.0; auth binding hardened in v3.13.0 (C14)
     */
    JsonObject handleCreateSession(RequestContext req, HttpServletResponse res) {
        return Python3RestEndpoints.withHandler("auth/session", res, () -> {
            JsonObject requestBody = Python3RestEndpoints.parseJsonBody(req);

            String clientId = requestBody.has("client_id") ? requestBody.get("client_id").getAsString() : null;
            // client_id is purely descriptive — kept for audit logging.
            if (clientId == null || clientId.isBlank()) {
                clientId = "unknown";
            }

            // ----- C14: bind security mode to actual Ignition role membership -----
            //
            // The previous behaviour ("startsWith(\"ignition-designer-\") → DESIGNER_ADMIN") let
            // any authenticated browser/REST client mint a privileged token. The fix is to
            // ignore the client_id and ask the SDK who the caller really is.

            // Step 1: caller must be authenticated to even attempt a token issue.
            if (!Python3RestEndpoints.isGatewayAuthenticated(req)) {
                logger.warn("Unauthenticated /auth/session request (client_id={}, remote={})",
                    clientId, req.getRequest().getRemoteAddr());
                return ApiResponse.error("Authentication required");
            }

            // Step 2: read the actual role set from the SDK.
            Set<String> roles = roleResolver.getRoles(req);
            SecurityMode mode = mapRolesToSecurityMode(roles);

            if (mode == null) {
                // Authenticated but lacks any role we recognise → no token at all.
                logger.warn("/auth/session denied: authenticated user (client_id={}) lacks Designer/Administrator role; "
                        + "actual roles: {}", clientId, roles);
                return ApiResponse.error("Forbidden: caller lacks Designer or Administrator role");
            }

            long durationSeconds = 28800;

            // Generate CSRF token FIRST - this is critical for browser-based clients.
            String httpSessionId = req.getRequest().getSession(true).getId();
            String csrfToken = Python3RestEndpoints.generateCSRFToken(httpSessionId);
            logger.debug("CSRF token generated for HTTP session: {}", httpSessionId);

            // Generate API session token bound to the resolved security mode.
            String apiToken = null;
            try {
                if (ctx.securityService != null) {
                    apiToken = ctx.securityService.generateApiToken(mode, durationSeconds);
                }
            } catch (Exception e) {
                logger.warn("Failed to generate API token (securityService issue), CSRF token still valid", e);
            }

            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.addProperty("token", csrfToken);
            if (apiToken != null) {
                response.addProperty("api_token", apiToken);
            }
            response.addProperty("expires_in", durationSeconds);
            response.addProperty("security_mode", mode.toString());

            logger.info("Session token created for client_id={} (mode={}, roles={}, expires_in={}s)",
                clientId, mode, roles, durationSeconds);

            // Audit log (v2.9.0 - session token creation)
            if (ctx.auditLogger != null) {
                try {
                    MessageDigest digest = MessageDigest.getInstance("SHA-256");
                    byte[] hash = digest.digest(clientId.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    StringBuilder hexString = new StringBuilder();
                    for (byte b : hash) {
                        String hex = Integer.toHexString(0xff & b);
                        if (hex.length() == 1) hexString.append('0');
                        hexString.append(hex);
                    }
                    String codeHash = hexString.toString();

                    Python3AuditEvent event = new Python3AuditEvent(
                            java.time.Instant.now(),
                            clientId,
                            req.getRequest().getRemoteAddr(),
                            mode,
                            codeHash,
                            true,
                            0L,
                            null,
                            "REST:/auth/session"
                    );
                    ctx.auditLogger.logExecution(event);
                } catch (Exception e) {
                    logger.warn("Failed to log session token creation audit event", e);
                }
            }

            return response;
        });
    }

    /**
     * Map a set of Ignition role names to the appropriate {@link SecurityMode}.
     *
     * <p>Returns {@code null} when the caller has no recognised role — caller should treat
     * this as a {@code 403 Forbidden} response (the previous "silently demote to RESTRICTED"
     * behaviour was removed when the RESTRICTED mode was deleted in C13).
     *
     * @param roles role names attached to the caller (case-insensitive); may be empty
     * @return the security mode to mint a token for, or {@code null} if no match
     */
    static SecurityMode mapRolesToSecurityMode(Set<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return null;
        }
        for (String r : roles) {
            if (r == null) continue;
            if (RoleResolver.ADMIN_ROLE.equalsIgnoreCase(r)
                || RoleResolver.DESIGNER_ROLE.equalsIgnoreCase(r)) {
                return SecurityMode.DESIGNER_ADMIN;
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Code execution
    // -------------------------------------------------------------------------

    /**
     * Handle POST /exec - Execute Python code.
     *
     * Request body: {"code": "...", "variables": {...}}
     * Response: {"success": true/false, "result": ..., "error": "..."}
     */
    JsonObject handleExec(RequestContext req, HttpServletResponse res) {
        return Python3RestEndpoints.withHandler("exec", res, () -> {
            Python3RestEndpoints.validateCSRFIfSession(req);

            JsonObject requestBody = Python3RestEndpoints.parseJsonBody(req);
            String code = requestBody.has("code") ? requestBody.get("code").getAsString() : "";
            Map<String, Object> variables = new HashMap<>();

            if (requestBody.has("variables") && requestBody.get("variables").isJsonObject()) {
                variables = Python3RestEndpoints.jsonToMap(requestBody.getAsJsonObject("variables"));
            }

            String pythonVersion = requestBody.has("version") ?
                requestBody.get("version").getAsString() : null;

            Python3RestEndpoints.validateCode(code);

            SecurityMode securityMode = Python3RestEndpoints.determineSecurityMode(req);
            logger.debug("Security mode for /exec: {}, version: {}", securityMode,
                pythonVersion != null ? pythonVersion : "default");

            Python3RestEndpoints.auditLog("PYTHON_EXEC", code);

            // Structured per-execution audit (user/IP/mode/codeHash/outcome) is emitted by
            // Python3ScriptModule.exec() via Python3AuditLogger — see that class's finally block.
            Object result;
            if (pythonVersion != null) {
                result = ctx.scriptModule.exec(code, variables, securityMode.getValue(), pythonVersion);
            } else {
                result = ctx.scriptModule.exec(code, variables, securityMode.getValue());
            }

            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.addProperty("result", result != null ? result.toString() : null);
            return response;
        });
    }

    /**
     * Handle POST /eval - Evaluate Python expression.
     *
     * Request body: {"expression": "...", "variables": {...}}
     * Response: {"success": true/false, "result": ..., "error": "..."}
     */
    JsonObject handleEval(RequestContext req, HttpServletResponse res) {
        return Python3RestEndpoints.withHandler("eval", res, () -> {
            Python3RestEndpoints.validateCSRFIfSession(req);

            JsonObject requestBody = Python3RestEndpoints.parseJsonBody(req);
            String expression = requestBody.has("expression") ? requestBody.get("expression").getAsString() : "";
            Map<String, Object> variables = new HashMap<>();

            if (requestBody.has("variables") && requestBody.get("variables").isJsonObject()) {
                variables = Python3RestEndpoints.jsonToMap(requestBody.getAsJsonObject("variables"));
            }

            String pythonVersion = requestBody.has("version") ?
                requestBody.get("version").getAsString() : null;

            Python3RestEndpoints.validateCode(expression);

            SecurityMode securityMode = Python3RestEndpoints.determineSecurityMode(req);
            logger.debug("Security mode for /eval: {}, version: {}", securityMode,
                pythonVersion != null ? pythonVersion : "default");

            Python3RestEndpoints.auditLog("PYTHON_EVAL", expression);

            // Structured per-execution audit is emitted by Python3ScriptModule.eval()
            // via Python3AuditLogger — see that class's finally block.
            Object result;
            if (pythonVersion != null) {
                result = ctx.scriptModule.eval(expression, variables, securityMode.getValue(), pythonVersion);
            } else {
                result = ctx.scriptModule.eval(expression, variables, securityMode.getValue());
            }

            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.addProperty("result", result != null ? result.toString() : null);
            return response;
        });
    }

    /**
     * Handle POST /call-module - Call Python module function.
     *
     * Request body: {"module": "...", "function": "...", "args": [...]}
     * Response: {"success": true/false, "result": ..., "error": "..."}
     */
    JsonObject handleCallModule(RequestContext req, HttpServletResponse res) {
        return Python3RestEndpoints.withHandler("call-module", res, () -> {
            Python3RestEndpoints.validateCSRFIfSession(req);

            JsonObject requestBody = Python3RestEndpoints.parseJsonBody(req);
            String moduleName = requestBody.has("module") ? requestBody.get("module").getAsString() : "";
            String functionName = requestBody.has("function") ? requestBody.get("function").getAsString() : "";
            List<Object> args = new ArrayList<>();

            if (requestBody.has("args") && requestBody.get("args").isJsonArray()) {
                JsonArray jsonArgs = requestBody.getAsJsonArray("args");
                for (JsonElement element : jsonArgs) {
                    args.add(Python3RestEndpoints.jsonElementToObject(element));
                }
            }

            SecurityMode securityMode = Python3RestEndpoints.determineSecurityMode(req);
            logger.debug("Security mode for /call-module: {}", securityMode);

            Python3RestEndpoints.auditLog("PYTHON_CALL_MODULE", moduleName + "." + functionName + "(" + args + ")");

            Object result = ctx.scriptModule.callModule(moduleName, functionName, args, Collections.emptyMap(), securityMode.getValue());

            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.addProperty("result", result != null ? result.toString() : null);
            return response;
        });
    }

    /**
     * Handle POST /call-script - Call saved Python script by path.
     *
     * Request body: {"scriptPath": "...", "args": [...], "kwargs": {...}}
     * Response: {"success": true/false, "result": ..., "error": "..."}
     */
    JsonObject handleCallScript(RequestContext req, HttpServletResponse res) {
        return Python3RestEndpoints.withHandler("call-script", res, () -> {
            Python3RestEndpoints.validateCSRFIfSession(req);

            JsonObject requestBody = Python3RestEndpoints.parseJsonBody(req);
            String scriptPath = requestBody.has("scriptPath") ? requestBody.get("scriptPath").getAsString() : "";

            if (scriptPath.isEmpty()) {
                return ApiResponse.error("scriptPath is required");
            }

            List<Object> args = new ArrayList<>();
            if (requestBody.has("args") && requestBody.get("args").isJsonArray()) {
                JsonArray jsonArgs = requestBody.getAsJsonArray("args");
                for (JsonElement element : jsonArgs) {
                    args.add(Python3RestEndpoints.jsonElementToObject(element));
                }
            }

            Map<String, Object> kwargs = new HashMap<>();
            if (requestBody.has("kwargs") && requestBody.get("kwargs").isJsonObject()) {
                kwargs = Python3RestEndpoints.jsonToMap(requestBody.getAsJsonObject("kwargs"));
            }

            Python3RestEndpoints.auditLog("PYTHON_CALL_SCRIPT", "scriptPath=" + scriptPath);

            Object result = ctx.scriptModule.callScript(scriptPath, args, kwargs);

            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.addProperty("result", result != null ? result.toString() : null);

            logger.debug("REST API: /call-script completed successfully for script: {}", scriptPath);
            return response;
        });
    }

    /**
     * Handle POST /check-syntax - Check Python code syntax.
     *
     * Request body: {"code": "..."}
     * Response: {"success": true, "errors": [{line, column, message, severity}, ...]}
     */
    JsonObject handleCheckSyntax(RequestContext req, HttpServletResponse res) {
        return Python3RestEndpoints.withHandler("check-syntax", res, () -> {
            JsonObject requestBody = Python3RestEndpoints.parseJsonBody(req);
            String code = requestBody.has("code") ? requestBody.get("code").getAsString() : "";

            if (code.isEmpty()) {
                JsonObject response = new JsonObject();
                response.addProperty("success", true);
                response.add("errors", new JsonArray());
                return response;
            }

            Python3RestEndpoints.validateCode(code);
            Python3RestEndpoints.auditLog("PYTHON_CHECK_SYNTAX", code);

            Map<String, Object> result = ctx.scriptModule.checkSyntax(code);

            JsonObject response = new JsonObject();
            response.addProperty("success", true);

            JsonArray errorsArray = new JsonArray();
            if (result.containsKey("errors") && result.get("errors") instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> errors = (List<Map<String, Object>>) result.get("errors");

                for (Map<String, Object> error : errors) {
                    JsonObject errorJson = Python3RestEndpoints.mapToJson(error);
                    errorsArray.add(errorJson);
                }
            }
            response.add("errors", errorsArray);

            logger.debug("REST API: /check-syntax found {} errors", errorsArray.size());
            return response;
        });
    }

    /**
     * Handle POST /completions - Get code completions at cursor position.
     *
     * Request body: {"code": "...", "line": 1, "column": 0}
     * Response: {"success": true, "completions": [{text, type, description, signature}, ...]}
     */
    JsonObject handleGetCompletions(RequestContext req, HttpServletResponse res) {
        return Python3RestEndpoints.withHandler("completions", res, () -> {
            JsonObject requestBody = Python3RestEndpoints.parseJsonBody(req);
            String code = requestBody.has("code") ? requestBody.get("code").getAsString() : "";
            int line = requestBody.has("line") ? requestBody.get("line").getAsInt() : 1;
            int column = requestBody.has("column") ? requestBody.get("column").getAsInt() : 0;

            if (code.isEmpty()) {
                JsonObject response = new JsonObject();
                response.addProperty("success", true);
                response.add("completions", new JsonArray());
                response.addProperty("count", 0);
                return response;
            }

            Map<String, Object> result = ctx.scriptModule.getCompletions(code, line, column);

            JsonObject response = new JsonObject();
            response.addProperty("success", true);

            JsonArray completionsArray = new JsonArray();
            if (result.containsKey("completions") && result.get("completions") instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> completions = (List<Map<String, Object>>) result.get("completions");

                for (Map<String, Object> completion : completions) {
                    JsonObject completionJson = Python3RestEndpoints.mapToJson(completion);
                    completionsArray.add(completionJson);
                }
            }
            response.add("completions", completionsArray);
            response.addProperty("count", completionsArray.size());

            if (result.containsKey("message")) {
                response.addProperty("message", result.get("message").toString());
            }

            logger.debug("REST API: /completions found {} completions", completionsArray.size());
            return response;
        });
    }

    /**
     * Handle GET /example - Run example test.
     *
     * Response: {"success": true/false, "result": "..."}
     */
    JsonObject handleExample(RequestContext req, HttpServletResponse res) {
        return Python3RestEndpoints.withHandler("example", res, () -> {
            Python3RestEndpoints.auditLog("PYTHON_EXAMPLE", "Example test execution");

            String result = ctx.scriptModule.example();

            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.addProperty("result", result);
            return response;
        });
    }

    // -------------------------------------------------------------------------
    // Interactive shell
    // -------------------------------------------------------------------------

    /**
     * Handles POST /shell-interactive/create - Creates a new interactive shell session.
     *
     * Request: {}
     * Response: {"success": true, "sessionId": "uuid"}
     *
     * v2.5.8: Interactive shell session creation
     */
    JsonObject handleCreateShellSession(RequestContext req, HttpServletResponse res) {
        return Python3RestEndpoints.withHandler("shell-interactive/create", res, () -> {
            Python3RestEndpoints.validateCSRFIfSession(req);

            Python3RestEndpoints.auditLog("SHELL_INTERACTIVE_CREATE", "Creating new Python shell session");

            JsonObject requestBody = Python3RestEndpoints.parseJsonBody(req);
            String pythonVersion = null;
            if (requestBody != null && requestBody.has("pythonVersion")) {
                pythonVersion = requestBody.get("pythonVersion").getAsString();
            }

            String pythonPath = resolvePythonPath(pythonVersion);

            logger.info("REST API: Creating interactive Python shell session with: {}", pythonPath);

            String sessionId = Python3InteractiveShell.createSession(pythonPath);

            if (sessionId == null) {
                return ApiResponse.error("Failed to create shell session");
            }

            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.addProperty("sessionId", sessionId);
            response.addProperty("pythonPath", pythonPath);

            logger.info("REST API: Created interactive Python shell session: {} (python: {})", sessionId, pythonPath);
            return response;
        });
    }

    /**
     * Handles POST /shell-interactive/exec - Executes a command in an existing interactive shell session.
     *
     * Request: {"sessionId": "uuid", "command": "ls -la"}
     * Response: {"success": true, "output": "..."}
     *
     * v2.5.8: Interactive shell command execution
     */
    JsonObject handleInteractiveShellExec(RequestContext req, HttpServletResponse res) {
        return Python3RestEndpoints.withHandler("shell-interactive/exec", res, () -> {
            Python3RestEndpoints.validateCSRFIfSession(req);

            JsonObject requestBody = Python3RestEndpoints.parseJsonBody(req);
            String sessionId = requestBody.has("sessionId") ? requestBody.get("sessionId").getAsString() : "";
            String command = requestBody.has("command") ? requestBody.get("command").getAsString() : "";

            if (sessionId == null || sessionId.trim().isEmpty()) {
                return ApiResponse.error("Missing required parameter: sessionId");
            }

            if (command == null || command.trim().isEmpty()) {
                return ApiResponse.error("Missing required parameter: command");
            }

            logger.info("REST API: Executing interactive shell command (session: {}): {}", sessionId, command);

            Python3RestEndpoints.auditLog("SHELL_INTERACTIVE_EXEC", "Session: " + sessionId + ", Command: " + command);

            String output = Python3InteractiveShell.executeCommand(sessionId, command);

            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.addProperty("output", output);

            logger.info("REST API: Interactive shell command completed (session: {})", sessionId);
            return response;
        });
    }

    /**
     * Handles POST /shell-interactive/close - Closes an interactive shell session.
     *
     * Request: {"sessionId": "uuid"}
     * Response: {"success": true}
     *
     * v2.5.8: Interactive shell session closure
     */
    JsonObject handleCloseShellSession(RequestContext req, HttpServletResponse res) {
        return Python3RestEndpoints.withHandler("shell-interactive/close", res, () -> {
            Python3RestEndpoints.validateCSRFIfSession(req);

            JsonObject requestBody = Python3RestEndpoints.parseJsonBody(req);
            String sessionId = requestBody.has("sessionId") ? requestBody.get("sessionId").getAsString() : "";

            if (sessionId == null || sessionId.trim().isEmpty()) {
                return ApiResponse.error("Missing required parameter: sessionId");
            }

            logger.info("REST API: Closing interactive shell session: {}", sessionId);

            Python3RestEndpoints.auditLog("SHELL_INTERACTIVE_CLOSE", "Session: " + sessionId);

            Python3InteractiveShell.closeSession(sessionId);

            JsonObject response = new JsonObject();
            response.addProperty("success", true);

            logger.info("REST API: Interactive shell session closed: {}", sessionId);
            return response;
        });
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Resolves the Python executable path from an optional version string.
     * Falls back through: distributionManager.getVersionExecutablePath →
     * distributionManager.getPythonPath → "python3"
     *
     * @param pythonVersion optional version string, e.g. "3.11" (may be null or blank)
     * @return resolved path to the Python executable
     */
    private String resolvePythonPath(String pythonVersion) {
        if (pythonVersion != null && !pythonVersion.trim().isEmpty() && ctx.distributionManager != null) {
            String path = ctx.distributionManager.getVersionExecutablePath(pythonVersion.trim());
            if (path != null) {
                return path;
            }
        }

        if (ctx.distributionManager != null) {
            try {
                return ctx.distributionManager.getPythonPath();
            } catch (Exception e) {
                logger.warn("Could not resolve default Python path from distributionManager: {}", e.getMessage());
            }
        }

        return "python3";
    }
}
