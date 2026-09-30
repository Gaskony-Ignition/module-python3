package com.gaskony.python3.gateway;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the v4.5.0 file-backed {@link Python3ScriptRepository}: per-script
 * {@code .py}+{@code .meta.json} storage, legacy {@code index.json} migration, and
 * hot-reload of externally dropped/edited files (the "edit files on the gateway,
 * no restart" behaviour).
 */
class Python3ScriptRepositoryFileBackedTest {

    private Path base;
    private Python3ScriptRepository repo;

    @BeforeEach
    void setUp() throws IOException {
        base = Files.createTempDirectory("py3-repo-test");
    }

    @AfterEach
    void tearDown() throws IOException {
        if (repo != null) {
            repo.close();
        }
        if (base != null && Files.exists(base)) {
            try (var s = Files.walk(base)) {
                s.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }

    @Test
    void saveWritesEditableFilesAndRoundTrips() throws IOException {
        repo = new Python3ScriptRepository(base);
        repo.saveScript("SensorStats", "result = 42", "demo", "nigel", "Demos", "1.0");

        Path py = base.resolve("scripts/Demos/SensorStats.py");
        Path meta = base.resolve("scripts/Demos/SensorStats.meta.json");
        assertThat(py).exists();
        assertThat(meta).exists();
        assertThat(Files.readString(py)).isEqualTo("result = 42");

        var loaded = repo.loadScriptByPath("Demos/SensorStats");
        assertThat(loaded).isNotNull();
        assertThat(loaded.getCode()).isEqualTo("result = 42");
        assertThat(loaded.getFolderPath()).isEqualTo("Demos");
        assertThat(loaded.getAuthor()).isEqualTo("nigel");
        assertThat(repo.getScriptCount()).isEqualTo(1);
    }

    @Test
    void deleteRemovesFiles() throws IOException {
        repo = new Python3ScriptRepository(base);
        repo.saveScript("Temp", "x = 1", "", "a", "", "1.0");
        assertThat(repo.exists("Temp")).isTrue();

        assertThat(repo.deleteScript("Temp")).isTrue();
        assertThat(repo.exists("Temp")).isFalse();
        assertThat(base.resolve("scripts/Temp.py")).doesNotExist();
    }

    @Test
    void migratesLegacyIndexJsonToFiles() throws IOException {
        // Pre-seed a legacy index.json before the repository is constructed.
        Path scriptsDir = Files.createDirectories(base.resolve("scripts"));
        String legacy = "{\"myfirstscript\":{"
                + "\"id\":\"myfirstscript\",\"name\":\"MyfirstScript\",\"code\":\"print('hi')\","
                + "\"description\":\"legacy\",\"author\":\"old\",\"createdDate\":\"2026-01-01T00:00:00Z\","
                + "\"lastModified\":\"2026-01-01T00:00:00Z\",\"folderPath\":\"TestFolder1\",\"version\":\"1.0\"}}";
        Files.writeString(scriptsDir.resolve("index.json"), legacy);

        repo = new Python3ScriptRepository(base);

        assertThat(base.resolve("scripts/TestFolder1/MyfirstScript.py")).exists();
        assertThat(Files.readString(base.resolve("scripts/TestFolder1/MyfirstScript.py")))
                .isEqualTo("print('hi')");
        assertThat(repo.loadScriptByPath("TestFolder1/MyfirstScript")).isNotNull();
        // Old index is renamed aside, not left to re-migrate.
        assertThat(scriptsDir.resolve("index.json")).doesNotExist();
        try (var s = Files.list(scriptsDir)) {
            assertThat(s.anyMatch(p -> p.getFileName().toString().startsWith("index.json.migrated-"))).isTrue();
        }
    }

    @Test
    void hotReloadsAnExternallyDroppedFile() throws IOException, InterruptedException {
        repo = new Python3ScriptRepository(base);
        assertThat(repo.getScriptCount()).isZero();

        // Simulate a user dropping a .py straight onto the gateway filesystem.
        Path folder = Files.createDirectories(base.resolve("scripts/Dropped"));
        Files.writeString(folder.resolve("Hello.py"), "result = 'dropped'");

        // Watcher debounces ~250ms; poll up to ~6s.
        boolean appeared = false;
        for (int i = 0; i < 60 && !appeared; i++) {
            Thread.sleep(100);
            appeared = repo.exists("Hello");
        }
        assertThat(appeared).as("externally dropped script picked up without a restart").isTrue();
        assertThat(repo.loadScriptByPath("Dropped/Hello").getCode()).isEqualTo("result = 'dropped'");
    }
}
