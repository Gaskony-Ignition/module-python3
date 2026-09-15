package com.gaskony.python3.gateway;

import com.inductiveautomation.ignition.common.licensing.LicenseState;
import com.inductiveautomation.ignition.common.script.ScriptManager;
import com.inductiveautomation.ignition.common.script.hints.PropertiesFileDocProvider;
import com.gaskony.python3.PoolConfig;
import com.inductiveautomation.ignition.common.rpc.proto.ProtoRpcSerializer;
import com.inductiveautomation.ignition.gateway.dataroutes.RouteGroup;
import com.inductiveautomation.ignition.gateway.model.AbstractGatewayModuleHook;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;
import com.inductiveautomation.ignition.gateway.rpc.GatewayRpcImplementation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Gateway hook for Python 3 Integration module.
 * Manages the lifecycle of the Python process pool and registers scripting functions.
 */
public class GatewayHook extends AbstractGatewayModuleHook {

    private static final Logger logger = LoggerFactory.getLogger(GatewayHook.class);

    private GatewayContext gatewayContext;
    private Python3ProcessPool processPool;  // Default pool (backward compatibility)
    private PoolManager poolManager;
    private PythonDistributionManager distributionManager;
    private Python3ScriptModule scriptModule;
    private Python3ScriptRepository scriptRepository;
    private Python3PackageManager packageManager;
    private Python3AuditLogger auditLogger;
    private Python3SecurityService securityService;

    // Configuration
    private volatile int poolSize = PoolConfig.DEFAULT_POOL_SIZE;
    private volatile boolean autoDownload = true; // Auto-download Python by default
    private String defaultPythonVersion = null; // Configured default version

    /**
     * Future that completes when the asynchronous startup work (pool init + optional Jedi
     * install) has finished. Callers that need a fully-initialised pool — e.g. REST handlers
     * mounted before the daemon thread completes — should consult this rather than busy-wait
     * on {@link #processPool} being non-null.
     *
     * <p>Marked completed exceptionally if pool init throws; consumers can decide whether to
     * surface a 503 Service Unavailable, retry, or degrade.
     *
     * @since v3.13.0 (async startup)
     */
    private final CompletableFuture<Void> readinessFuture = new CompletableFuture<>();

    /** Daemon thread doing the deferred pool init; held so {@link #shutdown} can interrupt it. */
    private final AtomicReference<Thread> initThreadRef = new AtomicReference<>();

    @Override
    public void setup(GatewayContext context) {
        this.gatewayContext = context;
        logger.info("Python 3 Integration module setup");

        // Register Gateway Web UI navigation (v3.2.0)
        try {
            com.inductiveautomation.ignition.gateway.web.systemjs.SystemJsModule jsModule =
                new com.inductiveautomation.ignition.gateway.web.systemjs.SystemJsModule(
                    "Python3IDE",
                    "/res/python3integration/Python3IDE.js"
                );

            context.getWebResourceManager()
                .getNavigationModel()
                .getHome()
                .addCategory("Python3", cat -> cat
                    .label("Python 3")
                    .addPage("IDE", page -> page
                        .position(200)
                        .mount("/python3-ide", "Python3IDE", jsModule)
                    )
                );

            logger.info("Python 3 IDE Web UI registered at /system/app/python3-ide");
        } catch (Exception e) {
            logger.error("Failed to register Web UI navigation: {}", e.getMessage(), e);
        }

        // Load configuration
        loadConfiguration();

        // Initialize distribution manager
        distributionManager = new PythonDistributionManager(
                context.getSystemManager().getDataDir().toPath().resolve("python3-integration"),
                autoDownload
        );

        // Initialize script repository
        try {
            scriptRepository = new Python3ScriptRepository(
                    context.getSystemManager().getDataDir().toPath().resolve("python3-integration")
            );
            logger.info("Script repository initialized");
        } catch (IOException e) {
            logger.error("Failed to initialize script repository", e);
        }

        // Test servlet not implemented - testing via Designer IDE and REST API
        logger.info("Testing available via Designer Python 3 IDE and REST API endpoints");
    }

