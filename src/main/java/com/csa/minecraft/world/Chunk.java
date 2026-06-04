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
    // Glass/water faces are built into a second mesh so they can be drawn in a
    // separate alpha-blended pass after all opaque geometry.
    public Mesh transparentMesh;
    // dirty/unloaded cross the worker/main boundary, so they are volatile:
    // a generation worker fills a chunk and an integrated chunk may be unloaded
    // while a mesh job for it is still in flight.
    public volatile boolean dirty = true;
    public volatile boolean unloaded = false;
    // True while a mesh-build job for this chunk is queued/running on a worker.
    // Main-thread only — guards against dispatching the same chunk twice.
    public boolean meshing = false;
    // Count of WATER cells in this chunk, kept current by set(). Lets the water
    // simulation skip the full-volume scan of chunks that hold no water.
    public int waterCount = 0;

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
        Block old = Block.byId(blocks[i] & 0xff);
        if (old == Block.WATER && b != Block.WATER) waterCount--;
        else if (old != Block.WATER && b == Block.WATER) waterCount++;
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
