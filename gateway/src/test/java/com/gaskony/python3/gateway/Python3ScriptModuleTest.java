package com.gaskony.python3.gateway;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link Python3ScriptModule}.
 *
 * <p>Updated for security review C13 (May 2026): the default
 * {@code securityMode} string passed downstream is now
 * {@code "DESIGNER_ADMIN"} (the legacy {@code "RESTRICTED"} mode was removed).
 * Tests also install a permissive {@link RoleResolver} stub so the
 * Administrator-role gate added in C13 doesn't block the existing happy-path
 * coverage; the gate itself is exercised by
 * {@link Python3ScriptModuleRoleGateTest}.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class Python3ScriptModuleTest {

    /** Default downstream security-mode wire value after the C13 cleanup. */
    private static final String DEFAULT_MODE = "DESIGNER_ADMIN";

    @Mock
    private GatewayHook mockGatewayHook;

    @Mock
    private Python3ProcessPool mockPool;

    @Mock
    private PythonDistributionManager mockDistributionManager;

    @Mock
    private Python3ScriptRepository mockScriptRepository;

    private Python3ScriptModule scriptModule;

    @BeforeEach
    void setUp() {
        // C13: install a permissive role resolver so the scripting role gate
        // doesn't block these tests. The gate itself is covered separately.
        Python3ScriptModule.setRoleResolverForTesting(new RoleResolver() {
            @Override
            boolean isScriptingAllowed() {
                return true;
            }
        });

        // Configure mock gateway hook
        when(mockGatewayHook.getProcessPool()).thenReturn(mockPool);
        when(mockGatewayHook.getDistributionManager()).thenReturn(mockDistributionManager);
        when(mockGatewayHook.getScriptRepository()).thenReturn(mockScriptRepository);

        scriptModule = new Python3ScriptModule(mockGatewayHook);
    }

    @AfterEach
    void tearDown() {
        Python3ScriptModule.setRoleResolverForTesting(null);
    }

    // ========== exec() Tests ==========

    @Test
    void testExecSimpleCode() throws Exception {
        // Given
        String code = "result = 2 + 2";
        Python3Result successResult = new Python3Result(true, 4.0, null, null);

        when(mockPool.execute(eq(code), anyMap(), eq(DEFAULT_MODE)))
            .thenReturn(successResult);

        // When
        Object result = scriptModule.exec(code);

        // Then
        assertThat(result).isEqualTo(4.0);
        verify(mockPool).execute(eq(code), anyMap(), eq(DEFAULT_MODE));
    }

    @Test
    void testExecWithVariables() throws Exception {
        // Given
        String code = "result = x + y";
        Map<String, Object> variables = new HashMap<>();
        variables.put("x", 10);
        variables.put("y", 20);

        Python3Result successResult = new Python3Result(true, 30.0, null, null);

        when(mockPool.execute(eq(code), eq(variables), eq(DEFAULT_MODE)))
            .thenReturn(successResult);

        // When
        Object result = scriptModule.exec(code, variables);

        // Then
        assertThat(result).isEqualTo(30.0);
        verify(mockPool).execute(eq(code), eq(variables), eq(DEFAULT_MODE));
    }

    @Test
    void testExecWithSecurityMode() throws Exception {
        // Given
        String code = "import sys; result = sys.version";
        Map<String, Object> variables = Collections.emptyMap();
        Python3Result successResult = new Python3Result(true, "3.11.0", null, null);

        when(mockPool.execute(eq(code), eq(variables), eq("ADMIN")))
            .thenReturn(successResult);

        // When
        Object result = scriptModule.exec(code, variables, "ADMIN");

        // Then
        assertThat(result).isEqualTo("3.11.0");
        verify(mockPool).execute(eq(code), eq(variables), eq("ADMIN"));
    }

    @Test
    void testExecWithNullCode() {
        // When/Then
        assertThatThrownBy(() -> scriptModule.exec(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("code parameter cannot be null");

        verifyNoInteractions(mockPool);
    }

    @Test
    void testExecWithNullVariables() throws Exception {
        // Given - pass DEFAULT_MODE explicitly; the legacy "RESTRICTED" wire-value
        // also passes through verbatim (the bridge ignores it post-C13) but
        // exercising the canonical default keeps the mock expectation simple.
        String code = "result = 42";
        Python3Result successResult = new Python3Result(true, 42.0, null, null);

        when(mockPool.execute(eq(code), anyMap(), eq(DEFAULT_MODE)))
            .thenReturn(successResult);

        // When
        Object result = scriptModule.exec(code, null, DEFAULT_MODE);

        // Then
        assertThat(result).isEqualTo(42.0);
        verify(mockPool).execute(eq(code), eq(Collections.emptyMap()), eq(DEFAULT_MODE));
    }

    @Test
    void testExecWithNullSecurityMode() throws Exception {
        // Given
        String code = "result = 100";
        Python3Result successResult = new Python3Result(true, 100.0, null, null);

        when(mockPool.execute(eq(code), anyMap(), eq(DEFAULT_MODE)))
            .thenReturn(successResult);

        // When - null security mode should default to DEFAULT_MODE (post-C13).
        Object result = scriptModule.exec(code, Collections.emptyMap(), null);

        // Then
        assertThat(result).isEqualTo(100.0);
        verify(mockPool).execute(eq(code), anyMap(), eq(DEFAULT_MODE));
    }

    @Test
    void testExecWhenPoolNotInitialized() {
        // Given
        when(mockGatewayHook.getProcessPool()).thenReturn(null);

        // When/Then
        assertThatThrownBy(() -> scriptModule.exec("result = 42"))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Python 3 process pool is not initialized");
    }

    @Test
    void testExecWithPythonError() throws Exception {
        // Given
        String code = "result = undefined_var";
        Python3Result errorResult = new Python3Result(
            false,
            null,
            "NameError: name 'undefined_var' is not defined",
            "Traceback (most recent call last)..."
        );

        when(mockPool.execute(eq(code), anyMap(), eq(DEFAULT_MODE)))
            .thenReturn(errorResult);

        // When/Then
        assertThatThrownBy(() -> scriptModule.exec(code))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Python error: NameError")
            .hasMessageContaining("Traceback");
    }

    @Test
    void testExecWithPython3Exception() throws Exception {
        // Given
        String code = "result = 42";
        Python3Exception exception = new Python3Exception("Pool exhausted");

        when(mockPool.execute(eq(code), anyMap(), eq(DEFAULT_MODE)))
            .thenThrow(exception);

        // When/Then
        assertThatThrownBy(() -> scriptModule.exec(code))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Failed to execute Python code")
            .hasMessageContaining("Pool exhausted");
    }

    // ========== eval() Tests ==========

    @Test
    void testEvalSimpleExpression() throws Exception {
        // Given
        String expression = "2 ** 10";
        Python3Result successResult = new Python3Result(true, 1024.0, null, null);

        when(mockPool.evaluate(eq(expression), anyMap(), eq(DEFAULT_MODE)))
            .thenReturn(successResult);

        // When
        Object result = scriptModule.eval(expression);

        // Then
        assertThat(result).isEqualTo(1024.0);
        verify(mockPool).evaluate(eq(expression), anyMap(), eq(DEFAULT_MODE));
    }

    @Test
    void testEvalWithVariables() throws Exception {
        // Given
        String expression = "x * y";
        Map<String, Object> variables = new HashMap<>();
        variables.put("x", 5);
        variables.put("y", 6);

        Python3Result successResult = new Python3Result(true, 30.0, null, null);

        when(mockPool.evaluate(eq(expression), eq(variables), eq(DEFAULT_MODE)))
            .thenReturn(successResult);

        // When
        Object result = scriptModule.eval(expression, variables);

        // Then
        assertThat(result).isEqualTo(30.0);
        verify(mockPool).evaluate(eq(expression), eq(variables), eq(DEFAULT_MODE));
    }

    @Test
    void testEvalWithNullExpression() {
        // When/Then
        assertThatThrownBy(() -> scriptModule.eval(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("expression parameter cannot be null");

        verifyNoInteractions(mockPool);
    }

    @Test
    void testEvalWhenPoolNotInitialized() {
        // Given
        when(mockGatewayHook.getProcessPool()).thenReturn(null);

        // When/Then
        assertThatThrownBy(() -> scriptModule.eval("2 + 2"))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Python 3 process pool is not initialized");
    }

    // ========== callModule() Tests ==========

    @Test
    void testCallModuleSimple() throws Exception {
        // Given
        String moduleName = "math";
        String functionName = "sqrt";
        List<Object> args = Arrays.asList(16);

        Python3Result successResult = new Python3Result(true, 4.0, null, null);

        when(mockPool.callModule(eq(moduleName), eq(functionName), eq(args), anyMap(), eq(DEFAULT_MODE)))
            .thenReturn(successResult);

        // When
        Object result = scriptModule.callModule(moduleName, functionName, args);

        // Then
        assertThat(result).isEqualTo(4.0);
        verify(mockPool).callModule(eq(moduleName), eq(functionName), eq(args), anyMap(), eq(DEFAULT_MODE));
    }

    @Test
    void testCallModuleWithKwargs() throws Exception {
        // Given
        String moduleName = "json";
        String functionName = "dumps";
        List<Object> args = Arrays.asList(Collections.singletonMap("key", "value"));
        Map<String, Object> kwargs = new HashMap<>();
        kwargs.put("indent", 2);

        Python3Result successResult = new Python3Result(true, "{\"key\": \"value\"}", null, null);

        when(mockPool.callModule(eq(moduleName), eq(functionName), eq(args), eq(kwargs), eq(DEFAULT_MODE)))
            .thenReturn(successResult);

        // When
        Object result = scriptModule.callModule(moduleName, functionName, args, kwargs);

        // Then
        assertThat(result).isEqualTo("{\"key\": \"value\"}");
        verify(mockPool).callModule(eq(moduleName), eq(functionName), eq(args), eq(kwargs), eq(DEFAULT_MODE));
    }

    @Test
    void testCallModuleWhenPoolNotInitialized() {
        // Given
        when(mockGatewayHook.getProcessPool()).thenReturn(null);

        // When/Then
        assertThatThrownBy(() -> scriptModule.callModule("math", "sqrt", Arrays.asList(16)))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Python 3 process pool is not initialized");
    }

    // ========== isAvailable() Tests ==========

    @Test
    void testIsAvailableWhenPoolHealthy() {
        // Given
        when(mockPool.isShutdown()).thenReturn(false);

        // When
        boolean result = scriptModule.isAvailable();

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void testIsAvailableWhenPoolShutdown() {
        // Given
        when(mockPool.isShutdown()).thenReturn(true);

        // When
        boolean result = scriptModule.isAvailable();

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void testIsAvailableWhenPoolNotInitialized() {
        // Given
        when(mockGatewayHook.getProcessPool()).thenReturn(null);

        // When
        boolean result = scriptModule.isAvailable();

        // Then
        assertThat(result).isFalse();
    }

    // ========== getVersion() Tests ==========

    @Test
    void testGetVersionSuccess() throws Exception {
        // Given
        Python3Result successResult = new Python3Result(true, "3.11.0", null, null);

        when(mockPool.execute(anyString(), anyMap(), eq("ADMIN")))
            .thenReturn(successResult);

        // When
        Map<String, Object> result = scriptModule.getVersion();

        // Then
        assertThat(result)
            .containsEntry("available", true)
            .containsEntry("version", "3.11.0");
    }

    @Test
    void testGetVersionWhenPoolNotInitialized() {
        // Given
        when(mockGatewayHook.getProcessPool()).thenReturn(null);

        // When
        Map<String, Object> result = scriptModule.getVersion();

        // Then
        assertThat(result)
            .containsEntry("available", false)
            .containsKey("error");
    }

    @Test
    void testGetVersionWithPythonError() throws Exception {
        // Given
        Python3Result errorResult = new Python3Result(false, null, "Import failed", null);

        when(mockPool.execute(anyString(), anyMap(), eq("ADMIN")))
            .thenReturn(errorResult);

        // When
        Map<String, Object> result = scriptModule.getVersion();

        // Then
        assertThat(result)
            .containsEntry("available", false)
            .containsEntry("error", "Import failed");
    }

    @Test
    void testGetVersionWithException() throws Exception {
        // Given
        when(mockPool.execute(anyString(), anyMap(), eq("ADMIN")))
            .thenThrow(new Python3Exception("Pool error"));

        // When
        Map<String, Object> result = scriptModule.getVersion();

        // Then
        assertThat(result)
            .containsEntry("available", false)
            .containsKey("error");
    }

    // ========== getPoolStats() Tests ==========

    @Test
    void testGetPoolStats() {
        // Given
        Python3ProcessPool.PoolStats stats = new Python3ProcessPool.PoolStats(5, 3, 2, 5);
        when(mockPool.getStats()).thenReturn(stats);

        // When
        Map<String, Object> result = scriptModule.getPoolStats();

        // Then
        assertThat(result)
            .containsEntry("poolSize", 5)
            .containsEntry("available", 3)
            .containsEntry("inUse", 2)
            .containsEntry("healthy", 5);
    }

    @Test
    void testGetPoolStatsWhenPoolNotInitialized() {
        // Given
        when(mockGatewayHook.getProcessPool()).thenReturn(null);

        // When
        Map<String, Object> result = scriptModule.getPoolStats();

        // Then
        assertThat(result).containsKey("error");
    }

    // ========== resizePool() Tests ==========

    @Test
    void testResizePool() {
        // Given
        int newSize = 10;

        // When
        scriptModule.resizePool(newSize);

        // Then
        verify(mockPool).resizePool(newSize);
    }

    @Test
    void testResizePoolWhenPoolNotInitialized() {
        // Given
        when(mockGatewayHook.getProcessPool()).thenReturn(null);

        // When/Then
        assertThatThrownBy(() -> scriptModule.resizePool(10))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Python 3 process pool is not initialized");
    }

    // ========== example() Tests ==========

    @Test
    void testExampleSuccess() throws Exception {
        // Given
        Python3Result successResult = new Python3Result(true, 1.2676506002282294E30, null, null);

        when(mockPool.evaluate(eq("2 ** 100"), anyMap(), eq(DEFAULT_MODE)))
            .thenReturn(successResult);

        // When
        String result = scriptModule.example();

        // Then
        assertThat(result).contains("Python 3 is working!")
                         .contains("2^100");
    }

    @Test
    void testExampleFailure() throws Exception {
        // Given
        Python3Result errorResult = new Python3Result(false, null, "Syntax error", null);

        when(mockPool.evaluate(anyString(), anyMap(), eq(DEFAULT_MODE)))
            .thenReturn(errorResult);

        // When
        String result = scriptModule.example();

        // Then
        assertThat(result).contains("Python 3 error:");
    }

    // ========== getDistributionInfo() Tests ==========

    @Test
    void testGetDistributionInfo() {
        // Given
        Map<String, Object> expectedInfo = new HashMap<>();
        expectedInfo.put("available", true);
        expectedInfo.put("pythonPath", "/usr/bin/python3");

        when(mockDistributionManager.getStatus()).thenReturn(expectedInfo);

        // When
        Map<String, Object> result = scriptModule.getDistributionInfo();

        // Then
        assertThat(result).isEqualTo(expectedInfo);
    }

    @Test
    void testGetDistributionInfoWhenManagerNotInitialized() {
        // Given
        when(mockGatewayHook.getDistributionManager()).thenReturn(null);

        // When
        Map<String, Object> result = scriptModule.getDistributionInfo();

        // Then
        assertThat(result)
            .containsEntry("available", false)
            .containsKey("error");
    }

    // ========== getAvailableScripts() Tests ==========

    @Test
    void testGetAvailableScripts() {
        // Given
        List<Python3ScriptRepository.ScriptMetadata> scripts = Arrays.asList(
            new Python3ScriptRepository.ScriptMetadata(
                "id1",
                "Script1",
                "Description 1",
                "author1",
                "2025-01-01",
                "2025-01-01",
                null,
                "1.0"
            ),
            new Python3ScriptRepository.ScriptMetadata(
                "id2",
                "Script2",
                "Description 2",
                "author2",
                "2025-01-02",
                "2025-01-02",
                "Folder1",
                "2.0"
            )
        );

        when(mockScriptRepository.listScripts()).thenReturn(scripts);

        // When
        List<Map<String, Object>> result = scriptModule.getAvailableScripts();

        // Then
        assertThat(result).hasSize(2);
        assertThat(result.get(0))
            .containsEntry("name", "Script1")
            .containsEntry("path", "Script1")
            .containsEntry("author", "author1");
        assertThat(result.get(1))
            .containsEntry("name", "Script2")
            .containsEntry("path", "Folder1/Script2")
            .containsEntry("author", "author2");
    }

    @Test
    void testGetAvailableScriptsWhenRepositoryNotInitialized() {
        // Given
        when(mockGatewayHook.getScriptRepository()).thenReturn(null);

        // When
        List<Map<String, Object>> result = scriptModule.getAvailableScripts();

        // Then
        assertThat(result).isEmpty();
    }

    // ========== checkSyntax() Tests ==========

    @Test
    void testCheckSyntaxValid() throws Exception {
        // Given
        String code = "print('Hello, World!')";
        Map<String, Object> syntaxResult = new HashMap<>();
        syntaxResult.put("errors", Collections.emptyList());

        Python3Result successResult = new Python3Result(true, syntaxResult, null, null);

        when(mockPool.checkSyntax(eq(code))).thenReturn(successResult);

        // When
        Map<String, Object> result = scriptModule.checkSyntax(code);

        // Then
        assertThat(result).containsEntry("errors", Collections.emptyList());
    }

    @Test
    void testCheckSyntaxWithErrors() throws Exception {
        // Given
        String code = "print('unclosed string";
        List<Map<String, Object>> errors = Arrays.asList(
            Collections.singletonMap("message", "SyntaxError: unterminated string literal")
        );
        Map<String, Object> syntaxResult = new HashMap<>();
        syntaxResult.put("errors", errors);

        Python3Result successResult = new Python3Result(true, syntaxResult, null, null);

        when(mockPool.checkSyntax(eq(code))).thenReturn(successResult);

        // When
        Map<String, Object> result = scriptModule.checkSyntax(code);

        // Then
        assertThat(result).containsKey("errors");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> resultErrors = (List<Map<String, Object>>) result.get("errors");
        assertThat(resultErrors).hasSize(1);
    }

    @Test
    void testCheckSyntaxWhenPoolNotInitialized() {
        // Given
        when(mockGatewayHook.getProcessPool()).thenReturn(null);

        // When
        Map<String, Object> result = scriptModule.checkSyntax("print('test')");

        // Then
        assertThat(result)
            .containsEntry("errors", Collections.emptyList())
            .containsKey("error");
    }
}
