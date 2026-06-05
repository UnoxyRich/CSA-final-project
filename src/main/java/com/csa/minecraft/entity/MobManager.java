package com.csa.minecraft.entity;

import com.csa.minecraft.player.Player;
import com.csa.minecraft.world.Block;
import com.csa.minecraft.world.World;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

@SuppressWarnings("unchecked")

public class MobManager {
    // Max total mobs alive at once
    private static final int MAX_PIGS   = 15;
    private static final int MAX_COWS   = 10;
    private static final int MAX_PIGMEN =  8;

    // Radius used for the initial spawn at game start (close to player)
    private static final int INIT_MIN_R =  6;
    private static final int INIT_MAX_R = 20;

    // Radius used for ongoing random spawning while exploring
    private static final int ROAM_MIN_R = 24;
    private static final int ROAM_MAX_R = 64;

    // Mobs beyond this horizontal distance from the player are despawned
    private static final float DESPAWN_R = 90f;

    // Seconds between each periodic spawn attempt
    private static final float SPAWN_INTERVAL = 5f;

    private final List<Pig>          pigs   = new ArrayList<>();
    private final List<Cow>          cows   = new ArrayList<>();
    private final List<ZombiePigman> pigmen = new ArrayList<>();
    private final Random             rng    = new Random();
    private float spawnTimer = 0f;

    // Nether-specific entities (null when not in nether)
    private NetherBoss boss      = null;
    private Zhuimu     ally      = null;
    // When true, pigs and cows are never spawned (used for the nether manager)
    private boolean    netherMode = false;

    public List<Pig>          pigs()   { return pigs; }
    public List<Cow>          cows()   { return cows; }
    public List<ZombiePigman> pigmen() { return pigmen; }
    public NetherBoss         boss()   { return boss; }
    public Zhuimu             ally()   { return ally; }

    /** Called once at world start to seed mobs close to the player. */
    public void spawnNearPlayer(World world, Vector3f playerPos) {
        pigs.clear(); cows.clear(); pigmen.clear();
        int px = (int) Math.floor(playerPos.x);
        int pz = (int) Math.floor(playerPos.z);
        spawnGroup(world, rng, px, pz, 5, INIT_MIN_R, INIT_MAX_R, s -> pigs.add(new Pig(s)));
        spawnGroup(world, rng, px, pz, 4, INIT_MIN_R, INIT_MAX_R, s -> cows.add(new Cow(s)));
        spawnGroup(world, rng, px, pz, 3, INIT_MIN_R, INIT_MAX_R, s -> pigmen.add(new ZombiePigman(s)));
    }

    /**
     * Initialises the nether mob set: boss Huanwoshiwanmeidao, 10 pig gods,
     * and the ally Zhuimu.  Call this once when the player first enters the nether.
     */
    public void initNether(World world, Vector3f playerPos) {
        netherMode = true;
        pigs.clear();
        cows.clear();
        int px = (int) Math.floor(playerPos.x);
        int pz = (int) Math.floor(playerPos.z);

        // Boss: 18-25 blocks away from player
        Vector3f bossPos = null;
        for (int attempt = 0; attempt < 40 && bossPos == null; attempt++) {
            double angle = rng.nextDouble() * Math.PI * 2;
            int r = 18 + rng.nextInt(8);
            bossPos = Mob.spawnAtColumn(world, px + (int)(Math.cos(angle)*r),
                                               pz + (int)(Math.sin(angle)*r));
        }
        if (bossPos == null) bossPos = new Vector3f(px + 18f, playerPos.y, pz);
        boss = new NetherBoss(bossPos);

        // 10 pigmen at medium range
        pigmen.clear();
        spawnGroup(world, rng, px, pz, 10, INIT_MIN_R, INIT_MAX_R,
                   s -> pigmen.add(new ZombiePigman(s)));

        // Ally Zhuimu: 4-7 blocks from player
        Vector3f allyPos = null;
        for (int attempt = 0; attempt < 20 && allyPos == null; attempt++) {
            double angle = rng.nextDouble() * Math.PI * 2;
            int r = 4 + rng.nextInt(4);
            allyPos = Mob.spawnAtColumn(world, px + (int)(Math.cos(angle)*r),
                                               pz + (int)(Math.sin(angle)*r));
        }
        if (allyPos == null) allyPos = new Vector3f(px + 5f, playerPos.y, pz);
        ally = new Zhuimu(allyPos);
    }

