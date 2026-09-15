package com.gaskony.python3.gateway;

import com.inductiveautomation.ignition.common.gson.JsonArray;
import com.inductiveautomation.ignition.common.gson.JsonObject;
import com.gaskony.python3.PoolConfig;
import com.inductiveautomation.ignition.gateway.dataroutes.RequestContext;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Handles REST API endpoints for monitoring, diagnostics, metrics, logs, and distributions.
 *
 * Covers: version, pool-stats, pool-size, health, versions, diagnostics, metrics,
 * metrics/gateway-impact, metrics/script-metrics, metrics/historical, metrics/alerts,
 * monitoring/metrics, monitoring/circuit-breaker, monitoring/alerts,
 * monitoring/prometheus, logs, distributions, distributions/install, distributions/uninstall.
 *
 * Created in v3.6.15 as part of Phase 2 refactoring of Python3RestEndpoints.
 */
class MonitoringHandlers {

    private static final Logger logger = LoggerFactory.getLogger(MonitoringHandlers.class);

    private final EndpointContext ctx;

    MonitoringHandlers(EndpointContext ctx) {
        this.ctx = ctx;
    }

    // -------------------------------------------------------------------------
    // Version / pool / health
    // -------------------------------------------------------------------------

    /**
     * Handle GET /version - Get Python version.
     *
     * Response: {"version": "...", "pythonVersion": "...", "available": true/false}
     */
    JsonObject handleGetVersion(RequestContext req, HttpServletResponse res) {
        return Python3RestEndpoints.withHandler("version", res, () -> {
            Map<String, Object> versionInfo = ctx.scriptModule.getVersion();
            JsonObject response = Python3RestEndpoints.mapToJson(versionInfo);

            // Ensure pythonVersion field exists for client compatibility (v1.17.1)
            if (response.has("python_version") && !response.has("pythonVersion")) {
                response.addProperty("pythonVersion", response.get("python_version").getAsString());
            }
            if (response.has("version") && !response.has("pythonVersion")) {
                response.addProperty("pythonVersion", response.get("version").getAsString());
            }

            // Always include the module version so the web UI displays the correct version
            response.addProperty("moduleVersion", getModuleVersion());

            return response;
        });
    }

    /** Reads the module version from version.properties bundled in common.jar. */
    private static String getModuleVersion() {
        try (InputStream is = MonitoringHandlers.class.getResourceAsStream("/version.properties")) {
            if (is != null) {
                Properties props = new Properties();
                props.load(is);
                String major = props.getProperty("version.major", "3");
                String minor = props.getProperty("version.minor", "8");
                String patch = props.getProperty("version.patch", "0");
                return major + "." + minor + "." + patch;
            }
        } catch (Exception e) {
            logger.warn("Failed to load version.properties for moduleVersion field", e);
        }
        return "4.2.0";  // ALWAYS UPDATE THIS WITH NEW RELEASES (fallback only)
    }

    /**
     * Handle GET /pool-stats - Get process pool statistics.
     *
     * Response: {"poolSize": ..., "available": ..., "inUse": ..., "healthy": ...}
     */
    JsonObject handleGetPoolStats(RequestContext req, HttpServletResponse res) {
        return Python3RestEndpoints.withHandler("pool-stats", res, () -> {
            Map<String, Object> poolStats = ctx.scriptModule.getPoolStats();
            JsonObject response = Python3RestEndpoints.mapToJson(poolStats);

            // Ensure healthCheckStatus field is present for the frontend (v3.5.2)
            if (!response.has("healthCheckStatus")) {
                boolean available = ctx.scriptModule.isAvailable();
                response.addProperty("healthCheckStatus", available ? "Healthy" : "Down");
            }

            return response;
        });
    }

