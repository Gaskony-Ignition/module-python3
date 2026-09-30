package com.gaskony.python3.gateway;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for Python3Exception.
 * Tests the custom exception class.
 */
class Python3ExceptionTest {

    @Test
    void testExceptionWithMessage() {
        // Create exception with message
        Python3Exception exception = new Python3Exception("Test error message");

        assertThat(exception.getMessage()).isEqualTo("Test error message");
        assertThat(exception.getCause()).isNull();
    }

    @Test
    void testExceptionWithMessageAndCause() {
        // Create exception with message and cause
        Exception cause = new RuntimeException("Original cause");
        Python3Exception exception = new Python3Exception("Test error", cause);

        assertThat(exception.getMessage()).isEqualTo("Test error");
        assertThat(exception.getCause()).isEqualTo(cause);
    }

    @Test
    void testExceptionWithTraceback() {
        // Create exception with traceback
        Python3Exception exception = new Python3Exception("Error occurred", "Traceback line 1\nTraceback line 2");

        assertThat(exception.getMessage()).contains("Error occurred");
        assertThat(exception.getMessage()).contains("Traceback line 1");
    }

    @Test
    void testExceptionIsThrowable() {
        // Verify it can be thrown
        assertThatThrownBy(() -> {
            throw new Python3Exception("Test exception");
        })
            .isInstanceOf(Python3Exception.class)
            .hasMessage("Test exception");
    }

    @Test
    void testExceptionIsException() {
        // Verify it's a proper Exception subclass
        Python3Exception exception = new Python3Exception("Test");

        assertThat(exception).isInstanceOf(Exception.class);
        assertThat(exception).isInstanceOf(Throwable.class);
    }

    @Test
    void testExceptionWithNullMessage() {
        // Exception with null message
        Python3Exception exception = new Python3Exception((String) null);

        assertThat(exception.getMessage()).isNull();
    }

    @Test
    void testExceptionWithEmptyMessage() {
        // Exception with empty message
        Python3Exception exception = new Python3Exception("");

        assertThat(exception.getMessage()).isEmpty();
    }

    @Test
    void testExceptionCauseChain() {
        // Test exception chaining
        Exception root = new RuntimeException("Root cause");
        Exception middle = new IllegalStateException("Middle", root);
        Python3Exception top = new Python3Exception("Top level", middle);

        assertThat(top.getCause()).isEqualTo(middle);
        assertThat(top.getCause().getCause()).isEqualTo(root);
    }
}
