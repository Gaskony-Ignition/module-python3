package com.gaskony.python3.designer.managers;

import com.gaskony.python3.designer.ComponentThemeHelper;
import com.gaskony.python3.designer.ModernTheme;
import com.gaskony.python3.designer.PreferenceKeys;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxScheme;
import org.fife.ui.rsyntaxtextarea.Style;
import org.fife.ui.rsyntaxtextarea.TokenTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.JTextArea;
import javax.swing.JTree;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.io.IOException;
import java.util.prefs.Preferences;

/**
 * Manages IDE theme application.
 * Self-contained theme management extracted from Python3IDE.
 *
 * v2.0.0: Extracted from Python3IDE.java monolith
 */
public class ThemeManager {
    private static final Logger logger = LoggerFactory.getLogger(ThemeManager.class);
    private static final String PREF_THEME = PreferenceKeys.IDE_THEME;

    private String currentTheme;
    private final Preferences prefs;

    public ThemeManager(Class<?> prefsClass) {
        this.prefs = Preferences.userNodeForPackage(prefsClass);
        this.currentTheme = prefs.get(PREF_THEME, "dark");
    }

    /**
     * Applies theme to IDE components.
     * Always updates currentTheme and preferences, even if RSTA theme loading fails.
     */
    public void applyTheme(String themeName, Component rootComponent, RSyntaxTextArea codeEditor,
                           JTextArea outputArea, JTextArea errorArea, JTree scriptTree) throws IOException {
        // Always update the tracked theme state first so toggle works correctly
        currentTheme = themeName;
        prefs.put(PREF_THEME, themeName);

        boolean isDarkTheme = false;

        switch (themeName.toLowerCase()) {
            case "dark":
            case "monokai":
            case "vs":
                isDarkTheme = true;
                break;
            default:
                isDarkTheme = false;
                break;
        }

        // Apply the editor syntax scheme programmatically. Historically this loaded an
        // RSTA XML theme via getResourceAsStream + Theme.load inside a swallow-all catch;
        // under the Designer's module classloader that load failed silently, leaving the
        // editor on RSTA's DEFAULT light palette (navy keywords / maroon strings) over the
        // dark background — unreadable (v4.5.3 fix). Building the SyntaxScheme in Java has no
        // resource/classloader dependency, so it cannot silently fail.
        applySyntaxScheme(codeEditor, isDarkTheme);

        if (isDarkTheme) {
            applyDarkTheme(outputArea, errorArea, scriptTree, rootComponent);
        } else {
            applyLightTheme(outputArea, errorArea, scriptTree, rootComponent);
        }

        ComponentThemeHelper.updateScrollPaneTheme(rootComponent, isDarkTheme);
        ComponentThemeHelper.updateSplitPaneDividers(rootComponent, isDarkTheme);

        logger.info("Applied theme: {}", themeName);
    }

    private void applyDarkTheme(JTextArea outputArea, JTextArea errorArea, JTree scriptTree, Component root) {
        if (outputArea != null) {
            outputArea.setBackground(ModernTheme.BACKGROUND_DARKER);
            outputArea.setForeground(ModernTheme.FOREGROUND_PRIMARY);
            outputArea.setCaretColor(ModernTheme.FOREGROUND_PRIMARY);
        }

        if (errorArea != null) {
            errorArea.setBackground(ModernTheme.BACKGROUND_DARKER);
            errorArea.setForeground(ModernTheme.ERROR);
            errorArea.setCaretColor(ModernTheme.ERROR);
        }

        if (scriptTree != null) {
            scriptTree.setBackground(ModernTheme.TREE_BACKGROUND);
            scriptTree.setForeground(ModernTheme.FOREGROUND_PRIMARY);
        }

        ComponentThemeHelper.updatePanelBackgrounds(root, ModernTheme.BACKGROUND_DARK);
        // NOTE: Do NOT call UIManager.put() here - that sets GLOBAL Swing defaults which
        // affects the entire Ignition Designer, not just our Script Console window.
        // All theming is done via direct component setBackground()/setForeground() calls.
    }

