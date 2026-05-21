package com.csa.minecraft.player;

import com.csa.minecraft.GameMode;
import com.csa.minecraft.Settings;
import com.csa.minecraft.engine.Camera;
import com.csa.minecraft.engine.Input;
import com.csa.minecraft.render.BlockEffectsRenderer;
import com.csa.minecraft.world.Block;
import com.csa.minecraft.world.World;
import org.joml.Vector3f;

import static org.lwjgl.glfw.GLFW.*;

public class Player {
    public static final float WIDTH = 0.6f, HEIGHT = 1.8f, EYE = 1.6f;
    public static final float SPEED = 5.0f, FLY_SPEED = 12.0f, JUMP = 9.0f, GRAVITY = 28.0f;
    public static final float REACH = 6.0f;
    private static final float CREATIVE_FLY_TAP_WINDOW = 0.28f;
    private static final float GROUND_ACCEL = 80.0f;
    private static final float AIR_ACCEL = 18.0f;
    private static final float WATER_ACCEL = 14.0f;
    private static final float ICE_ACCEL = 9.0f;
    private static final float GROUND_STOP_FRICTION = 0.25f;
    private static final float AIR_STOP_FRICTION = 0.98f;
    private static final float WATER_STOP_FRICTION = 0.74f;
    private static final float ICE_FRICTION = 0.992f;

    private final Vector3f pos;
    private final Vector3f vel = new Vector3f();
    private float yaw = 0, pitch = 0;
    private boolean onGround = false;
    private boolean fly = false;
    private float creativeFlyTapTimer = 0f;
    private float jumpBufferTimer = 0f;
    private static final float JUMP_BUFFER = 0.12f;
    private GameMode lastMode;
    private int breakX, breakY, breakZ;
    private float breakTimer = 0f;
    private float breakDuration = 1f;
    private boolean breakingBlock = false;
    private boolean inWater = false;
    private boolean underwater = false;
    private final Inventory inv = new Inventory();
    private final Settings settings;

    public Player(Vector3f start) { this(start, new Settings()); }
    public Player(Vector3f start, Settings settings) {
        this.pos = start;
        this.settings = settings;
        this.lastMode = settings.gameMode;
    }
    public Vector3f position() { return pos; }
    public Inventory inventory() { return inv; }
    public Settings settings() { return settings; }

    public void update(float dt, Input input, World world) {
        update(dt, input, world, null);
    }

