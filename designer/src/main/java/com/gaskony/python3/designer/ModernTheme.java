package com.gaskony.python3.designer;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.io.InputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Modern UI theme with color palette aligned to the Gateway Web UI.
 * Provides consistent colors and styles across the Designer IDE.
 *
 * v3.6.0: Colors aligned to web CSS variables (--bg-primary, --text-primary, etc.)
 * v3.6.0: Font resolution with JetBrains Mono bundled, Inter for UI
 */
public class ModernTheme {

    private static final Logger logger = LoggerFactory.getLogger(ModernTheme.class);

    // === Color Palette (v3.6.6 - VS Code Dark+ inspired) ===

    // Primary Backgrounds - VS Code Dark+ warm grays (shifted from cold navy-blacks)
    public static final Color BACKGROUND_DARKER = new Color(0x1e, 0x1e, 0x1e);     // #1e1e1e - VS Code editor bg
    public static final Color EDITOR_BACKGROUND = new Color(0x1e, 0x1e, 0x1e);     // #1e1e1e - code editor
    public static final Color PANEL_BACKGROUND = new Color(0x25, 0x25, 0x26);      // #252526 - VS Code sidebar/panels
    public static final Color BACKGROUND_LIGHT = new Color(0x2d, 0x2d, 0x30);      // #2d2d30 - lighter panels / hover
    public static final Color SIDEBAR_BACKGROUND = new Color(0x25, 0x25, 0x26);    // #252526 - sidebar
    public static final Color BACKGROUND_DARK = new Color(0x1e, 0x1e, 0x1e);       // #1e1e1e - general dark bg

    // Foreground Colors - VS Code text colors
    public static final Color FOREGROUND_PRIMARY = new Color(0xd4, 0xd4, 0xd4);    // #d4d4d4 - VS Code text
    public static final Color FOREGROUND_SECONDARY = new Color(0x96, 0x96, 0x96);  // #969696 - secondary text
    public static final Color FOREGROUND_MUTED = new Color(0x6a, 0x6a, 0x6a);      // #6a6a6a - muted text

    // Accent Colors - VS Code blue accent
    public static final Color ACCENT_PRIMARY = new Color(0x56, 0x9c, 0xd6);         // #569cd6 - VS Code blue
    public static final Color ACCENT_HOVER = new Color(0x6e, 0xb0, 0xe0);           // #6eb0e0 - lighter on hover
    public static final Color ACCENT_ACTIVE = new Color(0x45, 0x89, 0xc3);          // #4589c3 - darker when active

    // Semantic Colors
    public static final Color SUCCESS = new Color(0x6a, 0x99, 0x55);                // #6a9955 - VS Code green
    public static final Color WARNING = new Color(0xd7, 0xba, 0x7d);                // #d7ba7d - VS Code warning
    public static final Color ERROR = new Color(0xf4, 0x4c, 0x4c);                  // #f44c4c - VS Code error red
    public static final Color ERROR_BRIGHT = new Color(0xf4, 0x74, 0x74);           // #f47474 - brighter error
    public static final Color INFO = new Color(0x56, 0x9c, 0xd6);                   // #569cd6 - matches accent

    // Border Colors - VS Code border colors
    public static final Color BORDER_SUBTLE = new Color(0x2d, 0x2d, 0x30);          // #2d2d30 - subtle border
    public static final Color BORDER_DEFAULT = new Color(0x3c, 0x3c, 0x3c);         // #3c3c3c - default border
    public static final Color BORDER_FOCUSED = new Color(0x00, 0x7a, 0xcc);         // #007acc - VS Code focus blue
    public static final Color BORDER_HOVER = new Color(0x45, 0x45, 0x45);           // #454545 - hover border

    // Header accent border - accent-tinted translucent border for page/section headers
    public static final Color HEADER_BORDER_ACCENT = new Color(
            ACCENT_PRIMARY.getRed(), ACCENT_PRIMARY.getGreen(), ACCENT_PRIMARY.getBlue(), 30);
    public static final Color LIGHT_HEADER_BORDER_ACCENT = new Color(
            0x21, 0x76, 0xff, 30);  // Light mode accent border

