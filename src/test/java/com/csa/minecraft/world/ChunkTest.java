package com.csa.minecraft.world;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ChunkTest {

    @Test
    void freshChunkIsAllAir() {
        Chunk c = new Chunk(0, 0);
        for (int x = 0; x < Chunk.SX; x++)
            for (int y = 0; y < Chunk.SY; y++)
                for (int z = 0; z < Chunk.SZ; z++)
                    assertEquals(Block.AIR, c.get(x, y, z));
    }

    @Test
    void freshChunkStartsDirty() {
        // ChunkMesher only rebuilds when dirty; new chunks must rebuild on first frame.
        assertTrue(new Chunk(0, 0).dirty);
    }

    @Test
    void setThenGetRoundTrips() {
        Chunk c = new Chunk(0, 0);
        c.set(3, 64, 5, Block.STONE);
        assertEquals(Block.STONE, c.get(3, 64, 5));
        c.set(3, 64, 5, Block.GLASS);
        assertEquals(Block.GLASS, c.get(3, 64, 5));
    }

    @Test
    void setMarksDirty() {
        Chunk c = new Chunk(0, 0);
        c.dirty = false;
        c.set(1, 1, 1, Block.DIRT);
        assertTrue(c.dirty);
    }

    @Test
    void metadataRoundTripsAndIsClearedBySet() {
        Chunk c = new Chunk(0, 0);
        c.set(1, 2, 3, Block.WATER);
        c.setMeta(1, 2, 3, 5);

        assertEquals(5, c.getMeta(1, 2, 3));

        c.set(1, 2, 3, Block.STONE);
        assertEquals(0, c.getMeta(1, 2, 3));
    }

    @Test
    void outOfBoundsGetReturnsAir() {
        Chunk c = new Chunk(0, 0);
        c.set(0, 0, 0, Block.STONE);
        assertEquals(Block.AIR, c.get(-1, 0, 0));
        assertEquals(Block.AIR, c.get(Chunk.SX, 0, 0));
        assertEquals(Block.AIR, c.get(0, -1, 0));
        assertEquals(Block.AIR, c.get(0, Chunk.SY, 0));
        assertEquals(Block.AIR, c.get(0, 0, -1));
        assertEquals(Block.AIR, c.get(0, 0, Chunk.SZ));
    }

    @Test
    void outOfBoundsSetIsNoOp() {
        Chunk c = new Chunk(0, 0);
        c.dirty = false;
        c.set(-1, 0, 0, Block.STONE);
        c.set(Chunk.SX, 0, 0, Block.STONE);
        c.set(0, Chunk.SY, 0, Block.STONE);
        assertFalse(c.dirty, "out-of-bounds set must not dirty the chunk");
    }

    @Test
    void inBoundsCoversAllValidCells() {
        Chunk c = new Chunk(0, 0);
        assertTrue(c.inBounds(0, 0, 0));
        assertTrue(c.inBounds(Chunk.SX - 1, Chunk.SY - 1, Chunk.SZ - 1));
        assertFalse(c.inBounds(-1, 0, 0));
        assertFalse(c.inBounds(0, Chunk.SY, 0));
    }

    @Test
    void idxIsUniquePerCell() {
        // Index collisions would corrupt the block array.
        Set<Integer> seen = new HashSet<>();
        int total = Chunk.SX * Chunk.SY * Chunk.SZ;
        for (int x = 0; x < Chunk.SX; x++)
            for (int y = 0; y < Chunk.SY; y++)
                for (int z = 0; z < Chunk.SZ; z++) {
                    int i = Chunk.idx(x, y, z);
                    assertTrue(i >= 0 && i < total);
                    assertTrue(seen.add(i), "duplicate index at " + x + "," + y + "," + z);
                }
        assertEquals(total, seen.size());
    }

    @Test
    void worldCoordsRespectChunkOffset() {
        Chunk c = new Chunk(-2, 3);
        assertEquals(-32, c.worldX(0));
        assertEquals(-17, c.worldX(15));
        assertEquals(48, c.worldZ(0));
        assertEquals(63, c.worldZ(15));
    }
}
