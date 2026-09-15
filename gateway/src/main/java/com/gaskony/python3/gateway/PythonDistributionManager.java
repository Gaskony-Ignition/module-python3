package com.gaskony.python3.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

/**
 * Manages embedded Python distributions.
 * Supports downloading, installing, and uninstalling multiple Python versions.
 *
 * <p>Each Python version is installed into its own subdirectory under the distributions
 * directory (e.g., {@code python3-integration/distributions/3.11/}).</p>
 *
 * <p>Available versions for download are sourced from the
 * <a href="https://github.com/indygreg/python-build-standalone">python-build-standalone</a> project.</p>
 *
 * @since v3.1.0 - Multi-version support
 */
public class PythonDistributionManager {

    private static final Logger logger = LoggerFactory.getLogger(PythonDistributionManager.class);

    /**
     * Describes a downloadable Python version with URLs per platform.
     */
    public static class PythonDistribution {
        public final String version;         // e.g., "3.11"
        public final String fullVersion;     // e.g., "3.11.6"
        public final String releaseTag;      // e.g., "20231002"
        public final Map<String, String> platformUrls;  // os -> download URL

        public PythonDistribution(String version, String fullVersion, String releaseTag, Map<String, String> urls) {
            this.version = version;
            this.fullVersion = fullVersion;
            this.releaseTag = releaseTag;
            this.platformUrls = Collections.unmodifiableMap(urls);
        }
    }

    /**
     * Status information for an installed (or available) Python version.
     */
    public static class VersionStatus {
        public final String version;
        public final String fullVersion;
        public final boolean installed;
        public final boolean available;   // available for download
        public final String pythonPath;   // executable path if installed
        public final long installSizeBytes;

        public VersionStatus(String version, String fullVersion, boolean installed,
                             boolean available, String pythonPath, long installSizeBytes) {
            this.version = version;
            this.fullVersion = fullVersion;
            this.installed = installed;
            this.available = available;
            this.pythonPath = pythonPath;
            this.installSizeBytes = installSizeBytes;
        }
    }

    // Available Python distributions (from python-build-standalone)
    static final Map<String, PythonDistribution> AVAILABLE_DISTRIBUTIONS = new LinkedHashMap<>();  // package-private for pin-coverage test
    static {
        // Python 3.9
        Map<String, String> py39 = new HashMap<>();
        py39.put("windows", "https://github.com/indygreg/python-build-standalone/releases/download/20231002/cpython-3.9.18+20231002-x86_64-pc-windows-msvc-shared-install_only.tar.gz");
        py39.put("linux", "https://github.com/indygreg/python-build-standalone/releases/download/20231002/cpython-3.9.18+20231002-x86_64-unknown-linux-gnu-install_only.tar.gz");
        py39.put("macos-x64", "https://github.com/indygreg/python-build-standalone/releases/download/20231002/cpython-3.9.18+20231002-x86_64-apple-darwin-install_only.tar.gz");
        py39.put("macos-arm64", "https://github.com/indygreg/python-build-standalone/releases/download/20231002/cpython-3.9.18+20231002-aarch64-apple-darwin-install_only.tar.gz");
        AVAILABLE_DISTRIBUTIONS.put("3.9", new PythonDistribution("3.9", "3.9.18", "20231002", py39));

        // Python 3.10
        Map<String, String> py310 = new HashMap<>();
        py310.put("windows", "https://github.com/indygreg/python-build-standalone/releases/download/20231002/cpython-3.10.13+20231002-x86_64-pc-windows-msvc-shared-install_only.tar.gz");
        py310.put("linux", "https://github.com/indygreg/python-build-standalone/releases/download/20231002/cpython-3.10.13+20231002-x86_64-unknown-linux-gnu-install_only.tar.gz");
        py310.put("macos-x64", "https://github.com/indygreg/python-build-standalone/releases/download/20231002/cpython-3.10.13+20231002-x86_64-apple-darwin-install_only.tar.gz");
        py310.put("macos-arm64", "https://github.com/indygreg/python-build-standalone/releases/download/20231002/cpython-3.10.13+20231002-aarch64-apple-darwin-install_only.tar.gz");
        AVAILABLE_DISTRIBUTIONS.put("3.10", new PythonDistribution("3.10", "3.10.13", "20231002", py310));

        // Python 3.11
        Map<String, String> py311 = new HashMap<>();
        py311.put("windows", "https://github.com/indygreg/python-build-standalone/releases/download/20231002/cpython-3.11.6+20231002-x86_64-pc-windows-msvc-shared-install_only.tar.gz");
        py311.put("linux", "https://github.com/indygreg/python-build-standalone/releases/download/20231002/cpython-3.11.6+20231002-x86_64-unknown-linux-gnu-install_only.tar.gz");
        py311.put("macos-x64", "https://github.com/indygreg/python-build-standalone/releases/download/20231002/cpython-3.11.6+20231002-x86_64-apple-darwin-install_only.tar.gz");
        py311.put("macos-arm64", "https://github.com/indygreg/python-build-standalone/releases/download/20231002/cpython-3.11.6+20231002-aarch64-apple-darwin-install_only.tar.gz");
        AVAILABLE_DISTRIBUTIONS.put("3.11", new PythonDistribution("3.11", "3.11.6", "20231002", py311));

        // Python 3.12
        Map<String, String> py312 = new HashMap<>();
        py312.put("windows", "https://github.com/indygreg/python-build-standalone/releases/download/20240107/cpython-3.12.1+20240107-x86_64-pc-windows-msvc-shared-install_only.tar.gz");
        py312.put("linux", "https://github.com/indygreg/python-build-standalone/releases/download/20240107/cpython-3.12.1+20240107-x86_64-unknown-linux-gnu-install_only.tar.gz");
        py312.put("macos-x64", "https://github.com/indygreg/python-build-standalone/releases/download/20240107/cpython-3.12.1+20240107-x86_64-apple-darwin-install_only.tar.gz");
        py312.put("macos-arm64", "https://github.com/indygreg/python-build-standalone/releases/download/20240107/cpython-3.12.1+20240107-aarch64-apple-darwin-install_only.tar.gz");
        AVAILABLE_DISTRIBUTIONS.put("3.12", new PythonDistribution("3.12", "3.12.1", "20240107", py312));

        // Python 3.13
        Map<String, String> py313 = new HashMap<>();
        py313.put("windows", "https://github.com/indygreg/python-build-standalone/releases/download/20241016/cpython-3.13.0+20241016-x86_64-pc-windows-msvc-shared-install_only.tar.gz");
        py313.put("linux", "https://github.com/indygreg/python-build-standalone/releases/download/20241016/cpython-3.13.0+20241016-x86_64-unknown-linux-gnu-install_only.tar.gz");
        py313.put("macos-x64", "https://github.com/indygreg/python-build-standalone/releases/download/20241016/cpython-3.13.0+20241016-x86_64-apple-darwin-install_only.tar.gz");
        py313.put("macos-arm64", "https://github.com/indygreg/python-build-standalone/releases/download/20241016/cpython-3.13.0+20241016-aarch64-apple-darwin-install_only.tar.gz");
        AVAILABLE_DISTRIBUTIONS.put("3.13", new PythonDistribution("3.13", "3.13.0", "20241016", py313));
    }

