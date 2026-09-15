package com.gaskony.python3.designer;

import com.inductiveautomation.ignition.common.licensing.LicenseState;
import com.inductiveautomation.ignition.designer.model.AbstractDesignerModuleHook;
import com.inductiveautomation.ignition.designer.model.DesignerContext;
import com.gaskony.python3.designer.managers.ProjectBrowserManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.awt.Frame;
import java.io.InputStream;
import java.io.IOException;
import java.util.Properties;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Designer hook for the Python 3 Integration module.
 *
 * <p>This hook integrates the Python 3 IDE into the Ignition Designer by adding
 * a menu item to the Tools menu. The IDE communicates with the Gateway via REST API.</p>
 *
 * <p>Lifecycle:</p>
 * <ul>
 *   <li>startup() - Adds "Python 3 Script Console" menu item to Tools menu</li>
 *   <li>shutdown() - Closes Script Console window if open</li>
 * </ul>
 */
public class DesignerHook extends AbstractDesignerModuleHook {
    private static final Logger logger = LoggerFactory.getLogger(DesignerHook.class);
    private static final String MENU_ITEM_PREFIX = "Python 3 Script Console";

    private DesignerContext context;
    private JFrame scriptConsoleFrame;
    private ProjectBrowserManager projectBrowserManager;
    private volatile boolean menuItemAdded = false;

    /**
     * Called when the Designer module is starting up.
     *
     * @param context the Designer context
     * @param activationState the license state
     */
    @Override
    public void startup(DesignerContext context, LicenseState activationState) throws Exception {
        super.startup(context, activationState);

        // CRITICAL: Store context immediately at startup
        this.context = context;

        logger.info("Python 3 Integration Designer module starting up");

        // Add menu item to Tools menu (call directly, no deferral needed)
        addToolsMenuItem();

        // Register Python 3 Scripts node in the Project Browser (non-fatal if it fails)
        try {
            projectBrowserManager = new ProjectBrowserManager(context);
            projectBrowserManager.register();

            // Wire up action so nav tree nodes can open Script Console
            if (projectBrowserManager.getRootNode() != null) {
                projectBrowserManager.getRootNode().setOpenScriptConsoleAction(
                    this::openPython3ScriptConsole);
            }
        } catch (Exception e) {
            logger.warn("Failed to register Project Browser integration (non-fatal)", e);
        }

        logger.info("Python 3 Integration Designer module startup complete");
        logger.info("Python 3 Script Console available from Tools menu and Project Browser");
    }

    /**
     * Called when the Designer module is shutting down.
     */
    @Override
    public void shutdown() {
        super.shutdown();

        logger.info("Python 3 Integration Designer module shutting down");

        // Unregister Project Browser integration
        if (projectBrowserManager != null) {
            projectBrowserManager.unregister();
        }

        // Close Script Console window if open
        if (scriptConsoleFrame != null && scriptConsoleFrame.isVisible()) {
            scriptConsoleFrame.dispose();
            scriptConsoleFrame = null;
        }

        logger.info("Python 3 Integration Designer module shutdown complete");
    }

    /**
     * Adds the "Python 3 Script Console" menu item to the Tools menu.
     */
    private void addToolsMenuItem() {
        // Guard: only add menu item once
        if (menuItemAdded) {
            logger.debug("Menu item already added, skipping duplicate registration");
            return;
        }

        try {
            logger.info("Attempting to add Python 3 Script Console menu item...");

            Frame designerFrame = context.getFrame();
            if (!(designerFrame instanceof JFrame)) {
                logger.warn("Designer frame is not a JFrame, cannot add menu item");
                return;
            }

            JFrame jFrame = (JFrame) designerFrame;
            JMenuBar menuBar = jFrame.getJMenuBar();

            if (menuBar == null) {
                logger.warn("Could not get menu bar from Designer frame");
                return;
            }

            // Find or create Tools menu
            JMenu toolsMenu = findMenu(menuBar, "Tools");
            if (toolsMenu == null) {
                toolsMenu = new JMenu("Tools");
                menuBar.add(toolsMenu);
            }

            // Check if menu item already exists (defensive check against duplicate registration)
            for (int i = 0; i < toolsMenu.getItemCount(); i++) {
                JMenuItem existingItem = toolsMenu.getItem(i);
                if (existingItem != null && existingItem.getText() != null
                        && existingItem.getText().startsWith(MENU_ITEM_PREFIX)) {
                    logger.info("Python 3 Script Console menu item already exists, skipping");
                    menuItemAdded = true;
                    return;
                }
            }

            // Add separator if menu is not empty
            if (toolsMenu.getItemCount() > 0) {
                toolsMenu.addSeparator();
            }

            String menuVersion = getModuleVersion();
            JMenuItem scriptConsoleItem = new JMenuItem(MENU_ITEM_PREFIX + " v" + menuVersion);
            scriptConsoleItem.setToolTipText("Open the Python 3 Script Console for writing and testing Python code");
            scriptConsoleItem.addActionListener(e -> openPython3ScriptConsole());
            toolsMenu.add(scriptConsoleItem);

            menuItemAdded = true;
            logger.info("Successfully added 'Python 3 Script Console' menu item to Tools menu");

        } catch (Exception e) {
            logger.error("Failed to add Tools menu item", e);
        }
    }