    @Override
    public void startup(LicenseState licenseState) {
        logger.info("Python 3 Integration module startup");

        // ----- Fast-path: things that must complete before startup() returns -----
        //
        // Initialise the audit logger and security service synchronously because they
        // have no blocking I/O and several downstream consumers (e.g. REST handler
        // wiring inside #mountRouteHandlers) need them set.

        // Initialize audit logger (v2.6.0)
        String logsDir = System.getProperty("ignition.logs.dir", "logs");
        auditLogger = new Python3AuditLogger(logsDir);
        logger.info("Audit logger initialized: {}", auditLogger.getAuditLogPath());

        // Initialize security service (v2.6.0)
        securityService = new Python3SecurityService(this);
        logger.info("Security service initialized");

        // ----- Slow-path: pool spawn + optional Jedi install -----
        //
        // Each Python interpreter takes 1–4 s to spawn; the multi-version configuration
        // can require 10–40 s. The Jedi auto-install can wait up to 120 s on first boot.
        // Per `xc-performance.md` HIGH and `mod-python3.md`, blocking the Gateway lifecycle
        // thread for that long is a SDK contract violation.
        //
        // Defer all of it to a daemon thread; #mountRouteHandlers and the script module
        // tolerate a not-yet-ready pool by deferring to {@link #readinessFuture}.

        Thread initThread = new Thread(this::runDeferredInit, "Python3-DeferredInit");
        initThread.setDaemon(true);
        initThreadRef.set(initThread);
        initThread.start();

        logger.info("Python 3 Integration module startup() returning; pool initialisation continues asynchronously "
            + "(monitor readinessFuture for completion)");
    }

