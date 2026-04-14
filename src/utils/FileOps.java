package utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * File Copy and Move operations with tree traversal and deep copy support.
 * Concepts: Tree traversal, deep copy, block allocation
 */
public class FileOps {

    /**
     * Copy a file or directory recursively.
     * @param source Source file/directory path
     * @param destination Destination path
     * @param recursive True for directory copy
     */
    public static void copy(String source, String destination, boolean recursive) {
        File sourceFile = new File(source);
        File destFile = new File(destination);

        if (!sourceFile.exists()) {
            System.out.println(Colors.c(Colors.RED, "Source not found: " + source));
            return;
        }

        if (sourceFile.getAbsolutePath().equals(destFile.getAbsolutePath())) {
            System.out.println(Colors.c(Colors.RED, "Source and destination are the same."));
            return;
        }

        try {
            if (sourceFile.isFile()) {
                copyFile(sourceFile, destFile);
            } else if (sourceFile.isDirectory() && recursive) {
                copyDirectory(sourceFile, destFile);
            } else if (sourceFile.isDirectory() && !recursive) {
                System.out.println(Colors.c(Colors.YELLOW, "Use 'cp -r' to copy directories."));
                return;
            }
        } catch (IOException e) {
            System.out.println(Colors.c(Colors.RED, "Copy error: " + e.getMessage()));
        }
    }

    /**
     * Move a file or directory (rename).
     * @param source Source file/directory path
     * @param destination Destination path
     */
    public static void move(String source, String destination) {
        File sourceFile = new File(source);
        File destFile = new File(destination);

        if (!sourceFile.exists()) {
            System.out.println(Colors.c(Colors.RED, "Source not found: " + source));
            return;
        }

        if (sourceFile.getAbsolutePath().equals(destFile.getAbsolutePath())) {
            System.out.println(Colors.c(Colors.RED, "Source and destination are the same."));
            return;
        }

        try {
            Files.move(sourceFile.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            String icon = sourceFile.isDirectory() ? "📁" : "📄";
            System.out.println(Colors.c(Colors.GREEN, icon + " Moved: " + sourceFile.getName() + " → " + destFile.getName()));
        } catch (IOException e) {
            System.out.println(Colors.c(Colors.RED, "Move error: " + e.getMessage()));
        }
    }

    /**
     * Copy a single file.
     */
    private static void copyFile(File source, File destination) throws IOException {
        File destParent = destination.getParentFile();
        if (destParent != null && !destParent.exists()) {
            destParent.mkdirs();
        }

        long fileSize = source.length();
        Files.copy(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
        System.out.println(Colors.c(Colors.GREEN, "✓ Copied: " + source.getName() + " (" + formatSize(fileSize) + ")"));
    }

    /**
     * Recursively copy a directory and all its contents (tree traversal).
     */
    private static void copyDirectory(File source, File destination) throws IOException {
        if (!destination.exists()) {
            destination.mkdirs();
        }

        long totalSize = copyDirectoryRecursive(source, destination, 0);
        System.out.println(Colors.c(Colors.GREEN, "✓ Directory copied: " + source.getName() + " (" + formatSize(totalSize) + ")"));
    }

    /**
     * Recursive helper for directory copy (tree traversal).
     */
    private static long copyDirectoryRecursive(File source, File destination, long totalSize) throws IOException {
        File[] files = source.listFiles();
        if (files == null) return totalSize;

        for (File file : files) {
            File destFile = new File(destination, file.getName());

            if (file.isDirectory()) {
                destFile.mkdirs();
                totalSize = copyDirectoryRecursive(file, destFile, totalSize);
            } else {
                Files.copy(file.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                totalSize += file.length();
            }
        }

        return totalSize;
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
}
