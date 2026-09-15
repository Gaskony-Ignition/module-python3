package com.gaskony.python3.designer.navtree;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Static utility providing cached 16x16 icons for the Python 3 nav tree nodes.
 */
public final class Python3NavTreeIcons {

    private Python3NavTreeIcons() {
        throw new AssertionError("Utility class");
    }

    private static Icon python3RootIcon;
    private static Icon scriptIcon;
    private static Icon folderIcon;
    private static Icon folderOpenIcon;
    private static Icon disconnectedIcon;

    /**
     * Gets the Python 3 root node icon (blue circle with "P3" text, 16x16).
     */
    public static Icon getPython3RootIcon() {
        if (python3RootIcon == null) {
            python3RootIcon = new ImageIcon(createP3Icon(
                new Color(53, 114, 165), new Color(48, 105, 152), Color.WHITE));
        }
        return python3RootIcon;
    }

    /**
     * Gets the script leaf icon.
     */
    public static Icon getScriptIcon() {
        if (scriptIcon == null) {
            Icon uiIcon = UIManager.getIcon("Tree.leafIcon");
            scriptIcon = uiIcon != null ? uiIcon : new ImageIcon(createP3Icon(
                new Color(80, 140, 80), new Color(60, 120, 60), Color.WHITE));
        }
        return scriptIcon;
    }

    /**
     * Gets the closed folder icon.
     */
    public static Icon getFolderIcon() {
        if (folderIcon == null) {
            Icon uiIcon = UIManager.getIcon("Tree.closedIcon");
            folderIcon = uiIcon != null ? uiIcon : new ImageIcon(createP3Icon(
                new Color(180, 160, 60), new Color(160, 140, 40), Color.WHITE));
        }
        return folderIcon;
    }

    /**
     * Gets the open folder icon.
     */
    public static Icon getFolderOpenIcon() {
        if (folderOpenIcon == null) {
            Icon uiIcon = UIManager.getIcon("Tree.openIcon");
            folderOpenIcon = uiIcon != null ? uiIcon : getFolderIcon();
        }
        return folderOpenIcon;
    }

    /**
     * Gets a grayed-out variant of the root icon for disconnected state.
     */
    public static Icon getDisconnectedIcon() {
        if (disconnectedIcon == null) {
            disconnectedIcon = new ImageIcon(createP3Icon(
                new Color(120, 120, 120), new Color(100, 100, 100), new Color(180, 180, 180)));
        }
        return disconnectedIcon;
    }

    private static BufferedImage createP3Icon(Color gradStart, Color gradEnd, Color textColor) {
        int size = 16;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        GradientPaint gradient = new GradientPaint(0, 0, gradStart, size, size, gradEnd);
        g.setPaint(gradient);
        g.fillOval(0, 0, size, size);

        g.setColor(textColor);
        g.setFont(new Font("Arial", Font.BOLD, 9));
        FontMetrics fm = g.getFontMetrics();
        String text = "P3";
        int textX = (size - fm.stringWidth(text)) / 2;
        int textY = (size - fm.getHeight()) / 2 + fm.getAscent();
        g.drawString(text, textX, textY);

        g.dispose();
        return img;
    }
}
