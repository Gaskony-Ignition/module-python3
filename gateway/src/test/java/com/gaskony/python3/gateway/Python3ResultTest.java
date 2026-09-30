package com.gaskony.python3.gateway;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for Python3Result.
 * Tests the result object returned from Python execution.
 */
class Python3ResultTest {

    @Test
    void testSuccessResult() {
        // Create a successful result
        Python3Result result = new Python3Result(true, "test value", null, null);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getResult()).isEqualTo("test value");
        assertThat(result.getError()).isNull();
        assertThat(result.getTraceback()).isNull();
    }

    @Test
    void testFailureResult() {
        // Create a failed result
        Python3Result result = new Python3Result(false, null, "Error message", "Stack trace");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getResult()).isNull();
        assertThat(result.getError()).isEqualTo("Error message");
        assertThat(result.getTraceback()).isEqualTo("Stack trace");
    }

    @Test
    void testSuccessWithNullResult() {
        // Success but no result value (e.g., from execute without return)
        Python3Result result = new Python3Result(true, null, null, null);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getResult()).isNull();
    }

    @Test
    void testResultWithIntegerValue() {
        // Result with integer value
        Python3Result result = new Python3Result(true, 42, null, null);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getResult()).isEqualTo(42);
    }

    @Test
    void testResultWithDoubleValue() {
        // Result with double value
        Python3Result result = new Python3Result(true, 3.14, null, null);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getResult()).isEqualTo(3.14);
    }

    @Test
    void testResultWithStringValue() {
        // Result with string value
        Python3Result result = new Python3Result(true, "hello world", null, null);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getResult()).isEqualTo("hello world");
    }

    @Test
    void testErrorWithoutTraceback() {
        // Error without traceback
        Python3Result result = new Python3Result(false, null, "Error occurred", null);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).isEqualTo("Error occurred");
        assertThat(result.getTraceback()).isNull();
    }

    @Test
    void testToString() {
        // Test toString for successful result
        Python3Result success = new Python3Result(true, "value", null, null);
        assertThat(success.toString()).contains("success=true");

        // Test toString for failed result
        Python3Result failure = new Python3Result(false, null, "error", "trace");
        assertThat(failure.toString()).contains("success=false");
    }
}
