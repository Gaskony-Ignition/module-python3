package com.gaskony.python3.designer;

import javax.swing.JEditorPane;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Window;

/**
 * In-app help for the Python 3 Script Console (v4.4.0).
 *
 * <p>Explains, without leaving the Designer: how to use the console, how saved
 * scripts become callable from the rest of Ignition via {@code system.python3.*}
 * (Perspective events, tag scripts, timers), what data types cross the bridge,
 * and the injection anti-pattern from the project charter §2. Content mirrors
 * {@code docs/getting-started/INTEGRATION_GUIDE.md} — update both together.</p>
 *
 * <p>Modeless (like {@link DiagnosticsDialog}) so it can stay open next to the
 * console while following the console's light/dark theme.</p>
 */
public class HelpDialog extends BaseModuleDialog {

    private final JEditorPane content;
    private final JScrollPane scroll;

    public HelpDialog(Window parent, boolean isDark) {
        super(parent, "Python 3 Help", 780, 640);
        setModalityType(ModalityType.MODELESS);

        content = new JEditorPane();
        content.setContentType("text/html");
        content.setEditable(false);
        content.setBorder(null);

        scroll = new JScrollPane(content,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);

        setLayout(new BorderLayout());
        add(scroll, BorderLayout.CENTER);

        applyTheme(isDark);
    }

    /** Re-renders the help content in the given theme; safe to call while open. */
    public void applyTheme(boolean isDark) {
        Color bg = isDark ? ModernTheme.BACKGROUND_DARK : Color.WHITE;
        content.setText(buildHtml(isDark));
        content.setBackground(bg);
        content.setCaretPosition(0);
        scroll.getViewport().setBackground(bg);
        getContentPane().setBackground(bg);
    }

    /** Re-applies theme and brings the dialog forward. */
    public void reopen(boolean isDark) {
        applyTheme(isDark);
        setVisible(true);
        toFront();
    }

