package com.gaskony.python3.designer;

import org.fife.ui.rsyntaxtextarea.RSyntaxDocument;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.parser.AbstractParser;
import org.fife.ui.rsyntaxtextarea.parser.DefaultParseResult;
import org.fife.ui.rsyntaxtextarea.parser.DefaultParserNotice;
import org.fife.ui.rsyntaxtextarea.parser.ParseResult;
import org.fife.ui.rsyntaxtextarea.parser.ParserNotice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Python syntax checker that integrates with RSyntaxTextArea's parser system.
 * Provides real-time syntax checking with red squiggly underlines for errors
 * and yellow underlines for warnings.
 */
public class PythonSyntaxChecker extends AbstractParser implements DocumentListener {
    private static final Logger logger = LoggerFactory.getLogger(PythonSyntaxChecker.class);

    private final Python3RestClient restClient;
    private final RSyntaxTextArea textArea;
    private Timer debounceTimer;
    private static final int DEBOUNCE_DELAY_MS = 500;

    private DefaultParseResult lastResult;

    /**
     * Creates a new Python syntax checker.
     *
     * @param textArea   The text area to check
     * @param restClient The REST client for communicating with Gateway
     */
    public PythonSyntaxChecker(RSyntaxTextArea textArea, Python3RestClient restClient) {
        this.textArea = textArea;
        this.restClient = restClient;
        this.lastResult = new DefaultParseResult(this);

        // Set up debounced checking
        debounceTimer = new Timer(DEBOUNCE_DELAY_MS, e -> checkSyntaxAsync());
        debounceTimer.setRepeats(false);

        // Listen to document changes
        textArea.getDocument().addDocumentListener(this);

        logger.debug("PythonSyntaxChecker initialized");
    }

    @Override
    public ParseResult parse(RSyntaxDocument doc, String style) {
        // This is called by RSyntaxTextArea's parser manager
        // We update lastResult asynchronously via checkSyntaxAsync
        return lastResult;
    }

    /**
     * Triggers a syntax check after the debounce delay.
     */
    private void scheduleCheck() {
        debounceTimer.restart();
    }

    /**
     * Performs syntax checking asynchronously.
     */
    private void checkSyntaxAsync() {
        if (restClient == null) {
            logger.debug("REST client not available, skipping syntax check");
            return;
        }

        String code = textArea.getText();

        // Check syntax in background thread
        SwingWorker<List<SyntaxError>, Void> worker = new SwingWorker<List<SyntaxError>, Void>() {
            @Override
            protected List<SyntaxError> doInBackground() throws Exception {
                try {
                    Map<String, Object> result = restClient.checkSyntax(code);
                    return parseSyntaxErrors(result);
                } catch (Exception e) {
                    logger.warn("Syntax check failed: {}", e.getMessage());
                    return new ArrayList<>();
                }
            }

            @Override
            protected void done() {
                try {
                    List<SyntaxError> errors = get();
                    updateParseResult(errors);

                    // Notify RSyntaxTextArea to update error markers
                    textArea.forceReparsing(PythonSyntaxChecker.this);

                } catch (Exception e) {
                    logger.error("Failed to update syntax errors", e);
                }
            }
        };

        worker.execute();
    }

    /**
     * Parses syntax errors from REST API response.
     */
    private List<SyntaxError> parseSyntaxErrors(Map<String, Object> response) {
        List<SyntaxError> errors = new ArrayList<>();

        if (response == null || !response.containsKey("errors")) {
            return errors;
        }

        Object errorsObj = response.get("errors");
        if (!(errorsObj instanceof List)) {
            return errors;
        }

        // Safe cast: REST API returns List<Map<String, Object>> from JSON
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> errorsList = (List<Map<String, Object>>) errorsObj;

        for (Map<String, Object> errorMap : errorsList) {
            try {
                int line = getIntValue(errorMap, "line", 1);
                int column = getIntValue(errorMap, "column", 0);
                String message = getStringValue(errorMap, "message", "Syntax error");
                String severity = getStringValue(errorMap, "severity", "error");

                SyntaxError error = new SyntaxError(line, column, message, severity);
                errors.add(error);

            } catch (Exception e) {
                logger.warn("Failed to parse error entry: {}", errorMap, e);
            }
        }

        logger.debug("Parsed {} syntax errors", errors.size());
        return errors;
    }

    /**
     * Updates the parse result with new errors.
     */
    private void updateParseResult(List<SyntaxError> syntaxErrors) {
        DefaultParseResult result = new DefaultParseResult(this);

        for (SyntaxError syntaxError : syntaxErrors) {
            try {
                // Convert 1-based line to 0-based offset
                int line = syntaxError.line - 1;
                int offset = textArea.getLineStartOffset(line);

                // Add column offset if available
                if (syntaxError.column > 0) {
                    offset += syntaxError.column;
                }

                // Create parser notice with appropriate level
                DefaultParserNotice notice = new DefaultParserNotice(
                    this,
                    syntaxError.message,
                    line,
                    offset,
                    syntaxError.message.length()
                );

                // Set the level based on severity
                if ("error".equals(syntaxError.severity)) {
                    notice.setLevel(ParserNotice.Level.ERROR);
                } else {
                    notice.setLevel(ParserNotice.Level.WARNING);
                }

                result.addNotice(notice);

            } catch (BadLocationException e) {
                logger.warn("Invalid line number: {}", syntaxError.line, e);
            }
        }

        lastResult = result;
    }

    // DocumentListener implementation

    @Override
    public void insertUpdate(DocumentEvent e) {
        scheduleCheck();
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
        scheduleCheck();
    }

    @Override
    public void changedUpdate(DocumentEvent e) {
        // Attribute changes, usually don't need to recheck
    }

    /**
     * Stops the syntax checker and cleans up resources.
     */
    public void dispose() {
        if (debounceTimer != null) {
            debounceTimer.stop();
        }

        textArea.getDocument().removeDocumentListener(this);
        logger.debug("PythonSyntaxChecker disposed");
    }

    /**
     * Performs an immediate syntax check (not debounced).
     */
    public void checkNow() {
        debounceTimer.stop();
        checkSyntaxAsync();
    }

    // Helper methods for parsing REST response

    private int getIntValue(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }

    private String getStringValue(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        if (value != null) {
            return value.toString();
        }
        return defaultValue;
    }

    /**
     * Internal class to represent a syntax error.
     */
    private static class SyntaxError {
        final int line;
        final int column;
        final String message;
        final String severity;

        SyntaxError(int line, int column, String message, String severity) {
            this.line = line;
            this.column = column;
            this.message = message;
            this.severity = severity;
        }
    }
}
