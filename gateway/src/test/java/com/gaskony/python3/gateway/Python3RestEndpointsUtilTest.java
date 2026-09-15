package com.gaskony.python3.gateway;

import com.inductiveautomation.ignition.common.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for package-private utility methods in Python3RestEndpoints.
 *
 * Tests are in the same package to access package-private static helpers.
 * No Ignition SDK runtime is required for these pure-logic methods.
 */
class Python3RestEndpointsUtilTest {

    // ===== validateCode =====

    @Test
    void validateCode_validCode_doesNotThrow() {
        assertThatCode(() -> Python3RestEndpoints.validateCode("x = 2 + 2"))
            .doesNotThrowAnyException();
    }

    @Test
    void validateCode_null_throwsIllegalArgument() {
        assertThatThrownBy(() -> Python3RestEndpoints.validateCode(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("null");
    }

    @Test
    void validateCode_emptyString_throwsIllegalArgument() {
        assertThatThrownBy(() -> Python3RestEndpoints.validateCode(""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("empty");
    }

    @Test
    void validateCode_whitespaceOnly_throwsIllegalArgument() {
        assertThatThrownBy(() -> Python3RestEndpoints.validateCode("   \n  "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("empty");
    }

    @Test
    void validateCode_tooLarge_throwsIllegalArgument() {
        // MAX_CODE_SIZE is 1MB = 1_048_576 bytes
        String bigCode = "x" + "x".repeat(1_048_577);
        assertThatThrownBy(() -> Python3RestEndpoints.validateCode(bigCode))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("exceeds maximum");
    }

    // ===== validateScriptName =====

    @Test
    void validateScriptName_validName_doesNotThrow() {
        assertThatCode(() -> Python3RestEndpoints.validateScriptName("my_script"))
            .doesNotThrowAnyException();
        assertThatCode(() -> Python3RestEndpoints.validateScriptName("Script 1.0"))
            .doesNotThrowAnyException();
        assertThatCode(() -> Python3RestEndpoints.validateScriptName("hello-world"))
            .doesNotThrowAnyException();
    }

    @Test
    void validateScriptName_null_throwsIllegalArgument() {
        assertThatThrownBy(() -> Python3RestEndpoints.validateScriptName(null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validateScriptName_empty_throwsIllegalArgument() {
        assertThatThrownBy(() -> Python3RestEndpoints.validateScriptName(""))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validateScriptName_invalidChars_throwsIllegalArgument() {
        assertThatThrownBy(() -> Python3RestEndpoints.validateScriptName("my/script!"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("invalid characters");
    }

    @Test
    void validateScriptName_slashChar_throwsIllegalArgument() {
        assertThatThrownBy(() -> Python3RestEndpoints.validateScriptName("path/script"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validateScriptName_sqlKeyword_throwsIllegalArgument() {
        assertThatThrownBy(() -> Python3RestEndpoints.validateScriptName("DROP"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("forbidden keyword");
    }

    @Test
    void validateScriptName_tooLong_throwsIllegalArgument() {
        String longName = "a".repeat(300);
        assertThatThrownBy(() -> Python3RestEndpoints.validateScriptName(longName))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("too long");
    }

    // ===== validateFolderPath =====

    @Test
    void validateFolderPath_null_doesNotThrow() {
        assertThatCode(() -> Python3RestEndpoints.validateFolderPath(null))
            .doesNotThrowAnyException();
    }

    @Test
    void validateFolderPath_emptyString_doesNotThrow() {
        assertThatCode(() -> Python3RestEndpoints.validateFolderPath(""))
            .doesNotThrowAnyException();
    }

    @Test
    void validateFolderPath_validPath_doesNotThrow() {
        assertThatCode(() -> Python3RestEndpoints.validateFolderPath("my/folder"))
            .doesNotThrowAnyException();
        assertThatCode(() -> Python3RestEndpoints.validateFolderPath("scripts/subdir-1"))
            .doesNotThrowAnyException();
    }

    @Test
    void validateFolderPath_pathTraversal_throwsIllegalArgument() {
        assertThatThrownBy(() -> Python3RestEndpoints.validateFolderPath("../secret"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("forbidden pattern");
    }

    @Test
    void validateFolderPath_sqlKeyword_throwsIllegalArgument() {
        assertThatThrownBy(() -> Python3RestEndpoints.validateFolderPath("DROP"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validateFolderPath_invalidChars_throwsIllegalArgument() {
        assertThatThrownBy(() -> Python3RestEndpoints.validateFolderPath("path?query"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("invalid characters");
    }

    @Test
    void validateFolderPath_tooLong_throwsIllegalArgument() {
        // MAX_FOLDER_PATH_LENGTH is 1000 characters
        String longPath = "a/".repeat(600);
        assertThatThrownBy(() -> Python3RestEndpoints.validateFolderPath(longPath))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("too long");
    }

    // ===== sanitizeForLogging =====

    @Test
    void sanitizeForLogging_null_returnsEmpty() {
        assertThat(Python3RestEndpoints.sanitizeForLogging(null)).isEqualTo("");
    }

    @Test
    void sanitizeForLogging_normalCode_returnsUnchanged() {
        assertThat(Python3RestEndpoints.sanitizeForLogging("x = 2 + 2")).isEqualTo("x = 2 + 2");
    }

    @Test
    void sanitizeForLogging_password_redacted() {
        String result = Python3RestEndpoints.sanitizeForLogging("password='secret123'");
        assertThat(result).contains("password='***'");
        assertThat(result).doesNotContain("secret123");
    }

    @Test
    void sanitizeForLogging_apiKey_redacted() {
        String result = Python3RestEndpoints.sanitizeForLogging("api_key='mykey'");
        assertThat(result).contains("api_key='***'");
    }

    @Test
    void sanitizeForLogging_secret_redacted() {
        String result = Python3RestEndpoints.sanitizeForLogging("secret='mysecret'");
        assertThat(result).contains("secret='***'");
    }

    @Test
    void sanitizeForLogging_token_redacted() {
        String result = Python3RestEndpoints.sanitizeForLogging("token='mytoken'");
        assertThat(result).contains("token='***'");
    }

    @Test
    void sanitizeForLogging_longString_truncated() {
        String longData = "x".repeat(300);
        String result = Python3RestEndpoints.sanitizeForLogging(longData);
        assertThat(result).contains("truncated");
        assertThat(result.length()).isLessThan(300);
    }

    // ===== hashCode =====

    @Test
    void hashCode_returnsFixedLength16() {
        String hash = Python3RestEndpoints.hashCode("print('hello')");
        assertThat(hash).hasSize(16);
    }

    @Test
    void hashCode_sameInput_sameOutput() {
        String h1 = Python3RestEndpoints.hashCode("x = 1");
        String h2 = Python3RestEndpoints.hashCode("x = 1");
        assertThat(h1).isEqualTo(h2);
    }

    @Test
    void hashCode_differentInput_differentOutput() {
        String h1 = Python3RestEndpoints.hashCode("x = 1");
        String h2 = Python3RestEndpoints.hashCode("x = 2");
        assertThat(h1).isNotEqualTo(h2);
    }

    @Test
    void hashCode_onlyHexChars() {
        String hash = Python3RestEndpoints.hashCode("test code");
        assertThat(hash).matches("[0-9a-f]{16}");
    }

    // ===== mapToJson =====

    @Test
    void mapToJson_stringValue_addsStringProperty() {
        Map<String, Object> m = new HashMap<>();
        m.put("key", "value");
        JsonObject json = Python3RestEndpoints.mapToJson(m);
        assertThat(json.get("key").getAsString()).isEqualTo("value");
    }

    @Test
    void mapToJson_integerValue_addsNumberProperty() {
        Map<String, Object> m = new HashMap<>();
        m.put("count", 42);
        JsonObject json = Python3RestEndpoints.mapToJson(m);
        assertThat(json.get("count").getAsInt()).isEqualTo(42);
    }

    @Test
    void mapToJson_booleanValue_addsBooleanProperty() {
        Map<String, Object> m = new HashMap<>();
        m.put("active", true);
        JsonObject json = Python3RestEndpoints.mapToJson(m);
        assertThat(json.get("active").getAsBoolean()).isTrue();
    }

    @Test
    void mapToJson_doubleValue_addsDoubleProperty() {
        Map<String, Object> m = new HashMap<>();
        m.put("rate", 3.14);
        JsonObject json = Python3RestEndpoints.mapToJson(m);
        assertThat(json.get("rate").getAsDouble()).isEqualTo(3.14);
    }

    @Test
    void mapToJson_unknownObjectValue_usesToString() {
        Map<String, Object> m = new HashMap<>();
        m.put("obj", new Object() {
            @Override public String toString() { return "custom"; }
        });
        JsonObject json = Python3RestEndpoints.mapToJson(m);
        assertThat(json.get("obj").getAsString()).isEqualTo("custom");
    }

    @Test
    void mapToJson_nullValue_notIncluded() {
        Map<String, Object> m = new HashMap<>();
        m.put("nullKey", null);
        JsonObject json = Python3RestEndpoints.mapToJson(m);
        assertThat(json.has("nullKey")).isFalse();
    }

    @Test
    void mapToJson_emptyMap_returnsEmptyJsonObject() {
        JsonObject json = Python3RestEndpoints.mapToJson(new HashMap<>());
        assertThat(json.size()).isEqualTo(0);
    }

    @Test
    void mapToJson_multipleTypes_allPresent() {
        Map<String, Object> m = new HashMap<>();
        m.put("name", "test");
        m.put("count", 5);
        m.put("active", false);
        JsonObject json = Python3RestEndpoints.mapToJson(m);
        assertThat(json.get("name").getAsString()).isEqualTo("test");
        assertThat(json.get("count").getAsInt()).isEqualTo(5);
        assertThat(json.get("active").getAsBoolean()).isFalse();
    }

    // ===== jsonToMap =====

    @Test
    void jsonToMap_stringField_returnsString() {
        JsonObject json = new JsonObject();
        json.addProperty("key", "value");
        Map<String, Object> map = Python3RestEndpoints.jsonToMap(json);
        assertThat(map.get("key")).isEqualTo("value");
    }

    @Test
    void jsonToMap_numberField_returnsNumber() {
        JsonObject json = new JsonObject();
        json.addProperty("count", 42);
        Map<String, Object> map = Python3RestEndpoints.jsonToMap(json);
        assertThat(((Number) map.get("count")).intValue()).isEqualTo(42);
    }

    @Test
    void jsonToMap_booleanField_returnsBoolean() {
        JsonObject json = new JsonObject();
        json.addProperty("flag", true);
        Map<String, Object> map = Python3RestEndpoints.jsonToMap(json);
        assertThat(map.get("flag")).isEqualTo(true);
    }

    @Test
    void jsonToMap_nullField_returnsNull() {
        JsonObject json = new JsonObject();
        json.add("nullKey", com.inductiveautomation.ignition.common.gson.JsonNull.INSTANCE);
        Map<String, Object> map = Python3RestEndpoints.jsonToMap(json);
        assertThat(map.get("nullKey")).isNull();
    }

    @Test
    void jsonToMap_emptyObject_returnsEmptyMap() {
        Map<String, Object> map = Python3RestEndpoints.jsonToMap(new JsonObject());
        assertThat(map).isEmpty();
    }
}
