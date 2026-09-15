package com.gaskony.python3.designer;

/**
 * Represents a single code completion result from the Python completion engine.
 * <p>
 * Converted to Java 17 record in v2.10.0 (eliminated 65 lines of boilerplate).
 *
 * @param text Completion text
 * @param type Completion type (e.g., "function", "class", "module")
 * @param complete Full completion string
 * @param description Short description of the completion
 * @param docstring Full docstring if available
 * @param signature Function/method signature if available
 *
 * @since v1.0.0 (as class)
 * @since v2.10.0 (as record)
 */
public record CompletionResult(
    String text,
    String type,
    String complete,
    String description,
    String docstring,
    String signature
) {
    /**
     * Compact constructor with validation.
     */
    public CompletionResult {
        // All fields can be null (optional metadata)
    }

    // Legacy getter methods for backward compatibility with existing code
    public String getText() { return text; }
    public String getType() { return type; }
    public String getComplete() { return complete; }
    public String getDescription() { return description; }
    public String getDocstring() { return docstring; }
    public String getSignature() { return signature; }

    @Override
    public String toString() {
        return "CompletionResult{" +
                "text='" + text + '\'' +
                ", type='" + type + '\'' +
                ", complete='" + complete + '\'' +
                ", description='" + description + '\'' +
                '}';
    }
}
