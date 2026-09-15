package com.gaskony.python3.designer.navtree;

import com.inductiveautomation.ignition.designer.model.DesignerContext;
import com.inductiveautomation.ignition.designer.navtree.model.AbstractNavTreeNode;
import com.gaskony.python3.designer.DarkDialog;
import com.gaskony.python3.designer.Python3RestClient;
import com.gaskony.python3.designer.ScriptMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.Icon;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Top-level "Python 3 Scripts" node in the Designer Project Browser.
 * Orchestrates REST loading, caching, and auto-refresh.
 */
public class Python3RootNavTreeNode extends AbstractNavTreeNode {
    private static final Logger logger = LoggerFactory.getLogger(Python3RootNavTreeNode.class);

    private static final long CACHE_TTL_MS = 30_000;
    private static final int AUTO_REFRESH_INTERVAL_MS = 30_000;

    private final DesignerContext context;
    private final Python3RestClient restClient;

    private volatile List<ScriptMetadata> cachedScripts;
    private volatile long lastFetchTimestamp;
    private volatile boolean gatewayConnected = true;

    private Timer autoRefreshTimer;

    /** Callback to open a script in the Script Console. Set by ProjectBrowserManager. */
    private java.util.function.Consumer<String> openScriptConsoleAction;

    public Python3RootNavTreeNode(DesignerContext context) {
        this.context = context;
        this.restClient = new Python3RestClient(context);

        setName("Python 3 Scripts");
        setText("Python 3 Scripts");
        setIcon(Python3NavTreeIcons.getPython3RootIcon());
        setTooltip("Python 3 scripts managed on the Gateway");
        setBold(true);
    }

    public DesignerContext getDesignerContext() {
        return context;
    }

    public Python3RestClient getRestClient() {
        return restClient;
    }

    public void setOpenScriptConsoleAction(java.util.function.Consumer<String> action) {
        this.openScriptConsoleAction = action;
    }

    void openScriptInConsole(String scriptName) {
        if (openScriptConsoleAction != null) {
            openScriptConsoleAction.accept(scriptName);
        } else {
            logger.warn("No script console action registered");
        }
    }

    @Override
    public String getText() {
        return "Python 3 Scripts";
    }

    @Override
    public Icon getIcon() {
        return gatewayConnected
            ? Python3NavTreeIcons.getPython3RootIcon()
            : Python3NavTreeIcons.getDisconnectedIcon();
    }

    @Override
    public boolean isBold() {
        return true;
    }

    @Override
    public boolean isItalic() {
        return !gatewayConnected;
    }

    @Override
    public boolean isLeaf() {
        return false;
    }

    @Override
    protected List<AbstractNavTreeNode> loadChildren() {
        if (isCacheValid()) {
            return buildTreeFromMetadata(cachedScripts);
        }

        try {
            cachedScripts = restClient.listScripts();
            lastFetchTimestamp = System.currentTimeMillis();
            gatewayConnected = true;
            return buildTreeFromMetadata(cachedScripts);
        } catch (Exception e) {
            logger.warn("Failed to load scripts from Gateway: {}", e.getMessage());
            gatewayConnected = false;
            List<AbstractNavTreeNode> placeholder = new ArrayList<>();
            AbstractNavTreeNode offlineNode = new AbstractNavTreeNode() {
                {
                    setName("(Gateway unavailable)");
                    setText("(Gateway unavailable)");
                    setItalic(true);
                }

                @Override
                public boolean isLeaf() {
                    return true;
                }

                @Override
                protected List<AbstractNavTreeNode> loadChildren() {
                    return java.util.Collections.emptyList();
                }
            };
            placeholder.add(offlineNode);
            return placeholder;
        }
    }

    private boolean isCacheValid() {
        return cachedScripts != null
            && (System.currentTimeMillis() - lastFetchTimestamp) < CACHE_TTL_MS;
    }

