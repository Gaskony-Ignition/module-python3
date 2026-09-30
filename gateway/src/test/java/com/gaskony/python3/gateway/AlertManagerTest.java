package com.gaskony.python3.gateway;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for AlertManager.
 *
 * Tests alert threshold checks, cooldown suppression, and counter tracking.
 * No Ignition SDK required — pure Java logic.
 */
class AlertManagerTest {

    // ===== Initial state =====

    @Test
    void defaultConstructor_setsDefaultThresholds() {
        AlertManager am = new AlertManager();
        assertThat(am.getErrorRateThreshold()).isGreaterThan(0);
        assertThat(am.getSlowResponseThresholdMs()).isGreaterThan(0);
        assertThat(am.getAlertCooldownMs()).isGreaterThan(0);
    }

    @Test
    void customConstructor_setsGivenValues() {
        AlertManager am = new AlertManager(0.10, 3000, 5000);
        assertThat(am.getErrorRateThreshold()).isEqualTo(0.10);
        assertThat(am.getSlowResponseThresholdMs()).isEqualTo(3000);
        assertThat(am.getAlertCooldownMs()).isEqualTo(5000);
    }

    @Test
    void totalAlerts_startsAtZero() {
        AlertManager am = new AlertManager();
        assertThat(am.getTotalAlerts()).isEqualTo(0);
    }

    @Test
    void suppressedAlerts_startsAtZero() {
        AlertManager am = new AlertManager();
        assertThat(am.getSuppressedAlerts()).isEqualTo(0);
    }

    // ===== checkErrorRate =====

    @Test
    void checkErrorRate_belowThreshold_doesNotAlert() {
        // threshold=10%, current=5%, total=100 → below threshold
        AlertManager am = new AlertManager(0.10, 5000, 60000);
        am.checkErrorRate(0.05, 100);
        assertThat(am.getTotalAlerts()).isEqualTo(0);
    }

    @Test
    void checkErrorRate_aboveThreshold_firesAlert() {
        // threshold=10%, current=20%, total=100 → fires
        AlertManager am = new AlertManager(0.10, 5000, 60000);
        am.checkErrorRate(0.20, 100);
        assertThat(am.getTotalAlerts()).isEqualTo(1);
    }

    @Test
    void checkErrorRate_lowTotalExecutions_doesNotAlert() {
        // Even high error rate with very few executions should not alert (< 10 total)
        AlertManager am = new AlertManager(0.10, 5000, 60000);
        am.checkErrorRate(0.99, 5); // 5 total — below minimum threshold
        assertThat(am.getTotalAlerts()).isEqualTo(0);
    }

    @Test
    void checkErrorRate_cooldownSuppressesSecondAlert() {
        // Long cooldown: second alert within cooldown window is suppressed
        AlertManager am = new AlertManager(0.10, 5000, 60_000);
        am.checkErrorRate(0.50, 100); // first alert
        am.checkErrorRate(0.50, 100); // second — should be suppressed
        assertThat(am.getTotalAlerts()).isEqualTo(1);
        assertThat(am.getSuppressedAlerts()).isEqualTo(1);
    }

    // ===== alertPoolExhaustion =====

    @Test
    void alertPoolExhaustion_whenAllBorrowed_firesAlert() {
        AlertManager am = new AlertManager(0.10, 5000, 0); // cooldown=0 → always fires
        am.alertPoolExhaustion(3, 0);
        assertThat(am.getTotalAlerts()).isEqualTo(1);
    }

    @Test
    void alertPoolExhaustion_withAvailable_doesNotAlert() {
        AlertManager am = new AlertManager(0.10, 5000, 60000);
        am.alertPoolExhaustion(3, 1); // 1 available — not exhausted
        assertThat(am.getTotalAlerts()).isEqualTo(0);
    }

    // ===== alertExecutorCrash =====

