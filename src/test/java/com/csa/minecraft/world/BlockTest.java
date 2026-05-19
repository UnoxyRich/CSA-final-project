package com.csa.minecraft.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BlockTest {

    @Test
    void airIsOrdinalZero() {
        // Chunk.blocks is a zero-initialized byte array; AIR must map to 0.
        assertEquals(0, Block.AIR.ordinal());
    }

    @Test
    void airIsNotSolid() {
        assertFalse(Block.AIR.solid);
    }

    @Test
    void allNonAirBlocksAreSolid() {
        for (Block b : Block.values()) {
            if (b == Block.AIR || b == Block.WATER) continue;
            assertTrue(b.solid, b + " should be solid");
        }
    }

    @Test
    void byIdReturnsAirForOutOfRange() {
        assertEquals(Block.AIR, Block.byId(-1));
        assertEquals(Block.AIR, Block.byId(Block.values().length));
        assertEquals(Block.AIR, Block.byId(9999));
    }

    @Test
    void byIdRoundTripsOrdinals() {
        for (Block b : Block.values()) {
            assertEquals(b, Block.byId(b.ordinal()));
        }
    }

    @Test
    void grassUsesDistinctTopBottomAndSideTiles() {
        int top = Block.GRASS.atlasIndex(0);
        int bottom = Block.GRASS.atlasIndex(1);
        int side = Block.GRASS.atlasIndex(2);
        assertNotEquals(top, bottom);
        assertNotEquals(top, side);
        assertNotEquals(bottom, side);
    }

    @Test
    void dirtTileIsFaceInvariant() {
        int t = Block.DIRT.atlasIndex(0);
        assertEquals(t, Block.DIRT.atlasIndex(1));
        assertEquals(t, Block.DIRT.atlasIndex(2));
    }

    @Test
    void woodEndsAndSidesDiffer() {
        // top (face 0) and bottom (face 1) use the end tile, sides use the bark tile.
        int top = Block.WOOD.atlasIndex(0);
        int bottom = Block.WOOD.atlasIndex(1);
        int side = Block.WOOD.atlasIndex(2);
        assertEquals(top, bottom);
        assertNotEquals(top, side);
    }
}