    // UI Element Colors
    public static final Color BUTTON_BACKGROUND = new Color(0x33, 0x33, 0x33);     // #333333 - button bg
    public static final Color BUTTON_HOVER = new Color(0x3c, 0x3c, 0x3c);           // #3c3c3c - button hover
    public static final Color BUTTON_ACTIVE = new Color(0x2d, 0x2d, 0x30);          // #2d2d30 - button active

    public static final Color INPUT_BACKGROUND = new Color(0x3c, 0x3c, 0x3c);      // #3c3c3c - input bg
    public static final Color INPUT_BORDER = new Color(0x3c, 0x3c, 0x3c);           // #3c3c3c - input border

    public static final Color PANEL_BORDER = new Color(0x2d, 0x2d, 0x30);           // #2d2d30 - panel border

    // Tree Colors
    public static final Color TREE_BACKGROUND = new Color(0x25, 0x25, 0x26);       // #252526 - sidebar bg
    public static final Color TREE_SELECTION = new Color(0x04, 0x39, 0x5e);         // #04395e - VS Code selection
    public static final Color TREE_HOVER = new Color(0x2a, 0x2d, 0x2e);             // #2a2d2e - subtle hover

    // Tab Colors
    public static final Color TAB_ACTIVE_BG = new Color(0x1e, 0x1e, 0x1e);          // #1e1e1e - matches editor
    public static final Color TAB_INACTIVE_BG = new Color(0x2d, 0x2d, 0x30);        // #2d2d30 - darker
    public static final Color TAB_TEXT_ACTIVE = FOREGROUND_PRIMARY;
    public static final Color TAB_TEXT_INACTIVE = FOREGROUND_MUTED;

    // Status Colors
    public static final Color STATUS_TEXT = FOREGROUND_MUTED;

    // Light Mode Palette - use these instead of inline new Color(...) in theme-aware components
    public static final Color LIGHT_BACKGROUND = java.awt.Color.WHITE;
    public static final Color LIGHT_BACKGROUND_PANEL = new Color(0xf8, 0xf8, 0xf8);
    public static final Color LIGHT_BACKGROUND_DARKER = new Color(0xf5, 0xf5, 0xf5);
    public static final Color LIGHT_BACKGROUND_LIGHT = new Color(0xee, 0xee, 0xee);
    public static final Color LIGHT_FOREGROUND = java.awt.Color.BLACK;
    public static final Color LIGHT_FOREGROUND_SECONDARY = new Color(0x3c, 0x3c, 0x3c);
    public static final Color LIGHT_FOREGROUND_MUTED = new Color(0x64, 0x64, 0x64);
    public static final Color LIGHT_BORDER = new Color(0xc8, 0xc8, 0xc8);
    public static final Color LIGHT_BORDER_SUBTLE = new Color(0xdc, 0xdc, 0xdc);
    public static final Color LIGHT_BUTTON_BG = new Color(0xe6, 0xe6, 0xe6);
    public static final Color LIGHT_BUTTON_HOVER = new Color(0xdc, 0xdc, 0xdc);
    public static final Color LIGHT_BUTTON_ACTIVE = new Color(0xd2, 0xd2, 0xd2);
    public static final Color LIGHT_TREE_BG = java.awt.Color.WHITE;
    public static final Color LIGHT_SELECTION = new Color(0xdc, 0xeb, 0xff);
    // Light mode primary/accent button states (used for Connect, Execute buttons in light theme)
    public static final Color LIGHT_PRIMARY = new Color(0x21, 0x76, 0xff);
    public static final Color LIGHT_PRIMARY_HOVER = new Color(0x17, 0x6c, 0xf5);
    public static final Color LIGHT_PRIMARY_ACTIVE = new Color(0x0d, 0x62, 0xeb);
    // Light mode success button states (used for Save button in light theme)
    public static final Color LIGHT_SUCCESS = new Color(0x28, 0xa7, 0x45);
    public static final Color LIGHT_SUCCESS_HOVER = new Color(0x1e, 0x9d, 0x3b);
    public static final Color LIGHT_SUCCESS_ACTIVE = new Color(0x14, 0x93, 0x31);

