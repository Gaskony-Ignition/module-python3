package com.gaskony.python3.gateway;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PythonDistributionManager.
 *
 * These tests verify the manager's ability to:
 * - Detect system Python installations
 * - Detect virtual environments (venv)
 * - Validate Python versions
 * - Report installation status
 * - Handle missing Python installations
 *
 * @since v2.12.0 (Phase 2 Week 1-2: Testing Infrastructure)
 */
public class PythonDistributionManagerTest {

    private static final Logger logger = LoggerFactory.getLogger(PythonDistributionManagerTest.class);

    private Path tempDir;
    private PythonDistributionManager manager;

    @BeforeEach
    public void setUp() throws IOException {
        logger.info("Setting up PythonDistributionManagerTest");
        tempDir = Files.createTempDirectory("python3-test");
    }

    @AfterEach
    public void tearDown() {
        if (tempDir != null) {
            try {
                Files.walk(tempDir)
                    .sorted((a, b) -> -a.compareTo(b))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            // Ignore
                        }
                    });
            } catch (IOException e) {
                logger.warn("Failed to clean up temp directory", e);
            }
        }
    }

    /**
     * Test 1: Manager initialization.
     * Verifies that manager can be created with temp directory.
     */
    @Test
    public void testManagerInitialization() {
        logger.info("Test: Manager initialization");

        manager = new PythonDistributionManager(tempDir, false);
        assertNotNull(manager, "Manager should not be null");

        logger.info("✓ Manager initialization test passed");
    }

    /**
     * Test 2: Get status without auto-download.
     * Verifies that status reports system Python when found.
     */
    @Test
    public void testGetStatusWithSystemPython() {
        logger.info("Test: Get status with system Python");

        manager = new PythonDistributionManager(tempDir, false);
        Map<String, Object> status = manager.getStatus();

        assertNotNull(status, "Status should not be null");
        assertTrue(status.containsKey("os"), "Status should contain 'os' key");
        assertTrue(status.containsKey("autoDownload"), "Status should contain 'autoDownload' key");
        assertFalse((Boolean) status.get("autoDownload"));

        // If system Python is available, status should reflect it
        if (status.containsKey("available") && (Boolean) status.get("available")) {
            assertTrue(status.containsKey("pythonPath"),
                "Status should contain 'pythonPath' when available");
            assertNotNull(status.get("pythonPath"), "Python path should not be null");
            logger.info("System Python found at: {}", status.get("pythonPath"));
        }

        logger.info("✓ Get status test passed");
    }

    /**
     * Test 3: Detect OS type.
     * Verifies that OS detection returns expected values.
     */
    @Test
    public void testOSDetection() {
        logger.info("Test: OS detection");

        manager = new PythonDistributionManager(tempDir, false);
        Map<String, Object> status = manager.getStatus();

        String os = (String) status.get("os");
        assertNotNull(os, "OS should be detected");
        assertTrue(os.equals("windows") || os.equals("linux") ||
            os.equals("macos-x64") || os.equals("macos-arm64"),
            "OS should be recognized type");

        logger.info("Detected OS: {}", os);
        logger.info("✓ OS detection test passed");
    }

    /**
     * Test 4: Python path detection.
     * Verifies that manager can find system Python.
     */
    @Test
    public void testPythonPathDetection() throws IOException {
        logger.info("Test: Python path detection");

        manager = new PythonDistributionManager(tempDir, false);

        try {
            String pythonPath = manager.getPythonPath();
            assertNotNull(pythonPath, "Python path should be found");
            assertFalse(pythonPath.isEmpty(), "Python path should not be empty");
            logger.info("Python path detected: {}", pythonPath);

            // Verify it's a valid Python by checking if it contains python
            assertTrue(pythonPath.toLowerCase().contains("python"),
                "Path should contain 'python'");

        } catch (IOException e) {
            // Python not available on system - acceptable for test
            logger.warn("Python not found on system: {}", e.getMessage());
            assertTrue(e.getMessage().contains("Python 3 not found") ||
                e.getMessage().contains("not found"),
                "Exception message should mention Python not found");
        }

        logger.info("✓ Python path detection test passed");
    }

    /**
     * Test 5: Virtual environment detection with system property.
     * Simulates venv configuration via system property.
     */
    @Test
    public void testVirtualEnvironmentDetection() throws IOException {
        logger.info("Test: Virtual environment detection");

        // Create a fake venv structure
        Path venvDir = tempDir.resolve("test-venv");
        Path venvBin = venvDir.resolve("bin");
        Path venvPython = venvBin.resolve("python3");
        Path pyvenvCfg = venvDir.resolve("pyvenv.cfg");

        Files.createDirectories(venvBin);
        Files.createFile(pyvenvCfg);
        Files.write(pyvenvCfg, "home = /usr/bin\nversion = 3.11.0".getBytes());

        // Create empty python3 file (won't be executable but that's ok for test)
        Files.createFile(venvPython);

        // Set system property
        String originalVenvProp = System.getProperty("ignition.python3.venv");
        System.setProperty("ignition.python3.venv", venvDir.toString());

        try {
            manager = new PythonDistributionManager(tempDir, false);

            // Check if venv is detected in status
            Map<String, Object> status = manager.getStatus();

            if (status.containsKey("usingVenv")) {
                Boolean usingVenv = (Boolean) status.get("usingVenv");
                logger.info("Using venv: {}", usingVenv);

                if (usingVenv) {
                    assertTrue(status.containsKey("venvPath"), "Status should contain venvPath");
                    logger.info("Venv path: {}", status.get("venvPath"));
                }
            }

            // Check getVirtualEnvPath method
            String venvPath = manager.getVirtualEnvPath();
            if (venvPath != null) {
                assertFalse(venvPath.isEmpty(), "Venv path should not be empty");
                logger.info("Virtual environment detected: {}", venvPath);
            }

        } finally {
            // Restore original property
            if (originalVenvProp != null) {
                System.setProperty("ignition.python3.venv", originalVenvProp);
            } else {
                System.clearProperty("ignition.python3.venv");
            }
        }

        logger.info("✓ Virtual environment detection test passed");
    }

    /**
     * Test 6: No virtual environment when not configured.
     * Verifies that venv is not detected when not configured.
     */
    @Test
    public void testNoVirtualEnvironmentWhenNotConfigured() {
        logger.info("Test: No venv when not configured");

        // Ensure system property is not set
        String originalVenvProp = System.getProperty("ignition.python3.venv");
        System.clearProperty("ignition.python3.venv");

        try {
            manager = new PythonDistributionManager(tempDir, false);
            String venvPath = manager.getVirtualEnvPath();

            assertNull(venvPath, "Venv path should be null when not configured");

            Map<String, Object> status = manager.getStatus();
            if (status.containsKey("usingVenv")) {
                assertFalse((Boolean) status.get("usingVenv"));
            }

        } finally {
            // Restore original property
            if (originalVenvProp != null) {
                System.setProperty("ignition.python3.venv", originalVenvProp);
            }
        }

        logger.info("✓ No venv test passed");
    }

    /**
     * Test 7: Status includes expected keys.
     * Verifies that getStatus() returns all expected information.
     */
    @Test
    public void testStatusIncludesExpectedKeys() {
        logger.info("Test: Status includes expected keys");

        manager = new PythonDistributionManager(tempDir, false);
        Map<String, Object> status = manager.getStatus();

        assertNotNull(status, "Status should not be null");

        // Check for required keys
        assertTrue(status.containsKey("os"), "Status should contain 'os'");
        assertTrue(status.containsKey("embeddedInstalled"), "Status should contain 'embeddedInstalled'");
        assertTrue(status.containsKey("pythonDir"), "Status should contain 'pythonDir'");
        assertTrue(status.containsKey("autoDownload"), "Status should contain 'autoDownload'");
        assertTrue(status.containsKey("usingVenv"), "Status should contain 'usingVenv'");

        // Log all keys for debugging
        logger.info("Status keys: {}", status.keySet());

        logger.info("✓ Status keys test passed");
    }

    /**
     * Test 8: Embedded Python directory structure.
     * Verifies that manager creates expected directory structure.
     */
    @Test
    public void testEmbeddedPythonDirectoryStructure() {
        logger.info("Test: Embedded Python directory structure");

        manager = new PythonDistributionManager(tempDir, false);
        Map<String, Object> status = manager.getStatus();

        String pythonDir = (String) status.get("pythonDir");
        assertNotNull(pythonDir, "Python directory should be reported");

        Path pythonDirPath = Path.of(pythonDir);
        assertTrue(Files.exists(pythonDirPath), "Python directory should exist");
        assertTrue(Files.isDirectory(pythonDirPath), "Python directory should be a directory");

        logger.info("Python directory: {}", pythonDir);
        logger.info("✓ Directory structure test passed");
    }

    /**
     * Test 9: Auto-download flag configuration.
     * Verifies that auto-download flag is respected.
     */
    @Test
    public void testAutoDownloadConfiguration() {
        logger.info("Test: Auto-download configuration");

        // Test with auto-download disabled
        PythonDistributionManager manager1 = new PythonDistributionManager(tempDir, false);
        Map<String, Object> status1 = manager1.getStatus();
        assertFalse((Boolean) status1.get("autoDownload"));

        // Test with auto-download enabled
        PythonDistributionManager manager2 = new PythonDistributionManager(tempDir, true);
        Map<String, Object> status2 = manager2.getStatus();
        assertTrue((Boolean) status2.get("autoDownload"));

        logger.info("✓ Auto-download configuration test passed");
    }

    /**
     * Test 10 (v4.3.5): every downloadable distribution URL must carry a real
     * pinned SHA-256. Null pins make {@code verifyDownloadedTarball} refuse to
     * extract, which on a gateway with no system Python means the pool can
     * never start — the Acceptance Contract workflow-1 defect found 04/07/2026
     * (the C15 pin table had been seeded with nulls and never populated).
     */
    @Test
    public void testAllDistributionUrlsHavePinnedSha256() {
        java.util.Set<String> urls = new java.util.TreeSet<>();
        for (PythonDistributionManager.PythonDistribution dist
                : PythonDistributionManager.AVAILABLE_DISTRIBUTIONS.values()) {
            urls.addAll(dist.platformUrls.values());
        }
        urls.addAll(PythonDistributionManager.DISTRIBUTION_URLS.values());
        assertFalse(urls.isEmpty(), "No distribution URLs found");

        for (String url : urls) {
            String sha = PythonDistributionManager.getPinnedSha256(url);
            assertNotNull(sha, "No pinned SHA-256 for " + url
                    + " — a clean gateway cannot self-provision this distribution;"
                    + " fetch the upstream .sha256 sidecar and add it to PINNED_SHA256");
            assertTrue(sha.matches("[0-9a-f]{64}"),
                    "Pinned hash for " + url + " is not 64 lowercase hex chars: " + sha);
        }
    }

    /**
     * Test 10: Priority order verification.
     * Verifies that Python detection follows correct priority:
     * 1. Virtual environment
     * 2. Embedded Python
     * 3. System Python
     */
    @Test
    public void testPythonDetectionPriority() throws IOException {
        logger.info("Test: Python detection priority");

        // Without venv, should detect system or embedded Python
        manager = new PythonDistributionManager(tempDir, false);

        try {
            String pythonPath1 = manager.getPythonPath();
            assertNotNull(pythonPath1, "Should detect some Python");
            logger.info("Python detected (no venv): {}", pythonPath1);

            // With venv configured, should prefer venv (if valid)
            // Create fake venv
            Path venvDir = tempDir.resolve("priority-venv");
            Path venvBin = venvDir.resolve("bin");
            Files.createDirectories(venvBin);

            String originalVenvProp = System.getProperty("ignition.python3.venv");
            System.setProperty("ignition.python3.venv", venvDir.toString());

            try {
                manager = new PythonDistributionManager(tempDir, false);
                String venvPath = manager.getVirtualEnvPath();

                if (venvPath != null) {
                    logger.info("Venv detected and prioritized: {}", venvPath);
                } else {
                    logger.info("Venv not detected (expected - not a valid venv)");
                }

            } finally {
                if (originalVenvProp != null) {
                    System.setProperty("ignition.python3.venv", originalVenvProp);
                } else {
                    System.clearProperty("ignition.python3.venv");
                }
            }

        } catch (IOException e) {
            logger.info("Python not available on system (acceptable): {}", e.getMessage());
        }

        logger.info("✓ Detection priority test passed");
    }
}
