package com.gaskony.python3.gateway;

import com.inductiveautomation.ignition.common.gson.Gson;
import com.inductiveautomation.ignition.common.gson.GsonBuilder;
import com.inductiveautomation.ignition.common.gson.JsonObject;
import com.inductiveautomation.ignition.common.gson.JsonParser;
import com.inductiveautomation.ignition.common.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Manages saved Python scripts for the Python 3 Integration module.
 *
 * <p><b>Storage (v4.5.0 — file-backed):</b> each script is a plain, human-editable
 * {@code <Name>.py} file under {@code <dataDir>/python3-integration/scripts/},
 * laid out in folder subdirectories that mirror the script's folder path, with a
 * small sibling {@code <Name>.meta.json} sidecar holding description / author /
 * created-date / version. A {@link WatchService} watches the tree and hot-reloads
 * on any change, so editing a {@code .py} file directly on the gateway (or dropping
 * a new one in) shows up in the Designer within a second — no module restart.</p>
 *
 * <p>The store stays <b>gateway-global</b> (one repository shared by every project
 * and Designer, callable via {@code system.python3.callScript}); it is deliberately
 * not an Ignition project resource. The legacy single {@code index.json} blob is
 * migrated to the per-file layout automatically on first startup.</p>
 *
 * <p>Because the {@code .py} file is now the source of truth and filesystem write
 * access is the trust boundary, the HMAC signature is recomputed from the file's
 * current contents on load — so a hand-edited script always verifies. The signature
 * remains in the API/RPC responses for compatibility.</p>
 */
public class Python3ScriptRepository {

