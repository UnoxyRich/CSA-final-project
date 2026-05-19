package com.csa.minecraft.player;

import com.csa.minecraft.world.Block;
import com.csa.minecraft.world.World;
import org.joml.Vector3f;

public class Raycaster {
    public static class Hit {
        public int x, y, z;       // hit block coords
        public int px, py, pz;    // adjacent (placement) coords
    }

    public static Hit cast(World world, Vector3f origin, Vector3f dir, float maxDist) {
        // Amanatides & Woo voxel traversal
        int x = (int) Math.floor(origin.x);
        int y = (int) Math.floor(origin.y);
        int z = (int) Math.floor(origin.z);
        int stepX = dir.x > 0 ? 1 : -1;
        int stepY = dir.y > 0 ? 1 : -1;
        int stepZ = dir.z > 0 ? 1 : -1;
        float tDeltaX = Math.abs(1f / (dir.x == 0 ? 1e-9f : dir.x));
        float tDeltaY = Math.abs(1f / (dir.y == 0 ? 1e-9f : dir.y));
        float tDeltaZ = Math.abs(1f / (dir.z == 0 ? 1e-9f : dir.z));
        float tMaxX = ((stepX > 0 ? (x + 1 - origin.x) : (origin.x - x)) ) * tDeltaX;
        float tMaxY = ((stepY > 0 ? (y + 1 - origin.y) : (origin.y - y)) ) * tDeltaY;
        float tMaxZ = ((stepZ > 0 ? (z + 1 - origin.z) : (origin.z - z)) ) * tDeltaZ;
        int lastFace = -1;
        float t = 0;
        while (t <= maxDist) {
            if (world.getBlock(x, y, z).solid) {
                Hit h = new Hit();
                h.x = x; h.y = y; h.z = z;
                h.px = x; h.py = y; h.pz = z;
                switch (lastFace) {
                    case 0: h.px -= stepX; break;
                    case 1: h.py -= stepY; break;
                    case 2: h.pz -= stepZ; break;
                }
                return h;
            }
            if (tMaxX < tMaxY) {
                if (tMaxX < tMaxZ) { x += stepX; t = tMaxX; tMaxX += tDeltaX; lastFace = 0; }
                else { z += stepZ; t = tMaxZ; tMaxZ += tDeltaZ; lastFace = 2; }
            } else {
                if (tMaxY < tMaxZ) { y += stepY; t = tMaxY; tMaxY += tDeltaY; lastFace = 1; }
                else { z += stepZ; t = tMaxZ; tMaxZ += tDeltaZ; lastFace = 2; }
            }
        }
        return null;
    }
}
