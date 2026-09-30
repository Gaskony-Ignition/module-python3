package com.gaskony.python3.gateway;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for Python3SecurityUtils.
 * Tests code hashing and security utility functions.
 */
class Python3SecurityUtilsTest {

    @Test
    void testHashCode_ValidCode() {
        String code = "result = 2 + 2";
        String hash = Python3SecurityUtils.hashCode(code);

        // SHA-256 hash should be 64 hex characters
        assertThat(hash).hasSize(64);
        assertThat(hash).matches("[0-9a-f]{64}");
    }

    @Test
    void testHashCode_SameCodeSameHash() {
        String code = "print('hello world')";
        String hash1 = Python3SecurityUtils.hashCode(code);
        String hash2 = Python3SecurityUtils.hashCode(code);

        // Same code should produce same hash
        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    void testHashCode_DifferentCodeDifferentHash() {
        String code1 = "result = 2 + 2";
        String code2 = "result = 3 + 3";
        String hash1 = Python3SecurityUtils.hashCode(code1);
        String hash2 = Python3SecurityUtils.hashCode(code2);

        // Different code should produce different hash
        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void testHashCode_EmptyString() {
        String code = "";
        String hash = Python3SecurityUtils.hashCode(code);

        // Empty string should still produce valid hash
        assertThat(hash).hasSize(64);
        assertThat(hash).matches("[0-9a-f]{64}");
    }

    @Test
    void testHashCode_NullString() {
        String hash = Python3SecurityUtils.hashCode(null);

        // Null should be treated as empty string
        assertThat(hash).hasSize(64);
        assertThat(hash).matches("[0-9a-f]{64}");
        assertThat(hash).isEqualTo(Python3SecurityUtils.hashCode(""));
    }

    @Test
    void testHashCode_LongCode() {
        // Test with long code snippet
        String code = """
            import os
            import sys
            import json

            def process_data(data):
                result = []
                for item in data:
                    if item > 0:
                        result.append(item * 2)
                return result

            data = [1, 2, 3, 4, 5]
            result = process_data(data)
            print(result)
            """;

        String hash = Python3SecurityUtils.hashCode(code);

        assertThat(hash).hasSize(64);
        assertThat(hash).matches("[0-9a-f]{64}");
    }

    @Test
    void testHashCode_SpecialCharacters() {
        String code = "result = 'hello\\nworld\\t🐍'";
        String hash = Python3SecurityUtils.hashCode(code);

        // Special characters should be handled correctly
        assertThat(hash).hasSize(64);
        assertThat(hash).matches("[0-9a-f]{64}");
    }

    @Test
    void testHashCode_CaseSensitive() {
        String code1 = "result = 'HELLO'";
        String code2 = "result = 'hello'";
        String hash1 = Python3SecurityUtils.hashCode(code1);
        String hash2 = Python3SecurityUtils.hashCode(code2);

        // Hash should be case-sensitive
        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void testHashCode_WhitespaceSensitive() {
        String code1 = "result=2+2";
        String code2 = "result = 2 + 2";
        String hash1 = Python3SecurityUtils.hashCode(code1);
        String hash2 = Python3SecurityUtils.hashCode(code2);

        // Hash should be whitespace-sensitive
        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void testGetCurrentUser_ReturnsNull() {
        // TODO: Will be implemented when Ignition context API is integrated
        String user = Python3SecurityUtils.getCurrentUser();

        assertThat(user).isNull();
    }

    @Test
    void testGetSourceIP_ReturnsNull() {
        // TODO: Will be implemented when REST API integration is complete
        String ip = Python3SecurityUtils.getSourceIP();

        assertThat(ip).isNull();
    }

    @Test
    void testGetEndpoint_ValidInputs() {
        String endpoint = Python3SecurityUtils.getEndpoint("REST", "exec");

        assertThat(endpoint).isEqualTo("REST:exec");
    }

    @Test
    void testGetEndpoint_NullSource() {
        String endpoint = Python3SecurityUtils.getEndpoint(null, "exec");

        assertThat(endpoint).isEqualTo("UNKNOWN:exec");
    }

    @Test
    void testGetEndpoint_NullMethod() {
        String endpoint = Python3SecurityUtils.getEndpoint("REST", null);

        assertThat(endpoint).isEqualTo("REST:unknown");
    }

    @Test
    void testGetEndpoint_BothNull() {
        String endpoint = Python3SecurityUtils.getEndpoint(null, null);

        assertThat(endpoint).isEqualTo("UNKNOWN:unknown");
    }

    @Test
    void testGetEndpoint_VariousEndpoints() {
        assertThat(Python3SecurityUtils.getEndpoint("REST", "eval")).isEqualTo("REST:eval");
        assertThat(Python3SecurityUtils.getEndpoint("SCRIPT", "system.python3.exec")).isEqualTo("SCRIPT:system.python3.exec");
        assertThat(Python3SecurityUtils.getEndpoint("DESIGNER", "execute")).isEqualTo("DESIGNER:execute");
    }
}
