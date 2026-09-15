package com.gaskony.python3.designer;

import org.fife.ui.autocomplete.BasicCompletion;
import org.fife.ui.autocomplete.Completion;
import org.fife.ui.autocomplete.DefaultCompletionProvider;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Custom completion provider for Python code that uses the Gateway's Jedi-powered
 * completion engine to provide intelligent auto-completion suggestions.
 *
 * v2.4.0: Enhanced error handling and diagnostics
 */
public class Python3CompletionProvider extends DefaultCompletionProvider {
    private static final Logger logger = LoggerFactory.getLogger(Python3CompletionProvider.class);

    private final Python3RestClient restClient;
    private boolean jediAvailable = true;  // Assume available until proven otherwise
    private long lastFailureTime = 0;
    private static final long FAILURE_COOLDOWN = 60000;  // 1 minute cooldown after failures

    /**
     * Hard cap on how long a completion fetch may hold the EDT. The AutoComplete
     * library invokes {@link #getCompletionsImpl} synchronously on the EDT, so a
     * slow or cold Jedi must never be allowed to freeze the Designer. On timeout
     * we return no completions; the request keeps running server-side and warms
     * Jedi, so the next Ctrl+Space is typically fast. A timeout deliberately does
     * NOT trigger the failure cooldown.
     */
    private static final long COMPLETION_TIMEOUT_MS = 1500;

