package com.gaskony.python3.gateway;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link RoleResolver#isScriptingAllowed()} and
 * {@link RoleResolver#requireScriptingAllowed()} — the runtime
 * {@code system.python3.*} scripting gate.
 *
 * <p>Charter &sect;2 (2026-07-02): "authoring is the boundary." Anyone with
 * Designer access can already run arbitrary Jython, so runtime scripting is
 * <b>allowed by default</b> and a Gateway administrator opts OUT with
 * {@code ignition.python3.scriptingFunctions.allowed=false} (or the
 * equivalent env var). This test class covers {@link RoleResolver} directly,
 * independent of {@link Python3ScriptModule}'s wrapping into
 * {@link RuntimeException} (covered by {@code Python3ScriptModuleRoleGateTest}).
 *
 * @since v4.3.0
 */
class RoleResolverTest {

    private static final String PROP = "ignition.python3.scriptingFunctions.allowed";

    private final RoleResolver resolver = RoleResolver.getDefault();

    private String previousProp;

    @AfterEach
    void restoreProperty() {
        if (previousProp == null) {
            System.clearProperty(PROP);
        } else {
            System.setProperty(PROP, previousProp);
        }
    }

    @Test
    void isScriptingAllowed_noPropertyNoEnv_defaultsToAllowed() {
        previousProp = System.getProperty(PROP);
        System.clearProperty(PROP);

        // Env-var pollution guard: skip this assertion if the environment
        // this test happens to run in has explicitly set the opt-out env var,
        // since that would legitimately change the outcome.
        Assumptions.assumeTrue(
            System.getenv("IGNITION_PYTHON3_SCRIPTING_ALLOWED") == null,
            "IGNITION_PYTHON3_SCRIPTING_ALLOWED is set in this environment; "
                + "skipping the allow-by-default assertion");

        assertThat(resolver.isScriptingAllowed()).isTrue();
    }

    @Test
    void requireScriptingAllowed_propertyFalse_throwsSecurityException() {
        previousProp = System.getProperty(PROP);
        System.setProperty(PROP, "false");

        assertThat(resolver.isScriptingAllowed()).isFalse();
        assertThatThrownBy(resolver::requireScriptingAllowed)
            .isInstanceOf(SecurityException.class)
            .hasMessageContaining("disabled by the gateway administrator");
    }

    @Test
    void isScriptingAllowed_propertyTrue_allowed() {
        previousProp = System.getProperty(PROP);
        System.setProperty(PROP, "true");

        assertThat(resolver.isScriptingAllowed()).isTrue();
    }

    @Test
    void requireScriptingAllowed_propertyTrue_doesNotThrow() {
        previousProp = System.getProperty(PROP);
        System.setProperty(PROP, "true");

        assertThatCode(resolver::requireScriptingAllowed).doesNotThrowAnyException();
    }
}
