package com.gaskony.python3.gateway;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for Python3ProcessPool.PoolStats.
 * Tests the pool statistics value object.
 */
class PoolStatsTest {

    @Test
    void testPoolStatsCreation() {
        // Create pool stats
        Python3ProcessPool.PoolStats stats = new Python3ProcessPool.PoolStats(5, 3, 2, 5);

        assertThat(stats.totalSize).isEqualTo(5);
        assertThat(stats.available).isEqualTo(3);
        assertThat(stats.inUse).isEqualTo(2);
        assertThat(stats.healthy).isEqualTo(5);
    }

    @Test
    void testPoolStatsWithZeroValues() {
        // All zeros (empty pool scenario)
        Python3ProcessPool.PoolStats stats = new Python3ProcessPool.PoolStats(0, 0, 0, 0);

        assertThat(stats.totalSize).isEqualTo(0);
        assertThat(stats.available).isEqualTo(0);
        assertThat(stats.inUse).isEqualTo(0);
        assertThat(stats.healthy).isEqualTo(0);
    }

    @Test
    void testPoolStatsAllInUse() {
        // All executors in use
        Python3ProcessPool.PoolStats stats = new Python3ProcessPool.PoolStats(5, 0, 5, 5);

        assertThat(stats.totalSize).isEqualTo(5);
        assertThat(stats.available).isEqualTo(0);
        assertThat(stats.inUse).isEqualTo(5);
        assertThat(stats.healthy).isEqualTo(5);
    }

    @Test
    void testPoolStatsWithUnhealthyExecutors() {
        // Some executors unhealthy
        Python3ProcessPool.PoolStats stats = new Python3ProcessPool.PoolStats(5, 2, 1, 3);

        assertThat(stats.totalSize).isEqualTo(5);
        assertThat(stats.available).isEqualTo(2);
        assertThat(stats.inUse).isEqualTo(1);
        assertThat(stats.healthy).isEqualTo(3);
    }

    @Test
    void testPoolStatsToString() {
        // Test toString format
        Python3ProcessPool.PoolStats stats = new Python3ProcessPool.PoolStats(5, 3, 2, 4);
        String str = stats.toString();

        assertThat(str)
            .contains("PoolStats")
            .contains("total=5")
            .contains("available=3")
            .contains("inUse=2")
            .contains("healthy=4");
    }

    @Test
    void testPoolStatsLargePool() {
        // Test with larger pool size (20 is max)
        Python3ProcessPool.PoolStats stats = new Python3ProcessPool.PoolStats(20, 15, 5, 20);

        assertThat(stats.totalSize).isEqualTo(20);
        assertThat(stats.available).isEqualTo(15);
        assertThat(stats.inUse).isEqualTo(5);
        assertThat(stats.healthy).isEqualTo(20);
    }

    @Test
    void testPoolStatsSmallPool() {
        // Test with minimum pool size (1)
        Python3ProcessPool.PoolStats stats = new Python3ProcessPool.PoolStats(1, 0, 1, 1);

        assertThat(stats.totalSize).isEqualTo(1);
        assertThat(stats.available).isEqualTo(0);
        assertThat(stats.inUse).isEqualTo(1);
        assertThat(stats.healthy).isEqualTo(1);
    }
}