    // Semantic / Warning Colors - theme-independent
    public static final Color WARNING_BACKGROUND = new Color(0xff, 0xf4, 0xe5);
    public static final Color WARNING_BORDER_COLOR = new Color(0xff, 0xa0, 0x00);
    public static final Color WARNING_TEXT = new Color(0x66, 0x3c, 0x00);
    public static final Color ERROR_LIGHT = new Color(0xb4, 0x00, 0x00);
    public static final Color SUCCESS_LIGHT = new Color(0x00, 0x64, 0x00);

    // Editor Highlight Colors
    public static final Color EDITOR_LINE_HIGHLIGHT = new Color(0x28, 0x28, 0x28);
    public static final Color LIGHT_EDITOR_LINE_HIGHLIGHT = new Color(0xe8, 0xf2, 0xfe);
    public static final Color LIGHT_EDITOR_GUTTER_BG = LIGHT_BACKGROUND_DARKER;

    // Spacing and Sizing (v3.6.0 - Increased for modern spacing)
    public static final int CORNER_RADIUS = 6;
    public static final int CORNER_RADIUS_LARGE = 8;
    public static final int SPACING_SMALL = 4;
    public static final int SPACING_MEDIUM = 8;
    public static final int SPACING_LARGE = 16;
    public static final int SPACING_XL = 24;
    public static final int BUTTON_GAP = 6;
    public static final int TOOLBAR_VPADDING = 12;

    // Dialog and layout spacing - use these instead of inline EmptyBorder padding values
    public static final int DIALOG_PADDING = 16;
    public static final int SECTION_PADDING = 16;
    public static final int SECTION_GAP = 12;

    // Button Sizing (v3.6.0 - Increased primary height)
    public static final int BUTTON_HEIGHT_PRIMARY = 40;
    public static final int BUTTON_HEIGHT_SECONDARY = 34;
    public static final int BUTTON_HEIGHT_SMALL = 28;
    public static final int BUTTON_PADDING_H_PRIMARY = 16;
    public static final int BUTTON_PADDING_H_SECONDARY = 12;
    public static final int BUTTON_PADDING_V_PRIMARY = 10;
    public static final int BUTTON_PADDING_V_SECONDARY = 8;

    // === Font Resolution (v3.6.0) ===

    /** Resolved code font - JetBrains Mono preferred */
    public static final Font FONT_CODE;

    /** Resolved UI font - Inter preferred */
    public static final Font FONT_UI;

    // Standard font variants derived from resolved fonts
    public static final Font FONT_REGULAR;
    public static final Font FONT_BOLD;
    public static final Font FONT_BUTTON;
    public static final Font FONT_MONOSPACE;
    public static final Font FONT_TITLE;
    public static final Font FONT_STATUS;

    static {
        // Register bundled JetBrains Mono font
        Font registeredCodeFont = null;
        try {
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            InputStream regular = ModernTheme.class.getResourceAsStream("/fonts/JetBrainsMono-Regular.ttf");
            InputStream bold = ModernTheme.class.getResourceAsStream("/fonts/JetBrainsMono-Bold.ttf");

            if (regular != null) {
                Font regFont = Font.createFont(Font.TRUETYPE_FONT, regular);
                ge.registerFont(regFont);
                registeredCodeFont = regFont;
                regular.close();
            }
            if (bold != null) {
                Font boldFont = Font.createFont(Font.TRUETYPE_FONT, bold);
                ge.registerFont(boldFont);
                bold.close();
            }
        } catch (FontFormatException | IOException e) {
            logger.warn("Could not load bundled JetBrains Mono font", e);
        }

        // Resolve code font with fallback chain
        FONT_CODE = resolveCodeFont(registeredCodeFont);
        FONT_MONOSPACE = FONT_CODE.deriveFont(14f);

        // Resolve UI font with fallback chain
        FONT_UI = resolveUiFont();
        FONT_REGULAR = FONT_UI.deriveFont(Font.PLAIN, 12f);
        FONT_BOLD = FONT_UI.deriveFont(Font.BOLD, 12f);
        FONT_BUTTON = FONT_UI.deriveFont(Font.BOLD, 13f);
        FONT_TITLE = FONT_UI.deriveFont(Font.BOLD, 14f);
        FONT_STATUS = FONT_UI.deriveFont(Font.PLAIN, 10f);

        logger.info("ModernTheme initialized: code font='{}', ui font='{}'",
            FONT_CODE.getFontName(), FONT_UI.getFontName());
    }

