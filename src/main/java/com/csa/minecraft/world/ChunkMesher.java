package com.csa.minecraft.world;

import com.csa.minecraft.engine.Mesh;

import java.util.ArrayList;
import java.util.List;

public class ChunkMesher {
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

    private static final float[] AO_LEVELS = {0.65f, 0.78f, 0.89f, 1.00f};

    /** Transparent blocks (glass, water) and cutout leaves don't cast AO. */
    private static boolean isOccluder(Block b) {
        return b.solid && b != Block.GLASS && b != Block.WATER && !isLeaves(b);
    }

    private static boolean isTransparent(Block b) {
        return b == Block.GLASS || b == Block.WATER;
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

    public static void rebuild(Chunk c, World world) {
        List<Float> data = new ArrayList<>(4096);
        int atlasSize = 8; // must match Texture (8x8 = 64 tiles)
        float uStep = 1f / atlasSize;
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

                        int faceCat = (f == 2) ? 0 : (f == 3 ? 1 : 2);
                        int tile = b.atlasIndex(faceCat);
                        float u0 = (tile % atlasSize) * uStep;
                        float v0 = (tile / atlasSize) * uStep;
                        float[][] vs = FACE_VERTS[f];

                        // Per-vertex AO: each of the 4 corners samples its 2 edge-adjacent
                        // neighbors + 1 diagonal across the face normal. See vertexAO().
                        float[] ao = new float[4];
                        for (int k = 0; k < 4; k++) {
                            ao[k] = vertexAO(world, wx, y, wz, f,
                                             (int) vs[k][0], (int) vs[k][1], (int) vs[k][2]);
                        }

                        // Flip the quad's triangulation when AO is "twisted" so the gradient
                        // doesn't break across the diagonal seam. Standard Mojang trick.
                        boolean flip = ao[0] + ao[2] < ao[1] + ao[3];
                        int[] order = flip ? new int[]{1, 2, 3, 1, 3, 0}
                                           : new int[]{0, 1, 2, 0, 2, 3};
                        float[] nrm = FACE_NORMALS[f];
                        float[][] uvs = {{0,0},{0,1},{1,1},{1,0}};
                        for (int k : order) {
                            float vx = x + vs[k][0];
                            float vy = y + vs[k][1];
                            float vz = z + vs[k][2];
                            data.add(vx); data.add(vy); data.add(vz);
                            data.add(nrm[0]); data.add(nrm[1]); data.add(nrm[2]);
                            data.add(u0 + uvs[k][0] * uStep);
                            data.add(v0 + uvs[k][1] * uStep);
                            data.add(ao[k]);
                            data.add((float) b.ordinal());
                        }
                    }
                }
            }
        }
        float[] arr = new float[data.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = data.get(i);
        if (c.mesh != null) c.mesh.destroy();
        c.mesh = new Mesh();
        c.mesh.upload(arr, new int[]{3, 3, 2, 1, 1});
        c.dirty = false;
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
        int[] vert = {vx, vy, vz};
        int sa = vert[ta] * 2 - 1;          // -1 or +1: which side of the face
        int sb = vert[tb] * 2 - 1;

        int[] step = new int[3];
        step[faceAxis] = faceDir;

        Block s1 = sample(world, wx, y, wz, step, ta, sa, -1, 0);
        Block s2 = sample(world, wx, y, wz, step, tb, sb, -1, 0);
        Block cn = sample(world, wx, y, wz, step, ta, sa, tb, sb);

        boolean b1 = isOccluder(s1), b2 = isOccluder(s2), bc = isOccluder(cn);
        int level = (b1 && b2) ? 0
                                : 3 - ((b1 ? 1 : 0) + (b2 ? 1 : 0) + (bc ? 1 : 0));
        return AO_LEVELS[level];
    }

    private static Block sample(World world, int wx, int y, int wz, int[] step,
                                int axisA, int signA, int axisB, int signB) {
        int dx = step[0], dy = step[1], dz = step[2];
        if (axisA == 0) dx += signA; else if (axisA == 1) dy += signA; else if (axisA == 2) dz += signA;
        if (axisB == 0) dx += signB; else if (axisB == 1) dy += signB; else if (axisB == 2) dz += signB;
        return world.getBlock(wx + dx, y + dy, wz + dz);
    }
}
