package com.gaskony.python3.gateway;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Python3Executor.
 *
 * These tests verify the executor's ability to:
 * - Execute Python code and capture output
 * - Inject variables into Python environment
 * - Handle timeouts and errors gracefully
 * - Process various data types and encodings
 * - Recover from failures
 *
 * @since v2.12.0 (Phase 2 Week 1-2: Testing Infrastructure)
 */
public class Python3ExecutorTest {

    private static final Logger logger = LoggerFactory.getLogger(Python3ExecutorTest.class);
    private static final String TEST_PYTHON_PATH = "python3";

    private Python3Executor executor;

    @BeforeEach
    public void setUp() throws IOException {
        logger.info("Setting up Python3ExecutorTest");
        executor = new Python3Executor(TEST_PYTHON_PATH);
    }

    @AfterEach
    public void tearDown() {
        if (executor != null) {
            try {
                executor.shutdown();
            } catch (Exception e) {
                logger.warn("Error during executor shutdown in tearDown", e);
            }
        }
    }

    /**
     * Test 1: Simple code execution with print statement.
     */
    @Test
    public void testSimpleCodeExecution() throws Exception {
        logger.info("Test: Simple code execution");

        Map<String, Object> variables = new HashMap<>();
        Python3Result result = executor.execute("print('hello world')", variables);

        assertNotNull(result, "Result should not be null");
        if (!result.isSuccess()) {
            logger.error("Execution failed! Error: {}", result.getError());
            logger.error("Traceback: {}", result.getTraceback());
        }
        assertTrue(result.isSuccess(), "Execution should succeed. Error: " + result.getError());
        assertNull(result.getError(), "Should not have error");

        // Output should contain "hello world"
        String output = (String) result.getResult();
        assertNotNull(output, "Output should not be null");
        assertTrue(output.contains("hello world"), "Output should contain 'hello world'");

        logger.info("✓ Simple execution test passed");
    }

    /**
     * Test 2: Variable injection into Python environment.
     */
    @Test
    public void testVariableInjection() throws Exception {
        logger.info("Test: Variable injection");

        Map<String, Object> variables = new HashMap<>();
        variables.put("x", 5);
        variables.put("y", 10);

        Python3Result result = executor.execute("sum_val = x + y\nprint(sum_val)", variables);

        assertTrue(result.isSuccess(), "Execution should succeed. Error: " + result.getError());
        String output = (String) result.getResult();
        assertTrue(output.contains("15"), "Output should contain '15'");

        logger.info("✓ Variable injection test passed");
    }

    /**
     * Test 3: Expression evaluation (eval mode).
     */
    @Test
    public void testExpressionEvaluation() throws Exception {
        logger.info("Test: Expression evaluation");

        Map<String, Object> variables = new HashMap<>();
        variables.put("a", 3);
        variables.put("b", 7);

        Python3Result result = executor.evaluate("a * b", variables);

        assertTrue(result.isSuccess(), "Evaluation should succeed. Error: " + result.getError());
        // evaluate() returns the actual value (Integer 21), not a string
        assertEquals(21, ((Number) result.getResult()).intValue(), "Result should be 21");

        logger.info("✓ Expression evaluation test passed");
    }

    /**
     * Test 4: Syntax error handling.
     * Verifies that syntax errors are caught and reported.
     */
    @Test
    public void testSyntaxErrorHandling() throws Exception {
        logger.info("Test: Syntax error handling");

        Map<String, Object> variables = new HashMap<>();
        Python3Result result = executor.execute("def invalid syntax here", variables);

        assertFalse(result.isSuccess(), "Execution should fail");
        assertNotNull(result.getError(), "Error message should be present");
        assertTrue(result.getError().toLowerCase().contains("syntax") ||
            result.getError().toLowerCase().contains("invalid"),
            "Error should mention syntax");

        logger.info("✓ Syntax error handling test passed");
    }

