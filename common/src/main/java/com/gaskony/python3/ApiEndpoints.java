package com.gaskony.python3;

/**
 * Centralised HTTP API route and path constants for the Python 3 Integration module.
 *
 * <p>Single source of truth for all REST endpoint path strings. When an endpoint
 * path changes, update only this file and every caller benefits automatically.</p>
 *
 * <p>Usage patterns:</p>
 * <ul>
 *   <li>Gateway route registration: {@code routes.newRoute(ApiEndpoints.ROUTE_EXEC)}</li>
 *   <li>Gateway Web UI (browser REST client): builds request paths from the
 *       {@code ROUTE_*} constants below</li>
 * </ul>
 *
 * <p>The Designer no longer has an HTTP REST client (v4.3.0) — it calls the
 * Gateway over authenticated module RPC ({@code Python3Rpc}) instead, so the
 * designer-client-only path-building constants ({@code CLIENT_API_BASE},
 * {@code CLIENT_AUTH_BASE}, and the package install/uninstall URL-encoding
 * prefixes) were removed. The {@code ROUTE_*} constants and their segment
 * components below remain — the Gateway REST API still serves the browser
 * Web UI.</p>
 */
public final class ApiEndpoints {

    private ApiEndpoints() {
        // Utility class - no instances
    }

    // =========================================================================
    // Module identity
    // =========================================================================

    /** Ignition data-route alias for this module (used in URL path segments). */
    public static final String MODULE_ALIAS = "python3integration";

    // =========================================================================
    // Route prefixes (relative to module mount point)
    // =========================================================================

    /** Prefix for all REST API v1 routes as registered with the gateway RouteGroup. */
    public static final String API_V1_PREFIX = "/api/v1";

    /** Prefix for authentication routes as registered with the gateway RouteGroup. */
    public static final String AUTH_PREFIX = "/auth";

    // =========================================================================
    // Auth endpoint segments
    // =========================================================================

    public static final String AUTH_SESSION = "/session";

    // Full gateway route
    public static final String ROUTE_AUTH_SESSION = AUTH_PREFIX + AUTH_SESSION;

    // =========================================================================
    // Core execution endpoint segments
    // =========================================================================

    public static final String EXEC = "/exec";
    public static final String EVAL = "/eval";
    public static final String CALL_MODULE = "/call-module";
    public static final String CALL_SCRIPT = "/call-script";
    public static final String EXAMPLE = "/example";

    // =========================================================================
    // Shell endpoint segments
    // =========================================================================

    public static final String SHELL_EXEC = "/shell-exec";
    public static final String SHELL_INTERACTIVE_CREATE = "/shell-interactive/create";
    public static final String SHELL_INTERACTIVE_EXEC = "/shell-interactive/exec";
    public static final String SHELL_INTERACTIVE_CLOSE = "/shell-interactive/close";

    // =========================================================================
    // Status / diagnostic endpoint segments
    // =========================================================================

    public static final String POOL_STATS = "/pool-stats";
    public static final String POOL_SIZE = "/pool-size";
    public static final String HEALTH = "/health";
    public static final String DIAGNOSTICS = "/diagnostics";
    public static final String VERSION = "/version";
    public static final String VERSIONS = "/versions";
    public static final String GATEWAY_IMPACT = "/gateway-impact";
    public static final String LOGS = "/logs";

    // =========================================================================
    // Code analysis endpoint segments
    // =========================================================================

    public static final String CHECK_SYNTAX = "/check-syntax";
    public static final String COMPLETIONS = "/completions";

    // =========================================================================
    // Metrics / monitoring endpoint segments
    // =========================================================================

    public static final String METRICS = "/metrics";
    public static final String METRICS_SCRIPT_METRICS = "/metrics/script-metrics";
    public static final String METRICS_HISTORICAL = "/metrics/historical";
    public static final String METRICS_ALERTS = "/metrics/alerts";
    public static final String MONITORING_METRICS = "/monitoring/metrics";
    public static final String MONITORING_CIRCUIT_BREAKER = "/monitoring/circuit-breaker";
    public static final String MONITORING_ALERTS = "/monitoring/alerts";
    public static final String MONITORING_PROMETHEUS = "/monitoring/prometheus";

