package com.gaskony.python3.gateway;

import com.inductiveautomation.ignition.common.gson.Gson;
import com.inductiveautomation.ignition.common.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Audit logger for Python code execution events.
 * Logs all Python executions to both SLF4J logger and dedicated audit log file.
 * <p>
 * Thread-safe and non-blocking - uses background thread for file I/O.
 *
 * @since v2.6.0
 */
public class Python3AuditLogger {
    private static final Logger logger = LoggerFactory.getLogger(Python3AuditLogger.class);
    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .create();

    private final Path auditLogPath;
    private final ExecutorService executor;
    private volatile boolean shutdown = false;

    /**
     * Create a new audit logger.
     *
     * @param auditLogDirectory Directory to store audit log files
     */
    public Python3AuditLogger(String auditLogDirectory) {
        // Default to Ignition logs directory if not specified
        if (auditLogDirectory == null || auditLogDirectory.isEmpty()) {
            auditLogDirectory = System.getProperty("ignition.logs.dir", "logs");
        }

        // Create audit log file path: logs/python3-audit-YYYY-MM-DD.log
        String fileName = "python3-audit-" + LocalDate.now(ZoneId.systemDefault()).format(DateTimeFormatter.ISO_LOCAL_DATE) + ".log";
        this.auditLogPath = Paths.get(auditLogDirectory, fileName);

        // Create logs directory if it doesn't exist
        try {
            Files.createDirectories(auditLogPath.getParent());
        } catch (IOException e) {
            logger.error("Failed to create audit log directory: {}", auditLogPath.getParent(), e);
        }

        // Single background thread for non-blocking file I/O
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Python3-AuditLogger");
            t.setDaemon(true); // Don't prevent JVM shutdown
            return t;
        });

        logger.info("Python3 Audit Logger initialized: {}", auditLogPath);
    }

    /**
     * Log a Python execution event.
     * Non-blocking - writes to file asynchronously.
     *
     * @param event The audit event to log
     */
    public void logExecution(Python3AuditEvent event) {
        if (event == null) {
            logger.warn("Attempted to log null audit event");
            return;
        }

        if (shutdown) {
            logger.warn("Audit logger is shutdown, cannot log event");
            return;
        }

        // Log to SLF4J immediately (synchronous for visibility)
        logger.info(event.toLogLine());

        // Log to file asynchronously (non-blocking)
        executor.submit(() -> {
            try {
                writeToAuditLog(event);
            } catch (Exception e) {
                logger.error("Failed to write audit event to file: {}", event, e);
            }
        });
    }

    /**
     * Write audit event to JSON log file.
     * This method runs on background thread.
     *
     * @param event The event to write
     */
    private void writeToAuditLog(Python3AuditEvent event) {
        // Convert event to JSON
        Map<String, Object> auditJson = new HashMap<>();
        auditJson.put("timestamp", event.getTimestamp().toString());
        auditJson.put("user", event.getUser() != null ? event.getUser() : "UNAUTHENTICATED");
        auditJson.put("sourceIP", event.getSourceIP() != null ? event.getSourceIP() : "LOCAL");
        auditJson.put("securityMode", event.getSecurityMode().getValue());
        auditJson.put("codeHash", event.getCodeHash());
        auditJson.put("success", event.isSuccess());
        auditJson.put("durationMs", event.getDurationMs());
        auditJson.put("endpoint", event.getEndpoint());
        if (event.getErrorMessage() != null) {
            auditJson.put("error", event.getErrorMessage());
        }

        String jsonLine = GSON.toJson(auditJson);

        // Append to audit log file (one JSON object per line)
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(auditLogPath.toFile(), true))) {
            writer.write(jsonLine);
            writer.newLine();
        } catch (IOException e) {
            logger.error("Failed to write to audit log file: {}", auditLogPath, e);
        }
    }

    /**
     * Shutdown the audit logger.
     * Waits up to 10 seconds for pending writes to complete.
     */
    public void shutdown() {
        logger.info("Shutting down Python3 Audit Logger");
        shutdown = true;
        executor.shutdown();

        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                logger.warn("Audit logger did not shutdown cleanly within 10 seconds");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            logger.warn("Interrupted while waiting for audit logger shutdown", e);
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Get the path to the current audit log file.
     * @return The audit log file path
     */
    public Path getAuditLogPath() {
        return auditLogPath;
    }

    /**
     * Check if this audit logger has been shutdown.
     * @return true if shutdown() has been called
     */
    public boolean isShutdown() {
        return shutdown;
    }
}
