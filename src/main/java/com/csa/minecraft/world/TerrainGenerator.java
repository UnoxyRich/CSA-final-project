package com.csa.minecraft.world;

import java.util.Random;

/** Simple value-noise heightmap + tree scattering. */
public class TerrainGenerator {
    private final long seed;
    public TerrainGenerator(long seed) { this.seed = seed; }

    public void generate(Chunk c) {
        Random rng = new Random(seed ^ ((long)c.cx * 341873128712L) ^ ((long)c.cz * 132897987541L));
        for (int x = 0; x < Chunk.SX; x++) {
            for (int z = 0; z < Chunk.SZ; z++) {
                int wx = c.worldX(x), wz = c.worldZ(z);
                int h = heightAt(wx, wz);
                for (int y = 0; y <= h; y++) {
                    Block b;
                    if (y == h) b = h <= 62 ? Block.SAND : Block.GRASS;
                    else if (y >= h - 3) b = Block.DIRT;
                    else b = Block.STONE;
                    c.set(x, y, z, b);
                }
                // scatter trees
                if (h > 63 && rng.nextInt(60) == 0 && x > 1 && x < Chunk.SX - 2 && z > 1 && z < Chunk.SZ - 2) {
                    placeTree(c, x, h + 1, z, rng);
                }
            }
        }
    }

    public int heightAt(int wx, int wz) {
        float n = fbm(wx * 0.015f, wz * 0.015f, 4);
        return (int) (64 + n * 18);
    }

    private void placeTree(Chunk c, int x, int y, int z, Random rng) {
        int trunk = 4 + rng.nextInt(2);
        for (int i = 0; i < trunk; i++) c.set(x, y + i, z, Block.WOOD);
        int top = y + trunk;
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++)
                for (int dy = -1; dy <= 1; dy++)
                    if (Math.abs(dx) + Math.abs(dz) + Math.abs(dy) <= 3 && c.get(x+dx, top+dy, z+dz) == Block.AIR)
                        c.set(x+dx, top+dy, z+dz, Block.LEAVES);
    }

    // value noise
    private float hash(int x, int z) {
        long h = (long)x * 374761393L + (long)z * 668265263L + seed * 982451653L;
        h = (h ^ (h >>> 13)) * 1274126177L;
        h = h ^ (h >>> 16);
        return ((h & 0xffff) / 65535f) * 2f - 1f;
    }
    private float smooth(float t) { return t * t * (3 - 2 * t); }
    private float noise(float x, float z) {
        int xi = (int) Math.floor(x), zi = (int) Math.floor(z);
        float fx = x - xi, fz = z - zi;
        float a = hash(xi, zi), b = hash(xi+1, zi), cc = hash(xi, zi+1), d = hash(xi+1, zi+1);
        float u = smooth(fx), v = smooth(fz);
        return (a*(1-u) + b*u) * (1-v) + (cc*(1-u) + d*u) * v;
    }
    private float fbm(float x, float z, int oct) {
        float amp = 1, freq = 1, sum = 0, norm = 0;
        for (int i = 0; i < oct; i++) {
            sum += noise(x*freq, z*freq) * amp;
            norm += amp; amp *= 0.5f; freq *= 2;
        }
        return sum / norm;
    }
}
