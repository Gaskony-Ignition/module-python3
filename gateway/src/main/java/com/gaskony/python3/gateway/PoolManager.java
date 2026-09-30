package com.gaskony.python3.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages multiple Python process pools, one per Python version.
 * Handles version routing, fallback to default, and pool lifecycle.
 *
 * Configuration via system properties:
 *   -Dignition.python3.versions=3.10,3.11,3.12
 *   -Dignition.python3.path.3.10=/usr/bin/python3.10
 *   -Dignition.python3.path.3.11=/usr/bin/python3.11
 *   -Dignition.python3.path.3.12=/opt/python3.12/bin/python3
 *   -Dignition.python3.default=3.11
 *
 * @since v3.1.0
 */
public class PoolManager {

    private static final Logger logger = LoggerFactory.getLogger(PoolManager.class);

    private final Map<String, Python3ProcessPool> pools = new ConcurrentHashMap<>();
    private final Map<String, String> versionPaths = new ConcurrentHashMap<>();
    private final String defaultVersion;
    private volatile boolean isShutdown = false;

    /**
     * Create a PoolManager with a specified default version.
     *
     * @param defaultVersion the default Python version label (e.g., "3.11")
     */
    public PoolManager(String defaultVersion) {
        this.defaultVersion = defaultVersion;
        logger.info("PoolManager initialized with default version: {}", defaultVersion);
    }

    /**
     * Register a pool for a specific Python version.
     *
     * @param version the version label (e.g., "3.11")
     * @param pool    the process pool for that version
     * @param pythonPath the Python executable path for this version
     */
    public void registerPool(String version, Python3ProcessPool pool, String pythonPath) {
        if (pool == null) {
            throw new IllegalArgumentException("Pool cannot be null");
        }
        pools.put(version, pool);
        if (pythonPath != null) {
            versionPaths.put(version, pythonPath);
        }
        logger.info("Registered pool for Python {}: {}", version, pythonPath);
    }

    /**
     * Get pool for a specific version.
     * Falls back to default version if requested version is not available.
     *
     * @param version the desired Python version (null uses default)
     * @return the process pool
     * @throws VersionNotAvailableException if no pool is available
     */
    public Python3ProcessPool getPool(String version) throws VersionNotAvailableException {
        if (isShutdown) {
            throw new VersionNotAvailableException("PoolManager is shutdown");
        }

        String requestedVersion = (version == null || version.isEmpty()) ? defaultVersion : version;
        Python3ProcessPool pool = pools.get(requestedVersion);

        if (pool != null) {
            return pool;
        }

        // Fall back to default
        if (!requestedVersion.equals(defaultVersion)) {
            logger.warn("Python {} not available, falling back to default: {}", requestedVersion, defaultVersion);
            pool = pools.get(defaultVersion);
        }

        if (pool == null) {
            throw new VersionNotAvailableException(
                "No Python pool available for version '" + requestedVersion + "'. " +
                "Available versions: " + getAvailableVersions()
            );
        }

        return pool;
    }

    /**
     * Get the default pool (backward compatibility).
     *
     * @return the default version's process pool, or null if not available
     */
    public Python3ProcessPool getDefaultPool() {
        try {
            return getPool(defaultVersion);
        } catch (VersionNotAvailableException e) {
            logger.error("Default pool not available: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Get sorted list of available Python version labels.
     *
     * @return list of version strings
     */
    public List<String> getAvailableVersions() {
        List<String> versions = new ArrayList<>(pools.keySet());
        Collections.sort(versions);
        return versions;
    }

    /**
     * Get the default Python version label.
     *
     * @return default version string
     */
    public String getDefaultVersion() {
        return defaultVersion;
    }

    /**
     * Get the Python executable path for a given version.
     *
     * @param version the version label
     * @return the path, or null if not registered
     */
    public String getPythonPath(String version) {
        return versionPaths.get(version);
    }

    /**
     * Check if a specific version is available.
     *
     * @param version the version label
     * @return true if a pool exists for that version
     */
    public boolean isVersionAvailable(String version) {
        return pools.containsKey(version);
    }

    /**
     * Get the number of registered pools.
     *
     * @return number of pools
     */
    public int getPoolCount() {
        return pools.size();
    }

    /**
     * Shutdown all pools.
     */
    public void shutdown() {
        logger.info("Shutting down all Python process pools ({} versions)", pools.size());
        isShutdown = true;

        for (Map.Entry<String, Python3ProcessPool> entry : pools.entrySet()) {
            try {
                entry.getValue().shutdown();
                logger.info("Shut down pool for Python {}", entry.getKey());
            } catch (Exception e) {
                logger.error("Error shutting down pool for Python {}", entry.getKey(), e);
            }
        }

        pools.clear();
        versionPaths.clear();
        logger.info("All process pools shut down");
    }

    /**
     * Check if the manager has been shut down.
     *
     * @return true if shutdown
     */
    public boolean isShutdown() {
        return isShutdown;
    }

    /**
     * Exception thrown when a requested Python version is not available.
     */
    public static class VersionNotAvailableException extends Exception {
        public VersionNotAvailableException(String message) {
            super(message);
        }
    }
}
