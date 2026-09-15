package com.gaskony.python3.gateway;

import com.inductiveautomation.ignition.common.gson.JsonArray;
import com.inductiveautomation.ignition.common.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads Ignition gateway logs from the system_logs.idb SQLite database.
 * This is the same approach used by the AI-Terminal module.
 *
 * In Docker, wrapper.log is symlinked to /dev/stdout and cannot be read.
 * Ignition stores all logs in the system_logs.idb SQLite database.
 *
 * @since v3.5.0
 * @since v3.5.2 Rewritten to use SQLite database instead of wrapper.log
 */
public final class Python3LogsHandler {

    private static final Logger logger = LoggerFactory.getLogger(Python3LogsHandler.class);
    private static final int DEFAULT_LINES = 100;
    private static final int MAX_LINES = 500;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private Python3LogsHandler() {
        // Static utility class
    }

    /**
     * Read log entries from Ignition's system_logs.idb SQLite database.
     *
     * @param logsDir  The Ignition logs directory (from GatewayContext)
     * @param lines    Maximum number of entries to return
     * @param level    Minimum log level filter: ALL, DEBUG, INFO, WARN, ERROR
     * @param filter   Text search filter (case-insensitive)
     * @param afterId  Event ID for pagination (0 = most recent)
     * @return JSON object with log entries
     */
    public static JsonObject readLogs(File logsDir, int lines, String level, String filter, long afterId) {
        JsonObject response = new JsonObject();
        JsonArray entries = new JsonArray();

        lines = Math.max(1, Math.min(lines, MAX_LINES));

        File logDb = findSystemLogsDb(logsDir);
        if (logDb == null || !logDb.exists() || !logDb.canRead()) {
            response.addProperty("success", true);
            response.add("entries", entries);
            response.addProperty("count", 0);
            response.addProperty("total", 0);
            response.addProperty("hasMore", false);
            response.addProperty("warning", "system_logs.idb not found. Searched: " + getSearchedPaths(logsDir));
            return response;
        }

        String url = "jdbc:sqlite:" + logDb.getAbsolutePath();

        try (Connection conn = DriverManager.getConnection(url)) {
            // Build query with filters
            StringBuilder sql = new StringBuilder();
            sql.append("SELECT event_id, timestmp, formatted_message, logger_name, level_string ");
            sql.append("FROM logging_event WHERE 1=1 ");

            List<Object> params = new ArrayList<>();

            // Pagination: after event ID
            if (afterId > 0) {
                sql.append("AND event_id < ? ");
                params.add(afterId);
            }

            // Level filter
            Set<String> levelSet = parseLevelFilter(level);
            if (!levelSet.isEmpty()) {
                sql.append("AND level_string IN (");
                sql.append(String.join(",", Collections.nCopies(levelSet.size(), "?")));
                sql.append(") ");
                params.addAll(levelSet);
            }

            // Text filter
            if (filter != null && !filter.trim().isEmpty()) {
                sql.append("AND (formatted_message LIKE ? OR logger_name LIKE ?) ");
                params.add("%" + filter.trim() + "%");
                params.add("%" + filter.trim() + "%");
            }

            // Order newest first, limit
            sql.append("ORDER BY event_id DESC LIMIT ?");
            params.add(lines);

            try (PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
                for (int i = 0; i < params.size(); i++) {
                    Object param = params.get(i);
                    if (param instanceof Long) {
                        stmt.setLong(i + 1, (Long) param);
                    } else if (param instanceof Integer) {
                        stmt.setInt(i + 1, (Integer) param);
                    } else {
                        stmt.setString(i + 1, param.toString());
                    }
                }

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        JsonObject entry = new JsonObject();
                        long eventId = rs.getLong("event_id");
                        long timestmp = rs.getLong("timestmp");
                        String message = rs.getString("formatted_message");
                        String loggerName = rs.getString("logger_name");
                        String levelStr = rs.getString("level_string");

                        String formattedTime;
                        synchronized (DATE_FORMAT) {
                            formattedTime = DATE_FORMAT.format(new Date(timestmp));
                        }

                        entry.addProperty("id", eventId);
                        entry.addProperty("timestamp", formattedTime);
                        entry.addProperty("level", levelStr);
                        entry.addProperty("logger", loggerName);
                        entry.addProperty("message", message);

                        entries.add(entry);
                    }
                }
            }

