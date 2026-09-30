package com.gaskony.python3.designer.managers;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Smoke tests for the Manager classes used by the live Designer surfaces
 * (Script Console + Project Browser).
 *
 * <p>v4.3.3 removed the legacy standalone-IDE manager cluster (AutoSave,
 * Search, Execution, CommandPalette, ScriptOps, etc.) — it was only reachable
 * from the deleted {@code Python3IDE}. Only the managers below remain.</p>
 *
 * @since v2.11.0
 */
class ManagerSmokeTest {

    private static final String PACKAGE = "com.gaskony.python3.designer.managers";

    @Test
    void testProjectBrowserManagerClassExists() {
        // Resource probe, not Class.forName: ProjectBrowserManager extends Designer
        // SDK types that are compileOnly and absent from the test classpath, so
        // loading it would fail linkage even though the class is present and valid.
        assertNotNull(
            ManagerSmokeTest.class.getResource(
                "/" + PACKAGE.replace('.', '/') + "/ProjectBrowserManager.class"),
            "ProjectBrowserManager class file should be on the classpath");
    }

    @Test
    void testThemeManagerClassExists() {
        assertDoesNotThrow(() -> {
            Class<?> clazz = Class.forName(PACKAGE + ".ThemeManager");
            assertNotNull(clazz, "ThemeManager class should exist");
            assertEquals(PACKAGE, clazz.getPackage().getName());
            assertTrue(clazz.getConstructors().length > 0,
                "ThemeManager should have at least one public constructor");
        });
    }

    @Test
    void testLegacyIdeManagersAreGone() {
        // Guard against the dead cluster creeping back in (charter won't-do list)
        String[] removed = {
            "AutoSaveManager", "SearchManager", "ScriptImportExportManager",
            "ExecutionManager", "KeyboardShortcutsManager", "ScriptTransferManager",
            "CommandPaletteManager", "Python3IDETheme",
            "Python3IDEConnectionController", "Python3IDEScriptOps",
            "Python3IDELayout", "ScriptManager", "RecentScriptsManager"
        };
        for (String name : removed) {
            assertThrows(ClassNotFoundException.class,
                () -> Class.forName(PACKAGE + "." + name),
                name + " should have been deleted in v4.3.3");
        }
    }
}
