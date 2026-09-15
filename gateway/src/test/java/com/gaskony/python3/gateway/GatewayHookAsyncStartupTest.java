package com.gaskony.python3.gateway;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression tests for P2-PY3: {@code GatewayHook#startup} must return promptly
 * (≤100 ms) even when the deferred initialisation work would otherwise block on
 * Python interpreter spawn or {@code pip install jedi}.
 *
 * <p>The test cannot fully exercise the daemon thread (it would need a live
 * {@code GatewayContext}, a real Python interpreter, etc.), but it CAN prove the
 * fast-path: that the lifecycle thread is unblocked and that the readiness future
 * eventually completes (exceptionally is acceptable — the point is that it does
 * not hang the Gateway boot).
 *
 * @since v3.13.0 (P2-PY3 fix)
 */
class GatewayHookAsyncStartupTest {

    private GatewayHook hook;

    @AfterEach
    void tearDown() {
        // Deliberately NOT calling hook.shutdown() here — that would shut down
        // Python3Executor.TIMEOUT_EXECUTOR (a static, shared resource) and break
        // every other test in this JVM run.
        //
        // The async-init thread is a daemon and will be reaped on its own; it will
        // also have completed (exceptionally) by the time the test exits because
        // gatewayContext is null and the thread fast-fails on first NPE.
        hook = null;
    }

    @Test
    @DisplayName("startup() returns within 100 ms even when async init would block")
    void startup_returnsWithin100ms() {
        hook = new GatewayHook();
        // Note: we deliberately skip GatewayHook#setup so distributionManager is null —
        // when the daemon thread tries to use it, it will throw inside runDeferredInit
        // and complete readinessFuture exceptionally. The fast-path (startup itself)
        // must still return promptly regardless of that downstream failure.

        long startNs = System.nanoTime();
        hook.startup(null);
        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;

        assertThat(elapsedMs)
            .as("startup() must return promptly; deferred init runs on a daemon thread")
            .isLessThan(100);
    }

    @Test
    @DisplayName("readinessFuture is non-null after startup() and completes within 5 s")
    void readinessFuture_completesPromptly() throws Exception {
        hook = new GatewayHook();
        hook.startup(null);

        CompletableFuture<Void> future = hook.getReadinessFuture();
        assertThat(future).isNotNull();

        // Without a real GatewayContext the daemon thread will fail fast (NPE on
        // gatewayContext.getSystemManager()), completing the future exceptionally.
        // We only care that it completes — i.e. the daemon thread terminates.
        boolean settled = hook.awaitReadiness(5, TimeUnit.SECONDS);
        assertThat(settled)
            .as("Readiness future should settle within the test timeout")
            .isTrue();
        assertThat(future.isDone()).isTrue();
    }

    @Test
    @DisplayName("readinessFuture completes exceptionally on init failure (no setup)")
    void readinessFuture_completesExceptionally_whenInitFails() throws Exception {
        hook = new GatewayHook();
        hook.startup(null);

        // Without #setup the daemon thread will hit an NPE deep in #runDeferredInit
        // (gatewayContext.getSystemManager() is null). The future should complete
        // exceptionally, not hang.
        boolean settled = hook.awaitReadiness(5, TimeUnit.SECONDS);
        assertThat(settled).isTrue();
        assertThat(hook.getReadinessFuture().isCompletedExceptionally())
            .as("With no setup, deferred init must fail-fast and surface a completion exception")
            .isTrue();
    }
}
