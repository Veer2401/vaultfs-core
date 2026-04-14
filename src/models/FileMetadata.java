package models;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Represents metadata for a single file.
 * Now includes the starting block ID for DiskSimulator fragmentation.
 */
public class FileMetadata {
    public String filename;
    public long sizeBytes;
    public LocalDateTime createdAt;
    public LocalDateTime modifiedAt;
    public String type;
    public int startBlockId; // Used by DiskSimulator to trace file fragments

    /**
     * Initializes file metadata fields from name and size.
     */
    public FileMetadata(String filename, long sizeBytes) {
        this.filename = filename;
        this.sizeBytes = sizeBytes;
        this.createdAt = LocalDateTime.now();
        this.modifiedAt = LocalDateTime.now();
        this.type = getExtension(filename);
        this.startBlockId = -1; // Default to unallocated
    }

    private String getExtension(String name) {
        int lastDot = name.lastIndexOf('.');
        if (lastDot > 0 && lastDot < name.length() - 1) {
            return name.substring(lastDot + 1).toLowerCase();
        }
        return "unknown";
    }

    /**
     * Returns a human-readable file size in B, KB, or MB.
     */
    public String formattedSize() {
        if (sizeBytes < 1024) {
            return sizeBytes + " B";
        }
        if (sizeBytes < 1_048_576) {
            return String.format("%.1f KB", sizeBytes / 1024.0);
        }
        return String.format("%.1f MB", sizeBytes / 1_048_576.0);
    }

    /**
     * Returns a formatted row containing filename, size, type, and modified timestamp.
     */
    @Override
    public String toString() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return String.format("%-28s %-10s %-8s %s", filename, formattedSize(), type, modifiedAt.format(formatter));
    }
}
