package com.csa.minecraft.entity;

import com.csa.minecraft.world.World;
import org.joml.Vector3f;

import java.util.List;

/**
 * Ally bot "Zhuimu". Follows the player and attacks nearby enemies.
 */
public class Zhuimu extends Mob {
    public static final String NAME = "Zhuimu";

    public static final float WIDTH  = 0.6f;
    public static final float HEIGHT = 1.8f;

    private static final float FOLLOW_SPEED  = 4.2f;
    private static final float ATTACK_SPEED  = 5.0f;
    private static final float ENGAGE_RANGE  = 14.0f;
    private static final float ATTACK_RANGE  = 1.8f;
    private static final float ATTACK_DAMAGE = 4.0f;
    private static final float ATTACK_INTVL  = 0.75f;
    private static final float FOLLOW_DIST   = 3.5f;

    private float attackCooldown = 0f;
    private float armSwing = 0f;

    public Zhuimu(Vector3f start) {
        super(start, 40f);
    }

    @Override public float width()  { return WIDTH; }
    @Override public float height() { return HEIGHT; }
    public float armSwing()         { return armSwing; }

    @Override
    public void update(float dt, World world, Vector3f playerPos) {
        update(dt, world, playerPos, null);
    }

    /** Full update: follow player and attack enemies from the supplied list. */
    public void update(float dt, World world, Vector3f playerPos, List<Mob> enemies) {
        tickTimers(dt);
        attackCooldown = Math.max(0f, attackCooldown - dt);
        applyKnockback(dt, world);

        // Find nearest living enemy within engage range
        Mob target = null;
        float bestD2 = ENGAGE_RANGE * ENGAGE_RANGE;
        if (enemies != null) {
            for (Mob e : enemies) {
                if (!e.isAlive()) continue;
                float dx = e.position().x - pos.x;
                float dz = e.position().z - pos.z;
                float d2 = dx * dx + dz * dz;
                if (d2 < bestD2) { bestD2 = d2; target = e; }
            }
        }

        if (target != null) {
            // Chase and attack
            float dx = target.position().x - pos.x;
            float dy = target.position().y - pos.y;
            float dz = target.position().z - pos.z;
            float dist = (float) Math.sqrt(dx * dx + dz * dz);
            yaw = (float) Math.atan2(-dx, -dz);
            if (dist > 1.0f) {
                moveHorizontal(world, (dx / dist) * ATTACK_SPEED * dt,
                                      (dz / dist) * ATTACK_SPEED * dt);
            }
            if (dx * dx + dy * dy + dz * dz < ATTACK_RANGE * ATTACK_RANGE
                    && attackCooldown <= 0f) {
                target.damage(ATTACK_DAMAGE);
                target.knockback(pos.x, pos.z);
                attackCooldown = ATTACK_INTVL;
            }
            animTime += dt * 10f;
            armSwing = 0.9f * (float) Math.sin(animTime * 1.6f);
        } else {
            // Follow player if too far away
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
        snapToGround(world);
    }
}
