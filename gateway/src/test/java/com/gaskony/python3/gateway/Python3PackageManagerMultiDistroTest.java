package com.gaskony.python3.gateway;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression tests for v4.5.1: package install/uninstall must target EVERY
 * installed Python distribution, not just the primary/default one.
 *
 * <p>Rather than actually invoking {@code pip} (slow, network-dependent, and not
 * something a unit test should do), {@link Python3PackageManager#executeProcess}
 * is overridden in a test subclass to record every command that would have been
 * run and return a canned success/failure per executable — a testable seam over
 * the real {@link ProcessBuilder} call.</p>
 */
class Python3PackageManagerMultiDistroTest {

    private Path tmpDir;

    @BeforeEach
    void setUp() throws IOException {
        tmpDir = Files.createTempDirectory("pkgmgr-multidistro-test");
    }

    @AfterEach
    void tearDown() throws IOException {
        try (var stream = Files.walk(tmpDir)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }

    /** Records every command handed to {@link Python3PackageManager#executeProcess}. */
    private static final class RecordingPackageManager extends Python3PackageManager {
        private final List<List<String>> recordedCommands = new ArrayList<>();
        private final Map<String, Boolean> successByExecutable;

        RecordingPackageManager(Path moduleDataDir, String pythonExecutable,
                java.util.function.Supplier<List<String>> allPythonExecutablesSupplier,
                Map<String, Boolean> successByExecutable) {
            super(moduleDataDir, pythonExecutable, allPythonExecutablesSupplier);
            this.successByExecutable = successByExecutable;
        }

        @Override
        PipResult executeProcess(List<String> command, long timeoutSeconds) {
            recordedCommands.add(command);
            String executable = command.get(0);
            boolean success = successByExecutable.getOrDefault(executable, true);
            return new PipResult(success, success ? "OK" : "simulated failure for " + executable);
        }

        List<List<String>> getRecordedCommands() {
            return recordedCommands;
        }
    }

    @Test
    void pipInstall_runsAgainstEveryInstalledDistribution_primaryFirst() {
        RecordingPackageManager mgr = new RecordingPackageManager(
                tmpDir, "python-primary",
                () -> List.of("python-primary", "python-3.11", "python-3.13"), // primary duplicated deliberately
                Map.of());

        Python3PackageManager.InstallResult result = mgr.pipInstallFromPyPI("six");

        assertThat(result.success).isTrue();
        assertThat(mgr.getInstalledPackages()).contains("six");

        // The duplicate primary entry from the supplier must be de-duplicated —
        // exactly one pip invocation per distinct distribution.
        List<List<String>> commands = mgr.getRecordedCommands();
        assertThat(commands).hasSize(3);
        assertThat(commands.get(0).get(0)).isEqualTo("python-primary");
        assertThat(commands.get(1).get(0)).isEqualTo("python-3.11");
        assertThat(commands.get(2).get(0)).isEqualTo("python-3.13");

        // Every command must still carry the pip-injection hardening (end-of-options marker).
        for (List<String> command : commands) {
            assertThat(command).containsSubsequence("-m", "pip", "install");
            assertThat(command).contains("--", "six");
        }
    }

    @Test
    void pipInstall_overallSuccessFollowsPrimaryOnly_secondaryFailureIsNotFatal() {
        Map<String, Boolean> outcomes = new HashMap<>();
        outcomes.put("python-primary", true);
        outcomes.put("python-3.13", false); // secondary has no matching wheel for this version, say

        RecordingPackageManager mgr = new RecordingPackageManager(
                tmpDir, "python-primary",
                () -> List.of("python-3.13"),
                outcomes);

        Python3PackageManager.InstallResult result = mgr.pipInstallFromPyPI("six");

        // Primary succeeded -> overall success, even though the secondary distribution failed.
        assertThat(result.success).isTrue();
        assertThat(mgr.getInstalledPackages()).contains("six");
        assertThat(mgr.getRecordedCommands()).hasSize(2);
    }

    @Test
    void pipInstall_failsOverallWhenPrimaryFails_evenIfSecondarySucceeds() {
        Map<String, Boolean> outcomes = new HashMap<>();
        outcomes.put("python-primary", false);
        outcomes.put("python-3.13", true);

        RecordingPackageManager mgr = new RecordingPackageManager(
                tmpDir, "python-primary",
                () -> List.of("python-3.13"),
                outcomes);

        Python3PackageManager.InstallResult result = mgr.pipInstallFromPyPI("six");

        assertThat(result.success).isFalse();
        assertThat(mgr.getInstalledPackages()).doesNotContain("six");
        // Both distributions were still attempted.
        assertThat(mgr.getRecordedCommands()).hasSize(2);
    }

    @Test
    void pipUninstall_runsAgainstEveryInstalledDistribution() {
        RecordingPackageManager mgr = new RecordingPackageManager(
                tmpDir, "python-primary",
                () -> List.of("python-3.11", "python-3.13"),
                Map.of());

        // Seed the package as installed first (skip a real install call).
        mgr.pipInstallFromPyPI("six");
        assertThat(mgr.getRecordedCommands()).hasSize(3); // install: primary + 2 distributions
        mgr.getRecordedCommands().clear();

        boolean uninstalled = mgr.pipUninstall("six");

        assertThat(uninstalled).isTrue();
        assertThat(mgr.getInstalledPackages()).doesNotContain("six");

        List<List<String>> commands = mgr.getRecordedCommands();
        assertThat(commands).hasSize(3);
        assertThat(commands.get(0).get(0)).isEqualTo("python-primary");
        assertThat(commands.get(1).get(0)).isEqualTo("python-3.11");
        assertThat(commands.get(2).get(0)).isEqualTo("python-3.13");
        for (List<String> command : commands) {
            assertThat(command).containsSubsequence("-m", "pip", "uninstall", "-y");
            assertThat(command).contains("--", "six");
        }
    }

    @Test
    void pipUninstall_secondaryNotInstalledIsIgnored_primaryStillDeterminesResult() {
        Map<String, Boolean> outcomes = new HashMap<>();
        outcomes.put("python-3.13", false); // e.g. never had the package installed there

        RecordingPackageManager mgr = new RecordingPackageManager(
                tmpDir, "python-primary",
                () -> List.of("python-3.13"),
                outcomes);

        boolean uninstalled = mgr.pipUninstall("six");

        assertThat(uninstalled).isTrue(); // primary defaults to success (not in outcomes map)
        assertThat(mgr.getRecordedCommands()).hasSize(2);
    }

    @Test
    void noSupplier_targetsOnlyThePrimaryDistribution_backwardCompatible() {
        RecordingPackageManager mgr = new RecordingPackageManager(
                tmpDir, "python-primary", null, Map.of());

        mgr.pipInstallFromPyPI("six");

        assertThat(mgr.getRecordedCommands()).hasSize(1);
        assertThat(mgr.getRecordedCommands().get(0).get(0)).isEqualTo("python-primary");
    }

    // ===== v4.5.2: uninstall symmetry for individually-installed PyPI packages =====

    /** Seed installed-packages.json so the manager reports {@code name} as installed. */
    private void seedInstalled(String name) throws IOException {
        Files.writeString(tmpDir.resolve("installed-packages.json"), "[\"" + name + "\"]");
    }

    @Test
    void uninstallPackage_nonCatalogPackage_usesPipUninstallAcrossAllDistributions() throws IOException {
        // "pandas" is NOT a catalogue bundle key (jedi/web/datascience) — it is an
        // individually-installed PyPI package recorded under its bare name. Before
        // v4.5.2 this returned false ("Failed to uninstall"); now it falls back to a
        // single-package pip uninstall across every installed distribution.
        seedInstalled("pandas");
        RecordingPackageManager mgr = new RecordingPackageManager(
                tmpDir, "python-primary",
                () -> List.of("python-3.11", "python-3.13"),
                Map.of());
        assertThat(mgr.isInstalled("pandas")).isTrue();

        boolean uninstalled = mgr.uninstallPackage("pandas");

        assertThat(uninstalled).isTrue();
        assertThat(mgr.getInstalledPackages()).doesNotContain("pandas");

        // One pip uninstall per distinct distribution, every command for "pandas".
        List<List<String>> commands = mgr.getRecordedCommands();
        assertThat(commands).hasSize(3);
        assertThat(commands.get(0).get(0)).isEqualTo("python-primary");
        assertThat(commands.get(1).get(0)).isEqualTo("python-3.11");
        assertThat(commands.get(2).get(0)).isEqualTo("python-3.13");
        for (List<String> command : commands) {
            assertThat(command).containsSubsequence("-m", "pip", "uninstall", "-y");
            assertThat(command).contains("--", "pandas");
        }
    }

    @Test
    void uninstallPackage_catalogBundle_usesBundlePath() throws IOException {
        // A real catalogue bundle key must still take the bundle path: every pip
        // package in the bundle is uninstalled (jedi bundle == [jedi, parso]),
        // across every installed distribution.
        seedInstalled("jedi");
        RecordingPackageManager mgr = new RecordingPackageManager(
                tmpDir, "python-primary",
                () -> List.of("python-3.13"),
                Map.of());
        assertThat(mgr.isInstalled("jedi")).isTrue();
        // Confirm the catalogue actually defines the jedi bundle we rely on.
        assertThat(mgr.getPackageCatalog()).containsKey("jedi");
        assertThat(mgr.getPackageCatalog().get("jedi").pipPackages)
                .containsExactlyInAnyOrder("jedi", "parso");

        boolean uninstalled = mgr.uninstallPackage("jedi");

        assertThat(uninstalled).isTrue();
        assertThat(mgr.getInstalledPackages()).doesNotContain("jedi");

        // 2 pip packages (jedi, parso) x 2 distributions (primary + 1 secondary) = 4 commands.
        List<List<String>> commands = mgr.getRecordedCommands();
        assertThat(commands).hasSize(4);
        List<String> uninstalledNames = new ArrayList<>();
        for (List<String> command : commands) {
            assertThat(command).containsSubsequence("-m", "pip", "uninstall", "-y");
            uninstalledNames.add(command.get(command.size() - 1)); // spec is the final arg
        }
        assertThat(uninstalledNames).containsExactlyInAnyOrder("jedi", "jedi", "parso", "parso");
    }
}
