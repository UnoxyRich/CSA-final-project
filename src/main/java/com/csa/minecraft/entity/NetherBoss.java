package com.csa.minecraft.entity;

import com.csa.minecraft.world.World;
import org.joml.Vector3f;

/**
 * The nether boss "Huanwoshiwanmeidao".
 * Very high HP, huge size, always aggroed, heavy melee damage.
 */
public class NetherBoss extends Mob {
    public static final String NAME = "Huanwoshiwanmeidao";

    public static final float WIDTH  = 1.4f;
    public static final float HEIGHT = 2.8f;

    private static final float CHASE_SPEED     = 3.5f;
    private static final float ATTACK_RANGE    = 2.2f;
    private static final float ATTACK_DAMAGE   = 8f;
    private static final float ATTACK_INTERVAL = 1.4f;
    private static final float ARM_SWING_AMP   = 1.0f;

    private float attackCooldown = 0f;
    private float armSwing = 0f;

    public NetherBoss(Vector3f start) {
        super(start, 200f);
    }

    @Override public float width()  { return WIDTH; }
    @Override public float height() { return HEIGHT; }
    public float armSwing()         { return armSwing; }

    @Override
    public void update(float dt, World world, Vector3f playerPos) {
        tickTimers(dt);
        attackCooldown = Math.max(0f, attackCooldown - dt);
        applyKnockback(dt, world);

        float dx = playerPos.x - pos.x;
        float dz = playerPos.z - pos.z;
        float dist2 = dx * dx + dz * dz;
        if (dist2 > 0.001f) {
            float dist = (float) Math.sqrt(dist2);
            yaw = (float) Math.atan2(-dx, -dz);
            moveHorizontal(world, (dx / dist) * CHASE_SPEED * dt,
                                  (dz / dist) * CHASE_SPEED * dt);
            animTime += dt * 7f;
            armSwing = ARM_SWING_AMP * (float) Math.sin(animTime * 1.2f);
        }
        applyGravity(dt, world);
    }

    /** Returns damage dealt this tick, 0 otherwise. */
    public float tryAttack(Vector3f playerPos) {
        if (attackCooldown > 0f) return 0f;
        float dx = playerPos.x - pos.x;
        float dy = playerPos.y - pos.y;
        float dz = playerPos.z - pos.z;
        if (dx * dx + dy * dy + dz * dz > ATTACK_RANGE * ATTACK_RANGE) return 0f;
        attackCooldown = ATTACK_INTERVAL;
        return ATTACK_DAMAGE;
    }
}