    /**
     * Test 5: Runtime error handling (undefined variable).
     */
    @Test
    public void testRuntimeErrorHandling() throws Exception {
        logger.info("Test: Runtime error handling");

        Map<String, Object> variables = new HashMap<>();
        Python3Result result = executor.execute("x = undefined_variable", variables);

        assertFalse(result.isSuccess(), "Execution should fail");
        assertNotNull(result.getError(), "Error message should be present");
        assertTrue(result.getError().toLowerCase().contains("name") ||
            result.getError().toLowerCase().contains("undefined") ||
            result.getError().toLowerCase().contains("not defined"),
            "Error should mention name or undefined");

        logger.info("✓ Runtime error handling test passed");
    }

    /**
     * Test 6: Timeout handling for long-running code.
     * Note: This test uses a short timeout to avoid slowing down test suite.
     */
    @Test
    public void testTimeoutHandling() {
        logger.info("Test: Timeout handling");

        Map<String, Object> variables = new HashMap<>();

        // Execute code that would run forever - should throw Python3Exception (wrapping timeout)
        Python3Exception exception = assertThrows(Python3Exception.class, () -> {
            executor.execute("import time\nwhile True: time.sleep(1)", variables);
        }, "Should have thrown Python3Exception");

        // Verify it's a timeout error (check both message and cause)
        System.out.println("===== TIMEOUT EXCEPTION MESSAGE =====");
        System.out.println(exception.getMessage());
        if (exception.getCause() != null) {
            System.out.println("Cause: " + exception.getCause().getMessage());
        }
        System.out.println("======================================");

        String fullMessage = exception.getMessage();
        if (exception.getCause() != null) {
            fullMessage += " " + exception.getCause().getMessage();
        }

        assertTrue(fullMessage.toLowerCase().contains("timeout") ||
                fullMessage.toLowerCase().contains("no response"),
            "Exception should indicate timeout. Actual message: " + fullMessage);

        logger.info("✓ Timeout handling test passed");
    }

    /**
     * Test 7: Large output handling.
     * Verifies that executor can handle large amounts of output.
     */
    @Test
    public void testLargeOutputHandling() throws Exception {
        logger.info("Test: Large output handling");

        Map<String, Object> variables = new HashMap<>();

        // Generate 1000 lines of output
        String code = "for i in range(1000):\n    print(f'Line {i}')";
        Python3Result result = executor.execute(code, variables);

        assertTrue(result.isSuccess(), "Execution should succeed");
        String output = (String) result.getResult();
        assertNotNull(output, "Output should not be null");

        // Should contain references to both early and late lines
        assertTrue(output.contains("Line 0"), "Output should contain Line 0");
        assertTrue(output.contains("Line 999"), "Output should contain Line 999");

        logger.info("✓ Large output handling test passed");
    }

    /**
     * Test 8: Unicode and special character handling.
     */
    @Test
    public void testUnicodeHandling() throws Exception {
        logger.info("Test: Unicode handling");

        Map<String, Object> variables = new HashMap<>();
        variables.put("emoji", "🐍");
        variables.put("chinese", "你好");
        variables.put("arabic", "مرحبا");

        String code = "print(emoji)\nprint(chinese)\nprint(arabic)";
        Python3Result result = executor.execute(code, variables);

        assertTrue(result.isSuccess(), "Execution should succeed");
        String output = (String) result.getResult();

        assertTrue(output.contains("🐍"), "Output should contain emoji");
        assertTrue(output.contains("你好"), "Output should contain Chinese");
        assertTrue(output.contains("مرحبا"), "Output should contain Arabic");

        logger.info("✓ Unicode handling test passed");
    }

    /**
     * Test 9: Multiple variable types injection.
     * Tests integers, floats, strings, booleans, and null.
     */
    @Test
    public void testMultipleVariableTypes() throws Exception {
        logger.info("Test: Multiple variable types");

        Map<String, Object> variables = new HashMap<>();
        variables.put("int_var", 42);
        variables.put("float_var", 3.14);
        variables.put("str_var", "hello");
        variables.put("bool_var", true);
        variables.put("null_var", null);

        String code =
            "print(f'int: {int_var}')\n" +
            "print(f'float: {float_var}')\n" +
            "print(f'str: {str_var}')\n" +
            "print(f'bool: {bool_var}')\n" +
            "print(f'null: {null_var}')";

        Python3Result result = executor.execute(code, variables);

        if (!result.isSuccess()) {
            logger.error("Execution failed! Error: {}", result.getError());
            logger.error("Traceback: {}", result.getTraceback());
        }
        assertTrue(result.isSuccess(), "Execution should succeed. Error: " + result.getError());
        String output = (String) result.getResult();

        assertTrue(output.contains("int: 42"), "Output should contain int");
        assertTrue(output.contains("float: 3.14"), "Output should contain float");
        assertTrue(output.contains("str: hello"), "Output should contain str");
        assertTrue(output.contains("bool: True"), "Output should contain bool");
        assertTrue(output.contains("null: None"), "Output should contain None");

        logger.info("✓ Multiple variable types test passed");
    }

