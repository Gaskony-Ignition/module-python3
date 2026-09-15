package com.gaskony.python3.designer;

import javax.swing.JSplitPane;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import java.awt.Color;
import java.awt.Graphics;

/**
 * Custom SplitPaneUI that properly themes the divider.
 *
 * This is an alternative approach to UIManager-based theming, which has proven
 * unreliable in the Ignition Designer environment. By extending BasicSplitPaneUI
 * and providing a custom divider, we have direct control over divider appearance.
 *
 * v2.3.3: Created to solve persistent light gray divider issue
 */
public class ThemedSplitPaneUI extends BasicSplitPaneUI {

    private final Color dividerColor;

    /**
     * Creates a themed split pane UI with the specified divider color.
     *
     * @param dividerColor the color for the divider
     */
    public ThemedSplitPaneUI(Color dividerColor) {
        this.dividerColor = dividerColor;
    }

    /**
     * Creates the divider with custom theming.
     *
     * @return themed divider
     */
    @Override
    public BasicSplitPaneDivider createDefaultDivider() {
        return new BasicSplitPaneDivider(this) {
            @Override
            public void paint(Graphics g) {
                // Paint the entire divider with the theme color
                g.setColor(dividerColor);
                g.fillRect(0, 0, getWidth(), getHeight());
            }

            @Override
            public void setBackground(Color bg) {
                // Override to prevent external color changes
                super.setBackground(dividerColor);
            }
        };
    }
}
