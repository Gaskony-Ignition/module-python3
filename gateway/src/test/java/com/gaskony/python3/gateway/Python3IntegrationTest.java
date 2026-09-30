package com.gaskony.python3.gateway;

import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.*;
import java.util.concurrent.TimeUnit;

/**
 * Integration tests for Python3ProcessPool and Python3Executor.
 * Tests actual Python execution with real processes.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class Python3IntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(Python3IntegrationTest.class);
    private static Python3ProcessPool pool;
    private static String pythonPath;

    @BeforeAll
    static void setupPool() throws Exception {
        // Find Python 3
        pythonPath = findPython3();
        assertThat(pythonPath).isNotNull().describedAs("Python 3 must be available");

        logger.info("Using Python: {}", pythonPath);

        // Create pool with small size for testing
        // Bridge script is automatically extracted from resources by Python3Executor
        pool = new Python3ProcessPool(pythonPath, 2);

        // Wait for pool to initialize
        await().atMost(10, TimeUnit.SECONDS)
               .pollInterval(100, TimeUnit.MILLISECONDS)
               .until(() -> pool.getStats().healthy > 0);

        logger.info("Pool initialized: {}", pool.getStats());
    }

    @AfterAll
    static void shutdownPool() {
        if (pool != null) {
            pool.shutdown();
        }
    }

    @Test
    @Order(1)
    void testPoolInitialization() {
        Python3ProcessPool.PoolStats stats = pool.getStats();

        assertThat(stats.totalSize).isEqualTo(2);
        assertThat(stats.healthy).isGreaterThan(0);
        assertThat(stats.available).isGreaterThan(0);
    }

    @Test
    @Order(2)
    void testSimpleExecution() throws Exception {
        Python3Executor executor = pool.borrowExecutor(5, TimeUnit.SECONDS);
        assertThat(executor).isNotNull();

        try {
            String code = "result = 2 + 2";
            Python3Result result = executor.execute(code, new HashMap<>(), "UNRESTRICTED");

            assertThat(result.isSuccess()).isTrue();
            // Python 3 returns numbers as doubles in JSON
            assertThat(result.getResult()).isEqualTo(4.0);
            assertThat(result.getError()).isNull();
        } finally {
            pool.returnExecutor(executor);
        }
    }

    @Test
    @Order(3)
    void testSimpleEvaluation() throws Exception {
        Python3Executor executor = pool.borrowExecutor(5, TimeUnit.SECONDS);
        assertThat(executor).isNotNull();

        try {
            String expression = "2 + 2";
            Python3Result result = executor.evaluate(expression, new HashMap<>(), "UNRESTRICTED");

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getResult()).isEqualTo(4.0);
        } finally {
            pool.returnExecutor(executor);
        }
    }

    @Test
    @Order(4)
    void testVariableInjection() throws Exception {
        Python3Executor executor = pool.borrowExecutor(5, TimeUnit.SECONDS);
        assertThat(executor).isNotNull();

        try {
            Map<String, Object> variables = new HashMap<>();
            variables.put("x", 10);
            variables.put("y", 20);

            String expression = "x + y";
            Python3Result result = executor.evaluate(expression, variables, "UNRESTRICTED");

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getResult()).isEqualTo(30.0);
        } finally {
            pool.returnExecutor(executor);
        }
    }

    @Test
    @Order(5)
    void testStringResult() throws Exception {
        Python3Executor executor = pool.borrowExecutor(5, TimeUnit.SECONDS);
        assertThat(executor).isNotNull();

        try {
            String expression = "'hello world'";
            Python3Result result = executor.evaluate(expression, new HashMap<>(), "UNRESTRICTED");

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getResult()).isEqualTo("hello world");
        } finally {
            pool.returnExecutor(executor);
        }
    }

    @Test
    @Order(6)
    void testListResult() throws Exception {
        Python3Executor executor = pool.borrowExecutor(5, TimeUnit.SECONDS);
        assertThat(executor).isNotNull();

        try {
            String expression = "[1, 2, 3, 4, 5]";
            Python3Result result = executor.evaluate(expression, new HashMap<>(), "UNRESTRICTED");

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getResult()).isInstanceOf(java.util.List.class);
        } finally {
            pool.returnExecutor(executor);
        }
    }

    @Test
    @Order(7)
    void testDictResult() throws Exception {
        Python3Executor executor = pool.borrowExecutor(5, TimeUnit.SECONDS);
        assertThat(executor).isNotNull();

        try {
            String expression = "{'name': 'test', 'value': 42}";
            Python3Result result = executor.evaluate(expression, new HashMap<>(), "UNRESTRICTED");

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getResult()).isInstanceOf(Map.class);
        } finally {
            pool.returnExecutor(executor);
        }
    }

    @Test
    @Order(8)
    void testExecutionError() throws Exception {
        Python3Executor executor = pool.borrowExecutor(5, TimeUnit.SECONDS);
        assertThat(executor).isNotNull();

        try {
            String code = "raise ValueError('Test error')";
            Python3Result result = executor.execute(code, new HashMap<>(), "UNRESTRICTED");

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getError()).contains("Test error");
            assertThat(result.getTraceback()).isNotNull();
        } finally {
            pool.returnExecutor(executor);
        }
    }

    @Test
    @Order(9)
    void testSyntaxError() throws Exception {
        Python3Executor executor = pool.borrowExecutor(5, TimeUnit.SECONDS);
        assertThat(executor).isNotNull();

        try {
            String code = "def bad syntax";
            Python3Result result = executor.execute(code, new HashMap<>(), "UNRESTRICTED");

            assertThat(result.isSuccess()).isFalse();
            // Error message may vary between Python versions
            assertThat(result.getError()).isNotEmpty();
        } finally {
            pool.returnExecutor(executor);
        }
    }

    @Test
    @Order(10)
    void testMultipleExecutions() throws Exception {
        Python3Executor executor = pool.borrowExecutor(5, TimeUnit.SECONDS);
        assertThat(executor).isNotNull();

        try {
            // First execution
            Python3Result result1 = executor.evaluate("10 + 5", new HashMap<>(), "UNRESTRICTED");
            assertThat(result1.isSuccess()).isTrue();
            assertThat(result1.getResult()).isEqualTo(15.0);

            // Second execution (same executor)
            Python3Result result2 = executor.evaluate("20 * 2", new HashMap<>(), "UNRESTRICTED");
            assertThat(result2.isSuccess()).isTrue();
            assertThat(result2.getResult()).isEqualTo(40.0);

            // Third execution
            Python3Result result3 = executor.evaluate("'test'", new HashMap<>(), "UNRESTRICTED");
            assertThat(result3.isSuccess()).isTrue();
            assertThat(result3.getResult()).isEqualTo("test");
        } finally {
            pool.returnExecutor(executor);
        }
    }

    @Test
    @Order(11)
    void testConcurrentBorrowing() throws Exception {
        // Borrow multiple executors concurrently
        Python3Executor executor1 = pool.borrowExecutor(5, TimeUnit.SECONDS);
        Python3Executor executor2 = pool.borrowExecutor(5, TimeUnit.SECONDS);

        assertThat(executor1).isNotNull();
        assertThat(executor2).isNotNull();
        assertThat(executor1).isNotSameAs(executor2);

        try {
            // Execute on both
            Python3Result result1 = executor1.evaluate("100", new HashMap<>(), "UNRESTRICTED");
            Python3Result result2 = executor2.evaluate("200", new HashMap<>(), "UNRESTRICTED");

            assertThat(result1.isSuccess()).isTrue();
            assertThat(result1.getResult()).isEqualTo(100.0);
            assertThat(result2.isSuccess()).isTrue();
            assertThat(result2.getResult()).isEqualTo(200.0);
        } finally {
            pool.returnExecutor(executor1);
            pool.returnExecutor(executor2);
        }
    }

    @Test
    @Order(12)
    void testPoolStatsAfterBorrowing() throws Exception {
        Python3ProcessPool.PoolStats statsBefore = pool.getStats();
        int availableBefore = statsBefore.available;

        Python3Executor executor = pool.borrowExecutor(5, TimeUnit.SECONDS);

        try {
            Python3ProcessPool.PoolStats statsDuring = pool.getStats();
            assertThat(statsDuring.available).isLessThan(availableBefore);
            assertThat(statsDuring.inUse).isGreaterThan(0);
        } finally {
            pool.returnExecutor(executor);
        }

        // Poll until pool stats catch up with the executor return. Awaitility lets the
        // observed value end the wait rather than relying on a magic number.
        await().atMost(java.time.Duration.ofSeconds(2))
            .pollInterval(java.time.Duration.ofMillis(20))
            .untilAsserted(() ->
                assertThat(pool.getStats().available).isEqualTo(availableBefore)
            );

        Python3ProcessPool.PoolStats statsAfter = pool.getStats();
        assertThat(statsAfter.available).isEqualTo(availableBefore);
    }

    @Test
    @Order(13)
    void testImportStandardLibrary() throws Exception {
        Python3Executor executor = pool.borrowExecutor(5, TimeUnit.SECONDS);
        assertThat(executor).isNotNull();

        try {
            String code = "import math; result = math.sqrt(16)";
            Python3Result result = executor.execute(code, new HashMap<>(), "UNRESTRICTED");

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getResult()).isEqualTo(4.0);
        } finally {
            pool.returnExecutor(executor);
        }
    }

    @Test
    @Order(14)
    void testMultilineCode() throws Exception {
        Python3Executor executor = pool.borrowExecutor(5, TimeUnit.SECONDS);
        assertThat(executor).isNotNull();

        try {
            String code = """
                x = 10
                y = 20
                z = x + y
                result = z * 2
                """;
            Python3Result result = executor.execute(code, new HashMap<>(), "UNRESTRICTED");

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getResult()).isEqualTo(60.0);
        } finally {
            pool.returnExecutor(executor);
        }
    }

    @Test
    @Order(15)
    void testHealthCheck() {
        // Health check runs automatically in background
        Python3ProcessPool.PoolStats stats = pool.getStats();

        assertThat(stats.healthy).isGreaterThan(0);
        assertThat(stats.healthy).isLessThanOrEqualTo(stats.totalSize);
    }

    /**
     * Find Python 3 executable on the system.
     * Tries common locations and PATH.
     */
    private static String findPython3() {
        String[] candidates = {
            "python3",
            "python",
            "/usr/bin/python3",
            "/usr/local/bin/python3",
            "C:\\Python311\\python.exe",
            "C:\\Python310\\python.exe",
            "C:\\Program Files\\Python311\\python.exe"
        };

        for (String candidate : candidates) {
            try {
                Process process = new ProcessBuilder(candidate, "--version")
                    .redirectErrorStream(true)
                    .start();

                int exitCode = process.waitFor();
                if (exitCode == 0) {
                    String output = new String(process.getInputStream().readAllBytes());
                    if (output.contains("Python 3")) {
                        logger.info("Found Python 3: {} -> {}", candidate, output.trim());
                        return candidate;
                    }
                }
            } catch (Exception e) {
                // Try next candidate
            }
        }

        return null;
    }
}
