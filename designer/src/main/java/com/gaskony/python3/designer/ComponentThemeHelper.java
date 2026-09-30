package com.gaskony.python3.designer;

import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;

/**
 * Static helpers for applying IDE themes to Swing component trees.
 *
 * <p>Three recursive walkers consolidated here (originally private methods in
 * {@link managers.ThemeManager} and the since-removed legacy IDE) so any class
 * in the designer scope can use them without duplication.</p>
 *
 * <p>All methods walk the full component tree rooted at {@code comp} and apply
 * the relevant visual change; they are safe to call from the Event Dispatch
 * Thread only (as with all Swing mutations).</p>
 */
public final class ComponentThemeHelper {

    private ComponentThemeHelper() {
        // Utility class — no instances
    }

    // -------------------------------------------------------------------------
    // Panel background walker
    // -------------------------------------------------------------------------

    /**
     * Recursively sets the background of every {@link JPanel} in the component
     * tree rooted at {@code comp} to {@code background}.
     *
     * @param comp       root of the component tree to update
     * @param background target background colour
     */
    public static void updatePanelBackgrounds(Component comp, Color background) {
        if (comp instanceof JPanel) {
            comp.setBackground(background);
        }
        if (comp instanceof Container) {
            for (Component child : ((Container) comp).getComponents()) {
                updatePanelBackgrounds(child, background);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Scroll pane theme walker
    // -------------------------------------------------------------------------

    /**
     * Recursively updates every {@link JScrollPane} in the tree to match the
     * requested theme.  Scrollbar UI delegates are refreshed and background
     * colours are applied to the viewport and the scroll pane itself.
     *
     * @param comp        root of the component tree to update
     * @param isDarkTheme {@code true} for dark theme, {@code false} for light
     */
    public static void updateScrollPaneTheme(Component comp, boolean isDarkTheme) {
        if (comp instanceof JScrollPane) {
            JScrollPane scrollPane = (JScrollPane) comp;

            JScrollBar verticalBar = scrollPane.getVerticalScrollBar();
            JScrollBar horizontalBar = scrollPane.getHorizontalScrollBar();

            if (verticalBar != null) {
                verticalBar.updateUI();
                if (isDarkTheme) {
                    verticalBar.setBackground(ModernTheme.BACKGROUND_DARK);
                    verticalBar.setForeground(ModernTheme.FOREGROUND_PRIMARY);
                } else {
                    verticalBar.setBackground(ModernTheme.LIGHT_BACKGROUND);
                    verticalBar.setForeground(Color.BLACK);
                }
            }

            if (horizontalBar != null) {
                horizontalBar.updateUI();
                if (isDarkTheme) {
                    horizontalBar.setBackground(ModernTheme.BACKGROUND_DARK);
                    horizontalBar.setForeground(ModernTheme.FOREGROUND_PRIMARY);
                } else {
                    horizontalBar.setBackground(ModernTheme.LIGHT_BACKGROUND);
                    horizontalBar.setForeground(Color.BLACK);
                }
            }

            Color viewportBg = isDarkTheme ? ModernTheme.BACKGROUND_DARK : ModernTheme.LIGHT_BACKGROUND;
            scrollPane.getViewport().setBackground(viewportBg);
            scrollPane.setBackground(viewportBg);
        }

        if (comp instanceof Container) {
            for (Component child : ((Container) comp).getComponents()) {
                updateScrollPaneTheme(child, isDarkTheme);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Split-pane divider walker
    // -------------------------------------------------------------------------

    /**
     * Recursively updates every {@link JSplitPane} divider colour in the tree.
     * Uses {@link ThemedSplitPaneUI} for direct paint control — the only
     * reliable way to override divider colour without touching global UIManager
     * defaults.
     *
     * @param comp        root of the component tree to update
     * @param isDarkTheme {@code true} for dark theme, {@code false} for light
     */
    public static void updateSplitPaneDividers(Component comp, boolean isDarkTheme) {
        if (comp instanceof JSplitPane) {
            JSplitPane splitPane = (JSplitPane) comp;
            Color dividerColor = isDarkTheme ? ModernTheme.BORDER_DEFAULT : ModernTheme.LIGHT_BORDER;
            splitPane.setUI(new ThemedSplitPaneUI(dividerColor));
            splitPane.setBorder(null);
            splitPane.setDividerSize(4);
        }

        if (comp instanceof Container) {
            for (Component child : ((Container) comp).getComponents()) {
                updateSplitPaneDividers(child, isDarkTheme);
            }
        }
    }
}