    /**
     * Resolves the best available code font.
     * Fallback chain: JetBrains Mono -> Cascadia Code -> Fira Code -> Consolas -> Monospaced
     */
    private static Font resolveCodeFont(Font registeredFont) {
        if (registeredFont != null) {
            return registeredFont.deriveFont(14f);
        }
        String[] codeFonts = {"JetBrains Mono", "Cascadia Code", "Fira Code", "Consolas"};
        for (String name : codeFonts) {
            Font f = new Font(name, Font.PLAIN, 14);
            if (!f.getFontName().equals(Font.DIALOG)) {
                return f;
            }
        }
        return new Font(Font.MONOSPACED, Font.PLAIN, 14);
    }

    /**
     * Resolves the best available UI font.
     * Fallback chain: Inter -> Segoe UI -> SansSerif
     */
    private static Font resolveUiFont() {
        String[] uiFonts = {"Inter", "Segoe UI"};
        for (String name : uiFonts) {
            Font f = new Font(name, Font.PLAIN, 12);
            if (!f.getFontName().equals(Font.DIALOG)) {
                return f;
            }
        }
        return new Font(Font.SANS_SERIF, Font.PLAIN, 12);
    }

    // === Utility Methods ===

    /**
     * Creates a semi-transparent version of a color.
     */
    public static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    /**
     * Lightens a color by a percentage.
     */
    public static Color lighten(Color color, double percent) {
        int r = Math.min(255, (int) (color.getRed() + (255 - color.getRed()) * percent));
        int g = Math.min(255, (int) (color.getGreen() + (255 - color.getGreen()) * percent));
        int b = Math.min(255, (int) (color.getBlue() + (255 - color.getBlue()) * percent));
        return new Color(r, g, b);
    }

    /**
     * Darkens a color by a percentage.
     */
    public static Color darken(Color color, double percent) {
        int r = Math.max(0, (int) (color.getRed() * (1 - percent)));
        int g = Math.max(0, (int) (color.getGreen() * (1 - percent)));
        int b = Math.max(0, (int) (color.getBlue() * (1 - percent)));
        return new Color(r, g, b);
    }

    /**
     * Returns a font with the specified size.
     */
    public static Font withSize(Font baseFont, int size) {
        return baseFont.deriveFont((float) size);
    }

    /**
     * Returns a bold version of the font.
     */
    public static Font bold(Font baseFont) {
        return baseFont.deriveFont(Font.BOLD);
    }

    // === Floating Card Header Factory (v3.6.8) ===