            // Get total count
            long totalLines = countTotalLogs(conn);

            response.addProperty("success", true);
            response.add("entries", entries);
            response.addProperty("count", entries.size());
            response.addProperty("total", totalLines);
            response.addProperty("hasMore", entries.size() >= lines);
            response.addProperty("logFile", logDb.getAbsolutePath());

        } catch (SQLException e) {
            logger.error("Failed to read logs from SQLite database", e);
            response.addProperty("success", false);
            response.addProperty("error", "Failed to read logs: " + e.getMessage());
            response.add("entries", entries);
            response.addProperty("count", 0);
        }

        return response;
    }

    private static long countTotalLogs(Connection conn) {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM logging_event")) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            logger.warn("Error counting log entries: {}", e.getMessage());
        }
        return 0;
    }

    /**
     * Parse level filter. If a single level like "INFO" is passed,
     * include that level and all levels above it.
     */
    private static Set<String> parseLevelFilter(String level) {
        Set<String> filter = new HashSet<>();
        if (level == null || level.isEmpty() || "ALL".equalsIgnoreCase(level)) {
            return filter; // empty = all levels
        }

        String upperLevel = level.toUpperCase().trim();
        // Include the specified level and all above
        switch (upperLevel) {
            case "TRACE":
                filter.add("TRACE");
                // fall through
            case "DEBUG":
                filter.add("DEBUG");
                // fall through
            case "INFO":
                filter.add("INFO");
                // fall through
            case "WARN":
                filter.add("WARN");
                // fall through
            case "ERROR":
                filter.add("ERROR");
                break;
            default:
                // Comma-separated list
                Arrays.stream(level.split(","))
                    .map(String::trim)
                    .map(String::toUpperCase)
                    .filter(s -> !s.isEmpty())
                    .forEach(filter::add);
                break;
        }
        return filter;
    }

    /**
     * Find system_logs.idb file by checking multiple possible locations.
     */
    private static File findSystemLogsDb(File logsDir) {
        List<File> candidates = new ArrayList<>();

        // Primary: from GatewayContext logsDir
        if (logsDir != null) {
            candidates.add(new File(logsDir, "system_logs.idb"));
        }

        // Common Ignition installation paths
        candidates.add(new File("/usr/local/bin/ignition/logs/system_logs.idb"));
        candidates.add(new File("/var/lib/ignition/logs/system_logs.idb"));
        candidates.add(new File("C:/Program Files/Inductive Automation/Ignition/logs/system_logs.idb"));

        // From system properties
        String installDir = System.getProperty("ignition.install.dir", "");
        if (!installDir.isEmpty()) {
            candidates.add(new File(installDir, "logs/system_logs.idb"));
        }

        String ignHome = System.getProperty("ignition.home", System.getenv("IGNITION_HOME") != null ? System.getenv("IGNITION_HOME") : "");
        if (!ignHome.isEmpty()) {
            candidates.add(new File(ignHome, "logs/system_logs.idb"));
        }

        for (File candidate : candidates) {
            if (candidate.exists() && candidate.canRead()) {
                logger.debug("Found system_logs.idb at: {}", candidate.getAbsolutePath());
                return candidate;
            }
        }

        logger.debug("system_logs.idb not found in standard locations");
        return null;
    }

    private static String getSearchedPaths(File logsDir) {
        List<String> paths = new ArrayList<>();
        if (logsDir != null) {
            paths.add(new File(logsDir, "system_logs.idb").getAbsolutePath());
        }
        paths.add("/usr/local/bin/ignition/logs/system_logs.idb");
        paths.add("/var/lib/ignition/logs/system_logs.idb");
        return String.join(", ", paths);
    }
}
