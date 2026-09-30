package com.gaskony.python3.designer;

/**
 * Metadata for a saved Python script (without the full code).
 * <p>
 * Converted to Java 17 record in v2.10.0 (eliminated 64 lines of boilerplate).
 *
 * @param id Script unique identifier
 * @param name Script display name
 * @param description Script description/documentation
 * @param author Script author name
 * @param createdDate Creation timestamp
 * @param lastModified Last modification timestamp
 * @param folderPath Folder path in script tree
 * @param version Script version number
 *
 * @since v1.0.0 (as class)
 * @since v2.10.0 (as record)
 */
public record ScriptMetadata(
    String id,
    String name,
    String description,
    String author,
    String createdDate,
    String lastModified,
    String folderPath,
    String version
) {
    /**
     * Compact constructor with validation.
     */
    public ScriptMetadata {
        // name can be validated if needed, but allow null for partial metadata
        // All fields are optional metadata
    }

    // Legacy getter methods for backward compatibility with existing code
    // Records generate accessor methods without "get" prefix (e.g., name() instead of getName())
    // These methods provide JavaBeans-style getters for minimal refactoring impact
    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getAuthor() { return author; }
    public String getCreatedDate() { return createdDate; }
    public String getLastModified() { return lastModified; }
    public String getFolderPath() { return folderPath; }
    public String getVersion() { return version; }
}