    /**
     * Creates a floating card-style header panel matching the AI Terminal Dashboard style.
     * Features a subtle gradient background, rounded corners, and consistent spacing.
     *
     * @param title the header title text
     * @param subtitle optional subtitle text (null for none)
     * @return a styled JPanel to use as a page/section header
     * @since v3.6.8
     */
    public static javax.swing.JPanel createCardHeader(String title, String subtitle) {
        javax.swing.JPanel header = new javax.swing.JPanel(new java.awt.BorderLayout()) {
            @Override
            protected void paintComponent(java.awt.Graphics g) {
                java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
                g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                        java.awt.RenderingHints.VALUE_ANTIALIAS_ON);

                int w = getWidth();
                int h = getHeight();
                int r = CORNER_RADIUS_LARGE;

                // Subtle gradient background (matching AI Terminal Dashboard style)
                java.awt.GradientPaint gp = new java.awt.GradientPaint(
                        0, 0, new Color(ACCENT_PRIMARY.getRed(), ACCENT_PRIMARY.getGreen(),
                                ACCENT_PRIMARY.getBlue(), 15),
                        w * 0.7f, h,
                        new Color(PANEL_BACKGROUND.getRed(), PANEL_BACKGROUND.getGreen(),
                                PANEL_BACKGROUND.getBlue(), 255));
                g2.setPaint(gp);
                g2.fillRoundRect(0, 0, w, h, r, r);

                // Accent-tinted border stroke
                g2.setColor(HEADER_BORDER_ACCENT);
                g2.setStroke(new java.awt.BasicStroke(1f));
                g2.drawRoundRect(0, 0, w - 1, h - 1, r, r);

                g2.dispose();
            }
        };
        header.setOpaque(false);
        header.setBorder(javax.swing.BorderFactory.createEmptyBorder(12, 16, 12, 16));

        javax.swing.JPanel textPanel = new javax.swing.JPanel();
        textPanel.setLayout(new javax.swing.BoxLayout(textPanel, javax.swing.BoxLayout.Y_AXIS));
        textPanel.setOpaque(false);

        javax.swing.JLabel titleLabel = new javax.swing.JLabel(title);
        titleLabel.setFont(FONT_TITLE.deriveFont(Font.BOLD, 20f));
        titleLabel.setForeground(FOREGROUND_PRIMARY);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        textPanel.add(titleLabel);

        if (subtitle != null && !subtitle.isEmpty()) {
            javax.swing.JLabel subtitleLabel = new javax.swing.JLabel(subtitle);
            subtitleLabel.setFont(FONT_REGULAR.deriveFont(13f));
            subtitleLabel.setForeground(FOREGROUND_MUTED);
            subtitleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            subtitleLabel.setBorder(javax.swing.BorderFactory.createEmptyBorder(4, 0, 0, 0));
            textPanel.add(subtitleLabel);
        }

        header.add(textPanel, java.awt.BorderLayout.WEST);

        return header;
    }

    /**
     * Creates a card-style container panel with rounded border and proper background.
     * Use this to wrap content sections for a consistent card appearance.
     *
     * @return a styled JPanel with card appearance
     * @since v3.6.8
     */
    public static javax.swing.JPanel createCardPanel() {
        javax.swing.JPanel card = new javax.swing.JPanel(new java.awt.BorderLayout());
        card.setBackground(PANEL_BACKGROUND);
        card.setBorder(javax.swing.BorderFactory.createCompoundBorder(
                javax.swing.BorderFactory.createLineBorder(BORDER_SUBTLE, 1),
                javax.swing.BorderFactory.createEmptyBorder(12, 14, 12, 14)
        ));
        return card;
    }

    // === Component Styling Helpers ===

    /**
     * Applies modern theme to a component.
     */
    public static void applyToComponent(Component component) {
        component.setBackground(PANEL_BACKGROUND);
        component.setForeground(FOREGROUND_PRIMARY);
        component.setFont(FONT_REGULAR);
    }

    /**
     * Applies modern theme to a button.
     */
    public static void applyToButton(Component button) {
        button.setBackground(BUTTON_BACKGROUND);
        button.setForeground(FOREGROUND_PRIMARY);
        button.setFont(FONT_BOLD);
    }

    /**
     * Gets status color for a given message type.
     */
    public static Color getStatusColor(String type) {
        switch (type.toLowerCase()) {
            case "success":
                return SUCCESS;
            case "error":
                return ERROR;
            case "warning":
                return WARNING;
            case "info":
                return INFO;
            default:
                return FOREGROUND_PRIMARY;
        }
    }
}
