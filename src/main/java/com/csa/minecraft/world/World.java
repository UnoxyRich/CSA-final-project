package com.csa.minecraft.world;

import com.csa.minecraft.engine.ChunkWorkerPool;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class World {
    public static final int DEFAULT_RENDER_DIST = 6;
    private static final int WATER_MAX_LEVEL = 7;
    private static final int WATER_FALLING = 8;
    private static final int WATER_TICK_INTERVAL = 4;
    private static final int WATER_SIM_DIST = 3;
    private int renderDist = DEFAULT_RENDER_DIST;
    // ConcurrentHashMap because worker threads read it (getBlock during async
    // meshing) while the main thread inserts/removes chunks. Only the main
    // thread ever mutates the map; workers only read.
    private final Map<Long, Chunk> chunks = new ConcurrentHashMap<>();
    private final WorldGenerator gen;
    private final ChunkWorkerPool workers;
    // Keys of chunks currently being generated on a worker — prevents the ring
    // scan from submitting the same chunk twice.
    private final Set<Long> generating = ConcurrentHashMap.newKeySet();
    // Chunks finished by workers, awaiting integration into the map on the
    // main thread. ConcurrentLinkedQueue establishes the happens-before that
    // publishes every block write a worker made before integration reads it.
    private final ConcurrentLinkedQueue<Chunk> generated = new ConcurrentLinkedQueue<>();
    private int waterTickCounter = 0;
    private long blockRevision = 0;

    public World(long seed, ChunkWorkerPool workers) {
        this.gen = new TerrainGenerator(seed);
        this.workers = workers;
    }

    /** Use a custom generator (e.g. NetherGenerator). */
    public World(WorldGenerator gen, ChunkWorkerPool workers) {
        this.gen = gen;
        this.workers = workers;
    }

    public int renderDistance() { return renderDist; }
    public void setRenderDistance(int d) { this.renderDist = Math.max(1, d); }
    public long blockRevision() { return blockRevision; }

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

    public int getBlockMeta(int wx, int wy, int wz) {
        if (wy < 0 || wy >= Chunk.SY) return 0;
        int cx = Math.floorDiv(wx, Chunk.SX), cz = Math.floorDiv(wz, Chunk.SZ);
        Chunk c = chunks.get(key(cx, cz));
        if (c == null) return 0;
        return c.getMeta(Math.floorMod(wx, Chunk.SX), wy, Math.floorMod(wz, Chunk.SZ));
    }

    public void setBlock(int wx, int wy, int wz, Block b) {
        if (wy < 0 || wy >= Chunk.SY) return;
        int cx = Math.floorDiv(wx, Chunk.SX), cz = Math.floorDiv(wz, Chunk.SZ);
        Chunk c = chunkAt(cx, cz);
        int lx = Math.floorMod(wx, Chunk.SX), lz = Math.floorMod(wz, Chunk.SZ);
        Block oldBlock = c.get(lx, wy, lz);
        int oldMeta = c.getMeta(lx, wy, lz);
        c.set(lx, wy, lz, b);
        if (oldBlock != b || oldMeta != 0) blockRevision++;
        markBlockDirty(cx, cz, lx, lz);
        // When obsidian is placed, check if a nether portal frame was completed.
        // NETHER_PORTAL placement itself does not trigger this (no infinite loop).
        if (b == Block.OBSIDIAN) {
            PortalHelper.checkAndActivate(this, wx, wy, wz);
        }
    }

    private void setLoadedBlockWithMeta(int wx, int wy, int wz, Block b, int meta) {
        if (wy < 0 || wy >= Chunk.SY) return;
        int cx = Math.floorDiv(wx, Chunk.SX), cz = Math.floorDiv(wz, Chunk.SZ);
        Chunk c = chunks.get(key(cx, cz));
        if (c == null) return;
        int lx = Math.floorMod(wx, Chunk.SX), lz = Math.floorMod(wz, Chunk.SZ);
        Block oldBlock = c.get(lx, wy, lz);
        int oldMeta = c.getMeta(lx, wy, lz);
        c.set(lx, wy, lz, b);
        if (b == Block.WATER) c.setMeta(lx, wy, lz, meta);
        int newMeta = b == Block.WATER ? meta : 0;
        if (oldBlock != b || oldMeta != newMeta) blockRevision++;
        markBlockDirty(cx, cz, lx, lz);
    }

    private void markBlockDirty(int cx, int cz, int lx, int lz) {
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

        // 1. Integrate chunks finished by worker threads. This is cheap (map
        // puts + dirty flags), so the whole queue is drained every frame. If a
        // synchronous chunkAt() already created the chunk, putIfAbsent keeps it
        // and the worker's copy is discarded.
        Chunk done;
        while ((done = generated.poll()) != null) {
            long k = key(done.cx, done.cz);
            generating.remove(k);
            if (chunks.putIfAbsent(k, done) == null) {
                // Neighbours must remesh so their shared border faces cull
                // against the freshly arrived chunk instead of showing seams.
                markDirty(done.cx - 1, done.cz);
                markDirty(done.cx + 1, done.cz);
                markDirty(done.cx, done.cz - 1);
                markDirty(done.cx, done.cz + 1);
            }
        }

        // 2. Unload chunks outside the keep radius.
        int unloadR = r + 2;
        chunks.entrySet().removeIf(e -> {
            Chunk c = e.getValue();
            if (Math.abs(c.cx - pcx) > unloadR || Math.abs(c.cz - pcz) > unloadR) {
                c.unloaded = true; // a mesh job for it may still be in flight
                if (c.mesh != null) c.mesh.destroy();
                if (c.transparentMesh != null) c.transparentMesh.destroy();
                return true;
            }
            return false;
        });

        // 3. Queue generation for every missing in-range chunk, nearest ring
        // first. Generation runs on the worker pool, so there is no per-frame
        // cap; the `generating` set stops a chunk being submitted twice.
        for (int ring = 0; ring <= r; ring++) {
            for (int dz = -ring; dz <= ring; dz++) {
                for (int dx = -ring; dx <= ring; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                    int cx = pcx + dx, cz = pcz + dz;
                    long k = key(cx, cz);
                    if (chunks.containsKey(k) || !generating.add(k)) continue;
                    workers.submit(() -> {
                        Chunk c = new Chunk(cx, cz);
                        gen.generate(c);
                        generated.add(c);
                    });
                }
            }
        }

        if (++waterTickCounter >= WATER_TICK_INTERVAL) {
            waterTickCounter = 0;
            updateWater(pcx, pcz);
        }
    }

    private void updateWater(int pcx, int pcz) {
        Map<Long, Integer> desired = new HashMap<>();
        ArrayDeque<WaterNode> queue = new ArrayDeque<>();
        for (Chunk c : chunks.values()) {
            if (!waterChunkInRange(c, pcx, pcz) || c.waterCount == 0) continue;
            for (int y = 0; y < Chunk.SY; y++) {
                for (int z = 0; z < Chunk.SZ; z++) {
                    for (int x = 0; x < Chunk.SX; x++) {
                        if (c.get(x, y, z) != Block.WATER) continue;
                        if (c.getMeta(x, y, z) != 0) continue;
                        int wx = c.worldX(x);
                        int wz = c.worldZ(z);
                        queue.add(new WaterNode(wx, y, wz, 0));
                    }
                }
            }
        }

        while (!queue.isEmpty()) {
            WaterNode n = queue.removeFirst();
            if (canWaterFlowInto(n.x, n.y - 1, n.z)) {
                if (addDesiredWater(desired, n.x, n.y - 1, n.z, WATER_FALLING)) {
                    queue.add(new WaterNode(n.x, n.y - 1, n.z, WATER_FALLING));
                }
                continue;
            }
            if (n.level != WATER_FALLING && n.level >= WATER_MAX_LEVEL) continue;
            int next = n.level == WATER_FALLING ? 1 : n.level + 1;
            addHorizontalWater(desired, queue, n.x + 1, n.y, n.z, next);
            addHorizontalWater(desired, queue, n.x - 1, n.y, n.z, next);
            addHorizontalWater(desired, queue, n.x, n.y, n.z + 1, next);
            addHorizontalWater(desired, queue, n.x, n.y, n.z - 1, next);
        }

        List<long[]> removals = new ArrayList<>();
        for (Chunk c : chunks.values()) {
            if (!waterChunkInRange(c, pcx, pcz) || c.waterCount == 0) continue;
            for (int y = 0; y < Chunk.SY; y++) {
                for (int z = 0; z < Chunk.SZ; z++) {
                    for (int x = 0; x < Chunk.SX; x++) {
                        if (c.get(x, y, z) == Block.WATER && c.getMeta(x, y, z) != 0) {
                            int wx = c.worldX(x);
                            int wz = c.worldZ(z);
                            long waterKey = waterKey(wx, y, wz);
                            if (!desired.containsKey(waterKey)) removals.add(new long[]{wx, y, wz});
                        }
                    }
                }
            }
        }

        for (long[] p : removals) {
            setLoadedBlockWithMeta((int) p[0], (int) p[1], (int) p[2], Block.AIR, 0);
        }
        for (Map.Entry<Long, Integer> e : desired.entrySet()) {
            int wx = waterX(e.getKey());
            int wy = waterY(e.getKey());
            int wz = waterZ(e.getKey());
            if (canWaterFlowInto(wx, wy, wz)) {
                setLoadedBlockWithMeta(wx, wy, wz, Block.WATER, e.getValue());
            } else if (getBlock(wx, wy, wz) == Block.WATER && getBlockMeta(wx, wy, wz) != 0
                    && getBlockMeta(wx, wy, wz) != e.getValue()) {
                setLoadedBlockWithMeta(wx, wy, wz, Block.WATER, e.getValue());
            }
        }
    }

    private boolean waterChunkInRange(Chunk c, int pcx, int pcz) {
        return Math.abs(c.cx - pcx) <= WATER_SIM_DIST && Math.abs(c.cz - pcz) <= WATER_SIM_DIST;
    }

    private void addHorizontalWater(Map<Long, Integer> desired, ArrayDeque<WaterNode> queue,
                                    int wx, int wy, int wz, int level) {
        if (!canWaterFlowInto(wx, wy, wz)) return;
        if (canWaterFlowInto(wx, wy - 1, wz)) {
            if (addDesiredWater(desired, wx, wy, wz, WATER_FALLING)) {
                queue.add(new WaterNode(wx, wy, wz, WATER_FALLING));
            }
        } else {
            if (addDesiredWater(desired, wx, wy, wz, level)) {
                queue.add(new WaterNode(wx, wy, wz, level));
            }
        }
    }

    private boolean addDesiredWater(Map<Long, Integer> desired, int wx, int wy, int wz, int level) {
        if (wy < 0 || wy >= Chunk.SY || !isChunkLoaded(wx, wz)) return false;
        if (getBlock(wx, wy, wz) == Block.WATER && getBlockMeta(wx, wy, wz) == 0) return false;
        long k = waterKey(wx, wy, wz);
        Integer old = desired.get(k);
        if (old != null && old <= level) return false;
        desired.put(k, level);
        return true;
    }

    private boolean canWaterFlowInto(int wx, int wy, int wz) {
        if (wy < 0 || wy >= Chunk.SY || !isChunkLoaded(wx, wz)) return false;
        Block b = getBlock(wx, wy, wz);
        return b == Block.AIR || b == Block.WATER;
    }

    private boolean isChunkLoaded(int wx, int wz) {
        int cx = Math.floorDiv(wx, Chunk.SX), cz = Math.floorDiv(wz, Chunk.SZ);
        return chunks.containsKey(key(cx, cz));
    }

    private static long waterKey(int x, int y, int z) {
        return (((long) x & 0x3ffffffL) << 38)
            | (((long) z & 0x3ffffffL) << 12)
            | ((long) y & 0xfffL);
    }

    private static int waterX(long key) {
        return signExtend((int) (key >> 38), 26);
    }

    private static int waterZ(long key) {
        return signExtend((int) ((key >> 12) & 0x3ffffffL), 26);
    }

    private static int waterY(long key) {
        return (int) (key & 0xfffL);
    }

    private static int signExtend(int value, int bits) {
        int shift = 32 - bits;
        return (value << shift) >> shift;
    }

    private record WaterNode(int x, int y, int z, int level) {}
}
