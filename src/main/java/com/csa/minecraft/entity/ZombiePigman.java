package com.csa.minecraft.entity;

import com.csa.minecraft.world.World;
import org.joml.Vector3f;
import java.util.Random;

public class ZombiePigman extends Mob {
    public static final float WIDTH  = 0.6f;
    public static final float HEIGHT = 1.8f;
    private static final float CHASE_SPEED     = 2.8f;
    private static final float WANDER_SPEED    = 1.2f;
    private static final float AGGRO_RANGE     = 10.0f;
    private static final float DEAGGRO_RANGE   = 22.0f;
    private static final float ARM_SWING_AMP   = 0.8f;
    private static final float ATTACK_RANGE    = 1.8f;
    private static final float ATTACK_DAMAGE   = 3f;
    private static final float ATTACK_INTERVAL = 1.2f;

    private final Random rng;
    private boolean aggroed;
    private float wanderTimer;
    private float wanderAngle;
    private float armSwing;
    private float attackCooldown;

    public ZombiePigman(Vector3f start) {
        super(start, 20f);
        this.rng = new Random();
        this.wanderTimer = rng.nextFloat() * 3f;
        this.wanderAngle = rng.nextFloat() * (float) (Math.PI * 2);
    }

    @Override public float width()  { return WIDTH; }
    @Override public float height() { return HEIGHT; }
    public float armSwing()         { return armSwing; }

    @Override
    public void update(float dt, World world, Vector3f playerPos) {
        update(dt, world, playerPos, null);
    }

    /**
     * Full update: chases and attacks the player or allyTarget, whichever is closer
     * and in aggro range. Pass null for allyTarget when no ally is present.
     */
    public void update(float dt, World world, Vector3f playerPos, Mob allyTarget) {
        tickTimers(dt);
        attackCooldown = Math.max(0f, attackCooldown - dt);
        applyKnockback(dt, world);

        // Pick nearest living target between player and ally
        float pdx = playerPos.x - pos.x;
        float pdz = playerPos.z - pos.z;
        float playerDist2 = pdx * pdx + pdz * pdz;

        Vector3f chasePos = playerPos;
        float    chaseDist2 = playerDist2;
        if (allyTarget != null && allyTarget.isAlive()) {
            float adx = allyTarget.position().x - pos.x;
            float adz = allyTarget.position().z - pos.z;
            float allyDist2 = adx * adx + adz * adz;
            if (allyDist2 < playerDist2) {
                chasePos    = allyTarget.position();
                chaseDist2  = allyDist2;
            }
        }

        float dx = chasePos.x - pos.x;
        float dz = chasePos.z - pos.z;

        if (!aggroed && chaseDist2 < AGGRO_RANGE * AGGRO_RANGE)      aggroed = true;
        if (aggroed  && chaseDist2 > DEAGGRO_RANGE * DEAGGRO_RANGE)  aggroed = false;

        if (aggroed && chaseDist2 > 0.001f) {
            float dist = (float) Math.sqrt(chaseDist2);
            yaw = (float) Math.atan2(-dx, -dz);
            moveHorizontal(world, (dx / dist) * CHASE_SPEED * dt, (dz / dist) * CHASE_SPEED * dt);
            animTime += dt * 9f;
            armSwing = ARM_SWING_AMP * (float) Math.sin(animTime * 1.5f);
        } else {
            wanderTimer -= dt;
            if (wanderTimer <= 0) {
                wanderTimer = 2f + rng.nextFloat() * 4f;
                wanderAngle = rng.nextFloat() * (float) (Math.PI * 2);
            }
            float wanderDX = (float) Math.sin(wanderAngle);
            float wanderDZ = (float) Math.cos(wanderAngle);
            yaw = (float) Math.atan2(-wanderDX, -wanderDZ);
            boolean moved = moveHorizontal(world, wanderDX * WANDER_SPEED * dt, wanderDZ * WANDER_SPEED * dt);
            if (!moved) wanderTimer = 0;
            animTime += dt * 5f;
            armSwing = 0f;
        }

        applyGravity(dt, world);
    }

    // Returns damage dealt this tick (> 0) if the pigman hit the player, 0 otherwise.
    public float tryAttack(Vector3f playerPos) {
        if (!aggroed || attackCooldown > 0f) return 0f;
        float dx = playerPos.x - pos.x;
        float dy = playerPos.y - pos.y;
        float dz = playerPos.z - pos.z;
        if (dx * dx + dy * dy + dz * dz > ATTACK_RANGE * ATTACK_RANGE) return 0f;
        attackCooldown = ATTACK_INTERVAL;
        return ATTACK_DAMAGE;
    }

    // Deals damage directly to allyTarget if in melee range.
    // Zhuimu is agile: each swing has a 65% chance of being dodged.
    private static final float ALLY_DODGE_CHANCE = 0.65f;

    public void tryAttackAlly(Mob allyTarget) {
        if (!aggroed || attackCooldown > 0f || allyTarget == null || !allyTarget.isAlive()) return;
        float dx = allyTarget.position().x - pos.x;
        float dy = allyTarget.position().y - pos.y;
        float dz = allyTarget.position().z - pos.z;
        if (dx * dx + dy * dy + dz * dz > ATTACK_RANGE * ATTACK_RANGE) return;
        attackCooldown = ATTACK_INTERVAL;
        // Zhuimu dodges most attacks due to her strafing movement
        if (rng.nextFloat() < ALLY_DODGE_CHANCE) return;
        allyTarget.damage(ATTACK_DAMAGE);
        allyTarget.knockback(pos.x, pos.z);
    }
}
