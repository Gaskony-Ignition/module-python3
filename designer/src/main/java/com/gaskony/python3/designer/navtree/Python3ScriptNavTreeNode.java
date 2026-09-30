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
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.FileWriter;
import java.util.Collections;
import java.util.List;

/**
 * Leaf nav tree node representing a single Python 3 script in the Project Browser.
 */
public class Python3ScriptNavTreeNode extends AbstractNavTreeNode {
    private static final Logger logger = LoggerFactory.getLogger(Python3ScriptNavTreeNode.class);

    private ScriptMetadata metadata;
    private final Python3RootNavTreeNode rootNavNode;

    public Python3ScriptNavTreeNode(ScriptMetadata metadata, Python3RootNavTreeNode rootNavNode) {
        this.metadata = metadata;
        this.rootNavNode = rootNavNode;

        setName(metadata.getName());
        setText(metadata.getName());
        setIcon(Python3NavTreeIcons.getScriptIcon());

        String tip = metadata.getName();
        if (metadata.getAuthor() != null && !metadata.getAuthor().isEmpty()) {
            tip += " (by " + metadata.getAuthor() + ")";
        }
        if (metadata.getLastModified() != null && !metadata.getLastModified().isEmpty()) {
            tip += " - Modified: " + metadata.getLastModified();
        }
        setTooltip(tip);
    }

    public ScriptMetadata getMetadata() {
        return metadata;
    }

    @Override
    public String getText() {
        return metadata.getName();
    }

    @Override
    public Icon getIcon() {
        return Python3NavTreeIcons.getScriptIcon();
    }

    @Override
    public boolean isLeaf() {
        return true;
    }

    @Override
    public boolean isEditable() {
        return true;
    }

    @Override
    public void onEdit(String newText) {
        if (newText == null || newText.trim().isEmpty() || newText.equals(metadata.getName())) {
            return;
        }

        String oldName = metadata.getName();
        String trimmed = newText.trim();

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                Python3RestClient restClient = rootNavNode.getRestClient();
                SavedScript script = restClient.loadScript(oldName);
                restClient.saveScript(trimmed, script.getCode(), script.getDescription(),
                    script.getAuthor() != null ? script.getAuthor() : "Unknown",
                    script.getFolderPath() != null ? script.getFolderPath() : "",
                    script.getVersion() != null ? script.getVersion() : "1.0");
                restClient.deleteScript(oldName);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    logger.info("Renamed script '{}' to '{}'", oldName, trimmed);
                    rootNavNode.refreshFromGateway();
                } catch (Exception ex) {
                    logger.error("Failed to rename script", ex);
                }
            }
        }.execute();
    }

    @Override
    public void onDoubleClick() {
        rootNavNode.openScriptInConsole(metadata.getName());
    }

    @Override
    public boolean canDelete(List<AbstractNavTreeNode> selectedNodes) {
        return true;
    }

    @Override
    public void doDelete(List<? extends AbstractNavTreeNode> selectedNodes, DeleteReason reason) {
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                rootNavNode.getRestClient().deleteScript(metadata.getName());
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    logger.info("Deleted script: {}", metadata.getName());
                    rootNavNode.refreshFromGateway();
                } catch (Exception ex) {
                    logger.error("Failed to delete script: {}", metadata.getName(), ex);
                }
            }
        }.execute();
    }

    @Override
    public boolean confirmDelete(List<? extends AbstractNavTreeNode> selectedNodes) {
        return DarkDialog.showConfirm(null,
            "Delete script '" + metadata.getName() + "'?", "Confirm Delete");
    }

    @Override
    protected List<AbstractNavTreeNode> loadChildren() {
        return Collections.emptyList();
    }

    @Override
    protected void initPopupMenu(JPopupMenu menu, TreePath[] selectionPaths,
                                 List<AbstractNavTreeNode> selectedNodes, int modifiers) {

        JMenuItem openConsole = new JMenuItem("Open in Script Console");
        openConsole.addActionListener(e -> rootNavNode.openScriptInConsole(metadata.getName()));
        menu.add(openConsole);

        menu.addSeparator();

        JMenuItem renameItem = new JMenuItem("Rename...");
        renameItem.addActionListener(e -> {
            java.awt.Component parentFrame = rootNavNode.getDesignerContext().getFrame();
            String newName = DarkDialog.showInput(parentFrame,
                "Enter new name:", "Rename Script", metadata.getName());
            if (newName != null && !newName.trim().isEmpty()) {
                onEdit(newName.trim());
            }
        });
        menu.add(renameItem);

        JMenuItem deleteItem = new JMenuItem("Delete");
        deleteItem.addActionListener(e -> {
            if (confirmDelete(selectedNodes)) {
                doDelete(selectedNodes, DeleteReason.Delete);
            }
        });
        menu.add(deleteItem);

        menu.addSeparator();

        JMenuItem exportItem = new JMenuItem("Export as .py...");
        exportItem.addActionListener(e -> exportScript());
        menu.add(exportItem);

        JMenuItem copyNameItem = new JMenuItem("Copy Name");
        copyNameItem.addActionListener(e -> {
            StringSelection sel = new StringSelection(metadata.getName());
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, null);
        });
        menu.add(copyNameItem);
    }

    private void exportScript() {
        new SwingWorker<SavedScript, Void>() {
            @Override
            protected SavedScript doInBackground() throws Exception {
                return rootNavNode.getRestClient().loadScript(metadata.getName());
            }

            @Override
            protected void done() {
                try {
                    SavedScript script = get();
                    javax.swing.JFileChooser chooser = new javax.swing.JFileChooser();
                    chooser.setSelectedFile(new File(metadata.getName() + ".py"));
                    int result = chooser.showSaveDialog(null);
                    if (result == javax.swing.JFileChooser.APPROVE_OPTION) {
                        try (FileWriter writer = new FileWriter(chooser.getSelectedFile())) {
                            writer.write(script.getCode());
                        }
                        logger.info("Exported script to: {}", chooser.getSelectedFile().getAbsolutePath());
                    }
                } catch (Exception ex) {
                    logger.error("Failed to export script", ex);
                }
            }
        }.execute();
    }
}
