package com.gaskony.python3;

/**
 * Centralised JSON field name constants for the Python 3 Integration REST API.
 *
 * <p>Single source of truth for all JSON property key strings exchanged between
 * the Gateway REST endpoints and the Designer REST client. When a field name
 * changes, update only this file.</p>
 */
public final class JsonFields {

    private JsonFields() {
        // Utility class - no instances
    }

    // =========================================================================
    // Universal response envelope fields
    // =========================================================================

    /** {@code "success"} — top-level boolean in every API response. */
    public static final String SUCCESS = "success";

    /** {@code "error"} — human-readable error message when success is false. */
    public static final String ERROR = "error";

    /** {@code "result"} — execution or evaluation result value. */
    public static final String RESULT = "result";

    /** {@code "message"} — informational status message. */
    public static final String MESSAGE = "message";

    /** {@code "timestamp"} — Unix epoch millisecond timestamp. */
    public static final String TIMESTAMP = "timestamp";

    // =========================================================================
    // Request body fields (sent from client to gateway)
    // =========================================================================

    /** {@code "code"} — Python source code to execute. */
    public static final String CODE = "code";

    /** {@code "expression"} — Python expression to evaluate. */
    public static final String EXPRESSION = "expression";

    /** {@code "variables"} — variable injection map for exec/eval requests. */
    public static final String VARIABLES = "variables";

    /** {@code "module"} — Python module name for call-module requests. */
    public static final String MODULE = "module";

    /** {@code "function"} — Function name within the module. */
    public static final String FUNCTION = "function";

    /** {@code "args"} — Positional argument list. */
    public static final String ARGS = "args";

    /** {@code "kwargs"} — Keyword argument map. */
    public static final String KWARGS = "kwargs";

    /** {@code "name"} — Script, package, or session name. */
    public static final String NAME = "name";

    /** {@code "content"} — Script content / source code body. */
    public static final String CONTENT = "content";

    /** {@code "description"} — Script description metadata. */
    public static final String DESCRIPTION = "description";

    /** {@code "author"} — Script author metadata. */
    public static final String AUTHOR = "author";

    /** {@code "folderPath"} — Folder path for script organisation. */
    public static final String FOLDER_PATH = "folderPath";

    /** {@code "version"} — Script or Python version string. */
    public static final String VERSION = "version";

    // =========================================================================
    // Process pool stat fields
    // =========================================================================

    /** {@code "poolSize"} — Total number of executors in the pool. */
    public static final String POOL_SIZE = "poolSize";

    /** {@code "available"} — Number of idle executors, or availability boolean. */
    public static final String AVAILABLE = "available";

    /** {@code "inUse"} — Number of executors currently executing code. */
    public static final String IN_USE = "inUse";

    /** {@code "healthy"} — Number of healthy executors, or health boolean. */
    public static final String HEALTHY = "healthy";

    /** {@code "status"} — Textual status string (e.g. "HEALTHY", "DOWN"). */
    public static final String STATUS = "status";

    // =========================================================================
    // Version / Python info fields
    // =========================================================================

    /** {@code "pythonVersion"} — Full Python version string (e.g. "3.11.5"). */
    public static final String PYTHON_VERSION = "pythonVersion";

    /** {@code "pythonPath"} — Path to the Python executable. */
    public static final String PYTHON_PATH = "pythonPath";

    /** {@code "default"} — Default Python version identifier. */
    public static final String DEFAULT = "default";

    /** {@code "versions"} — Array of available Python version strings. */
    public static final String VERSIONS = "versions";

    /** {@code "count"} — Element count in an array response. */
    public static final String COUNT = "count";

    /** {@code "isDefault"} — Whether this version is the pool default. */
    public static final String IS_DEFAULT = "isDefault";

    /** {@code "path"} — Filesystem path string. */
    public static final String PATH = "path";

    // =========================================================================
    // Authentication fields
    // =========================================================================

    /** {@code "api_token"} — API token returned by auth endpoint. */
    public static final String API_TOKEN = "api_token";

    /** {@code "token"} — Session token (fallback field name). */
    public static final String TOKEN = "token";

    /** {@code "expires_in"} — Token validity duration in seconds. */
    public static final String EXPIRES_IN = "expires_in";

    // =========================================================================
    // Interactive shell fields
    // =========================================================================

    /** {@code "sessionId"} — Shell session identifier UUID. */
    public static final String SESSION_ID = "sessionId";

    /** {@code "output"} — Shell command output text. */
    public static final String OUTPUT = "output";

    /** {@code "stdout"} — Standard output from shell execution. */
    public static final String STDOUT = "stdout";

    /** {@code "stderr"} — Standard error from shell execution. */
    public static final String STDERR = "stderr";

    /** {@code "exitCode"} — Process exit code from shell execution. */
    public static final String EXIT_CODE = "exitCode";

    // =========================================================================
    // Script metadata fields
    // =========================================================================

    /** {@code "id"} — Script identifier. */
    public static final String ID = "id";

    /** {@code "createdDate"} — Script creation date string. */
    public static final String CREATED_DATE = "createdDate";

    /** {@code "lastModified"} — Script last-modified date string. */
    public static final String LAST_MODIFIED = "lastModified";

    /** {@code "scripts"} — Array of script objects. */
    public static final String SCRIPTS = "scripts";

    // =========================================================================
    // Syntax check / completion fields
    // =========================================================================

    /** {@code "errors"} — Array of syntax error objects. */
    public static final String ERRORS = "errors";

    /** {@code "completions"} — Array of auto-completion objects. */
    public static final String COMPLETIONS = "completions";

    /** {@code "line"} — Source line number. */
    public static final String LINE = "line";

    /** {@code "column"} — Source column number. */
    public static final String COLUMN = "column";

    /** {@code "severity"} — Error severity (e.g. "error", "warning"). */
    public static final String SEVERITY = "severity";

    /** {@code "text"} — Completion text. */
    public static final String TEXT = "text";

    /** {@code "type"} — Completion type. */
    public static final String TYPE = "type";

    /** {@code "signature"} — Completion function signature. */
    public static final String SIGNATURE = "signature";

    // =========================================================================
    // Execution metrics fields
    // =========================================================================

    /** {@code "executionTimeMs"} — Script execution time in milliseconds. */
    public static final String EXECUTION_TIME_MS = "executionTimeMs";

    /** {@code "traceback"} — Python exception traceback string. */
    public static final String TRACEBACK = "traceback";

    // =========================================================================
    // Package / distribution fields
    // =========================================================================

    /** {@code "distributions"} — Array of Python distribution objects. */
    public static final String DISTRIBUTIONS = "distributions";

    /** {@code "healthCheckStatus"} — Human-readable health status string. */
    public static final String HEALTH_CHECK_STATUS = "healthCheckStatus";
}
