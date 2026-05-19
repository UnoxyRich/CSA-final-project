package com.csa.minecraft.player;

import com.csa.minecraft.world.Block;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InventoryTest {

    @Test
    void hotbarHasNineSlots() {
        // The Player update loop indexes keys 1..9 into this array; size must be 9.
        assertEquals(9, new Inventory().hotbar.length);
    }

    @Test
    void defaultSelectionIsFirstSlot() {
        Inventory inv = new Inventory();
        assertEquals(0, inv.selected);
        assertEquals(inv.hotbar[0], inv.selectedBlock());
    }

    @Test
    void selectedBlockFollowsSelection() {
        Inventory inv = new Inventory();
        for (int i = 0; i < inv.hotbar.length; i++) {
            inv.selected = i;
            assertEquals(inv.hotbar[i], inv.selectedBlock());
        }
    }

    @Test
    void hotbarContainsNoAir() {
        // Placing AIR via the hotbar would be indistinguishable from breaking a block.
        for (Block b : new Inventory().hotbar) {
            assertNotEquals(Block.AIR, b);
        }
    }
}
