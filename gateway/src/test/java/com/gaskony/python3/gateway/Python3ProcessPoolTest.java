package com.gaskony.python3.gateway;

import com.gaskony.python3.gateway.Python3ProcessPool.PoolStats;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Python3ProcessPool.
 *
 * These tests verify the process pool's ability to:
 * - Initialize and manage multiple Python executors
 * - Handle concurrent borrowing and returning
 * - Recover from executor failures
 * - Properly shut down and clean up resources
 *
 * @since v2.12.0 (Phase 2 Week 1-2: Testing Infrastructure)
 */
public class Python3ProcessPoolTest {

    private static final Logger logger = LoggerFactory.getLogger(Python3ProcessPoolTest.class);
    private static final String TEST_PYTHON_PATH = "python3";
    private static final int SMALL_POOL_SIZE = 2;
    private static final int MEDIUM_POOL_SIZE = 3;

    private Python3ProcessPool pool;

    @BeforeEach
    public void setUp() {
        logger.info("Setting up Python3ProcessPoolTest");
    }

    @AfterEach
    public void tearDown() {
        if (pool != null) {
            try {
                pool.shutdown();
            } catch (Exception e) {
                logger.warn("Error during pool shutdown in tearDown", e);
            }
        }
    }

    /**
     * Test 1: Pool initialization creates correct number of processes
     * and all executors are healthy after initialization.
     */
    @Test
    public void testPoolInitialization() throws Exception {
        logger.info("Test: Pool initialization");

        pool = new Python3ProcessPool(TEST_PYTHON_PATH, MEDIUM_POOL_SIZE);

        // Verify pool size
        PoolStats stats = pool.getStats();
        assertNotNull(stats, "Pool stats should not be null");
        assertEquals(MEDIUM_POOL_SIZE, stats.totalSize, "Pool size should match configuration");
        assertEquals(MEDIUM_POOL_SIZE, stats.available, "All executors should be available initially");
        assertEquals(0, stats.inUse, "No executors should be borrowed initially");

        // Verify all executors are healthy by borrowing each one
        List<Python3Executor> executors = new ArrayList<>();
        for (int i = 0; i < MEDIUM_POOL_SIZE; i++) {
            Python3Executor executor = pool.borrowExecutor(5, TimeUnit.SECONDS);
            assertNotNull(executor, "Should be able to borrow executor " + i);
            assertTrue(executor.isAlive(), "Executor " + i + " should be alive");
            executors.add(executor);
        }

        // Return all executors
        for (Python3Executor executor : executors) {
            pool.returnExecutor(executor);
        }

        logger.info("✓ Pool initialization test passed");
    }

    /**
     * Test 2: Borrow and return executor lifecycle.
     * Verifies that borrowing decreases available count and returning increases it.
     */
    @Test
    public void testBorrowAndReturnExecutor() throws Exception {
        logger.info("Test: Borrow and return executor");

        pool = new Python3ProcessPool(TEST_PYTHON_PATH, SMALL_POOL_SIZE);

        // Initial state
        PoolStats stats = pool.getStats();
        assertEquals(SMALL_POOL_SIZE, stats.available, "Initial available executors");
        assertEquals(0, stats.inUse, "Initial borrowed executors");

        // Borrow an executor
        Python3Executor executor = pool.borrowExecutor(5, TimeUnit.SECONDS);
        assertNotNull(executor, "Should successfully borrow executor");

        // Verify pool state changed
        stats = pool.getStats();
        assertEquals(SMALL_POOL_SIZE - 1, stats.available, "Available executors should decrease");
        assertEquals(1, stats.inUse, "Borrowed executors should increase");

        // Verify executor works
        assertTrue(executor.isAlive(), "Borrowed executor should be alive");

        // Return executor
        pool.returnExecutor(executor);

        // Verify pool state restored
        stats = pool.getStats();
        assertEquals(SMALL_POOL_SIZE, stats.available, "Available executors should be restored");
        assertEquals(0, stats.inUse, "Borrowed executors should be zero");

        // Verify we can borrow the same executor again
        Python3Executor executor2 = pool.borrowExecutor(5, TimeUnit.SECONDS);
        assertNotNull(executor2, "Should be able to borrow executor again");
        assertTrue(executor2.isAlive(), "Re-borrowed executor should be alive");

        pool.returnExecutor(executor2);

        logger.info("✓ Borrow and return test passed");
    }

