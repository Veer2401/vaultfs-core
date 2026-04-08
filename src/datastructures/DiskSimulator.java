package datastructures;

import java.util.ArrayList;
import java.util.List;

/**
 * Simulates a disk block storage system where a file is fragmented across multiple blocks.
 * Uses a LinkedList approach to chain blocks together.
 */
public class DiskSimulator {
    // Simulate a 10MB disk with 4KB blocks
    private static final int BLOCK_SIZE = 4096;
    private static final int TOTAL_BLOCKS = 2560; // 10MB total
    private Block[] disk;
    private int freeBlocks;

    public static class Block {
        public int id;
        public int nextBlockId; // -1 means EOF
        public boolean isUsed;

        public Block(int id) {
            this.id = id;
            this.nextBlockId = -1;
            this.isUsed = false;
        }
    }

    public DiskSimulator() {
        this.disk = new Block[TOTAL_BLOCKS];
        for (int i = 0; i < TOTAL_BLOCKS; i++) {
            this.disk[i] = new Block(i);
        }
        this.freeBlocks = TOTAL_BLOCKS;
    }

    /**
     * Allocates blocks for a file of a given size using a linked list fragmentation strategy.
     * Returns the ID of the first block (head of the linked list).
     */
    public int allocateFile(long sizeBytes) {
        int requiredBlocks = (int) Math.ceil((double) sizeBytes / BLOCK_SIZE);
        if (requiredBlocks == 0) requiredBlocks = 1; // Minimum 1 block

        if (requiredBlocks > freeBlocks) {
            System.out.println("DiskSimulator Error: Not enough free blocks. Required: " + requiredBlocks + ", Available: " + freeBlocks);
            return -1;
        }

        int firstBlockId = -1;
        int prevBlockId = -1;
        int allocated = 0;

        // Find free blocks and chain them together (simulating fragmentation)
        for (int i = 0; i < TOTAL_BLOCKS && allocated < requiredBlocks; i++) {
            if (!disk[i].isUsed) {
                disk[i].isUsed = true;
                
                if (firstBlockId == -1) {
                    firstBlockId = i;
                }
                
                if (prevBlockId != -1) {
                    disk[prevBlockId].nextBlockId = i;
                }
                
                prevBlockId = i;
                allocated++;
                freeBlocks--;
            }
        }
        
        // Mark the last block as EOF
        if (prevBlockId != -1) {
            disk[prevBlockId].nextBlockId = -1;
        }

        return firstBlockId;
    }

    /**
     * Frees all blocks associated with a file starting from the given block ID.
     */
    public void freeFile(int startBlockId) {
        if (startBlockId < 0 || startBlockId >= TOTAL_BLOCKS) return;

        int currentId = startBlockId;
        while (currentId != -1) {
            Block block = disk[currentId];
            block.isUsed = false;
            int nextId = block.nextBlockId;
            block.nextBlockId = -1;
            freeBlocks++;
            currentId = nextId;
        }
    }

    /**
     * Traverses the linked list of blocks to return the sequence of block IDs used by a file.
     */
    public List<Integer> getBlockChain(int startBlockId) {
        List<Integer> chain = new ArrayList<>();
        int currentId = startBlockId;
        while (currentId != -1) {
            chain.add(currentId);
            currentId = disk[currentId].nextBlockId;
        }
        return chain;
    }

    public int getFreeBlocks() {
        return freeBlocks;
    }

    public int getTotalBlocks() {
        return TOTAL_BLOCKS;
    }
}
