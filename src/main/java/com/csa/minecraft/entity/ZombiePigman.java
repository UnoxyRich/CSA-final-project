package com.csa.minecraft.entity;

import com.csa.minecraft.world.World;
import org.joml.Vector3f;
import java.util.Random;

public class ZombiePigman extends Mob {
    public static final float WIDTH  = 0.6f;
    public static final float HEIGHT = 1.8f;
    private static final float CHASE_SPEED   = 2.8f;
    private static final float WANDER_SPEED  = 1.2f;
    private static final float AGGRO_RANGE   = 10.0f;
    private static final float DEAGGRO_RANGE = 22.0f;
    private static final float ARM_SWING_AMP = 0.8f;

    private final Random rng;
    private boolean aggroed;
    private float wanderTimer;
    private float wanderAngle;
    private float armSwing;

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
        tickTimers(dt);
        applyKnockback(dt, world);
        snapToGround(world);

        float dx = playerPos.x - pos.x;
        float dz = playerPos.z - pos.z;
        float dist2 = dx * dx + dz * dz;

        if (!aggroed && dist2 < AGGRO_RANGE * AGGRO_RANGE)      aggroed = true;
        if (aggroed  && dist2 > DEAGGRO_RANGE * DEAGGRO_RANGE)  aggroed = false;

        if (aggroed && dist2 > 0.001f) {
            float dist = (float) Math.sqrt(dist2);
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

        snapToGround(world);
    }
}
