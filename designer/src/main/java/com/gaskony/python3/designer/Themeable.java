package com.gaskony.python3.designer;

/**
 * Contract for IDE components that can switch between dark and light themes.
 *
 * <p>Implementing classes should update all child component colours when
 * {@link #applyTheme(boolean)} is called, without modifying global
 * {@code UIManager} defaults (which would affect the entire Ignition
 * Designer).</p>
 *
 * <p>Callers — typically {@link managers.ThemeManager} or the Script Console — invoke
 * this method on the Event Dispatch Thread after loading and applying the
 * RSyntaxTextArea syntax theme.</p>
 */
public interface Themeable {

    /**
     * Updates all visual components owned by this object to match the
     * requested theme.
     *
     * @param isDark {@code true} to apply dark theme colours,
     *               {@code false} for light theme
     */
    void applyTheme(boolean isDark);
}
