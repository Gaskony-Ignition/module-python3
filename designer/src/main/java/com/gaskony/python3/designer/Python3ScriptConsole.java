package com.gaskony.python3.designer;

import com.inductiveautomation.ignition.designer.model.DesignerContext;
import com.gaskony.python3.designer.managers.ThemeManager;
import com.gaskony.python3.designer.ui.FindReplaceDialog;
import org.fife.ui.autocomplete.AutoCompletion;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import javax.swing.event.CaretEvent;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;

/**
 * Python 3 Script Console for the Ignition Designer.
 * Redesigned to match the Gateway Web UI appearance with a single combined
 * output panel (no separate error tab), prominent Run button, and dark theme.
 *
 * @since v3.3.0
 * @revised v3.5.0 - Redesigned to match Web GUI, merged output/error panels
 */
public class Python3ScriptConsole extends JPanel {
    private static final Logger logger = LoggerFactory.getLogger(Python3ScriptConsole.class);

    private static final String PREF_THEME = PreferenceKeys.CONSOLE_THEME;
    private static final String PREF_SPLIT_ORIENTATION = PreferenceKeys.CONSOLE_SPLIT_ORIENTATION;

    private final Python3RestClient restClient;
    private final ThemeManager themeManager;
    private final Preferences prefs;

    private RSyntaxTextArea codeEditor;
    private JTextPane outputPane;
    private JSplitPane splitPane;
    private ModernStatusBar statusBar;
    private JComboBox<String> versionCombo;
    private JButton runButton;

    // Toolbar references for theme updates
    private JPanel toolbarPanel;
    private final java.util.List<JButton> toolbarButtons = new java.util.ArrayList<>();
    private final java.util.List<JLabel> separatorLabels = new java.util.ArrayList<>();
    private JPanel outputHeaderPanel;
    private JLabel outputHeaderLabel;

    // Script tracking
    private String loadedScriptName;
    private JLabel scriptNameLabel;
    private JPanel scriptNameBar;

    // Diagnostics dialog (v4.3.0, charter workflows 4/5) — lazily created, reused
    private DiagnosticsDialog diagnosticsDialog;
    private FindReplaceDialog findReplaceDialog;
    private HelpDialog helpDialog;
    private int runCounter;

    // Theme-aware output colors (updated when theme changes)
    private Color outputFgPrimary = ModernTheme.FOREGROUND_PRIMARY;
    private Color outputFgSecondary = ModernTheme.FOREGROUND_SECONDARY;
    private Color outputSuccessColor = ModernTheme.SUCCESS;
    private Color outputErrorColor = ModernTheme.ERROR;

