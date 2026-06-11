package com.csa.minecraft.entity;

import com.csa.minecraft.world.Block;
import com.csa.minecraft.world.Chunk;
import com.csa.minecraft.world.World;
import org.joml.Vector3f;

public abstract class Mob {
    protected static final float GROUND_SKIN = 0.01f;
    private static final float HURT_DURATION        = 0.3f;
    private static final float GRAVITY               = 28.0f;
    // Vertical speed (m/s) at landing that starts dealing fall damage (default 3-block drop)
    private static final float FALL_DAMAGE_THRESHOLD = 13.0f;

    /** Override to change when this mob starts taking fall damage. */
    protected float fallDamageThreshold() { return FALL_DAMAGE_THRESHOLD; }

    protected final Vector3f pos;
    protected float yaw;
    protected float animTime;
    protected float health;
    protected float maxHealth;
    protected float hurtTimer;
    protected float kbX, kbZ;
    protected float velY     = 0f;
    protected boolean onGround = false;

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
    // Also rejects moves that would put the mob into water.
    protected boolean moveHorizontal(World world, float mx, float mz) {
        boolean moved = false;
        boolean steppedUp = false;

        pos.x += mx;
        if (collides(world) || touchesWater(world)) {
            pos.y += 1f;
            if (!collides(world) && !touchesWater(world)) {
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
        if (collides(world) || touchesWater(world)) {
            if (!steppedUp) {
                pos.y += 1f;
                if (!collides(world) && !touchesWater(world)) {
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

    // Returns true if any block in the mob's AABB is water.
    protected boolean touchesWater(World world) {
        float hw = width() / 2f;
        int minX = (int) Math.floor(pos.x - hw);
        int maxX = (int) Math.floor(pos.x + hw);
        int minY = (int) Math.floor(pos.y);
        int maxY = (int) Math.floor(pos.y + height() * 0.5f); // only check lower half
        int minZ = (int) Math.floor(pos.z - hw);
        int maxZ = (int) Math.floor(pos.z + hw);
        for (int x = minX; x <= maxX; x++)
            for (int y = minY; y <= maxY; y++)
                for (int z = minZ; z <= maxZ; z++)
                    if (world.getBlock(x, y, z) == Block.WATER) return true;
        return false;
    }

    /**
     * Applies gravity, moves the mob vertically, resolves ground/ceiling
     * collisions, and deals fall damage when landing hard.
     * Call once per update tick, after horizontal movement.
     */
    protected void applyGravity(float dt, World world) {
        onGround = false;
        velY -= GRAVITY * dt;
        pos.y += velY * dt;

        if (collides(world)) {
            if (velY <= 0f) {
                // Feet hit the floor: snap up to surface
                float impactSpeed = -velY;
                pos.y = (float) Math.floor(pos.y);
                while (collides(world)) pos.y += 1f;
                pos.y += GROUND_SKIN;
                onGround = true;
                // Apply fall damage proportional to excess impact speed
                float fdt = fallDamageThreshold();
                if (impactSpeed > fdt) {
                    damage((impactSpeed - fdt) * 0.5f);
                }
            } else {
                // Head hit a ceiling: push down below the block
                pos.y = (float) Math.floor(pos.y + height()) - height() - GROUND_SKIN;
            }
            velY = 0f;
        }
    }

    /** Teleports the mob to the top of the column below it (used at spawn only). */
    protected void snapToGround(World world) {
        Vector3f ground = spawnAtColumn(world, (int) Math.floor(pos.x), (int) Math.floor(pos.z));
        if (ground != null) { pos.y = ground.y; velY = 0f; onGround = true; }
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

    public static Vector3f spawnAtColumn(World world, int x, int z) {
        return spawnAtColumn(world, x, z, Chunk.SY - 3);
    }

    /** Like spawnAtColumn(world, x, z) but starts the downward search at startY
     *  instead of the world ceiling. Used for the nether, where the cave ceiling
     *  can have air pockets that look like a high "ground" plateau. */
    public static Vector3f spawnAtColumn(World world, int x, int z, int startY) {
        for (int y = Math.min(startY, Chunk.SY - 3); y >= 0; y--) {
            if (!isGround(world.getBlock(x, y, z))) continue;
            if (world.getBlock(x, y + 1, z) != Block.AIR) continue;
            return new Vector3f(x + 0.5f, y + 1f + GROUND_SKIN, z + 0.5f);
        }
        return null;
    }

    static boolean isGround(Block block) {
        return switch (block) {
            case GRASS, DIRT, STONE, SAND, SNOW, ICE, SANDSTONE, DRY_GRASS, NETHERRACK -> true;
            default -> false;
        };
    }
}