    public void update(float dt, Input input, World world, BlockEffectsRenderer effects) {
        // mouse look
        float sens = settings.mouseSensitivity;
        float pitchSign = settings.invertY ? 1f : -1f;
        yaw -= input.mouseDX() * sens;
        pitch += input.mouseDY() * sens * pitchSign;
        pitch = Math.max(-89f, Math.min(89f, pitch));

        GameMode mode = settings.gameMode;
        if (mode != lastMode) {
            fly = mode == GameMode.SPECTATOR;
            creativeFlyTapTimer = 0f;
            vel.zero();
            lastMode = mode;
        }
        boolean creative = mode == GameMode.CREATIVE;
        boolean spectator = mode == GameMode.SPECTATOR;
        inWater = isInWater(world);
        underwater = isUnderwater(world);
        if (!creative) creativeFlyTapTimer = 0f;
        else creativeFlyTapTimer = Math.max(0f, creativeFlyTapTimer - dt);

        // hotbar select
        for (int i = 0; i < 9; i++) {
            if (input.keyPressed(GLFW_KEY_1 + i)) inv.selected = i;
        }
        boolean jumpPressed = input.keyPressed(GLFW_KEY_SPACE);
        // Buffer the press for a short window so a 1-frame onGround blip can't eat it.
        if (jumpPressed) jumpBufferTimer = JUMP_BUFFER;
        else jumpBufferTimer = Math.max(0f, jumpBufferTimer - dt);
        boolean jumpConsumedByFlyToggle = false;
        if (creative && jumpPressed) {
            if (creativeFlyTapTimer > 0f) {
                fly = !fly;
                vel.y = 0f;
                onGround = false;
                creativeFlyTapTimer = 0f;
                jumpConsumedByFlyToggle = true;
            } else {
                creativeFlyTapTimer = CREATIVE_FLY_TAP_WINDOW;
            }
        }
        if (spectator) fly = true;
        if (mode == GameMode.SURVIVAL) fly = false;

        // movement direction
        float yawR = (float) Math.toRadians(yaw);
        Vector3f forward = spectator
            ? lookDir()
            : new Vector3f((float)-Math.sin(yawR), 0, (float)-Math.cos(yawR));
        Vector3f right = new Vector3f((float)Math.cos(yawR), 0, (float)-Math.sin(yawR));
        Vector3f wish = new Vector3f();
        if (input.key(GLFW_KEY_W)) wish.add(forward);
        if (input.key(GLFW_KEY_S)) wish.sub(forward);
        if (input.key(GLFW_KEY_D)) wish.add(right);
        if (input.key(GLFW_KEY_A)) wish.sub(right);
        if (wish.lengthSquared() > 0) wish.normalize();
        float waterFactor = inWater && !fly && !spectator ? 0.45f : 1.0f;
        float speed = (spectator ? FLY_SPEED * 1.35f : (fly ? FLY_SPEED : SPEED)) * waterFactor;
        float targetX = wish.x * speed;
        float targetZ = wish.z * speed;
        if (fly || spectator) {
            vel.x = targetX;
            vel.y = spectator ? wish.y * speed : vel.y;
            vel.z = targetZ;
        } else {
            boolean onIce = onGround && isOnIce(world);
            float accel = inWater ? WATER_ACCEL : (onGround ? (onIce ? ICE_ACCEL : GROUND_ACCEL) : AIR_ACCEL);
            vel.x = approach(vel.x, targetX, accel * dt);
            vel.z = approach(vel.z, targetZ, accel * dt);
            if (wish.lengthSquared() == 0f) {
                float friction = inWater ? WATER_STOP_FRICTION
                    : (onGround ? (onIce ? ICE_FRICTION : GROUND_STOP_FRICTION) : AIR_STOP_FRICTION);
                vel.x *= friction;
                vel.z *= friction;
            } else if (onIce) {
                vel.x *= ICE_FRICTION;
                vel.z *= ICE_FRICTION;
            }
        }

        if (fly) {
            if (!spectator) vel.y = 0;
            if (input.key(GLFW_KEY_SPACE)) vel.y += speed;
            if (input.key(GLFW_KEY_LEFT_SHIFT)) vel.y -= speed;
        } else {
            if (inWater) {
                vel.y -= GRAVITY * 0.18f * dt;
                vel.y *= 0.82f;
                if (input.key(GLFW_KEY_SPACE)) vel.y = Math.max(vel.y, 3.2f);
                if (input.key(GLFW_KEY_LEFT_SHIFT)) vel.y = Math.min(vel.y, -3.2f);
            } else {
                vel.y -= GRAVITY * dt;
            }
            if (onGround && jumpBufferTimer > 0f && !jumpConsumedByFlyToggle) {
                vel.y = JUMP;
                onGround = false;
                jumpBufferTimer = 0f;
            }
        }

        if (spectator) {
            pos.add(new Vector3f(vel).mul(dt));
            onGround = false;
        } else {
            Physics.moveAndCollide(this, world, dt);
        }

        // mouse interaction
        Vector3f eye = new Vector3f(pos.x, pos.y + EYE, pos.z);
        Vector3f dir = lookDir();
        if (!spectator && creative && input.leftClick() && input.cursorGrabbed()) {
            Raycaster.Hit h = Raycaster.cast(world, eye, dir, REACH);
            if (h != null) destroyBlock(world, effects, h.x, h.y, h.z);
        } else if (!spectator && mode == GameMode.SURVIVAL && input.leftDown() && input.cursorGrabbed()) {
            updateSurvivalBreaking(dt, world, effects, eye, dir);
        } else {
            clearBreaking();
        }
        if (!spectator && input.rightClick() && input.cursorGrabbed()) {
            Raycaster.Hit h = Raycaster.cast(world, eye, dir, REACH);
            if (h != null) {
                // don't place inside player
                if (!intersectsPlayer(h.px, h.py, h.pz))
                    world.setBlock(h.px, h.py, h.pz, inv.selectedBlock());
            }
        }
    }

    public boolean isOnGround() { return onGround; }
    public void setOnGround(boolean g) { onGround = g; }
    public Vector3f velocity() { return vel; }
    public boolean isBreakingBlock() { return breakingBlock; }
    public boolean isInWater() { return inWater; }
    public boolean isUnderwater() { return underwater; }
    public int breakingX() { return breakX; }
    public int breakingY() { return breakY; }
    public int breakingZ() { return breakZ; }
    public float breakProgress() {
        return breakingBlock ? Math.max(0f, Math.min(1f, breakTimer / breakDuration)) : 0f;
    }

