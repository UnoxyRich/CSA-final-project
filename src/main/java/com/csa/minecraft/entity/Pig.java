package com.csa.minecraft.entity;

import com.csa.minecraft.world.World;
import org.joml.Vector3f;
import java.util.Random;

public class Pig extends Mob {
    public static final float WIDTH  = 0.9f;
    public static final float HEIGHT = 0.9f;
    private static final float WANDER_SPEED = 1.6f;
    private static final float FLEE_SPEED   = 3.8f;
    private static final float FLEE_RANGE   = 5.0f;

    private final Random rng;
    private float wanderTimer;
    private float wanderAngle;

    public Pig(Vector3f start) {
        super(start, 10f);
        this.rng = new Random();
        this.wanderTimer = rng.nextFloat() * 3f;
        this.wanderAngle = rng.nextFloat() * (float) (Math.PI * 2);
    }

    @Override public float width()  { return WIDTH; }
    @Override public float height() { return HEIGHT; }

    @Override
    public void update(float dt, World world, Vector3f playerPos) {
        tickTimers(dt);
        applyKnockback(dt, world);

        float dx = playerPos.x - pos.x;
        float dz = playerPos.z - pos.z;
        float dist2 = dx * dx + dz * dz;

        if (dist2 < FLEE_RANGE * FLEE_RANGE && dist2 > 0.001f) {
            float dist = (float) Math.sqrt(dist2);
            float step = FLEE_SPEED * dt;
            // Face away from player, run in opposite direction
            yaw = (float) Math.atan2(dx, dz);
            moveHorizontal(world, -(dx / dist) * step, -(dz / dist) * step);
            animTime += dt * 12f;
        } else {
            wanderTimer -= dt;
            if (wanderTimer <= 0) {
                wanderTimer = 1.5f + rng.nextFloat() * 3.5f;
                wanderAngle = rng.nextFloat() * (float) (Math.PI * 2);
            }
            float wanderDX = (float) Math.sin(wanderAngle);
            float wanderDZ = (float) Math.cos(wanderAngle);
            yaw = (float) Math.atan2(-wanderDX, -wanderDZ);
            boolean moved = moveHorizontal(world, wanderDX * WANDER_SPEED * dt, wanderDZ * WANDER_SPEED * dt);
            if (!moved) wanderTimer = 0;
            animTime += dt * 7f;
        }

        applyGravity(dt, world);
    }
}
