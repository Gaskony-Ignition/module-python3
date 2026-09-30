package com.gaskony.python3.designer.managers;

import com.inductiveautomation.ignition.designer.model.DesignerContext;
import com.inductiveautomation.ignition.designer.navtree.model.ProjectBrowserRoot;
import com.gaskony.python3.designer.navtree.Python3RootNavTreeNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lifecycle manager for the Python 3 Project Browser integration.
 * Handles registration and cleanup of the nav tree node.
 */
public class ProjectBrowserManager {
    private static final Logger logger = LoggerFactory.getLogger(ProjectBrowserManager.class);

    private final DesignerContext context;
    private Python3RootNavTreeNode rootNode;

    public ProjectBrowserManager(DesignerContext context) {
        this.context = context;
    }

    /**
     * Gets the root nav tree node (for wiring up actions from DesignerHook).
     */
    public Python3RootNavTreeNode getRootNode() {
        return rootNode;
    }

    /**
     * Registers the Python 3 Scripts node in the Project Browser.
     */
    public void register() {
        try {
            rootNode = new Python3RootNavTreeNode(context);

            ProjectBrowserRoot browserRoot = context.getProjectBrowserRoot();
            browserRoot.addChild(rootNode);

            rootNode.startAutoRefresh();

            logger.info("Python 3 Scripts node registered in Project Browser");
        } catch (Exception e) {
            logger.error("Failed to register Python 3 Scripts in Project Browser", e);
        }
    }

    /**
     * Unregisters the Python 3 Scripts node and stops auto-refresh.
     */
    public void unregister() {
        try {
            if (rootNode != null) {
                rootNode.stopAutoRefresh();
                logger.info("Python 3 Scripts node unregistered from Project Browser");
            }
        } catch (Exception e) {
            logger.error("Error during Project Browser cleanup", e);
        }
    }

    /**
     * Triggers a manual refresh of the nav tree.
     */
    public void refresh() {
        if (rootNode != null) {
            rootNode.refreshFromGateway();
        }
    }
}
