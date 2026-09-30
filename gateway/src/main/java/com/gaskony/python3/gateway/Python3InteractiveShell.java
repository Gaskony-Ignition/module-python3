package com.gaskony.python3.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Manages persistent interactive Python shell sessions for Terminal mode.
 *
 * v2.5.8: Simple interactive shell implementation
 * - Maintains persistent Python process (python3 -u -i)
 * - Keeps streams open for interactive command execution
 * - Command history per session
 * - Session lifecycle management
 *
 * v3.1.1: Fixed to use Python instead of bash.
 * - createSession(pythonPath) overload for multi-version support
 * - isPython flag controls prompt filtering and end-of-command marker
 * - Merged stderr into stdout for tracebacks
 */
public class Python3InteractiveShell {

    private static final Logger logger = LoggerFactory.getLogger(Python3InteractiveShell.class);

    // Session storage
    private static final ConcurrentHashMap<String, ShellSession> SESSIONS = new ConcurrentHashMap<>();

    // Session timeout (30 minutes of inactivity)
    private static final long SESSION_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(30);

    /**
     * Creates a new interactive Python shell session using the default python3 executable.
     *
     * @return session ID
     */
    public static String createSession() {
        return createSession("python3");
    }

    /**
     * Creates a new interactive Python shell session.
     *
     * @param pythonPath path to the python executable (e.g., "/usr/bin/python3.11")
     * @return session ID
     */
    public static String createSession(String pythonPath) {
        String sessionId = UUID.randomUUID().toString();
        ShellSession session = new ShellSession(sessionId, pythonPath);

        if (session.start()) {
            SESSIONS.put(sessionId, session);
            logger.info("Created new Python shell session: {} (python: {})", sessionId, pythonPath);
            return sessionId;
        } else {
            logger.error("Failed to start Python shell session: {}", sessionId);
            return null;
        }
    }

    /**
     * Executes a command in an existing session.
     *
     * @param sessionId session ID
     * @param command   command to execute
     * @return shell output
     */
    public static String executeCommand(String sessionId, String command) {
        ShellSession session = SESSIONS.get(sessionId);
        if (session == null) {
            return "ERROR: Session not found. Please restart terminal.";
        }

        session.touch();  // Update last activity time
        return session.executeCommand(command);
    }

    /**
     * Closes a shell session.
     *
     * @param sessionId session ID
     */
    public static void closeSession(String sessionId) {
        ShellSession session = SESSIONS.remove(sessionId);
        if (session != null) {
            session.close();
            logger.info("Closed shell session: {}", sessionId);
        }
    }

    /**
     * Closes all sessions (called during module shutdown).
     */
    public static void closeAllSessions() {
        logger.info("Closing all shell sessions ({} active)", SESSIONS.size());
        for (ShellSession session : SESSIONS.values()) {
            session.close();
        }
        SESSIONS.clear();
    }

    /**
     * Cleans up inactive sessions (called periodically).
     */
    public static void cleanupInactiveSessions() {
        long now = System.currentTimeMillis();
        List<String> toRemove = new ArrayList<>();

        for (String sessionId : SESSIONS.keySet()) {
            ShellSession session = SESSIONS.get(sessionId);
            if (session != null && (now - session.getLastActivity()) > SESSION_TIMEOUT_MS) {
                toRemove.add(sessionId);
            }
        }

        for (String sessionId : toRemove) {
            logger.info("Removing inactive shell session: {}", sessionId);
            closeSession(sessionId);
        }
    }

    /**
     * Individual shell session.
     */
    private static class ShellSession {
        private final String sessionId;
        private Process process;
        private BufferedWriter stdin;
        private BufferedReader stdout;
        private long lastActivity;
        private final String shellCommand;
        private final boolean isPython;
        private final String workingDirectory;

        public ShellSession(String sessionId, String customShellCommand) {
            this.sessionId = sessionId;
            this.lastActivity = System.currentTimeMillis();
            this.shellCommand = customShellCommand;
            this.isPython = !customShellCommand.contains("bash") && !customShellCommand.contains("cmd");
            this.workingDirectory = System.getProperty("user.home");
        }

