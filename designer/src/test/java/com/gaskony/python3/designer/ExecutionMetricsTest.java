package com.gaskony.python3.designer;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the v4.4.0 fix to the Diagnostics dialog's execution stats.
 *
 * <p>{@link ExecutionMetrics} reads {@code totalExecutions} /
 * {@code successfulExecutions} / {@code averageExecutionTime} from the TOP LEVEL
 * of the {@code getDiagnostics()} payload. Before v4.4.0 the gateway never put
 * those keys there, so every field was structurally zero and Success Rate showed
 * a permanent red 0.0% even after running scripts. This test pins the contract:
 * the panel must populate from a top-level payload, and must still degrade to
 * zeros (not throw) when the keys are missing.</p>
 */
class ExecutionMetricsTest {

    @Test
    void parsesTopLevelExecutionKeys() {
        // Matches the v4.4.0 Python3RpcHandler.getDiagnostics() shape.
        String payload = "{"
                + "\"available\":true,"
                + "\"poolStats\":{\"poolSize\":3,\"available\":3,\"inUse\":0,\"healthy\":3},"
                + "\"totalExecutions\":25,"
                + "\"successfulExecutions\":20,"
                + "\"failedExecutions\":5,"
                + "\"averageExecutionTime\":12.5,"
                + "\"timestamp\":1234567890}";

        ExecutionMetrics m = ExecutionMetrics.fromJson(payload);

        assertThat(m.getTotalExecutions()).isEqualTo(25);
        assertThat(m.getSuccessfulExecutions()).isEqualTo(20);
        assertThat(m.getFailedExecutions()).isEqualTo(5);
        assertThat(m.getAverageExecutionTime()).isEqualTo(12.5);
        assertThat(m.getSuccessRate()).isEqualTo(80.0);
    }

    @Test
    void nestedExecutionKeysDoNotCount() {
        // The OLD broken behaviour: stats nested under poolStats instead of top
        // level would read as zero. Documents exactly what regressed.
        String payload = "{\"poolStats\":{\"totalExecutions\":25,\"successfulExecutions\":20}}";

        ExecutionMetrics m = ExecutionMetrics.fromJson(payload);

        assertThat(m.getTotalExecutions()).isZero();
        assertThat(m.getSuccessRate()).isZero();
    }

    @Test
    void missingKeysDegradeToZeroWithoutThrowing() {
        ExecutionMetrics m = ExecutionMetrics.fromJson("{\"available\":true}");
        assertThat(m.getTotalExecutions()).isZero();
        assertThat(m.getAverageExecutionTime()).isZero();
        assertThat(m.getSuccessRate()).isZero();
    }
}
