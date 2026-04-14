package utils;

import models.FileNode;
import models.FileMetadata;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Detects duplicate files by content hash using HashMap and HashSet.
 * Uses SHA-256 for reliable hash computation.
 */
public class DuplicateDetector {
    
    /**
     * Finds all duplicate files in a directory tree.
     * Returns a map where key is the hash and value is list of file paths with that hash.
     */
    public static Map<String, List<String>> findDuplicates(FileNode rootNode) {
        Map<String, List<String>> hashToFiles = new HashMap<>();
        Set<String> visitedPaths = new HashSet<>();
        
        traverseAndHash(rootNode, hashToFiles, visitedPaths);
        
        // Filter to only keep entries with duplicates (more than 1 file)
        Map<String, List<String>> duplicates = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : hashToFiles.entrySet()) {
            if (entry.getValue().size() > 1) {
                duplicates.put(entry.getKey(), entry.getValue());
            }
        }
        
        return duplicates;
    }
    
    /**
     * Recursively traverses the file tree and computes hashes for all files.
     */
    private static void traverseAndHash(FileNode node, Map<String, List<String>> hashToFiles, Set<String> visited) {
        if (node == null) return;
        
        // Avoid cycles in symlink structures
        if (visited.contains(node.absolutePath)) {
            return;
        }
        visited.add(node.absolutePath);
        
        // Hash all files in this directory
        if (node.files != null) {
            for (FileMetadata file : node.files.getAll()) {
                String filePath = node.absolutePath + "/" + file.filename;
                String hash = computeHash(file);
                hashToFiles.computeIfAbsent(hash, k -> new ArrayList<>()).add(filePath);
            }
        }
        
        // Recursively process child directories
        if (node.children != null) {
            for (FileNode child : node.children) {
                traverseAndHash(child, hashToFiles, visited);
            }
        }
    }
    
    /**
     * Computes SHA-256 hash of a file's metadata as a proxy for content.
     * In a real system, you'd hash the actual file content.
     */
    private static String computeHash(FileMetadata file) {
        try {
            // Hash: filename + size + type + creation time
            String content = file.filename + "|" + file.sizeBytes + "|" + file.type + "|" + file.createdAt;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (Exception e) {
            return "error-hash";
        }
    }
    
    /**
     * Converts byte array to hexadecimal string.
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }
    
    /**
     * Removes duplicate files, keeping only the first occurrence.
     * Returns the number of files deleted.
     */
    public static int removeDuplicates(FileNode rootNode) {
        Map<String, List<String>> duplicates = findDuplicates(rootNode);
        int deletedCount = 0;
        
        // For each group of duplicates, remove all but the first
        for (List<String> filePaths : duplicates.values()) {
            // Keep the first, delete the rest
            for (int i = 1; i < filePaths.size(); i++) {
                String pathToDelete = filePaths.get(i);
                FileNode parent = findParentNode(rootNode, pathToDelete);
                
                if (parent != null && parent.files != null) {
                    String filenameToDelete = getFilename(pathToDelete);
                    if (parent.files.remove(filenameToDelete)) {
                        deletedCount++;
                    }
                }
            }
        }
        
        return deletedCount;
    }
    
    /**
     * Finds the parent node of a file given its absolute path.
     */
    private static FileNode findParentNode(FileNode root, String childPath) {
        String parentPath = getParentPath(childPath);
        return findNodeByPath(root, parentPath);
    }
    
    /**
     * Finds a node by its absolute path.
     */
    private static FileNode findNodeByPath(FileNode root, String targetPath) {
        if (root.absolutePath.equals(targetPath)) {
            return root;
        }
        
        if (root.children != null) {
            for (FileNode child : root.children) {
                FileNode found = findNodeByPath(child, targetPath);
                if (found != null) {
                    return found;
                }
            }
        }
        
        return null;
    }
    
    /**
     * Extracts the parent path from an absolute path.
     */
    private static String getParentPath(String absolutePath) {
        int lastSlash = absolutePath.lastIndexOf("/");
        if (lastSlash <= 0) {
            return "/";
        }
        return absolutePath.substring(0, lastSlash);
    }
    
    /**
     * Extracts the filename from an absolute path.
     */
    private static String getFilename(String absolutePath) {
        int lastSlash = absolutePath.lastIndexOf("/");
        if (lastSlash < 0) {
            return absolutePath;
        }
        return absolutePath.substring(lastSlash + 1);
    }
    
    /**
     * Formats duplicate detection results for display.
     */
    public static void printDuplicates(Map<String, List<String>> duplicates) {
        if (duplicates.isEmpty()) {
            System.out.println(Colors.c(Colors.GREEN, "✓ No duplicate files found!"));
            return;
        }
        
        System.out.println();
        System.out.println(Colors.c(Colors.YELLOW, "Found " + duplicates.size() + " group(s) of duplicates:"));
        System.out.println(Colors.c(Colors.GRAY, "─".repeat(60)));
        
        int groupNum = 1;
        for (Map.Entry<String, List<String>> entry : duplicates.entrySet()) {
            System.out.println();
            System.out.println(Colors.c(Colors.CYAN, "Group " + groupNum + " (Hash: " + entry.getKey().substring(0, 12) + "...)"));
            System.out.println(Colors.c(Colors.GRAY, "  " + entry.getValue().size() + " duplicate file(s):"));
            
            for (int i = 0; i < entry.getValue().size(); i++) {
                String marker = (i == 0) ? "→ [KEPT]" : "  [DUPE]";
                System.out.println(Colors.c(Colors.GRAY, "  " + marker + " " + entry.getValue().get(i)));
            }
            
            groupNum++;
        }
        
        System.out.println();
        System.out.println(Colors.c(Colors.GRAY, "─".repeat(60)));
    }
}
