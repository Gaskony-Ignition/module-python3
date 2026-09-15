package com.gaskony.python3.gateway;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for the {@code execShell} removal (C16 in
 * {@code /modules/.review/FINAL_REVIEW.md}).
 *
 * <p>Ignition's {@code ScriptManager.addScriptModule} reflectively exposes the
 * public methods of the registered object as {@code system.python3.*} functions.
 * The {@code execShell} method built a Python source string via interpolation
 * and ran it with {@code subprocess.run(..., shell=True, ...)}, a classic shell
 * injection sink, and was removed.</p>
 *
 * <p>This test fails if anyone re-adds an {@code execShell} method to
 * {@link Python3ScriptModule} (which would re-expose the binding to Jython
 * callers via {@code system.python3.execShell}).</p>
 */
class Python3ScriptModuleExecShellRemovedTest {

    @Test
    void execShellIsNotExposedAsAScriptingFunction() {
        Method[] methods = Python3ScriptModule.class.getMethods();

        boolean hasExecShell = Arrays.stream(methods)
                .anyMatch(m -> "execShell".equals(m.getName()));

        assertThat(hasExecShell)
                .as("Python3ScriptModule.execShell must remain removed (security finding C16). "
                        + "If you genuinely need a shell-exec scripting binding, design it without "
                        + "shell=True and without Python source-string interpolation.")
                .isFalse();
    }

    @Test
    void escapeForPythonHelperIsRemoved() {
        // The only caller of escapeForPython was execShell. If both vanish,
        // there is no path back to the shell-injection sink.
        Method[] methods = Python3ScriptModule.class.getDeclaredMethods();

        boolean hasEscape = Arrays.stream(methods)
                .anyMatch(m -> "escapeForPython".equals(m.getName()));

        assertThat(hasEscape)
                .as("escapeForPython() helper should be removed alongside execShell (C16).")
                .isFalse();
    }
}