    private static final Logger logger = LoggerFactory.getLogger(Python3ScriptRepository.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String PY_EXT = ".py";
    private static final String META_EXT = ".meta.json";

    private final Path scriptsDirectory;
    private final Path legacyIndexFile;

    /** Replaced atomically on each reload; reads always see a consistent snapshot. */
    private volatile Map<String, SavedScript> scriptIndex = new HashMap<>();

    private WatchService watchService;
    private Thread watcherThread;
    private volatile boolean watching;

    /**
     * Creates a new script repository and starts watching for on-disk changes.
     *
     * @param baseDirectory the base directory for script storage
     * @throws IOException if the directory cannot be created
     */
    public Python3ScriptRepository(Path baseDirectory) throws IOException {
        this.scriptsDirectory = baseDirectory.resolve("scripts");
        this.legacyIndexFile = scriptsDirectory.resolve("index.json");

        Files.createDirectories(scriptsDirectory);

        migrateLegacyIndexIfPresent();
        reloadFromDisk();
        startWatcher();

        logger.info("Python3ScriptRepository initialized at: {} ({} scripts, file-backed, hot-reload on)",
                scriptsDirectory, scriptIndex.size());
    }

    // ========================================================================
    // Public API (unchanged signatures — RPC/REST/nav-tree depend on these)
    // ========================================================================

    /**
     * Saves a Python script to its {@code .py} file plus a {@code .meta.json} sidecar.
     */
    public SavedScript saveScript(String name, String code, String description, String author,
                                  String folderPath, String version) throws IOException {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Script name cannot be empty");
        }
        String cleanName = name.trim();
        String cleanFolder = normalizeFolder(folderPath);
        String now = Instant.now().toString();

        SavedScript existing = loadScriptByPath(cleanFolder.isEmpty() ? cleanName : cleanFolder + "/" + cleanName);
        String createdDate = existing != null ? existing.getCreatedDate() : now;
        String auth = author != null ? author : "Unknown";
        String ver = version != null ? version : "1.0";

        Path dir = resolveFolder(cleanFolder);
        Files.createDirectories(dir);
        Path pyFile = dir.resolve(fileBase(cleanName) + PY_EXT);
        Path metaFile = dir.resolve(fileBase(cleanName) + META_EXT);

        Files.writeString(pyFile, code == null ? "" : code,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        JsonObject meta = new JsonObject();
        meta.addProperty("name", cleanName);
        meta.addProperty("description", description == null ? "" : description);
        meta.addProperty("author", auth);
        meta.addProperty("createdDate", createdDate);
        meta.addProperty("version", ver);
        Files.writeString(metaFile, GSON.toJson(meta),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        reloadFromDisk();
        logger.info("Script saved: {} in folder: '{}' -> {}", cleanName, cleanFolder, pyFile.getFileName());
        return loadScript(cleanName);
    }

    public SavedScript saveScript(String name, String code, String description) throws IOException {
        return saveScript(name, code, description, "Unknown", "", "1.0");
    }

    /**
     * Loads a saved script by name. Signature verification is preserved for
     * compatibility; in file-backed mode the signature is recomputed on load, so a
     * hand-edited script verifies cleanly (the file is the source of truth).
     */
    public SavedScript loadScript(String name) {
        String sanitizedName = sanitizeName(name);
        SavedScript script = scriptIndex.get(sanitizedName);
        if (script == null) {
            logger.warn("Script not found: {}", name);
            return null;
        }

        boolean enforceSignatures = Boolean.parseBoolean(
                System.getProperty("ignition.python3.enforce.signatures", "false"));
        if (script.getSignature() != null) {
            boolean valid = Python3ScriptSigner.verifyScript(script.getCode(), script.getSignature());
            if (!valid) {
                if (enforceSignatures) {
                    logger.error("SECURITY: Script signature verification FAILED for: {} - possible tampering!", name);
                    throw new SecurityException("Script signature verification failed for: " + name);
                }
                logger.warn("SECURITY: Script signature mismatch for {} (enforcement disabled)", name);
            }
        }
        return script;
    }

    /**
     * Loads a saved script by path (supports folder hierarchy), e.g.
     * {@code "Folder/Sub/My Script"}; a leading slash is optional.
     */
    public SavedScript loadScriptByPath(String scriptPath) {
        if (scriptPath == null || scriptPath.trim().isEmpty()) {
            logger.warn("Script path is empty");
            return null;
        }
        String normalizedPath = scriptPath.replaceAll("^/+|/+$", "").trim();
        String folderPath;
        String scriptName;
        int lastSlash = normalizedPath.lastIndexOf('/');
        if (lastSlash == -1) {
            folderPath = "";
            scriptName = normalizedPath;
        } else {
            folderPath = normalizedPath.substring(0, lastSlash);
            scriptName = normalizedPath.substring(lastSlash + 1);
        }

        Map<String, SavedScript> snapshot = scriptIndex;
        for (SavedScript script : snapshot.values()) {
            String f = script.getFolderPath() != null ? script.getFolderPath() : "";
            if (script.getName().equals(scriptName) && f.equals(folderPath)) {
                return script;
            }
        }
        for (SavedScript script : snapshot.values()) {
            String f = script.getFolderPath() != null ? script.getFolderPath() : "";
            if (script.getName().equalsIgnoreCase(scriptName) && f.equalsIgnoreCase(folderPath)) {
                return script;
            }
        }
        logger.warn("Script not found by path: {}", scriptPath);
        return null;
    }

    public List<ScriptMetadata> listScripts() {
        return scriptIndex.values().stream()
                .map(s -> new ScriptMetadata(s.getId(), s.getName(), s.getDescription(),
                        s.getAuthor(), s.getCreatedDate(), s.getLastModified(),
                        s.getFolderPath(), s.getVersion()))
                .collect(Collectors.toList());
    }

    /** Deletes a saved script's {@code .py} and {@code .meta.json} files. */
    public boolean deleteScript(String name) throws IOException {
        SavedScript script = scriptIndex.get(sanitizeName(name));
        if (script == null) {
            logger.warn("Script not found for deletion: {}", name);
            return false;
        }
        Path dir = resolveFolder(script.getFolderPath());
        Files.deleteIfExists(dir.resolve(fileBase(script.getName()) + PY_EXT));
        Files.deleteIfExists(dir.resolve(fileBase(script.getName()) + META_EXT));
        reloadFromDisk();
        logger.info("Script deleted: {}", name);
        return true;
    }

    public boolean exists(String name) {
        return scriptIndex.containsKey(sanitizeName(name));
    }

    public int getScriptCount() {
        return scriptIndex.size();
    }

    /** Stops the filesystem watcher. Call from the module shutdown path. */
    public void close() {
        watching = false;
        if (watcherThread != null) {
            watcherThread.interrupt();
        }
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                logger.debug("Error closing script watch service: {}", e.getMessage());
            }
        }
        logger.info("Python3ScriptRepository watcher stopped");
    }

    // ========================================================================
    // Disk <-> memory
    // ========================================================================

    /** Rebuilds the in-memory index by scanning every {@code .py} file under the tree. */
    private synchronized void reloadFromDisk() {
        Map<String, SavedScript> rebuilt = new HashMap<>();
        try {
            if (Files.exists(scriptsDirectory)) {
                try (var stream = Files.walk(scriptsDirectory)) {
                    stream.filter(p -> p.toString().endsWith(PY_EXT))
                          .forEach(py -> {
                              try {
                                  SavedScript s = readScriptFile(py);
                                  if (s != null) {
                                      rebuilt.put(sanitizeName(s.getName()), s);
                                  }
                              } catch (Exception e) {
                                  logger.warn("Skipping unreadable script {}: {}", py, e.getMessage());
                              }
                          });
                }
            }
        } catch (IOException e) {
            logger.error("Failed to scan scripts directory {}", scriptsDirectory, e);
        }
        this.scriptIndex = rebuilt;
    }

