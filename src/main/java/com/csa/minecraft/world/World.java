package com.csa.minecraft.world;

import org.joml.Vector3f;

import java.util.*;

public class World {
    public static final int DEFAULT_RENDER_DIST = 6;
    private int renderDist = DEFAULT_RENDER_DIST;
    private final Map<Long, Chunk> chunks = new HashMap<>();
    private final TerrainGenerator gen;

    public World(long seed) { this.gen = new TerrainGenerator(seed); }

    public int renderDistance() { return renderDist; }
    public void setRenderDistance(int d) { this.renderDist = Math.max(1, d); }

    public static long key(int cx, int cz) { return ((long) cx << 32) ^ (cz & 0xffffffffL); }

    public Chunk chunkAt(int cx, int cz) {
        return chunks.computeIfAbsent(key(cx, cz), k -> {
            Chunk c = new Chunk(cx, cz);
            gen.generate(c);
            return c;
        });
    }

    public Collection<Chunk> loaded() { return chunks.values(); }

    public Block getBlock(int wx, int wy, int wz) {
        if (wy < 0 || wy >= Chunk.SY) return Block.AIR;
        int cx = Math.floorDiv(wx, Chunk.SX), cz = Math.floorDiv(wz, Chunk.SZ);
        Chunk c = chunks.get(key(cx, cz));
        if (c == null) return Block.AIR;
        return c.get(Math.floorMod(wx, Chunk.SX), wy, Math.floorMod(wz, Chunk.SZ));
    }

    public void setBlock(int wx, int wy, int wz, Block b) {
        if (wy < 0 || wy >= Chunk.SY) return;
        int cx = Math.floorDiv(wx, Chunk.SX), cz = Math.floorDiv(wz, Chunk.SZ);
        Chunk c = chunkAt(cx, cz);
        int lx = Math.floorMod(wx, Chunk.SX), lz = Math.floorMod(wz, Chunk.SZ);
        c.set(lx, wy, lz, b);
        // mark neighbors dirty on borders
        if (lx == 0)            markDirty(cx - 1, cz);
        if (lx == Chunk.SX - 1) markDirty(cx + 1, cz);
        if (lz == 0)            markDirty(cx, cz - 1);
        if (lz == Chunk.SZ - 1) markDirty(cx, cz + 1);
    }
    private void markDirty(int cx, int cz) {
        Chunk c = chunks.get(key(cx, cz));
        if (c != null) c.dirty = true;
    }

    public void update(Vector3f playerPos) {
        int pcx = (int) Math.floor(playerPos.x / Chunk.SX);
        int pcz = (int) Math.floor(playerPos.z / Chunk.SZ);
        int r = renderDist;
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                chunkAt(pcx + dx, pcz + dz);
            }
        }
        // unload distant
        int unloadR = r + 2;
        chunks.entrySet().removeIf(e -> {
            Chunk c = e.getValue();
            if (Math.abs(c.cx - pcx) > unloadR || Math.abs(c.cz - pcz) > unloadR) {
                if (c.mesh != null) c.mesh.destroy();
                return true;
            }
            return false;
        });
    }
}