    // Legacy single-version URLs (backward compatibility)
    static final Map<String, String> DISTRIBUTION_URLS = new HashMap<>();  // package-private for pin-coverage test
    static {
        DISTRIBUTION_URLS.put("windows",
                "https://github.com/indygreg/python-build-standalone/releases/download/20231002/cpython-3.11.6+20231002-x86_64-pc-windows-msvc-shared-install_only.tar.gz");
        DISTRIBUTION_URLS.put("linux",
                "https://github.com/indygreg/python-build-standalone/releases/download/20231002/cpython-3.11.6+20231002-x86_64-unknown-linux-gnu-install_only.tar.gz");
        DISTRIBUTION_URLS.put("macos-x64",
                "https://github.com/indygreg/python-build-standalone/releases/download/20231002/cpython-3.11.6+20231002-x86_64-apple-darwin-install_only.tar.gz");
        DISTRIBUTION_URLS.put("macos-arm64",
                "https://github.com/indygreg/python-build-standalone/releases/download/20231002/cpython-3.11.6+20231002-aarch64-apple-darwin-install_only.tar.gz");
    }

    private final Path moduleDataDir;
    private final Path pythonDir;           // Legacy single-version dir
    private final Path distributionsDir;    // Multi-version distributions dir
    private String pythonExecutable;
    private final boolean autoDownload;

    // Track installed versions
    private final Map<String, String> installedVersionPaths = new ConcurrentHashMap<>();

    // Installation progress callback
    private volatile InstallProgressListener progressListener;

    /**
     * Listener for installation progress updates.
     */
    public interface InstallProgressListener {
        void onProgress(String version, String stage, int percentComplete, String message);
        void onComplete(String version, boolean success, String message);
    }

    /**
     * Create a Python distribution manager
     *
     * @param moduleDataDir Directory for module data
     * @param autoDownload  Whether to auto-download Python if not found
     */
    public PythonDistributionManager(Path moduleDataDir, boolean autoDownload) {
        this.moduleDataDir = moduleDataDir;
        this.pythonDir = moduleDataDir.resolve("python");
        this.distributionsDir = moduleDataDir.resolve("distributions");
        this.autoDownload = autoDownload;

        try {
            Files.createDirectories(moduleDataDir);
            Files.createDirectories(pythonDir);
            Files.createDirectories(distributionsDir);
        } catch (IOException e) {
            logger.error("Failed to create module directories", e);
        }

        // Scan for already-installed distributions
        scanInstalledDistributions();
    }

    /**
     * Set a progress listener for installation updates.
     */
    public void setProgressListener(InstallProgressListener listener) {
        this.progressListener = listener;
    }

    // ========================================================================
    // Multi-version API (v3.1.0)
    // ========================================================================

    /**
     * Get all available Python versions with their install status.
     *
     * @return list of version status objects
     */
    public List<VersionStatus> getAllVersionStatuses() {
        List<VersionStatus> statuses = new ArrayList<>();
        String os = detectOS();

        for (Map.Entry<String, PythonDistribution> entry : AVAILABLE_DISTRIBUTIONS.entrySet()) {
            String version = entry.getKey();
            PythonDistribution dist = entry.getValue();

            boolean available = dist.platformUrls.containsKey(os);
            boolean installed = isVersionInstalled(version);
            String execPath = installed ? getVersionExecutablePath(version) : null;
            long size = installed ? getInstalledSize(version) : 0;

            statuses.add(new VersionStatus(version, dist.fullVersion,
                    installed, available, execPath, size));
        }

        return statuses;
    }

    /**
     * Get the list of available Python versions (that can be downloaded).
     *
     * @return sorted list of version strings
     */
    public List<String> getAvailableDownloadVersions() {
        String os = detectOS();
        List<String> versions = new ArrayList<>();

        for (Map.Entry<String, PythonDistribution> entry : AVAILABLE_DISTRIBUTIONS.entrySet()) {
            if (entry.getValue().platformUrls.containsKey(os)) {
                versions.add(entry.getKey());
            }
        }

        Collections.sort(versions);
        return versions;
    }

    /**
     * Get the list of currently installed Python versions.
     *
     * @return sorted list of installed version strings
     */
    public List<String> getInstalledVersions() {
        List<String> versions = new ArrayList<>(installedVersionPaths.keySet());
        Collections.sort(versions);
        return versions;
    }

    /**
     * Check if a specific version is installed.
     *
     * @param version the Python version (e.g., "3.11")
     * @return true if installed
     */
    public boolean isVersionInstalled(String version) {
        return installedVersionPaths.containsKey(version);
    }

    /**
     * Get the Python executable path for a specific installed version.
     *
     * @param version the Python version (e.g., "3.11")
     * @return path to executable, or null if not installed
     */
    public String getVersionExecutablePath(String version) {
        return installedVersionPaths.get(version);
    }

    /**
     * Install a specific Python version by downloading from python-build-standalone.
     *
     * @param version the Python version to install (e.g., "3.11")
     * @throws IOException if download or extraction fails
     * @throws IllegalArgumentException if version is not available
     */
    public void installVersion(String version) throws IOException {
        PythonDistribution dist = AVAILABLE_DISTRIBUTIONS.get(version);
        if (dist == null) {
            throw new IllegalArgumentException("Unknown Python version: " + version
                    + ". Available: " + AVAILABLE_DISTRIBUTIONS.keySet());
        }

        String os = detectOS();
        String url = dist.platformUrls.get(os);
        if (url == null) {
            throw new IOException("No Python " + version + " distribution available for OS: " + os);
        }

        if (isVersionInstalled(version)) {
            logger.info("Python {} is already installed", version);
            return;
        }

        Path versionDir = distributionsDir.resolve(version);
        Files.createDirectories(versionDir);

        logger.info("Installing Python {} ({}) for {}", version, dist.fullVersion, os);
        notifyProgress(version, "downloading", 0, "Starting download...");

        Path downloadPath = Files.createTempFile("python-" + version + "-", ".tar.gz");

        try {
            downloadFileWithProgress(url, downloadPath, version);
            notifyProgress(version, "verifying", 75, "Verifying integrity...");

            // C15: Verify pinned SHA-256 before extraction. Refuses by default if no
            // hash is configured (override via -Dignition.python3.skipChecksum=true).
            verifyDownloadedTarball(downloadPath, url);

            notifyProgress(version, "extracting", 80, "Extracting...");

            extractTarGz(downloadPath, versionDir);
            notifyProgress(version, "verifying", 95, "Verifying installation...");

            // Find and verify the executable
            String execPath = findVersionExecutable(version, versionDir);
            if (execPath == null) {
                deleteDirectory(versionDir);
                throw new IOException("Python " + version + " extraction succeeded but executable not found");
            }

            // Validate the installation
            if (!isPythonValid(execPath)) {
                deleteDirectory(versionDir);
                throw new IOException("Python " + version + " executable is not valid: " + execPath);
            }

            installedVersionPaths.put(version, execPath);
            logger.info("Python {} installed successfully at: {}", version, execPath);
            notifyProgress(version, "complete", 100, "Installation complete");
            notifyComplete(version, true, "Python " + version + " installed successfully");

        } catch (IOException e) {
            logger.error("Failed to install Python {}", version, e);
            notifyComplete(version, false, "Installation failed: " + e.getMessage());
            // Clean up partial installation
            try {
                deleteDirectory(versionDir);
            } catch (IOException cleanupErr) {
                logger.warn("Failed to clean up failed installation for {}", version);
            }
            throw e;
        } finally {
            try {
                Files.deleteIfExists(downloadPath);
            } catch (IOException e) {
                logger.warn("Failed to delete temp file: {}", downloadPath);
            }
        }
    }

