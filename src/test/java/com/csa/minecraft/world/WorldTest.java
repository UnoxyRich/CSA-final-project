package com.csa.minecraft.world;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class WorldTest {

    @Test
    void keyIsUniqueForNearbyCoords() {
        Set<Long> keys = new HashSet<>();
        for (int cx = -8; cx <= 8; cx++)
            for (int cz = -8; cz <= 8; cz++)
                assertTrue(keys.add(World.key(cx, cz)),
                    "collision at " + cx + "," + cz);
    }

    @Test
    void keyDistinguishesSignFlip() {
        assertNotEquals(World.key(1, -1), World.key(-1, 1));
        assertNotEquals(World.key(0, -1), World.key(-1, 0));
    }

    @Test
    void getBlockOutsideYRangeIsAir() {
        World w = new World(1234L);
        assertEquals(Block.AIR, w.getBlock(0, -1, 0));
        assertEquals(Block.AIR, w.getBlock(0, Chunk.SY, 0));
        assertEquals(Block.AIR, w.getBlock(0, Chunk.SY + 100, 0));
    }

    @Test
    void getBlockInUnloadedChunkIsAir() {
        World w = new World(1234L);
        // Far-away chunk that has not been touched: must not lazily load.
        assertEquals(Block.AIR, w.getBlock(50_000, 64, 50_000));
    }

    @Test
    void setBlockRoundTripsAcrossNegativeCoords() {
        World w = new World(42L);
        // y=120 is reliably above terrain (max heightAt ~82), so it starts as AIR.
        w.setBlock(-3, 120, -17, Block.STONE);
        assertEquals(Block.STONE, w.getBlock(-3, 120, -17));
        w.setBlock(-3, 120, -17, Block.AIR);
        assertEquals(Block.AIR, w.getBlock(-3, 120, -17));
    }

    @Test
    void setBlockOnNegativeXBorderMarksWestNeighborDirty() {
        World w = new World(1L);
        // Force both chunks to load (and clear post-generation dirty flags).
        Chunk own  = w.chunkAt(0, 0);
        Chunk west = w.chunkAt(-1, 0);
        own.dirty = false;
        west.dirty = false;

        w.setBlock(0, 120, 8, Block.STONE); // lx == 0 in chunk (0,0)

        assertTrue(own.dirty, "edited chunk must be dirty");
        assertTrue(west.dirty, "west neighbor must be dirty for cross-chunk face culling");
    }

    @Test
    void setBlockOnPositiveXBorderMarksEastNeighborDirty() {
        World w = new World(1L);
        Chunk own  = w.chunkAt(0, 0);
        Chunk east = w.chunkAt(1, 0);
        own.dirty = false;
        east.dirty = false;

        w.setBlock(Chunk.SX - 1, 120, 8, Block.STONE);

        assertTrue(own.dirty);
        assertTrue(east.dirty);
    }

    @Test
    void setBlockAtCornerMarksBothNeighbors() {
        World w = new World(1L);
        Chunk own   = w.chunkAt(0, 0);
        Chunk west  = w.chunkAt(-1, 0);
        Chunk north = w.chunkAt(0, -1);
        Chunk diag  = w.chunkAt(-1, -1);
        own.dirty = false; west.dirty = false; north.dirty = false; diag.dirty = false;

        w.setBlock(0, 120, 0, Block.STONE); // lx=0, lz=0

        assertTrue(own.dirty);
        assertTrue(west.dirty);
        assertTrue(north.dirty);
        // Diagonal neighbor is intentionally not marked — only orthogonal neighbors.
        assertFalse(diag.dirty,
            "diagonal neighbor is not adjacent on a face and should not be marked");
    }

    @Test
    void setBlockMidChunkDoesNotMarkNeighbors() {
        World w = new World(1L);
        Chunk own   = w.chunkAt(0, 0);
        Chunk west  = w.chunkAt(-1, 0);
        Chunk east  = w.chunkAt(1, 0);
        Chunk north = w.chunkAt(0, -1);
        Chunk south = w.chunkAt(0, 1);
        own.dirty = false; west.dirty = false; east.dirty = false;
        north.dirty = false; south.dirty = false;

        w.setBlock(8, 120, 8, Block.STONE);

        assertTrue(own.dirty);
        assertFalse(west.dirty);
        assertFalse(east.dirty);
        assertFalse(north.dirty);
        assertFalse(south.dirty);
    }

    @Test
    void chunkAtIsIdempotent() {
        World w = new World(7L);
        Chunk a = w.chunkAt(2, 3);
        Chunk b = w.chunkAt(2, 3);
        assertSame(a, b);
    }

    /** Calls update() until the loaded set stops growing. update() generates
     *  only a budgeted number of chunks per call, so a full load takes several. */
    private static void loadFully(World w, Vector3f pos) {
        int prev = -1;
        while (w.loaded().size() != prev) {
            prev = w.loaded().size();
            w.update(pos);
        }
    }

    @Test
    void updateLoadsChunksAroundPlayer() {
        World w = new World(7L);
        loadFully(w, new Vector3f(0, 70, 0));
        // (2*renderDistance+1)^2 chunks in a square around the player.
        int side = 2 * w.renderDistance() + 1;
        assertEquals(side * side, w.loaded().size());
    }

    @Test
    void setRenderDistanceShrinksLoadedSetOnNextUpdate() {
        World w = new World(7L);
        loadFully(w, new Vector3f(0, 70, 0));
        int before = w.loaded().size();

        w.setRenderDistance(3);
        w.update(new Vector3f(0, 70, 0));
        int after = w.loaded().size();
        // Unload margin keeps chunks within renderDist+2 of the player, so the loaded set
        // is (2*(r+2)+1)^2 = 11*11 = 121, not the inner 7*7 = 49.
        int marginSide = 2 * (w.renderDistance() + 2) + 1;
        assertEquals(marginSide * marginSide, after);
        assertTrue(after < before);
    }
}
