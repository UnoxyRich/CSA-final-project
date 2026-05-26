package com.csa.minecraft.entity;

import com.csa.minecraft.world.World;
import org.joml.Vector3f;
import java.util.Random;

public class Cow extends Mob {
    public static final float WIDTH  = 0.9f;
    public static final float HEIGHT = 1.4f;
    private static final float WANDER_SPEED = 1.0f;

    private final Random rng;
    private float wanderTimer;
    private float wanderAngle;

    public Cow(Vector3f start) {
        super(start, 10f);
        this.rng = new Random();
        this.wanderTimer = rng.nextFloat() * 4f;
        this.wanderAngle = rng.nextFloat() * (float) (Math.PI * 2);
    }

    @Override public float width()  { return WIDTH; }
    @Override public float height() { return HEIGHT; }

    @Override
    public void update(float dt, World world, Vector3f playerPos) {
        tickTimers(dt);
        applyKnockback(dt, world);
        snapToGround(world);

        wanderTimer -= dt;
        if (wanderTimer <= 0) {
            wanderTimer = 2f + rng.nextFloat() * 5f;
            wanderAngle = rng.nextFloat() * (float) (Math.PI * 2);
        }

        float wanderDX = (float) Math.sin(wanderAngle);
        float wanderDZ = (float) Math.cos(wanderAngle);
        yaw = (float) Math.atan2(-wanderDX, -wanderDZ);
        boolean moved = moveHorizontal(world, wanderDX * WANDER_SPEED * dt, wanderDZ * WANDER_SPEED * dt);
        if (!moved) wanderTimer = 0;
        animTime += dt * 5f;

        snapToGround(world);
    }
}
