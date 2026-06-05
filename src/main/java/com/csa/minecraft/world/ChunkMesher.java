package com.csa.minecraft.world;

import java.util.Arrays;

public class ChunkMesher {
    /** Vertex attribute layout of the arrays buildMesh() returns: pos3, nrm3, uv2, ao1, id1. */
    public static final int[] LAYOUT = {3, 3, 2, 1, 1};

    /**
     * The two vertex arrays a build produces: opaque geometry and the
     * separately-blended transparent geometry (glass/water). Both use
     * {@link #LAYOUT}. The caller uploads them to GL on the main thread.
     */
    public record MeshData(float[] opaque, float[] transparent) {}

    // face order: +X, -X, +Y(top), -Y(bottom), +Z, -Z
    private static final int[][] OFFSETS = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};

    // For each face, 4 vertex offsets (CCW when viewed from outside)
    private static final float[][][] FACE_VERTS = {
        {{1,0,0},{1,1,0},{1,1,1},{1,0,1}},
        {{0,0,1},{0,1,1},{0,1,0},{0,0,0}},
        {{0,1,1},{1,1,1},{1,1,0},{0,1,0}},
        {{0,0,0},{1,0,0},{1,0,1},{0,0,1}},
        {{1,0,1},{1,1,1},{0,1,1},{0,0,1}},
        {{0,0,0},{0,1,0},{1,1,0},{1,0,0}},
    };

    private static final float[][] FACE_NORMALS = {
        { 1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0,-1, 0}, {0, 0, 1}, {0, 0,-1},
    };

    // Pyramid normals for ROCKET block side faces: outward + 26.6° upward (atan(0.5)).
    // Normalized components of (±1, 0.5, 0) and (0, 0.5, ±1).
    private static final float[][] ROCKET_NORMALS = {
        { 0.894f, 0.447f,  0.000f},  // f=0: +X sloped up
        {-0.894f, 0.447f,  0.000f},  // f=1: -X sloped up
        { 0.000f, 1.000f,  0.000f},  // f=2: +Y (face is skipped for rockets)
        { 0.000f,-1.000f,  0.000f},  // f=3: -Y bottom, same as cube
        { 0.000f, 0.447f,  0.894f},  // f=4: +Z sloped up
        { 0.000f, 0.447f, -0.894f},  // f=5: -Z sloped up
    };

    private static final float[] AO_LEVELS = {0.65f, 0.78f, 0.89f, 1.00f};

    // Hoisted constants: these never change, so allocating them per face (as the
    // old code did, ~44k times per chunk) was pure garbage. See buildMesh().
    private static final float[][] FACE_UVS = {{0,0},{0,1},{1,1},{1,0}};
    private static final int[] ORDER_NORMAL = {0, 1, 2, 0, 2, 3};
    private static final int[] ORDER_FLIP   = {1, 2, 3, 1, 3, 0};
    private static final int FLOATS_PER_VERTEX = 10; // pos3 + nrm3 + uv2 + ao1 + id1

    /** Transparent blocks (glass, water) and cutout leaves don't cast AO. */
    private static boolean isOccluder(Block b) {
        return b.solid && b != Block.GLASS && b != Block.WATER && !isLeaves(b);
    }

    private static boolean isTransparent(Block b) {
        return b == Block.GLASS || b == Block.WATER || b == Block.NETHER_PORTAL;
    }

    /**
     * Leaves use a cutout (alpha-tested) texture. Like glass/water, their faces
     * must not be culled away by adjacent opaque blocks. But unlike glass/water
     * they keep the faces shared with neighbouring leaves, so the canopy renders
     * as a dense mass instead of a see-through shell.
     */
    private static boolean isLeaves(Block b) {
        return b == Block.LEAVES || b == Block.CHERRY_LEAVES || b == Block.SPRUCE_LEAVES;
    }

    /**
     * Builds the interleaved vertex arrays for a chunk and returns them. This is
     * pure CPU work — it only reads block data — so it runs on a worker thread.
     * Glass/water faces go to {@link MeshData#transparent()} for the blended
     * pass, everything else to {@link MeshData#opaque()}. The caller uploads the
     * result to GL buffers on the main thread (see {@link #LAYOUT}); GL objects
     * must never be touched off the GL thread.
     */
    public static MeshData buildMesh(Chunk c, World world) {
        // Write vertices straight into a growable float[]: no Float boxing, no
        // per-face/per-vertex allocations. The only garbage a build produces
        // now is these arrays (and their final trim), which keeps GC pauses, the
        // main cause of frame-time spikes, to a minimum.
        FloatBuilder opaque = new FloatBuilder(4096);
        FloatBuilder transparent = new FloatBuilder(1024);
        int atlasSize = 8; // must match Texture (8x8 = 64 tiles)
        float uStep = 1f / atlasSize;
        float[] ao = new float[4]; // reused for every face
        for (int y = 0; y < Chunk.SY; y++) {
            for (int z = 0; z < Chunk.SZ; z++) {
                for (int x = 0; x < Chunk.SX; x++) {
                    Block b = c.get(x, y, z);
                    if (b == Block.AIR) continue;
                    int wx = c.worldX(x), wz = c.worldZ(z);
                    for (int f = 0; f < 6; f++) {
                        int nx = x + OFFSETS[f][0], ny = y + OFFSETS[f][1], nz = z + OFFSETS[f][2];
                        Block n;
                        if (ny < 0 || ny >= Chunk.SY) n = Block.AIR;
                        else if (nx < 0 || nx >= Chunk.SX || nz < 0 || nz >= Chunk.SZ) {
                            n = world.getBlock(wx + OFFSETS[f][0], ny, wz + OFFSETS[f][2]);
                        } else {
                            n = c.get(nx, ny, nz);
                        }
                        if (n.solid && !isTransparent(n) && !isLeaves(n)) continue;
                        if (n == b && isTransparent(b)) continue;
                        if (b == Block.ROCKET && f == 2) continue; // nose cone: no flat top

                        int faceCat = (f == 2) ? 0 : (f == 3 ? 1 : 2);
                        int tile = b.atlasIndex(faceCat);
                        float u0 = (tile % atlasSize) * uStep;
                        float v0 = (tile / atlasSize) * uStep;
                        float[][] vs = FACE_VERTS[f];

                        // Per-vertex AO: each of the 4 corners samples its 2 edge-adjacent
                        // neighbors + 1 diagonal across the face normal. See vertexAO().
                        for (int k = 0; k < 4; k++) {
                            ao[k] = vertexAO(world, wx, y, wz, f,
                                             (int) vs[k][0], (int) vs[k][1], (int) vs[k][2]);
                        }

                        // Flip the quad's triangulation when AO is "twisted" so the gradient
                        // doesn't break across the diagonal seam. Standard Mojang trick.
                        boolean flip = ao[0] + ao[2] < ao[1] + ao[3];
                        int[] order = flip ? ORDER_FLIP : ORDER_NORMAL;
                        boolean rocketSide = (b == Block.ROCKET && f != 3);
                        float[] nrm = rocketSide ? ROCKET_NORMALS[f] : FACE_NORMALS[f];

                        // A face emits 6 vertices; grow once up front, then write.
                        FloatBuilder out = isTransparent(b) ? transparent : opaque;
                        out.ensure(6 * FLOATS_PER_VERTEX);
                        for (int k : order) {
                            float vx = vs[k][0], vy = vs[k][1], vz = vs[k][2];
                            // Rocket side faces taper all top vertices (vy==1) to the
                            // block centre, forming a pyramid nose-cone shape.
                            if (rocketSide && vy > 0f) { vx = 0.5f; vz = 0.5f; }
                            out.add(x + vx);
                            out.add(y + vertexY(b, c.getMeta(x, y, z), vy));
                            out.add(z + vz);
                            out.add(nrm[0]);
                            out.add(nrm[1]);
                            out.add(nrm[2]);
                            out.add(u0 + FACE_UVS[k][0] * uStep);
                            out.add(v0 + FACE_UVS[k][1] * uStep);
                            out.add(ao[k]);
                            out.add(b.ordinal());
                        }
                    }
                }
            }
        }
        return new MeshData(opaque.toArray(), transparent.toArray());
    }

    private static float vertexY(Block b, int meta, float vy) {
        if (b != Block.WATER || vy <= 0f) return vy;
        return waterHeight(meta);
    }

    private static float waterHeight(int meta) {
        if (meta == 8) return 1.0f;
        return Math.max(0.2f, 0.9f - meta * 0.1f);
    }

    /**
     * Classic voxel AO: at each vertex of a face, sample the two edge-adjacent neighbors
     * and the diagonal block in the direction of the face normal. The more of those are
     * solid, the more occluded the corner. If both edge neighbors are solid the corner is
     * pinched closed and AO is clamped to the darkest level regardless of the diagonal.
     */
    private static float vertexAO(World world, int wx, int y, int wz,
                                   int face, int vx, int vy, int vz) {
        int faceAxis = face / 2;            // 0=X, 1=Y, 2=Z
        int faceDir  = (face % 2 == 0) ? 1 : -1;
        int ta = (faceAxis + 1) % 3;
        int tb = (faceAxis + 2) % 3;
        int sa = comp(vx, vy, vz, ta) * 2 - 1;  // -1 or +1: which side of the face
        int sb = comp(vx, vy, vz, tb) * 2 - 1;

        Block s1 = sample(world, wx, y, wz, faceAxis, faceDir, ta, sa, -1, 0);
        Block s2 = sample(world, wx, y, wz, faceAxis, faceDir, tb, sb, -1, 0);
        Block cn = sample(world, wx, y, wz, faceAxis, faceDir, ta, sa, tb, sb);

        boolean b1 = isOccluder(s1), b2 = isOccluder(s2), bc = isOccluder(cn);
        int level = (b1 && b2) ? 0
                                : 3 - ((b1 ? 1 : 0) + (b2 ? 1 : 0) + (bc ? 1 : 0));
        return AO_LEVELS[level];
    }

    /** Picks the x/y/z component for a 0/1/2 axis index without an array. */
    private static int comp(int x, int y, int z, int axis) {
        return axis == 0 ? x : (axis == 1 ? y : z);
    }

    private static Block sample(World world, int wx, int y, int wz,
                                int faceAxis, int faceDir,
                                int axisA, int signA, int axisB, int signB) {
        int dx = 0, dy = 0, dz = 0;
        if (faceAxis == 0) dx = faceDir; else if (faceAxis == 1) dy = faceDir; else dz = faceDir;
        if (axisA == 0) dx += signA; else if (axisA == 1) dy += signA; else if (axisA == 2) dz += signA;
        if (axisB == 0) dx += signB; else if (axisB == 1) dy += signB; else if (axisB == 2) dz += signB;
        return world.getBlock(wx + dx, y + dy, wz + dz);
    }

    /** Growable float array — avoids Float boxing and per-face reallocation. */
    private static class FloatBuilder {
        private float[] data;
        private int len;

        FloatBuilder(int initialCapacity) {
            data = new float[initialCapacity];
        }

        void ensure(int extra) {
            int min = len + extra;
            if (min <= data.length) return;
            int n = data.length * 2;
            while (n < min) n *= 2;
            data = Arrays.copyOf(data, n);
        }

        void add(float value) {
            data[len++] = value;
        }

        float[] toArray() {
            return len == data.length ? data : Arrays.copyOf(data, len);
        }
    }
}
