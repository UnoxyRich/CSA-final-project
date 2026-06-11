package com.csa.minecraft.entity;

import com.csa.minecraft.world.World;
import org.joml.Vector3f;

import java.util.List;

/**
 * Ally bot "Zhuimu". Follows the player and attacks nearby enemies.
 * Combat AI: strafe around targets at optimal melee range,
 * charge when far, briefly retreat after landing a hit.
 */
public class Zhuimu extends Mob {
    public static final String NAME = "Zhuimu";

    public static final float WIDTH  = 0.6f;
    public static final float HEIGHT = 1.8f;

    // Movement
    private static final float FOLLOW_SPEED   = 5.5f;
    private static final float CHARGE_SPEED   = 6.5f;
    private static final float STRAFE_SPEED   = 3.8f;
    private static final float RETREAT_SPEED  = 5.5f;

    // Combat
    private static final float ENGAGE_RANGE   = 64.0f;
    private static final float OPTIMAL_RANGE  = 1.3f;  // preferred fighting distance
    private static final float ATTACK_RANGE   = 1.8f;
    private static final float ATTACK_DAMAGE  = 5.0f;
    private static final float ATTACK_INTVL   = 0.55f;
    private static final float FOLLOW_DIST    = 3.5f;

    // Strafe / retreat timers
    private static final float STRAFE_FLIP_BASE = 1.0f;
    private static final float RETREAT_TIME     = 0.35f;

    private float attackCooldown = 0f;
    private float armSwing       = 0f;
    private float strafeDir      = 1f;    // +1 or -1 (clockwise / counter-clockwise)
    private float strafeTimer    = STRAFE_FLIP_BASE;
    private float retreatTimer   = 0f;

    // Last known target position used during retreat
    private float retreatFromX = 0f, retreatFromZ = 0f;

    public Zhuimu(Vector3f start) {
        super(start, 100f);
    }

    @Override public float width()  { return WIDTH; }
    @Override public float height() { return HEIGHT; }
    public float armSwing()         { return armSwing; }

    // No fall damage below 4-block drops (impact speed ~15 m/s)
    @Override protected float fallDamageThreshold() { return 15.0f; }

    @Override
    public void update(float dt, World world, Vector3f playerPos) {
        update(dt, world, playerPos, null);
    }

    /** Full update: follow player and attack enemies from the supplied list. */
    public void update(float dt, World world, Vector3f playerPos, List<Mob> enemies) {
        tickTimers(dt);
        attackCooldown = Math.max(0f, attackCooldown - dt);
        retreatTimer   = Math.max(0f, retreatTimer   - dt);
        strafeTimer   -= dt;
        if (strafeTimer <= 0f) {
            strafeTimer = STRAFE_FLIP_BASE + (float)(Math.random() * 0.6);
            strafeDir   = -strafeDir;
        }
        applyKnockback(dt, world);

        // Find nearest living enemy: prefer within ENGAGE_RANGE for full combat AI,
        // but also track the global nearest so we can path to it when none are close.
        Mob target = null;
        float bestD2 = ENGAGE_RANGE * ENGAGE_RANGE;
        Mob nearestEnemy = null;
        float nearestD2  = Float.MAX_VALUE;
        if (enemies != null) {
            for (Mob e : enemies) {
                if (!e.isAlive()) continue;
                float ex = e.position().x - pos.x;
                float ez = e.position().z - pos.z;
                float d2 = ex * ex + ez * ez;
                if (d2 < bestD2)    { bestD2 = d2; target = e; }
                if (d2 < nearestD2) { nearestD2 = d2; nearestEnemy = e; }
            }
        }

        if (target != null) {
            float dx   = target.position().x - pos.x;
            float dy   = target.position().y - pos.y;
            float dz   = target.position().z - pos.z;
            float dist = (float) Math.sqrt(dx * dx + dz * dz);

            // Always face the target
            yaw = (float) Math.atan2(-dx, -dz);

            if (retreatTimer > 0f) {
                // Brief back-off after landing a hit
                float rdx = pos.x - retreatFromX;
                float rdz = pos.z - retreatFromZ;
                float rLen = (float) Math.sqrt(rdx * rdx + rdz * rdz);
                if (rLen > 0.001f) {
                    moveHorizontal(world, (rdx / rLen) * RETREAT_SPEED * dt,
                                         (rdz / rLen) * RETREAT_SPEED * dt);
                }
            } else if (dist > ATTACK_RANGE * 1.6f) {
                // Far away: charge straight in
                moveHorizontal(world, (dx / dist) * CHARGE_SPEED * dt,
                                      (dz / dist) * CHARGE_SPEED * dt);
            } else if (dist > 0.1f) {
                // In combat range: strafe around target and hold optimal distance
                float perpX = (-dz / dist) * strafeDir;
                float perpZ = ( dx / dist) * strafeDir;
                // Radial correction: close in if too far, ease back if too close
                float radial = (dist - OPTIMAL_RANGE) * 2.5f;
                radial = Math.max(-CHARGE_SPEED, Math.min(CHARGE_SPEED, radial));
                float moveX = perpX * STRAFE_SPEED * dt + (dx / dist) * radial * dt;
                float moveZ = perpZ * STRAFE_SPEED * dt + (dz / dist) * radial * dt;
                moveHorizontal(world, moveX, moveZ);
            }

            // Attack when close enough
            if (dx * dx + dy * dy + dz * dz < ATTACK_RANGE * ATTACK_RANGE
                    && attackCooldown <= 0f) {
                target.damage(ATTACK_DAMAGE);
                target.knockback(pos.x, pos.z);
                attackCooldown  = ATTACK_INTVL;
                retreatTimer    = RETREAT_TIME;
                retreatFromX    = target.position().x;
                retreatFromZ    = target.position().z;
                // Flip strafe direction after each hit to keep movement unpredictable
                strafeDir = -strafeDir;
                strafeTimer = STRAFE_FLIP_BASE;
            }

            animTime += dt * 10f;
            armSwing = 0.9f * (float) Math.sin(animTime * 1.6f);
        } else if (nearestEnemy != null) {
            // Enemies exist but are outside engage range: path straight toward the nearest one
            retreatTimer = 0f;
            float dx   = nearestEnemy.position().x - pos.x;
            float dz   = nearestEnemy.position().z - pos.z;
            float dist = (float) Math.sqrt(dx * dx + dz * dz);
            yaw = (float) Math.atan2(-dx, -dz);
            moveHorizontal(world, (dx / dist) * CHARGE_SPEED * dt,
                                  (dz / dist) * CHARGE_SPEED * dt);
            animTime += dt * 9f;
            armSwing  = 0.5f * (float) Math.sin(animTime * 1.4f);
        } else {
            // No enemies at all: follow player if too far away
            retreatTimer = 0f;
            float dx = playerPos.x - pos.x;
            float dz = playerPos.z - pos.z;
            float d2 = dx * dx + dz * dz;
            if (d2 > FOLLOW_DIST * FOLLOW_DIST) {
                float dist = (float) Math.sqrt(d2);
                yaw = (float) Math.atan2(-dx, -dz);
                moveHorizontal(world, (dx / dist) * FOLLOW_SPEED * dt,
                                      (dz / dist) * FOLLOW_SPEED * dt);
                animTime += dt * 7f;
            }
            armSwing = 0f;
        }
        applyGravity(dt, world);
    }
}