    /**
     * Creates a new Python 3 Script Console.
     *
     * @param context the Designer context
     */
    public Python3ScriptConsole(DesignerContext context) {
        setLayout(new BorderLayout());
        setBackground(ModernTheme.BACKGROUND_DARK);
        this.prefs = Preferences.userNodeForPackage(Python3ScriptConsole.class);
        this.restClient = new Python3RestClient(context);
        this.themeManager = new ThemeManager(Python3ScriptConsole.class);

        // Top section: toolbar + optional script name bar
        JPanel topSection = new JPanel(new BorderLayout());
        topSection.setOpaque(false);
        topSection.add(createToolbar(), BorderLayout.NORTH);
        topSection.add(createScriptNameBar(), BorderLayout.SOUTH);
        add(topSection, BorderLayout.NORTH);

        // Code editor
        JPanel editorPanel = createEditorPanel();

        // Output panel (single combined panel, no tabs)
        JPanel outputPanel = createOutputPanel();

        // Split pane (respect saved orientation)
        int savedOrientation = prefs.getInt(PREF_SPLIT_ORIENTATION, JSplitPane.VERTICAL_SPLIT);
        splitPane = new JSplitPane(savedOrientation, editorPanel, outputPanel);
        splitPane.setResizeWeight(0.65);
        splitPane.setDividerSize(3);
        splitPane.setBorder(BorderFactory.createEmptyBorder());
        splitPane.setBackground(ModernTheme.BORDER_SUBTLE);
        add(splitPane, BorderLayout.CENTER);

        // v4.4.0: on first showing, place the divider at 65/35 explicitly. Without
        // this the divider sits wherever the children's preferred sizes put it —
        // in practice the output pane opened only a few lines tall and the first
        // run's output was cut off until the user dragged the divider.
        splitPane.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0
                    && splitPane.isShowing()) {
                SwingUtilities.invokeLater(() -> splitPane.setDividerLocation(0.65));
            }
        });

        // Status bar
        statusBar = new ModernStatusBar();
        add(statusBar, BorderLayout.SOUTH);

        // Apply saved theme
        applyCurrentTheme();

        // Populate version combo in background
        populateVersionCombo();

        // Update status bar in background
        updateStatusBarAsync();

        // Setup keyboard shortcuts
        setupKeyboardShortcuts();

        // Caret listener for cursor position
        codeEditor.addCaretListener(this::updateCursorPosition);

        logger.info("Python 3 Script Console initialized");
    }

    /**
     * Loads a named script into the editor. Called from Project Browser nav tree.
     *
     * @param scriptName the name of the script to load
     */
    public void openScript(String scriptName) {
        if (scriptName == null || scriptName.isEmpty()) {
            return;
        }
        loadScriptByName(scriptName);
    }

    // =========================================================================
    // Toolbar - matches Web GUI layout: Run | Version | spacer | Load | Save | Save As | Clear
    // =========================================================================

    private JPanel createToolbar() {
        toolbarPanel = new JPanel(new BorderLayout());
        toolbarPanel.setBackground(ModernTheme.BACKGROUND_DARKER);
        toolbarPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, ModernTheme.BORDER_SUBTLE),
                new EmptyBorder(8, 12, 8, 12)
        ));

        // Left section: Run button + version selector
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        leftPanel.setOpaque(false);

        runButton = createRunButton();
        leftPanel.add(runButton);

        // Separator
        JLabel sep1 = new JLabel("|");
        sep1.setForeground(ModernTheme.BORDER_DEFAULT);
        sep1.setBorder(new EmptyBorder(0, 4, 0, 4));
        separatorLabels.add(sep1);
        leftPanel.add(sep1);

        versionCombo = new JComboBox<>();
        versionCombo.addItem("(Default)");
        versionCombo.setPreferredSize(new Dimension(120, 28));
        versionCombo.setBackground(ModernTheme.BACKGROUND_DARKER);
        versionCombo.setForeground(ModernTheme.FOREGROUND_PRIMARY);
        versionCombo.setToolTipText("Select Python version");
        leftPanel.add(versionCombo);

        toolbarPanel.add(leftPanel, BorderLayout.WEST);

        // Right section: Load Script, Save, Save As, Clear
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        rightPanel.setOpaque(false);

        JButton loadButton = createToolbarButton("Load Script");
        loadButton.setToolTipText("Load a saved script (Ctrl+O)");
        loadButton.addActionListener(e -> loadScript());
        toolbarButtons.add(loadButton);
        rightPanel.add(loadButton);

        JButton saveButton = createToolbarButton("Save");
        saveButton.setToolTipText("Save current script (Ctrl+S)");
        saveButton.addActionListener(e -> saveScript());
        toolbarButtons.add(saveButton);
        rightPanel.add(saveButton);

        JButton saveAsButton = createToolbarButton("Save As");
        saveAsButton.setToolTipText("Save as new script");
        saveAsButton.addActionListener(e -> saveScriptAs());
        toolbarButtons.add(saveAsButton);
        rightPanel.add(saveAsButton);

        JButton clearButton = createToolbarButton("Clear");
        clearButton.setToolTipText("Clear output (Ctrl+L)");
        clearButton.addActionListener(e -> clearOutput());
        toolbarButtons.add(clearButton);
        rightPanel.add(clearButton);

        JButton diagnosticsButton = createToolbarButton("Diagnostics");
        diagnosticsButton.setToolTipText("Pool stats, gateway impact, logs and Python environment (read-only)");
        diagnosticsButton.addActionListener(e -> openDiagnostics());
        toolbarButtons.add(diagnosticsButton);
        rightPanel.add(diagnosticsButton);

        JButton splitButton = createToolbarButton("Split");
        splitButton.setToolTipText("Toggle split orientation (horizontal/vertical)");
        splitButton.addActionListener(e -> toggleSplitOrientation());
        toolbarButtons.add(splitButton);
        rightPanel.add(splitButton);

        JButton helpButton = createToolbarButton("Help");
        helpButton.setToolTipText("How to use the console and call your scripts from Perspective, tags and gateway events");
        helpButton.addActionListener(e -> openHelp());
        toolbarButtons.add(helpButton);
        rightPanel.add(helpButton);

        // Separator before theme toggle
        JLabel sep2 = new JLabel("|");
        sep2.setForeground(ModernTheme.BORDER_DEFAULT);
        sep2.setBorder(new EmptyBorder(0, 4, 0, 4));
        separatorLabels.add(sep2);
        rightPanel.add(sep2);

        JButton themeButton = createToolbarButton("Theme");
        themeButton.setToolTipText("Toggle light/dark theme");
        themeButton.addActionListener(e -> toggleTheme());
        toolbarButtons.add(themeButton);
        rightPanel.add(themeButton);

        toolbarPanel.add(rightPanel, BorderLayout.EAST);

        return toolbarPanel;
    }

    // =========================================================================
    // Script name bar (visible only when a script is loaded)
    // =========================================================================

    private JPanel createScriptNameBar() {
        scriptNameBar = new JPanel(new BorderLayout());
        scriptNameBar.setBackground(ModernTheme.BACKGROUND_LIGHT);
        scriptNameBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, ModernTheme.BORDER_SUBTLE),
                new EmptyBorder(4, 14, 4, 14)
        ));

        scriptNameLabel = new JLabel("");
        scriptNameLabel.setFont(ModernTheme.FONT_REGULAR);
        scriptNameLabel.setForeground(ModernTheme.FOREGROUND_SECONDARY);
        scriptNameBar.add(scriptNameLabel, BorderLayout.WEST);

        // Initially hidden
        scriptNameBar.setVisible(false);

        return scriptNameBar;
    }

    private void updateScriptNameBar(String name) {
        if (name != null && !name.isEmpty()) {
            loadedScriptName = name;
            scriptNameLabel.setText("Script: " + name);
            scriptNameBar.setVisible(true);
        } else {
            loadedScriptName = null;
            scriptNameLabel.setText("");
            scriptNameBar.setVisible(false);
        }
    }

    // =========================================================================
    // Editor panel
    // =========================================================================

    private JPanel createEditorPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(ModernTheme.BACKGROUND_DARK);

        codeEditor = new RSyntaxTextArea(20, 80);
        codeEditor.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_PYTHON);
        codeEditor.setCodeFoldingEnabled(true);
        codeEditor.setAntiAliasingEnabled(true);
        codeEditor.setTabSize(4);
        codeEditor.setTabsEmulated(true);
        codeEditor.setAutoIndentEnabled(true);
        codeEditor.setBracketMatchingEnabled(true);
        codeEditor.setAnimateBracketMatching(true);
        codeEditor.setFont(ModernTheme.FONT_MONOSPACE);
        codeEditor.setText("# Python 3 Script Console\n# Press Ctrl+Enter to run\n\nprint('Hello, World!')\n");

        // Attach syntax checker
        PythonSyntaxChecker syntaxChecker = new PythonSyntaxChecker(codeEditor, restClient);
        codeEditor.addParser(syntaxChecker);

        // Setup autocomplete
        try {
            Python3CompletionProvider completionProvider = new Python3CompletionProvider(restClient);
            AutoCompletion autoCompletion = new AutoCompletion(completionProvider);
            // Explicit Ctrl+Space only (charter §4 quality bar). The provider is
            // invoked synchronously on the EDT, so firing it on every typing
            // pause would stutter the editor now that completions actually
            // reach Jedi over RPC.
            autoCompletion.setAutoActivationEnabled(false);
            autoCompletion.setShowDescWindow(true);
            autoCompletion.install(codeEditor);
        } catch (Exception e) {
            logger.warn("Failed to setup autocomplete: {}", e.getMessage());
        }

        RTextScrollPane editorScrollPane = new RTextScrollPane(codeEditor);
        editorScrollPane.setFoldIndicatorEnabled(true);
        editorScrollPane.setLineNumbersEnabled(true);
        editorScrollPane.setBorder(BorderFactory.createEmptyBorder());
        editorScrollPane.getGutter().setBackground(ModernTheme.BACKGROUND_DARKER);

        // Invisible scrollbars - users scroll with mouse wheel / trackpad
        editorScrollPane.getVerticalScrollBar().setPreferredSize(new Dimension(0, 0));
        editorScrollPane.getHorizontalScrollBar().setPreferredSize(new Dimension(0, 0));
        editorScrollPane.getVerticalScrollBar().setUnitIncrement(16);

        panel.add(editorScrollPane, BorderLayout.CENTER);
        return panel;
    }

    // =========================================================================
    // Output panel - single combined panel (no tabs)
    // =========================================================================

    private JPanel createOutputPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(ModernTheme.BACKGROUND_DARKER);

        // Output header with "Output" label
        outputHeaderPanel = new JPanel(new BorderLayout());
        outputHeaderPanel.setBackground(ModernTheme.BACKGROUND_DARKER);
        outputHeaderPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, ModernTheme.BORDER_SUBTLE),
                new EmptyBorder(6, 14, 6, 14)
        ));

        outputHeaderLabel = new JLabel("Output");
        outputHeaderLabel.setFont(ModernTheme.FONT_BOLD);
        outputHeaderLabel.setForeground(ModernTheme.FOREGROUND_SECONDARY);
        outputHeaderPanel.add(outputHeaderLabel, BorderLayout.WEST);

        panel.add(outputHeaderPanel, BorderLayout.NORTH);

        // Single styled output pane (supports colored text for errors)
        outputPane = new JTextPane();
        outputPane.setEditable(false);
        outputPane.setFont(ModernTheme.FONT_MONOSPACE);
        outputPane.setBackground(ModernTheme.BACKGROUND_DARKER);
        outputPane.setForeground(ModernTheme.FOREGROUND_PRIMARY);
        outputPane.setCaretColor(ModernTheme.FOREGROUND_PRIMARY);
        outputPane.setBorder(new EmptyBorder(10, 14, 10, 14));

        // Set default text style (uses instance field so theme changes are reflected)
        StyledDocument doc = outputPane.getStyledDocument();
        SimpleAttributeSet defaultStyle = new SimpleAttributeSet();
        StyleConstants.setForeground(defaultStyle, outputFgSecondary);
        StyleConstants.setFontFamily(defaultStyle, ModernTheme.FONT_CODE.getFamily());
        StyleConstants.setFontSize(defaultStyle, 14);
        try {
            doc.insertString(0, "Run a script to see output here. (Ctrl+Enter)", defaultStyle);
        } catch (BadLocationException ignored) {
        }

        JScrollPane outputScroll = new JScrollPane(outputPane,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        outputScroll.setBorder(BorderFactory.createEmptyBorder());
        outputScroll.getViewport().setBackground(ModernTheme.BACKGROUND_DARKER);

        // Invisible scrollbars - users scroll with mouse wheel / trackpad
        outputScroll.getVerticalScrollBar().setPreferredSize(new Dimension(0, 0));
        outputScroll.getVerticalScrollBar().setUnitIncrement(16);

        panel.add(outputScroll, BorderLayout.CENTER);
        return panel;
    }

    // =========================================================================
    // Actions
    // =========================================================================

    private void executeCode() {
        String code = codeEditor.getText();
        if (code == null || code.trim().isEmpty()) {
            statusBar.setStatus("Nothing to execute", ModernStatusBar.MessageType.WARNING);
            return;
        }

        // Get selected version (null if default)
        String selectedVersion = null;
        if (versionCombo.getSelectedIndex() > 0) {
            selectedVersion = (String) versionCombo.getSelectedItem();
        }

        statusBar.setStatus("Executing...", ModernStatusBar.MessageType.INFO);
        runButton.setEnabled(false);
        ensureOutputVisible();

        final String version = selectedVersion;
        new SwingWorker<ExecutionResult, Void>() {
            @Override
            protected ExecutionResult doInBackground() throws Exception {
                return restClient.executeCode(code, new HashMap<>(), version);
            }

            @Override
            protected void done() {
                runButton.setEnabled(true);
                // v4.4.0: prepend each run as a dated block at the top instead of
                // wiping the pane, so the latest result is immediately visible and
                // earlier runs remain scrollable below.
                List<OutputSegment> block = new ArrayList<>();
                String header = "▶ run #" + (++runCounter) + "  ·  "
                        + java.time.LocalTime.now().withNano(0);
                try {
                    ExecutionResult result = get();
                    long timeMs = result.getExecutionTimeMs() != null ? result.getExecutionTimeMs() : 0;

                    if (result.isSuccess()) {
                        block.add(new OutputSegment(header + "  ·  completed in "
                                + timeMs + "ms\n", outputSuccessColor));
                        String output = result.getResult() != null ? result.getResult() : "";
                        block.add(output.isEmpty()
                                ? new OutputSegment("(no output)\n", outputFgSecondary)
                                : new OutputSegment(output + "\n", outputFgPrimary));
                        statusBar.setStatus("Executed in " + timeMs + "ms",
                                ModernStatusBar.MessageType.SUCCESS);
                    } else {
                        block.add(new OutputSegment(header + "  ·  failed\n", outputErrorColor));
                        String output = result.getResult();
                        if (output != null && !output.isEmpty()) {
                            block.add(new OutputSegment(output + "\n", outputFgPrimary));
                        }
                        String error = result.getError() != null ? result.getError() : "Execution failed";
                        block.add(new OutputSegment(error + "\n", outputErrorColor));
                        statusBar.setStatus("Execution failed",
                                ModernStatusBar.MessageType.ERROR);
                    }
                } catch (Exception ex) {
                    block.add(new OutputSegment(header + "  ·  error\n", outputErrorColor));
                    block.add(new OutputSegment("Error: " + ex.getMessage() + "\n", outputErrorColor));
                    statusBar.setStatus("Execution error",
                            ModernStatusBar.MessageType.ERROR);
                }
                prependBlock(block);
            }
        }.execute();
    }

    /** A run of styled text destined for the output pane. */
    private static final class OutputSegment {
        final String text;
        final Color color;
        OutputSegment(String text, Color color) {
            this.text = text;
            this.color = color;
        }
    }

    /**
     * Inserts a run's output as a block at the TOP of the output pane, above any
     * previous runs, and scrolls to it (v4.4.0). Segments are inserted at an
     * advancing offset from 0 so they read top-to-bottom within the block; a thin
     * divider separates this block from older output.
     */
    private void prependBlock(List<OutputSegment> segments) {
        StyledDocument doc = outputPane.getStyledDocument();
        int offset = 0;
        try {
            if (doc.getLength() > 0) {
                offset += insertStyled(doc, offset,
                        "────────\n", outputFgSecondary);
            }
            for (OutputSegment seg : segments) {
                offset += insertStyled(doc, offset, seg.text, seg.color);
            }
        } catch (BadLocationException ignored) {
        }
        outputPane.setCaretPosition(0);
    }

    private int insertStyled(StyledDocument doc, int offset, String text, Color color)
            throws BadLocationException {
        SimpleAttributeSet attrs = new SimpleAttributeSet();
        StyleConstants.setForeground(attrs, color);
        StyleConstants.setFontFamily(attrs, ModernTheme.FONT_CODE.getFamily());
        StyleConstants.setFontSize(attrs, 14);
        doc.insertString(offset, text, attrs);
        return text.length();
    }

    private void clearOutput() {
        clearOutputPane();
        runCounter = 0;
        appendToOutput("Output cleared.", outputFgSecondary);
        statusBar.setStatus("Output cleared", ModernStatusBar.MessageType.INFO);
    }

    /**
     * If the output side of the split has been squeezed too small to read
     * results, restore the 65/35 split. Runs when an execution completes so
     * fresh output is never invisible (v4.4.0); leaves the divider alone when
     * the user has already given the output pane reasonable space.
     */
    private void ensureOutputVisible() {
        if (splitPane == null) {
            return;
        }
        boolean vertical = splitPane.getOrientation() == JSplitPane.VERTICAL_SPLIT;
        int total = vertical ? splitPane.getHeight() : splitPane.getWidth();
        if (total <= 0) {
            return;
        }
        int outputSpan = total - splitPane.getDividerLocation() - splitPane.getDividerSize();
        int minimumSpan = Math.max(120, (int) (total * 0.20));
        if (outputSpan < minimumSpan) {
            splitPane.setDividerLocation(0.65);
        }
    }

    private void clearOutputPane() {
        outputPane.setText("");
    }

    private void appendToOutput(String text, Color color) {
        StyledDocument doc = outputPane.getStyledDocument();
        SimpleAttributeSet attrs = new SimpleAttributeSet();
        StyleConstants.setForeground(attrs, color);
        StyleConstants.setFontFamily(attrs, ModernTheme.FONT_CODE.getFamily());
        StyleConstants.setFontSize(attrs, 14);
        try {
            doc.insertString(doc.getLength(), text, attrs);
        } catch (BadLocationException ignored) {
        }
        // Auto-scroll to bottom
        outputPane.setCaretPosition(doc.getLength());
    }

    private void loadScript() {
        new SwingWorker<List<ScriptMetadata>, Void>() {
            @Override
            protected List<ScriptMetadata> doInBackground() throws Exception {
                return restClient.listScripts();
            }

            @Override
            protected void done() {
                try {
                    List<ScriptMetadata> scripts = get();
                    if (scripts.isEmpty()) {
                        DarkDialog.showMessage(Python3ScriptConsole.this,
                                "No saved scripts found.", "Load Script");
                        return;
                    }

                    // Build list of script names
                    String[] scriptNames = scripts.stream()
                            .map(ScriptMetadata::getName)
                            .toArray(String[]::new);

                    String selected = (String) javax.swing.JOptionPane.showInputDialog(
                            Python3ScriptConsole.this,
                            "Select a script to load:",
                            "Load Script",
                            javax.swing.JOptionPane.PLAIN_MESSAGE,
                            null,
                            scriptNames,
                            scriptNames[0]
                    );

                    if (selected != null) {
                        loadScriptByName(selected);
                    }
                } catch (Exception ex) {
                    logger.error("Failed to list scripts", ex);
                    DarkDialog.showMessage(Python3ScriptConsole.this,
                            "Failed to load script list: " + ex.getMessage(), "Error");
                }
            }
        }.execute();
    }

    private void loadScriptByName(String name) {
        new SwingWorker<SavedScript, Void>() {
            @Override
            protected SavedScript doInBackground() throws Exception {
                return restClient.loadScript(name);
            }

            @Override
            protected void done() {
                try {
                    SavedScript script = get();
                    codeEditor.setText(script.getCode());
                    codeEditor.setCaretPosition(0);
                    updateScriptNameBar(script.getName());
                    statusBar.setStatus("Loaded: " + script.getName(),
                            ModernStatusBar.MessageType.SUCCESS);
                } catch (Exception ex) {
                    logger.error("Failed to load script: {}", name, ex);
                    DarkDialog.showMessage(Python3ScriptConsole.this,
                            "Failed to load script: " + ex.getMessage(), "Error");
                }
            }
        }.execute();
    }

    /**
     * Save: if a script is loaded, auto-save to that name. Otherwise fall through to Save As.
     */
    private void saveScript() {
        if (loadedScriptName != null && !loadedScriptName.isEmpty()) {
            // Auto-save to existing name
            String code = codeEditor.getText();
            statusBar.setStatus("Saving...", ModernStatusBar.MessageType.INFO);

            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() throws Exception {
                    restClient.saveScript(loadedScriptName, code, "");
                    return null;
                }

                @Override
                protected void done() {
                    try {
                        get();
                        statusBar.setStatus("Saved: " + loadedScriptName,
                                ModernStatusBar.MessageType.SUCCESS);
                    } catch (Exception ex) {
                        logger.error("Failed to save script", ex);
                        statusBar.setStatus("Save failed",
                                ModernStatusBar.MessageType.ERROR);
                    }
                }
            }.execute();
        } else {
            // No script loaded, fall through to Save As
            saveScriptAs();
        }
    }

    /**
     * Save As: always prompts for a new script name.
     */
    private void saveScriptAs() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("Name", "");
        fields.put("Description", "");

        Map<String, String> result = DarkDialog.showMultiInput(this, "Save Script As", fields);
        if (result == null) {
            return;
        }

        String name = result.get("Name");
        String description = result.get("Description");

        if (name == null || name.trim().isEmpty()) {
            DarkDialog.showMessage(this, "Script name is required.", "Save Script");
            return;
        }

        String code = codeEditor.getText();
        statusBar.setStatus("Saving...", ModernStatusBar.MessageType.INFO);

        final String trimmedName = name.trim();
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                restClient.saveScript(trimmedName, code, description);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    updateScriptNameBar(trimmedName);
                    statusBar.setStatus("Saved: " + trimmedName,
                            ModernStatusBar.MessageType.SUCCESS);
                } catch (Exception ex) {
                    logger.error("Failed to save script", ex);
                    statusBar.setStatus("Save failed",
                            ModernStatusBar.MessageType.ERROR);
                    DarkDialog.showMessage(Python3ScriptConsole.this,
                            "Failed to save script: " + ex.getMessage(), "Error");
                }
            }
        }.execute();
    }

    // =========================================================================
    // Diagnostics (v4.3.0 — charter workflows 4/5)
    // =========================================================================

    /**
     * Opens the read-only diagnostics/environment dialog. Lazily created and
     * reused; re-themed and refreshed on every reopen so it tracks the
     * console's current light/dark state.
     */
    private void openDiagnostics() {
        boolean isDark = !"default".equals(themeManager.getSavedThemePreference());
        if (diagnosticsDialog == null) {
            diagnosticsDialog = new DiagnosticsDialog(
                    SwingUtilities.getWindowAncestor(this), restClient, isDark);
            diagnosticsDialog.setVisible(true);
        } else {
            diagnosticsDialog.reopen(isDark);
        }
    }

    /** Opens (lazily creating) the in-app help dialog (v4.4.0). */
    private void openHelp() {
        boolean isDark = !"default".equals(themeManager.getSavedThemePreference());
        if (helpDialog == null) {
            helpDialog = new HelpDialog(SwingUtilities.getWindowAncestor(this), isDark);
            helpDialog.setVisible(true);
        } else {
            helpDialog.reopen(isDark);
        }
    }

    // =========================================================================
    // Theme management
    // =========================================================================

    private void applyCurrentTheme() {
        String savedTheme = themeManager.getSavedThemePreference();
        applyThemeByName(savedTheme);
    }

    private void applyThemeByName(String themeName) {
        boolean isDark = !"default".equals(themeName);

        // Step 1: Try to apply RSTA syntax theme to code editor (non-fatal if it fails)
        try {
            themeManager.applyTheme(themeName, this, codeEditor, null, null, null);
        } catch (Exception e) {
            logger.warn("Failed to apply RSTA syntax theme '{}' (non-fatal, console colors still applied): {}",
                    themeName, e.getMessage());
        }

        // Step 2: Always apply console visual theme regardless of RSTA theme success
        DarkDialog.setDarkTheme(isDark);

        // Keep open Diagnostics/Help dialogs in step with the console theme (v4.3.1/v4.4.0)
        if (diagnosticsDialog != null) {
            diagnosticsDialog.applyTheme(isDark);
        }
        if (helpDialog != null) {
            helpDialog.applyTheme(isDark);
        }

        // Theme colors for dark vs light
        Color bg = isDark ? ModernTheme.BACKGROUND_DARK : Color.WHITE;
        Color bgDarker = isDark ? ModernTheme.BACKGROUND_DARKER : ModernTheme.LIGHT_BACKGROUND_DARKER;
        Color bgLight = isDark ? ModernTheme.BACKGROUND_LIGHT : ModernTheme.LIGHT_BACKGROUND_PANEL;
        Color fgPrimary = isDark ? ModernTheme.FOREGROUND_PRIMARY : ModernTheme.LIGHT_FOREGROUND;
        Color fgSecondary = isDark ? ModernTheme.FOREGROUND_SECONDARY : ModernTheme.LIGHT_FOREGROUND_SECONDARY;
        Color borderSubtle = isDark ? ModernTheme.BORDER_SUBTLE : ModernTheme.LIGHT_BORDER_SUBTLE;
        Color borderDefault = isDark ? ModernTheme.BORDER_DEFAULT : ModernTheme.LIGHT_BORDER;

        // Update theme-aware output colors so appendToOutput uses readable colors
        outputFgPrimary = fgPrimary;
        outputFgSecondary = fgSecondary;
        outputSuccessColor = isDark ? ModernTheme.SUCCESS : ModernTheme.SUCCESS_LIGHT;
        outputErrorColor = isDark ? ModernTheme.ERROR : ModernTheme.ERROR_LIGHT;

        // Root panel
        setBackground(bg);

        // Toolbar
        if (toolbarPanel != null) {
            toolbarPanel.setBackground(bgDarker);
            toolbarPanel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, borderSubtle),
                    new EmptyBorder(8, 12, 8, 12)
            ));
        }

        // Toolbar buttons - update foreground and background for custom paint
        Color buttonBg = isDark ? ModernTheme.BUTTON_BACKGROUND : ModernTheme.LIGHT_BUTTON_BG;
        for (JButton btn : toolbarButtons) {
            btn.setForeground(fgPrimary);
            btn.setBackground(buttonBg);
            btn.repaint();
        }

        // Separator labels
        for (JLabel sep : separatorLabels) {
            sep.setForeground(borderDefault);
        }

        // Version combo
        if (versionCombo != null) {
            versionCombo.setBackground(bgDarker);
            versionCombo.setForeground(fgPrimary);
        }

        // Code editor backgrounds (in case RSTA theme didn't load)
        if (codeEditor != null) {
            if (isDark) {
                codeEditor.setBackground(ModernTheme.EDITOR_BACKGROUND);
                codeEditor.setForeground(ModernTheme.FOREGROUND_PRIMARY);
                codeEditor.setCaretColor(ModernTheme.FOREGROUND_PRIMARY);
                codeEditor.setCurrentLineHighlightColor(ModernTheme.EDITOR_LINE_HIGHLIGHT);
            } else {
                codeEditor.setBackground(ModernTheme.LIGHT_BACKGROUND);
                codeEditor.setForeground(ModernTheme.LIGHT_FOREGROUND);
                codeEditor.setCaretColor(ModernTheme.LIGHT_FOREGROUND);
                codeEditor.setCurrentLineHighlightColor(ModernTheme.LIGHT_EDITOR_LINE_HIGHLIGHT);
            }
        }

        // Output pane - update background and re-color existing styled text
        if (outputPane != null) {
            outputPane.setBackground(bgDarker);
            outputPane.setForeground(fgPrimary);
            outputPane.setCaretColor(fgPrimary);

            // Update scroll pane viewport background
            java.awt.Container outputParent = outputPane.getParent();
            if (outputParent instanceof javax.swing.JViewport) {
                outputParent.setBackground(bgDarker);
            }

            // Re-color all existing text in the styled document to match new theme
            StyledDocument doc = outputPane.getStyledDocument();
            if (doc.getLength() > 0) {
                SimpleAttributeSet recolorAttrs = new SimpleAttributeSet();
                StyleConstants.setForeground(recolorAttrs, fgSecondary);
                doc.setCharacterAttributes(0, doc.getLength(), recolorAttrs, false);
            }
        }

        // Output header
        if (outputHeaderPanel != null) {
            outputHeaderPanel.setBackground(bgDarker);
            outputHeaderPanel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, borderSubtle),
                    new EmptyBorder(6, 14, 6, 14)
            ));
        }
        if (outputHeaderLabel != null) {
            outputHeaderLabel.setForeground(fgSecondary);
        }

        // Script name bar
        if (scriptNameBar != null) {
            scriptNameBar.setBackground(bgLight);
            scriptNameBar.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, borderSubtle),
                    new EmptyBorder(4, 14, 4, 14)
            ));
        }
        if (scriptNameLabel != null) {
            scriptNameLabel.setForeground(fgSecondary);
        }

        // Split pane - update both background and the divider component
        if (splitPane != null) {
            splitPane.setBackground(borderSubtle);
            if (splitPane.getUI() instanceof javax.swing.plaf.basic.BasicSplitPaneUI) {
                Color dividerColor = isDark ? ModernTheme.BORDER_DEFAULT : ModernTheme.LIGHT_BORDER;
                ((javax.swing.plaf.basic.BasicSplitPaneUI) splitPane.getUI())
                        .getDivider().setBackground(dividerColor);
            }
        }

        // Status bar
        if (statusBar != null) {
            statusBar.updateTheme(isDark);
        }

        // Editor gutter
        java.awt.Container editorParent = codeEditor != null ? codeEditor.getParent() : null;
        while (editorParent != null) {
            if (editorParent instanceof org.fife.ui.rtextarea.RTextScrollPane) {
                org.fife.ui.rtextarea.RTextScrollPane rsp = (org.fife.ui.rtextarea.RTextScrollPane) editorParent;
                rsp.getGutter().setBackground(isDark ? ModernTheme.BACKGROUND_DARKER : ModernTheme.LIGHT_EDITOR_GUTTER_BG);
                break;
            }
            editorParent = editorParent.getParent();
        }

        // Apply theme to the parent JFrame if we're embedded in one
        java.awt.Window window = javax.swing.SwingUtilities.getWindowAncestor(this);
        if (window instanceof JFrame) {
            JFrame frame = (JFrame) window;
            frame.setBackground(bg);
            frame.getRootPane().setBackground(bg);
            frame.getRootPane().setOpaque(true);
            frame.getLayeredPane().setBackground(bg);
            frame.getContentPane().setBackground(bg);
        }

        // Force full repaint
        revalidate();
        repaint();

        statusBar.setStatus(isDark ? "Theme: Dark" : "Theme: Light", ModernStatusBar.MessageType.INFO);
        logger.info("Applied console theme: {} (isDark={})", themeName, isDark);
    }

    private void toggleTheme() {
        String current = themeManager.getCurrentTheme();
        String newTheme = "default".equals(current) ? "dark" : "default";
        logger.info("Toggling theme from '{}' to '{}'", current, newTheme);
        applyThemeByName(newTheme);
    }

    /**
     * Gets the REST client for external use.
     */
    public Python3RestClient getRestClient() {
        return restClient;
    }

    // =========================================================================
    // Split orientation
    // =========================================================================

    private void toggleSplitOrientation() {
        int current = splitPane.getOrientation();
        int newOrientation = (current == JSplitPane.VERTICAL_SPLIT)
                ? JSplitPane.HORIZONTAL_SPLIT
                : JSplitPane.VERTICAL_SPLIT;
        splitPane.setOrientation(newOrientation);
        splitPane.setResizeWeight(0.65);
        splitPane.setDividerLocation(0.65);
        splitPane.revalidate();
        splitPane.repaint();
        prefs.putInt(PREF_SPLIT_ORIENTATION, newOrientation);

        String label = (newOrientation == JSplitPane.VERTICAL_SPLIT)
                ? "top/bottom" : "left/right";
        statusBar.setStatus("Split: " + label, ModernStatusBar.MessageType.INFO);
    }

    // =========================================================================
    // Version combo population
    // =========================================================================

    private void populateVersionCombo() {
        new SwingWorker<List<String>, Void>() {
            @Override
            protected List<String> doInBackground() throws Exception {
                return restClient.getAvailableVersions();
            }

            @Override
            protected void done() {
                try {
                    List<String> versions = get();
                    for (String version : versions) {
                        versionCombo.addItem(version);
                    }
                } catch (Exception ex) {
                    logger.warn("Failed to populate version combo: {}", ex.getMessage());
                }
            }
        }.execute();
    }

    // =========================================================================
    // Status bar updates
    // =========================================================================

    private void updateStatusBarAsync() {
        new SwingWorker<Void, Void>() {
            private String pythonVersion;
            private PoolStats poolStats;
            private boolean healthy;

            @Override
            protected Void doInBackground() throws Exception {
                try {
                    pythonVersion = restClient.getPythonVersion();
                } catch (Exception e) {
                    pythonVersion = "Unknown";
                }
                try {
                    poolStats = restClient.getPoolStats();
                } catch (Exception e) {
                    poolStats = null;
                }
                try {
                    healthy = restClient.isHealthy();
                } catch (Exception e) {
                    healthy = false;
                }
                return null;
            }

            @Override
            protected void done() {
                statusBar.setPythonVersion("Python " + pythonVersion);
                if (poolStats != null) {
                    statusBar.updatePoolStats(poolStats);
                }
                if (healthy) {
                    statusBar.setConnection("Connected", ModernTheme.SUCCESS);
                } else {
                    statusBar.setConnection("Disconnected", ModernTheme.ERROR);
                }
                statusBar.setStatus("Ready", ModernStatusBar.MessageType.INFO);
            }
        }.execute();
    }

    private void updateCursorPosition(CaretEvent e) {
        try {
            int caretPos = codeEditor.getCaretPosition();
            int line = codeEditor.getLineOfOffset(caretPos) + 1;
            int col = caretPos - codeEditor.getLineStartOffset(line - 1) + 1;
            statusBar.setCursorPosition(line, col);
        } catch (Exception ex) {
            // Ignore bad location
        }
    }

    // =========================================================================
    // Keyboard shortcuts
    // =========================================================================

    private void setupKeyboardShortcuts() {
        InputMap inputMap = getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = getActionMap();

        // Ctrl+Enter -> Execute
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.CTRL_DOWN_MASK), "execute");
        actionMap.put("execute", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                executeCode();
            }
        });

        // Ctrl+S -> Save
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_S, KeyEvent.CTRL_DOWN_MASK), "save");
        actionMap.put("save", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                saveScript();
            }
        });

        // Ctrl+O -> Load
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_O, KeyEvent.CTRL_DOWN_MASK), "load");
        actionMap.put("load", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                loadScript();
            }
        });

        // Ctrl+L -> Clear
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_L, KeyEvent.CTRL_DOWN_MASK), "clear");
        actionMap.put("clear", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                clearOutput();
            }
        });

        // Ctrl+F -> Find/Replace (charter editor bar; wired to the console in v4.3.3
        // when the legacy IDE — the dialog's only previous host — was removed)
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_F, KeyEvent.CTRL_DOWN_MASK), "findReplace");
        actionMap.put("findReplace", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                openFindReplace();
            }
        });
    }

    /** Opens (lazily creating) the Find/Replace dialog bound to the code editor. */
    private void openFindReplace() {
        if (findReplaceDialog == null) {
            java.awt.Window ancestor = SwingUtilities.getWindowAncestor(this);
            JFrame parentFrame = (ancestor instanceof JFrame) ? (JFrame) ancestor : null;
            findReplaceDialog = new FindReplaceDialog(parentFrame, codeEditor);
        }
        findReplaceDialog.showDialog();
    }

    // =========================================================================
    // Button factory methods
    // =========================================================================

    /**
     * Creates the prominent green Run button (matches Web GUI's primary action button).
     */
    private JButton createRunButton() {
        JButton button = new JButton("Run") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                Color bgColor;
                if (!isEnabled()) {
                    bgColor = ModernTheme.darken(ModernTheme.SUCCESS, 0.4);
                } else if (getModel().isPressed()) {
                    bgColor = ModernTheme.darken(ModernTheme.SUCCESS, 0.2);
                } else if (getModel().isRollover()) {
                    bgColor = ModernTheme.lighten(ModernTheme.SUCCESS, 0.15);
                } else {
                    bgColor = ModernTheme.SUCCESS;
                }

                g2.setColor(bgColor);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(),
                        ModernTheme.CORNER_RADIUS, ModernTheme.CORNER_RADIUS);

                // Draw play triangle icon
                g2.setColor(Color.WHITE);
                int iconSize = 10;
                int iconX = 12;
                int iconY = (getHeight() - iconSize) / 2;
                int[] xPoints = {iconX, iconX, iconX + iconSize};
                int[] yPoints = {iconY, iconY + iconSize, iconY + iconSize / 2};
                g2.fillPolygon(xPoints, yPoints, 3);

                // Draw text
                g2.setFont(getFont());
                java.awt.FontMetrics fm = g2.getFontMetrics();
                int textX = iconX + iconSize + 6;
                int textY = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
                g2.drawString(getText(), textX, textY);

                g2.dispose();
            }
        };

        button.setFont(ModernTheme.FONT_BUTTON);
        button.setForeground(Color.WHITE);
        button.setBackground(ModernTheme.SUCCESS);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setOpaque(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setPreferredSize(new Dimension(90, ModernTheme.BUTTON_HEIGHT_SECONDARY));
        button.setToolTipText("Execute code (Ctrl+Enter)");
        button.addActionListener(e -> executeCode());

        return button;
    }

    private JButton createToolbarButton(String text) {
        JButton button = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // Use getBackground() so theme changes are reflected
                Color base = getBackground();
                if (getModel().isPressed()) {
                    g2.setColor(ModernTheme.darken(base, 0.15));
                } else if (getModel().isRollover()) {
                    g2.setColor(ModernTheme.lighten(base, 0.1));
                } else {
                    g2.setColor(base);
                }

                g2.fillRoundRect(0, 0, getWidth(), getHeight(), ModernTheme.CORNER_RADIUS, ModernTheme.CORNER_RADIUS);

                g2.setColor(getForeground());
                g2.setFont(getFont());
                java.awt.FontMetrics fm = g2.getFontMetrics();
                int textX = (getWidth() - fm.stringWidth(getText())) / 2;
                int textY = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
                g2.drawString(getText(), textX, textY);

                g2.dispose();
            }
        };

        button.setFont(ModernTheme.FONT_BOLD);
        button.setForeground(ModernTheme.FOREGROUND_PRIMARY);
        button.setBackground(ModernTheme.BUTTON_BACKGROUND);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setOpaque(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // Auto-size based on text width
        int textWidth = button.getFontMetrics(ModernTheme.FONT_BOLD).stringWidth(text);
        button.setPreferredSize(new Dimension(textWidth + 24, ModernTheme.BUTTON_HEIGHT_SECONDARY));
        button.setMargin(new Insets(4, 10, 4, 10));

        return button;
    }
}