    /**
     * Finds a menu by name in the menu bar.
     *
     * @param menuBar the menu bar to search
     * @param menuName the menu name to find
     * @return the menu, or null if not found
     */
    private JMenu findMenu(JMenuBar menuBar, String menuName) {
        for (int i = 0; i < menuBar.getMenuCount(); i++) {
            JMenu menu = menuBar.getMenu(i);
            if (menu != null && menuName.equals(menu.getText())) {
                return menu;
            }
        }
        return null;
    }

    /**
     * Gets the module version from version.properties.
     */
    private static String getModuleVersion() {
        try (InputStream is = DesignerHook.class.getResourceAsStream("/version.properties")) {
            if (is != null) {
                Properties props = new Properties();
                props.load(is);
                String major = props.getProperty("version.major", "3");
                String minor = props.getProperty("version.minor", "0");
                String patch = props.getProperty("version.patch", "0");
                return major + "." + minor + "." + patch;
            }
        } catch (IOException e) {
            logger.warn("Failed to load version.properties, using fallback version", e);
        }
        return "4.6.2"; // MODULE_VERSION_FALLBACK — kept current by the syncVersion Gradle task
    }

    /**
     * Opens the Python 3 Script Console window.
     */
    private void openPython3ScriptConsole() {
        openPython3ScriptConsole(null);
    }

    /**
     * Opens the Python 3 Script Console, optionally loading a specific script.
     *
     * @param scriptName the script to load, or null for an empty console
     */
    private void openPython3ScriptConsole(String scriptName) {
        logger.info("Opening Python 3 Script Console{}",
            scriptName != null ? " with script: " + scriptName : "");

        if (scriptConsoleFrame != null && scriptConsoleFrame.isVisible()) {
            scriptConsoleFrame.toFront();
            scriptConsoleFrame.requestFocus();
            if (scriptName != null && scriptConsoleFrame.getContentPane() instanceof Python3ScriptConsole) {
                ((Python3ScriptConsole) scriptConsoleFrame.getContentPane()).openScript(scriptName);
            }
            return;
        }

        SwingUtilities.invokeLater(() -> {
            try {
                // Construct the console panel (this also sets UIManager dark defaults)
                Python3ScriptConsole consolePanel = new Python3ScriptConsole(context);
                String version = getModuleVersion();

                scriptConsoleFrame = new JFrame("Python 3 Script Console v" + version);
                scriptConsoleFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

                // Apply dark background to frame internals to prevent white flash
                scriptConsoleFrame.setBackground(ModernTheme.BACKGROUND_DARK);
                scriptConsoleFrame.getRootPane().setBackground(ModernTheme.BACKGROUND_DARK);
                scriptConsoleFrame.getRootPane().setOpaque(true);
                scriptConsoleFrame.getLayeredPane().setBackground(ModernTheme.BACKGROUND_DARK);

                scriptConsoleFrame.setContentPane(consolePanel);
                scriptConsoleFrame.setIconImage(DarkDialog.createPython3Icon());
                scriptConsoleFrame.setSize(1000, 700);
                scriptConsoleFrame.setLocationRelativeTo(context.getFrame());

                // Clean up reference when window is closed
                scriptConsoleFrame.addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosed(WindowEvent e) {
                        scriptConsoleFrame = null;
                    }
                });

                scriptConsoleFrame.setVisible(true);

                logger.info("Python 3 Script Console v{} window opened", version);

                if (scriptName != null) {
                    consolePanel.openScript(scriptName);
                }
            } catch (Exception e) {
                logger.error("Failed to open Python 3 Script Console", e);
                JOptionPane.showMessageDialog(
                        context.getFrame(),
                        "Failed to open Python 3 Script Console: " + e.getMessage(),
                        "Error",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        });
    }
}