    /**
     * Asynchronously refreshes the tree from the Gateway REST API.
     * Only rebuilds the tree if the script list has actually changed,
     * preventing unnecessary UI flicker and tree collapse.
     */
    public void refreshFromGateway() {
        new SwingWorker<List<ScriptMetadata>, Void>() {
            @Override
            protected List<ScriptMetadata> doInBackground() throws Exception {
                return restClient.listScripts();
            }

            @Override
            protected void done() {
                try {
                    List<ScriptMetadata> newScripts = get();
                    lastFetchTimestamp = System.currentTimeMillis();
                    gatewayConnected = true;

                    // Only rebuild tree if data has actually changed
                    if (isScriptListUnchanged(cachedScripts, newScripts)) {
                        logger.debug("Auto-refresh: no changes detected, skipping tree rebuild");
                        return;
                    }

                    cachedScripts = newScripts;

                    // Rebuild tree with new data
                    List<AbstractNavTreeNode> newChildren = buildTreeFromMetadata(cachedScripts);
                    setChildren(newChildren, true);
                } catch (Exception ex) {
                    logger.warn("Auto-refresh failed: {}", ex.getMessage());
                    gatewayConnected = false;
                }
            }
        }.execute();
    }

    /**
     * Compares two script lists to determine if the tree needs rebuilding.
     * Returns true if the lists are equivalent (same scripts, same metadata).
     */
    private boolean isScriptListUnchanged(List<ScriptMetadata> oldList, List<ScriptMetadata> newList) {
        if (oldList == null || newList == null) return false;
        if (oldList.size() != newList.size()) return false;

        // Build a set of script signatures from old list
        java.util.Set<String> oldSignatures = new java.util.HashSet<>();
        for (ScriptMetadata s : oldList) {
            oldSignatures.add(scriptSignature(s));
        }

        // Check if all new scripts match
        for (ScriptMetadata s : newList) {
            if (!oldSignatures.contains(scriptSignature(s))) {
                return false;
            }
        }
        return true;
    }

    private String scriptSignature(ScriptMetadata s) {
        return s.getName() + "|" +
               (s.getFolderPath() != null ? s.getFolderPath() : "") + "|" +
               (s.getLastModified() != null ? s.getLastModified() : "");
    }

    /**
     * Starts periodic auto-refresh (30 second interval).
     */
    public void startAutoRefresh() {
        if (autoRefreshTimer != null) {
            autoRefreshTimer.stop();
        }
        autoRefreshTimer = new Timer(AUTO_REFRESH_INTERVAL_MS, e -> refreshFromGateway());
        autoRefreshTimer.setInitialDelay(AUTO_REFRESH_INTERVAL_MS);
        autoRefreshTimer.start();
        logger.info("Python 3 nav tree auto-refresh started ({}s interval)",
            AUTO_REFRESH_INTERVAL_MS / 1000);
    }

    /**
     * Stops periodic auto-refresh.
     */
    public void stopAutoRefresh() {
        if (autoRefreshTimer != null) {
            autoRefreshTimer.stop();
            autoRefreshTimer = null;
            logger.info("Python 3 nav tree auto-refresh stopped");
        }
    }

    /**
     * Converts a flat list of ScriptMetadata into a folder/script hierarchy.
     * Builds intermediate folder nodes on demand so nested paths render correctly.
     */
    private List<AbstractNavTreeNode> buildTreeFromMetadata(List<ScriptMetadata> scripts) {
        List<AbstractNavTreeNode> rootChildren = new ArrayList<>();
        Map<String, Python3FolderNavTreeNode> folders = new HashMap<>();

        for (ScriptMetadata script : scripts) {
            String folderPath = script.getFolderPath();
            if (folderPath == null || folderPath.isEmpty()) {
                rootChildren.add(new Python3ScriptNavTreeNode(script, this));
            } else {
                Python3FolderNavTreeNode folder = getOrCreateFolder(
                    folderPath, folders, rootChildren);
                folder.addChildNode(new Python3ScriptNavTreeNode(script, this));
            }
        }

        return rootChildren;
    }

