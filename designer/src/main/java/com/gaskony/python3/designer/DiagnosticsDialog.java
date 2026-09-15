package com.gaskony.python3.designer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Window;

/**
 * Dialog hosting the {@link DiagnosticsPanel} for the Script Console (v4.3.0).
 *
 * <p>Gives Designer users — who may have no Gateway web access — a read-only
 * view of pool statistics, gateway impact, module logs, and the Python
 * environment (charter §4, workflows 4 and 5).</p>
 *
 * <p>Deliberately modeless (overriding the {@link BaseModuleDialog} default)
 * so a developer can run scripts in the console and watch pool stats and
 * gateway impact respond.</p>
 */
public class DiagnosticsDialog extends BaseModuleDialog {

    private final DiagnosticsPanel panel;

    public DiagnosticsDialog(Window parent, Python3RestClient restClient, boolean isDark) {
        super(parent, "Python 3 Diagnostics", 920, 640);
        setModalityType(ModalityType.MODELESS);

        panel = new DiagnosticsPanel();
        panel.applyTheme(isDark);
        panel.setRestClient(restClient);

        setLayout(new BorderLayout());
        add(panel, BorderLayout.CENTER);
    }

    /**
     * Applies dark/light theming to the hosted panel and the dialog surface.
     * Called on open/reopen AND live from the Script Console's theme toggle so
     * an already-open dialog follows the console's theme (v4.3.1).
     */
    public void applyTheme(boolean isDark) {
        getContentPane().setBackground(isDark ? ModernTheme.BACKGROUND_DARK : Color.WHITE);
        panel.applyTheme(isDark);
    }

    /** Re-applies dark/light theming, refreshes content, and brings the dialog forward. */
    public void reopen(boolean isDark) {
        applyTheme(isDark);
        panel.refreshMetrics();
        panel.refreshLogs();
        setVisible(true);
        toFront();
    }
}