    private static String hex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    private String buildHtml(boolean isDark) {
        String bg = hex(isDark ? ModernTheme.BACKGROUND_DARK : Color.WHITE);
        String fg = hex(isDark ? ModernTheme.FOREGROUND_PRIMARY : ModernTheme.LIGHT_FOREGROUND);
        String fgMuted = hex(isDark ? ModernTheme.FOREGROUND_SECONDARY : ModernTheme.LIGHT_FOREGROUND_SECONDARY);
        String accent = hex(ModernTheme.ACCENT_PRIMARY);
        String codeBg = hex(isDark ? ModernTheme.BACKGROUND_LIGHT : ModernTheme.LIGHT_BACKGROUND_DARKER);
        String warn = hex(isDark ? ModernTheme.WARNING : new Color(0x9a, 0x6a, 0x00));

        // JEditorPane speaks HTML 3.2: keep markup simple (font tags via CSS subset,
        // tables for layout, no flex/grid).
        return "<html><body style='background-color:" + bg + "; color:" + fg + ";"
            + " font-family:sans-serif; font-size:12px; margin:18px 22px;'>"

            + "<h2 style='color:" + accent + ";'>Python 3 Script Console</h2>"
            + "<p>This console runs <b>real Python 3</b> on the gateway (not Jython). Code executes in a"
            + " warm process pool, so runs are fast and cannot hang the Designer. Use the version dropdown"
            + " to target a specific installed Python; <i>(Default)</i> uses the gateway's default pool.</p>"

            + "<h3 style='color:" + accent + ";'>1. Write and test here</h3>"
            + "<p>Set a variable named <code>result</code> to return a value; anything printed appears in"
            + " the output pane below.</p>"
            + "<pre style='background-color:" + codeBg + "; padding:8px;'>"
            + "import statistics\n"
            + "readings = [4.1, 4.4, 3.9, 4.7]\n"
            + "result = statistics.mean(readings)</pre>"

            + "<h3 style='color:" + accent + ";'>2. Save it like a project library script</h3>"
            + "<p><b>Save</b> stores the script on the gateway; organise scripts into folders under the"
            + " <b>Python 3 Scripts</b> node in the Project Browser (right-click for new script/folder,"
            + " double-click to edit). Saved scripts are shared by every Designer on this gateway.</p>"

            + "<h3 style='color:" + accent + ";'>3. Call it from anywhere Jython runs</h3>"
            + "<p>Every saved script is callable via <code>system.python3.callScript(path, args, kwargs)</code>"
            + " from any Ignition scripting context — Perspective events, tag value-change scripts, gateway"
            + " timer/scheduled scripts, alarm pipelines, WebDev endpoints:</p>"
            + "<pre style='background-color:" + codeBg + "; padding:8px;'>"
            + "# Perspective button onActionPerformed (Jython):\n"
            + "summary = system.python3.callScript(\"Reports/DailySummary\")\n"
            + "self.getSibling(\"Label\").props.text = str(summary)\n"
            + "\n"
            + "# Tag change script:\n"
            + "flagged = system.python3.callScript(\"Quality/CheckLimits\",\n"
            + "                                    [currentValue.value])</pre>"
            + "<p>One-off snippets can use <code>system.python3.exec(code)</code> (reads the"
            + " <code>result</code> variable) or <code>system.python3.eval(expression)</code>. Both accept"
            + " an optional trailing version argument, e.g. <code>exec(code, vars, \"3.12\")</code>.</p>"
            + "<p style='color:" + fgMuted + ";'>Data types cross the bridge as JSON: numbers arrive in"
            + " Jython as floats/longs, plus strings, booleans, lists and dictionaries. Convert/round on"
            + " the Jython side as needed.</p>"

            + "<h3 style='color:" + warn + ";'>Security: never exec user input</h3>"
            + "<p>Treat <code>exec</code>/<code>eval</code> like SQL: <b>never</b> feed text from a"
            + " Perspective input component into them. Author a saved script and pass values as arguments"
            + " with <code>callScript</code> — arguments are data, never code.</p>"
            + "<pre style='background-color:" + codeBg + "; padding:8px;'>"
            + "# BAD  - user text becomes code:\n"
            + "system.python3.exec(self.props.text)\n"
            + "# GOOD - user text stays data:\n"
            + "system.python3.callScript(\"Math/Evaluate\", [self.props.text])</pre>"

            + "<h3 style='color:" + accent + ";'>Environment and diagnostics</h3>"
            + "<p>The <b>Diagnostics</b> button shows live pool statistics, execution timing, gateway"
            + " impact, module logs, and (read-only) the installed Python versions and packages."
            + " Installing packages and Python versions is an administrator task in the"
            + " <b>gateway web UI</b> (Config &gt; Python 3), not the Designer.</p>"

            + "<h3 style='color:" + accent + ";'>Keyboard shortcuts</h3>"
            + "<table cellpadding='3' style='color:" + fg + ";'>"
            + "<tr><td><b>Ctrl+Enter</b></td><td>Run</td>"
            + "<td width='30'></td><td><b>Ctrl+Space</b></td><td>Autocomplete (Jedi)</td></tr>"
            + "<tr><td><b>Ctrl+S</b></td><td>Save script</td>"
            + "<td></td><td><b>Ctrl+F</b></td><td>Find / replace</td></tr>"
            + "<tr><td><b>Ctrl+O</b></td><td>Load script</td>"
            + "<td></td><td><b>Ctrl+L</b></td><td>Clear output</td></tr>"
            + "</table>"

            + "<p style='color:" + fgMuted + ";'>Full guides (integration, security, operations) ship in"
            + " the module's <code>docs/</code> folder — start with"
            + " <code>getting-started/INTEGRATION_GUIDE.md</code>.</p>"

            + "</body></html>";
    }

    /** Test seam: current HTML (theme-dependent). */
    String currentHtml() {
        return content.getText();
    }
}
