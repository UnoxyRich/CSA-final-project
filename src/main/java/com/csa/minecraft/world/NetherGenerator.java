package com.csa.minecraft.world;

import java.util.Random;

/**
 * Generates nether-like terrain: solid netherrack floor and ceiling with
 * large cave spaces carved by 3-D FBM noise in between.
 */
public class NetherGenerator implements WorldGenerator {
    private static final int FLOOR_Y  = 12;   // solid netherrack up to this y
    private static final int CEIL_Y   = 100;  // solid netherrack from this y up

    private final long seed;

    public NetherGenerator(long seed) {
        // XOR with a constant so nether gen differs from overworld gen with same seed
        this.seed = seed ^ 0xDEADBEEF_CAFEBABEL;
    }

    @Override
    public void generate(Chunk c) {
        for (int x = 0; x < Chunk.SX; x++) {
            for (int z = 0; z < Chunk.SZ; z++) {
                int wx = c.worldX(x), wz = c.worldZ(z);
                for (int y = 0; y < Chunk.SY; y++) {
                    if (y <= FLOOR_Y || y >= CEIL_Y) {
                        c.set(x, y, z, Block.NETHERRACK);
                    } else {
                        float n = noise3d(wx, y, wz);
                        // bias toward solid near floor and ceiling
                        float floorBias = Math.max(0f, 1f - (float)(y - FLOOR_Y) / 14f);
                        float ceilBias  = Math.max(0f, 1f - (float)(CEIL_Y - y)  / 12f);
                        float bias = Math.max(floorBias, ceilBias);
                        if (n > 0.22f - bias * 0.45f) {
                            c.set(x, y, z, Block.NETHERRACK);
                        }
                        // else AIR (cave space)
                    }
                }
            }
        }
    }

    private float noise3d(int wx, int wy, int wz) {
        float n1 = fbm(wx * 0.055f,        wz * 0.055f,        3);
        float n2 = fbm(wx * 0.070f + 100f, wy * 0.070f + 300f, 3);
        float n3 = fbm(wy * 0.045f + 700f, wz * 0.045f + 500f, 3);
        return (n1 + n2 + n3) / 3f;
    }

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
        float a = hash(xi, zi), b = hash(xi+1, zi);
        float cc = hash(xi, zi+1), d = hash(xi+1, zi+1);
        float u = smooth(fx), v = smooth(fz);
        return (a*(1-u) + b*u) * (1-v) + (cc*(1-u) + d*u) * v;
    }

    private float fbm(float x, float z, int oct) {
        float amp = 1, freq = 1, sum = 0, norm = 0;
        for (int i = 0; i < oct; i++) {
            sum += noise(x * freq, z * freq) * amp;
            norm += amp; amp *= 0.5f; freq *= 2f;
        }
        return sum / norm;
    }
}
