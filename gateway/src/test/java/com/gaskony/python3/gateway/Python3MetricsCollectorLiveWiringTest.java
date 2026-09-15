package com.gaskony.python3.gateway;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression tests for the v4.4.0 Diagnostics-data fix.
 *
 * <p>The Designer's Diagnostics dialog reads {@link Python3MetricsCollector}, whose
 * own execution counters are never incremented. Before v4.4.0 that made
 * {@code impactLevel}/{@code healthScore}/execution rate permanently "LOW"/100/0,
 * regardless of real load. The fix sources those from the pool's LIVE
 * {@link MetricsCollector} (the one actually fed by every {@code pool.execute()})
 * plus live {@link Python3ProcessPool.PoolStats}. These tests pin that wiring.</p>
 */
class Python3MetricsCollectorLiveWiringTest {

    private Python3ProcessPool poolWith(MetricsCollector metrics,
                                        Python3ProcessPool.PoolStats stats) {
        Python3ProcessPool pool = mock(Python3ProcessPool.class);
        when(pool.getMetricsCollector()).thenReturn(metrics);
        when(pool.getStats()).thenReturn(stats);
        when(pool.getSubprocessPids()).thenReturn(Collections.emptyList());
        return pool;
    }

    @Test
    void gatewayImpactReflectsLivePoolLoad() {
        MetricsCollector live = new MetricsCollector();
        for (int i = 0; i < 20; i++) {
            live.recordExecution(true, 12);
        }
        for (int i = 0; i < 5; i++) {
            live.recordExecution(false, 40);
        }
        // 2 of 3 executors busy -> ~66% utilisation -> not the frozen "LOW".
        Python3ProcessPool.PoolStats stats = new Python3ProcessPool.PoolStats(3, 1, 2, 3);

        Python3MetricsCollector collector = new Python3MetricsCollector();
        collector.setProcessPool(poolWith(live, stats));

        Map<String, Object> impact = collector.getGatewayImpact();

        assertThat(((Number) impact.get("pool_utilization_percent")).doubleValue())
                .as("66% of a 3-executor pool in use").isGreaterThan(60.0);
        assertThat(impact.get("impactLevel"))
                .as("was permanently LOW before v4.4.0").isEqualTo("MEDIUM");
        assertThat(((Number) impact.get("healthScore")).intValue())
                .as("dinged for utilisation + failures, no longer frozen at 100")
                .isLessThan(100);
    }

    @Test
    void gatewayImpactIsCleanWhenPoolIdleAndHealthy() {
        MetricsCollector live = new MetricsCollector();
        for (int i = 0; i < 10; i++) {
            live.recordExecution(true, 5);
        }
        Python3ProcessPool.PoolStats idle = new Python3ProcessPool.PoolStats(3, 3, 0, 3);

        Python3MetricsCollector collector = new Python3MetricsCollector();
        collector.setProcessPool(poolWith(live, idle));

        Map<String, Object> impact = collector.getGatewayImpact();

        assertThat(impact.get("impactLevel")).isEqualTo("LOW");
        assertThat(((Number) impact.get("healthScore")).intValue()).isEqualTo(100);
        assertThat(((Number) impact.get("pool_utilization_percent")).doubleValue()).isZero();
    }
}
