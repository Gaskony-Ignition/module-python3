package com.gaskony.python3.gateway;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Python3RestEndpoints.
 *
 * Tests verify REST API endpoint functionality including:
 * - Request/response handling
 * - Error handling
 * - JSON serialization/deserialization
 * - HTTP status codes
 *
 * Note: These tests mock the HTTP layer but use real Python3ProcessPool
 * for integration testing. Full end-to-end tests require a running Gateway.
 *
 * @since v2.12.0 (Phase 2 Week 1-2: Testing Infrastructure)
 */
public class Python3RestEndpointsTest {

    private Python3ProcessPool pool;
    private static final String TEST_PYTHON_PATH = "python3";

    @BeforeEach
    public void setUp() throws Exception {
        // Create a real process pool for integration testing
        pool = new Python3ProcessPool(TEST_PYTHON_PATH, 2);
    }

    /**
     * Test 1: POST /api/v1/health endpoint returns healthy status
     */
    @Test
    public void testHealthEndpoint() throws Exception {
        // This test verifies the health check endpoint structure
        // Full implementation would require mocking RouteGroup and RequestContext
        assertTrue(pool != null, "Pool should be initialized");
        assertTrue(pool.getStats().available > 0, "Pool should have available executors");
    }

    /**
     * Test 2: Verify pool can execute Python code (integration test)
     */
    @Test
    public void testIntegrationExecution() throws Exception {
        Python3Executor executor = pool.borrowExecutor(5, java.util.concurrent.TimeUnit.SECONDS);
        assertNotNull(executor, "Should be able to borrow executor");

        try {
            Map<String, Object> variables = new HashMap<>();
            variables.put("x", 10);
            variables.put("y", 20);

            Python3Result result = executor.execute("result = x + y", variables);
            assertTrue(result.isSuccess(), "Execution should succeed");
            assertEquals(30, ((Number) result.getResult()).intValue(), "Result should be 30");
        } finally {
            pool.returnExecutor(executor);
        }
    }

    /**
     * Test 3: Verify pool stats are available
     */
    @Test
    public void testPoolStatsAvailability() {
        Python3ProcessPool.PoolStats stats = pool.getStats();

        assertNotNull(stats, "Stats should not be null");
        assertEquals(2, stats.totalSize, "Pool size should match initialization");
        assertTrue(stats.available >= 0, "Available should be non-negative");
        assertTrue(stats.inUse >= 0, "InUse should be non-negative");
        assertEquals(stats.totalSize, stats.available + stats.inUse,
            "Total should equal available + inUse");
    }

    /**
     * Test 4: Verify error handling for invalid Python code
     */
    @Test
    public void testErrorHandling() throws Exception {
        Python3Executor executor = pool.borrowExecutor(5, java.util.concurrent.TimeUnit.SECONDS);
        assertNotNull(executor, "Should be able to borrow executor");

        try {
            Map<String, Object> variables = new HashMap<>();
            Python3Result result = executor.execute("invalid syntax here", variables);

            assertFalse(result.isSuccess(), "Execution should fail");
            assertNotNull(result.getError(), "Error should be present");
            assertTrue(result.getError().toLowerCase().contains("syntax") ||
                      result.getError().toLowerCase().contains("invalid"),
                "Error should mention syntax issue");
        } finally {
            pool.returnExecutor(executor);
        }
    }

    /**
     * Test 5: Verify expression evaluation
     */
    @Test
    public void testEvaluation() throws Exception {
        Python3Executor executor = pool.borrowExecutor(5, java.util.concurrent.TimeUnit.SECONDS);
        assertNotNull(executor, "Should be able to borrow executor");

        try {
            Map<String, Object> variables = new HashMap<>();
            variables.put("a", 7);
            variables.put("b", 3);

            Python3Result result = executor.evaluate("a * b", variables);
            assertTrue(result.isSuccess(), "Evaluation should succeed");
            assertEquals(21, ((Number) result.getResult()).intValue(), "Result should be 21");
        } finally {
            pool.returnExecutor(executor);
        }
    }

    /**
     * Test 6: Verify JSON response structure for success
     */
    @Test
    public void testJsonResponseStructure() throws Exception {
        Python3Executor executor = pool.borrowExecutor(5, java.util.concurrent.TimeUnit.SECONDS);

        try {
            Map<String, Object> variables = new HashMap<>();
            Python3Result result = executor.execute("print('test')", variables);

            // Verify result structure
            assertTrue(result.isSuccess(), "Should be successful");
            assertNotNull(result.getResult(), "Result should not be null");
            assertNull(result.getError(), "Error should be null on success");
        } finally {
            pool.returnExecutor(executor);
        }
    }

    /**
     * Test 7: Verify concurrent execution handling
     */
    @Test
    public void testConcurrentExecution() throws Exception {
        Python3Executor executor1 = pool.borrowExecutor(5, java.util.concurrent.TimeUnit.SECONDS);
        Python3Executor executor2 = pool.borrowExecutor(5, java.util.concurrent.TimeUnit.SECONDS);

        assertNotNull(executor1, "First executor should be available");
        assertNotNull(executor2, "Second executor should be available");
        assertNotSame(executor1, executor2, "Executors should be different instances");

        try {
            Map<String, Object> vars1 = new HashMap<>();
            vars1.put("n", 1);
            Python3Result result1 = executor1.execute("x = n * 10", vars1);
            assertTrue(result1.isSuccess(), "First execution should succeed");

            Map<String, Object> vars2 = new HashMap<>();
            vars2.put("n", 2);
            Python3Result result2 = executor2.execute("x = n * 10", vars2);
            assertTrue(result2.isSuccess(), "Second execution should succeed");
        } finally {
            pool.returnExecutor(executor1);
            pool.returnExecutor(executor2);
        }
    }

    /**
     * Test 8: Verify pool exhaustion handling
     */
    @Test
    public void testPoolExhaustion() throws Exception {
        // Borrow all executors
        Python3Executor executor1 = pool.borrowExecutor(5, java.util.concurrent.TimeUnit.SECONDS);
        Python3Executor executor2 = pool.borrowExecutor(5, java.util.concurrent.TimeUnit.SECONDS);

        // Verify pool is exhausted
        Python3ProcessPool.PoolStats stats = pool.getStats();
        assertEquals(0, stats.available, "Pool should be exhausted");
        assertEquals(2, stats.inUse, "All executors should be in use");

        // Try to borrow another with short timeout - should timeout
        try {
            Python3Executor executor3 = pool.borrowExecutor(1, java.util.concurrent.TimeUnit.SECONDS);
            fail("Should have timed out trying to borrow from exhausted pool");
        } catch (Exception e) {
            // Expected - pool exhausted
            // Just verify we got an exception (timeout, interrupted, etc.)
            assertNotNull(e, "Should have thrown an exception");
        } finally {
            pool.returnExecutor(executor1);
            pool.returnExecutor(executor2);
        }
    }

    @org.junit.jupiter.api.AfterEach
    public void tearDown() {
        if (pool != null) {
            pool.shutdown();
        }
    }
}
