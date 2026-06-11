package com.csa.minecraft.player;

import com.csa.minecraft.world.Block;

public class Inventory {
    public static final int SIZE      = 9;
    public static final int MAX_STACK = 64;

    /** Fixed block types shown in creative mode (infinite, no count). */
    public static final Block[] CREATIVE_HOTBAR = {
        Block.GRASS, Block.DIRT, Block.STONE, Block.WOOD,
        Block.LEAVES, Block.SAND, Block.PLANKS, Block.GLASS, Block.OBSIDIAN
    };

    /** Survival hotbar: block type per slot (AIR = empty). */
    public final Block[] hotbar = new Block[SIZE];
    /** Survival hotbar: stack count per slot (0 = empty). */
    public final int[] counts = new int[SIZE];
    /** Currently selected slot index 0-8. */
    public int selected = 0;

    public Inventory() {
        for (int i = 0; i < SIZE; i++) {
            hotbar[i] = Block.AIR;
            counts[i] = 0;
        }
    }

    /** Returns the block that would be placed by a right-click in survival (AIR if slot empty). */
    public Block selectedBlock() {
        Block b = hotbar[selected];
        return (b != null && b != Block.AIR && counts[selected] > 0) ? b : Block.AIR;
    }

    /**
     * Add one block to the inventory.
     * Stacks onto an existing matching slot first, then fills an empty slot.
     * Returns true if the block was accepted, false if inventory is full.
     */
    public boolean addBlock(Block b) {
        if (b == null || b == Block.AIR || b == Block.WATER) return true;
        // Stack onto existing slot
        for (int i = 0; i < SIZE; i++) {
            if (hotbar[i] == b && counts[i] < MAX_STACK) {
                counts[i]++;
                return true;
            }
        }
        // Find first empty slot
        for (int i = 0; i < SIZE; i++) {
            if (hotbar[i] == Block.AIR || counts[i] == 0) {
                hotbar[i] = b;
                counts[i] = 1;
                return true;
            }
        }
        return false; // inventory full - block is lost
    }

    /**
     * Consume one of the currently selected block.
     * Returns true if the slot had at least one item and it was consumed.
     */
    public boolean consumeSelected() {
        if (hotbar[selected] == Block.AIR || counts[selected] <= 0) return false;
        counts[selected]--;
        if (counts[selected] == 0) hotbar[selected] = Block.AIR;
        return true;
    }
}
