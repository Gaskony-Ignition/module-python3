package com.gaskony.python3.designer;

/**
 * Centralised user-preference key strings for the Python 3 Designer components.
 *
 * <p>Single source of truth for all {@link java.util.prefs.Preferences} key strings
 * used by the Script Console ({@link Python3ScriptConsole}) and
 * {@link managers.ThemeManager}.</p>
 *
 * <p>Console preferences node: {@code Preferences.userNodeForPackage(Python3ScriptConsole.class)}</p>
 */
public final class PreferenceKeys {

    private PreferenceKeys() {
        // Utility class - no instances
    }

    /**
     * Key for the saved colour theme name ("dark", "light", "vscode").
     * The key string predates v4.3.3's removal of the legacy standalone IDE —
     * it is kept as-is so existing users' saved theme choice survives.
     */
    public static final String IDE_THEME = "python3ide.theme";

    /** Key for the Script Console colour theme name. */
    public static final String CONSOLE_THEME = "python3console.theme";

    /** Key for the Script Console split-pane orientation (JSplitPane constant). */
    public static final String CONSOLE_SPLIT_ORIENTATION = "python3console.splitOrientation";
}
