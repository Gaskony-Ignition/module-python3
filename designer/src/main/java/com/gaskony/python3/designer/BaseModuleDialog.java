package com.gaskony.python3.designer;

import javax.swing.JDialog;
import java.awt.Window;

/**
 * Abstract base class for all Python 3 Integration module dialogs.
 *
 * <p>Consolidates the boilerplate constructor sequence shared by every dialog
 * in this module: modal application dialog, fixed size, centred on parent,
 * and dispose-on-close.</p>
 *
 * <p>Subclasses call the single super constructor and then perform their own
 * component creation and layout:</p>
 * <pre>
 *   public class MyDialog extends BaseModuleDialog {
 *       public MyDialog(Window parent) {
 *           super(parent, "My Dialog Title", 600, 400);
 *           initComponents();
 *           layoutComponents();
 *       }
 *   }
 * </pre>
 */
public abstract class BaseModuleDialog extends JDialog {

    /**
     * Initialises a module dialog with standard presentation settings.
     *
     * @param parent the owner window used for modality and centering
     * @param title  dialog title shown in the title bar
     * @param width  preferred width in pixels
     * @param height preferred height in pixels
     */
    protected BaseModuleDialog(Window parent, String title, int width, int height) {
        super(parent, title, ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(width, height);
        setLocationRelativeTo(parent);
    }
}
