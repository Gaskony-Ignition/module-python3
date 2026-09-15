package com.gaskony.python3.gateway;

import com.inductiveautomation.ignition.common.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for ApiResponse factory class.
 *
 * ApiResponse has no external dependencies — pure unit tests with no mocking.
 */
class ApiResponseTest {

    // ===== success() =====

    @Test
    void success_noArgs_returnsSuccessTrue() {
        JsonObject r = ApiResponse.success();
        assertThat(r.get("success").getAsBoolean()).isTrue();
        assertThat(r.has("result")).isFalse();
        assertThat(r.has("error")).isFalse();
    }

    // ===== success(String) =====

    @Test
    void success_withString_returnsResultField() {
        JsonObject r = ApiResponse.success("hello");
        assertThat(r.get("success").getAsBoolean()).isTrue();
        assertThat(r.get("result").getAsString()).isEqualTo("hello");
    }

    @Test
    void success_withNullString_resultFieldIsNull() {
        JsonObject r = ApiResponse.success((String) null);
        assertThat(r.get("success").getAsBoolean()).isTrue();
        assertThat(r.get("result").isJsonNull()).isTrue();
    }

    @Test
    void success_withEmptyString_resultFieldIsEmpty() {
        JsonObject r = ApiResponse.success("");
        assertThat(r.get("result").getAsString()).isEqualTo("");
    }

    // ===== success(Number) =====

    @Test
    void success_withInteger_returnsNumericResult() {
        JsonObject r = ApiResponse.success((Number) 42);
        assertThat(r.get("success").getAsBoolean()).isTrue();
        assertThat(r.get("result").getAsInt()).isEqualTo(42);
    }

    @Test
    void success_withDouble_returnsNumericResult() {
        JsonObject r = ApiResponse.success((Number) 3.14);
        assertThat(r.get("result").getAsDouble()).isEqualTo(3.14);
    }

    @Test
    void success_withLong_returnsNumericResult() {
        JsonObject r = ApiResponse.success((Number) Long.MAX_VALUE);
        assertThat(r.get("result").getAsLong()).isEqualTo(Long.MAX_VALUE);
    }

    // ===== success(Boolean) =====

    @Test
    void success_withTrue_returnsBooleanResult() {
        JsonObject r = ApiResponse.success(Boolean.TRUE);
        assertThat(r.get("success").getAsBoolean()).isTrue();
        assertThat(r.get("result").getAsBoolean()).isTrue();
    }

    @Test
    void success_withFalse_returnsBooleanFalseResult() {
        JsonObject r = ApiResponse.success(Boolean.FALSE);
        assertThat(r.get("success").getAsBoolean()).isTrue();
        assertThat(r.get("result").getAsBoolean()).isFalse();
    }

    // ===== error(String) =====

    @Test
    void error_withMessage_returnsSuccessFalseAndError() {
        JsonObject r = ApiResponse.error("Something went wrong");
        assertThat(r.get("success").getAsBoolean()).isFalse();
        assertThat(r.get("error").getAsString()).isEqualTo("Something went wrong");
    }

    @Test
    void error_withNullMessage_errorFieldIsNull() {
        JsonObject r = ApiResponse.error((String) null);
        assertThat(r.get("success").getAsBoolean()).isFalse();
        assertThat(r.get("error").isJsonNull()).isTrue();
    }

    @Test
    void error_withEmptyMessage_errorFieldIsEmpty() {
        JsonObject r = ApiResponse.error("");
        assertThat(r.get("error").getAsString()).isEqualTo("");
    }

    // ===== error(Throwable) =====

    @Test
    void error_withThrowable_usesExceptionMessage() {
        RuntimeException ex = new RuntimeException("boom");
        JsonObject r = ApiResponse.error(ex);
        assertThat(r.get("success").getAsBoolean()).isFalse();
        assertThat(r.get("error").getAsString()).isEqualTo("boom");
    }

    @Test
    void error_withNullThrowable_returnsUnknownError() {
        JsonObject r = ApiResponse.error((Throwable) null);
        assertThat(r.get("success").getAsBoolean()).isFalse();
        assertThat(r.get("error").getAsString()).isEqualTo("Unknown error");
    }

    @Test
    void error_withThrowableWithNullMessage_errorFieldIsNull() {
        RuntimeException ex = new RuntimeException((String) null);
        JsonObject r = ApiResponse.error(ex);
        // e.getMessage() returns null → error(null) → JsonNull field
        assertThat(r.get("success").getAsBoolean()).isFalse();
        assertThat(r.get("error").isJsonNull()).isTrue();
    }
}