    /** Try to place one mob at a random position in [minR, maxR] from (px,pz). */
    private static void spawnOne(World world, Random rng, int px, int pz,
                                 int minR, int maxR, Consumer<Vector3f> adder) {
        for (int attempt = 0; attempt < 20; attempt++) {
            int r = minR + rng.nextInt(maxR - minR + 1);
            double angle = rng.nextDouble() * Math.PI * 2;
            int ox = (int) Math.round(Math.cos(angle) * r);
            int oz = (int) Math.round(Math.sin(angle) * r);
            Vector3f spawn = Mob.spawnAtColumn(world, px + ox, pz + oz);
            if (spawn != null) { adder.accept(spawn); return; }
        }
    }

    private static void spawnGroup(World world, Random rng,
                                   int px, int pz, int count, int minR, int maxR,
                                   Consumer<Vector3f> adder) {
        for (int i = 0; i < count; i++)
            spawnOne(world, rng, px, pz, minR, maxR, adder);
    }

    private static float distSq2D(Vector3f a, Vector3f b) {
        float dx = a.x - b.x, dz = a.z - b.z;
        return dx * dx + dz * dz;
    }

    public void update(float dt, World world, Vector3f playerPos, Player player) {
        // Mob AI
        for (Pig p          : pigs)   p.update(dt, world, playerPos);
        for (Cow c          : cows)   c.update(dt, world, playerPos);
        for (ZombiePigman z : pigmen) {
            z.update(dt, world, playerPos);
            float dmg = z.tryAttack(playerPos);
            if (dmg > 0f) player.takeDamage(dmg);
        }

        // Boss AI and attacks
        if (boss != null && boss.isAlive()) {
            boss.update(dt, world, playerPos);
            float dmg = boss.tryAttack(playerPos);
            if (dmg > 0f) player.takeDamage(dmg);
        }

        // Ally Zhuimu follows and fights
        if (ally != null && ally.isAlive()) {
            List<Mob> enemies = new ArrayList<>();
            for (ZombiePigman z : pigmen) if (z.isAlive()) enemies.add(z);
            if (boss != null && boss.isAlive()) enemies.add(boss);
            ally.update(dt, world, playerPos, enemies);
        }

        // Zombie pigman death drop: give obsidian directly to player inventory
        for (ZombiePigman z : pigmen) {
            if (!z.isAlive()) {
                player.inventory().addBlock(Block.OBSIDIAN);
            }
        }
        // Boss death drop: give 5 obsidian
        if (boss != null && !boss.isAlive()) {
            for (int i = 0; i < 5; i++) player.inventory().addBlock(Block.OBSIDIAN);
            boss = null; // only drop once
        }

        // Remove dead mobs
        pigs.removeIf(p -> !p.isAlive());
        cows.removeIf(c -> !c.isAlive());
        pigmen.removeIf(z -> !z.isAlive());
        // Despawn mobs that wandered too far from the player
        float dr2 = DESPAWN_R * DESPAWN_R;
        pigs.removeIf(p -> distSq2D(p.position(), playerPos) > dr2);
        cows.removeIf(c -> distSq2D(c.position(), playerPos) > dr2);
        pigmen.removeIf(z -> distSq2D(z.position(), playerPos) > dr2);
        // Periodic random spawning across the world as the player explores
        spawnTimer += dt;
        if (spawnTimer >= SPAWN_INTERVAL) {
            spawnTimer = 0f;
            int px = (int) Math.floor(playerPos.x);
            int pz = (int) Math.floor(playerPos.z);
            if (!netherMode && pigs.size() < MAX_PIGS)
                spawnOne(world, rng, px, pz, ROAM_MIN_R, ROAM_MAX_R, s -> pigs.add(new Pig(s)));
            if (!netherMode && cows.size() < MAX_COWS)
                spawnOne(world, rng, px, pz, ROAM_MIN_R, ROAM_MAX_R, s -> cows.add(new Cow(s)));
            if (pigmen.size() < MAX_PIGMEN)
                spawnOne(world, rng, px, pz, ROAM_MIN_R, ROAM_MAX_R, s -> pigmen.add(new ZombiePigman(s)));
        }
    }

    // Returns true if a mob within reach was hit. The nearest mob along the ray is damaged.
    public boolean tryHit(Vector3f origin, Vector3f dir, float reach, float damageMult) {
        float bestT = reach;
        Mob best = null;
        for (Pig p          : pigs)   { float t = rayHit(origin, dir, p); if (t >= 0 && t < bestT) { bestT = t; best = p; } }
        for (Cow c          : cows)   { float t = rayHit(origin, dir, c); if (t >= 0 && t < bestT) { bestT = t; best = c; } }
        for (ZombiePigman z : pigmen) { float t = rayHit(origin, dir, z); if (t >= 0 && t < bestT) { bestT = t; best = z; } }
        // Boss is hittable by player
        if (boss != null && boss.isAlive()) {
            float t = rayHit(origin, dir, boss);
            if (t >= 0 && t < bestT) { bestT = t; best = boss; }
        }
        // Ally (Zhuimu) is never hit by the player
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
