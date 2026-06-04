package com.csa.minecraft.entity;

import com.csa.minecraft.player.Player;
import com.csa.minecraft.world.World;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

public class MobManager {
    private static final int PIG_COUNT    = 5;
    private static final int COW_COUNT    = 4;
    private static final int PIGMAN_COUNT = 3;

    private final List<Pig>          pigs   = new ArrayList<>();
    private final List<Cow>          cows   = new ArrayList<>();
    private final List<ZombiePigman> pigmen = new ArrayList<>();

    public List<Pig>          pigs()   { return pigs; }
    public List<Cow>          cows()   { return cows; }
    public List<ZombiePigman> pigmen() { return pigmen; }

    public void spawnNearPlayer(World world, Vector3f playerPos) {
        pigs.clear();
        cows.clear();
        pigmen.clear();
        Random rng = new Random();
        int px = (int) Math.floor(playerPos.x);
        int pz = (int) Math.floor(playerPos.z);
        spawnGroup(world, playerPos, rng, px, pz, PIG_COUNT,     6, 14, s -> pigs.add(new Pig(s)));
        spawnGroup(world, playerPos, rng, px, pz, COW_COUNT,     7, 15, s -> cows.add(new Cow(s)));
        spawnGroup(world, playerPos, rng, px, pz, PIGMAN_COUNT, 10, 20, s -> pigmen.add(new ZombiePigman(s)));
    }

    private static void spawnGroup(World world, Vector3f playerPos, Random rng,
                                   int px, int pz, int count, int minR, int maxR,
                                   Consumer<Vector3f> adder) {
        for (int i = 0; i < count; i++) {
            Vector3f spawn = null;
            for (int attempt = 0; attempt < 30 && spawn == null; attempt++) {
                int r = minR + rng.nextInt(maxR - minR + 1);
                double angle = rng.nextDouble() * Math.PI * 2;
                int ox = (int) Math.round(Math.cos(angle) * r);
                int oz = (int) Math.round(Math.sin(angle) * r);
                spawn = Mob.spawnAtColumn(world, px + ox, pz + oz);
            }
            if (spawn == null) continue; // no dry land found — skip rather than spawn in water
            adder.accept(spawn);
        }
    }

    public void update(float dt, World world, Vector3f playerPos, Player player) {
        for (Pig p          : pigs)   p.update(dt, world, playerPos);
        for (Cow c          : cows)   c.update(dt, world, playerPos);
        for (ZombiePigman z : pigmen) {
            z.update(dt, world, playerPos);
            float dmg = z.tryAttack(playerPos);
            if (dmg > 0f) player.takeDamage(dmg);
        }
        pigs.removeIf(p -> !p.isAlive());
        cows.removeIf(c -> !c.isAlive());
        pigmen.removeIf(z -> !z.isAlive());
    }

    // Returns true if a mob within reach was hit. The nearest mob along the ray is damaged.
    public boolean tryHit(Vector3f origin, Vector3f dir, float reach, float damageMult) {
        float bestT = reach;
        Mob best = null;
        for (Pig p          : pigs)   { float t = rayHit(origin, dir, p); if (t >= 0 && t < bestT) { bestT = t; best = p; } }
        for (Cow c          : cows)   { float t = rayHit(origin, dir, c); if (t >= 0 && t < bestT) { bestT = t; best = c; } }
        for (ZombiePigman z : pigmen) { float t = rayHit(origin, dir, z); if (t >= 0 && t < bestT) { bestT = t; best = z; } }
        if (best == null) return false;
        best.damage(2f * damageMult);
        best.knockback(origin.x, origin.z);
        return true;
    }

    private static float rayHit(Vector3f o, Vector3f d, Mob mob) {
        float hw = mob.width() / 2f;
        Vector3f p = mob.position();
        float x0 = p.x - hw, x1 = p.x + hw;
        float y0 = p.y,       y1 = p.y + mob.height();
        float z0 = p.z - hw,  z1 = p.z + hw;
        float tmin = Float.NEGATIVE_INFINITY, tmax = Float.POSITIVE_INFINITY;
        if (Math.abs(d.x) > 1e-6f) {
            float t1 = (x0 - o.x) / d.x, t2 = (x1 - o.x) / d.x;
            if (t1 > t2) { float tmp = t1; t1 = t2; t2 = tmp; }
            tmin = Math.max(tmin, t1); tmax = Math.min(tmax, t2);
        } else if (o.x < x0 || o.x > x1) return -1f;
        if (Math.abs(d.y) > 1e-6f) {
            float t1 = (y0 - o.y) / d.y, t2 = (y1 - o.y) / d.y;
            if (t1 > t2) { float tmp = t1; t1 = t2; t2 = tmp; }
            tmin = Math.max(tmin, t1); tmax = Math.min(tmax, t2);
        } else if (o.y < y0 || o.y > y1) return -1f;
        if (Math.abs(d.z) > 1e-6f) {
            float t1 = (z0 - o.z) / d.z, t2 = (z1 - o.z) / d.z;
            if (t1 > t2) { float tmp = t1; t1 = t2; t2 = tmp; }
            tmin = Math.max(tmin, t1); tmax = Math.min(tmax, t2);
        } else if (o.z < z0 || o.z > z1) return -1f;
        if (tmax < 0 || tmin > tmax) return -1f;
        return tmin < 0 ? tmax : tmin;
    }
}
