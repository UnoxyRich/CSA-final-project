package com.csa.minecraft.player;

import com.csa.minecraft.GameMode;
import com.csa.minecraft.Settings;
import com.csa.minecraft.engine.Input;
import com.csa.minecraft.world.Block;
import com.csa.minecraft.world.World;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

class PlayerMovementTest {
    private static final float EPS = 0.01f;

    @Test
    void holdingForwardUsesBaseWalkSpeed() {
        World world = new World(1L);
        Player player = new Player(new Vector3f(8.5f, 120f, 8.5f));
        player.setOnGround(true);
        TestInput input = new TestInput();
        input.setKey(GLFW_KEY_W, true);

        player.update(0.2f, input, world);

        assertFalse(player.isSprinting());
        assertEquals(-Player.SPEED, player.velocity().z, EPS);
    }

    @Test
    void holdingControlAndForwardSprints() {
        World world = new World(1L);
        Player player = new Player(new Vector3f(8.5f, 120f, 8.5f));
        player.setOnGround(true);
        TestInput input = new TestInput();
        input.setKey(GLFW_KEY_W, true);
        input.setKey(GLFW_KEY_LEFT_CONTROL, true);

        player.update(0.2f, input, world);

        assertTrue(player.isSprinting());
        assertTrue(player.velocity().z < -Player.SPEED);
    }

    @Test
    void controlDoesNotSprintWhenMovingBackward() {
        World world = new World(1L);
        Player player = new Player(new Vector3f(8.5f, 120f, 8.5f));
        player.setOnGround(true);
        TestInput input = new TestInput();
        input.setKey(GLFW_KEY_S, true);
        input.setKey(GLFW_KEY_LEFT_CONTROL, true);

        player.update(0.2f, input, world);

        assertFalse(player.isSprinting());
        assertEquals(Player.SPEED, player.velocity().z, EPS);
    }

    @Test
    void touchingCactusDealsDamageInSurvival() {
        World world = new World(1L);
        addFloor(world);
        world.setBlock(9, 120, 8, Block.CACTUS);
        Player player = new Player(new Vector3f(8.65f, 120f, 8.5f));

        player.update(0.1f, new TestInput(), world);

        assertEquals(Player.MAX_HEALTH - 1f, player.health(), EPS);
        assertTrue(player.damageShake() > 0f);
    }

    @Test
    void cactusDamageHasCooldown() {
        World world = new World(1L);
        addFloor(world);
        world.setBlock(9, 120, 8, Block.CACTUS);
        Player player = new Player(new Vector3f(8.65f, 120f, 8.5f));
        TestInput input = new TestInput();

        player.update(0.1f, input, world);
        player.update(0.1f, input, world);

        assertEquals(Player.MAX_HEALTH - 1f, player.health(), EPS);

        player.update(0.5f, input, world);

        assertEquals(Player.MAX_HEALTH - 2f, player.health(), EPS);
    }

    @Test
    void damageShakeDecaysAfterDamage() {
        World world = new World(1L);
        addFloor(world);
        world.setBlock(9, 120, 8, Block.CACTUS);
        Player player = new Player(new Vector3f(8.65f, 120f, 8.5f));
        TestInput input = new TestInput();

        player.update(0.1f, input, world);
        float initialShake = player.damageShake();
        player.update(0.1f, input, world);

        assertTrue(initialShake > 0f);
        assertTrue(player.damageShake() < initialShake);
    }

    @Test
    void cactusDoesNotDamageCreativePlayer() {
        World world = new World(1L);
        addFloor(world);
        world.setBlock(9, 120, 8, Block.CACTUS);
        Settings settings = new Settings();
        settings.gameMode = GameMode.CREATIVE;
        Player player = new Player(new Vector3f(8.65f, 120f, 8.5f), settings);

        player.update(0.1f, new TestInput(), world);

        assertEquals(Player.MAX_HEALTH, player.health(), EPS);
    }

    private static void addFloor(World world) {
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                world.setBlock(8 + dx, 119, 8 + dz, Block.STONE);
    }

    private static class TestInput extends Input {
        private final boolean[] keys = new boolean[GLFW_KEY_LAST + 1];

        void setKey(int key, boolean down) {
            keys[key] = down;
        }

        @Override
        public boolean key(int k) {
            return keys[k];
        }

        @Override
        public boolean cursorGrabbed() {
            return true;
        }
    }
}