    // =========================================================================
    // Script management endpoint segments
    // =========================================================================

    public static final String SCRIPTS_SAVE = "/scripts/save";
    /** Gateway route form — uses :name placeholder. Client appends the encoded name to {@link #SCRIPTS_LOAD_PREFIX}. */
    public static final String SCRIPTS_LOAD = "/scripts/load/:name";
    /** Client prefix for script-load requests; append URL-encoded script name. */
    public static final String SCRIPTS_LOAD_PREFIX = "/scripts/load/";
    public static final String SCRIPTS_LIST = "/scripts/list";
    /** Gateway route form — uses :name placeholder. Client appends the encoded name to {@link #SCRIPTS_DELETE_PREFIX}. */
    public static final String SCRIPTS_DELETE = "/scripts/delete/:name";
    /** Client prefix for script-delete requests; append URL-encoded script name. */
    public static final String SCRIPTS_DELETE_PREFIX = "/scripts/delete/";
    public static final String SCRIPTS_AVAILABLE = "/scripts/available";

    // =========================================================================
    // Package management endpoint segments
    // =========================================================================

    public static final String PACKAGES_CATALOG = "/packages/catalog";
    public static final String PACKAGES_STATUS = "/packages/status";
    /** Gateway route form — uses :name placeholder. */
    public static final String PACKAGES_INSTALL = "/packages/install/:name";
    /** Gateway route form — uses :name placeholder. */
    public static final String PACKAGES_UNINSTALL = "/packages/uninstall/:name";
    public static final String PACKAGES_VERIFY = "/packages/verify";
    public static final String PACKAGES_SEARCH_PYPI = "/packages/search-pypi";
    /** Gateway route form — uses :name placeholder. Client appends the encoded name to {@link #PACKAGES_PYPI_INFO_PREFIX}. */
    public static final String PACKAGES_PYPI_INFO = "/packages/pypi-info/:name";
    /** Client prefix for PyPI-info requests; append URL-encoded package name. */
    public static final String PACKAGES_PYPI_INFO_PREFIX = "/packages/pypi-info/";

    // =========================================================================
    // Python distribution endpoint segments
    // =========================================================================

    public static final String DISTRIBUTIONS = "/distributions";
    public static final String DISTRIBUTIONS_INSTALL = "/distributions/install";
    public static final String DISTRIBUTIONS_UNINSTALL = "/distributions/uninstall";

    // =========================================================================
    // Full gateway routes  (API_V1_PREFIX + segment)
    // Used in Python3RestEndpoints.mountRoutes() calls to routes.newRoute(...)
    // =========================================================================

