package com.csa.minecraft.world;

import com.csa.minecraft.engine.Mesh;

public class Chunk {
    public static final int SX = 16, SY = 128, SZ = 16;
    public final int cx, cz;
    private final byte[] blocks = new byte[SX * SY * SZ];
    // Per-cell metadata, parallel to blocks. Only WATER uses it: the low 4 bits
    // store the flow level (0 = source, 1-7 = flowing, 8 = falling). Always 0
    // for every other block. Chunk.set() clears it.
    private final byte[] meta = new byte[SX * SY * SZ];
    public Mesh mesh;
    public boolean dirty = true;

    public Chunk(int cx, int cz) { this.cx = cx; this.cz = cz; }

    public static int idx(int x, int y, int z) { return (y * SZ + z) * SX + x; }
    public boolean inBounds(int x, int y, int z) {
        return x >= 0 && x < SX && y >= 0 && y < SY && z >= 0 && z < SZ;
    }
    public Block get(int x, int y, int z) {
        if (!inBounds(x, y, z)) return Block.AIR;
        return Block.byId(blocks[idx(x, y, z)] & 0xff);
    }
    public void set(int x, int y, int z, Block b) {
        if (!inBounds(x, y, z)) return;
        int i = idx(x, y, z);
        blocks[i] = (byte) b.ordinal();
        meta[i] = 0; // metadata never outlives the block it described
        dirty = true;
    }
    /** Water flow level (0 source, 1-7 flowing, 8 falling); 0 for non-water. */
    public int getMeta(int x, int y, int z) {
        if (!inBounds(x, y, z)) return 0;
        return meta[idx(x, y, z)] & 0xff;
    }
    public void setMeta(int x, int y, int z, int value) {
        if (!inBounds(x, y, z)) return;
        meta[idx(x, y, z)] = (byte) value;
        dirty = true;
    }
    public int worldX(int lx) { return cx * SX + lx; }
    public int worldZ(int lz) { return cz * SZ + lz; }
}
