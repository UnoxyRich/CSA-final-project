package com.csa.minecraft.entity;

import com.csa.minecraft.world.Block;
import com.csa.minecraft.world.Chunk;
import com.csa.minecraft.world.World;
import org.joml.Vector3f;

public class MilkFrog {
    public static final float WIDTH = 0.92f;
    public static final float HEIGHT = 1.58f;
    private static final float SPEED = 2.2f;
    private static final float STOP_DISTANCE = 1.45f;
    private static final float GROUND_SKIN = 0.01f;

    private final Vector3f pos;
    private float yaw;
    private float hopTime;

    public MilkFrog(Vector3f start) {
        this.pos = start;
    }

    public Vector3f position() { return pos; }
    public float yaw() { return yaw; }
    public float hopTime() { return hopTime; }

    public void update(float dt, World world, Vector3f target) {
        snapToGround(world);

        float dx = target.x - pos.x;
        float dz = target.z - pos.z;
        float dist2 = dx * dx + dz * dz;
        if (dist2 > 0.0001f) {
            yaw = (float) Math.atan2(-dx, -dz);
        }
        if (dist2 > STOP_DISTANCE * STOP_DISTANCE) {
            float dist = (float) Math.sqrt(dist2);
            float step = SPEED * dt;
            float mx = dx / dist * step;
            float mz = dz / dist * step;
            moveHorizontal(world, mx, mz);
            hopTime += dt * 7.5f;
        } else {
            hopTime += dt * 2.0f;
        }

        snapToGround(world);
    }

    public static MilkFrog nearPlayer(World world, Vector3f playerPos) {
        int px = (int) Math.floor(playerPos.x);
        int pz = (int) Math.floor(playerPos.z);
        int[][] offsets = {
            {4, 4}, {-4, 4}, {4, -4}, {-4, -4},
            {6, 0}, {-6, 0}, {0, 6}, {0, -6}
        };
        for (int[] offset : offsets) {
            Vector3f spawn = spawnAtColumn(world, px + offset[0], pz + offset[1]);
            if (spawn != null) return new MilkFrog(spawn);
        }
        return new MilkFrog(new Vector3f(playerPos.x + 3f, playerPos.y, playerPos.z + 3f));
    }

    private void moveHorizontal(World world, float mx, float mz) {
        pos.x += mx;
        if (collides(world)) pos.x -= mx;
        pos.z += mz;
        if (collides(world)) pos.z -= mz;
    }

    private void snapToGround(World world) {
        Vector3f ground = spawnAtColumn(world, (int) Math.floor(pos.x), (int) Math.floor(pos.z));
        if (ground != null) pos.y = ground.y;
    }

    private boolean collides(World world) {
        float hw = WIDTH / 2f;
        int minX = (int) Math.floor(pos.x - hw);
        int maxX = (int) Math.floor(pos.x + hw);
        int minY = (int) Math.floor(pos.y);
        int maxY = (int) Math.floor(pos.y + HEIGHT);
        int minZ = (int) Math.floor(pos.z - hw);
        int maxZ = (int) Math.floor(pos.z + hw);
        for (int x = minX; x <= maxX; x++)
            for (int y = minY; y <= maxY; y++)
                for (int z = minZ; z <= maxZ; z++)
                    if (world.getBlock(x, y, z).solid) return true;
        return false;
    }

    private static Vector3f spawnAtColumn(World world, int x, int z) {
        for (int y = Chunk.SY - 3; y >= 0; y--) {
            if (!isGround(world.getBlock(x, y, z))) continue;
            if (world.getBlock(x, y + 1, z) != Block.AIR) continue;
            return new Vector3f(x + 0.5f, y + 1f + GROUND_SKIN, z + 0.5f);
        }
        return null;
    }

    private static boolean isGround(Block block) {
        return switch (block) {
            case GRASS, DIRT, STONE, SAND, SNOW, ICE, SANDSTONE, DRY_GRASS -> true;
            default -> false;
        };
    }
}
