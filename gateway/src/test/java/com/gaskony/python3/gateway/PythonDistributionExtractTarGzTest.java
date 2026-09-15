package com.gaskony.python3.gateway;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regression tests for the C15 fix to {@code PythonDistributionManager}:
 *
 * <ul>
 *   <li>tar-slip: malicious entry names with {@code ../} or absolute paths are rejected;</li>
 *   <li>symlink/hardlink entries with absolute or escaping targets are rejected,
 *       while in-tree relative links (required by real CPython distributions,
 *       v4.3.5) extract correctly;</li>
 *   <li>per-file size cap and total size cap are enforced;</li>
 *   <li>SHA-256 verification is mandatory by default;</li>
 *   <li>SHA-256 mismatch aborts extraction.</li>
 * </ul>
 *
 * <p>The tests build the input tarballs in-memory rather than fetching real
 * python-build-standalone artefacts so the suite stays hermetic and fast.
 *
 * @since v3.13.0 (C15 fix)
 */
class PythonDistributionExtractTarGzTest {

    private Path tempDir;
    private PythonDistributionManager manager;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("c15-test-");
        manager = new PythonDistributionManager(tempDir, false);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (tempDir != null && Files.exists(tempDir)) {
            try (var stream = Files.walk(tempDir)) {
                stream.sorted((a, b) -> -a.compareTo(b))
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                            // best effort
                        }
                    });
            }
        }
    }

    // ===== Tar-slip protection =====

    @Test
    @DisplayName("Tar slip via ../escape.sh is rejected")
    void tarSlip_relativeTraversal_isRejected() throws IOException {
        Path tarball = tempDir.resolve("malicious-relative.tar.gz");
        try (var out = new ByteArrayOutputStream();
             var gz = new GZIPOutputStream(out);
             TarArchiveOutputStream tar = new TarArchiveOutputStream(gz)) {
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
            byte[] payload = "rm -rf /".getBytes(StandardCharsets.UTF_8);
            TarArchiveEntry entry = new TarArchiveEntry("../../../etc/cron.d/escape.sh");
            entry.setSize(payload.length);
            tar.putArchiveEntry(entry);
            tar.write(payload);
            tar.closeArchiveEntry();
            tar.finish();
            gz.finish();
            Files.write(tarball, out.toByteArray());
        }

        Path destDir = Files.createDirectory(tempDir.resolve("dest"));
        assertThatThrownBy(() -> manager.extractTarGz(tarball, destDir))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("Tar slip");
    }

    @Test
    @DisplayName("Tar slip via deeply nested ../../.. traversal is rejected")
    void tarSlip_deepTraversal_isRejected() throws IOException {
        // commons-compress's TarArchiveOutputStream silently strips leading '/' from entry
        // names, so a true absolute-path attack can only originate from a tar tool we don't
        // control. Verify that the same defence catches an even deeper relative-traversal
        // payload (e.g. an attacker reaching the system bin directory).
        Path tarball = tempDir.resolve("malicious-deep.tar.gz");
        writeSingleFileTarball(tarball, "../../../../../../../../usr/local/bin/evil",
            "evil".getBytes(StandardCharsets.UTF_8));

        Path destDir = Files.createDirectory(tempDir.resolve("dest-deep"));
        assertThatThrownBy(() -> manager.extractTarGz(tarball, destDir))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("Tar slip");
    }

    // ===== Symlink / hardlink policy (v4.3.5: contained links allowed) =====

    @Test
    @DisplayName("Symlink entry with absolute target is rejected")
    void symlinkEntry_isRejected() throws IOException {
        Path tarball = tempDir.resolve("symlink.tar.gz");
        try (var bos = new ByteArrayOutputStream();
             var gz = new GZIPOutputStream(bos);
             TarArchiveOutputStream tar = new TarArchiveOutputStream(gz)) {
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
            TarArchiveEntry entry = new TarArchiveEntry("python/bin/python3", TarArchiveEntry.LF_SYMLINK);
            entry.setLinkName("/etc/passwd");
            tar.putArchiveEntry(entry);
            tar.closeArchiveEntry();
            tar.finish();
            gz.finish();
            Files.write(tarball, bos.toByteArray());
        }

        Path destDir = Files.createDirectory(tempDir.resolve("dest"));
        assertThatThrownBy(() -> manager.extractTarGz(tarball, destDir))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("Symbolic link");
    }

    @Test
    @DisplayName("Hardlink entry is rejected")
    void hardlinkEntry_isRejected() throws IOException {
        Path tarball = tempDir.resolve("hardlink.tar.gz");
        try (var bos = new ByteArrayOutputStream();
             var gz = new GZIPOutputStream(bos);
             TarArchiveOutputStream tar = new TarArchiveOutputStream(gz)) {
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
            TarArchiveEntry entry = new TarArchiveEntry("python/bin/link", TarArchiveEntry.LF_LINK);
            entry.setLinkName("/etc/passwd");
            tar.putArchiveEntry(entry);
            tar.closeArchiveEntry();
            tar.finish();
            gz.finish();
            Files.write(tarball, bos.toByteArray());
        }

        Path destDir = Files.createDirectory(tempDir.resolve("dest"));
        assertThatThrownBy(() -> manager.extractTarGz(tarball, destDir))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("Hard link");
    }

    @Test
    @DisplayName("In-tree relative symlink (CPython bin/2to3 layout) extracts")
    void symlinkEntry_containedRelativeTarget_extracts() throws IOException {
        // Mirrors the first entries of a real python-build-standalone tarball:
        // a regular file followed by a sibling symlink to it. The pre-v4.3.5
        // blanket link ban failed exactly here on every clean-gateway install.
        Path tarball = tempDir.resolve("cpython-layout.tar.gz");
        try (var bos = new ByteArrayOutputStream();
             var gz = new GZIPOutputStream(bos);
             TarArchiveOutputStream tar = new TarArchiveOutputStream(gz)) {
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
            byte[] payload = "#!/usr/bin/env python\n".getBytes(StandardCharsets.UTF_8);
            TarArchiveEntry file = new TarArchiveEntry("python/bin/2to3-3.11");
            file.setSize(payload.length);
            tar.putArchiveEntry(file);
            tar.write(payload);
            tar.closeArchiveEntry();
            TarArchiveEntry link = new TarArchiveEntry("python/bin/2to3", TarArchiveEntry.LF_SYMLINK);
            link.setLinkName("2to3-3.11");
            tar.putArchiveEntry(link);
            tar.closeArchiveEntry();
            tar.finish();
            gz.finish();
            Files.write(tarball, bos.toByteArray());
        }

        Path destDir = Files.createDirectory(tempDir.resolve("dest-cpython"));
        manager.extractTarGz(tarball, destDir);

        Path linkPath = destDir.resolve("python/bin/2to3");
        assertThat(Files.exists(linkPath)).isTrue();
        // Following the link must land on the sibling file's content
        assertThat(Files.readAllBytes(linkPath))
            .isEqualTo(Files.readAllBytes(destDir.resolve("python/bin/2to3-3.11")));
    }

    @Test
    @DisplayName("Symlink whose relative target escapes destDir is rejected")
    void symlinkEntry_escapingRelativeTarget_isRejected() throws IOException {
        Path tarball = tempDir.resolve("symlink-escape.tar.gz");
        try (var bos = new ByteArrayOutputStream();
             var gz = new GZIPOutputStream(bos);
             TarArchiveOutputStream tar = new TarArchiveOutputStream(gz)) {
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
            TarArchiveEntry link = new TarArchiveEntry("python/bin/evil", TarArchiveEntry.LF_SYMLINK);
            link.setLinkName("../../../../etc/passwd");
            tar.putArchiveEntry(link);
            tar.closeArchiveEntry();
            tar.finish();
            gz.finish();
            Files.write(tarball, bos.toByteArray());
        }

        Path destDir = Files.createDirectory(tempDir.resolve("dest-escape"));
        assertThatThrownBy(() -> manager.extractTarGz(tarball, destDir))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("escapes destination");
    }

    @Test
    @DisplayName("Hard link to an in-tree file extracts")
    void hardlinkEntry_containedTarget_extracts() throws IOException {
        Path tarball = tempDir.resolve("hardlink-ok.tar.gz");
        try (var bos = new ByteArrayOutputStream();
             var gz = new GZIPOutputStream(bos);
             TarArchiveOutputStream tar = new TarArchiveOutputStream(gz)) {
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
            byte[] payload = "data".getBytes(StandardCharsets.UTF_8);
            TarArchiveEntry file = new TarArchiveEntry("python/lib/original");
            file.setSize(payload.length);
            tar.putArchiveEntry(file);
            tar.write(payload);
            tar.closeArchiveEntry();
            TarArchiveEntry link = new TarArchiveEntry("python/lib/alias", TarArchiveEntry.LF_LINK);
            link.setLinkName("python/lib/original");
            tar.putArchiveEntry(link);
            tar.closeArchiveEntry();
            tar.finish();
            gz.finish();
            Files.write(tarball, bos.toByteArray());
        }

        Path destDir = Files.createDirectory(tempDir.resolve("dest-hardlink"));
        manager.extractTarGz(tarball, destDir);
        assertThat(Files.readAllBytes(destDir.resolve("python/lib/alias")))
            .isEqualTo("data".getBytes(StandardCharsets.UTF_8));
    }

    // ===== Size caps =====
    //
    // To avoid building a 100 MB+ tarball fixture, the tests below temporarily
    // lower the static caps to a trivial value (1 KB) for the duration of the
    // test, then restore the production defaults in a finally block. The
    // production caps are also asserted to sensible ranges as a guard against
    // accidental zeroing.

    @Test
    @DisplayName("Size cap constants are set to sensible production values")
    void sizeCapConstants_areSensible() {
        assertThat(PythonDistributionManager.MAX_PER_FILE_BYTES)
            .as("Per-file cap must allow real Python binaries (≥10 MB) but cap zip-bombs (<1 GB)")
            .isBetween(10L * 1024 * 1024, 1024L * 1024 * 1024);
        assertThat(PythonDistributionManager.MAX_UNCOMPRESSED_TOTAL_BYTES)
            .as("Total cap must accommodate full python-build-standalone tarballs (≥250 MB) but cap DoS (<2 GB)")
            .isBetween(250L * 1024 * 1024, 2L * 1024L * 1024 * 1024);
        assertThat(PythonDistributionManager.MAX_PER_FILE_BYTES)
            .isLessThanOrEqualTo(PythonDistributionManager.MAX_UNCOMPRESSED_TOTAL_BYTES);
    }

    @Test
    @DisplayName("Per-file size cap rejects a single oversized declared entry")
    void perFileSizeCap_isEnforced() throws IOException {
        long savedPerFile = PythonDistributionManager.MAX_PER_FILE_BYTES;
        long savedTotal = PythonDistributionManager.MAX_UNCOMPRESSED_TOTAL_BYTES;
        try {
            // Lower the cap to 1 KB; the tar entry below will declare 2 KB and be rejected.
            PythonDistributionManager.MAX_PER_FILE_BYTES = 1024L;
            PythonDistributionManager.MAX_UNCOMPRESSED_TOTAL_BYTES = 1024L * 1024L; // 1 MB total cap, irrelevant for this test

            Path tarball = tempDir.resolve("oversize-file.tar.gz");
            byte[] payload = new byte[2048]; // 2 KB
            writeSingleFileTarball(tarball, "python/big.bin", payload);

            Path destDir = Files.createDirectory(tempDir.resolve("dest-perfile"));
            assertThatThrownBy(() -> manager.extractTarGz(tarball, destDir))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("per-file size cap");
        } finally {
            PythonDistributionManager.MAX_PER_FILE_BYTES = savedPerFile;
            PythonDistributionManager.MAX_UNCOMPRESSED_TOTAL_BYTES = savedTotal;
        }
    }

    @Test
    @DisplayName("Total size cap rejects when sum of declared entries exceeds cap")
    void totalSizeCap_isEnforced_acrossEntries() throws IOException {
        long savedPerFile = PythonDistributionManager.MAX_PER_FILE_BYTES;
        long savedTotal = PythonDistributionManager.MAX_UNCOMPRESSED_TOTAL_BYTES;
        try {
            // Generous per-file cap so it doesn't fire first; tight total cap.
            PythonDistributionManager.MAX_PER_FILE_BYTES = 10L * 1024L; // 10 KB
            PythonDistributionManager.MAX_UNCOMPRESSED_TOTAL_BYTES = 5L * 1024L; // 5 KB

            Path tarball = tempDir.resolve("oversize-total.tar.gz");
            try (var bos = new ByteArrayOutputStream();
                 var gz = new GZIPOutputStream(bos);
                 TarArchiveOutputStream tar = new TarArchiveOutputStream(gz)) {
                tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
                // Ten entries × 1 KB declared = 10 KB total → exceeds 5 KB total cap.
                for (int i = 0; i < 10; i++) {
                    byte[] payload = new byte[1024];
                    TarArchiveEntry entry = new TarArchiveEntry("python/file_" + i + ".bin");
                    entry.setSize(payload.length);
                    tar.putArchiveEntry(entry);
                    tar.write(payload);
                    tar.closeArchiveEntry();
                }
                tar.finish();
                gz.finish();
                Files.write(tarball, bos.toByteArray());
            }

            Path destDir = Files.createDirectory(tempDir.resolve("dest-total"));
            assertThatThrownBy(() -> manager.extractTarGz(tarball, destDir))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("total uncompressed size cap");
        } finally {
            PythonDistributionManager.MAX_PER_FILE_BYTES = savedPerFile;
            PythonDistributionManager.MAX_UNCOMPRESSED_TOTAL_BYTES = savedTotal;
        }
    }

    // ===== Happy path =====

    @Test
    @DisplayName("Well-formed small tarball extracts successfully")
    void wellFormedTarball_extractsCleanly() throws IOException {
        Path tarball = tempDir.resolve("clean.tar.gz");
        writeSingleFileTarball(tarball, "python/bin/script.sh",
            "#!/bin/sh\necho hi\n".getBytes(StandardCharsets.UTF_8));

        Path destDir = Files.createDirectory(tempDir.resolve("dest"));
        manager.extractTarGz(tarball, destDir);

        Path extracted = destDir.resolve("python/bin/script.sh");
        assertThat(Files.exists(extracted)).isTrue();
        assertThat(new String(Files.readAllBytes(extracted), StandardCharsets.UTF_8))
            .startsWith("#!/bin/sh");
    }

    // ===== SHA-256 verification =====

    @Test
    @DisplayName("verifySha256 accepts a matching hash")
    void verifySha256_matching_passes() throws IOException {
        Path file = tempDir.resolve("sample.bin");
        Files.write(file, "hello world".getBytes(StandardCharsets.UTF_8));
        // pre-computed sha256 of "hello world"
        String expected = "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9";

        // No exception → success
        PythonDistributionManager.verifySha256(file, expected);
    }

    @Test
    @DisplayName("verifySha256 rejects a mismatching hash")
    void verifySha256_mismatching_throws() throws IOException {
        Path file = tempDir.resolve("sample.bin");
        Files.write(file, "hello world".getBytes(StandardCharsets.UTF_8));
        String wrongHash = "0000000000000000000000000000000000000000000000000000000000000000";

        assertThatThrownBy(() -> PythonDistributionManager.verifySha256(file, wrongHash))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("SHA-256 mismatch");
    }

    @Test
    @DisplayName("verifySha256 refuses null/blank expected hash")
    void verifySha256_nullHash_throws() throws IOException {
        Path file = tempDir.resolve("sample.bin");
        Files.write(file, "hello world".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> PythonDistributionManager.verifySha256(file, null))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("no SHA-256 hash configured");
        assertThatThrownBy(() -> PythonDistributionManager.verifySha256(file, "   "))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("no SHA-256 hash configured");
    }

    @Test
    @DisplayName("verifyDownloadedTarball refuses by default when no pin exists")
    void verifyDownloadedTarball_noPin_refuses() throws IOException {
        Path file = tempDir.resolve("download.bin");
        Files.write(file, "payload".getBytes(StandardCharsets.UTF_8));
        String unknownUrl = "https://example.test/never-pinned.tar.gz";
        // Ensure skipChecksum is OFF
        String previous = System.getProperty("ignition.python3.skipChecksum");
        System.clearProperty("ignition.python3.skipChecksum");
        try {
            assertThatThrownBy(() -> manager.verifyDownloadedTarball(file, unknownUrl))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("No pinned SHA-256");
        } finally {
            if (previous != null) System.setProperty("ignition.python3.skipChecksum", previous);
        }
    }

    @Test
    @DisplayName("verifyDownloadedTarball with skipChecksum=true logs warning and proceeds")
    void verifyDownloadedTarball_skipChecksumOptIn_proceeds() throws IOException {
        Path file = tempDir.resolve("download.bin");
        Files.write(file, "payload".getBytes(StandardCharsets.UTF_8));
        String unknownUrl = "https://example.test/never-pinned.tar.gz";
        String previous = System.getProperty("ignition.python3.skipChecksum");
        System.setProperty("ignition.python3.skipChecksum", "true");
        try {
            // No exception expected — skipping is explicitly opted in.
            manager.verifyDownloadedTarball(file, unknownUrl);
        } finally {
            if (previous != null) System.setProperty("ignition.python3.skipChecksum", previous);
            else System.clearProperty("ignition.python3.skipChecksum");
        }
    }

    // ===== helpers =====

    private static void writeSingleFileTarball(Path tarballPath, String entryName, byte[] payload) throws IOException {
        try (var bos = new ByteArrayOutputStream();
             var gz = new GZIPOutputStream(bos);
             TarArchiveOutputStream tar = new TarArchiveOutputStream(gz)) {
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
            TarArchiveEntry entry = new TarArchiveEntry(entryName);
            entry.setSize(payload.length);
            tar.putArchiveEntry(entry);
            tar.write(payload);
            tar.closeArchiveEntry();
            tar.finish();
            gz.finish();
            Files.write(tarballPath, bos.toByteArray());
        }
    }
}
