package datastructures;

import java.util.ArrayList;
import java.util.List;

/**
 * A Custom Binary Search Tree for fast O(log n) file name lookups.
 * Note: Not self-balancing. Depending on insertion order, worst-case lookup can be O(n).
 * Handles duplicate file names by maintaining a list of absolute paths.
 */
public class BinarySearchTree {

    private static class BSTNode {
        String name;
        List<String> paths;
        BSTNode left, right;

        public BSTNode(String name, String path) {
            this.name = name;
            this.paths = new ArrayList<>();
            this.paths.add(path);
        }
    }

    private BSTNode root;

    public BinarySearchTree() {
        this.root = null;
    }

    /** Inserts a file/directory name and its absolute path into the BST. */
    public void insert(String name, String path) {
        root = insertRec(root, name, path);
    }

    private BSTNode insertRec(BSTNode node, String name, String path) {
        if (node == null) {
            return new BSTNode(name, path);
        }
        int cmp = name.compareToIgnoreCase(node.name);
        if (cmp < 0) {
            node.left = insertRec(node.left, name, path);
        } else if (cmp > 0) {
            node.right = insertRec(node.right, name, path);
        } else {
            // Duplicate name, just add the new path if it doesn't exist
            if (!node.paths.contains(path)) {
                node.paths.add(path);
            }
        }
        return node;
    }

    /** Removes a specific path for a given name from the BST. */
    public void remove(String name, String path) {
        root = removeRec(root, name, path);
    }

    private BSTNode removeRec(BSTNode node, String name, String path) {
        if (node == null) {
            return null;
        }
        int cmp = name.compareToIgnoreCase(node.name);
        if (cmp < 0) {
            node.left = removeRec(node.left, name, path);
        } else if (cmp > 0) {
            node.right = removeRec(node.right, name, path);
        } else {
            // Found the node
            node.paths.remove(path);
            if (node.paths.isEmpty()) {
                // If no paths remain, remove the node entirely
                if (node.left == null) {
                    return node.right;
                } else if (node.right == null) {
                    return node.left;
                }
                // Node with two children: Get the inorder successor (smallest in the right subtree)
                BSTNode minNode = findMin(node.right);
                node.name = minNode.name;
                node.paths = minNode.paths;
                // Delete the inorder successor
                node.right = removeAllRec(node.right, minNode.name);
            }
        }
        return node;
    }

    /** Removes a node completely by name (helper for node deletion). */
    private BSTNode removeAllRec(BSTNode node, String name) {
        if (node == null) return null;
        int cmp = name.compareToIgnoreCase(node.name);
        if (cmp < 0) {
            node.left = removeAllRec(node.left, name);
        } else if (cmp > 0) {
            node.right = removeAllRec(node.right, name);
        } else {
            if (node.left == null) return node.right;
            if (node.right == null) return node.left;
            BSTNode minNode = findMin(node.right);
            node.name = minNode.name;
            node.paths = minNode.paths;
            node.right = removeAllRec(node.right, minNode.name);
        }
        return node;
    }

    private BSTNode findMin(BSTNode node) {
        while (node.left != null) {
            node = node.left;
        }
        return node;
    }

    /** Searches for a name and returns all its absolute paths. */
    public List<String> search(String name) {
        BSTNode res = searchRec(root, name);
        return res != null ? res.paths : new ArrayList<>();
    }

    private BSTNode searchRec(BSTNode node, String name) {
        if (node == null) {
            return null;
        }
        int cmp = name.compareToIgnoreCase(node.name);
        if (cmp == 0) {
            return node;
        } else if (cmp < 0) {
            return searchRec(node.left, name);
        } else {
            return searchRec(node.right, name);
        }
    }
}
