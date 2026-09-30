package com.gaskony.python3.gateway;

/**
 * Holds all service dependencies needed by handler companion classes.
 * Package-private: only visible within the gateway package.
 *
 * Created in v3.6.15 as part of Phase 2 refactoring of Python3RestEndpoints.
 */
class EndpointContext {
    final Python3ScriptModule scriptModule;
    final Python3ScriptRepository scriptRepository;
    final Python3SecurityService securityService;
    final Python3AuditLogger auditLogger;
    final PythonDistributionManager distributionManager;
    final java.io.File logsDir;
    final Python3MetricsCollector metricsCollector;

    // NOT final (v4.3.1): these two services are created on the deferred-init
    // daemon thread AFTER the platform mounts routes and this snapshot is built,
    // so final fields stayed null forever and every REST endpoint needing them
    // reported "not initialized" (the web UI Packages page showed 0 packages).
    // Python3RestEndpoints.setPackageManager/setPoolManager rewire the live
    // context when the services come up. Volatile: written by the init thread,
    // read by HTTP handler threads.
    volatile Python3PackageManager packageManager;
    volatile PoolManager poolManager;

    EndpointContext(
            Python3ScriptModule scriptModule,
            Python3ScriptRepository scriptRepository,
            Python3PackageManager packageManager,
            Python3SecurityService securityService,
            Python3AuditLogger auditLogger,
            PoolManager poolManager,
            PythonDistributionManager distributionManager,
            java.io.File logsDir,
            Python3MetricsCollector metricsCollector) {
        this.scriptModule = scriptModule;
        this.scriptRepository = scriptRepository;
        this.packageManager = packageManager;
        this.securityService = securityService;
        this.auditLogger = auditLogger;
        this.poolManager = poolManager;
        this.distributionManager = distributionManager;
        this.logsDir = logsDir;
        this.metricsCollector = metricsCollector;
    }
}
