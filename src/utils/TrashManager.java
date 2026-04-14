package utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.LinkedList;

/**
 * Trash/Recycle Bin management using Queue (FIFO).
 * Tracks deleted files with timestamps for recovery.
 */
public class TrashManager {
    private static final Queue<TrashItem> trashBin = new LinkedList<>();
    private static final Map<String, TrashItem> trashIndex = new HashMap<>();
    private static final String TRASH_DIR = System.getProperty("user.dir") + File.separator + ".vaultfs_trash";

    static {
        new File(TRASH_DIR).mkdirs();
    }

    /**
     * Represents a deleted file in trash.
     */
    public static class TrashItem {
        public String originalPath;
        public String trashedPath;
        public long deletionTime;
        public long fileSize;
        public boolean isDirectory;

        public TrashItem(String originalPath, String trashedPath, long fileSize, boolean isDirectory) {
            this.originalPath = originalPath;
            this.trashedPath = trashedPath;
            this.fileSize = fileSize;
            this.isDirectory = isDirectory;
            this.deletionTime = System.currentTimeMillis();
        }

        public String getAge() {
            long ageMs = System.currentTimeMillis() - deletionTime;
            long seconds = ageMs / 1000;
            long minutes = seconds / 60;
            long hours = minutes / 60;
            long days = hours / 24;

            if (days > 0) return days + "d ago";
            if (hours > 0) return hours + "h ago";
            if (minutes > 0) return minutes + "m ago";
            return seconds + "s ago";
        }
    }

    /**
     * Move file/directory to trash.
     */
    public static void moveToTrash(String originalPath, boolean isDirectory) {
        try {
            // Normalize path for storage
            String normalizedPath = normalizePath(originalPath);
            File source = new File(normalizedPath);
            if (!source.exists()) {
                System.out.println(Colors.c(Colors.RED, "File not found: " + originalPath));
                return;
            }

            long fileSize = getFileSize(source);
            String timestamp = String.valueOf(System.currentTimeMillis());
            String fileName = source.getName();
            String trashedPath = TRASH_DIR + File.separator + timestamp + "_" + fileName;

            Files.move(source.toPath(), Paths.get(trashedPath));

            TrashItem item = new TrashItem(normalizedPath, trashedPath, fileSize, isDirectory);
            trashBin.offer(item); // Add to queue (FIFO)
            trashIndex.put(normalizedPath, item);

            System.out.println(Colors.c(Colors.YELLOW, "✓ Moved to trash: " + fileName));
        } catch (IOException e) {
            System.out.println(Colors.c(Colors.RED, "Error moving to trash: " + e.getMessage()));
        }
    }

    /**
     * List all items in trash.
     */
    public static void listTrash() {
        if (trashBin.isEmpty()) {
            System.out.println(Colors.c(Colors.CYAN, "Trash is empty."));
            return;
        }

        System.out.println(Colors.c(Colors.CYAN, "╔════════════════════════════════════════════╗"));
        System.out.println(Colors.c(Colors.CYAN, "║          Trash / Recycle Bin              ║"));
        System.out.println(Colors.c(Colors.CYAN, "╠════════════════════════════════════════════╣"));

        int index = 1;
        long totalSize = 0;
        for (TrashItem item : trashBin) {
            String icon = item.isDirectory ? "📁" : "📄";
            String size = formatSize(item.fileSize);
            System.out.printf("%s [%d] %s %-30s | %s | %s%n",
                icon, index, item.getAge().substring(0, Math.min(6, item.getAge().length())),
                item.originalPath, size, item.originalPath);
            totalSize += item.fileSize;
            index++;
        }

        System.out.println(Colors.c(Colors.CYAN, "╠════════════════════════════════════════════╣"));
        System.out.println(Colors.c(Colors.CYAN, String.format("Total items: %d | Total size: %s", trashBin.size(), formatSize(totalSize))));
        System.out.println(Colors.c(Colors.CYAN, "╚════════════════════════════════════════════╝"));
    }

