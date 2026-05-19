package com.csa.minecraft.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TerrainGeneratorTest {

    @Test
    void heightAtIsDeterministicForSeed() {
        TerrainGenerator a = new TerrainGenerator(123L);
        TerrainGenerator b = new TerrainGenerator(123L);
        for (int x = -10; x <= 10; x++)
            for (int z = -10; z <= 10; z++)
                assertEquals(a.heightAt(x, z), b.heightAt(x, z));
    }

    @Test
    void heightAtVariesWithSeed() {
        TerrainGenerator a = new TerrainGenerator(1L);
        TerrainGenerator b = new TerrainGenerator(2L);
        boolean anyDifferent = false;
        for (int x = -20; x <= 20 && !anyDifferent; x++)
            for (int z = -20; z <= 20 && !anyDifferent; z++)
                if (a.heightAt(x, z) != b.heightAt(x, z)) anyDifferent = true;
        assertTrue(anyDifferent, "different seeds should produce different terrain");
    }

    @Test
    void heightStaysInExpectedRange() {
        // Cherry mountain uplift can raise peaks, but terrain must stay inside chunk height.
        TerrainGenerator g = new TerrainGenerator(99L);
        for (int x = -50; x <= 50; x++)
            for (int z = -50; z <= 50; z++) {
                int h = g.heightAt(x, z);
                assertTrue(h >= 42 && h <= 112, "height " + h + " at " + x + "," + z + " out of range");
            }
    }

    @Test
    void generatedColumnIsSolidUpToHeightAndAirAbove() {
        TerrainGenerator g = new TerrainGenerator(55L);
        Chunk c = new Chunk(0, 0);
        g.generate(c);
        for (int x = 0; x < Chunk.SX; x++)
            for (int z = 0; z < Chunk.SZ; z++) {
                int h = g.heightAt(c.worldX(x), c.worldZ(z));
                assertTrue(c.get(x, h, z).solid,
                    "top of column should be solid at " + x + "," + h + "," + z);
                // The first cell above the heightmap should be AIR unless a tree dropped a
                // trunk or leaves there; we check a safe margin above where no tree reaches.
                assertEquals(Block.AIR, c.get(x, Math.min(Chunk.SY - 1, h + 12), z),
                    "well above terrain should be AIR");
            }
    }

    @Test
    void generationIsRepeatableForSameChunk() {
        TerrainGenerator g1 = new TerrainGenerator(77L);
        TerrainGenerator g2 = new TerrainGenerator(77L);
        Chunk a = new Chunk(1, 2);
        Chunk b = new Chunk(1, 2);
        g1.generate(a);
        g2.generate(b);
        for (int x = 0; x < Chunk.SX; x++)
            for (int y = 0; y < Chunk.SY; y++)
                for (int z = 0; z < Chunk.SZ; z++)
                    assertEquals(a.get(x, y, z), b.get(x, y, z),
                        "mismatch at " + x + "," + y + "," + z);
    }

    @Test
    void differentChunksFromSameSeedDiffer() {
        TerrainGenerator g = new TerrainGenerator(77L);
        Chunk a = new Chunk(0, 0);
        Chunk b = new Chunk(5, 5);
        g.generate(a);
        g.generate(b);
        boolean anyDifferent = false;
        outer:
        for (int x = 0; x < Chunk.SX; x++)
            for (int z = 0; z < Chunk.SZ; z++)
                if (a.get(x, 0, z) != b.get(x, 0, z) || a.get(x, 64, z) != b.get(x, 64, z)) {
                    anyDifferent = true;
                    break outer;
                }
        assertTrue(anyDifferent);
    }
}
