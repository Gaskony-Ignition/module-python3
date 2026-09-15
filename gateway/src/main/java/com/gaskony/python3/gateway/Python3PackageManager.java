package com.gaskony.python3.gateway;

import com.inductiveautomation.ignition.common.gson.Gson;
import com.inductiveautomation.ignition.common.gson.JsonObject;
import com.inductiveautomation.ignition.common.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Manages bundled Python packages for offline installation.
 * <p>
 * Bundles .whl files in module resources and installs them to the Python environment
 * on demand, enabling offline/air-gapped deployment.
 * </p>
 *
 * <p>Features:</p>
 * <ul>
 *   <li>Platform-specific wheel extraction (Windows x64, Linux x64)</li>
 *   <li>Package installation via pip</li>
 *   <li>Installation tracking and verification</li>
 *   <li>Support for package bundles (e.g., "web" includes requests + dependencies)</li>
 * </ul>
 *
 * @since v2.3.0
 */
public class Python3PackageManager {

    private static final Logger logger = LoggerFactory.getLogger(Python3PackageManager.class);
    private static final Gson GSON = new Gson();

    /**
     * PEP 503 normalised distribution name with optional version specifier.
     * <p>
     * Used to reject argument-injection attempts against pip. Specs that do not
     * match this pattern (or that start with {@code -}) are refused before being
     * passed to {@code python -m pip ...}.
     * </p>
     * <ul>
     *   <li>Name: starts with alphanumeric, then alphanumeric / {@code .} / {@code _} / {@code -}.</li>
     *   <li>Optional version: one of {@code ==}, {@code &gt;=}, {@code &lt;=}, {@code !=}, {@code ~=}, {@code &gt;}, {@code &lt;}
     *       followed by version characters {@code A-Za-z0-9.+!*}.</li>
     * </ul>
     */
    static final Pattern PACKAGE_SPEC_PATTERN =
            Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]*([=<>!~]=?[A-Za-z0-9.+!*]+)?$");

    /**
     * Validate a user-supplied pip package spec.
     * <p>
     * pip parses any argument starting with {@code -} as an option (e.g.
     * {@code --index-url=http://attacker/}), so {@link ProcessBuilder} alone is
     * not sufficient — we must pre-validate before invoking pip and additionally
     * insert a {@code --} separator on the command line.
     * </p>
     *
     * @param spec package specifier (e.g., {@code "requests"}, {@code "numpy==1.26.2"})
     * @return {@code true} if the spec is safe to pass to pip
     */
    static boolean isValidPackageSpec(String spec) {
        if (spec == null) {
            return false;
        }
        String trimmed = spec.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("-")) {
            return false;
        }
        return PACKAGE_SPEC_PATTERN.matcher(trimmed).matches();
    }

    private final Path moduleDataDir;
    private final Path packagesDir;
    private final Path installedPackagesFile;
    private final String pythonExecutable;
    private final Supplier<List<String>> allPythonExecutablesSupplier;

    private Map<String, PackageInfo> packageCatalog;
    private Set<String> installedPackages;

    /**
     * Creates a new package manager that only ever targets a single Python executable.
     *
     * @param moduleDataDir    Directory for module data
     * @param pythonExecutable Path to Python executable
     */
    public Python3PackageManager(Path moduleDataDir, String pythonExecutable) {
        this(moduleDataDir, pythonExecutable, null);
    }

    /**
     * Creates a new package manager that installs/uninstalls packages across EVERY
     * installed Python distribution, not just the primary one (v4.5.1).
     *
     * <p>{@code pythonExecutable} remains the PRIMARY/default distribution: it is used
     * for catalog reads, {@link #verifyPackages()}, {@link #getStatus()}, and is the
     * sole determinant of overall install/uninstall success (so behaviour for
     * single-distribution gateways never regresses). {@code allPythonExecutablesSupplier}
     * is consulted live at the start of every install/uninstall — since distributions can
     * be added or removed at runtime — and any additional distributions it returns are
     * treated as best-effort: a failure on a secondary distribution is logged at WARN but
     * does not fail the operation.</p>
     *
     * @param moduleDataDir                  Directory for module data
     * @param pythonExecutable               Path to the PRIMARY/default Python executable
     * @param allPythonExecutablesSupplier   supplies the executables of every currently
     *                                       installed distribution (including the primary,
     *                                       if present — duplicates are de-duplicated); may
     *                                       be {@code null} to only ever target the primary
     * @since v4.5.1
     */
    public Python3PackageManager(Path moduleDataDir, String pythonExecutable,
            Supplier<List<String>> allPythonExecutablesSupplier) {
        this.moduleDataDir = moduleDataDir;
        this.packagesDir = moduleDataDir.resolve("packages");
        this.installedPackagesFile = moduleDataDir.resolve("installed-packages.json");
        this.pythonExecutable = pythonExecutable;
        this.allPythonExecutablesSupplier = allPythonExecutablesSupplier;

        try {
            Files.createDirectories(packagesDir);
            loadPackageCatalog();
            loadInstalledPackages();
        } catch (IOException e) {
            logger.error("Failed to initialize package manager", e);
            this.packageCatalog = new HashMap<>();
            this.installedPackages = new HashSet<>();
        }
    }

    /**
     * Get the primary Python executable plus every other installed distribution's
     * executable (read live from {@link #allPythonExecutablesSupplier}), de-duplicated,
     * primary first.
     *
     * @since v4.5.1
     */
    private List<String> getAllPythonExecutables() {
        List<String> result = new ArrayList<>();
        result.add(pythonExecutable);
        if (allPythonExecutablesSupplier != null) {
            try {
                List<String> extra = allPythonExecutablesSupplier.get();
                if (extra != null) {
                    for (String exe : extra) {
                        if (exe != null && !exe.isBlank() && !result.contains(exe)) {
                            result.add(exe);
                        }
                    }
                }
            } catch (Exception e) {
                logger.warn("Failed to read list of installed Python distributions;"
                        + " only the primary distribution will be targeted", e);
            }
        }
        return result;
    }

    /**
     * Load package catalog from packages.json resource.
     */
    private void loadPackageCatalog() throws IOException {
        try (InputStream is = getClass().getResourceAsStream("/packages.json")) {
            if (is == null) {
                logger.warn("packages.json not found in resources, package catalog empty");
                packageCatalog = new HashMap<>();
                return;
            }

            String json = new BufferedReader(new InputStreamReader(is))
                    .lines()
                    .reduce("", (a, b) -> a + b);

            packageCatalog = GSON.fromJson(json,
                    new TypeToken<Map<String, PackageInfo>>() {
                    }.getType());

            logger.info("Loaded package catalog: {} packages", packageCatalog.size());
        }
    }

    /**
     * Load list of installed packages from tracking file.
     */
    private void loadInstalledPackages() {
        installedPackages = new HashSet<>();

        if (!Files.exists(installedPackagesFile)) {
            return;
        }

        try {
            String json = Files.readString(installedPackagesFile);
            installedPackages = GSON.fromJson(json, new TypeToken<Set<String>>() {
            }.getType());
            logger.info("Loaded installed packages: {}", installedPackages);
        } catch (IOException e) {
            logger.error("Failed to load installed packages file", e);
        }
    }

    /**
     * Save installed packages to tracking file.
     */
    private void saveInstalledPackages() {
        try {
            String json = GSON.toJson(installedPackages);
            Files.writeString(installedPackagesFile, json);
        } catch (IOException e) {
            logger.error("Failed to save installed packages file", e);
        }
    }

    /**
     * Get package catalog (available packages).
     *
     * @return Map of package name to package info
     */
    public Map<String, PackageInfo> getPackageCatalog() {
        return new HashMap<>(packageCatalog);
    }

    /**
     * Get list of installed package names.
     *
     * @return Set of installed package names
     */
    public Set<String> getInstalledPackages() {
        return new HashSet<>(installedPackages);
    }

    /**
     * Check if a package is installed.
     *
     * @param packageName Package name
     * @return True if installed
     */
    public boolean isInstalled(String packageName) {
        return installedPackages.contains(packageName);
    }

    /**
     * Install a package bundle by name.
     *
     * @param packageName Package bundle name (e.g., "jedi", "web", "datascience")
     * @return Installation result
     */
    public InstallResult installPackage(String packageName) {
        logger.info("Installing package: {}", packageName);

        PackageInfo packageInfo = packageCatalog.get(packageName);
        if (packageInfo == null) {
            return new InstallResult(false,
                    "Package not found in catalog: " + packageName, new ArrayList<>());
        }

        // Check if already installed
        if (isInstalled(packageName)) {
            logger.info("Package {} already installed, skipping", packageName);
            return new InstallResult(true, "Already installed", new ArrayList<>());
        }

        List<String> installedWheels = new ArrayList<>();

        try {
            // v4.3.5 (workflow-8 defect): resolve against what is ACTUALLY bundled
            // instead of the exact filenames in packages.json. The catalog's wheel
            // list had drifted from the bundled files (and contained literal
            // "{platform}" placeholders the runtime never substituted), so every
            // bundle except jedi failed with "Wheel not found in resources".
            // The bundled resource directory is now the single source of truth;
            // anything not bundled for this platform (e.g. numpy on linux, where
            // binary wheels are not shipped for size) falls back to PyPI — the
            // same network posture as the Python distribution auto-download.
            String platform = detectPlatform();
            List<String> bundled = listBundledWheels(platform);
            logger.debug("Bundled wheels for {}: {}", platform, bundled);

            List<String> fromPyPI = new ArrayList<>();
            for (String pipName : packageInfo.pipPackages) {
                String wheelName = findBundledWheel(bundled, pipName);
                if (wheelName == null) {
                    fromPyPI.add(pipName);
                    continue;
                }
                Path wheelPath = extractWheel("/python-packages/" + platform + "/" + wheelName);
                if (wheelPath == null) {
                    return new InstallResult(false,
                            "Wheel not found in resources: " + wheelName, installedWheels);
                }
                if (!installWheel(wheelPath)) {
                    return new InstallResult(false,
                            "Failed to install wheel: " + wheelName, installedWheels);
                }
                installedWheels.add(wheelName);
            }

            for (String pipName : fromPyPI) {
                logger.info("No bundled {} wheel for {}; installing from PyPI", pipName, platform);
                InstallResult pypi = pipInstallFromPyPI(pipName);
                if (!pypi.success) {
                    return new InstallResult(false,
                            "'" + pipName + "' is not bundled for platform '" + platform
                            + "' and PyPI install failed: " + pypi.message, installedWheels);
                }
                installedWheels.add(pipName + " (PyPI)");
            }

            // Mark as installed
            installedPackages.add(packageName);
            saveInstalledPackages();

            String summary = "Successfully installed " + (installedWheels.size() - fromPyPI.size())
                    + " bundled wheel(s)"
                    + (fromPyPI.isEmpty() ? "" : " and " + fromPyPI.size() + " package(s) from PyPI");
            logger.info("Successfully installed package: {} ({})", packageName, summary);
            return new InstallResult(true, summary, installedWheels);

        } catch (Exception e) {
            logger.error("Failed to install package: {}", packageName, e);
            return new InstallResult(false, "Installation failed: " + e.getMessage(), installedWheels);
        }
    }

    /**
     * Install a package directly from PyPI using pip.
     * Unlike installPackage() which uses bundled wheels, this downloads from PyPI.
     *
     * @param packageSpec Package name, optionally with version (e.g., "numpy", "numpy==1.26.2")
     * @return Installation result
     * @since v3.6.1
     */
    public InstallResult pipInstallFromPyPI(String packageSpec) {
        logger.info("Installing from PyPI: {}", packageSpec);

        // Defence in depth: reject argument-injection attempts (e.g. --index-url=...)
        // before invoking pip. ProcessBuilder doesn't shell-escape, but pip itself
        // parses any argument starting with `-` as an option, so a malicious spec
        // can re-point pip at an attacker-controlled index. See B2 in
        // /modules/.review/FINAL_REVIEW.md.
        if (!isValidPackageSpec(packageSpec)) {
            logger.warn("Rejected invalid pip package spec: {}", packageSpec);
            return new InstallResult(false,
                    "Invalid package spec. Must match PEP 503 name with optional version (e.g. 'requests', 'numpy==1.26.2').",
                    new ArrayList<>());
        }
        String safeSpec = packageSpec.trim();

        // v4.5.1: install into EVERY installed distribution, not just the primary.
        // Overall success/failure is determined solely by the primary distribution
        // (index 0) so single-distribution gateways behave exactly as before;
        // secondary distributions are best-effort (e.g. no matching wheel for that
        // Python version is a WARN, not a failure of the whole operation).
        List<String> pythons = getAllPythonExecutables();
        PipResult primaryResult = runPipInstall(pythons.get(0), safeSpec);

        for (int i = 1; i < pythons.size(); i++) {
            String exe = pythons.get(i);
            PipResult secondaryResult = runPipInstall(exe, safeSpec);
            if (secondaryResult.success) {
                logger.info("Installed {} into secondary distribution: {}", safeSpec, exe);
            } else {
                logger.warn("Failed to install {} into secondary distribution {}: {}",
                        safeSpec, exe, secondaryResult.message);
            }
        }

        if (primaryResult.success) {
            // Extract package name (without version spec) for tracking
            String baseName = safeSpec.split("[=<>!\\[\\]]")[0].trim();
            installedPackages.add(baseName);
            saveInstalledPackages();

            logger.info("Successfully installed from PyPI: {}", safeSpec);
            List<String> installed = new ArrayList<>();
            installed.add(safeSpec);
            return new InstallResult(true, "Successfully installed " + safeSpec, installed);
        } else {
            logger.error("pip install failed for {} on primary distribution ({}): {}",
                    safeSpec, pythons.get(0), primaryResult.message);
            return new InstallResult(false, "Installation failed: " + primaryResult.message, new ArrayList<>());
        }
    }

    /**
     * Run {@code pip install} for a single package spec against a single Python
     * executable. Pure I/O helper — no state mutation — so both the primary and
     * secondary distributions can share it.
     *
     * @since v4.5.1
     */
    private PipResult runPipInstall(String pythonExe, String safeSpec) {
        List<String> command = List.of(
                pythonExe,
                "-m", "pip", "install",
                "--disable-pip-version-check",
                "--no-input",
                "--",                // end-of-options marker; pip won't parse safeSpec as a flag
                safeSpec
        );
        return executeProcess(command, 120);
    }

    /**
     * Uninstall a pip package by name (works for both catalog and PyPI packages).
     *
     * @param packageName Package name
     * @return True if successfully uninstalled
     * @since v3.6.1
     */
    public boolean pipUninstall(String packageName) {
        logger.info("Uninstalling pip package: {}", packageName);
        boolean result = uninstallPipPackage(packageName);
        if (result) {
            installedPackages.remove(packageName);
            saveInstalledPackages();
        }
        return result;
    }

    /**
     * Uninstall a package by name.
     * <p>
     * If {@code packageName} is a catalog bundle key (e.g. {@code jedi},
     * {@code web}, {@code datascience}) every pip package in the bundle is
     * uninstalled. Otherwise — the common case for a package that was installed
     * individually from PyPI and recorded under its bare name (e.g.
     * {@code pandas}) — it falls back to a direct single-package pip uninstall.
     * Both paths remove the package from every installed distribution and from
     * {@code installed-packages.json} (v4.5.2 — fixes install/uninstall
     * asymmetry where individually-installed packages could never be removed).
     * </p>
     *
     * @param packageName Package bundle name or individual PyPI package name
     * @return True if successfully uninstalled
     */
    public boolean uninstallPackage(String packageName) {
        logger.info("Uninstalling package: {}", packageName);

        if (!isInstalled(packageName)) {
            logger.warn("Package {} not installed, skipping uninstall", packageName);
            return true;
        }

        PackageInfo packageInfo = packageCatalog.get(packageName);
        if (packageInfo == null) {
            // Not a catalog bundle — this is an individually-installed PyPI
            // package recorded under its bare name. Uninstall it as a single
            // pip package (pipUninstall runs across ALL installed distributions
            // and updates installed-packages.json on success).
            logger.info("Package {} is not a catalog bundle; uninstalling as a single pip package",
                    packageName);
            return pipUninstall(packageName);
        }

        try {
            // Uninstall each package in the bundle
            for (String pipPackage : packageInfo.pipPackages) {
                boolean success = uninstallPipPackage(pipPackage);
                if (!success) {
                    logger.warn("Failed to uninstall pip package: {}", pipPackage);
                    // Continue with other packages
                }
            }

            // Remove from installed set
            installedPackages.remove(packageName);
            saveInstalledPackages();

            logger.info("Successfully uninstalled package: {}", packageName);
            return true;

        } catch (Exception e) {
            logger.error("Failed to uninstall package: {}", packageName, e);
            return false;
        }
    }

    /**
     * Verify installed packages (check they're actually available to Python).
     *
     * @return Map of package name to verification status
     */
    public Map<String, Boolean> verifyPackages() {
        Map<String, Boolean> results = new HashMap<>();

        for (String packageName : installedPackages) {
            PackageInfo info = packageCatalog.get(packageName);
            if (info == null) {
                results.put(packageName, false);
                continue;
            }

            // Try importing the primary module
            boolean verified = verifyPythonImport(info.importName);
            results.put(packageName, verified);
        }

        return results;
    }

    /**
     * Extract a wheel file from resources to packages directory.
     *
     * @param resourcePath Resource path to wheel file
     * @return Path to extracted wheel, or null if not found
     */
    /**
     * List the wheel files actually bundled under {@code /python-packages/<platform>/}.
     * Works whether the resources are inside the module jar (production) or on a
     * plain classpath directory (unit tests / IDE). (v4.3.5 — see installPackage.)
     */
    List<String> listBundledWheels(String platform) {
        String prefix = "python-packages/" + platform + "/";
        List<String> wheels = new ArrayList<>();
        try {
            java.net.URL url = getClass().getResource("/" + prefix);
            if (url == null) {
                logger.warn("No bundled wheel directory for platform: {}", platform);
                return wheels;
            }
            if ("jar".equals(url.getProtocol())) {
                java.net.JarURLConnection conn = (java.net.JarURLConnection) url.openConnection();
                try (java.util.jar.JarFile jar = conn.getJarFile()) {
                    java.util.Enumeration<java.util.jar.JarEntry> entries = jar.entries();
                    while (entries.hasMoreElements()) {
                        String name = entries.nextElement().getName();
                        if (name.startsWith(prefix) && name.endsWith(".whl")) {
                            wheels.add(name.substring(prefix.length()));
                        }
                    }
                }
            } else {
                Path dir = Path.of(url.toURI());
                try (var stream = Files.list(dir)) {
                    stream.map(p -> p.getFileName().toString())
                          .filter(n -> n.endsWith(".whl"))
                          .forEach(wheels::add);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to list bundled wheels for platform: {}", platform, e);
        }
        Collections.sort(wheels);
        return wheels;
    }

    /**
     * Find the bundled wheel for a pip package name, or {@code null} if none.
     * Both sides are normalised per PEP 503 (lowercase, {@code -}/{@code .} → {@code _})
     * because wheel filenames use underscores where pip names use hyphens
     * (e.g. pip {@code python-dateutil} ↔ wheel {@code python_dateutil-2.9.0...whl}).
     */
    static String findBundledWheel(List<String> bundledWheels, String pipName) {
        String want = pipName.toLowerCase().replace('-', '_').replace('.', '_');
        for (String wheel : bundledWheels) {
            int dash = wheel.indexOf('-');
            if (dash <= 0) {
                continue;
            }
            String dist = wheel.substring(0, dash).toLowerCase().replace('-', '_').replace('.', '_');
            if (dist.equals(want)) {
                return wheel;
            }
        }
        return null;
    }

    private Path extractWheel(String resourcePath) {
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) {
                logger.error("Wheel not found in resources: {}", resourcePath);
                return null;
            }

            String wheelName = resourcePath.substring(resourcePath.lastIndexOf('/') + 1);
            Path wheelPath = packagesDir.resolve(wheelName);

            Files.copy(is, wheelPath, StandardCopyOption.REPLACE_EXISTING);
            logger.debug("Extracted wheel to: {}", wheelPath);

            return wheelPath;

        } catch (IOException e) {
            logger.error("Failed to extract wheel: {}", resourcePath, e);
            return null;
        }
    }

    /**
     * Install a wheel file using pip, into EVERY installed distribution (v4.5.1).
     * Overall success is determined solely by the primary distribution; secondary
     * distributions are best-effort (see {@link #pipInstallFromPyPI(String)}).
     *
     * @param wheelPath Path to wheel file
     * @return True if the PRIMARY distribution install succeeded
     */
    private boolean installWheel(Path wheelPath) {
        List<String> pythons = getAllPythonExecutables();
        boolean primarySuccess = runWheelInstall(pythons.get(0), wheelPath);

        for (int i = 1; i < pythons.size(); i++) {
            String exe = pythons.get(i);
            boolean secondarySuccess = runWheelInstall(exe, wheelPath);
            if (secondarySuccess) {
                logger.info("Installed wheel {} into secondary distribution: {}", wheelPath.getFileName(), exe);
            } else {
                logger.warn("Failed to install wheel {} into secondary distribution: {}",
                        wheelPath.getFileName(), exe);
            }
        }

        return primarySuccess;
    }

    /**
     * Run {@code pip install --no-index --no-deps <wheel>} against a single Python
     * executable.
     *
     * @since v4.5.1
     */
    private boolean runWheelInstall(String pythonExe, Path wheelPath) {
        List<String> command = List.of(
                pythonExe,
                "-m", "pip", "install",
                "--no-index",  // Don't use PyPI
                "--no-deps",   // Don't install dependencies (we bundle them)
                wheelPath.toString()
        );
        PipResult result = executeProcess(command, 60);
        if (result.success) {
            logger.info("Successfully installed wheel: {} ({})", wheelPath.getFileName(), pythonExe);
        } else {
            logger.error("pip install failed for wheel {} on {}: {}", wheelPath.getFileName(), pythonExe, result.message);
        }
        return result.success;
    }

    /**
     * Uninstall a pip package from EVERY installed distribution (v4.5.1). Overall
     * success is determined solely by the primary distribution; a secondary
     * distribution not having the package installed (or any other secondary
     * failure) is logged at WARN and does not fail the operation.
     *
     * @param packageName Package name
     * @return True if the PRIMARY distribution uninstall succeeded
     */
    private boolean uninstallPipPackage(String packageName) {
        // Defence in depth: same pip argument-injection class as install. See B2.
        if (!isValidPackageSpec(packageName)) {
            logger.warn("Rejected invalid pip uninstall spec: {}", packageName);
            return false;
        }
        String safeName = packageName.trim();

        List<String> pythons = getAllPythonExecutables();
        PipResult primaryResult = runPipUninstall(pythons.get(0), safeName);

        for (int i = 1; i < pythons.size(); i++) {
            String exe = pythons.get(i);
            PipResult secondaryResult = runPipUninstall(exe, safeName);
            if (secondaryResult.success) {
                logger.info("Uninstalled {} from secondary distribution: {}", safeName, exe);
            } else {
                logger.warn("Could not uninstall {} from secondary distribution {}"
                        + " (may not be installed there): {}", safeName, exe, secondaryResult.message);
            }
        }

        if (!primaryResult.success) {
            logger.error("pip uninstall failed for {} on primary distribution ({}): {}",
                    safeName, pythons.get(0), primaryResult.message);
        }
        return primaryResult.success;
    }

    /**
     * Run {@code pip uninstall -y <name>} against a single Python executable.
     *
     * @since v4.5.1
     */
    private PipResult runPipUninstall(String pythonExe, String safeName) {
        List<String> command = List.of(
                pythonExe,
                "-m", "pip", "uninstall",
                "-y",  // Don't ask for confirmation
                "--",  // end-of-options marker; pip won't parse safeName as a flag
                safeName
        );
        return executeProcess(command, 30);
    }

    /**
     * Run a pip subprocess to completion and capture the outcome. Package-private
     * (rather than {@code private}) so tests can override it in a subclass to
     * verify which distributions were targeted without actually invoking pip.
     *
     * @param command       full command line (executable + args)
     * @param timeoutSeconds how long to wait before forcibly destroying the process
     * @since v4.5.1
     */
    PipResult executeProcess(List<String> command, long timeoutSeconds) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);

            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    logger.debug("pip [{}]: {}", command.get(0), line);
                }
            }

            boolean exited = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!exited) {
                process.destroyForcibly();
                return new PipResult(false, "Timed out after " + timeoutSeconds + " seconds");
            }

            if (process.exitValue() == 0) {
                return new PipResult(true, "OK");
            }

            String msg = output.toString().trim();
            if (msg.length() > 500) msg = msg.substring(msg.length() - 500);
            return new PipResult(false, msg);

        } catch (Exception e) {
            return new PipResult(false, String.valueOf(e.getMessage()));
        }
    }

    /**
     * Outcome of a single pip invocation (install or uninstall) against one Python
     * executable.
     *
     * @since v4.5.1
     */
    static final class PipResult {
        final boolean success;
        final String message;

        PipResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }

    /**
     * Verify a Python module can be imported.
     *
     * @param moduleName Module name to import
     * @return True if import succeeds
     */
    private boolean verifyPythonImport(String moduleName) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    pythonExecutable,
                    "-c",
                    "import " + moduleName
            );

            Process process = pb.start();
            boolean exited = process.waitFor(5, TimeUnit.SECONDS);

            if (!exited) {
                process.destroyForcibly();
                return false;
            }

            return process.exitValue() == 0;

        } catch (Exception e) {
            logger.error("Failed to verify import: {}", moduleName, e);
            return false;
        }
    }

    /**
     * Detect platform for wheel selection.
     *
     * @return Platform string (windows-x64 or linux-x64)
     */
    private String detectPlatform() {
        String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("win")) {
            return "windows-x64";
        } else if (os.contains("linux")) {
            return "linux-x64";
        } else {
            logger.warn("Unsupported platform: {}, defaulting to linux-x64", os);
            return "linux-x64";
        }
    }

    /**
     * Get installation status summary.
     *
     * @return Status map
     */
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();

        status.put("platform", detectPlatform());
        status.put("pythonExecutable", pythonExecutable);
        status.put("packagesDir", packagesDir.toString());
        status.put("catalogSize", packageCatalog.size());
        status.put("installedCount", installedPackages.size());
        status.put("installedPackages", new ArrayList<>(installedPackages));

        return status;
    }

    // Inner classes

    /**
     * Package metadata from packages.json.
     */
    public static class PackageInfo {
        public String version;
        public String description;
        public double sizeMb;
        public List<String> wheels;
        public List<String> pipPackages;
        public String importName;
        public List<String> requiredFor;

        public PackageInfo() {
            this.wheels = new ArrayList<>();
            this.pipPackages = new ArrayList<>();
            this.requiredFor = new ArrayList<>();
        }
    }

    /**
     * Installation result.
     */
    public static class InstallResult {
        public final boolean success;
        public final String message;
        public final List<String> installedWheels;

        public InstallResult(boolean success, String message, List<String> installedWheels) {
            this.success = success;
            this.message = message;
            this.installedWheels = installedWheels;
        }

        public JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("success", success);
            json.addProperty("message", message);
            json.add("installedWheels", GSON.toJsonTree(installedWheels));
            return json;
        }
    }
}
