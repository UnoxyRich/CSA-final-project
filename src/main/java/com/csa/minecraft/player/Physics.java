package com.csa.minecraft.player;

import com.csa.minecraft.world.World;
import org.joml.Vector3f;

public class Physics {
    public static void moveAndCollide(Player p, World world, float dt) {
        Vector3f pos = p.position();
        Vector3f vel = p.velocity();
        float hw = Player.WIDTH / 2;
        float h = Player.HEIGHT;

        // X
        float dx = vel.x * dt;
        pos.x += dx;
        if (collides(world, pos, hw, h)) {
            // step back
            float sign = Math.signum(dx);
            while (collides(world, pos, hw, h)) pos.x -= sign * 0.01f;
            vel.x = 0;
        }

        // Y
        float dy = vel.y * dt;
        pos.y += dy;
        p.setOnGround(false);
        if (collides(world, pos, hw, h)) {
            float sign = Math.signum(dy);
            while (collides(world, pos, hw, h)) pos.y -= sign * 0.01f;
            if (dy < 0) p.setOnGround(true);
            vel.y = 0;
        }

        // Z
        float dz = vel.z * dt;
        pos.z += dz;
        if (collides(world, pos, hw, h)) {
            float sign = Math.signum(dz);
            while (collides(world, pos, hw, h)) pos.z -= sign * 0.01f;
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