    /**
     * Heavy initialisation work executed on a daemon thread so {@link #startup} can return
     * within milliseconds even if Python or Jedi take tens of seconds to come online.
     *
     * <p>Completes {@link #readinessFuture} normally on success; exceptionally on failure
     * (logged at ERROR with operator guidance).
     *
     * @since v3.13.0
     */
    private void runDeferredInit() {
        long start = System.nanoTime();
        try {
            // Load configured Python versions (v3.1.0)
            Map<String, String> configuredVersions = loadConfiguredVersions();

            // Also add any versions installed via the distribution manager
            for (String installedVersion : distributionManager.getInstalledVersions()) {
                if (!configuredVersions.containsKey(installedVersion)) {
                    String execPath = distributionManager.getVersionExecutablePath(installedVersion);
                    if (execPath != null) {
                        configuredVersions.put(installedVersion, execPath);
                        logger.info("Found installed distribution: Python {} at {}", installedVersion, execPath);
                    }
                }
            }

            if (configuredVersions.isEmpty()) {
                // Single-version mode (backward compatible)
                String pythonPath = distributionManager.getPythonPath();
                String detectedVersion = detectPythonVersion(pythonPath);
                configuredVersions.put(detectedVersion, pythonPath);
                if (defaultPythonVersion == null) {
                    defaultPythonVersion = detectedVersion;
                }
                logger.info("Single Python version mode: {} at {}", detectedVersion, pythonPath);
            }

            if (defaultPythonVersion == null) {
                defaultPythonVersion = configuredVersions.keySet().iterator().next();
            }

            // Initialize PoolManager (v3.1.0)
            PoolManager localPoolManager = new PoolManager(defaultPythonVersion);

            for (Map.Entry<String, String> entry : configuredVersions.entrySet()) {
                String version = entry.getKey();
                String pythonPath = entry.getValue();

                logger.info("Initializing Python {} pool (size: {}): {}", version, poolSize, pythonPath);
                try {
                    Python3ProcessPool pool = new Python3ProcessPool(pythonPath, poolSize);
                    localPoolManager.registerPool(version, pool, pythonPath);
                } catch (IOException e) {
                    logger.error("Failed to initialize pool for Python {}: {}", version, e.getMessage());
                    // Continue with other versions
                }
            }

            if (localPoolManager.getPoolCount() == 0) {
                throw new IOException("No Python pools could be initialized");
            }

            // Publish in correct order: assign fields, then complete future, so any consumer
            // observing readinessFuture.isDone() also sees a non-null poolManager/processPool.
            this.poolManager = localPoolManager;
            this.processPool = localPoolManager.getDefaultPool();

            // v3.1.0: pool/dist managers register with REST endpoints once available
            Python3RestEndpoints.setProcessPool(processPool);
            Python3RestEndpoints.setPoolManager(poolManager);
            Python3RestEndpoints.setDistributionManager(distributionManager);

            logger.info("Python pools initialized: {} version(s) available: {}",
                poolManager.getPoolCount(), poolManager.getAvailableVersions());

            // The package manager is fast to construct; the Jedi auto-install is the slow
            // part (up to 120 s) and is where the user-visible startup delay comes from.

            String defaultPythonPath = poolManager.getPythonPath(defaultPythonVersion);
            try {
                // Package installs/uninstalls target EVERY installed Python distribution,
                // not just the default. The supplier is read live at each
                // install/uninstall call (distributions can be added/removed at runtime);
                // the default distribution above remains the primary/fallback and is the
                // sole determinant of overall success (see Python3PackageManager javadoc).
                packageManager = new Python3PackageManager(
                        gatewayContext.getSystemManager().getDataDir().toPath().resolve("python3-integration"),
                        defaultPythonPath,
                        () -> distributionManager.getInstalledVersions().stream()
                                .map(distributionManager::getVersionExecutablePath)
                                .filter(path -> path != null)
                                .collect(Collectors.toList())
                );
                logger.info("Package manager initialized");
                Python3RestEndpoints.setPackageManager(packageManager);

                // Auto-install Jedi for IDE autocomplete on a *separate* daemon thread so
                // that pool readiness is reported as soon as the pool is up — operators
                // get scripting functionality even before Jedi finishes.
                final Python3PackageManager pkgMgr = packageManager;
                Thread jediThread = new Thread(() -> {
                    try {
                        if (!pkgMgr.isInstalled("jedi")) {
                            logger.info("Jedi not installed — installing in background for IDE autocomplete...");
                            try {
                                Python3PackageManager.InstallResult result = pkgMgr.installPackage("jedi");
                                if (result.success) {
                                    logger.info("Jedi installed successfully — autocomplete will be available");
                                } else {
                                    logger.warn("Failed to auto-install Jedi: {}", result.message);
                                    logger.warn("IDE autocomplete may not work. Install jedi manually or download wheels.");
                                }
                            } catch (Exception e) {
                                logger.error("Failed to auto-install Jedi", e);
                            }
                        } else {
                            logger.info("Jedi already installed — autocomplete ready");
                        }
                    } catch (Throwable t) {
                        logger.error("Unexpected error in Jedi install thread", t);
                    }
                }, "Python3-JediInstall");
                jediThread.setDaemon(true);
                jediThread.start();

            } catch (Exception e) {
                logger.error("Failed to initialize package manager", e);
                // Pool is still usable without package manager — don't fail readinessFuture.
            }

            long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
            logger.info("Python 3 Integration module deferred init complete in {} ms", elapsedMs);
            readinessFuture.complete(null);

        } catch (Exception e) {
            logger.error("Failed to initialize Python 3 process pool", e);
            logger.error("Options:");
            logger.error("  1. Install Python 3.8+ on this server");
            logger.error("  2. Enable auto-download: -Dignition.python3.autodownload=true");
            logger.error("  3. Specify Python path: -Dignition.python3.path=/path/to/python3");
            logger.error("  4. Configure versions: -Dignition.python3.versions=3.10,3.11,3.12");
            readinessFuture.completeExceptionally(e);
        }
    }

    /**
     * Block the calling thread until the deferred pool initialisation has finished.
     * Used by tests; production code should consult {@link #getReadinessFuture()}.
     *
     * @param timeout maximum wait
     * @param unit time unit
     * @return {@code true} if init completed (successfully or exceptionally) within the timeout
     * @throws InterruptedException if the wait is interrupted
     */
    boolean awaitReadiness(long timeout, TimeUnit unit) throws InterruptedException {
        try {
            readinessFuture.get(timeout, unit);
            return true;
        } catch (java.util.concurrent.ExecutionException e) {
            // Init failed — still "complete", caller should check getReadinessFuture()
            return true;
        } catch (java.util.concurrent.TimeoutException e) {
            return false;
        }
    }

    /**
     * @return future that completes when async init has finished (normal completion = pool
     *         is ready; exceptional completion = init failed and module is degraded).
     *         Never {@code null}.
     */
    public CompletableFuture<Void> getReadinessFuture() {
        return readinessFuture;
    }