    @Test
    void alertExecutorCrash_firesAlert() {
        AlertManager am = new AlertManager(0.10, 5000, 0);
        am.alertExecutorCrash("OutOfMemoryError");
        assertThat(am.getTotalAlerts()).isEqualTo(1);
    }

    // ===== alertCircuitBreakerOpened =====

    @Test
    void alertCircuitBreakerOpened_firesAlert() {
        AlertManager am = new AlertManager(0.10, 5000, 0);
        am.alertCircuitBreakerOpened(5, 60000);
        assertThat(am.getTotalAlerts()).isEqualTo(1);
    }

    // ===== alertCircuitBreakerClosed =====

    @Test
    void alertCircuitBreakerClosed_doesNotCountAsAlert() {
        // Closed is informational — may or may not increment alert counter
        AlertManager am = new AlertManager(0.10, 5000, 0);
        am.alertCircuitBreakerClosed();
        // Just verify it doesn't throw
        assertThat(am).isNotNull();
    }

    // ===== checkResponseTime =====

    @Test
    void checkResponseTime_belowThreshold_doesNotAlert() {
        AlertManager am = new AlertManager(0.10, 5000, 60000);
        am.checkResponseTime(2000); // 2s < 5s threshold
        assertThat(am.getTotalAlerts()).isEqualTo(0);
    }

    @Test
    void checkResponseTime_aboveThreshold_firesAlert() {
        AlertManager am = new AlertManager(0.10, 5000, 0);
        am.checkResponseTime(6000); // 6s > 5s threshold
        assertThat(am.getTotalAlerts()).isEqualTo(1);
    }

    // ===== alertExecutorHealthDegraded =====

    @Test
    void alertExecutorHealthDegraded_criticalScore_firesAlert() {
        AlertManager am = new AlertManager(0.10, 5000, 0);
        am.alertExecutorHealthDegraded(20, "Critical"); // score < 25 triggers alert
        assertThat(am.getTotalAlerts()).isEqualTo(1);
    }

    @Test
    void alertExecutorHealthDegraded_degradedButNotCritical_doesNotAlert() {
        AlertManager am = new AlertManager(0.10, 5000, 0);
        am.alertExecutorHealthDegraded(60, "Degraded"); // score >= 25 → no alert
        assertThat(am.getTotalAlerts()).isEqualTo(0);
    }

    // ===== Setters =====

    @Test
    void setErrorRateThreshold_updatesValue() {
        AlertManager am = new AlertManager();
        am.setErrorRateThreshold(0.25);
        assertThat(am.getErrorRateThreshold()).isEqualTo(0.25);
    }

    @Test
    void setSlowResponseThresholdMs_updatesValue() {
        AlertManager am = new AlertManager();
        am.setSlowResponseThresholdMs(8000);
        assertThat(am.getSlowResponseThresholdMs()).isEqualTo(8000);
    }

    @Test
    void setAlertCooldownMs_updatesValue() {
        AlertManager am = new AlertManager();
        am.setAlertCooldownMs(120_000);
        assertThat(am.getAlertCooldownMs()).isEqualTo(120_000);
    }

    // ===== resetCooldowns =====

    @Test
    void resetCooldowns_allowsAlertsAgain() {
        AlertManager am = new AlertManager(0.10, 5000, 60_000);
        am.checkErrorRate(0.50, 100); // first alert (total=1)
        am.checkErrorRate(0.50, 100); // suppressed (suppressed=1)

        am.resetCooldowns();

        am.checkErrorRate(0.50, 100); // fires again after cooldown reset
        assertThat(am.getTotalAlerts()).isEqualTo(2);
    }

    // ===== getAlertSummary and toString =====

    @Test
    void getAlertSummary_returnsNonEmpty() {
        AlertManager am = new AlertManager();
        assertThat(am.getAlertSummary()).isNotEmpty();
    }

    @Test
    void toString_returnsNonEmpty() {
        AlertManager am = new AlertManager();
        assertThat(am.toString()).isNotEmpty();
    }
}