    private Python3FolderNavTreeNode getOrCreateFolder(
            String folderPath,
            Map<String, Python3FolderNavTreeNode> folders,
            List<AbstractNavTreeNode> rootChildren) {

        if (folders.containsKey(folderPath)) {
            return folders.get(folderPath);
        }

        String[] parts = folderPath.split("/");
        Python3FolderNavTreeNode currentParent = null;
        StringBuilder currentPath = new StringBuilder();

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (i > 0) {
                currentPath.append("/");
            }
            currentPath.append(part);
            String pathSoFar = currentPath.toString();

            if (folders.containsKey(pathSoFar)) {
                currentParent = folders.get(pathSoFar);
            } else {
                Python3FolderNavTreeNode folderNode =
                    new Python3FolderNavTreeNode(part, pathSoFar, this);
                folders.put(pathSoFar, folderNode);

                if (currentParent != null) {
                    currentParent.addChildNode(folderNode);
                } else {
                    rootChildren.add(folderNode);
                }
                currentParent = folderNode;
            }
        }

        return currentParent;
    }

    /**
     * Creates a new script (prompts user for name, saves via REST).
     */
    void createNewScript(String folderPath) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("Name", "");
        fields.put("Description", "");

        Map<String, String> result = DarkDialog.showMultiInput(
            context.getFrame(), "New Script", fields);
        if (result == null) {
            return;
        }

        String name = result.get("Name");
        String description = result.get("Description");

        if (name == null || name.trim().isEmpty()) {
            DarkDialog.showMessage(context.getFrame(),
                "Script name is required.", "New Script");
            return;
        }

        String defaultCode = "# " + name.trim() + "\n# Press Ctrl+Enter to run\n\nprint('Hello from " + name.trim() + "')\n";

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                restClient.saveScript(name.trim(), defaultCode, description,
                    "Designer", folderPath != null ? folderPath : "", "1.0");
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    logger.info("Created new script: {} in folder: {}", name.trim(), folderPath);
                    refreshFromGateway();
                } catch (Exception ex) {
                    logger.error("Failed to create script", ex);
                    DarkDialog.showMessage(context.getFrame(),
                        "Failed to create script: " + ex.getMessage(), "Error");
                }
            }
        }.execute();
    }

    /**
     * Creates a new empty folder by saving a placeholder __init__ script inside it.
     * This ensures the folder persists on the Gateway and appears in the tree.
     */
    void createNewFolder(String parentPath) {
        String folderName = DarkDialog.showInput(context.getFrame(),
            "Enter folder name:", "New Folder", "");
        if (folderName == null || folderName.trim().isEmpty()) {
            return;
        }

        String trimmed = folderName.trim();
        String fullPath = (parentPath != null && !parentPath.isEmpty())
            ? parentPath + "/" + trimmed
            : trimmed;

        // Save a placeholder __init__ script so the folder exists on the Gateway
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                restClient.saveScript("__init__",
                    "# Package init for " + fullPath + "\n",
                    "Auto-created folder marker",
                    "Designer", fullPath, "1.0");
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    logger.info("Created folder: {}", fullPath);
                    refreshFromGateway();
                } catch (Exception ex) {
                    logger.error("Failed to create folder", ex);
                    DarkDialog.showMessage(context.getFrame(),
                        "Failed to create folder: " + ex.getMessage(), "Error");
                }
            }
        }.execute();
    }

    @Override
    protected void initPopupMenu(JPopupMenu menu, TreePath[] selectionPaths,
                                 List<AbstractNavTreeNode> selectedNodes, int modifiers) {

        JMenuItem newScriptItem = new JMenuItem("New Script...");
        newScriptItem.addActionListener(e -> createNewScript(""));
        menu.add(newScriptItem);

        JMenuItem newFolderItem = new JMenuItem("New Folder...");
        newFolderItem.addActionListener(e -> createNewFolder(""));
        menu.add(newFolderItem);

        menu.addSeparator();

        JMenuItem refreshItem = new JMenuItem("Refresh");
        refreshItem.addActionListener(e -> refreshFromGateway());
        menu.add(refreshItem);

        menu.addSeparator();

        JMenuItem openConsoleItem = new JMenuItem("Open Script Console");
        openConsoleItem.addActionListener(e -> openScriptInConsole(null));
        menu.add(openConsoleItem);
    }

    @Override
    protected void uninstall() {
        stopAutoRefresh();
        super.uninstall();
    }
}