    private void applyLightTheme(JTextArea outputArea, JTextArea errorArea, JTree scriptTree, Component root) {
        if (outputArea != null) {
            outputArea.setBackground(Color.WHITE);
            outputArea.setForeground(Color.BLACK);
            outputArea.setCaretColor(Color.BLACK);
        }

        if (errorArea != null) {
            errorArea.setBackground(Color.WHITE);
            errorArea.setForeground(ModernTheme.ERROR_LIGHT);
            errorArea.setCaretColor(ModernTheme.ERROR_LIGHT);
        }

        if (scriptTree != null) {
            scriptTree.setBackground(Color.WHITE);
            scriptTree.setForeground(Color.BLACK);
        }

        ComponentThemeHelper.updatePanelBackgrounds(root, Color.WHITE);
        // NOTE: Do NOT call UIManager.put() here - that sets GLOBAL Swing defaults which
        // affects the entire Ignition Designer, not just our Script Console window.
        // All theming is done via direct component setBackground()/setForeground() calls.
    }

    // =========================================================================
    // Editor syntax scheme (programmatic — no XML/classloader dependency)
    // =========================================================================
    //
    // VS Code Dark+ palette (was designer/src/main/resources/themes/python3-dark.xml,
    // retired in v4.5.3 so there is a single source of truth). Java Swing uses literal
    // Color hex throughout this module (see ModernTheme) — the "no hardcoded hex" rule
    // applies to the React/CSS front-end, not to Swing colour constants.
    static final Color SYN_IDENTIFIER   = new Color(0xd4d4d4);
    static final Color SYN_TYPE         = new Color(0x4ec9b0);
    static final Color SYN_FUNCTION     = new Color(0xdcdcaa);
    static final Color SYN_KEYWORD      = new Color(0x569cd6);
    static final Color SYN_KEYWORD2     = new Color(0xc586c0);
    static final Color SYN_COMMENT      = new Color(0x6a9955);
    static final Color SYN_COMMENT_DIM  = new Color(0x969696);
    static final Color SYN_STRING       = new Color(0xce9178);
    static final Color SYN_NUMBER       = new Color(0xb5cea8);
    static final Color SYN_VARIABLE     = new Color(0x9cdcfe);
    static final Color SYN_REGEX        = new Color(0xd16969);
    static final Color SYN_ERROR        = new Color(0xf44c4c);

    static final Color EDITOR_DARK_BG            = new Color(0x1e1e1e);
    static final Color EDITOR_DARK_CURRENT_LINE  = new Color(0x282828);
    static final Color EDITOR_DARK_SELECTION     = new Color(0x264f78);
    static final Color EDITOR_LIGHT_CURRENT_LINE = new Color(0xf5f5f5);

    /**
     * Applies editor colours + a syntax scheme for the given mode directly to the
     * RSyntaxTextArea. No resources are loaded, so this can never silently fall back
     * to unreadable defaults the way the old XML {@code Theme.load} path could.
     */
    private void applySyntaxScheme(RSyntaxTextArea editor, boolean dark) {
        if (editor == null) {
            return;
        }
        if (dark) {
            editor.setBackground(EDITOR_DARK_BG);
            editor.setForeground(SYN_IDENTIFIER);
            editor.setCaretColor(SYN_IDENTIFIER);
            editor.setCurrentLineHighlightColor(EDITOR_DARK_CURRENT_LINE);
            editor.setSelectionColor(EDITOR_DARK_SELECTION);
            editor.setSyntaxScheme(buildDarkSyntaxScheme(editor.getFont()));
        } else {
            editor.setBackground(Color.WHITE);
            editor.setForeground(Color.BLACK);
            editor.setCaretColor(Color.BLACK);
            editor.setCurrentLineHighlightColor(EDITOR_LIGHT_CURRENT_LINE);
            // RSTA's built-in defaults are a readable light palette on white.
            editor.restoreDefaultSyntaxScheme();
        }
        editor.revalidate();
        editor.repaint();
    }