    public static final String ROUTE_EXEC                      = API_V1_PREFIX + EXEC;
    public static final String ROUTE_EVAL                      = API_V1_PREFIX + EVAL;
    public static final String ROUTE_CALL_MODULE               = API_V1_PREFIX + CALL_MODULE;
    public static final String ROUTE_CALL_SCRIPT               = API_V1_PREFIX + CALL_SCRIPT;
    public static final String ROUTE_EXAMPLE                   = API_V1_PREFIX + EXAMPLE;
    public static final String ROUTE_SHELL_EXEC                = API_V1_PREFIX + SHELL_EXEC;
    public static final String ROUTE_SHELL_INTERACTIVE_CREATE  = API_V1_PREFIX + SHELL_INTERACTIVE_CREATE;
    public static final String ROUTE_SHELL_INTERACTIVE_EXEC    = API_V1_PREFIX + SHELL_INTERACTIVE_EXEC;
    public static final String ROUTE_SHELL_INTERACTIVE_CLOSE   = API_V1_PREFIX + SHELL_INTERACTIVE_CLOSE;
    public static final String ROUTE_POOL_STATS                = API_V1_PREFIX + POOL_STATS;
    public static final String ROUTE_POOL_SIZE                 = API_V1_PREFIX + POOL_SIZE;
    public static final String ROUTE_HEALTH                    = API_V1_PREFIX + HEALTH;
    public static final String ROUTE_DIAGNOSTICS               = API_V1_PREFIX + DIAGNOSTICS;
    public static final String ROUTE_VERSION                   = API_V1_PREFIX + VERSION;
    public static final String ROUTE_VERSIONS                  = API_V1_PREFIX + VERSIONS;
    public static final String ROUTE_GATEWAY_IMPACT            = API_V1_PREFIX + GATEWAY_IMPACT;
    public static final String ROUTE_LOGS                      = API_V1_PREFIX + LOGS;
    public static final String ROUTE_CHECK_SYNTAX              = API_V1_PREFIX + CHECK_SYNTAX;
    public static final String ROUTE_COMPLETIONS               = API_V1_PREFIX + COMPLETIONS;
    public static final String ROUTE_METRICS                   = API_V1_PREFIX + METRICS;
    public static final String ROUTE_METRICS_SCRIPT_METRICS    = API_V1_PREFIX + METRICS_SCRIPT_METRICS;
    public static final String ROUTE_METRICS_HISTORICAL        = API_V1_PREFIX + METRICS_HISTORICAL;
    public static final String ROUTE_METRICS_ALERTS            = API_V1_PREFIX + METRICS_ALERTS;
    public static final String ROUTE_MONITORING_METRICS        = API_V1_PREFIX + MONITORING_METRICS;
    public static final String ROUTE_MONITORING_CIRCUIT_BREAKER = API_V1_PREFIX + MONITORING_CIRCUIT_BREAKER;
    public static final String ROUTE_MONITORING_ALERTS         = API_V1_PREFIX + MONITORING_ALERTS;
    public static final String ROUTE_MONITORING_PROMETHEUS     = API_V1_PREFIX + MONITORING_PROMETHEUS;
    public static final String ROUTE_SCRIPTS_SAVE              = API_V1_PREFIX + SCRIPTS_SAVE;
    public static final String ROUTE_SCRIPTS_LOAD              = API_V1_PREFIX + SCRIPTS_LOAD;
    public static final String ROUTE_SCRIPTS_LIST              = API_V1_PREFIX + SCRIPTS_LIST;
    public static final String ROUTE_SCRIPTS_DELETE            = API_V1_PREFIX + SCRIPTS_DELETE;
    public static final String ROUTE_SCRIPTS_AVAILABLE         = API_V1_PREFIX + SCRIPTS_AVAILABLE;
    public static final String ROUTE_PACKAGES_CATALOG          = API_V1_PREFIX + PACKAGES_CATALOG;
    public static final String ROUTE_PACKAGES_STATUS           = API_V1_PREFIX + PACKAGES_STATUS;
    public static final String ROUTE_PACKAGES_INSTALL          = API_V1_PREFIX + PACKAGES_INSTALL;
    public static final String ROUTE_PACKAGES_UNINSTALL        = API_V1_PREFIX + PACKAGES_UNINSTALL;
    public static final String ROUTE_PACKAGES_VERIFY           = API_V1_PREFIX + PACKAGES_VERIFY;
    public static final String ROUTE_PACKAGES_SEARCH_PYPI      = API_V1_PREFIX + PACKAGES_SEARCH_PYPI;
    public static final String ROUTE_PACKAGES_PYPI_INFO        = API_V1_PREFIX + PACKAGES_PYPI_INFO;
    public static final String ROUTE_DISTRIBUTIONS             = API_V1_PREFIX + DISTRIBUTIONS;
    public static final String ROUTE_DISTRIBUTIONS_INSTALL     = API_V1_PREFIX + DISTRIBUTIONS_INSTALL;
    public static final String ROUTE_DISTRIBUTIONS_UNINSTALL   = API_V1_PREFIX + DISTRIBUTIONS_UNINSTALL;
}
