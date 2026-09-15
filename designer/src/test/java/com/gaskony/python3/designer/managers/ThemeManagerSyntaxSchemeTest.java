package com.gaskony.python3.designer.managers;

import org.fife.ui.rsyntaxtextarea.SyntaxScheme;
import org.fife.ui.rsyntaxtextarea.TokenTypes;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Font;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the v4.5.3 fix for unreadable dark-mode editor text.
 *
 * <p>Before v4.5.3 the Script Console loaded its dark syntax colours from an RSTA
 * XML theme via {@code Theme.load(getClass().getResourceAsStream("/themes/python3-dark.xml"))}
 * inside a swallow-all {@code catch}. Under the Designer's module classloader that load
 * failed silently, so the editor kept RSTA's DEFAULT light palette (navy keywords, maroon
 * strings) on the dark background — barely readable. v4.5.3 builds the scheme programmatically
 * ({@link ThemeManager#buildDarkSyntaxScheme(Font)}), which has no resource/classloader
 * dependency and therefore cannot silently fall back.</p>
 *
 * <p>These tests pin the readable VS Code Dark+ foregrounds and prove they differ from the
 * default (light) scheme that caused the bug.</p>
 */
class ThemeManagerSyntaxSchemeTest {

    private static final Font BASE = new Font(Font.MONOSPACED, Font.PLAIN, 12);

    @Test
    void darkSchemeUsesReadableVsCodeDarkForegrounds() {
        SyntaxScheme s = ThemeManager.buildDarkSyntaxScheme(BASE);

        assertThat(s.getStyle(TokenTypes.RESERVED_WORD).foreground).isEqualTo(new Color(0x569cd6));
        assertThat(s.getStyle(TokenTypes.LITERAL_STRING_DOUBLE_QUOTE).foreground).isEqualTo(new Color(0xce9178));
        assertThat(s.getStyle(TokenTypes.COMMENT_EOL).foreground).isEqualTo(new Color(0x6a9955));
        assertThat(s.getStyle(TokenTypes.IDENTIFIER).foreground).isEqualTo(new Color(0xd4d4d4));
        assertThat(s.getStyle(TokenTypes.LITERAL_NUMBER_DECIMAL_INT).foreground).isEqualTo(new Color(0xb5cea8));
        assertThat(s.getStyle(TokenTypes.FUNCTION).foreground).isEqualTo(new Color(0xdcdcaa));
    }

    @Test
    void darkSchemeDiffersFromDefaultLightScheme() {
        // The default scheme is exactly what was showing through when the XML load failed.
        SyntaxScheme dflt = new SyntaxScheme(BASE);
        SyntaxScheme dark = ThemeManager.buildDarkSyntaxScheme(BASE);

        assertThat(dark.getStyle(TokenTypes.RESERVED_WORD).foreground)
                .isNotEqualTo(dflt.getStyle(TokenTypes.RESERVED_WORD).foreground);
        assertThat(dark.getStyle(TokenTypes.LITERAL_STRING_DOUBLE_QUOTE).foreground)
                .isNotEqualTo(dflt.getStyle(TokenTypes.LITERAL_STRING_DOUBLE_QUOTE).foreground);
    }

    @Test
    void keywordsAreBoldAndCommentsItalicForVsCodeFidelity() {
        SyntaxScheme s = ThemeManager.buildDarkSyntaxScheme(BASE);

        assertThat(s.getStyle(TokenTypes.RESERVED_WORD).font).isNotNull();
        assertThat(s.getStyle(TokenTypes.RESERVED_WORD).font.isBold()).isTrue();
        assertThat(s.getStyle(TokenTypes.COMMENT_EOL).font).isNotNull();
        assertThat(s.getStyle(TokenTypes.COMMENT_EOL).font.isItalic()).isTrue();
    }

    @Test
    void nullBaseFontDoesNotThrow() {
        // Defensive: a null editor font must not break scheme construction.
        SyntaxScheme s = ThemeManager.buildDarkSyntaxScheme(null);
        assertThat(s.getStyle(TokenTypes.RESERVED_WORD).foreground).isEqualTo(new Color(0x569cd6));
    }
}
