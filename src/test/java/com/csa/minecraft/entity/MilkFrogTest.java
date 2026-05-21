package com.csa.minecraft.entity;

import com.csa.minecraft.world.Block;
import com.csa.minecraft.world.World;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MilkFrogTest {
    private static final float EPS = 0.05f;

    @Test
    void nearPlayerSpawnsOnGround() {
        World world = new World(1L);
        addFloor(world, 0, 15, 0, 15, 119);
        Vector3f player = new Vector3f(8.5f, 120.01f, 8.5f);

        MilkFrog frog = MilkFrog.nearPlayer(world, player);

        assertEquals(120.01f, frog.position().y, EPS);
        assertTrue(world.getBlock((int) Math.floor(frog.position().x), 119,
                                  (int) Math.floor(frog.position().z)).solid);
    }

    @Test
    void updateMovesTowardPlayer() {
        World world = new World(1L);
        addFloor(world, 0, 15, 0, 15, 119);
        Vector3f player = new Vector3f(8.5f, 120.01f, 8.5f);
        MilkFrog frog = new MilkFrog(new Vector3f(2.5f, 120.01f, 8.5f));
        float before = horizontalDistance(frog.position(), player);

        frog.update(0.5f, world, player);

        assertTrue(horizontalDistance(frog.position(), player) < before);
    }

    private static float horizontalDistance(Vector3f a, Vector3f b) {
        float dx = a.x - b.x;
        float dz = a.z - b.z;
        return (float) Math.sqrt(dx * dx + dz * dz);
    }

    private static void addFloor(World world, int minX, int maxX, int minZ, int maxZ, int y) {
        for (int x = minX; x <= maxX; x++)
            for (int z = minZ; z <= maxZ; z++)
                world.setBlock(x, y, z, Block.STONE);
    }
}