    /**
     * Uninstall a specific Python version.
     *
     * @param version the Python version to uninstall
     * @throws IOException if deletion fails
     */
    public void uninstallVersion(String version) throws IOException {
        if (!isVersionInstalled(version)) {
            logger.info("Python {} is not installed, nothing to uninstall", version);
            return;
        }

        Path versionDir = distributionsDir.resolve(version);
        logger.info("Uninstalling Python {} from: {}", version, versionDir);

        deleteDirectory(versionDir);
        installedVersionPaths.remove(version);

        logger.info("Python {} uninstalled successfully", version);
    }

    /**
     * Get the full version string for a distribution (e.g., "3.11.6").
     *
     * @param version the short version (e.g., "3.11")
     * @return the full version string, or the short version if not found
     */
    public String getFullVersion(String version) {
        PythonDistribution dist = AVAILABLE_DISTRIBUTIONS.get(version);
        return dist != null ? dist.fullVersion : version;
    }

    /**
     * Get the installed size of a Python version in bytes.
     *
     * @param version the Python version
     * @return size in bytes, or 0 if not installed
     */
    public long getInstalledSize(String version) {
        Path versionDir = distributionsDir.resolve(version);
        if (!Files.exists(versionDir)) {
            return 0;
        }

        try {
            return Files.walk(versionDir)
                    .filter(Files::isRegularFile)
                    .mapToLong(p -> {
                        try {
                            return Files.size(p);
                        } catch (IOException e) {
                            return 0;
                        }
                    })
                    .sum();
        } catch (IOException e) {
            return 0;
        }
    }

    /**
     * Scan for already-installed distributions on startup.
     */
    private void scanInstalledDistributions() {
        if (!Files.exists(distributionsDir)) {
            return;
        }

        try {
            Files.list(distributionsDir)
                    .filter(Files::isDirectory)
                    .forEach(versionDir -> {
                        String version = versionDir.getFileName().toString();
                        String execPath = findVersionExecutable(version, versionDir);
                        if (execPath != null && isPythonValid(execPath)) {
                            installedVersionPaths.put(version, execPath);
                            logger.info("Found installed Python {}: {}", version, execPath);
                        }
                    });
        } catch (IOException e) {
            logger.error("Failed to scan installed distributions", e);
        }

        logger.info("Installed Python distributions: {}", installedVersionPaths.keySet());
    }

    /**
     * Find the Python executable for a specific version in its install directory.
     */
    private String findVersionExecutable(String version, Path versionDir) {
        String os = detectOS();

        // python-build-standalone extracts to a "python" subdirectory
        Path pythonSubDir = versionDir.resolve("python");
        if (!Files.exists(pythonSubDir)) {
            // Some versions may extract directly
            pythonSubDir = versionDir;
        }

        Path executable;
        if ("windows".equals(os)) {
            executable = pythonSubDir.resolve("python.exe");
        } else {
            // Try version-specific binary first (e.g., python3.11)
            PythonDistribution dist = AVAILABLE_DISTRIBUTIONS.get(version);
            if (dist != null) {
                executable = pythonSubDir.resolve("bin/python" + dist.fullVersion.substring(0, dist.fullVersion.lastIndexOf('.')));
                if (!Files.exists(executable)) {
                    executable = pythonSubDir.resolve("bin/python3." + version.split("\\.")[1]);
                }
            } else {
                executable = pythonSubDir.resolve("bin/python3");
            }

            if (!Files.exists(executable)) {
                executable = pythonSubDir.resolve("bin/python3");
            }
            if (!Files.exists(executable)) {
                executable = pythonSubDir.resolve("bin/python");
            }
        }

        if (Files.exists(executable)) {
            try {
                executable.toFile().setExecutable(true);
            } catch (Exception e) {
                // Ignore on Windows
            }
            return executable.toString();
        }

        return null;
    }

    // ========================================================================
    // Legacy single-version API (backward compatibility)
    // ========================================================================

    /**
     * Get Python executable path.
     * Priority for self-contained module:
     * 1. Virtual environment (if configured via system property)
     * 2. Embedded Python (if already installed)
     * 3. Download embedded Python (if autoDownload enabled)
     * 4. System Python (fallback if autoDownload disabled)
     *
     * @return Path to Python executable
     * @throws IOException if Python cannot be found or installed
     */
    public String getPythonPath() throws IOException {
        // Priority 1: Check for virtual environment via system property
        String venvPath = detectVirtualEnv();
        if (venvPath != null) {
            logger.info("Using virtual environment Python: {}", venvPath);
            return venvPath;
        }

        // Priority 2: Check installed distributions (multi-version)
        if (!installedVersionPaths.isEmpty()) {
            // Prefer 3.11 if available, otherwise use the latest installed
            String preferred = installedVersionPaths.get("3.11");
            if (preferred != null) {
                logger.info("Using installed distribution Python 3.11: {}", preferred);
                return preferred;
            }
            // Use highest version
            List<String> versions = new ArrayList<>(installedVersionPaths.keySet());
            Collections.sort(versions);
            String latest = versions.get(versions.size() - 1);
            String latestPath = installedVersionPaths.get(latest);
            logger.info("Using installed distribution Python {}: {}", latest, latestPath);
            return latestPath;
        }

        // Priority 3: Check if embedded Python already extracted (legacy path)
        if (isEmbeddedPythonInstalled()) {
            logger.info("Using embedded Python: {}", pythonExecutable);
            return pythonExecutable;
        }

        // Priority 4: Download if enabled (prioritize self-contained distribution)
        if (autoDownload) {
            logger.info("Embedded Python not found, downloading distribution...");
            downloadAndInstall();
            return pythonExecutable;
        }

        // Priority 5: Try system Python as fallback (only when autoDownload disabled)
        String systemPython = detectSystemPython();
        if (systemPython != null) {
            logger.info("Using system Python: {}", systemPython);
            return systemPython;
        }

        throw new IOException(
                "Python 3 not found. Please install Python 3.8+ or enable auto-download.\n"
                        + "Set system property: -Dignition.python3.autodownload=true"
        );
    }

