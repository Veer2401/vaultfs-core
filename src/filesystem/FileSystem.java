package filesystem;

import auth.AuthManager;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import sync.FirestoreSync;
import utils.Colors;
import utils.Logger;

/**
 * Core engine that connects data structures and sandbox disk operations.
 */
public class FileSystem {
    private datastructures.DirectoryTree tree;
    private datastructures.FileHeap globalHeap;
    private datastructures.DiskSimulator diskSimulator;
    private DiskService diskService;
    private SearchService searchService;
    private Map<String, Integer> fileBlockIndex;
    public models.FileNode currentDirectory;
    private utils.JsonExporter exporter;
    private Stack<String> historyStack;
    private final ExecutorService syncExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "vaultfs-sync");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean syncPending = new AtomicBoolean(false);

    /** Initializes the tree from the current working directory and syncs from disk. */
    public FileSystem() {
        String startPath = new File(System.getProperty("user.dir")).getAbsolutePath();
        this.tree = new datastructures.DirectoryTree(startPath);
        this.globalHeap = new datastructures.FileHeap();
        this.diskSimulator = new datastructures.DiskSimulator();
        this.diskService = new DiskService();
        this.searchService = new SearchService();
        this.fileBlockIndex = new HashMap<>();
        this.currentDirectory = this.tree.getRoot();
        this.exporter = new utils.JsonExporter(System.getProperty("user.dir") + File.separator + "state.json");
        this.historyStack = new Stack<>();
        refreshState();
    }

    /** Returns the current directory path for prompt display. */
    public String getCurrentPath() {
        return currentDirectory.absolutePath;
    }

    /** Prints the absolute path of the current directory. */
    public void pwd() {
        System.out.println(Colors.c(Colors.CYAN, currentDirectory.absolutePath));
        exportState();
    }

    /** Changes current directory using relative or absolute paths from disk. */
    public void cd(String path) {
        if ("-".equals(path)) {
            if (historyStack.isEmpty()) {
                System.out.println(Colors.c(Colors.RED, "No previous directory in history."));
                exportState();
                return;
            }
            String prevPath = historyStack.peek();
            File targetDir = resolveDirectory(prevPath);
            if (targetDir != null) {
                models.FileNode resolved = ensureNodeForDirectory(targetDir);
                if (resolved != null) {
                    historyStack.pop();
                    currentDirectory = resolved;
                    refreshTreeAndHeap();
                }
            }
            exportState();
            return;
        }

        File targetDir = resolveDirectory(path);
        if (targetDir == null) {
            System.out.println(Colors.c(Colors.RED, "Directory not found: " + path));
            exportState();
            return;
        }

        models.FileNode resolved = ensureNodeForDirectory(targetDir);
        if (resolved == null) {
            System.out.println(Colors.c(Colors.RED, "Unable to access directory: " + path));
            exportState();
            return;
        }

        // Push current directory to history stack before changing
        historyStack.push(currentDirectory.absolutePath);

        currentDirectory = resolved;
        refreshTreeAndHeap();
        exportState();
    }

    /** Creates a new child directory in memory and on disk. */
    public void mkdir(String name) {
        if (!isSimpleName(name)) {
            System.out.println(Colors.c(Colors.RED, "Invalid directory name: " + name));
            exportState();
            return;
        }

        syncDirectoryFromDisk(currentDirectory, false);

        if (currentDirectory.getChild(name) != null) {
            System.out.println(Colors.c(Colors.RED, "Directory already exists: " + name));
            exportState();
            return;
        }

        if (currentDirectory.fileIndex.contains(name)) {
            System.out.println(Colors.c(Colors.RED, "A file with that name already exists: " + name));
            exportState();
            return;
        }

        String absolutePath = currentDirectory.absolutePath + File.separator + name;
        if (!isWithinBoundary(currentDirectory.absolutePath, absolutePath)) {
            System.out.println(Colors.c(Colors.RED, "Invalid path: escapes current directory boundary."));
            exportState();
            return;
        }
        boolean created = new File(absolutePath).mkdir();
        if (!created) {
            System.out.println(Colors.c(Colors.RED, "Failed to create directory on disk: " + name));
            exportState();
            return;
        }

        tree.insertDirectory(currentDirectory, name, absolutePath);
        refreshTreeAndHeap();
        System.out.println("Directory '" + Colors.c(Colors.BLUE, name) + "' "
                + Colors.c(Colors.GREEN, "created successfully") + ".");
        exportState();
    }

    /** Removes a child directory with optional forced recursive deletion. */
    public void rmdir(String name, boolean force) {
        syncDirectoryFromDisk(currentDirectory, false);

        models.FileNode node = currentDirectory.getChild(name);
        if (node == null) {
            System.out.println(Colors.c(Colors.RED, "Directory not found: " + name));
            exportState();
            return;
        }

        File targetDir = new File(node.absolutePath);
        if (!targetDir.exists() || !targetDir.isDirectory()) {
            System.out.println(Colors.c(Colors.RED, "Directory not found on disk: " + name));
            currentDirectory.removeChild(node);
            exportState();
            return;
        }

        String[] entries = targetDir.list();
        boolean hasDiskContents = entries != null && entries.length > 0;

        if (!force && hasDiskContents) {
            System.out.println(Colors.c(Colors.RED, "Directory not empty. Use rmdir -f " + name));
            exportState();
            return;
        }

        // Move to trash instead of permanently deleting
        utils.TrashManager.moveToTrash(node.absolutePath, true);

        removeHeapEntriesUnderNode(node);
        tree.removeDirectory(node);
        refreshTreeAndHeap();
        System.out.println("Directory '" + Colors.c(Colors.BLUE, name) + "' "
            + Colors.c(Colors.GREEN, "removed successfully") + ".");
        exportState();
    }

    /** Renames a child directory in memory and on disk. */
    public void renameDirectory(String oldName, String newName) {
        if (!isSimpleName(oldName) || !isSimpleName(newName)) {
            System.out.println(Colors.c(Colors.RED, "Invalid directory name."));
            exportState();
            return;
        }

        syncDirectoryFromDisk(currentDirectory, false);

        models.FileNode node = currentDirectory.getChild(oldName);
        if (node == null) {
            System.out.println(Colors.c(Colors.RED, "Directory not found: " + oldName));
            exportState();
            return;
        }

        if (currentDirectory.getChild(newName) != null || currentDirectory.fileIndex.contains(newName)) {
            System.out.println(Colors.c(Colors.RED, "Name already exists: " + newName));
            exportState();
            return;
        }

        String oldPath = node.absolutePath;
        String newPath = currentDirectory.absolutePath + File.separator + newName;
        if (!isWithinBoundary(currentDirectory.absolutePath, newPath)) {
            System.out.println(Colors.c(Colors.RED, "Invalid path: escapes current directory boundary."));
            exportState();
            return;
        }
        if (new File(newPath).exists()) {
            System.out.println(Colors.c(Colors.RED, "Target already exists on disk: " + newName));
            exportState();
            return;
        }

        boolean renamed = new File(oldPath).renameTo(new File(newPath));
        if (!renamed) {
            System.out.println(Colors.c(Colors.RED, "Failed to rename directory: " + oldName));
            exportState();
            return;
        }

        node.name = newName;
        updateNodePathRecursively(node, newPath);
        refreshTreeAndHeap();
        System.out.println("Directory renamed successfully.");
        exportState();
    }

    /** Creates an empty file and records real on-disk metadata. */
    public void createFile(String filename) {
        if (!isSimpleName(filename)) {
            System.out.println(Colors.c(Colors.RED, "Invalid file name: " + filename));
            exportState();
            return;
        }

        syncDirectoryFromDisk(currentDirectory, false);

        if (currentDirectory.fileIndex.contains(filename)) {
            System.out.println(Colors.c(Colors.RED, "File already exists: " + filename));
            exportState();
            return;
        }

        if (currentDirectory.getChild(filename) != null) {
            System.out.println(Colors.c(Colors.RED, "A directory with that name already exists: " + filename));
            exportState();
            return;
        }

        String filePath = currentDirectory.absolutePath + File.separator + filename;
        if (!isWithinBoundary(currentDirectory.absolutePath, filePath)) {
            System.out.println(Colors.c(Colors.RED, "Invalid path: escapes current directory boundary."));
            exportState();
            return;
        }
        File diskFile = new File(filePath);
        try {
            boolean created = diskFile.createNewFile();
            if (!created) {
                System.out.println(Colors.c(Colors.RED, "Failed to create file on disk: " + filename));
                exportState();
                return;
            }
        } catch (IOException e) {
            System.out.println(Colors.c(Colors.RED, "Failed to create file: " + e.getMessage()));
            exportState();
            return;
        }

        models.FileMetadata m = diskService.metadataFromDiskFile(diskFile, diskSimulator, -1);
        currentDirectory.files.add(m);
        currentDirectory.fileIndex.put(filename, m);
        tree.trackFile(filename, filePath);
        fileBlockIndex.put(filePath, m.startBlockId);
        globalHeap.insert(filename, filePath, m.sizeBytes);
        
        System.out.println("File '" + Colors.c(Colors.WHITE, filename) + "' "
            + Colors.c(Colors.GREEN, "created successfully")
            + " with size " + Colors.c(Colors.CYAN, m.formattedSize()) + ".");
        if (m.startBlockId != -1) {
            System.out.println(Colors.c(Colors.GRAY, "Allocated blocks: " + diskSimulator.getBlockChain(m.startBlockId).size() 
                + " (Start: " + m.startBlockId + ")"));
        }
        exportState();
    }

    /** Deletes a file from list/map/heap and removes it from disk. */
    public void deleteFile(String filename) {
        syncDirectoryFromDisk(currentDirectory, false);

        if (!currentDirectory.fileIndex.contains(filename)) {
            System.out.println(Colors.c(Colors.RED, "File not found: " + filename));
            exportState();
            return;
        }

        models.FileMetadata meta = currentDirectory.fileIndex.get(filename);
        if (meta != null && meta.startBlockId != -1) {
            diskSimulator.freeFile(meta.startBlockId);
        }

        String filePath = currentDirectory.absolutePath + File.separator + filename;
        if (!isWithinBoundary(currentDirectory.absolutePath, filePath)) {
            System.out.println(Colors.c(Colors.RED, "Invalid path: escapes current directory boundary."));
            exportState();
            return;
        }
        File diskFile = new File(filePath);
        if (!diskFile.exists() || !diskFile.isFile()) {
            System.out.println(Colors.c(Colors.RED, "File not found on disk: " + filename));
            currentDirectory.files.remove(filename);
            currentDirectory.fileIndex.remove(filename);
            globalHeap.remove(filePath);
            exportState();
            return;
        }

        // Move to trash instead of permanently deleting
        utils.TrashManager.moveToTrash(filePath, false);

        currentDirectory.files.remove(filename);
        currentDirectory.fileIndex.remove(filename);
        tree.untrackFile(filename, filePath);
        fileBlockIndex.remove(filePath);
        globalHeap.remove(filePath);
        exportState();
    }

    /** Renames a file and synchronizes linked list, hash map, heap, and disk. */
    public void renameFile(String oldName, String newName) {
        if (!isSimpleName(oldName) || !isSimpleName(newName)) {
            System.out.println(Colors.c(Colors.RED, "Invalid file name."));
            exportState();
            return;
        }

        syncDirectoryFromDisk(currentDirectory, false);

        models.FileMetadata oldMetadata = currentDirectory.fileIndex.get(oldName);
        if (oldMetadata == null) {
            System.out.println(Colors.c(Colors.RED, "File not found: " + oldName));
            exportState();
            return;
        }

        if (currentDirectory.fileIndex.contains(newName) || currentDirectory.getChild(newName) != null) {
            System.out.println(Colors.c(Colors.RED, "Name already exists: " + newName));
            exportState();
            return;
        }

        String oldPath = currentDirectory.absolutePath + File.separator + oldName;
        String newPath = currentDirectory.absolutePath + File.separator + newName;
        if (!isWithinBoundary(currentDirectory.absolutePath, oldPath)
                || !isWithinBoundary(currentDirectory.absolutePath, newPath)) {
            System.out.println(Colors.c(Colors.RED, "Invalid path: escapes current directory boundary."));
            exportState();
            return;
        }
        File oldDiskFile = new File(oldPath);
        File newDiskFile = new File(newPath);
        if (!oldDiskFile.exists() || !oldDiskFile.isFile()) {
            System.out.println(Colors.c(Colors.RED, "File not found on disk: " + oldName));
            exportState();
            return;
        }
        if (newDiskFile.exists()) {
            System.out.println(Colors.c(Colors.RED, "Target already exists on disk: " + newName));
            exportState();
            return;
        }

        boolean renamed = new File(oldPath).renameTo(new File(newPath));
        if (!renamed) {
            System.out.println(Colors.c(Colors.RED, "Failed to rename file: " + oldName));
            exportState();
            return;
        }

        currentDirectory.files.remove(oldName);
        currentDirectory.fileIndex.remove(oldName);

        tree.untrackFile(oldName, oldPath);
        Integer existingBlockId = fileBlockIndex.remove(oldPath);

        models.FileMetadata newMetadata = diskService.metadataFromDiskFile(
            newDiskFile,
            diskSimulator,
            existingBlockId != null ? existingBlockId : -1
        );
        newMetadata.createdAt = oldMetadata.createdAt;
        currentDirectory.files.add(newMetadata);
        currentDirectory.fileIndex.put(newName, newMetadata);

        tree.trackFile(newName, newPath);
        fileBlockIndex.put(newPath, newMetadata.startBlockId);

        globalHeap.remove(oldPath);
        globalHeap.insert(newName, newPath, newMetadata.sizeBytes);

        System.out.println("File renamed successfully.");
        exportState();
    }

    /** Lists files and directories in current folder, optionally sorting them or showing details. */
    public void ls(boolean detailed, String sortFlag) {
        syncDirectoryFromDisk(currentDirectory, false);

        int childCount = currentDirectory.children.size();
        int fileCount = currentDirectory.files != null ? currentDirectory.files.getAll().size() : 0;

        if (childCount == 0 && fileCount == 0) {
            System.out.println(Colors.c(Colors.GRAY, "Directory is empty"));
            exportState();
            return;
        }

        List<models.FileNode> dirs = new ArrayList<>(currentDirectory.children);
        List<models.FileMetadata> files = new ArrayList<>(currentDirectory.files.getAll());

        // Sort if flag is provided
        if ("-name".equals(sortFlag)) {
            dirs.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
            files.sort((a, b) -> a.filename.compareToIgnoreCase(b.filename));
        } else if ("-size".equals(sortFlag)) {
            // Directories don't have a direct size property in this model, so we sort files only
            files.sort((a, b) -> Long.compare(b.sizeBytes, a.sizeBytes)); // Descending
        } else if ("-date".equals(sortFlag)) {
            files.sort((a, b) -> b.modifiedAt.compareTo(a.modifiedAt)); // Newest first
        }

        if (detailed) {
            System.out.println(String.format("%-28s %-11s %-9s %s",
                    Colors.c(Colors.WHITE + Colors.BOLD, "NAME"),
                    Colors.c(Colors.WHITE + Colors.BOLD, "SIZE"),
                    Colors.c(Colors.WHITE + Colors.BOLD, "TYPE"),
                    Colors.c(Colors.WHITE + Colors.BOLD, "MODIFIED")));

            for (models.FileNode child : dirs) {
                System.out.println(String.format("%-37s %-20s %-18s %s",
                        Colors.c(Colors.BLUE + Colors.BOLD, child.name + "/"),
                        Colors.c(Colors.GRAY, "--"),
                        Colors.c(Colors.GRAY, "dir"),
                        Colors.c(Colors.GRAY, "--")));
            }

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            for (models.FileMetadata m : files) {
                System.out.println(String.format("%-37s %-20s %-18s %s",
                        Colors.c(Colors.WHITE, m.filename),
                        Colors.c(Colors.CYAN, m.formattedSize()),
                        Colors.c(Colors.GRAY, m.type),
                        Colors.c(Colors.GRAY, m.modifiedAt.format(formatter))));
            }
        } else {
            for (models.FileNode child : dirs) {
                System.out.println(Colors.c(Colors.BLUE + Colors.BOLD, child.name + "/"));
            }
            for (models.FileMetadata m : files) {
                System.out.println(Colors.c(Colors.WHITE, m.filename));
            }
        }
        exportState();
    }

    /** Prints complete metadata information for a file in the current directory. */
    public void info(String filename) {
        syncDirectoryFromDisk(currentDirectory, false);

        models.FileMetadata m = currentDirectory.fileIndex.get(filename);
        if (m == null) {
            System.out.println(Colors.c(Colors.RED, "File not found"));
            exportState();
            return;
        }

        File diskFile = new File(currentDirectory.absolutePath + File.separator + filename);
        if (diskFile.exists() && diskFile.isFile()) {
            m.sizeBytes = diskFile.length();
            m.modifiedAt = LocalDateTime.ofInstant(Instant.ofEpochMilli(diskFile.lastModified()), ZoneId.systemDefault());
        }

        System.out.println(Colors.c(Colors.GRAY, "Filename: ") + Colors.c(Colors.WHITE, m.filename));
        System.out.println(Colors.c(Colors.GRAY, "Size: ") + Colors.c(Colors.WHITE, m.sizeBytes + " bytes"));
        System.out.println(Colors.c(Colors.GRAY, "Type: ") + Colors.c(Colors.WHITE, m.type));
        System.out.println(Colors.c(Colors.GRAY, "CreatedAt: ") + Colors.c(Colors.WHITE, String.valueOf(m.createdAt)));
        System.out.println(Colors.c(Colors.GRAY, "ModifiedAt: ") + Colors.c(Colors.WHITE, String.valueOf(m.modifiedAt)));
        if (m.startBlockId != -1) {
            System.out.println(Colors.c(Colors.GRAY, "Disk Blocks: ") + Colors.c(Colors.WHITE, diskSimulator.getBlockChain(m.startBlockId).size() + " allocated"));
            System.out.println(Colors.c(Colors.GRAY, "Start Block ID: ") + Colors.c(Colors.WHITE, String.valueOf(m.startBlockId)));
        }
        exportState();
    }

    /** Finds all matching filenames in the tree using the O(log n) BST search. */
    public void find(String filename) {
        syncDirectoryFromDisk(currentDirectory, true);

        List<String> results = tree.getGlobalSearchBst().search(filename);

        if (results.isEmpty()) {
            System.out.println(Colors.c(Colors.RED, "No file named '" + filename + "' found"));
        } else {
            for (String path : results) {
                System.out.println(Colors.c(Colors.CYAN, "Found: " + path));
            }
        }
        exportState();
    }

    /** Performs DFS and prints files whose type matches the requested extension. */
    public void searchByType(String type) {
        syncDirectoryFromDisk(currentDirectory, true);

        List<String> results = new ArrayList<>();
        searchService.collectByType(currentDirectory, type, results);

        if (results.isEmpty()) {
            System.out.println(Colors.c(Colors.RED, "No files of type '." + type + "' found"));
        } else {
            for (String line : results) {
                System.out.println(line);
            }
        }
        exportState();
    }

    /** Prints the directory tree from a resolved absolute path node. */
    public void tree(String path) {
        if (path == null || path.isEmpty()) {
            System.out.println(Colors.c(Colors.RED, "Path is required. Usage: tree <path>"));
            exportState();
            return;
        }

        File targetDir = resolveDirectory(path);
        if (targetDir == null) {
            System.out.println(Colors.c(Colors.RED, "Directory not found: " + path));
            exportState();
            return;
        }

        models.FileNode node = ensureNodeForDirectory(targetDir);
        if (node == null) {
            System.out.println(Colors.c(Colors.RED, "Directory not found: " + path));
        } else {
            syncDirectoryFromDisk(node, true);
            this.tree.printTree(node, "", true);
        }
        exportState();
    }

    /** Prints ranked top-k largest files globally or within a subtree path scope. */
    public void topK(int k, String path) {
        if (k <= 0) {
            System.out.println(Colors.c(Colors.RED, "k must be greater than 0"));
            exportState();
            return;
        }

        ArrayList<datastructures.FileHeap.HeapEntry> results;

        if (path == null || path.isEmpty()) {
            System.out.println(Colors.c(Colors.RED, "Path is required. Usage: topk <k> <path>"));
            exportState();
            return;
        } else {
            File targetDir = resolveDirectory(path);
            if (targetDir == null) {
                System.out.println(Colors.c(Colors.RED, "Directory not found: " + path));
                exportState();
                return;
            }

            datastructures.FileHeap tempHeap = new datastructures.FileHeap();
            diskService.populateHeapFromDisk(targetDir, tempHeap);
            results = tempHeap.topK(k);
        }

        if (results.isEmpty()) {
            System.out.println(Colors.c(Colors.RED, "No files found"));
            exportState();
            return;
        }

        for (int i = 0; i < results.size(); i++) {
            datastructures.FileHeap.HeapEntry entry = results.get(i);
            String rank = Colors.c(Colors.YELLOW, (i + 1) + ".");
            String file = Colors.c(Colors.WHITE, entry.filename);
            String size = Colors.c(Colors.CYAN, searchService.formatSize(entry.sizeBytes));
            System.out.println(rank + " " + file + " — " + size + " — " + entry.absolutePath);
        }
        exportState();
    }

    /** Resolves a user path to a real directory from current location or absolute root. */
    private File resolveDirectory(String path) {
        if (path == null || path.trim().isEmpty()) {
            return null;
        }

        if ("/".equals(path)) {
            Path current = Paths.get(currentDirectory.absolutePath);
            Path rootPath = current.getRoot();
            if (rootPath == null) {
                return null;
            }
            File root = rootPath.toFile();
            if (root.exists() && root.isDirectory()) {
                return root;
            }
            return null;
        }

        Path candidate = Paths.get(path);
        if (!candidate.isAbsolute()) {
            candidate = Paths.get(currentDirectory.absolutePath).resolve(path);
        }

        File dir = candidate.normalize().toFile();
        if (!dir.exists() || !dir.isDirectory()) {
            return null;
        }
        return dir;
    }

    /** Ensures a directory path exists in memory tree, resetting root when needed. */
    private models.FileNode ensureNodeForDirectory(File directory) {
        String targetPath = diskService.normalizePath(directory.getAbsolutePath());
        models.FileNode rootNode = tree.getRoot();
        String rootPath = diskService.normalizePath(rootNode.absolutePath);

        if (!isSameOrDescendant(rootPath, targetPath)) {
            this.tree = new datastructures.DirectoryTree(targetPath);
            this.currentDirectory = this.tree.getRoot();
            syncDirectoryFromDisk(this.currentDirectory, false);
            return this.currentDirectory;
        }

        if (targetPath.equals(rootPath)) {
            syncDirectoryFromDisk(rootNode, false);
            return rootNode;
        }

        Path root;
        Path target;
        try {
            root = Paths.get(rootPath);
            target = Paths.get(targetPath);
        } catch (Exception e) {
            this.tree = new datastructures.DirectoryTree(targetPath);
            this.currentDirectory = this.tree.getRoot();
            syncDirectoryFromDisk(this.currentDirectory, false);
            return this.currentDirectory;
        }

        Path relative;
        try {
            relative = root.relativize(target);
        } catch (IllegalArgumentException e) {
            this.tree = new datastructures.DirectoryTree(targetPath);
            this.currentDirectory = this.tree.getRoot();
            syncDirectoryFromDisk(this.currentDirectory, false);
            return this.currentDirectory;
        }

        models.FileNode walker = rootNode;
        for (Path segment : relative) {
            String childName = segment.toString();
            syncDirectoryFromDisk(walker, false);
            models.FileNode child = walker.getChild(childName);
            if (child == null) {
                String childPath = diskService.normalizePath(new File(walker.absolutePath, childName).getAbsolutePath());
                File childDir = new File(childPath);
                if (!childDir.exists() || !childDir.isDirectory()) {
                    return null;
                }
                child = tree.insertDirectory(walker, childName, childPath);
            }
            walker = child;
        }

        syncDirectoryFromDisk(walker, false);
        return walker;
    }

    /** Synchronizes one node with on-disk files/directories and can recurse into children. */
    private void syncDirectoryFromDisk(models.FileNode node, boolean recursive) {
        if (node == null) {
            return;
        }

        File dir = new File(node.absolutePath);
        if (!dir.exists() || !dir.isDirectory()) {
            return;
        }

        File[] entries = dir.listFiles();
        if (entries == null) {
            entries = new File[0];
        }

        Map<String, models.FileNode> existingChildren = new HashMap<>();
        for (models.FileNode child : node.children) {
            existingChildren.put(child.name, child);
        }

        Set<String> diskDirectoryNames = new HashSet<>();
        Set<String> diskFilePaths = new HashSet<>();
        ArrayList<models.FileMetadata> diskFiles = new ArrayList<>();

        for (File entry : entries) {
            if (entry.isDirectory()) {
                String childName = entry.getName();
                diskDirectoryNames.add(childName);
                models.FileNode child = existingChildren.get(childName);
                String childPath = diskService.normalizePath(entry.getAbsolutePath());
                if (child == null) {
                    child = tree.insertDirectory(node, childName, childPath);
                } else {
                    child.absolutePath = childPath;
                    child.parent = node;
                }

                if (recursive) {
                    syncDirectoryFromDisk(child, true);
                }
            } else if (entry.isFile()) {
                String filePath = diskService.normalizePath(entry.getAbsolutePath());
                Integer existingBlockId = fileBlockIndex.get(filePath);
                // If file is already tracked (even with -1 = disk full), skip re-allocation
                int blockHint = existingBlockId != null ? existingBlockId : -1;
                boolean alreadyTracked = fileBlockIndex.containsKey(filePath);
                models.FileMetadata metadata = diskService.metadataFromDiskFile(
                        entry,
                        diskSimulator,
                        alreadyTracked ? Math.max(blockHint, 0) == blockHint ? blockHint : -2 : -1
                );
                diskFiles.add(metadata);
                diskFilePaths.add(filePath);
                fileBlockIndex.put(filePath, metadata.startBlockId);
                tree.trackFile(metadata.filename, filePath);
            }
        }

        if (node.files != null) {
            for (models.FileMetadata existing : node.files.getAll()) {
                String previousPath = diskService.normalizePath(node.absolutePath + File.separator + existing.filename);
                if (!diskFilePaths.contains(previousPath)) {
                    tree.untrackFile(existing.filename, previousPath);
                    Integer blockId = fileBlockIndex.remove(previousPath);
                    if (blockId != null && blockId >= 0) {
                        diskSimulator.freeFile(blockId);
                    }
                }
            }
        }

        for (int i = node.children.size() - 1; i >= 0; i--) {
            models.FileNode child = node.children.get(i);
            if (!diskDirectoryNames.contains(child.name)) {
                node.children.remove(i);
            }
        }

        node.files = new datastructures.FileLinkedList();
        node.fileIndex = new datastructures.FileHashMap();
        for (models.FileMetadata metadata : diskFiles) {
            node.files.add(metadata);
            node.fileIndex.put(metadata.filename, metadata);
        }
    }

    /** Recursively updates absolute paths after a directory rename. */
    private void updateNodePathRecursively(models.FileNode node, String newAbsolutePath) {
        String oldPath = node.absolutePath;
        node.absolutePath = diskService.normalizePath(newAbsolutePath);

        for (models.FileNode child : node.children) {
            String childNewPath = child.absolutePath.replace(oldPath, node.absolutePath);
            updateNodePathRecursively(child, childNewPath);
        }
    }

    /** Removes heap entries for all files under a directory node before removing it. */
    private void removeHeapEntriesUnderNode(models.FileNode node) {
        for (models.FileMetadata metadata : node.files.getAll()) {
            String absolutePath = node.absolutePath + File.separator + metadata.filename;
            globalHeap.remove(absolutePath);
            tree.untrackFile(metadata.filename, absolutePath);

            Integer blockId = fileBlockIndex.remove(absolutePath);
            if (blockId != null && blockId >= 0) {
                diskSimulator.freeFile(blockId);
            }
        }

        for (models.FileNode child : node.children) {
            removeHeapEntriesUnderNode(child);
        }
    }

    /** Refreshes current directory tree view and global heap snapshot from disk. */
    private void refreshTreeAndHeap() {
        syncDirectoryFromDisk(currentDirectory, false);
        rebuildGlobalHeapFromRoot();
    }

    /** Refreshes memory structures and persists state for UI/sync consumers. */
    private void refreshState() {
        refreshTreeAndHeap();
        exportState();
    }

    /** Rebuilds the global heap from all files currently reachable from the tree root path. */
    private void rebuildGlobalHeapFromRoot() {
        this.globalHeap = new datastructures.FileHeap();
        diskService.populateHeapFromDisk(new File(tree.getRoot().absolutePath), this.globalHeap);
    }

    /** Returns true if root and target are equal or target is a descendant path of root. */
    private boolean isSameOrDescendant(String rootPath, String targetPath) {
        String normalizedRoot = diskService.normalizePath(rootPath).toLowerCase();
        String normalizedTarget = diskService.normalizePath(targetPath).toLowerCase();
        return normalizedTarget.equals(normalizedRoot)
                || normalizedTarget.startsWith(normalizedRoot + File.separator.toLowerCase());
    }

    /** Allows plain names only so commands operate within current directory scope.
     *  Rejects path separators, traversal patterns (..), null bytes, and empty/blank names.
     *  Note: any name containing ".." is rejected, including "foo..bar", as a
     *  defense-in-depth measure against path traversal after separator stripping. */
    private boolean isSimpleName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }
        if (name.contains("/") || name.contains("\\") || name.contains("\0")) {
            return false;
        }
        if (".".equals(name) || "..".equals(name)) {
            return false;
        }
        if (name.contains("..")) {
            return false;
        }
        return true;
    }

    /** Validates that a resolved path stays within the given parent directory boundary.
     *  Uses both normalized absolute paths and canonical paths to defeat all traversal attacks. */
    private boolean isWithinBoundary(String parentPath, String childPath) {
        try {
            // Primary check: normalize + toAbsolutePath
            java.nio.file.Path normalizedParent = Paths.get(parentPath).normalize().toAbsolutePath();
            java.nio.file.Path normalizedChild = Paths.get(childPath).normalize().toAbsolutePath();
            if (!normalizedChild.startsWith(normalizedParent)) {
                return false;
            }
            // Secondary check: canonical path (resolves symlinks)
            String canonicalParent = new File(parentPath).getCanonicalPath();
            String canonicalChild = new File(childPath).getCanonicalPath();
            return canonicalChild.startsWith(canonicalParent + File.separator)
                || canonicalChild.equals(canonicalParent);
        } catch (IOException e) {
            return false;
        }
    }

    /** Creates a symbolic link (shortcut) to another directory and checks for cycles. */
    public void createSymlink(String targetPath, String linkName) {
        if (!isSimpleName(linkName)) {
            System.out.println(Colors.c(Colors.RED, "Invalid symlink name: " + linkName));
            exportState();
            return;
        }

        File targetDir = resolveDirectory(targetPath);
        if (targetDir == null || !targetDir.exists()) {
            System.out.println(Colors.c(Colors.RED, "Target directory not found: " + targetPath));
            exportState();
            return;
        }

        // Feature 4: Cycle Detection using HashSet
        HashSet<String> visited = new HashSet<>();
        visited.add(diskService.normalizePath(targetDir.getAbsolutePath()));
        
        models.FileNode walker = currentDirectory;
        while (walker != null) {
            if (visited.contains(diskService.normalizePath(walker.absolutePath))) {
                System.out.println(Colors.c(Colors.RED, "Error: Creating this symlink would cause a circular reference cycle."));
                exportState();
                return;
            }
            walker = walker.parent;
        }

        try {
            java.nio.file.Path link = Paths.get(currentDirectory.absolutePath, linkName);
            java.nio.file.Files.createSymbolicLink(link, targetDir.toPath());
            
            // Register it in the tree
            tree.insertDirectory(currentDirectory, linkName, diskService.normalizePath(link.toString()));
            syncDirectoryFromDisk(currentDirectory, false);
            
            System.out.println("Symlink '" + Colors.c(Colors.BLUE, linkName) + "' "
                + Colors.c(Colors.GREEN, "created successfully") + " -> " + targetPath);
        } catch (UnsupportedOperationException e) {
            System.out.println(Colors.c(Colors.RED,
                    "Failed to create symlink: symbolic links are not supported on this platform or filesystem."));
        } catch (SecurityException e) {
            System.out.println(Colors.c(Colors.RED,
                    "Failed to create symlink: insufficient permissions to create symbolic links."));
        } catch (IOException e) {
            System.out.println(Colors.c(Colors.RED, "Failed to create symlink: " + e.getMessage()));
        }
        exportState();
    }

    /** Finds and displays all duplicate files by content hash in the entire tree. */
    public void findDuplicates() {
        Map<String, List<String>> duplicates = utils.DuplicateDetector.findDuplicates(tree.getRoot());
        utils.DuplicateDetector.printDuplicates(duplicates);
        exportState();
    }

    /** Removes duplicate files, keeping only the first occurrence of each hash. */
    public void removeDuplicates() {
        Map<String, List<String>> duplicates = utils.DuplicateDetector.findDuplicates(tree.getRoot());
        
        if (duplicates.isEmpty()) {
            System.out.println(Colors.c(Colors.GREEN, "✓ No duplicate files found!"));
            exportState();
            return;
        }
        
        // Show what will be deleted
        int totalToDelete = 0;
        for (List<String> group : duplicates.values()) {
            totalToDelete += group.size() - 1; // All except the first in each group
        }
        
        System.out.println();
        System.out.println(Colors.c(Colors.YELLOW, "Found " + duplicates.size() + " group(s) with " + totalToDelete + " duplicate file(s)"));
        System.out.println(Colors.c(Colors.CYAN, "Removing duplicates (keeping the first occurrence of each)..."));
        System.out.println();
        
        // Perform the deletion
        int deletedCount = utils.DuplicateDetector.removeDuplicates(tree.getRoot());
        
        System.out.println(Colors.c(Colors.GREEN, "✓ Successfully removed " + deletedCount + " duplicate file(s)!"));
        System.out.println();
        exportState();
    }

    /** Exports the current root, active directory, and global heap state to state.json.
     *  Cloud sync is dispatched asynchronously on a daemon thread with debounce. */
    public void exportState() {
        exporter.export(tree.getRoot(), currentDirectory, globalHeap);
        if (AuthManager.isLoggedIn() && syncPending.compareAndSet(false, true)) {
            syncExecutor.submit(() -> {
                try {
                    String stateContent = new String(
                            java.nio.file.Files.readAllBytes(
                                    java.nio.file.Paths.get(
                                            System.getProperty("user.dir") + java.io.File.separator + "state.json"
                                    )
                            )
                    );
                    FirestoreSync.push(
                            AuthManager.getUserEmail(),
                            AuthManager.getDeviceId(),
                            stateContent
                    );
                } catch (Exception e) {
                    Logger.warn("[sync] Cloud sync failed: " + e.getClass().getSimpleName());
                } finally {
                    syncPending.set(false);
                }
            });
        }
    }

    /** Copy a file or directory to a new location. */
    public void copy(String source, String destination, boolean recursive) {
        String resolvedSource = resolveAbsolutePath(source);
        String resolvedDest = resolveAbsolutePath(destination);
        utils.FileOps.copy(resolvedSource, resolvedDest, recursive);
        exportState();
    }

    /** Move/rename a file or directory. */
    public void move(String source, String destination) {
        String resolvedSource = resolveAbsolutePath(source);
        String resolvedDest = resolveAbsolutePath(destination);
        utils.FileOps.move(resolvedSource, resolvedDest);
        exportState();
    }

    /** Move file to trash. */
    public void moveFileToTrash(String filename) {
        File file = new File(currentDirectory.absolutePath + File.separator + filename);
        if (!file.exists()) {
            System.out.println(Colors.c(Colors.RED, "File not found: " + filename));
            exportState();
            return;
        }
        utils.TrashManager.moveToTrash(file.getAbsolutePath(), file.isDirectory());
        exportState();
    }

    /** List items in trash. */
    public void listTrash() {
        utils.TrashManager.listTrash();
        exportState();
    }

    /** Restore file from trash. */
    public void restoreFromTrash(String originalPath) {
        utils.TrashManager.restoreFromTrash(originalPath);
        exportState();
    }

    /** Restore file from trash by filename only (searches most recent match). */
    public void restoreFromTrashByName(String filename) {
        utils.TrashManager.restoreFromTrashByName(filename);
        exportState();
    }

    /** Empty all trash. */
    public void emptyTrash() {
        utils.TrashManager.emptyTrash();
        exportState();
    }

    /** Show trash size. */
    public void trashSize() {
        utils.TrashManager.showTrashSize();
        exportState();
    }

    /** Helper: Resolve absolute path from relative or absolute path string. */
    private String resolveAbsolutePath(String path) {
        if (path.startsWith("/")) {
            return path;
        } else {
            return currentDirectory.absolutePath + File.separator + path;
        }
    }
}