    /** Reads one {@code .py} file (+ optional {@code .meta.json}) into a SavedScript. */
    private SavedScript readScriptFile(Path pyFile) throws IOException {
        String fileBase = pyFile.getFileName().toString();
        fileBase = fileBase.substring(0, fileBase.length() - PY_EXT.length());

        Path rel = scriptsDirectory.relativize(pyFile.getParent());
        String folderPath = rel.toString().replace('\\', '/');
        if (folderPath.equals(".")) {
            folderPath = "";
        }

        String code = Files.readString(pyFile);

        String name = fileBase;
        String description = "";
        String author = "Unknown";
        String createdDate = fileTime(pyFile);
        String version = "1.0";

        Path metaFile = pyFile.getParent().resolve(fileBase + META_EXT);
        if (Files.exists(metaFile)) {
            try {
                JsonObject meta = JsonParser.parseString(Files.readString(metaFile)).getAsJsonObject();
                if (meta.has("name")) {
                    name = meta.get("name").getAsString();
                }
                if (meta.has("description")) {
                    description = meta.get("description").getAsString();
                }
                if (meta.has("author")) {
                    author = meta.get("author").getAsString();
                }
                if (meta.has("createdDate")) {
                    createdDate = meta.get("createdDate").getAsString();
                }
                if (meta.has("version")) {
                    version = meta.get("version").getAsString();
                }
            } catch (Exception e) {
                logger.warn("Bad meta for {}, using defaults: {}", pyFile.getFileName(), e.getMessage());
            }
        }

        // File is the source of truth: derive a fresh signature so hand-edits verify.
        String signature = Python3ScriptSigner.signScript(code);
        return new SavedScript(sanitizeName(name), name, code, description, author,
                createdDate, fileTime(pyFile), folderPath, version, signature);
    }

    private String fileTime(Path p) {
        try {
            return Files.getLastModifiedTime(p).toInstant().toString();
        } catch (IOException e) {
            return Instant.now().toString();
        }
    }