    /**
     * Handle POST /pool-size - Set process pool size.
     *
     * Request body: {"size": ...}
     * Response: {"success": true/false, "poolSize": ..., "message": "..."}
     *
     * v1.17.2: New endpoint for dynamic pool size adjustment (1-20)
     */
    JsonObject handleSetPoolSize(RequestContext req, HttpServletResponse res) {
        return Python3RestEndpoints.withHandler("pool-size", res, () -> {
            Python3RestEndpoints.validateCSRFIfSession(req);

            JsonObject requestBody = Python3RestEndpoints.parseJsonBody(req);

            if (!requestBody.has("size")) {
                res.setStatus(400);
                return ApiResponse.error("Pool size parameter is required");
            }

            int newSize = requestBody.get("size").getAsInt();

            if (newSize < PoolConfig.MIN_POOL_SIZE || newSize > PoolConfig.MAX_POOL_SIZE) {
                res.setStatus(400);
                return ApiResponse.error("Pool size must be between " + PoolConfig.MIN_POOL_SIZE + " and " + PoolConfig.MAX_POOL_SIZE);
            }

            Python3RestEndpoints.auditLog("POOL_SIZE_CHANGE", "newSize=" + newSize);

            ctx.scriptModule.resizePool(newSize);

            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.addProperty("poolSize", newSize);
            response.addProperty("message", "Pool size changed to " + newSize);

            logger.info("REST API: Pool size changed to {}", newSize);
            return response;
        });
    }

    /**
     * Handle GET /health - Health check.
     *
     * Response: {"healthy": true/false, "available": true/false}
     */
    JsonObject handleHealthCheck(RequestContext req, HttpServletResponse res) {
        return Python3RestEndpoints.withHandler("health", res, () -> {
            boolean available = ctx.scriptModule.isAvailable();

            JsonObject response = new JsonObject();
            response.addProperty("healthy", available);
            response.addProperty("available", available);
            response.addProperty("status", available ? "HEALTHY" : "DOWN");
            response.addProperty("timestamp", System.currentTimeMillis());
            return response;
        });
    }

