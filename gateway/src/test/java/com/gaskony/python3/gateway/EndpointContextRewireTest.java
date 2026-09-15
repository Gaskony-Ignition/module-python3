package com.gaskony.python3.gateway;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for the v4.3.1 web-UI "0 packages" bug.
 *
 * <p>The platform mounts REST routes before the deferred-init daemon thread
 * creates the package/pool managers, and {@code mountRoutes} snapshots services
 * into the shared {@link EndpointContext}. Prior to v4.3.1 those two fields were
 * {@code final}, so the snapshot kept {@code null} forever and every packages/
 * versions REST endpoint answered "not initialized" — the Gateway web UI
 * Packages page showed "0 installed · 0 total" while the Designer (which
 * resolves services lazily over RPC) saw the real catalog.</p>
 *
 * <p>Verifies that the deferred-init setters rewire the live context.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EndpointContextRewireTest {

    @Mock private Python3PackageManager packageManager;
    @Mock private PoolManager poolManager;

    @AfterEach
    void resetStatics() {
        Python3RestEndpoints.activeContext = null;
        Python3RestEndpoints.setPackageManager(null);
        Python3RestEndpoints.setPoolManager(null);
    }

    private EndpointContext newContextWithNullDeferredServices() {
        return new EndpointContext(null, null, null, null, null, null, null, null, null);
    }

    @Test
    void setPackageManager_rewiresLiveContext() {
        EndpointContext ctx = newContextWithNullDeferredServices();
        Python3RestEndpoints.activeContext = ctx;
        assertThat(ctx.packageManager).isNull();

        Python3RestEndpoints.setPackageManager(packageManager);

        assertThat(ctx.packageManager).isSameAs(packageManager);
    }

    @Test
    void setPoolManager_rewiresLiveContext() {
        EndpointContext ctx = newContextWithNullDeferredServices();
        Python3RestEndpoints.activeContext = ctx;
        assertThat(ctx.poolManager).isNull();

        Python3RestEndpoints.setPoolManager(poolManager);

        assertThat(ctx.poolManager).isSameAs(poolManager);
    }

    @Test
    void settersWithoutActiveContext_doNotThrow() {
        Python3RestEndpoints.activeContext = null;

        Python3RestEndpoints.setPackageManager(packageManager);
        Python3RestEndpoints.setPoolManager(poolManager);
        // No exception = pass; statics reset in @AfterEach.
    }
}