    @Override
    public void shutdown() {
        logger.info("Python 3 Integration module shutdown");

        // v3.13.0: If async init is still running, interrupt it and complete
        // readinessFuture exceptionally so any waiter wakes up immediately.
        Thread initThread = initThreadRef.get();
        if (initThread != null && initThread.isAlive()) {
            logger.info("Interrupting in-flight Python3-DeferredInit thread");
            initThread.interrupt();
            try {
                initThread.join(2000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
        if (!readinessFuture.isDone()) {
            readinessFuture.completeExceptionally(new IllegalStateException("Module shutdown before init completed"));
        }

        // v2.5.8: Close all interactive shell sessions
        try {
            Python3InteractiveShell.closeAllSessions();
        } catch (Exception e) {
            logger.error("Error closing interactive shell sessions", e);
        }

        // v4.5.0: stop the script-repository filesystem watcher thread
        if (scriptRepository != null) {
            try {
                scriptRepository.close();
            } catch (Exception e) {
                logger.error("Error stopping script repository watcher", e);
            }
        }

        // Shutdown all process pools via PoolManager (v3.1.0)
        if (poolManager != null) {
            try {
                poolManager.shutdown();
            } catch (Exception e) {
                logger.error("Error shutting down pool manager", e);
            }
        } else if (processPool != null) {
            // Fallback: single pool mode
            try {
                processPool.shutdown();
            } catch (Exception e) {
                logger.error("Error shutting down process pool", e);
            }
        }

        // Shutdown audit logger (v2.6.0 - legacy)
        if (auditLogger != null) {
            try {
                auditLogger.shutdown();
            } catch (Exception e) {
                logger.error("Error shutting down audit logger", e);
            }
        }

        // Shutdown static timeout executor (v2.15.9 - memory leak fix)
        try {
            Python3Executor.shutdownTimeoutExecutor();
        } catch (Exception e) {
            logger.error("Error shutting down timeout executor", e);
        }

        logger.info("Python 3 Integration module shutdown complete");
    }

    private volatile boolean scriptManagerInitialized = false;

    @Override
    public void initializeScriptManager(ScriptManager manager) {
        super.initializeScriptManager(manager);

        // Ignition calls this once per scripting scope (gateway + each project).
        // Only create and register objects on the first call to avoid resource leaks.
        if (scriptManagerInitialized) {
            logger.debug("Script manager already initialized, registering for additional scope");
            if (scriptModule != null) {
                manager.addScriptModule(
                        "system.python3",
                        scriptModule,
                        new PropertiesFileDocProvider()
                );
            }
            return;
        }
        scriptManagerInitialized = true;

        logger.info("Registering Python 3 scripting functions");

        // Create script module with lazy access to process pool
        // The module will become available once startup() initializes the pool
        scriptModule = new Python3ScriptModule(this);

        // Register under system.python3
        manager.addScriptModule(
                "system.python3",
                scriptModule,
                new PropertiesFileDocProvider()
        );

        // Initialize REST API endpoints with script module
        Python3RestEndpoints.initialize(scriptModule);
        if (scriptRepository != null) {
            Python3RestEndpoints.setScriptRepository(scriptRepository);
        }
        if (packageManager != null) {
            Python3RestEndpoints.setPackageManager(packageManager);
        }
        logger.info("REST API endpoints initialized");

        // Designer -> Gateway communication uses module RPC (registered via
        // getRpcImplementation() below), which travels over the Designer's already
        // authenticated Gateway channel. This replaced the Designer's cold-HTTP REST
        // client in v4.2.0: after security hardening the REST client could no longer
        // authenticate (no session cookie/token), so the Project Browser showed
        // "(Gateway unavailable)". The REST API remains for the browser Web UI and
        // external callers.

        logger.info("Python 3 scripting functions registered (pool will initialize during startup)");
    }

    @Override
    public void mountRouteHandlers(RouteGroup routes) {
        // Configure REST endpoints with required services (v2.6.0)
        Python3RestEndpoints.setSecurityService(securityService);
        Python3RestEndpoints.setAuditLogger(auditLogger);

        // v3.13.0: pool / pool-manager / distribution-manager / package-manager
        // are wired by #runDeferredInit once the daemon thread completes. We still set the
        // distribution manager here defensively (it is created synchronously in #setup), but
        // pool-dependent setters are deferred until readinessFuture completes.
        Python3RestEndpoints.setDistributionManager(distributionManager);
        if (processPool != null) {
            Python3RestEndpoints.setProcessPool(processPool);
        }
        if (poolManager != null) {
            Python3RestEndpoints.setPoolManager(poolManager);
        }
        if (packageManager != null) {
            Python3RestEndpoints.setPackageManager(packageManager);
        }

        // v3.5.2: Set logs directory for gateway log reading
        try {
            Python3RestEndpoints.setLogsDir(gatewayContext.getSystemManager().getLogsDir());
        } catch (Exception e) {
            logger.warn("Failed to set logs directory (non-fatal)", e);
        }

        // Mount REST API endpoints at /data/python3integration/api/v1/* (Ignition 8.3 OpenAPI compliant)
        Python3RestEndpoints.mountRoutes(routes);
        logger.info("Python3 REST API routes mounted at /data/python3integration/api/v1/ "
            + "(pool readiness: {})", readinessFuture.isDone() ? "READY" : "PENDING");
    }

    @Override
    public Optional<String> getMountPathAlias() {
        // Use shorter alias for resources: /res/python3integration/ instead of full module ID
        return Optional.of("python3integration");
    }

    @Override
    public Optional<String> getMountedResourceFolder() {
        // Serve web UI resources from classpath at /res/python3integration/
        // Contains Python3IDE.js (webpack UMD bundle) and standalone.html
        return Optional.of("mounted");
    }

    /**
     * Register the module-RPC implementation used by the Designer (v4.2.0).
     *
     * <p>The handler resolves services lazily from this hook on each call, so it is
     * safe to register here even though the process pool finishes initialising on a
     * background thread after startup. RPC calls arrive on the Designer's already
     * authenticated Gateway channel, giving the Gateway the real caller identity
     * without the (now-removed) REST session-token grant.</p>
     */
    @Override
    public Optional<GatewayRpcImplementation> getRpcImplementation() {
        return Optional.of(GatewayRpcImplementation.of(
            ProtoRpcSerializer.DEFAULT_INSTANCE,
            new Python3RpcHandler(this)));
    }

    /**
     * Load configuration from system properties or environment variables
     */
    private void loadConfiguration() {
        // Load pool size configuration
        String configuredSize = System.getProperty("ignition.python3.poolsize");
        if (configuredSize != null) {
            try {
                poolSize = Integer.parseInt(configuredSize);
                logger.info("Using configured pool size: {}", poolSize);
            } catch (NumberFormatException e) {
                logger.warn("Invalid pool size configuration: {}, using default: {}", configuredSize, poolSize);
            }
        }

        // Load auto-download configuration
        String configuredAutoDownload = System.getProperty("ignition.python3.autodownload");
        if (configuredAutoDownload != null) {
            autoDownload = Boolean.parseBoolean(configuredAutoDownload);
            logger.info("Auto-download: {}", autoDownload);
        }

        // Load default version (v3.1.0)
        defaultPythonVersion = System.getProperty("ignition.python3.default");
        if (defaultPythonVersion != null) {
            logger.info("Configured default Python version: {}", defaultPythonVersion);
        }
    }

    /**
     * Load configured Python versions from system properties.
     *
     * Reads:
     *   -Dignition.python3.versions=3.10,3.11,3.12
     *   -Dignition.python3.path.3.10=/usr/bin/python3.10
     *   -Dignition.python3.path.3.11=/usr/bin/python3.11
     *   -Dignition.python3.path.3.12=/opt/python3.12/bin/python3
     *
     * @return ordered map of version -> python path (empty if not configured)
     */
    private Map<String, String> loadConfiguredVersions() {
        Map<String, String> versions = new LinkedHashMap<>();

        String versionList = System.getProperty("ignition.python3.versions");
        if (versionList == null || versionList.trim().isEmpty()) {
            logger.debug("No multi-version configuration found (ignition.python3.versions not set)");
            return versions;
        }

        logger.info("Multi-version configuration detected: {}", versionList);

        for (String version : versionList.split(",")) {
            String trimmed = version.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            String path = System.getProperty("ignition.python3.path." + trimmed);
            if (path != null && !path.isEmpty()) {
                versions.put(trimmed, path);
                logger.info("  Python {}: {}", trimmed, path);
            } else {
                logger.warn("  Python {}: no path configured (ignition.python3.path.{} not set), skipping",
                    trimmed, trimmed);
            }
        }

        return versions;
    }

    /**
     * Detect the Python version from a Python executable.
     *
     * @param pythonPath path to the Python executable
     * @return version string like "3.11" (major.minor only)
     */
    private String detectPythonVersion(String pythonPath) {
        try {
            ProcessBuilder pb = new ProcessBuilder(pythonPath, "-c",
                "import sys; print(f'{sys.version_info.major}.{sys.version_info.minor}')");
            pb.redirectErrorStream(true);
            Process process = pb.start();

            String versionLine;
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(process.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                versionLine = reader.readLine();
            }

            boolean exited = process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            if (exited && process.exitValue() == 0 && versionLine != null) {
                String version = versionLine.trim();
                logger.info("Detected Python version: {}", version);
                return version;
            }
        } catch (Exception e) {
            logger.warn("Failed to detect Python version from {}: {}", pythonPath, e.getMessage());
        }
        return "3.11"; // Safe default
    }

    /**
     * Get the default process pool (backward compatibility).
     */
    public Python3ProcessPool getProcessPool() {
        return processPool;
    }

    /**
     * Get the pool manager for multi-version access (v3.1.0).
     */
    public PoolManager getPoolManager() {
        return poolManager;
    }

    /**
     * Get the distribution manager (for testing/debugging)
     */
    public PythonDistributionManager getDistributionManager() {
        return distributionManager;
    }

    /**
     * Get the script repository (for script management)
     */
    public Python3ScriptRepository getScriptRepository() {
        return scriptRepository;
    }

    /**
     * Get the script module (execution + monitoring). Used by {@link Python3RpcHandler}
     * to serve authenticated Designer calls over module RPC (v4.2.0).
     */
    public Python3ScriptModule getScriptModule() {
        return scriptModule;
    }

    /**
     * Get the audit logger (v2.6.0)
     */
    public Python3AuditLogger getAuditLogger() {
        return auditLogger;
    }

    /**
     * Get the package manager. Used by {@link Python3RpcHandler} to serve the
     * Designer's read-only package catalog view over module RPC (v4.3.0).
     */
    public Python3PackageManager getPackageManager() {
        return packageManager;
    }

    /**
     * Get the Gateway logs directory, for RPC-based log reading by
     * {@link Python3RpcHandler#getModuleLogs(int)}. Mirrors the defensive handling
     * already used for the REST path in {@link #mountRouteHandlers}: failures are
     * logged and {@code null} is returned rather than propagated, since log reading
     * is a non-critical diagnostic feature.
     *
     * @since v4.3.0
     */
    public java.io.File getLogsDir() {
        try {
            return gatewayContext.getSystemManager().getLogsDir();
        } catch (Exception e) {
            logger.warn("Failed to get logs directory (non-fatal)", e);
            return null;
        }
    }

    /**
     * Get the security service (v2.6.0)
     */
    public Python3SecurityService getSecurityService() {
        return securityService;
    }

    /**
     * Check if Python 3 is available
     */
    public boolean isPython3Available() {
        return processPool != null && !processPool.isShutdown();
    }

    /**
     * This module is free - no license required.
     * Overrides the default behavior to ensure the module shows as "Free" not "Trial".
     *
     * @return true indicating this is a free module
     */
    @Override
    public boolean isFreeModule() {
        return true;
    }

    /**
     * Opt this module in to Ignition Maker Edition.
     *
     * <p>{@code AbstractGatewayModuleHook} defaults this to {@code false}, which means
     * Maker silently refuses to start the module and reports it as "not eligible for
     * use with Ignition Maker Edition" — with no fault and no other log line. Available
     * since the 8.0.14 SDK.
     *
     * <p>Note: that same message is also shown when a module fails to load for an
     * unrelated reason (e.g. an SDK/platform version mismatch) — the real failure
     * reason gets overwritten, so the message alone can't tell you which case you're
     * in. See
     * https://forum.inductiveautomation.com/t/not-eligible-for-use-with-ignition-maker-edition/113129
     *
     * @since v4.6.0
     */
    @Override
    public boolean isMakerEditionCompatible() {
        return true;
    }
}