    /**
     * Test 10: Module import functionality.
     * Verifies that standard library modules can be imported.
     */
    @Test
    public void testModuleImport() throws Exception {
        logger.info("Test: Module import");

        Map<String, Object> variables = new HashMap<>();

        String code =
            "import math\n" +
            "import json\n" +
            "sqrt_val = math.sqrt(16)\n" +
            "data = json.dumps({'value': sqrt_val})\n" +
            "print(f'sqrt(16) = {sqrt_val}, json = {data}')";

        Python3Result result = executor.execute(code, variables);

        assertTrue(result.isSuccess(), "Execution should succeed. Error: " + result.getError());
        String output = (String) result.getResult();
        assertTrue(output.contains("sqrt(16) = 4"), "Output should contain sqrt result");

        logger.info("✓ Module import test passed");
    }

    /**
     * Test 11: Empty code execution.
     * Verifies handling of empty or whitespace-only code.
     */
    @Test
    public void testEmptyCodeExecution() throws Exception {
        logger.info("Test: Empty code execution");

        Map<String, Object> variables = new HashMap<>();

        // Empty string
        Python3Result result1 = executor.execute("", variables);
        assertTrue(result1.isSuccess(), "Empty code should succeed");

        // Whitespace only
        Python3Result result2 = executor.execute("   \n  \n  ", variables);
        assertTrue(result2.isSuccess(), "Whitespace-only code should succeed");

        logger.info("✓ Empty code execution test passed");
    }

    /**
     * Test 12: Exception with traceback.
     * Verifies that full traceback is captured for debugging.
     */
    @Test
    public void testExceptionTraceback() throws Exception {
        logger.info("Test: Exception traceback");

        Map<String, Object> variables = new HashMap<>();

        String code =
            "def func1():\n" +
            "    func2()\n" +
            "\n" +
            "def func2():\n" +
            "    raise ValueError('Test error')\n" +
            "\n" +
            "func1()";

        Python3Result result = executor.execute(code, variables);

        if (result.isSuccess()) {
            logger.error("Expected execution to fail but it succeeded! Result: {}", result.getResult());
        }
        assertFalse(result.isSuccess(), "Execution should fail");
        assertNotNull(result.getError(), "Error should be present");

        String error = result.getError();
        System.out.println("===== ERROR MESSAGE =====");
        System.out.println(error);
        System.out.println("=========================");
        assertTrue(error.contains("ValueError"), "Error should contain ValueError. Actual error: " + error);
        assertTrue(error.contains("Test error"), "Error should contain 'Test error'. Actual error: " + error);

        // Check traceback (stored in separate field in Python3Result)
        String traceback = result.getTraceback();
        System.out.println("===== TRACEBACK =====");
        System.out.println(traceback);
        System.out.println("=====================");

        // Traceback should show the call stack
        assertTrue(traceback != null && (traceback.contains("func1") || traceback.contains("func2") || traceback.contains("Traceback")),
            "Traceback should contain call stack info. Actual traceback: " + traceback);

        logger.info("✓ Exception traceback test passed");
    }

    /**
     * Test 13: Multiple sequential executions.
     * Verifies that executor can be reused for multiple executions.
     */
    @Test
    public void testMultipleSequentialExecutions() throws Exception {
        logger.info("Test: Multiple sequential executions");

        Map<String, Object> variables = new HashMap<>();

        // Execute 10 different scripts sequentially
        for (int i = 0; i < 10; i++) {
            variables.clear();
            variables.put("iteration", i);

            String code = "value = iteration * 2\nprint(f'Result: {value}')";
            Python3Result result = executor.execute(code, variables);

            assertTrue(result.isSuccess(), "Execution " + i + " should succeed. Error: " + result.getError());
            assertTrue(((String) result.getResult()).contains("Result: " + (i * 2)),
                "Output " + i + " should contain correct result");
        }

        logger.info("✓ Multiple sequential executions test passed");
    }

