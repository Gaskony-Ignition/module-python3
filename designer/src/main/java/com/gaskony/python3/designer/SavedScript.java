package com.gaskony.python3.designer;

/**
 * Represents a saved Python script with full code.
 * <p>
 * Converted to Java 17 record in v2.10.0 (eliminated 93 lines of boilerplate).
 *
 * @param id Script unique identifier
 * @param name Script display name
 * @param code Python code content
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
public record SavedScript(
    String id,
    String name,
    String code,
    String description,
    String author,
    String createdDate,
    String lastModified,
    String folderPath,
    String version
) {
    /**
     * Compact constructor with validation.
     * Records automatically generate constructor, but we can add validation here.
     */
    public SavedScript {
        // Null checks for required fields
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Script name cannot be null or blank");
        }
        // Other fields can be null (optional metadata)
    }

    // Legacy getter methods for backward compatibility with existing code
    // Records generate accessor methods without "get" prefix (e.g., name() instead of getName())
    // These methods provide JavaBeans-style getters for minimal refactoring impact
    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public String getAuthor() {
        return author;
    }

    public String getCreatedDate() {
        return createdDate;
    }

    public String getLastModified() {
        return lastModified;
    }

    public String getFolderPath() {
        return folderPath;
    }

    public String getVersion() {
        return version;
    }
}
