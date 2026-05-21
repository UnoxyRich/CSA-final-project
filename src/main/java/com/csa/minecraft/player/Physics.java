package com.csa.minecraft.player;

import com.csa.minecraft.world.World;
import org.joml.Vector3f;

public class Physics {
    // Tiny gap kept between the player AABB and a solid block face. Without it the
    // resolved edge would sit exactly on an integer coordinate, and floor() of that
    // edge would re-detect the block next frame, causing a 1-frame re-resolve flicker.
    private static final float SKIN = 1e-4f;

    public static void moveAndCollide(Player p, World world, float dt) {
        Vector3f pos = p.position();
        Vector3f vel = p.velocity();
        float hw = Player.WIDTH / 2;
        float h = Player.HEIGHT;

        // X — snap the leading face exactly to the penetrated block.
        float dx = vel.x * dt;
        pos.x += dx;
        if (collides(world, pos, hw, h)) {
            if (dx > 0) pos.x = (float) Math.floor(pos.x + hw) - hw - SKIN;
            else if (dx < 0) pos.x = (float) Math.floor(pos.x - hw) + 1 + hw + SKIN;
            vel.x = 0;
        }

        // Y
        float dy = vel.y * dt;
        pos.y += dy;
        p.setOnGround(false);
        if (collides(world, pos, hw, h)) {
            if (dy > 0) {
                // head hit a ceiling — rest it just below the block's underside
                pos.y = (float) Math.floor(pos.y + h) - h - SKIN;
            } else if (dy < 0) {
                // feet hit ground — rise by whole blocks to the surface landed on
                // (a single fast frame can drop the feet past more than one block)
                pos.y = (float) Math.floor(pos.y);
                while (collides(world, pos, hw, h)) pos.y += 1f;
                pos.y += SKIN;
                p.setOnGround(true);
            }
            vel.y = 0;
        }

        // Z
        float dz = vel.z * dt;
        pos.z += dz;
        if (collides(world, pos, hw, h)) {
            if (dz > 0) pos.z = (float) Math.floor(pos.z + hw) - hw - SKIN;
            else if (dz < 0) pos.z = (float) Math.floor(pos.z - hw) + 1 + hw + SKIN;
            vel.z = 0;
        }
    }

    private static boolean collides(World world, Vector3f pos, float hw, float h) {
        int minX = (int) Math.floor(pos.x - hw);
        int maxX = (int) Math.floor(pos.x + hw);
        int minY = (int) Math.floor(pos.y);
        int maxY = (int) Math.floor(pos.y + h);
        int minZ = (int) Math.floor(pos.z - hw);
        int maxZ = (int) Math.floor(pos.z + hw);
        for (int x = minX; x <= maxX; x++)
            for (int y = minY; y <= maxY; y++)
                for (int z = minZ; z <= maxZ; z++)
                    if (world.getBlock(x, y, z).solid) return true;
        return false;
    }
}