    /**
     * Test 14: JSON data handling.
     * Verifies that JSON data can be passed and parsed.
     */
    @Test
    public void testJsonDataHandling() throws Exception {
        logger.info("Test: JSON data handling");

        Map<String, Object> variables = new HashMap<>();
        variables.put("json_str", "{\"name\": \"Python\", \"version\": 3}");

        String code =
            "import json\n" +
            "data = json.loads(json_str)\n" +
            "print(f\"Name: {data['name']}\")\n" +
            "print(f\"Version: {data['version']}\")";

        Python3Result result = executor.execute(code, variables);

        assertTrue(result.isSuccess(), "Execution should succeed");
        String output = (String) result.getResult();
        assertTrue(output.contains("Name: Python"), "Output should contain name");
        assertTrue(output.contains("Version: 3"), "Output should contain version");

        logger.info("✓ JSON data handling test passed");
    }

    /**
     * Test 15: Division by zero error handling.
     */
    @Test
    public void testDivisionByZeroError() throws Exception {
        logger.info("Test: Division by zero error");

        Map<String, Object> variables = new HashMap<>();
        Python3Result result = executor.execute("x = 1 / 0", variables);

        assertFalse(result.isSuccess(), "Execution should fail");
        assertNotNull(result.getError(), "Error should be present");
        assertTrue(result.getError().toLowerCase().contains("division") ||
            result.getError().toLowerCase().contains("zerodivision"),
            "Error should mention division");

        logger.info("✓ Division by zero error test passed");
    }

    /**
     * Test 16: Process health check.
     * Verifies that executor reports correct alive status.
     */
    @Test
    public void testProcessHealthCheck() throws Exception {
        logger.info("Test: Process health check");

        // Initially should be alive
        assertTrue(executor.isAlive(), "Executor should be alive initially");

        // Execute some code
        Map<String, Object> variables = new HashMap<>();
        executor.execute("x = 1 + 1", variables);

        // Should still be alive after execution
        assertTrue(executor.isAlive(), "Executor should still be alive after execution");

        // Shutdown
        executor.shutdown();

        // Should not be alive after shutdown
        assertFalse(executor.isAlive(), "Executor should not be alive after shutdown");

        executor = null; // Don't double-shutdown in tearDown

        logger.info("✓ Process health check test passed");
    }

    /**
     * Test 17: String with quotes handling.
     * Verifies proper escaping of special characters.
     */
    @Test
    public void testStringWithQuotesHandling() throws Exception {
        logger.info("Test: String with quotes handling");

        Map<String, Object> variables = new HashMap<>();
        variables.put("text", "He said \"Hello\" and she said 'Hi'");

        String code = "print(text)";
        Python3Result result = executor.execute(code, variables);

        assertTrue(result.isSuccess(), "Execution should succeed");
        String output = (String) result.getResult();
        assertTrue(output.contains("\"Hello\""), "Output should contain double quotes");
        assertTrue(output.contains("'Hi'"), "Output should contain single quotes");

        logger.info("✓ String with quotes handling test passed");
    }

    /**
     * Test 18: Multi-line code with proper indentation.
     */
    @Test
    public void testMultiLineIndentation() throws Exception {
        logger.info("Test: Multi-line indentation");

        Map<String, Object> variables = new HashMap<>();
        variables.put("numbers", "[1, 2, 3, 4, 5]");

        String code =
            "import json\n" +
            "nums = json.loads(numbers)\n" +
            "total = 0\n" +
            "for num in nums:\n" +
            "    if num % 2 == 0:\n" +
            "        total += num\n" +
            "print(f'Sum of even numbers: {total}')";

        Python3Result result = executor.execute(code, variables);

        assertTrue(result.isSuccess(), "Execution should succeed");
        String output = (String) result.getResult();
        assertTrue(output.contains("Sum of even numbers: 6"), "Output should contain sum");

        logger.info("✓ Multi-line indentation test passed");
    }
}
