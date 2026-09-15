package com.gaskony.python3.gateway;

import com.inductiveautomation.ignition.common.gson.Gson;
import com.inductiveautomation.ignition.common.gson.GsonBuilder;
import com.inductiveautomation.ignition.common.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Manages a single Python 3 process and handles communication via stdin/stdout.
 * This class is thread-safe for sequential execution but only one command can be
 * executed at a time per executor instance.
 *
 * Enhanced in v2.14.0 with:
 * - Resource limits enforcement
 * - Input validation
 * - User context tracking
 * - Audit logging
 */
public class Python3Executor {

    private static final Logger logger = LoggerFactory.getLogger(Python3Executor.class);
    private static final Gson GSON = new GsonBuilder()
            .serializeNulls()  // Serialize null values as "null" in JSON
            .create();
    private static final long DEFAULT_TIMEOUT_MS = 30000; // 30 seconds

    /**
     * Default audit-label passed downstream in the JSON envelope. The bridge ignores
     * {@code security_mode} (access control is enforced Java-side); this value only
     * appears in audit logs. Normalised across all execute/eval/callModule paths in
     * v4.1.0 (previously a mix of "ADMIN"/"RESTRICTED").
     */
    private static final String DEFAULT_MODE = "ADMIN";

    // Shared executor for timeout operations - avoids creating new thread pool for each read
    private static final ExecutorService TIMEOUT_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "Python3Executor-Timeout");
        t.setDaemon(true);
        return t;
    });

    private final String pythonPath;
    private final Path bridgeScriptPath;
    private Process process;
    private BufferedWriter processInput;
    private BufferedReader processOutput;
    private BufferedReader processError;
    private final Object executionLock = new Object();
    private volatile boolean isHealthy = false;

    /**
     * Daemon thread that continuously drains the subprocess stderr stream.
     *
     * <p>Without this, anything the Python process writes to stderr (e.g. user code calling
     * {@code print(..., file=sys.stderr)}, or a noisy library) accumulates in the OS pipe buffer
     * (~64 KB). Once full, the subprocess blocks mid-write and the request hangs until the 30 s
     * read timeout recycles the executor. Draining stderr on its own thread removes that
     * deadlock. stderr is log-only — the JSON protocol runs over stdout (see v4.1.0 fix).
     */
    private Thread stderrDrainThread;
    private volatile boolean draining = false;

    /**
     * Create a new Python3Executor.
     *
     * <p>v4.1.0: dropped the {@code (resourceLimits, inputValidator, auditLogger)} overload.
     * Input validation never ran on the live path and the {@code InputValidator} sandbox was
     * removed (it blocked legitimate code; OS isolation + the Java-side role gate are the real
     * boundary — see {@code docs/architecture/ARCHITECTURE.md} §7). Audit logging now happens at
     * the REST/scripting entry points via {@code Python3AuditLogger}.
     *
     * @param pythonPath Path to Python 3 executable
     * @throws IOException if Python process cannot be started
     */
    public Python3Executor(String pythonPath) throws IOException {
        this.pythonPath = pythonPath;
        this.bridgeScriptPath = extractBridgeScript();
        startProcess();
    }

    /**
     * Extract the python_bridge.py script from resources to a temporary file
     */
    private Path extractBridgeScript() throws IOException {
        Path tempScript = Files.createTempFile("python_bridge", ".py");
        tempScript.toFile().deleteOnExit();

        try (InputStream is = getClass().getResourceAsStream("/python_bridge.py")) {
            if (is == null) {
                throw new IOException("Could not find python_bridge.py in resources");
            }
            Files.copy(is, tempScript, StandardCopyOption.REPLACE_EXISTING);
        }

        logger.debug("Extracted bridge script to: {}", tempScript);
        return tempScript;
    }

    /**
     * Start the Python process with resource limits
     */
    private void startProcess() throws IOException {
        logger.info("Starting Python 3 process: {}", pythonPath);

        ProcessBuilder pb = new ProcessBuilder(
                pythonPath,
                "-u",  // Unbuffered output
                bridgeScriptPath.toString()
        );

        // Set environment
        pb.environment().put("PYTHONIOENCODING", "utf-8");

        // Pass virtual environment path if configured
        // This tells Python to use the venv's site-packages
        String venvPath = System.getProperty("ignition.python3.venv");
        if (venvPath != null && !venvPath.isEmpty()) {
            pb.environment().put("VIRTUAL_ENV", venvPath);
            logger.info("VIRTUAL_ENV set to: {}", venvPath);
        }

        // Resource limits (configurable via system properties)
        // v4.4.0: default memory 512 -> 2048 MB. The bridge caps RLIMIT_AS (virtual
        // address space); 512 MB was below what OpenBLAS reserves just to import
        // numpy, so the datascience packages installed but could not be used.
        String maxMemoryMB = System.getProperty("ignition.python3.max.memory.mb", "2048");
        String maxCpuSeconds = System.getProperty("ignition.python3.max.cpu.seconds", "60");

        pb.environment().put("PYTHON3_MAX_MEMORY_MB", maxMemoryMB);
        pb.environment().put("PYTHON3_MAX_CPU_SECONDS", maxCpuSeconds);

        // v4.4.0: keep threaded-allocator virtual reservations close to real usage
        // so the RLIMIT_AS cap reflects actual memory pressure rather than OpenBLAS's
        // per-thread arena reservations. Single-threaded BLAS is also the right
        // default for a shared gateway pool (avoids N processes each spawning
        // core-count worker threads). Users can override in their own scripts.
        pb.environment().putIfAbsent("OPENBLAS_NUM_THREADS", "1");
        pb.environment().putIfAbsent("OMP_NUM_THREADS", "1");
        pb.environment().putIfAbsent("MKL_NUM_THREADS", "1");
        pb.environment().putIfAbsent("MALLOC_ARENA_MAX", "2");

        logger.info("Python process resource limits: Max memory={}MB, Max CPU={}s",
                maxMemoryMB, maxCpuSeconds);

        pb.redirectErrorStream(false);

        // Start process
        process = pb.start();

        // Set up streams
        processInput = new BufferedWriter(
                new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8)
        );
        processOutput = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)
        );
        processError = new BufferedReader(
                new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8)
        );

        // Start the stderr drainer so the subprocess can never block on a full stderr pipe.
        startStderrDrain();

        // Wait for ready signal
        waitForReady();

        logger.info("Python 3 process started successfully");
    }

    /**
     * Start a daemon thread that drains the subprocess stderr stream to the log.
     * See {@link #stderrDrainThread} for why this is required (v4.1.0 deadlock fix).
     */
    private void startStderrDrain() {
        draining = true;
        stderrDrainThread = new Thread(() -> {
            try {
                String line;
                while (draining && (line = processError.readLine()) != null) {
                    // stderr is diagnostic only; the JSON protocol is on stdout.
                    logger.debug("[py-stderr] {}", line);
                }
            } catch (IOException e) {
                // Expected when the process exits / streams close during shutdown.
                logger.trace("stderr drain ended: {}", e.getMessage());
            }
        }, "Python3-StderrDrain");
        stderrDrainThread.setDaemon(true);
        stderrDrainThread.start();
    }

    /**
     * Wait for the Python process to send ready signal
     */
    private void waitForReady() throws IOException {
        try {
            String line = processOutput.readLine();
            if (line != null) {
                JsonObject response = GSON.fromJson(line, JsonObject.class);
                if (response.has("status") && "ready".equals(response.get("status").getAsString())) {
                    isHealthy = true;
                    logger.debug("Python process is ready");
                    return;
                }
            }
            throw new IOException("Python process did not send ready signal");
        } catch (Exception e) {
            throw new IOException("Failed to receive ready signal from Python process", e);
        }
    }

    /**
     * Execute Python code
     *
     * @param code      Python code to execute
     * @param variables Variables to pass to Python
     * @return Result object
     * @throws Python3Exception if execution fails
     */
    public Python3Result execute(String code, Map<String, Object> variables) throws Python3Exception {
        return execute(code, variables, DEFAULT_MODE);
    }

    /**
     * Execute Python code with a security-mode label.
     *
     * <p>The {@code securityMode} value is recorded in audit logs only — the python_bridge
     * subprocess ignores it (access control is enforced Java-side; see
     * {@code docs/architecture/ARCHITECTURE.md} §7).</p>
     *
     * @param code         Python code to execute
     * @param variables    Variables to pass to Python
     * @param securityMode Audit-log label (e.g. "ADMIN", "DESIGNER_ADMIN")
     * @return Result object
     * @throws Python3Exception if execution fails
     */
    public Python3Result execute(String code, Map<String, Object> variables, String securityMode) throws Python3Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("command", "execute");
        request.put("code", code);
        request.put("variables", variables);
        request.put("security_mode", securityMode);

        return sendRequest(request, DEFAULT_TIMEOUT_MS);
    }

    /**
     * Evaluate Python expression
     *
     * @param expression Python expression to evaluate
     * @param variables  Variables to pass to Python
     * @return Result object
     * @throws Python3Exception if evaluation fails
     */
    public Python3Result evaluate(String expression, Map<String, Object> variables) throws Python3Exception {
        return evaluate(expression, variables, DEFAULT_MODE);
    }

    /**
     * Evaluate Python expression with a security-mode label.
     *
     * @param expression   Python expression to evaluate
     * @param variables    Variables to pass to Python
     * @param securityMode Audit-log label (e.g. "ADMIN", "DESIGNER_ADMIN")
     * @return Result object
     * @throws Python3Exception if evaluation fails
     */
    public Python3Result evaluate(String expression, Map<String, Object> variables, String securityMode) throws Python3Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("command", "evaluate");
        request.put("expression", expression);
        request.put("variables", variables);
        request.put("security_mode", securityMode);

        return sendRequest(request, DEFAULT_TIMEOUT_MS);

    }

    /**
     * Call a Python module function
     *
     * @param moduleName   Module name (e.g., "math")
     * @param functionName Function name (e.g., "sqrt")
     * @param args         Positional arguments
     * @param kwargs       Keyword arguments
     * @return Result object
     * @throws Python3Exception if call fails
     */
    public Python3Result callModule(String moduleName, String functionName,
                                     List<Object> args, Map<String, Object> kwargs) throws Python3Exception {
        return callModule(moduleName, functionName, args, kwargs, DEFAULT_MODE);
    }

    /**
     * Call a Python module function with a security-mode label
     *
     * @param moduleName   Module name (e.g., "math")
     * @param functionName Function name (e.g., "sqrt")
     * @param args         Positional arguments
     * @param kwargs       Keyword arguments
     * @param securityMode Audit-log label (e.g. "ADMIN", "DESIGNER_ADMIN")
     * @return Result object
     * @throws Python3Exception if call fails
     */
    public Python3Result callModule(String moduleName, String functionName,
                                     List<Object> args, Map<String, Object> kwargs, String securityMode) throws Python3Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("command", "call_module");
        request.put("module", moduleName);
        request.put("function", functionName);
        request.put("args", args);
        request.put("kwargs", kwargs);
        request.put("security_mode", securityMode);

        return sendRequest(request, DEFAULT_TIMEOUT_MS);
    }

    /**
     * Check Python code syntax
     *
     * @param code Python code to check
     * @return Result object with list of errors
     * @throws Python3Exception if request fails
     */
    public Python3Result checkSyntax(String code) throws Python3Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("command", "check_syntax");
        request.put("code", code);

        return sendRequest(request, DEFAULT_TIMEOUT_MS);
    }

    /**
     * Get code completions at cursor position
     *
     * @param code   Python code
     * @param line   Line number (1-based)
     * @param column Column number (0-based)
     * @return Result object with list of completions
     * @throws Python3Exception if request fails
     */
    public Python3Result getCompletions(String code, int line, int column) throws Python3Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("command", "get_completions");
        request.put("code", code);
        request.put("line", line);
        request.put("column", column);

        return sendRequest(request, DEFAULT_TIMEOUT_MS);
    }

    /**
     * Get Python version information
     *
     * @return Result object with version info
     * @throws Python3Exception if request fails
     */
    public Python3Result getVersion() throws Python3Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("command", "version");

        return sendRequest(request, DEFAULT_TIMEOUT_MS);
    }

    /**
     * Ping the Python process to check if it's alive
     *
     * @return true if process responds to ping
     */
    public boolean ping() {
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("command", "ping");

            Python3Result result = sendRequest(request, 5000);
            return result.isSuccess();
        } catch (Exception e) {
            logger.warn("Ping failed", e);
            return false;
        }
    }

    /**
     * Send a request to Python process and wait for response
     */
    private Python3Result sendRequest(Map<String, Object> request, long timeoutMs) throws Python3Exception {
        synchronized (executionLock) {
            if (!isAlive()) {
                throw new Python3Exception("Python process is not alive");
            }

            try {
                // Send request
                String requestJson = GSON.toJson(request);
                logger.debug("Sending request: {}", requestJson);

                processInput.write(requestJson);
                processInput.newLine();
                processInput.flush();

                // Read response with timeout
                String responseLine = readLineWithTimeout(timeoutMs);

                if (responseLine == null) {
                    isHealthy = false;
                    throw new Python3Exception("No response from Python process (timeout: " + timeoutMs + "ms)");
                }

                logger.debug("Received response: {}", responseLine);

                // Parse response
                JsonObject response = GSON.fromJson(responseLine, JsonObject.class);
                boolean success = response.has("success") && response.get("success").getAsBoolean();

                if (success) {
                    Object result = GSON.fromJson(response.get("result"), Object.class);
                    return new Python3Result(true, result, null, null);
                } else {
                    String error = response.has("error") ? response.get("error").getAsString() : "Unknown error";
                    String traceback = response.has("traceback") ? response.get("traceback").getAsString() : null;
                    return new Python3Result(false, null, error, traceback);
                }

            } catch (IOException e) {
                isHealthy = false;
                throw new Python3Exception("Communication error with Python process", e);
            } catch (Exception e) {
                throw new Python3Exception("Error processing Python request", e);
            }
        }
    }

    /**
     * Read a line from process output with timeout.
     * Uses shared thread pool to avoid overhead of creating executor per read.
     */
    private String readLineWithTimeout(long timeoutMs) throws IOException {
        Future<String> future = TIMEOUT_EXECUTOR.submit(() -> processOutput.readLine());

        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            logger.warn("Read timeout after {}ms", timeoutMs);
            return null;
        } catch (InterruptedException | ExecutionException e) {
            throw new IOException("Error reading from process", e);
        }
    }

    /**
     * Check if process is alive
     */
    public boolean isAlive() {
        return process != null && process.isAlive();
    }

    /**
     * Check if process is healthy (alive and responding)
     */
    public boolean isHealthy() {
        return isHealthy && isAlive();
    }

    /**
     * Get the subprocess process ID (PID).
     * v2.15.5: Required for memory/CPU monitoring
     *
     * @return process ID, or 0 if process is not alive
     */
    public long getProcessPid() {
        if (process != null && process.isAlive()) {
            return process.pid();
        }
        return 0;
    }

    /**
     * Shutdown the Python process gracefully
     */
    public void shutdown() {
        logger.info("Shutting down Python 3 process");

        try {
            // Send shutdown command
            Map<String, Object> request = new HashMap<>();
            request.put("command", "shutdown");
            String requestJson = GSON.toJson(request);

            processInput.write(requestJson);
            processInput.newLine();
            processInput.flush();

            // Wait for graceful shutdown
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                logger.warn("Python process did not shutdown gracefully, forcing");
                process.destroyForcibly();
            }

        } catch (Exception e) {
            logger.error("Error during shutdown", e);
            if (process != null) {
                process.destroyForcibly();
            }
        } finally {
            isHealthy = false;
            draining = false;
            if (stderrDrainThread != null) {
                stderrDrainThread.interrupt();
            }
            closeStreams();
        }

        logger.info("Python 3 process shutdown complete");
    }

    /**
     * Shutdown the shared timeout executor service.
     * Should be called from GatewayHook.shutdown() to prevent thread pool leak.
     * v2.15.9: Added to fix resource leak
     */
    public static void shutdownTimeoutExecutor() {
        logger.info("Shutting down Python3Executor timeout thread pool");
        try {
            TIMEOUT_EXECUTOR.shutdown();
            if (!TIMEOUT_EXECUTOR.awaitTermination(5, TimeUnit.SECONDS)) {
                logger.warn("Timeout executor did not terminate gracefully, forcing shutdown");
                TIMEOUT_EXECUTOR.shutdownNow();
            }
        } catch (InterruptedException e) {
            logger.error("Interrupted while shutting down timeout executor", e);
            TIMEOUT_EXECUTOR.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("Python3Executor timeout thread pool shutdown complete");
    }

    /**
     * Close all streams
     */
    private void closeStreams() {
        try {
            if (processInput != null) {
                processInput.close();
            }
            if (processOutput != null) {
                processOutput.close();
            }
            if (processError != null) {
                processError.close();
            }
        } catch (IOException e) {
            logger.error("Error closing streams", e);
        }
    }

    /**
     * Get any error output from the process
     */
    public String getErrorOutput() {
        try {
            StringBuilder sb = new StringBuilder();
            while (processError.ready()) {
                String line = processError.readLine();
                if (line != null) {
                    sb.append(line).append("\n");
                }
            }
            return sb.toString();
        } catch (IOException e) {
            return "Could not read error output: " + e.getMessage();
        }
    }

}