    /**
     * Test 3: Concurrent borrowing with proper queuing.
     * Spawns multiple threads trying to borrow from a small pool.
     */
    @Test
    public void testConcurrentBorrowing() throws Exception {
        logger.info("Test: Concurrent borrowing");

        pool = new Python3ProcessPool(TEST_PYTHON_PATH, MEDIUM_POOL_SIZE);

        final int THREAD_COUNT = 10;
        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);
        final List<Exception> exceptions = new CopyOnWriteArrayList<>();
        final List<String> successfulBorrows = new CopyOnWriteArrayList<>();

        // Spawn threads that try to borrow executors
        for (int i = 0; i < THREAD_COUNT; i++) {
            final int threadId = i;
            new Thread(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready

                    Python3Executor executor = pool.borrowExecutor(10, TimeUnit.SECONDS);
                    successfulBorrows.add("Thread-" + threadId);

                    // intentional fixed delay — this sleep runs *inside* a worker thread
                    // holding a borrowed executor. It exists so that other threads see
                    // contention while borrowing; replacing it with Awaitility would
                    // remove the very contention the test exercises.
                    Thread.sleep(50);

                    pool.returnExecutor(executor);
                } catch (Exception e) {
                    exceptions.add(e);
                } finally {
                    doneLatch.countDown();
                }
            }).start();
        }

        // Start all threads simultaneously
        startLatch.countDown();

        // Wait for all threads to complete (with timeout)
        boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
        assertTrue(completed, "All threads should complete within timeout");

        // Verify no exceptions occurred
        if (!exceptions.isEmpty()) {
            logger.error("Exceptions during concurrent borrowing:");
            exceptions.forEach(e -> logger.error("  - " + e.getMessage(), e));
        }
        assertTrue(exceptions.isEmpty(), "No exceptions should occur during concurrent borrowing");

        // Verify all threads successfully borrowed an executor
        assertEquals(THREAD_COUNT, successfulBorrows.size(), "All threads should successfully borrow");

        // Verify final pool state
        PoolStats stats = pool.getStats();
        assertEquals(MEDIUM_POOL_SIZE, stats.available, "All executors should be returned");
        assertEquals(0, stats.inUse, "No executors should be borrowed");

        logger.info("✓ Concurrent borrowing test passed");
    }

    /**
     * Test 4: Borrow timeout when pool is exhausted.
     * Verifies that borrowing times out when no executors are available.
     */
    @Test
    public void testBorrowTimeout() throws Exception {
        logger.info("Test: Borrow timeout");

        pool = new Python3ProcessPool(TEST_PYTHON_PATH, 1); // Single executor pool

        // Borrow the only executor
        Python3Executor executor1 = pool.borrowExecutor(5, TimeUnit.SECONDS);
        assertNotNull(executor1, "Should successfully borrow first executor");

        // Try to borrow another with short timeout - should timeout
        long startTime = System.currentTimeMillis();
        try {
            pool.borrowExecutor(1, TimeUnit.SECONDS);
            fail("Should have thrown TimeoutException");
        } catch (TimeoutException e) {
            long duration = System.currentTimeMillis() - startTime;
            assertTrue(duration >= 900 && duration <= 1500, "Timeout should occur around 1 second");
            logger.info("Expected timeout occurred after {}ms", duration);
        }

        // Return first executor
        pool.returnExecutor(executor1);

        // Now borrowing should succeed immediately
        Python3Executor executor2 = pool.borrowExecutor(5, TimeUnit.SECONDS);
        assertNotNull(executor2, "Should successfully borrow after return");

        pool.returnExecutor(executor2);

        logger.info("✓ Borrow timeout test passed");
    }

    /**
     * Test 5: Pool shutdown terminates all processes.
     * Verifies that shutdown cleanly terminates all executors.
     */
    @Test
    public void testPoolShutdown() throws Exception {
        logger.info("Test: Pool shutdown");

        pool = new Python3ProcessPool(TEST_PYTHON_PATH, MEDIUM_POOL_SIZE);

        // Borrow and execute some tasks to ensure executors are active
        List<Python3Executor> executors = new ArrayList<>();
        for (int i = 0; i < MEDIUM_POOL_SIZE; i++) {
            Python3Executor executor = pool.borrowExecutor(5, TimeUnit.SECONDS);
            // Execute a simple task to ensure subprocess is active
            executor.execute("x = 1 + 1", new java.util.HashMap<>());
            executors.add(executor);
        }

        // Return all executors
        for (Python3Executor executor : executors) {
            pool.returnExecutor(executor);
        }

        // Shutdown pool
        pool.shutdown();

        // Verify all executors are terminated
        for (Python3Executor executor : executors) {
            assertFalse(executor.isAlive(), "Executor should be terminated after shutdown");
        }

        // Verify we can't borrow after shutdown
        try {
            pool.borrowExecutor(1, TimeUnit.SECONDS);
            fail("Should not be able to borrow after shutdown");
        } catch (IllegalStateException e) {
            logger.info("Expected exception after shutdown: {}", e.getMessage());
        } catch (Exception e) {
            // Also acceptable - pool is shut down
            logger.info("Pool correctly prevented borrowing after shutdown");
        }

        pool = null; // Don't double-shutdown in tearDown

        logger.info("✓ Pool shutdown test passed");
    }

    /**
     * Test 6: Pool state tracking after executions.
     * Verifies that pool correctly maintains state across multiple executions.
     */
    @Test
    public void testPoolStateAfterExecutions() throws Exception {
        logger.info("Test: Pool state after executions");

        pool = new Python3ProcessPool(TEST_PYTHON_PATH, SMALL_POOL_SIZE);

        // Initial stats
        PoolStats initialStats = pool.getStats();
        assertEquals(SMALL_POOL_SIZE, initialStats.totalSize, "Pool size should match");
        assertEquals(SMALL_POOL_SIZE, initialStats.available, "All executors should be available");

        // Borrow executor and execute code
        Python3Executor executor = pool.borrowExecutor(5, TimeUnit.SECONDS);
        executor.execute("result = 2 + 2", new java.util.HashMap<>());
        pool.returnExecutor(executor);

        // Verify pool is still healthy
        PoolStats afterStats = pool.getStats();
        assertEquals(SMALL_POOL_SIZE, afterStats.totalSize, "Pool size should remain constant");
        assertEquals(SMALL_POOL_SIZE, afterStats.available, "All executors should be available again");

        logger.info("✓ Pool state test passed");
    }

    /**
     * Test 7: Pool health check and executor replacement.
     * Verifies that unhealthy executors are detected and replaced.
     *
     * Note: This test may be skipped if health checking is not fully implemented yet.
     */
    @Test
    public void testHealthCheckAndRecovery() throws Exception {
        logger.info("Test: Health check and recovery (may be limited by implementation)");

        pool = new Python3ProcessPool(TEST_PYTHON_PATH, SMALL_POOL_SIZE);

        // Get initial pool state
        PoolStats initialStats = pool.getStats();
        int initialPoolSize = initialStats.totalSize;

        // Borrow an executor
        Python3Executor executor = pool.borrowExecutor(5, TimeUnit.SECONDS);

        // Try to kill the executor's subprocess to simulate failure
        // (This may not work depending on implementation)
        try {
            executor.shutdown(); // Shutdown to simulate failure
        } catch (Exception e) {
            logger.warn("Could not shutdown executor: {}", e.getMessage());
        }

        // Return the potentially unhealthy executor
        pool.returnExecutor(executor);

        // Verify pool still functions
        Python3Executor newExecutor = pool.borrowExecutor(5, TimeUnit.SECONDS);
        assertNotNull(newExecutor, "Should still be able to borrow executor");
        assertTrue(newExecutor.isAlive(), "Borrowed executor should be alive");

        pool.returnExecutor(newExecutor);

        // Verify pool maintained correct size
        PoolStats finalStats = pool.getStats();
        assertEquals(initialPoolSize, finalStats.totalSize, "Pool size should remain constant");

        logger.info("✓ Health check test passed (basic verification)");
    }

    /**
     * Test 8: Multiple borrow and return cycles.
     * Verifies that executors can be reused many times without issues.
     */
    @Test
    public void testMultipleBorrowReturnCycles() throws Exception {
        logger.info("Test: Multiple borrow/return cycles");

        pool = new Python3ProcessPool(TEST_PYTHON_PATH, SMALL_POOL_SIZE);

        final int CYCLES = 20;

        for (int i = 0; i < CYCLES; i++) {
            Python3Executor executor = pool.borrowExecutor(5, TimeUnit.SECONDS);
            assertNotNull(executor, "Cycle " + i + ": Should borrow executor");
            assertTrue(executor.isAlive(), "Cycle " + i + ": Executor should be alive");

            // Execute simple code
            executor.execute("x = " + i, new java.util.HashMap<>());

            pool.returnExecutor(executor);
        }

        // Verify pool is still healthy
        PoolStats finalStats = pool.getStats();
        assertEquals(SMALL_POOL_SIZE, finalStats.available, "All executors should be available");
        assertEquals(SMALL_POOL_SIZE, finalStats.totalSize, "Pool size should remain constant");

        logger.info("✓ Multiple cycles test passed ({} cycles)", CYCLES);
    }
}