    public Vector3f lookDir() {
        float yawR = (float) Math.toRadians(yaw);
        float pitchR = (float) Math.toRadians(pitch);
        return new Vector3f(
            (float)(-Math.sin(yawR) * Math.cos(pitchR)),
            (float) Math.sin(pitchR),
            (float)(-Math.cos(yawR) * Math.cos(pitchR))
        ).normalize();
    }

    public Camera camera(float aspect) {
        Vector3f eye = new Vector3f(pos.x, pos.y + EYE, pos.z);
        return new Camera(eye, lookDir(), settings.fov, aspect, 0.1f, 500f);
    }

    private boolean intersectsPlayer(int bx, int by, int bz) {
        float minX = pos.x - WIDTH/2, maxX = pos.x + WIDTH/2;
        float minY = pos.y,           maxY = pos.y + HEIGHT;
        float minZ = pos.z - WIDTH/2, maxZ = pos.z + WIDTH/2;
        return bx + 1 > minX && bx < maxX && by + 1 > minY && by < maxY && bz + 1 > minZ && bz < maxZ;
    }

    private void updateSurvivalBreaking(float dt, World world, BlockEffectsRenderer effects,
                                        Vector3f eye, Vector3f dir) {
        Raycaster.Hit h = Raycaster.cast(world, eye, dir, REACH);
        if (h == null) {
            clearBreaking();
            return;
        }
        Block block = world.getBlock(h.x, h.y, h.z);
        if (block == Block.AIR) {
            clearBreaking();
            return;
        }
        if (!breakingBlock || h.x != breakX || h.y != breakY || h.z != breakZ) {
            breakX = h.x;
            breakY = h.y;
            breakZ = h.z;
            breakTimer = 0f;
            breakDuration = hardness(block);
            breakingBlock = true;
        }
        breakTimer += dt;
        if (breakTimer >= breakDuration) {
            destroyBlock(world, effects, breakX, breakY, breakZ);
            clearBreaking();
        }
    }

    private void destroyBlock(World world, BlockEffectsRenderer effects, int x, int y, int z) {
        Block block = world.getBlock(x, y, z);
        if (block == Block.AIR) return;
        if (effects != null) effects.spawnBlockBreak(x, y, z, block);
        world.setBlock(x, y, z, Block.AIR);
    }

    private void clearBreaking() {
        breakingBlock = false;
        breakTimer = 0f;
    }

    private float hardness(Block block) {
        return switch (block) {
            case LEAVES, CHERRY_LEAVES, GLASS, WATER -> 0.45f;
            case DIRT, GRASS, SAND -> 0.75f;
            case WOOD, CHERRY_WOOD, PLANKS -> 1.15f;
            case STONE -> 1.55f;
            default -> 1.0f;
        };
    }

    private boolean isInWater(World world) {
        int minX = (int) Math.floor(pos.x - WIDTH / 2);
        int maxX = (int) Math.floor(pos.x + WIDTH / 2);
        int minY = (int) Math.floor(pos.y);
        int maxY = (int) Math.floor(pos.y + HEIGHT * 0.9f);
        int minZ = (int) Math.floor(pos.z - WIDTH / 2);
        int maxZ = (int) Math.floor(pos.z + WIDTH / 2);
        for (int x = minX; x <= maxX; x++)
            for (int y = minY; y <= maxY; y++)
                for (int z = minZ; z <= maxZ; z++)
                    if (world.getBlock(x, y, z) == Block.WATER) return true;
        return false;
    }

    private boolean isUnderwater(World world) {
        return world.getBlock((int) Math.floor(pos.x),
                              (int) Math.floor(pos.y + EYE),
                              (int) Math.floor(pos.z)) == Block.WATER;
    }

    private boolean isOnIce(World world) {
        int minX = (int) Math.floor(pos.x - WIDTH / 2);
        int maxX = (int) Math.floor(pos.x + WIDTH / 2);
        int y = (int) Math.floor(pos.y - 0.05f);
        int minZ = (int) Math.floor(pos.z - WIDTH / 2);
        int maxZ = (int) Math.floor(pos.z + WIDTH / 2);
        for (int x = minX; x <= maxX; x++)
            for (int z = minZ; z <= maxZ; z++)
                if (world.getBlock(x, y, z) == Block.ICE) return true;
        return false;
    }

    private static float approach(float value, float target, float maxDelta) {
        if (value < target) return Math.min(value + maxDelta, target);
        if (value > target) return Math.max(value - maxDelta, target);
        return value;
    }
}
