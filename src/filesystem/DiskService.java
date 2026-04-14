package filesystem;

import datastructures.DiskSimulator;
import datastructures.FileHeap;
import java.io.File;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import models.FileMetadata;

/**
 * Encapsulates disk-oriented operations used by FileSystem.
 */
public class DiskService {
    /** Recursively deletes a file or directory from disk and returns full success. */
    public boolean deleteDiskRecursive(File file) {
        boolean success = true;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (!deleteDiskRecursive(child)) {
                        success = false;
                    }
                }
            }
        }
        if (!file.delete()) {
            success = false;
        }
        return success;
    }

    /** Creates metadata from actual disk size and modified timestamp. */
    public FileMetadata metadataFromDiskFile(File file, DiskSimulator diskSimulator, int existingStartBlockId) {
        FileMetadata metadata = new FileMetadata(file.getName(), file.length());
        LocalDateTime modified = LocalDateTime.ofInstant(Instant.ofEpochMilli(file.lastModified()), ZoneId.systemDefault());
        metadata.modifiedAt = modified;

        if (existingStartBlockId >= 0) {
            metadata.startBlockId = existingStartBlockId;
        } else if (existingStartBlockId == -2) {
            // Already tracked but disk was full — don't retry allocation
            metadata.startBlockId = -1;
        } else if (metadata.startBlockId == -1) {
            metadata.startBlockId = diskSimulator.allocateFile(metadata.sizeBytes);
        }
        return metadata;
    }

    /** Recursively inserts file metadata from disk into a heap. */
    public void populateHeapFromDisk(File directory, FileHeap heap) {
        if (directory == null || !directory.exists() || !directory.isDirectory()) {
            return;
        }

        File[] children = directory.listFiles();
        if (children == null) {
            return;
        }

        for (File child : children) {
            if (child.isFile()) {
                heap.insert(child.getName(), normalizePath(child.getAbsolutePath()), child.length());
            } else if (child.isDirectory()) {
                populateHeapFromDisk(child, heap);
            }
        }
    }

    /** Normalizes a path into absolute canonical-like form without resolving symlinks. */
    public String normalizePath(String path) {
        return new File(path).getAbsoluteFile().toPath().normalize().toString();
    }
}
