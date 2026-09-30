package com.gaskony.python3.designer.ui;

import com.gaskony.python3.designer.ModernTheme;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.SearchContext;
import org.fife.ui.rtextarea.SearchEngine;
import org.fife.ui.rtextarea.SearchResult;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.KeyStroke;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Advanced Find/Replace dialog with regex support, whole word matching, and search history.
 *
 * Features:
 * - Regex pattern support
 * - Whole word matching
 * - Case-sensitive search
 * - Search direction (forward/backward)
 * - Find/Replace history with combo boxes
 * - Match count display
 * - Modern dark theme
 *
 * v2.1.0: Advanced Find/Replace Dialog implementation
 */
public class FindReplaceDialog extends JDialog {

    private static final int MAX_HISTORY_SIZE = 20;

    private final RSyntaxTextArea textArea;
    private final JComboBox<String> findCombo;
    private final JComboBox<String> replaceCombo;
    private final JCheckBox matchCaseCheckbox;
    private final JCheckBox wholeWordCheckbox;
    private final JCheckBox regexCheckbox;
    private final JRadioButton forwardRadio;
    private final JRadioButton backwardRadio;
    private final JLabel statusLabel;

    private final List<String> findHistory;
    private final List<String> replaceHistory;

    /**
     * Creates a new Find/Replace dialog.
     *
     * @param parent the parent frame
     * @param textArea the text area to search in
     */
    public FindReplaceDialog(JFrame parent, RSyntaxTextArea textArea) {
        super(parent, "Find and Replace", false); // Non-modal
        this.textArea = textArea;
        this.findHistory = new ArrayList<>();
        this.replaceHistory = new ArrayList<>();

        // Initialize components
        findCombo = new JComboBox<>();
        findCombo.setEditable(true);
        findCombo.setPreferredSize(new Dimension(300, 25));
        findCombo.setFont(ModernTheme.FONT_REGULAR);
        findCombo.setBackground(ModernTheme.INPUT_BACKGROUND);
        findCombo.setForeground(ModernTheme.FOREGROUND_PRIMARY);

        replaceCombo = new JComboBox<>();
        replaceCombo.setEditable(true);
        replaceCombo.setPreferredSize(new Dimension(300, 25));
        replaceCombo.setFont(ModernTheme.FONT_REGULAR);
        replaceCombo.setBackground(ModernTheme.INPUT_BACKGROUND);
        replaceCombo.setForeground(ModernTheme.FOREGROUND_PRIMARY);

        matchCaseCheckbox = new JCheckBox("Match Case");
        matchCaseCheckbox.setFont(ModernTheme.FONT_REGULAR);
        matchCaseCheckbox.setBackground(ModernTheme.PANEL_BACKGROUND);
        matchCaseCheckbox.setForeground(ModernTheme.FOREGROUND_PRIMARY);

        wholeWordCheckbox = new JCheckBox("Whole Word");
        wholeWordCheckbox.setFont(ModernTheme.FONT_REGULAR);
        wholeWordCheckbox.setBackground(ModernTheme.PANEL_BACKGROUND);
        wholeWordCheckbox.setForeground(ModernTheme.FOREGROUND_PRIMARY);

        regexCheckbox = new JCheckBox("Regular Expression");
        regexCheckbox.setFont(ModernTheme.FONT_REGULAR);
        regexCheckbox.setBackground(ModernTheme.PANEL_BACKGROUND);
        regexCheckbox.setForeground(ModernTheme.FOREGROUND_PRIMARY);

        forwardRadio = new JRadioButton("Forward", true);
        forwardRadio.setFont(ModernTheme.FONT_REGULAR);
        forwardRadio.setBackground(ModernTheme.PANEL_BACKGROUND);
        forwardRadio.setForeground(ModernTheme.FOREGROUND_PRIMARY);

        backwardRadio = new JRadioButton("Backward");
        backwardRadio.setFont(ModernTheme.FONT_REGULAR);
        backwardRadio.setBackground(ModernTheme.PANEL_BACKGROUND);
        backwardRadio.setForeground(ModernTheme.FOREGROUND_PRIMARY);

        ButtonGroup directionGroup = new ButtonGroup();
        directionGroup.add(forwardRadio);
        directionGroup.add(backwardRadio);

        statusLabel = new JLabel(" ");
        statusLabel.setFont(ModernTheme.FONT_REGULAR);
        statusLabel.setForeground(ModernTheme.INFO);

        // Build UI
        setLayout(new BorderLayout());
        getContentPane().setBackground(ModernTheme.BACKGROUND_DARK);

        add(createMainPanel(), BorderLayout.CENTER);
        add(createButtonPanel(), BorderLayout.EAST);
        add(createStatusPanel(), BorderLayout.SOUTH);

        // Keyboard shortcuts
        setupKeyboardShortcuts();

        // Dialog settings
        pack();
        setLocationRelativeTo(parent);
        setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);
    }

    /**
     * Creates the main panel with find/replace fields and options.
     */
    private JPanel createMainPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(ModernTheme.PANEL_BACKGROUND);
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 10));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Find label and combo
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        JLabel findLabel = new JLabel("Find:");
        findLabel.setFont(ModernTheme.FONT_REGULAR);
        findLabel.setForeground(ModernTheme.FOREGROUND_PRIMARY);
        panel.add(findLabel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1;
        panel.add(findCombo, gbc);

        // Replace label and combo
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        JLabel replaceLabel = new JLabel("Replace:");
        replaceLabel.setFont(ModernTheme.FONT_REGULAR);
        replaceLabel.setForeground(ModernTheme.FOREGROUND_PRIMARY);
        panel.add(replaceLabel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1;
        panel.add(replaceCombo, gbc);

        // Options panel
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        panel.add(createOptionsPanel(), gbc);

        return panel;
    }

    /**
     * Creates the options panel with checkboxes and radio buttons.
     */
    private JPanel createOptionsPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(ModernTheme.PANEL_BACKGROUND);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ModernTheme.BORDER_SUBTLE),
            BorderFactory.createEmptyBorder(5, 5, 5, 5)
        ));

        // Checkboxes panel
        JPanel checkboxPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        checkboxPanel.setBackground(ModernTheme.PANEL_BACKGROUND);
        checkboxPanel.add(matchCaseCheckbox);
        checkboxPanel.add(wholeWordCheckbox);
        checkboxPanel.add(regexCheckbox);

        // Direction panel
        JPanel directionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        directionPanel.setBackground(ModernTheme.PANEL_BACKGROUND);
        JLabel directionLabel = new JLabel("Direction:");
        directionLabel.setFont(ModernTheme.FONT_REGULAR);
        directionLabel.setForeground(ModernTheme.FOREGROUND_PRIMARY);
        directionPanel.add(directionLabel);
        directionPanel.add(forwardRadio);
        directionPanel.add(backwardRadio);

        panel.add(checkboxPanel);
        panel.add(directionPanel);

        return panel;
    }

    /**
     * Creates the button panel with action buttons.
     */
    private JPanel createButtonPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(ModernTheme.PANEL_BACKGROUND);
        panel.setBorder(BorderFactory.createEmptyBorder(15, 5, 15, 15));

        JButton findNextButton = createStyledButton("Find Next");
        findNextButton.addActionListener(e -> findNext());

        JButton findPrevButton = createStyledButton("Find Previous");
        findPrevButton.addActionListener(e -> findPrevious());

        JButton replaceButton = createStyledButton("Replace");
        replaceButton.addActionListener(e -> replace());

        JButton replaceAllButton = createStyledButton("Replace All");
        replaceAllButton.addActionListener(e -> replaceAll());

        JButton countButton = createStyledButton("Count Matches");
        countButton.addActionListener(e -> countMatches());

        JButton closeButton = createStyledButton("Close");
        closeButton.addActionListener(e -> setVisible(false));

        panel.add(findNextButton);
        panel.add(Box.createVerticalStrut(5));
        panel.add(findPrevButton);
        panel.add(Box.createVerticalStrut(10));
        panel.add(replaceButton);
        panel.add(Box.createVerticalStrut(5));
        panel.add(replaceAllButton);
        panel.add(Box.createVerticalStrut(10));
        panel.add(countButton);
        panel.add(Box.createVerticalStrut(20));
        panel.add(closeButton);

        return panel;
    }

    /**
     * Creates the status panel with status label.
     */
    private JPanel createStatusPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(ModernTheme.BACKGROUND_DARKER);
        panel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        panel.add(statusLabel, BorderLayout.WEST);
        return panel;
    }

    /**
     * Creates a styled button with modern theme.
     */
    private JButton createStyledButton(String text) {
        JButton button = new JButton(text);
        button.setFont(ModernTheme.FONT_REGULAR);
        button.setBackground(ModernTheme.BUTTON_BACKGROUND);
        button.setForeground(ModernTheme.FOREGROUND_PRIMARY);
        button.setFocusPainted(false);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        button.setPreferredSize(new Dimension(140, 30));
        return button;
    }

    /**
     * Sets up keyboard shortcuts for the dialog.
     */
    private void setupKeyboardShortcuts() {
        // F3 - Find Next
        getRootPane().registerKeyboardAction(
            e -> findNext(),
            KeyStroke.getKeyStroke(KeyEvent.VK_F3, 0),
            javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW
        );

        // Shift+F3 - Find Previous
        getRootPane().registerKeyboardAction(
            e -> findPrevious(),
            KeyStroke.getKeyStroke(KeyEvent.VK_F3, KeyEvent.SHIFT_DOWN_MASK),
            javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW
        );

        // Ctrl+H - Replace
        getRootPane().registerKeyboardAction(
            e -> replace(),
            KeyStroke.getKeyStroke(KeyEvent.VK_H, KeyEvent.CTRL_DOWN_MASK),
            javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW
        );

        // Escape - Close
        getRootPane().registerKeyboardAction(
            e -> setVisible(false),
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW
        );
    }

    /**
     * Creates a search context based on current options.
     */
    private SearchContext createSearchContext() {
        String searchText = getComboText(findCombo);
        if (searchText == null || searchText.isEmpty()) {
            setStatus("Please enter search text", ModernTheme.WARNING);
            return null;
        }

        addToHistory(findHistory, searchText, findCombo);

        SearchContext context = new SearchContext();
        context.setSearchFor(searchText);
        context.setMatchCase(matchCaseCheckbox.isSelected());
        context.setWholeWord(wholeWordCheckbox.isSelected());
        context.setRegularExpression(regexCheckbox.isSelected());
        context.setSearchForward(forwardRadio.isSelected());

        return context;
    }

    /**
     * Finds the next occurrence.
     */
    private void findNext() {
        SearchContext context = createSearchContext();
        if (context == null) {
            return;
        }

        context.setSearchForward(true);
        SearchResult result = SearchEngine.find(textArea, context);

        if (result.wasFound()) {
            setStatus("Found match", ModernTheme.SUCCESS);
        } else {
            setStatus("No more matches found", ModernTheme.WARNING);
        }
    }

    /**
     * Finds the previous occurrence.
     */
    private void findPrevious() {
        SearchContext context = createSearchContext();
        if (context == null) {
            return;
        }

        context.setSearchForward(false);
        SearchResult result = SearchEngine.find(textArea, context);

        if (result.wasFound()) {
            setStatus("Found match", ModernTheme.SUCCESS);
        } else {
            setStatus("No previous matches found", ModernTheme.WARNING);
        }
    }

    /**
     * Replaces the current selection and finds next.
     */
    private void replace() {
        SearchContext context = createSearchContext();
        if (context == null) {
            return;
        }

        String replaceText = getComboText(replaceCombo);
        addToHistory(replaceHistory, replaceText != null ? replaceText : "", replaceCombo);

        context.setReplaceWith(replaceText != null ? replaceText : "");
        SearchResult result = SearchEngine.replace(textArea, context);

        if (result.wasFound()) {
            setStatus("Replaced and found next", ModernTheme.SUCCESS);
        } else {
            setStatus("No match to replace", ModernTheme.WARNING);
        }
    }

    /**
     * Replaces all occurrences.
     */
    private void replaceAll() {
        SearchContext context = createSearchContext();
        if (context == null) {
            return;
        }

        String replaceText = getComboText(replaceCombo);
        addToHistory(replaceHistory, replaceText != null ? replaceText : "", replaceCombo);

        context.setReplaceWith(replaceText != null ? replaceText : "");
        SearchResult result = SearchEngine.replaceAll(textArea, context);

        int count = result.getCount();
        if (count > 0) {
            setStatus("Replaced " + count + " occurrence(s)", ModernTheme.SUCCESS);
        } else {
            setStatus("No matches found to replace", ModernTheme.WARNING);
        }
    }

    /**
     * Counts the number of matches.
     */
    private void countMatches() {
        SearchContext context = createSearchContext();
        if (context == null) {
            return;
        }

        // Save current position
        int originalPos = textArea.getCaretPosition();
        int originalSelStart = textArea.getSelectionStart();
        int originalSelEnd = textArea.getSelectionEnd();

        // Count matches
        textArea.setCaretPosition(0);
        int count = 0;
        SearchResult result;
        do {
            result = SearchEngine.find(textArea, context);
            if (result.wasFound()) {
                count++;
            }
        } while (result.wasFound());

        // Restore position
        textArea.setCaretPosition(originalPos);
        if (originalSelStart != originalSelEnd) {
            textArea.setSelectionStart(originalSelStart);
            textArea.setSelectionEnd(originalSelEnd);
        }

        if (count > 0) {
            setStatus("Found " + count + " match(es)", ModernTheme.INFO);
        } else {
            setStatus("No matches found", ModernTheme.WARNING);
        }
    }

    /**
     * Gets the text from a combo box.
     */
    private String getComboText(JComboBox<String> combo) {
        Object editor = combo.getEditor().getItem();
        return editor != null ? editor.toString() : null;
    }

    /**
     * Adds an item to history and updates the combo box.
     */
    private void addToHistory(List<String> history, String item, JComboBox<String> combo) {
        if (item == null || item.isEmpty()) {
            return;
        }

        // Remove if already exists (move to top)
        history.remove(item);

        // Add to beginning
        history.add(0, item);

        // Limit size
        if (history.size() > MAX_HISTORY_SIZE) {
            history.remove(history.size() - 1);
        }

        // Update combo box
        combo.removeAllItems();
        for (String historyItem : history) {
            combo.addItem(historyItem);
        }
        combo.setSelectedItem(item);
    }

    /**
     * Sets the status message with color.
     */
    private void setStatus(String message, java.awt.Color color) {
        statusLabel.setText(message);
        statusLabel.setForeground(color);
    }

    /**
     * Shows the dialog and focuses the find field.
     */
    public void showDialog() {
        setVisible(true);
        findCombo.requestFocusInWindow();

        // Pre-populate with selected text if any
        String selectedText = textArea.getSelectedText();
        if (selectedText != null && !selectedText.isEmpty() && !selectedText.contains("\n")) {
            findCombo.getEditor().setItem(selectedText);
        }
    }
}