    /**
     * Detect virtual environment via system property.
     * Supports two configuration methods:
     * 1. Direct venv path: -Dignition.python3.venv=/path/to/venv
     * 2. Direct Python path pointing to venv: -Dignition.python3.path=/path/to/venv/bin/python3
     *
     * @return Path to Python executable in venv, or null if not configured
     */
    private String detectVirtualEnv() {
        // Method 1: Explicit venv directory
        String venvDir = System.getProperty("ignition.python3.venv");
        if (venvDir != null && !venvDir.isEmpty()) {
            String os = detectOS();
            Path venvPath;

            if ("windows".equals(os)) {
                venvPath = Path.of(venvDir, "Scripts", "python.exe");
            } else {
                venvPath = Path.of(venvDir, "bin", "python3");
            }

            if (Files.exists(venvPath) && Files.isExecutable(venvPath)) {
                String pythonPath = venvPath.toString();
                if (isPythonValid(pythonPath)) {
                    logger.info("Virtual environment detected: {}", venvDir);
                    return pythonPath;
                } else {
                    logger.warn("Virtual environment Python is invalid: {}", pythonPath);
                }
            } else {
                logger.warn("Virtual environment not found at: {}", venvPath);
            }
        }

        // Method 2: Python path pointing to venv (check if it's in a venv)
        String pythonPath = System.getProperty("ignition.python3.path");
        if (pythonPath != null && !pythonPath.isEmpty()) {
            Path path = Path.of(pythonPath);

            // Check if this Python is inside a virtual environment
            // Typical venv structure: venv/bin/python3 or venv/Scripts/python.exe
            if (path.getParent() != null) {
                Path parentDir = path.getParent();
                String parentName = parentDir.getFileName().toString();

                if ("bin".equals(parentName) || "Scripts".equals(parentName)) {
                    Path possibleVenvRoot = parentDir.getParent();

                    // Verify venv markers exist
                    if (possibleVenvRoot != null) {
                        Path pyvenvCfg = possibleVenvRoot.resolve("pyvenv.cfg");
                        if (Files.exists(pyvenvCfg)) {
                            logger.info("Virtual environment detected via python3.path: {}", possibleVenvRoot);
                            if (isPythonValid(pythonPath)) {
                                return pythonPath;
                            }
                        }
                    }
                }
            }
        }

        return null;
    }

    /**
     * Check if embedded Python is already installed (legacy single-version)
     */
    private boolean isEmbeddedPythonInstalled() {
        String os = detectOS();
        Path executable;

        if ("windows".equals(os)) {
            executable = pythonDir.resolve("python/python.exe");
        } else {
            // Try python3.11 first (standalone builds have broken python3 symlink)
            executable = pythonDir.resolve("python/bin/python3.11");
            if (!Files.exists(executable) || !Files.isExecutable(executable)) {
                // Fallback to python3
                executable = pythonDir.resolve("python/bin/python3");
            }
        }

        if (Files.exists(executable) && Files.isExecutable(executable)) {
            pythonExecutable = executable.toString();
            return true;
        }

        return false;
    }

    /**
     * Detect system Python installation
     */
    private String detectSystemPython() {
        String os = detectOS();
        String[] candidates;

        if ("windows".equals(os)) {
            candidates = new String[]{
                    "python3",
                    "python",
                    "C:\\Python311\\python.exe",
                    "C:\\Python310\\python.exe",
                    "C:\\Python39\\python.exe"
            };
        } else if (os.startsWith("macos")) {
            candidates = new String[]{
                    "python3",
                    "/usr/local/bin/python3",
                    "/opt/homebrew/bin/python3",
                    "/usr/bin/python3"
            };
        } else {
            candidates = new String[]{
                    "python3",
                    "/usr/bin/python3",
                    "/usr/local/bin/python3"
            };
        }

        for (String candidate : candidates) {
            if (isPythonValid(candidate)) {
                return candidate;
            }
        }

        return null;
    }

    /**
     * Test if a Python path is valid and version >= 3.8
     */
    boolean isPythonValid(String pythonPath) {
        try {
            ProcessBuilder pb = new ProcessBuilder(pythonPath, "--version");
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream())
            );
            String versionLine = reader.readLine();