    /**
     * Handle GET /versions - Available Python versions (v3.1.0).
     *
     * Response: {"versions": ["3.10", "3.11", "3.12"], "default": "3.11", "details": {...}}
     */
    JsonObject handleGetVersions(RequestContext req, HttpServletResponse res) {
        return Python3RestEndpoints.withHandler("versions", res, () -> {
            JsonObject response = new JsonObject();

            if (ctx.poolManager != null) {
                java.util.List<String> versions = ctx.poolManager.getAvailableVersions();

                JsonArray versionArray = new JsonArray();
                for (String v : versions) {
                    versionArray.add(v);
                }
                response.add("versions", versionArray);
                response.addProperty("default", ctx.poolManager.getDefaultVersion());
                response.addProperty("count", versions.size());

                JsonObject details = new JsonObject();
                for (String v : versions) {
                    JsonObject versionDetail = new JsonObject();
                    String path = ctx.poolManager.getPythonPath(v);
                    versionDetail.addProperty("path", path != null ? path : "unknown");
                    versionDetail.addProperty("isDefault", v.equals(ctx.poolManager.getDefaultVersion()));
                    try {
                        Python3ProcessPool pool = ctx.poolManager.getPool(v);
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
                // Fallback: single version mode
                JsonArray versionArray = new JsonArray();
                java.util.List<String> versions = ctx.scriptModule.getAvailableVersions();
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
            return response;
        });
    }

    /**
     * Handle GET /diagnostics - Performance diagnostics.
     *
     * Response: comprehensive diagnostic information
     */
    JsonObject handleDiagnostics(RequestContext req, HttpServletResponse res) {
        return Python3RestEndpoints.withHandler("diagnostics", res, () -> {
            JsonObject response = new JsonObject();

            boolean available = ctx.scriptModule.isAvailable();
            response.addProperty("available", available);

            Map<String, Object> poolStats = ctx.scriptModule.getPoolStats();
            response.add("poolStats", Python3RestEndpoints.mapToJson(poolStats));

            Map<String, Object> versionInfo = ctx.scriptModule.getVersion();
            response.add("versionInfo", Python3RestEndpoints.mapToJson(versionInfo));

            Map<String, Object> distributionInfo = ctx.scriptModule.getDistributionInfo();
            response.add("distributionInfo", Python3RestEndpoints.mapToJson(distributionInfo));

            response.addProperty("timestamp", System.currentTimeMillis());
            return response;
        });
    }

    // -------------------------------------------------------------------------
    // Metrics
    // -------------------------------------------------------------------------

    /**
     * Handle GET /metrics - Get performance metrics.
     *
     * Response: {"total_executions": ..., "success_rate": ..., "health_score": ..., ...}
     */
    JsonObject handleGetMetrics(RequestContext req, HttpServletResponse res) {
        return Python3RestEndpoints.withHandler("metrics", res, () -> {
            Map<String, Object> metrics = ctx.metricsCollector.getMetrics();
            return Python3RestEndpoints.mapToJson(metrics);
        });
    }

    /**
     * Handle GET /gateway-impact - Get Gateway impact assessment.
     *
     * Response: {"executions_per_minute": ..., "pool_utilization_percent": ..., "impact_level": "LOW|MEDIUM|HIGH", ...}
     */
    JsonObject handleGetGatewayImpact(RequestContext req, HttpServletResponse res) {
        return Python3RestEndpoints.withHandler("gateway-impact", res, () -> {
            Map<String, Object> impact = ctx.metricsCollector.getGatewayImpact();
            return Python3RestEndpoints.mapToJson(impact);
        });
    }

    /**
     * Handle GET /metrics/script-metrics - Get per-script performance metrics (NEW v1.16.0).
     *
     * Response: [{"script_identifier": "...", "total_executions": ..., "success_rate": ..., ...}, ...]
     */
    JsonObject handleGetScriptMetrics(RequestContext req, HttpServletResponse res) {
        return Python3RestEndpoints.withHandler("metrics/script-metrics", res, () -> {
            List<Map<String, Object>> scriptMetrics = ctx.metricsCollector.getScriptMetrics();

            JsonObject response = new JsonObject();
            response.addProperty("success", true);

            JsonArray metricsArray = new JsonArray();
            for (Map<String, Object> metrics : scriptMetrics) {
                metricsArray.add(Python3RestEndpoints.mapToJson(metrics));
            }
            response.add("script_metrics", metricsArray);
            response.addProperty("count", metricsArray.size());

            logger.debug("REST API: /metrics/script-metrics found {} scripts", metricsArray.size());
            return response;
        });
    }

    /**
     * Handle GET /metrics/historical - Get historical metric snapshots (NEW v1.16.0).
     *
     * Response: [{"timestamp": ..., "total_executions": ..., "pool_utilization": ..., ...}, ...]
     */
    JsonObject handleGetHistoricalMetrics(RequestContext req, HttpServletResponse res) {
        return Python3RestEndpoints.withHandler("metrics/historical", res, () -> {
            List<Map<String, Object>> historicalMetrics = ctx.metricsCollector.getHistoricalMetrics();

            JsonObject response = new JsonObject();
            response.addProperty("success", true);

            JsonArray historyArray = new JsonArray();
            for (Map<String, Object> snapshot : historicalMetrics) {
                historyArray.add(Python3RestEndpoints.mapToJson(snapshot));
            }
            response.add("historical_metrics", historyArray);
            response.addProperty("count", historyArray.size());

            logger.debug("REST API: /metrics/historical found {} snapshots", historyArray.size());
            return response;
        });
    }

    /**
     * Handle GET /metrics/alerts - Get active health alerts (NEW v1.16.0).
     *
     * Response: [{"timestamp": ..., "alert_id": "...", "message": "...", "severity": "WARNING|CRITICAL"}, ...]
     */
    JsonObject handleGetHealthAlerts(RequestContext req, HttpServletResponse res) {
        return Python3RestEndpoints.withHandler("metrics/alerts", res, () -> {
            List<Map<String, Object>> healthAlerts = ctx.metricsCollector.getHealthAlerts();

            JsonObject response = new JsonObject();
            response.addProperty("success", true);

            JsonArray alertsArray = new JsonArray();
            for (Map<String, Object> alert : healthAlerts) {
                alertsArray.add(Python3RestEndpoints.mapToJson(alert));
            }
            response.add("alerts", alertsArray);
            response.addProperty("count", alertsArray.size());

            logger.debug("REST API: /metrics/alerts found {} active alerts", alertsArray.size());
            return response;
        });
    }

    // -------------------------------------------------------------------------
    // Enhanced monitoring (v2.14.0+)
    // -------------------------------------------------------------------------

    /**
     * Handle GET /monitoring/metrics - Get enhanced monitoring metrics.
     *
     * @since v2.14.0 Phase 2 Week 3-4
     */
    JsonObject handleGetEnhancedMetrics(RequestContext req, HttpServletResponse res) {
        return Python3RestEndpoints.withHandler("monitoring/metrics", res, () -> {
            if (ctx.scriptModule == null || ctx.scriptModule.getProcessPool() == null) {
                return ApiResponse.error("Process pool not available");
            }

            Python3ProcessPool pool = ctx.scriptModule.getProcessPool();
            MetricsCollector collector = pool.getMetricsCollector();

            if (collector == null) {
                return ApiResponse.error("Metrics collector not available");
            }

            JsonObject response = new JsonObject();
            response.addProperty("success", true);

            response.addProperty("totalBorrows", collector.getTotalBorrows());
            response.addProperty("totalReturns", collector.getTotalReturns());
            response.addProperty("totalExecutions", collector.getTotalExecutions());
            response.addProperty("successfulExecutions", collector.getSuccessfulExecutions());
            response.addProperty("failedExecutions", collector.getFailedExecutions());
            response.addProperty("timeoutWaits", collector.getTimeoutWaits());

            response.addProperty("p50ResponseTimeMs", collector.getP50ResponseTime());
            response.addProperty("p95ResponseTimeMs", collector.getP95ResponseTime());
            response.addProperty("p99ResponseTimeMs", collector.getP99ResponseTime());

            long total = collector.getTotalExecutions();
            long failed = collector.getFailedExecutions();
            double errorRate = total > 0 ? (double) failed / total * 100.0 : 0.0;
            response.addProperty("errorRatePercent", String.format("%.2f", errorRate));

            response.addProperty("avgQueueWaitMs", collector.getAverageQueueWaitTimeMs());
            response.addProperty("maxQueueWaitMs", collector.getMaxQueueWaitTimeMs());

            return response;
        });
    }

    /**
     * Handle GET /monitoring/circuit-breaker - Get circuit breaker status.
     *
     * @since v2.14.0 Phase 2 Week 3-4
     */
    JsonObject handleGetCircuitBreakerStatus(RequestContext req, HttpServletResponse res) {
        return Python3RestEndpoints.withHandler("monitoring/circuit-breaker", res, () -> {
            if (ctx.scriptModule == null || ctx.scriptModule.getProcessPool() == null) {
                return ApiResponse.error("Process pool not available");
            }

            Python3ProcessPool pool = ctx.scriptModule.getProcessPool();
            CircuitBreaker breaker = pool.getCircuitBreaker();

            if (breaker == null) {
                return ApiResponse.error("Circuit breaker not available");
            }

            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.addProperty("state", breaker.getState().toString());
            response.addProperty("failureCount", breaker.getFailureCount());
            response.addProperty("totalOpens", breaker.getTotalOpens());
            response.addProperty("totalCloses", breaker.getTotalCloses());
            response.addProperty("totalRejections", breaker.getTotalRejections());
            response.addProperty("timeSinceStateChangeMs", breaker.getTimeSinceStateChange());
            return response;
        });
    }

    /**
     * Handle GET /monitoring/alerts - Get alert manager status.
     *
     * @since v2.14.0 Phase 2 Week 3-4
     */
    JsonObject handleGetAlertManagerStatus(RequestContext req, HttpServletResponse res) {
        return Python3RestEndpoints.withHandler("monitoring/alerts", res, () -> {
            if (ctx.scriptModule == null || ctx.scriptModule.getProcessPool() == null) {
                return ApiResponse.error("Process pool not available");
            }

            Python3ProcessPool pool = ctx.scriptModule.getProcessPool();
            AlertManager alertManager = pool.getAlertManager();

            if (alertManager == null) {
                return ApiResponse.error("Alert manager not available");
            }

            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.addProperty("totalAlerts", alertManager.getTotalAlerts());
            response.addProperty("suppressedAlerts", alertManager.getSuppressedAlerts());
            response.addProperty("alertSummary", alertManager.getAlertSummary());
            return response;
        });
    }

    /**
     * Handle GET /monitoring/prometheus - Get Prometheus metrics (NEW v2.15.0 Phase 3 Week 1-2).
     *
     * Special-case handler: writes plain text directly to the response and returns null.
     * Calls applySecurityHeaders directly instead of using withHandler.
     *
     * Returns metrics in Prometheus text format for scraping.
     */
    JsonObject handleGetPrometheusMetrics(RequestContext req, HttpServletResponse res) {
        logger.debug("REST API: /monitoring/prometheus called");
        Python3RestEndpoints.applySecurityHeaders(res);

        try {
            if (ctx.scriptModule == null || ctx.scriptModule.getProcessPool() == null) {
                res.setContentType("text/plain; charset=utf-8");
                res.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                res.getWriter().write("# ERROR: Process pool not available\n");
                res.getWriter().flush();
                return null;
            }

            Python3ProcessPool pool = ctx.scriptModule.getProcessPool();
            Python3ProcessPool.PoolStats stats = pool.getStats();
            MetricsCollector metricsCollector = pool.getMetricsCollector();

            String prometheusMetrics = PrometheusExporter.exportMetrics(stats, metricsCollector);

            res.setContentType("text/plain; version=0.0.4; charset=utf-8");
            res.setStatus(HttpServletResponse.SC_OK);
            res.getWriter().write(prometheusMetrics);
            res.getWriter().flush();

            logger.debug("REST API: /monitoring/prometheus completed successfully");
            return null;  // Response already written

        } catch (Exception e) {
            logger.error("REST API: /monitoring/prometheus failed", e);
            try {
                res.setContentType("text/plain; charset=utf-8");
                res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                res.getWriter().write("# ERROR: " + e.getMessage() + "\n");
                res.getWriter().flush();
            } catch (Exception writeError) {
                logger.error("Failed to write error response", writeError);
            }
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Logs
    // -------------------------------------------------------------------------

    /**
     * Handle GET /logs - Read gateway logs from wrapper.log (v3.5.0).
     *
     * Query parameters:
     *   lines  - max entries to return (default 100)
     *   level  - minimum level filter: ALL, DEBUG, INFO, WARN, ERROR (default ALL)
     *   filter - text search (case-insensitive)
     *   after  - line offset for pagination (default 0)
     */
    JsonObject handleGetLogs(RequestContext req, HttpServletResponse res) {
        return Python3RestEndpoints.withHandler("logs", res, () -> {
            String linesParam = req.getRequest().getParameter("lines");
            String levelParam = req.getRequest().getParameter("level");
            String filterParam = req.getRequest().getParameter("filter");
            String afterParam = req.getRequest().getParameter("after");

            int lines = 100;
            if (linesParam != null) {
                try { lines = Math.min(500, Math.max(1, Integer.parseInt(linesParam))); }
                catch (NumberFormatException ignored) {}
            }

            long after = 0;
            if (afterParam != null) {
                try { after = Math.max(0, Long.parseLong(afterParam)); }
                catch (NumberFormatException ignored) {}
            }

            return Python3LogsHandler.readLogs(ctx.logsDir, lines, levelParam, filterParam, after);
        });
    }

    // -------------------------------------------------------------------------
    // Python Distribution Management (v3.1.0)
    // -------------------------------------------------------------------------

    /**
     * Handle GET /distributions - List all Python distributions with install status.
     *
     * Response: {"distributions": [...], "os": "linux", "installedCount": 1}
     */
    JsonObject handleGetDistributions(RequestContext req, HttpServletResponse res) {
        return Python3RestEndpoints.withHandler("distributions", res, () -> {
            if (ctx.distributionManager == null) {
                return ApiResponse.error("Distribution manager not initialized");
            }

            JsonObject response = new JsonObject();
            JsonArray distributions = new JsonArray();

            for (PythonDistributionManager.VersionStatus status : ctx.distributionManager.getAllVersionStatuses()) {
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

                if (status.installed && ctx.poolManager != null && ctx.poolManager.isVersionAvailable(status.version)) {
                    dist.addProperty("poolActive", true);
                } else {
                    dist.addProperty("poolActive", false);
                }

                distributions.add(dist);
            }

            response.add("distributions", distributions);
            response.addProperty("os", ctx.distributionManager.detectOS());
            response.addProperty("installedCount", ctx.distributionManager.getInstalledVersions().size());
            response.addProperty("success", true);
            response.addProperty("timestamp", System.currentTimeMillis());
            return response;
        });
    }

    /**
     * Handle POST /distributions/install - Install a Python version.
     *
     * Request body: {"version": "3.12"}
     * Response: {"success": true, "version": "3.12", "fullVersion": "3.12.1", "pythonPath": "..."}
     */
    JsonObject handleInstallDistribution(RequestContext req, HttpServletResponse res) {
        logger.info("REST API: /distributions/install called");
        return Python3RestEndpoints.withHandler("distributions/install", res, () -> {
            // Downloading and unpacking a Python distribution onto the Gateway
            // is code execution by any reasonable reading. Gate it like /exec.
            Python3RestEndpoints.determineSecurityMode(req);
            Python3RestEndpoints.validateCSRFIfSession(req);

            if (ctx.distributionManager == null) {
                return ApiResponse.error("Distribution manager not initialized");
            }

            JsonObject requestJson = Python3RestEndpoints.parseJsonBody(req);
            String version = requestJson.get("version").getAsString();

            if (version == null || version.trim().isEmpty()) {
                return ApiResponse.error("Missing required field: version");
            }

            version = version.trim();
            logger.info("Installing Python version: {}", version);

            ctx.distributionManager.installVersion(version);

            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.addProperty("version", version);
            response.addProperty("fullVersion", ctx.distributionManager.getFullVersion(version));
            response.addProperty("pythonPath", ctx.distributionManager.getVersionExecutablePath(version));
            response.addProperty("message", "Python " + version + " installed successfully");
            response.addProperty("timestamp", System.currentTimeMillis());
            return response;
        });
    }

    /**
     * Handle POST /distributions/uninstall - Uninstall a Python version.
     *
     * Request body: {"version": "3.12"}
     * Response: {"success": true, "version": "3.12", "message": "..."}
     */
    JsonObject handleUninstallDistribution(RequestContext req, HttpServletResponse res) {
        logger.info("REST API: /distributions/uninstall called");
        return Python3RestEndpoints.withHandler("distributions/uninstall", res, () -> {
            // Removing an interpreter changes what every saved script runs on.
            Python3RestEndpoints.determineSecurityMode(req);
            Python3RestEndpoints.validateCSRFIfSession(req);

            if (ctx.distributionManager == null) {
                return ApiResponse.error("Distribution manager not initialized");
            }

            JsonObject requestJson = Python3RestEndpoints.parseJsonBody(req);
            String version = requestJson.get("version").getAsString();

            if (version == null || version.trim().isEmpty()) {
                return ApiResponse.error("Missing required field: version");
            }

            version = version.trim();

            if (ctx.poolManager != null && ctx.poolManager.isVersionAvailable(version)) {
                logger.warn("Uninstalling Python {} which has an active pool. Pool will become invalid.", version);
            }

            logger.info("Uninstalling Python version: {}", version);
            ctx.distributionManager.uninstallVersion(version);

            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.addProperty("version", version);
            response.addProperty("message", "Python " + version + " uninstalled successfully. Restart module to update pools.");
            response.addProperty("timestamp", System.currentTimeMillis());
            return response;
        });
    }
}