    /**
     * One-time migration of the legacy {@code index.json} blob to per-file storage.
     * Existing files are never overwritten; the old index is renamed aside afterwards.
     */
    private void migrateLegacyIndexIfPresent() {
        if (!Files.exists(legacyIndexFile)) {
            return;
        }
        try {
            String json = Files.readString(legacyIndexFile);
            Map<String, SavedScript> loaded = GSON.fromJson(
                    json, new TypeToken<Map<String, SavedScript>>() {}.getType());
            int migrated = 0;
            if (loaded != null) {
                for (SavedScript s : loaded.values()) {
                    String folder = normalizeFolder(s.getFolderPath());
                    Path dir = resolveFolder(folder);
                    Files.createDirectories(dir);
                    Path pyFile = dir.resolve(fileBase(s.getName()) + PY_EXT);
                    Path metaFile = dir.resolve(fileBase(s.getName()) + META_EXT);
                    if (Files.exists(pyFile)) {
                        continue;  // already migrated / newer file present
                    }
                    Files.writeString(pyFile, s.getCode() == null ? "" : s.getCode(),
                            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                    JsonObject meta = new JsonObject();
                    meta.addProperty("name", s.getName());
                    meta.addProperty("description", s.getDescription() == null ? "" : s.getDescription());
                    meta.addProperty("author", s.getAuthor() == null ? "Unknown" : s.getAuthor());
                    meta.addProperty("createdDate", s.getCreatedDate() == null
                            ? Instant.now().toString() : s.getCreatedDate());
                    meta.addProperty("version", s.getVersion() == null ? "1.0" : s.getVersion());
                    Files.writeString(metaFile, GSON.toJson(meta),
                            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                    migrated++;
                }
            }
            Path archived = scriptsDirectory.resolve("index.json.migrated-" + System.currentTimeMillis());
            Files.move(legacyIndexFile, archived);
            logger.info("Migrated {} script(s) from legacy index.json to per-file storage; old index kept at {}",
                    migrated, archived.getFileName());
        } catch (Exception e) {
            logger.error("Legacy index migration failed (leaving index.json in place)", e);
        }
    }

    // ========================================================================
    // Filesystem watcher
    // ========================================================================

    private void startWatcher() {
        try {
            watchService = scriptsDirectory.getFileSystem().newWatchService();
            registerAll(scriptsDirectory);
            watching = true;
            watcherThread = new Thread(this::watchLoop, "python3-scripts-watcher");
            watcherThread.setDaemon(true);
            watcherThread.start();
        } catch (IOException e) {
            logger.warn("Could not start script filesystem watcher (edits will need a module reload): {}",
                    e.getMessage());
        }
    }

    private void registerAll(Path root) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                dir.register(watchService,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY,
                        StandardWatchEventKinds.ENTRY_DELETE);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void watchLoop() {
        while (watching) {
            WatchKey key;
            try {
                key = watchService.take();
            } catch (InterruptedException | java.nio.file.ClosedWatchServiceException e) {
                return;
            }

            boolean relevant = false;
            boolean newDir = false;
            for (WatchEvent<?> event : key.pollEvents()) {
                Object ctx = event.context();
                if (!(ctx instanceof Path)) {
                    continue;
                }
                Path changed = ((Path) key.watchable()).resolve((Path) ctx);
                String n = changed.getFileName().toString();
                if (n.endsWith(PY_EXT) || n.endsWith(META_EXT)) {
                    relevant = true;
                }
                if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE && Files.isDirectory(changed)) {
                    newDir = true;
                }
            }
            key.reset();

            if (newDir) {
                try {
                    registerAll(scriptsDirectory);  // pick up new subfolders
                } catch (IOException e) {
                    logger.debug("Re-register after new dir failed: {}", e.getMessage());
                }
                relevant = true;
            }

            if (relevant) {
                // Debounce: editors often fire several events per save.
                try {
                    TimeUnit.MILLISECONDS.sleep(250);
                } catch (InterruptedException e) {
                    return;
                }
                // Drain any events that arrived during the debounce window.
                WatchKey extra;
                while ((extra = watchService.poll()) != null) {
                    extra.pollEvents();
                    extra.reset();
                }
                int before = scriptIndex.size();
                reloadFromDisk();
                logger.info("Reloaded scripts from disk after change ({} -> {})", before, scriptIndex.size());
            }
        }
    }

    // ========================================================================
    // Path helpers
    // ========================================================================

    /** Normalises a folder path: strips slashes, rejects traversal. */
    private String normalizeFolder(String folderPath) {
        if (folderPath == null) {
            return "";
        }
        String f = folderPath.replace('\\', '/').replaceAll("^/+|/+$", "").trim();
        if (f.contains("..")) {
            throw new IllegalArgumentException("Invalid folder path: " + folderPath);
        }
        return f;
    }

    /** Resolves a folder path to an absolute directory inside the scripts root. */
    private Path resolveFolder(String folderPath) {
        String f = normalizeFolder(folderPath);
        Path dir = f.isEmpty() ? scriptsDirectory : scriptsDirectory.resolve(f);
        Path normalized = dir.normalize();
        if (!normalized.startsWith(scriptsDirectory)) {
            throw new IllegalArgumentException("Folder escapes scripts directory: " + folderPath);
        }
        return normalized;
    }

    /** Filesystem-safe base name (keeps case/spaces; replaces illegal chars). */
    private String fileBase(String name) {
        return name.trim().replaceAll("[^a-zA-Z0-9 _.\\-]", "_");
    }

    private String sanitizeName(String name) {
        if (name == null) {
            return "unnamed";
        }
        return name.trim().toLowerCase()
                .replaceAll("[^a-z0-9_-]", "_")
                .replaceAll("_+", "_");
    }

    // ========================================================================
    // Data classes (unchanged)
    // ========================================================================

    public static class SavedScript {
        private final String id;
        private final String name;
        private final String code;
        private final String description;
        private final String author;
        private final String createdDate;
        private final String lastModified;
        private final String folderPath;
        private final String version;
        private final String signature;

        public SavedScript(String id, String name, String code, String description,
                           String author, String createdDate, String lastModified,
                           String folderPath, String version, String signature) {
            this.id = id;
            this.name = name;
            this.code = code;
            this.description = description;
            this.author = author;
            this.createdDate = createdDate;
            this.lastModified = lastModified;
            this.folderPath = folderPath;
            this.version = version;
            this.signature = signature;
        }

        public SavedScript(String id, String name, String code, String description,
                           String author, String createdDate, String lastModified,
                           String folderPath, String version) {
            this(id, name, code, description, author, createdDate, lastModified,
                    folderPath, version, null);
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public String getCode() { return code; }
        public String getDescription() { return description; }
        public String getAuthor() { return author; }
        public String getCreatedDate() { return createdDate; }
        public String getLastModified() { return lastModified; }
        public String getFolderPath() { return folderPath; }
        public String getVersion() { return version; }
        public String getSignature() { return signature; }
    }

    public static class ScriptMetadata {
        private final String id;
        private final String name;
        private final String description;
        private final String author;
        private final String createdDate;
        private final String lastModified;
        private final String folderPath;
        private final String version;

        public ScriptMetadata(String id, String name, String description,
                              String author, String createdDate, String lastModified,
                              String folderPath, String version) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.author = author;
            this.createdDate = createdDate;
            this.lastModified = lastModified;
            this.folderPath = folderPath;
            this.version = version;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public String getAuthor() { return author; }
        public String getCreatedDate() { return createdDate; }
        public String getLastModified() { return lastModified; }
        public String getFolderPath() { return folderPath; }
        public String getVersion() { return version; }
    }
}
