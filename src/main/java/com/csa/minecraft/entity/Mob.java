package com.csa.minecraft.entity;

import com.csa.minecraft.world.Block;
import com.csa.minecraft.world.Chunk;
import com.csa.minecraft.world.World;
import org.joml.Vector3f;

public abstract class Mob {
    protected static final float GROUND_SKIN = 0.01f;
    private static final float HURT_DURATION = 0.3f;

    protected final Vector3f pos;
    protected float yaw;
    protected float animTime;
    protected float health;
    protected float maxHealth;
    protected float hurtTimer;
    protected float kbX, kbZ;

    protected Mob(Vector3f start, float maxHealth) {
        this.pos = start;
        this.maxHealth = maxHealth;
        this.health = maxHealth;
    }

    public Vector3f position() { return pos; }
    public float yaw()         { return yaw; }
    public float animTime()    { return animTime; }
    public float health()      { return health; }
    public float maxHealth()   { return maxHealth; }
    public boolean isAlive()   { return health > 0f; }
    public float hurtTimer()   { return hurtTimer; }

    public void damage(float amount) {
        health = Math.max(0f, health - amount);
        hurtTimer = HURT_DURATION;
    }

    protected void tickTimers(float dt) {
        hurtTimer = Math.max(0f, hurtTimer - dt);
    }

    // Push mob away from (fromX, fromZ) — call after damage().
    public void knockback(float fromX, float fromZ) {
        float dx = pos.x - fromX;
        float dz = pos.z - fromZ;
        float len = (float) Math.sqrt(dx * dx + dz * dz);
        float speed = 9f;
        if (len < 0.001f) { kbX = 0f; kbZ = speed; return; }
        kbX = (dx / len) * speed;
        kbZ = (dz / len) * speed;
    }

    // Move by current knockback velocity (respects walls) and decay it.
    protected void applyKnockback(float dt, World world) {
        if (kbX == 0f && kbZ == 0f) return;
        moveHorizontal(world, kbX * dt, kbZ * dt);
        float decay = (float) Math.exp(-dt / 0.20f);
        kbX *= decay;
        kbZ *= decay;
        if (Math.abs(kbX) < 0.05f && Math.abs(kbZ) < 0.05f) { kbX = 0f; kbZ = 0f; }
    }

    public abstract float width();
    public abstract float height();
    public abstract void update(float dt, World world, Vector3f playerPos);

    // Returns true if any horizontal movement was made.
    // Tries to step up 1 block when blocked (auto-jump over single-block obstacles).
    protected boolean moveHorizontal(World world, float mx, float mz) {
        boolean moved = false;
        boolean steppedUp = false;

        pos.x += mx;
        if (collides(world)) {
            pos.y += 1f;
            if (!collides(world)) {
                steppedUp = true;
                moved = true;
            } else {
                pos.y -= 1f;
                pos.x -= mx;
            }
        } else {
            moved = true;
        }

        pos.z += mz;
        if (collides(world)) {
            if (!steppedUp) {
                pos.y += 1f;
                if (!collides(world)) {
                    moved = true;
                } else {
                    pos.y -= 1f;
                    pos.z -= mz;
                }
            } else {
                pos.z -= mz;
            }
        } else {
            moved = true;
        }

        return moved;
    }

    protected void snapToGround(World world) {
        Vector3f ground = spawnAtColumn(world, (int) Math.floor(pos.x), (int) Math.floor(pos.z));
        if (ground != null) pos.y = ground.y;
    }

    protected boolean collides(World world) {
        float hw = width() / 2f;
        int minX = (int) Math.floor(pos.x - hw);
        int maxX = (int) Math.floor(pos.x + hw);
        int minY = (int) Math.floor(pos.y);
        int maxY = (int) Math.floor(pos.y + height());
        int minZ = (int) Math.floor(pos.z - hw);
        int maxZ = (int) Math.floor(pos.z + hw);
        for (int x = minX; x <= maxX; x++)
            for (int y = minY; y <= maxY; y++)
                for (int z = minZ; z <= maxZ; z++)
                    if (world.getBlock(x, y, z).solid) return true;
        return false;
    }

    static Vector3f spawnAtColumn(World world, int x, int z) {
        for (int y = Chunk.SY - 3; y >= 0; y--) {
            if (!isGround(world.getBlock(x, y, z))) continue;
            if (world.getBlock(x, y + 1, z) != Block.AIR) continue;
            return new Vector3f(x + 0.5f, y + 1f + GROUND_SKIN, z + 0.5f);
        }
        return null;
    }

    static boolean isGround(Block block) {
        return switch (block) {
            case GRASS, DIRT, STONE, SAND, SNOW, ICE, SANDSTONE, DRY_GRASS -> true;
            default -> false;
        };
    }
}
