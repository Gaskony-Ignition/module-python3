package com.gaskony.python3.designer.navtree;

import com.inductiveautomation.ignition.designer.navtree.model.AbstractNavTreeNode;
import com.gaskony.python3.designer.DarkDialog;
import com.gaskony.python3.designer.Python3RestClient;
import com.gaskony.python3.designer.SavedScript;
import com.gaskony.python3.designer.ScriptMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.Icon;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingWorker;
import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.List;

/**
 * Folder nav tree node in the Python 3 Project Browser tree.
 * Children are pre-built by the root node, not loaded separately.
 */
public class Python3FolderNavTreeNode extends AbstractNavTreeNode {
    private static final Logger logger = LoggerFactory.getLogger(Python3FolderNavTreeNode.class);

    private final String folderName;
    private final String fullPath;
    private final Python3RootNavTreeNode rootNavNode;
    private final List<AbstractNavTreeNode> childNodes;

    public Python3FolderNavTreeNode(String folderName, String fullPath,
                                    Python3RootNavTreeNode rootNavNode) {
        this.folderName = folderName;
        this.fullPath = fullPath;
        this.rootNavNode = rootNavNode;
        this.childNodes = new ArrayList<>();

        setName(folderName);
        setText(folderName);
        setIcon(Python3NavTreeIcons.getFolderIcon());
        setTooltip("Folder: " + fullPath);
    }

    public String getFolderName() {
        return folderName;
    }

    public String getFullPath() {
        return fullPath;
    }

    public void addChildNode(AbstractNavTreeNode node) {
        childNodes.add(node);
    }

    @Override
    public String getText() {
        return folderName;
    }

    @Override
    public Icon getIcon() {
        return Python3NavTreeIcons.getFolderIcon();
    }

    @Override
    public Icon getExpandedIcon() {
        return Python3NavTreeIcons.getFolderOpenIcon();
    }

    @Override
    public boolean isLeaf() {
        return false;
    }

    @Override
    public boolean isEditable() {
        return true;
    }

    @Override
    public void onEdit(String newText) {
        if (newText == null || newText.trim().isEmpty() || newText.equals(folderName)) {
            return;
        }

        String oldPath = fullPath;
        // Build new path: replace the last segment
        String newPath;
        int lastSlash = oldPath.lastIndexOf('/');
        if (lastSlash >= 0) {
            newPath = oldPath.substring(0, lastSlash + 1) + newText.trim();
        } else {
            newPath = newText.trim();
        }

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                Python3RestClient restClient = rootNavNode.getRestClient();
                List<ScriptMetadata> allScripts = restClient.listScripts();

                for (ScriptMetadata script : allScripts) {
                    String fp = script.getFolderPath();
                    if (fp != null && (fp.equals(oldPath) || fp.startsWith(oldPath + "/"))) {
                        String updatedPath = newPath + fp.substring(oldPath.length());
                        SavedScript full = restClient.loadScript(script.getName());
                        restClient.saveScript(script.getName(), full.getCode(),
                            full.getDescription(),
                            full.getAuthor() != null ? full.getAuthor() : "Unknown",
                            updatedPath,
                            full.getVersion() != null ? full.getVersion() : "1.0");
                    }
                }
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    logger.info("Renamed folder '{}' to '{}'", oldPath, newPath);
                    rootNavNode.refreshFromGateway();
                } catch (Exception ex) {
                    logger.error("Failed to rename folder", ex);
                }
            }
        }.execute();
    }

    @Override
    public boolean canDelete(List<AbstractNavTreeNode> selectedNodes) {
        return true;
    }

    @Override
    public boolean confirmDelete(List<? extends AbstractNavTreeNode> selectedNodes) {
        return DarkDialog.showConfirm(null,
            "Delete folder '" + folderName + "' and all scripts within it?",
            "Confirm Delete Folder");
    }

    @Override
    public void doDelete(List<? extends AbstractNavTreeNode> selectedNodes, DeleteReason reason) {
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                Python3RestClient restClient = rootNavNode.getRestClient();
                List<ScriptMetadata> allScripts = restClient.listScripts();

                for (ScriptMetadata script : allScripts) {
                    String fp = script.getFolderPath();
                    if (fp != null && (fp.equals(fullPath) || fp.startsWith(fullPath + "/"))) {
                        restClient.deleteScript(script.getName());
                    }
                }
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    logger.info("Deleted folder and contents: {}", fullPath);
                    rootNavNode.refreshFromGateway();
                } catch (Exception ex) {
                    logger.error("Failed to delete folder: {}", fullPath, ex);
                }
            }
        }.execute();
    }

    @Override
    protected List<AbstractNavTreeNode> loadChildren() {
        return new ArrayList<>(childNodes);
    }

    @Override
    protected void initPopupMenu(JPopupMenu menu, TreePath[] selectionPaths,
                                 List<AbstractNavTreeNode> selectedNodes, int modifiers) {

        JMenuItem newScriptItem = new JMenuItem("New Script...");
        newScriptItem.addActionListener(e -> rootNavNode.createNewScript(fullPath));
        menu.add(newScriptItem);

        JMenuItem newFolderItem = new JMenuItem("New Folder...");
        newFolderItem.addActionListener(e -> rootNavNode.createNewFolder(fullPath));
        menu.add(newFolderItem);

        menu.addSeparator();

        JMenuItem renameItem = new JMenuItem("Rename Folder...");
        renameItem.addActionListener(e -> {
            java.awt.Component parentFrame = rootNavNode.getDesignerContext().getFrame();
            String newName = DarkDialog.showInput(parentFrame,
                "Enter new folder name:", "Rename Folder", folderName);
            if (newName != null && !newName.trim().isEmpty()) {
                onEdit(newName.trim());
            }
        });
        menu.add(renameItem);

        JMenuItem deleteItem = new JMenuItem("Delete Folder");
        deleteItem.addActionListener(e -> {
            if (confirmDelete(selectedNodes)) {
                doDelete(selectedNodes, DeleteReason.Delete);
            }
        });
        menu.add(deleteItem);
    }
}
