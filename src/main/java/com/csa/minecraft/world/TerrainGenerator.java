package com.csa.minecraft.world;

import java.util.Random;

/** Value-noise heightmap with plains, wooded hills, and cherry blossom mountains. */
public class TerrainGenerator {
    public static final int SEA_LEVEL = 62;
    private final long seed;
    public TerrainGenerator(long seed) { this.seed = seed; }

    public void generate(Chunk c) {
        Random rng = new Random(seed ^ ((long)c.cx * 341873128712L) ^ ((long)c.cz * 132897987541L));
        for (int x = 0; x < Chunk.SX; x++) {
            for (int z = 0; z < Chunk.SZ; z++) {
                int wx = c.worldX(x), wz = c.worldZ(z);
                int h = heightAt(wx, wz);
                boolean cherryMountain = cherryMountainAt(wx, wz);
                for (int y = 0; y <= h; y++) {
                    Block b;
                    if (y == h) b = h <= SEA_LEVEL + 1 ? Block.SAND : Block.GRASS;
                    else if (y >= h - 3) b = h <= SEA_LEVEL + 1 ? Block.SAND : Block.DIRT;
                    else b = Block.STONE;
                    c.set(x, y, z, b);
                }
                if (h < SEA_LEVEL) {
                    for (int y = h + 1; y <= SEA_LEVEL; y++) c.set(x, y, z, Block.WATER);
                }
                if (cherryMountain && h > 76 && rng.nextInt(24) == 0 &&
                    x > 2 && x < Chunk.SX - 3 && z > 2 && z < Chunk.SZ - 3) {
                    placeCherryTree(c, x, h + 1, z, rng);
                } else if (h > 63 && rng.nextInt(60) == 0 &&
                           x > 1 && x < Chunk.SX - 2 && z > 1 && z < Chunk.SZ - 2) {
                    placeTree(c, x, h + 1, z, rng);
                }
            }
        }
    }

    public int heightAt(int wx, int wz) {
        float rolling = fbm(wx * 0.015f, wz * 0.015f, 4);
        float mountainMask = cherryMountainMask(wx, wz);
        float oceanMask = oceanMask(wx, wz);
        float ridge = Math.abs(fbm(wx * 0.028f + 91.7f, wz * 0.028f - 37.2f, 4));
        float peaks = (float) Math.pow(ridge, 1.35f);
        float oceanFloor = 49f + rolling * 6f;
        float land = 64 + rolling * 18 + mountainMask * (18 + peaks * 30);
        return clampHeight((int) mix(land, oceanFloor, oceanMask));
    }

    public boolean cherryMountainAt(int wx, int wz) {
        return cherryMountainMask(wx, wz) > 0.38f;
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

    private void placeCherryTree(Chunk c, int x, int y, int z, Random rng) {
        int trunk = 5 + rng.nextInt(3);
        for (int i = 0; i < trunk; i++) c.set(x, y + i, z, Block.CHERRY_WOOD);
        int top = y + trunk;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dy = -2; dy <= 1; dy++) {
                    int dist = Math.abs(dx) + Math.abs(dz);
                    if (dist + Math.abs(dy) <= 5 && rng.nextInt(10) != 0 &&
                        c.get(x + dx, top + dy, z + dz) == Block.AIR) {
                        c.set(x + dx, top + dy, z + dz, Block.CHERRY_LEAVES);
                    }
                }
            }
        }
        for (int i = 0; i < 5; i++) {
            int bx = x + rng.nextInt(5) - 2;
            int bz = z + rng.nextInt(5) - 2;
            int by = top - 1 - rng.nextInt(2);
            if (c.get(bx, by, bz) == Block.AIR) c.set(bx, by, bz, Block.CHERRY_LEAVES);
        }
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

    private float cherryMountainMask(int wx, int wz) {
        float biome = fbm(wx * 0.0045f + 241.3f, wz * 0.0045f - 117.8f, 3);
        return smoothstep(0.18f, 0.64f, biome) * (1f - oceanMask(wx, wz));
    }

    private float oceanMask(int wx, int wz) {
        float biome = fbm(wx * 0.0035f - 83.4f, wz * 0.0035f + 219.8f, 3);
        return smoothstep(0.05f, 0.42f, -biome);
    }

    private float smoothstep(float edge0, float edge1, float x) {
        float t = Math.max(0f, Math.min(1f, (x - edge0) / (edge1 - edge0)));
        return t * t * (3f - 2f * t);
    }

    private int clampHeight(int h) {
        return Math.max(42, Math.min(112, h));
    }

    private float mix(float a, float b, float t) {
        return a * (1f - t) + b * t;
    }
}