    /**
     * Builds the VS Code Dark+ syntax scheme. Package-private + static so it can be
     * unit-tested without a live Designer (assert token foregrounds are the readable
     * dark palette, not RSTA's default light one).
     *
     * @param baseFont the editor's base font, used to derive bold/italic token fonts
     */
    static SyntaxScheme buildDarkSyntaxScheme(Font baseFont) {
        Font base = (baseFont != null) ? baseFont : new Font(Font.MONOSPACED, Font.PLAIN, 12);
        SyntaxScheme scheme = new SyntaxScheme(base);

        style(scheme, TokenTypes.IDENTIFIER, SYN_IDENTIFIER, base, Font.PLAIN);
        style(scheme, TokenTypes.DATA_TYPE, SYN_TYPE, base, Font.BOLD);
        style(scheme, TokenTypes.ANNOTATION, SYN_TYPE, base, Font.PLAIN);
        style(scheme, TokenTypes.FUNCTION, SYN_FUNCTION, base, Font.PLAIN);
        style(scheme, TokenTypes.RESERVED_WORD, SYN_KEYWORD, base, Font.BOLD);
        style(scheme, TokenTypes.RESERVED_WORD_2, SYN_KEYWORD2, base, Font.PLAIN);
        style(scheme, TokenTypes.COMMENT_EOL, SYN_COMMENT, base, Font.ITALIC);
        style(scheme, TokenTypes.COMMENT_MULTILINE, SYN_COMMENT, base, Font.ITALIC);
        style(scheme, TokenTypes.COMMENT_DOCUMENTATION, SYN_COMMENT, base, Font.ITALIC);
        style(scheme, TokenTypes.COMMENT_KEYWORD, SYN_COMMENT, base, Font.ITALIC);
        style(scheme, TokenTypes.COMMENT_MARKUP, SYN_COMMENT_DIM, base, Font.ITALIC);
        style(scheme, TokenTypes.LITERAL_STRING_DOUBLE_QUOTE, SYN_STRING, base, Font.PLAIN);
        style(scheme, TokenTypes.LITERAL_CHAR, SYN_STRING, base, Font.PLAIN);
        style(scheme, TokenTypes.LITERAL_BACKQUOTE, SYN_STRING, base, Font.PLAIN);
        style(scheme, TokenTypes.LITERAL_NUMBER_DECIMAL_INT, SYN_NUMBER, base, Font.PLAIN);
        style(scheme, TokenTypes.LITERAL_NUMBER_FLOAT, SYN_NUMBER, base, Font.PLAIN);
        style(scheme, TokenTypes.LITERAL_NUMBER_HEXADECIMAL, SYN_NUMBER, base, Font.PLAIN);
        style(scheme, TokenTypes.LITERAL_BOOLEAN, SYN_KEYWORD, base, Font.BOLD);
        style(scheme, TokenTypes.OPERATOR, SYN_IDENTIFIER, base, Font.PLAIN);
        style(scheme, TokenTypes.SEPARATOR, SYN_IDENTIFIER, base, Font.PLAIN);
        style(scheme, TokenTypes.PREPROCESSOR, SYN_KEYWORD2, base, Font.PLAIN);
        style(scheme, TokenTypes.REGEX, SYN_REGEX, base, Font.PLAIN);
        style(scheme, TokenTypes.VARIABLE, SYN_VARIABLE, base, Font.PLAIN);
        style(scheme, TokenTypes.ERROR_IDENTIFIER, SYN_ERROR, base, Font.PLAIN);
        style(scheme, TokenTypes.ERROR_NUMBER_FORMAT, SYN_ERROR, base, Font.PLAIN);
        style(scheme, TokenTypes.ERROR_STRING_DOUBLE, SYN_ERROR, base, Font.PLAIN);
        style(scheme, TokenTypes.ERROR_CHAR, SYN_ERROR, base, Font.PLAIN);
        return scheme;
    }

    private static void style(SyntaxScheme scheme, int token, Color fg, Font base, int fontStyle) {
        Style s = scheme.getStyle(token);
        if (s == null) {
            s = new Style();
        }
        s.foreground = fg;
        if (fontStyle != Font.PLAIN && base != null) {
            s.font = base.deriveFont(fontStyle);
        }
        scheme.setStyle(token, s);
    }

    public String mapThemeNameToKey(String displayName) {
        switch (displayName) {
            case "Dark":
                return "dark";
            case "VS Code Dark+":
                return "vs";
            case "Monokai":
                return "monokai";
            default:
                return "dark";
        }
    }

    public String getCurrentTheme() {
        return currentTheme;
    }

    public String getSavedThemePreference() {
        return prefs.get(PREF_THEME, "dark");
    }
}