    public Python3CompletionProvider(Python3RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * Returns true if autocomplete is currently available.
     *
     * @return true if Jedi is available, false otherwise
     */
    public boolean isAvailable() {
        return jediAvailable && (System.currentTimeMillis() - lastFailureTime > FAILURE_COOLDOWN);
    }

    /**
     * Returns a status message about autocomplete availability.
     *
     * @return status message
     */
    public String getStatusMessage() {
        if (!jediAvailable) {
            return "Autocomplete unavailable (Jedi not installed)";
        } else if (System.currentTimeMillis() - lastFailureTime < FAILURE_COOLDOWN) {
            long secondsRemaining = (FAILURE_COOLDOWN - (System.currentTimeMillis() - lastFailureTime)) / 1000;
            return "Autocomplete temporarily disabled (" + secondsRemaining + "s cooldown)";
        } else {
            return "Autocomplete ready (Ctrl+Space)";
        }
    }

    @Override
    protected List<Completion> getCompletionsImpl(JTextComponent comp) {
        List<Completion> completions = new ArrayList<>();

        if (!(comp instanceof RSyntaxTextArea)) {
            return completions;
        }

        RSyntaxTextArea textArea = (RSyntaxTextArea) comp;

        try {
            // Get cursor position
            int caretPos = textArea.getCaretPosition();
            int lineNum = textArea.getLineOfOffset(caretPos);
            int lineStart = textArea.getLineStartOffset(lineNum);
            int column = caretPos - lineStart;

            // Get all code in the editor
            String code = textArea.getText();

            // Convert to 1-based line number for Python
            int pythonLine = lineNum + 1;

            logger.debug("Getting completions at line {}, column {}", pythonLine, column);

            // Fetch over Gateway RPC on a background thread, bounded by
            // COMPLETION_TIMEOUT_MS so the EDT can never be frozen by a slow call.
            List<CompletionResult> results;
            try {
                results = CompletableFuture.supplyAsync(() -> {
                    try {
                        return restClient.getCompletions(code, pythonLine, column);
                    } catch (Exception ex) {
                        throw new CompletionException(ex);
                    }
                }).get(COMPLETION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (TimeoutException te) {
                logger.debug("Completion request exceeded {} ms (Jedi may be warming up); returning none",
                        COMPLETION_TIMEOUT_MS);
                return completions;
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return completions;
            } catch (ExecutionException ee) {
                // Unwrap so the existing Jedi-detection / cooldown logic below sees
                // the original exception message.
                Throwable cause = ee.getCause() != null ? ee.getCause() : ee;
                throw cause instanceof Exception ? (Exception) cause : new Exception(cause);
            }

            // Convert CompletionResult objects to RSyntaxTextArea Completion objects
            for (CompletionResult result : results) {
                String replacementText = result.getComplete() != null ? result.getComplete() : result.getText();
                String shortDesc = result.getDescription();
                String summary = buildSummary(result);

                BasicCompletion completion = new BasicCompletion(this, replacementText, shortDesc, summary);
                completions.add(completion);
            }

            logger.debug("Providing {} completions", completions.size());

            // Mark Jedi as available if we got results successfully
            if (!jediAvailable && !results.isEmpty()) {
                jediAvailable = true;
                logger.info("Autocomplete now available (Jedi detected)");
            }

        } catch (BadLocationException e) {
            logger.error("Failed to get cursor position", e);
        } catch (Exception e) {
            // Distinguish between different error types
            String errorMsg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";

            if (errorMsg.contains("jedi") || errorMsg.contains("not installed") || errorMsg.contains("module not found")) {
                // Jedi not available
                if (jediAvailable) {
                    logger.warn("Autocomplete unavailable: Jedi not installed on Gateway. Install with: pip install jedi");
                    jediAvailable = false;
                }
            } else {
                // Other error - temporary failure
                logger.debug("Failed to get completions from Gateway: {}", e.getMessage());
                lastFailureTime = System.currentTimeMillis();
            }
            // Return empty list on error - don't break the user experience
        }

        return completions;
    }

    /**
     * Builds a rich HTML summary for the completion popup.
     *
     * @param result the completion result
     * @return HTML-formatted summary
     */
    private String buildSummary(CompletionResult result) {
        StringBuilder html = new StringBuilder();
        html.append("<html><body style='width: 300px; padding: 5px;'>");

        // Type badge
        if (result.getType() != null) {
            String typeColor = getTypeColor(result.getType());
            html.append("<span style='background-color: ").append(typeColor)
                .append("; color: white; padding: 2px 6px; border-radius: 3px; font-size: 10px; font-weight: bold;'>")
                .append(result.getType().toUpperCase())
                .append("</span> ");
        }

        // Completion text
        html.append("<b>").append(escapeHtml(result.getText())).append("</b>");

        // Signature
        if (result.getSignature() != null && !result.getSignature().isEmpty()) {
            html.append("<br><code style='color: #666;'>")
                .append(escapeHtml(result.getSignature()))
                .append("</code>");
        }

        // Description
        if (result.getDescription() != null && !result.getDescription().isEmpty()) {
            html.append("<br><p style='margin-top: 5px; color: #333;'>")
                .append(escapeHtml(result.getDescription()))
                .append("</p>");
        }

        // Docstring (first 200 chars)
        if (result.getDocstring() != null && !result.getDocstring().isEmpty()) {
            String docstring = result.getDocstring();
            if (docstring.length() > 200) {
                docstring = docstring.substring(0, 200) + "...";
            }
            html.append("<br><p style='margin-top: 5px; color: #555; font-size: 11px; font-style: italic;'>")
                .append(escapeHtml(docstring))
                .append("</p>");
        }

        html.append("</body></html>");
        return html.toString();
    }

    /**
     * Gets a color for the completion type badge.
     *
     * @param type the completion type (function, class, module, etc.)
     * @return hex color code
     */
    private String getTypeColor(String type) {
        switch (type.toLowerCase()) {
            case "function":
            case "method":
                return "#3776AB";  // Python blue
            case "class":
                return "#FFD43B";  // Python yellow
            case "module":
                return "#646464";  // Gray
            case "keyword":
                return "#FF6B6B";  // Red
            case "variable":
            case "instance":
                return "#4ECDC4";  // Teal
            default:
                return "#95A5A6";  // Default gray
        }
    }

    /**
     * Escapes HTML special characters.
     *
     * @param text the text to escape
     * @return escaped HTML
     */
    private String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }

    @Override
    public String getAlreadyEnteredText(JTextComponent comp) {
        String text = "";
        try {
            // Get the text from the start of the current word to the cursor
            int caretPos = comp.getCaretPosition();
            int start = caretPos;

            // Find the start of the current word (letter, digit, or underscore)
            while (start > 0) {
                char ch = comp.getText(start - 1, 1).charAt(0);
                if (!Character.isLetterOrDigit(ch) && ch != '_' && ch != '.') {
                    break;
                }
                start--;
            }

            text = comp.getText(start, caretPos - start);

        } catch (BadLocationException e) {
            logger.error("Failed to get already entered text", e);
        }

        return text;
    }
}