        /**
         * Starts the shell process.
         */
        public boolean start() {
            try {
                ProcessBuilder pb;
                if (isPython) {
                    // Start Python in unbuffered, interactive mode
                    pb = new ProcessBuilder(shellCommand, "-u", "-i");
                    // Merge stderr (tracebacks) into stdout so we capture them
                    pb.redirectErrorStream(true);
                } else {
                    pb = new ProcessBuilder(shellCommand);
                    pb.redirectErrorStream(false);
                }
                pb.directory(new File(workingDirectory));
                process = pb.start();

                // Setup streams
                stdin = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
                stdout = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));

                if (isPython) {
                    // Consume the Python startup banner and initial >>> prompt
                    consumeInitialOutput();
                }

                logger.info("Started shell process: {} (session: {}, isPython: {})", shellCommand, sessionId, isPython);
                return true;

            } catch (IOException e) {
                logger.error("Failed to start shell process", e);
                return false;
            }
        }

        /**
         * Consumes the Python startup banner (version line + initial prompt) so it does not
         * bleed into the first command's output.
         */
        private void consumeInitialOutput() {
            try {
                long start = System.currentTimeMillis();
                // Give Python up to 3 seconds to emit its banner
                while (System.currentTimeMillis() - start < 3000) {
                    if (stdout.ready()) {
                        stdout.read(); // consume one character at a time
                    } else {
                        Thread.sleep(50);
                        if (!stdout.ready()) {
                            break; // no more output — banner fully consumed
                        }
                    }
                }
            } catch (Exception e) {
                logger.debug("Error consuming initial Python output", e);
            }
        }

        /**
         * Executes a command in the shell.
         */
        public String executeCommand(String command) {
            if (process == null || !process.isAlive()) {
                return "ERROR: Shell process is not alive. Please restart terminal.";
            }

            try {
                // Write command to shell
                stdin.write(command);
                stdin.newLine();
                stdin.flush();

                // Write a sentinel so we know when output is complete
                if (isPython) {
                    stdin.write("print(\"__END_OF_COMMAND__\")");
                } else {
                    stdin.write("echo __END_OF_COMMAND__");
                }
                stdin.newLine();
                stdin.flush();

                // Read output until sentinel
                StringBuilder output = new StringBuilder();

                // Read stdout (with timeout)
                long startTime = System.currentTimeMillis();
                long timeout = 10000;  // 10 second timeout

                while (System.currentTimeMillis() - startTime < timeout) {
                    if (stdout.ready()) {
                        String line = stdout.readLine();
                        if (line == null) {
                            break;
                        }

                        // Stop when we see the sentinel
                        if (line.contains("__END_OF_COMMAND__")) {
                            break;
                        }

                        if (isPython) {
                            // Strip Python interactive prompts
                            String cleaned = line;
                            if (cleaned.startsWith(">>> ")) {
                                cleaned = cleaned.substring(4);
                            } else if (cleaned.startsWith("... ")) {
                                cleaned = cleaned.substring(4);
                            }

                            // Skip prompt-only lines that have no real content
                            if (cleaned.trim().isEmpty()
                                    && (line.startsWith(">>> ") || line.startsWith("... "))) {
                                continue;
                            }

                            // Skip lines that echo our sentinel print statement
                            if (cleaned.contains("print(\"__END_OF_COMMAND__\")")) {
                                continue;
                            }

                            output.append(cleaned).append("\n");
                        } else {
                            output.append(line).append("\n");
                        }
                    }

                    // Small sleep to avoid busy waiting
                    Thread.sleep(10);
                }

                String result = output.toString();
                return result.isEmpty() ? "(no output)" : result;

            } catch (IOException | InterruptedException e) {
                logger.error("Error executing command in shell session: {}", sessionId, e);
                return "ERROR: " + e.getMessage();
            }
        }

        /**
         * Updates last activity timestamp.
         */
        public void touch() {
            this.lastActivity = System.currentTimeMillis();
        }

        /**
         * Gets last activity timestamp.
         */
        public long getLastActivity() {
            return lastActivity;
        }

        /**
         * Closes the shell session.
         */
        public void close() {
            try {
                if (stdin != null) {
                    stdin.close();
                }
                if (stdout != null) {
                    stdout.close();
                }
                if (process != null && process.isAlive()) {
                    process.destroy();
                    if (!process.waitFor(5, TimeUnit.SECONDS)) {
                        process.destroyForcibly();
                    }
                }
            } catch (Exception e) {
                logger.error("Error closing shell session: {}", sessionId, e);
            }
        }
    }
}