            boolean exited = process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);

            if (exited && process.exitValue() == 0 && versionLine != null) {
                // Parse version (e.g., "Python 3.11.6")
                if (versionLine.startsWith("Python 3.")) {
                    String[] parts = versionLine.split(" ")[1].split("\\.");
                    int minor = Integer.parseInt(parts[1]);
                    if (minor >= 8) {
                        logger.debug("Valid Python found: {} ({})", pythonPath, versionLine);
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            // Path not valid
        }

        return false;
    }

    /**
     * Download and install Python distribution (legacy single-version)
     */
    private void downloadAndInstall() throws IOException {
        String os = detectOS();
        String url = DISTRIBUTION_URLS.get(os);

        if (url == null) {
            throw new IOException("No Python distribution available for OS: " + os);
        }

        logger.info("Downloading Python distribution for {}", os);
        logger.info("URL: {}", url);

        // Download to temp file
        Path downloadPath = Files.createTempFile("python", ".tar.gz");

        try {
            downloadFile(url, downloadPath);
            logger.info("Download complete, verifying integrity...");

            // C15: Verify pinned SHA-256 before extraction.
            verifyDownloadedTarball(downloadPath, url);

            logger.info("Integrity verified, extracting...");

            // Extract
            extractTarGz(downloadPath, pythonDir);

            logger.info("Python distribution installed successfully");

            // Verify installation
            if (!isEmbeddedPythonInstalled()) {
                throw new IOException("Python extraction succeeded but executable not found");
            }

        } finally {
            // Clean up download
            try {
                Files.deleteIfExists(downloadPath);
            } catch (IOException e) {
                logger.warn("Failed to delete temp file: {}", downloadPath);
            }
        }
    }

    /**
     * Download a file with progress logging
     */
    private void downloadFile(String urlString, Path destination) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(30000);

        long fileSize = conn.getContentLengthLong();
        logger.info("Download size: {} MB", fileSize / 1024 / 1024);

        try (InputStream in = new BufferedInputStream(conn.getInputStream());
             OutputStream out = new BufferedOutputStream(Files.newOutputStream(destination))) {

            byte[] buffer = new byte[8192];
            long totalRead = 0;
            int bytesRead;
            long lastLog = System.currentTimeMillis();

            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                totalRead += bytesRead;

                // Log progress every 5 seconds
                long now = System.currentTimeMillis();
                if (now - lastLog > 5000) {
                    double progress = (totalRead * 100.0) / fileSize;
                    logger.info("Download progress: {}/{} MB ({:.1f}%)",
                            totalRead / 1024 / 1024,
                            fileSize / 1024 / 1024,
                            progress);
                    lastLog = now;
                }
            }

            logger.info("Download complete: {} MB", totalRead / 1024 / 1024);
        }
    }

    /**
     * Download a file with progress callbacks (for multi-version installs).
     */
    private void downloadFileWithProgress(String urlString, Path destination, String version) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(60000);
        conn.setInstanceFollowRedirects(true);

        // Handle redirects (GitHub releases use redirects)
        int responseCode = conn.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP
                || responseCode == HttpURLConnection.HTTP_MOVED_PERM
                || responseCode == 307 || responseCode == 308) {
            String redirectUrl = conn.getHeaderField("Location");
            conn.disconnect();
            conn = (HttpURLConnection) new URL(redirectUrl).openConnection();
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(60000);
        }

        long fileSize = conn.getContentLengthLong();
        logger.info("Download size for Python {}: {} MB", version,
                fileSize > 0 ? fileSize / 1024 / 1024 : "unknown");

        try (InputStream in = new BufferedInputStream(conn.getInputStream());
             OutputStream out = new BufferedOutputStream(Files.newOutputStream(destination))) {

            byte[] buffer = new byte[8192];
            long totalRead = 0;
            int bytesRead;
            long lastNotify = System.currentTimeMillis();

            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                totalRead += bytesRead;

                long now = System.currentTimeMillis();
                if (now - lastNotify > 2000) {
                    int percent = fileSize > 0 ? (int) ((totalRead * 70) / fileSize) : -1;
                    String msg = fileSize > 0
                            ? String.format("Downloaded %d/%d MB", totalRead / 1024 / 1024, fileSize / 1024 / 1024)
                            : String.format("Downloaded %d MB", totalRead / 1024 / 1024);
                    notifyProgress(version, "downloading", Math.max(0, percent), msg);
                    logger.debug("Download progress for Python {}: {}", version, msg);
                    lastNotify = now;
                }
            }

            logger.info("Download complete for Python {}: {} MB", version, totalRead / 1024 / 1024);
        } finally {
            conn.disconnect();
        }
    }

    // ========================================================================
    // Tar extraction security limits (C15 — Sprint 2 hardening)
    // ========================================================================
    //
    // python-build-standalone tarballs are typically 30–60 MB compressed,
    // 100–250 MB uncompressed. The 500 MB total / 100 MB per-file caps below
    // are loose enough for legitimate CPython distributions but tight enough
    // to refuse a "zip-bomb-style" malicious tarball intended to fill the
    // Gateway data dir.
    //
    // The path-traversal guard (`assertWithinDestDir`) and the symlink/hardlink
    // refusal collectively close the original CVE-style "tar slip" attack
    // documented in `mod-python3.md` SEV-1 #4.

    /**
     * Maximum total uncompressed size of any tarball this manager will accept.
     *
     * <p>Volatile to allow package-private overrides during unit testing of
     * the size-cap branches without having to construct a 500 MB+ fixture.
     * Production code never mutates this field; see C15 fix-report.
     */
    static volatile long MAX_UNCOMPRESSED_TOTAL_BYTES = 500L * 1024 * 1024; // 500 MB

    /**
     * Maximum size of any single file inside a tarball.
     *
     * <p>Volatile per {@link #MAX_UNCOMPRESSED_TOTAL_BYTES} — same rationale.
     */
    static volatile long MAX_PER_FILE_BYTES = 100L * 1024 * 1024; // 100 MB

    /** Maximum number of tar entries — refuses tarballs with absurd entry counts. */
    static volatile int MAX_ENTRY_COUNT = 100_000;

    /**
     * Extract a tar.gz archive into {@code destDir} with full security validation.
     *
     * <p>Hardening (Sprint 2 / C15):
     * <ol>
     *   <li><b>Tar-slip protection</b> — every entry's normalised path must remain inside
     *       {@code destDir}. Entries containing {@code ../}, absolute paths, or
     *       Windows-style drive letters are rejected.</li>
     *   <li><b>Contained links only</b> (v4.3.5) — symlink/hardlink entries are
     *       permitted only when the link target is relative and lexically resolves
     *       inside {@code destDir}; absolute or escaping targets are refused. CPython
     *       distributions require in-tree symlinks ({@code bin/python3 → python3.11}),
     *       so the previous blanket ban broke clean-gateway self-provisioning.</li>
     *   <li><b>Per-file size cap</b> — any entry whose declared size exceeds
     *       {@value #MAX_PER_FILE_BYTES} bytes is rejected before any bytes are written.</li>
     *   <li><b>Total size cap</b> — the running sum of uncompressed bytes is checked
     *       against {@value #MAX_UNCOMPRESSED_TOTAL_BYTES} after every write; if exceeded
     *       extraction is aborted (and partial files are left for the caller to clean up).</li>
     *   <li><b>Entry-count cap</b> — refuses tarballs declaring more than
     *       {@value #MAX_ENTRY_COUNT} entries.</li>
     * </ol>
     *
     * @throws IOException with a {@code "Tar slip"}, {@code "Symbolic link"},
     *         {@code "Hard link"}, {@code "exceeds per-file"}, {@code "exceeds total"},
     *         or {@code "too many entries"} prefix on failure
     */
    void extractTarGz(Path tarGzPath, Path destDir) throws IOException {
        logger.info("Extracting to: {}", destDir);

        // Resolve destDir once so we can compare normalised paths cheaply per entry.
        Path normalisedDestDir = destDir.toAbsolutePath().normalize();
        Files.createDirectories(normalisedDestDir);

        long runningTotal = 0L;
        int extractedEntries = 0;

        try (InputStream fileIn = Files.newInputStream(tarGzPath);
             GZIPInputStream gzIn = new GZIPInputStream(fileIn);
             TarArchiveInputStream tarIn = new TarArchiveInputStream(gzIn)) {

            TarArchiveEntry entry;

            while ((entry = tarIn.getNextTarEntry()) != null) {
                extractedEntries++;
                if (extractedEntries > MAX_ENTRY_COUNT) {
                    throw new IOException("Tarball declares too many entries (>" + MAX_ENTRY_COUNT
                        + "); refusing extraction");
                }

                String entryName = entry.getName();

                // (1) Tar-slip guard — normalise then verify containment.
                Path resolved = normalisedDestDir.resolve(entryName).normalize().toAbsolutePath();
                if (!resolved.startsWith(normalisedDestDir)) {
                    throw new IOException("Tar slip: entry escapes destination directory: " + entryName);
                }
                // Also reject entries whose name is absolute (e.g. "/etc/passwd")
                if (entry.getName().startsWith("/") || entry.getName().startsWith("\\")) {
                    throw new IOException("Tar slip: absolute entry name: " + entryName);
                }

                // (2) Links. Real CPython distributions REQUIRE symlinks on
                // linux/macos (bin/python3 -> python3.11, lib aliases, ...), so a
                // blanket ban made every distribution un-extractable and a clean
                // gateway could never self-provision (workflow-1 defect, v4.3.5).
                // Policy now matches Python's own tarfile "data" filter: a link is
                // permitted ONLY if its target is relative and lexically resolves
                // inside destDir. Every link is checked individually, so chains
                // cannot escape either — the escaping hop is itself refused.
                if (entry.isSymbolicLink() || entry.isLink()) {
                    String linkKind = entry.isSymbolicLink() ? "Symbolic" : "Hard";
                    String linkName = entry.getLinkName();
                    if (linkName == null || linkName.isEmpty()
                            || linkName.startsWith("/") || linkName.startsWith("\\")
                            || linkName.contains(":")) {
                        throw new IOException(linkKind + " link with absolute or invalid target: "
                            + entryName + " -> " + linkName);
                    }
                    Path linkParent = resolved.getParent() != null ? resolved.getParent() : normalisedDestDir;
                    // Tar semantics: symlink targets are relative to the entry's
                    // directory; hardlink targets are relative to the archive root.
                    Path linkTarget = (entry.isSymbolicLink() ? linkParent : normalisedDestDir)
                            .resolve(linkName).normalize().toAbsolutePath();
                    if (!linkTarget.startsWith(normalisedDestDir)) {
                        throw new IOException(linkKind + " link escapes destination directory: "
                            + entryName + " -> " + linkName);
                    }
                    Files.createDirectories(linkParent);
                    Files.deleteIfExists(resolved);
                    if (entry.isSymbolicLink()) {
                        try {
                            // Keep the relative target from the tar so the tree stays relocatable.
                            Files.createSymbolicLink(resolved, Paths.get(linkName));
                        } catch (UnsupportedOperationException | IOException e) {
                            // Filesystems without symlink support (e.g. Windows without
                            // the privilege): fall back to a copy of the target, which
                            // these tarballs order before the links that reference it.
                            if (Files.exists(linkTarget)) {
                                Files.copy(linkTarget, resolved, StandardCopyOption.REPLACE_EXISTING);
                            } else {
                                throw new IOException("Cannot create symbolic link "
                                    + entryName + " -> " + linkName, e);
                            }
                        }
                    } else {
                        if (!Files.exists(linkTarget)) {
                            throw new IOException("Hard link target missing: "
                                + entryName + " -> " + linkName);
                        }
                        try {
                            Files.createLink(resolved, linkTarget);
                        } catch (UnsupportedOperationException e) {
                            Files.copy(linkTarget, resolved, StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                    continue;
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(resolved);
                    continue;
                }

                // (3) Per-file size cap (declared size).
                long declaredSize = entry.getSize();
                if (declaredSize > MAX_PER_FILE_BYTES) {
                    throw new IOException("Entry exceeds per-file size cap (" + declaredSize
                        + " > " + MAX_PER_FILE_BYTES + " bytes): " + entryName);
                }

                // (4) Pre-check against total cap based on declared size.
                if (runningTotal + Math.max(0, declaredSize) > MAX_UNCOMPRESSED_TOTAL_BYTES) {
                    throw new IOException("Tarball exceeds total uncompressed size cap ("
                        + MAX_UNCOMPRESSED_TOTAL_BYTES + " bytes); refusing extraction");
                }

                // Ensure parent directory exists (and stays inside destDir — implied because
                // resolved is within normalisedDestDir).
                Path parent = resolved.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }

                // Stream-copy with both per-file and running total enforcement
                // (defends against tar entries that lie about their size).
                long bytesWritten = 0L;
                try (OutputStream out = Files.newOutputStream(resolved)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = tarIn.read(buffer)) != -1) {
                        bytesWritten += bytesRead;
                        if (bytesWritten > MAX_PER_FILE_BYTES) {
                            throw new IOException("Entry " + entryName + " exceeds per-file size cap during streaming");
                        }
                        if (runningTotal + bytesWritten > MAX_UNCOMPRESSED_TOTAL_BYTES) {
                            throw new IOException("Tarball exceeds total uncompressed size cap during streaming");
                        }
                        out.write(buffer, 0, bytesRead);
                    }
                }
                runningTotal += bytesWritten;

                // Set executable permissions for Unix systems
                if (entryName.contains("/bin/") || entryName.endsWith(".so")) {
                    try {
                        resolved.toFile().setExecutable(true);
                    } catch (Exception e) {
                        // Ignore permission errors on Windows
                    }
                }

                if (extractedEntries % 1000 == 0) {
                    logger.debug("Extracted {} entries ({} bytes)...", extractedEntries, runningTotal);
                }
            }

            logger.info("Extraction complete: {} entries, {} bytes uncompressed",
                extractedEntries, runningTotal);
        }
    }

    // ========================================================================
    // SHA-256 verification (C15 — Sprint 2 hardening)
    // ========================================================================

    /**
     * Verify that the file at {@code path} hashes to {@code expectedHexSha256}.
     *
     * <p>Uses streaming SHA-256 so memory use stays constant regardless of file size.
     * Comparison is constant-time (via {@link MessageDigest#isEqual}) to avoid leaking
     * partial-match information through timing.
     *
     * @throws IOException if the file is unreadable, the algorithm is unavailable,
     *                     or the hash does not match
     */
    static void verifySha256(Path path, String expectedHexSha256) throws IOException {
        if (expectedHexSha256 == null || expectedHexSha256.isBlank()) {
            throw new IOException("Refusing to extract: no SHA-256 hash configured for downloaded file. "
                + "Configure a hash in PythonDistributionManager.PINNED_SHA256, or set "
                + "-Dignition.python3.skipChecksum=true to override (NOT recommended).");
        }
        String expected = expectedHexSha256.trim().toLowerCase(java.util.Locale.ROOT);

        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 algorithm unavailable (JVM misconfiguration?)", e);
        }

        try (InputStream in = new BufferedInputStream(Files.newInputStream(path));
             DigestInputStream dis = new DigestInputStream(in, digest)) {
            byte[] buf = new byte[8192];
            while (dis.read(buf) != -1) {
                // Just consume — DigestInputStream updates the digest.
            }
        }

        byte[] actualBytes = digest.digest();
        StringBuilder hex = new StringBuilder(actualBytes.length * 2);
        for (byte b : actualBytes) {
            String h = Integer.toHexString(b & 0xff);
            if (h.length() == 1) hex.append('0');
            hex.append(h);
        }
        String actual = hex.toString();

        if (!MessageDigest.isEqual(
                expected.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                actual.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            throw new IOException("SHA-256 mismatch: expected=" + expected + ", actual=" + actual);
        }
    }

    /**
     * Pinned SHA-256 hashes for python-build-standalone tarballs.
     *
     * <p>Keys are the URLs declared in {@link #AVAILABLE_DISTRIBUTIONS} and
     * {@link #DISTRIBUTION_URLS}. Values are lower-case hex SHA-256.
     *
     * <p>The hashes are sourced from the upstream {@code .sha256} sidecar files
     * published next to each release tarball at
     * <a href="https://github.com/indygreg/python-build-standalone/releases">github.com/indygreg/python-build-standalone/releases</a>.
     * The Sprint 2 fix (C15) introduced this map; entries without a real hash
     * intentionally remain {@code null} so {@link #verifySha256} refuses to
     * extract them — operators must opt-in to skip verification by setting
     * {@code -Dignition.python3.skipChecksum=true}.
     *
     * <p><b>To add or update a hash</b>: download the matching {@code .sha256}
     * sidecar from the GitHub release, paste the hex string here, and rebuild.
     */
    static final Map<String, String> PINNED_SHA256 = new HashMap<>();
    static {
        // v4.3.5: all 20 hashes populated from the upstream .sha256 sidecars —
        // the C15 "verify out-of-band then pin" rollout step had been left as
        // null-seeding, so a clean gateway with no system Python refused to
        // extract its own auto-download and the pool never started
        // (Acceptance Contract workflow 1 defect, found 04/07/2026).
        // Any URL not listed here still resolves to null = refuse to extract.
        PINNED_SHA256.put(
                "https://github.com/indygreg/python-build-standalone/releases/download/20231002/cpython-3.10.13+20231002-aarch64-apple-darwin-install_only.tar.gz",
                "fd027b1dedf1ea034cdaa272e91771bdf75ddef4c8653b05d224a0645aa2ca3c");
        PINNED_SHA256.put(
                "https://github.com/indygreg/python-build-standalone/releases/download/20231002/cpython-3.10.13+20231002-x86_64-apple-darwin-install_only.tar.gz",
                "be0b19b6af1f7d8c667e5abef5505ad06cf72e5a11bb5844970c395a7e5b1275");
        PINNED_SHA256.put(
                "https://github.com/indygreg/python-build-standalone/releases/download/20231002/cpython-3.10.13+20231002-x86_64-pc-windows-msvc-shared-install_only.tar.gz",
                "b8d930ce0d04bda83037ad3653d7450f8907c88e24bb8255a29b8dab8930d6f1");
        PINNED_SHA256.put(
                "https://github.com/indygreg/python-build-standalone/releases/download/20231002/cpython-3.10.13+20231002-x86_64-unknown-linux-gnu-install_only.tar.gz",
                "5d0429c67c992da19ba3eb58b3acd0b35ec5e915b8cae9a4aa8ca565c423847a");
        PINNED_SHA256.put(
                "https://github.com/indygreg/python-build-standalone/releases/download/20231002/cpython-3.11.6+20231002-aarch64-apple-darwin-install_only.tar.gz",
                "916c35125b5d8323a21526d7a9154ca626453f63d0878e95b9f613a95006c990");
        PINNED_SHA256.put(
                "https://github.com/indygreg/python-build-standalone/releases/download/20231002/cpython-3.11.6+20231002-x86_64-apple-darwin-install_only.tar.gz",
                "178cb1716c2abc25cb56ae915096c1a083e60abeba57af001996e8bc6ce1a371");
        PINNED_SHA256.put(
                "https://github.com/indygreg/python-build-standalone/releases/download/20231002/cpython-3.11.6+20231002-x86_64-pc-windows-msvc-shared-install_only.tar.gz",
                "3933545e6d41462dd6a47e44133ea40995bc6efeed8c2e4cbdf1a699303e95ea");
        PINNED_SHA256.put(
                "https://github.com/indygreg/python-build-standalone/releases/download/20231002/cpython-3.11.6+20231002-x86_64-unknown-linux-gnu-install_only.tar.gz",
                "ee37a7eae6e80148c7e3abc56e48a397c1664f044920463ad0df0fc706eacea8");
        PINNED_SHA256.put(
                "https://github.com/indygreg/python-build-standalone/releases/download/20231002/cpython-3.9.18+20231002-aarch64-apple-darwin-install_only.tar.gz",
                "fdc4054837e37b69798c2ef796222a480bc1f80e8ad3a01a95d0168d8282a007");
        PINNED_SHA256.put(
                "https://github.com/indygreg/python-build-standalone/releases/download/20231002/cpython-3.9.18+20231002-x86_64-apple-darwin-install_only.tar.gz",
                "82231cb77d4a5c8081a1a1d5b8ae440abe6993514eb77a926c826e9a69a94fb1");
        PINNED_SHA256.put(
                "https://github.com/indygreg/python-build-standalone/releases/download/20231002/cpython-3.9.18+20231002-x86_64-pc-windows-msvc-shared-install_only.tar.gz",
                "02ea7bb64524886bd2b05d6b6be4401035e4ba4319146f274f0bcd992822cd75");
        PINNED_SHA256.put(
                "https://github.com/indygreg/python-build-standalone/releases/download/20231002/cpython-3.9.18+20231002-x86_64-unknown-linux-gnu-install_only.tar.gz",
                "f3ff38b1ccae7dcebd8bbf2e533c9a984fac881de0ffd1636fbb61842bd924de");
        PINNED_SHA256.put(
                "https://github.com/indygreg/python-build-standalone/releases/download/20240107/cpython-3.12.1+20240107-aarch64-apple-darwin-install_only.tar.gz",
                "f93f8375ca6ac0a35d58ff007043cbd3a88d9609113f1cb59cf7c8d215f064af");
        PINNED_SHA256.put(
                "https://github.com/indygreg/python-build-standalone/releases/download/20240107/cpython-3.12.1+20240107-x86_64-apple-darwin-install_only.tar.gz",
                "eca96158c1568dedd9a0b3425375637a83764d1fa74446438293089a8bfac1f8");
        PINNED_SHA256.put(
                "https://github.com/indygreg/python-build-standalone/releases/download/20240107/cpython-3.12.1+20240107-x86_64-pc-windows-msvc-shared-install_only.tar.gz",
                "fd5a9e0f41959d0341246d3643f2b8794f638adc0cec8dd5e1b6465198eae08a");
        PINNED_SHA256.put(
                "https://github.com/indygreg/python-build-standalone/releases/download/20240107/cpython-3.12.1+20240107-x86_64-unknown-linux-gnu-install_only.tar.gz",
                "74e330b8212ca22fd4d9a2003b9eec14892155566738febc8e5e572f267b9472");
        PINNED_SHA256.put(
                "https://github.com/indygreg/python-build-standalone/releases/download/20241016/cpython-3.13.0+20241016-aarch64-apple-darwin-install_only.tar.gz",
                "31397953849d275aa2506580f3fa1cb5a85b6a3d392e495f8030e8b6412f5556");
        PINNED_SHA256.put(
                "https://github.com/indygreg/python-build-standalone/releases/download/20241016/cpython-3.13.0+20241016-x86_64-apple-darwin-install_only.tar.gz",
                "cff1b7e7cd26f2d47acac1ad6590e27d29829776f77e8afa067e9419f2f6ce77");
        PINNED_SHA256.put(
                "https://github.com/indygreg/python-build-standalone/releases/download/20241016/cpython-3.13.0+20241016-x86_64-pc-windows-msvc-shared-install_only.tar.gz",
                "b25926e8ce4164cf103bacc4f4d154894ea53e07dd3fdd5ebb16fb1a82a7b1a0");
        PINNED_SHA256.put(
                "https://github.com/indygreg/python-build-standalone/releases/download/20241016/cpython-3.13.0+20241016-x86_64-unknown-linux-gnu-install_only.tar.gz",
                "2c8cb15c6a2caadaa98af51df6fe78a8155b8471cb3dd7b9836038e0d3657fb4");
    }

    /**
     * @return the pinned SHA-256 hash for {@code url}, or {@code null} if no hash is pinned.
     */
    static String getPinnedSha256(String url) {
        return PINNED_SHA256.get(url);
    }

    /**
     * Verify a downloaded tarball against its pinned SHA-256 (if any).
     *
     * <p>Behaviour matrix:
     * <ul>
     *   <li>pinned hash present, file matches → {@code return} normally;</li>
     *   <li>pinned hash present, file mismatches → throws {@link IOException};</li>
     *   <li>no pinned hash, system property
     *       {@code -Dignition.python3.skipChecksum=true} → log warning and continue;</li>
     *   <li>no pinned hash, no opt-in → throw {@link IOException} (default-deny).</li>
     * </ul>
     */
    void verifyDownloadedTarball(Path downloadPath, String url) throws IOException {
        String pinned = getPinnedSha256(url);
        boolean skipChecksum = Boolean.parseBoolean(
            System.getProperty("ignition.python3.skipChecksum", "false"));

        if (pinned == null) {
            if (skipChecksum) {
                logger.warn("No pinned SHA-256 for {} and -Dignition.python3.skipChecksum=true; "
                    + "proceeding WITHOUT integrity check (NOT recommended for production).", url);
                return;
            }
            throw new IOException("No pinned SHA-256 for download URL " + url
                + "; refusing to extract. Set -Dignition.python3.skipChecksum=true "
                + "to override (NOT recommended), or pin the hash in PythonDistributionManager.PINNED_SHA256.");
        }

        verifySha256(downloadPath, pinned);
        logger.info("SHA-256 verified for {} ({} bytes)", url, Files.size(downloadPath));
    }

    /**
     * Detect operating system
     */
    String detectOS() {
        String os = System.getProperty("os.name").toLowerCase();
        String arch = System.getProperty("os.arch").toLowerCase();

        if (os.contains("win")) {
            return "windows";
        } else if (os.contains("mac")) {
            if (arch.contains("aarch64") || arch.contains("arm")) {
                return "macos-arm64";
            } else {
                return "macos-x64";
            }
        } else {
            return "linux";
        }
    }

    /**
     * Get installation status (legacy, includes multi-version info)
     */
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();

        status.put("os", detectOS());
        status.put("embeddedInstalled", isEmbeddedPythonInstalled());
        status.put("pythonDir", pythonDir.toString());
        status.put("distributionsDir", distributionsDir.toString());
        status.put("autoDownload", autoDownload);
        status.put("installedVersions", getInstalledVersions());
        status.put("availableVersions", getAvailableDownloadVersions());

        // Check for virtual environment
        String venvPath = detectVirtualEnv();
        if (venvPath != null) {
            status.put("usingVenv", true);
            status.put("venvPath", venvPath);
        } else {
            status.put("usingVenv", false);
        }

        try {
            String pythonPath = getPythonPath();
            status.put("pythonPath", pythonPath);
            status.put("available", true);
        } catch (IOException e) {
            status.put("available", false);
            status.put("error", e.getMessage());
        }

        return status;
    }

    /**
     * Get the currently configured virtual environment path, if any.
     *
     * @return Virtual environment root directory, or null if not using venv
     */
    public String getVirtualEnvPath() {
        String venvDir = System.getProperty("ignition.python3.venv");
        if (venvDir != null && !venvDir.isEmpty()) {
            Path venvPath = Path.of(venvDir);
            if (Files.exists(venvPath)) {
                return venvDir;
            }
        }

        // Check if python3.path points to a venv
        String pythonPath = System.getProperty("ignition.python3.path");
        if (pythonPath != null && !pythonPath.isEmpty()) {
            Path path = Path.of(pythonPath);
            if (path.getParent() != null) {
                Path parentDir = path.getParent();
                String parentName = parentDir.getFileName().toString();

                if ("bin".equals(parentName) || "Scripts".equals(parentName)) {
                    Path possibleVenvRoot = parentDir.getParent();
                    if (possibleVenvRoot != null) {
                        Path pyvenvCfg = possibleVenvRoot.resolve("pyvenv.cfg");
                        if (Files.exists(pyvenvCfg)) {
                            return possibleVenvRoot.toString();
                        }
                    }
                }
            }
        }

        return null;
    }

    /**
     * Force reinstall of embedded Python (for troubleshooting)
     */
    public void reinstall() throws IOException {
        logger.info("Reinstalling embedded Python...");

        // Delete existing installation
        if (Files.exists(pythonDir)) {
            deleteDirectory(pythonDir);
        }

        Files.createDirectories(pythonDir);

        // Download and install
        downloadAndInstall();
    }

    /**
     * Get the distributions directory path.
     *
     * @return path to the distributions directory
     */
    public Path getDistributionsDir() {
        return distributionsDir;
    }

    /**
     * Delete directory recursively
     */
    private void deleteDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }

        Files.walk(directory)
                .sorted((a, b) -> -a.compareTo(b)) // Reverse order for bottom-up deletion
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        logger.warn("Failed to delete: {}", path);
                    }
                });
    }

    // ========================================================================
    // Progress notification helpers
    // ========================================================================

    private void notifyProgress(String version, String stage, int percent, String message) {
        InstallProgressListener listener = this.progressListener;
        if (listener != null) {
            try {
                listener.onProgress(version, stage, percent, message);
            } catch (Exception e) {
                logger.warn("Progress listener error", e);
            }
        }
    }

    private void notifyComplete(String version, boolean success, String message) {
        InstallProgressListener listener = this.progressListener;
        if (listener != null) {
            try {
                listener.onComplete(version, success, message);
            } catch (Exception e) {
                logger.warn("Progress listener error", e);
            }
        }
    }
}