    /**
     * Restore file from trash by filename only (searches for most recent match).
     */
    public static void restoreFromTrashByName(String filename) {
        TrashItem itemToRestore = null;
        
        // Find the item with matching filename (most recent = last in queue for this name)
        for (TrashItem item : trashBin) {
            if (new File(item.originalPath).getName().equals(filename)) {
                itemToRestore = item;
                break; // Take first match (oldest deleted first)
            }
        }
        
        if (itemToRestore == null) {
            System.out.println(Colors.c(Colors.RED, "No file '" + filename + "' found in trash."));
            return;
        }

        try {
            File trashedFile = new File(itemToRestore.trashedPath);
            if (!trashedFile.exists()) {
                System.out.println(Colors.c(Colors.RED, "Trashed file no longer exists."));
                trashBin.remove(itemToRestore);
                trashIndex.remove(itemToRestore.originalPath);
                return;
            }

            Files.move(trashedFile.toPath(), Paths.get(itemToRestore.originalPath));
            trashBin.remove(itemToRestore);
            trashIndex.remove(itemToRestore.originalPath);

            System.out.println(Colors.c(Colors.GREEN, "✓ Restored: " + filename + " to " + itemToRestore.originalPath));
        } catch (IOException e) {
            System.out.println(Colors.c(Colors.RED, "Error restoring file: " + e.getMessage()));
        }
    }

    /**
     * Restore file from trash by original path.
     */
    public static void restoreFromTrash(String originalPath) {
        // Normalize the path (remove backslash escapes)
        String normalizedPath = normalizePath(originalPath);
        
        TrashItem item = trashIndex.get(normalizedPath);
        if (item == null) {
            System.out.println(Colors.c(Colors.RED, "Item not found in trash: " + originalPath));
            System.out.println(Colors.c(Colors.YELLOW, "Tip: Use 'trash' command to see exact paths in trash"));
            return;
        }

        try {
            File trashedFile = new File(item.trashedPath);
            if (!trashedFile.exists()) {
                System.out.println(Colors.c(Colors.RED, "Trashed file no longer exists: " + item.trashedPath));
                return;
            }

            Files.move(trashedFile.toPath(), Paths.get(normalizedPath));
            trashBin.remove(item);
            trashIndex.remove(normalizedPath);

            System.out.println(Colors.c(Colors.GREEN, "✓ Restored: " + new File(normalizedPath).getName()));
        } catch (IOException e) {
            System.out.println(Colors.c(Colors.RED, "Error restoring file: " + e.getMessage()));
        }
    }

    /**
     * Permanently delete oldest item in trash (FIFO).
     */
    public static boolean deleteOldest() {
        TrashItem oldest = trashBin.poll();
        if (oldest == null) return false;

        try {
            Files.deleteIfExists(Paths.get(oldest.trashedPath));
            trashIndex.remove(oldest.originalPath);
            return true;
        } catch (IOException e) {
            System.out.println(Colors.c(Colors.RED, "Error deleting: " + e.getMessage()));
            return false;
        }
    }

    /**
     * Empty entire trash permanently.
     */
    public static void emptyTrash() {
        if (trashBin.isEmpty()) {
            System.out.println(Colors.c(Colors.YELLOW, "Trash is already empty."));
            return;
        }

        int count = 0;
        long freedSpace = 0;

        while (!trashBin.isEmpty()) {
            TrashItem item = trashBin.poll();
            try {
                Files.deleteIfExists(Paths.get(item.trashedPath));
                trashIndex.remove(item.originalPath);
                freedSpace += item.fileSize;
                count++;
            } catch (IOException e) {
                System.out.println(Colors.c(Colors.RED, "Error deleting: " + e.getMessage()));
            }
        }

        System.out.println(Colors.c(Colors.GREEN, String.format("✓ Trash emptied! Deleted %d items, freed %s", count, formatSize(freedSpace))));
    }

    /**
     * Get total size of trash.
     */
    public static void showTrashSize() {
        long totalSize = 0;
        for (TrashItem item : trashBin) {
            totalSize += item.fileSize;
        }
        System.out.println(Colors.c(Colors.CYAN, String.format("Trash size: %s (%d items)", formatSize(totalSize), trashBin.size())));
    }

    /**
     * Helper: Calculate total file size (including subdirectories).
     */
    private static long getFileSize(File file) {
        if (file.isFile()) return file.length();
        long size = 0;
        File[] files = file.listFiles();
        if (files != null) {
            for (File f : files) size += getFileSize(f);
        }
        return size;
    }

    /**
     * Helper: Format bytes to human-readable format.
     */
    private static String formatSize(long bytes) {
        if (bytes <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB"};
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        return String.format("%.2f %s", bytes / Math.pow(1024, digitGroups), units[digitGroups]);
    }

    public static int getTrashCount() {
        return trashBin.size();
    }

    /**
     * Helper: Normalize path by removing shell escape characters (backslashes before spaces).
     */
    private static String normalizePath(String path) {
        // Remove backslash escapes: "path\ with\ spaces" -> "path with spaces"
        return path.replace("\\ ", " ");
    }
}
