package datastructures;

import java.util.HashMap;
import java.util.Map;
import models.FileNode;
import utils.Colors;

/**
 * Manages directory hierarchy operations over a FileNode tree.
 * Implements HashMap for O(1) directory lookups and BST for O(log n) file searches.
 */
public class DirectoryTree {
    private FileNode root;
    private Map<String, FileNode> pathLookup;
    private BinarySearchTree globalSearchBst;

    /**
     * Creates the root directory node using the provided sandbox path.
     */
    public DirectoryTree(String sandboxPath) {
        this.root = new FileNode("/", sandboxPath, null);
        this.pathLookup = new HashMap<>();
        this.globalSearchBst = new BinarySearchTree();
        
        pathLookup.put(sandboxPath, this.root);
        globalSearchBst.insert("/", sandboxPath);
    }

    public FileNode getRoot() {
        return root;
    }

    public BinarySearchTree getGlobalSearchBst() {
        return globalSearchBst;
    }

    /** Creates and inserts a new directory node. */
    public FileNode insertDirectory(FileNode parent, String name, String absolutePath) {
        FileNode newDir = new FileNode(name, absolutePath, parent);
        parent.children.add(newDir);
        pathLookup.put(absolutePath, newDir);
        globalSearchBst.insert(name, absolutePath);
        return newDir;
    }

    /** Tracks a file inside the global BST. */
    public void trackFile(String name, String absolutePath) {
        globalSearchBst.insert(name, absolutePath);
    }

    /** Untracks a file from the global BST. */
    public void untrackFile(String name, String absolutePath) {
        globalSearchBst.remove(name, absolutePath);
    }

    /** Removes a directory node from the tree and lookup map. */
    public void removeDirectory(FileNode node) {
        if (node == root || node == null) return;
        
        // Remove all children recursively from lookups
        removeLookupsRecursive(node);
        
        // Remove from parent
        if (node.parent != null) {
            node.parent.children.remove(node);
        }
    }

    private void removeLookupsRecursive(FileNode node) {
        pathLookup.remove(node.absolutePath);
        globalSearchBst.remove(node.name, node.absolutePath);
        
        // Remove all files in this directory from the BST
        if (node.files != null) {
            for (models.FileMetadata file : node.files.getAll()) {
                globalSearchBst.remove(file.filename, node.absolutePath + java.io.File.separator + file.filename);
            }
        }
        
        // Recurse for child directories
        for (FileNode child : node.children) {
            removeLookupsRecursive(child);
        }
    }

    /**
     * Finds a directory node by absolute path. Uses O(1) HashMap lookup.
     */
    public FileNode findNode(String absolutePath) {
        if (absolutePath == null) {
            return null;
        }
        return pathLookup.get(absolutePath);
    }

    /**
     * Prints an ASCII representation of directories and files recursively.
     */
    public void printTree(FileNode node, String prefix, boolean isLast) {
        if (node == null) {
            return;
        }

        if (node.parent == null && "".equals(prefix)) {
            System.out.println(Colors.c(Colors.BLUE + Colors.BOLD, "/"));
        } else {
            System.out.println(prefix.replace("│", Colors.c(Colors.GRAY, "│"))
                    + Colors.c(Colors.GRAY, isLast ? "└── " : "├── ")
                    + Colors.c(Colors.BLUE + Colors.BOLD, node.name + "/"));
        }

        String childPrefix = prefix + (isLast ? "    " : "│   ");
        int childCount = node.children.size();

        int fileCount = 0;
        if (node.files != null) {
            fileCount = node.files.getAll().size();
        }

        for (int i = 0; i < childCount; i++) {
            boolean childIsLast = (i == childCount - 1) && (fileCount == 0);
            printTree(node.children.get(i), childPrefix, childIsLast);
        }

        if (node.files != null) {
            for (int i = 0; i < fileCount; i++) {
                boolean fileIsLast = i == fileCount - 1;
                System.out.println(childPrefix.replace("│", Colors.c(Colors.GRAY, "│"))
                        + Colors.c(Colors.GRAY, fileIsLast ? "└── " : "├── ")
                        + Colors.c(Colors.WHITE, node.files.getAll().get(i).filename));
            }
        }
    }

    /**
     * Prints the full tree starting from the root node.
     */
    public void printTreeFromRoot() {
        printTree(root, "", true);
    }
}
