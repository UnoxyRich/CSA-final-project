package com.csa.minecraft;

import com.csa.minecraft.engine.*;
import com.csa.minecraft.entity.MilkFrog;
import com.csa.minecraft.entity.MobManager;
import com.csa.minecraft.player.*;
import com.csa.minecraft.render.*;
import com.csa.minecraft.world.*;
import com.csa.minecraft.NetherEnvironment;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_E;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_SLASH;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_T;
import static org.lwjgl.opengl.GL11.*;

public class Main {
    private static final long WORLD_SEED  = 1337L;
    private static final int  SPAWN_X     = 8;
    private static final int  SPAWN_Z     = 8;
    private static final float SPAWN_SKIN = 0.01f;
    // Portal entry: player must stand in portal for this many seconds to teleport
    private static final float PORTAL_CHARGE_TIME = 4.0f;
    // After teleporting, block re-entry for this many seconds (prevents bounce)
    private static final float PORTAL_COOLDOWN_TIME = 3.5f;

    public static void main(String[] args) {
        Window window = new Window("NailongCraft", 1280, 720);
        window.init();
        Input input = new Input(window.handle());

        Settings settings = new Settings();
        World world = null;
        Player player = null;
        MilkFrog milkFrog = null;
        long currentSeed = WORLD_SEED;
        Environment environment = new Environment();
        Environment netherEnv = new NetherEnvironment();
        // Nether dimension state
        World netherWorld = null;
        MobManager netherMobs = new MobManager();
        boolean inNether = false;
        float portalTimer = 0f;          // charge timer while standing in portal
        float portalCooldown = 0f;       // post-teleport cooldown (prevent bounce)
        Vector3f overworldReturnPos = null; // saved position for returning from nether
        CommandConsole console = new CommandConsole();
        // Worker pool for off-thread chunk generation and mesh building. One
        // pool for the whole process, shared by every World that gets created.
        ChunkWorkerPool workers = new ChunkWorkerPool();
        System.out.println("Chunk worker pool: " + workers.threadCount() + " threads");
        WorldRenderer worldRenderer = new WorldRenderer(workers);
        WeatherRenderer weatherRenderer = new WeatherRenderer();
        MilkFrogRenderer milkFrogRenderer = new MilkFrogRenderer();
        MobRenderer mobRenderer = new MobRenderer();
        MobManager mobs = new MobManager();
        BlockEffectsRenderer blockEffects = new BlockEffectsRenderer();
        HudRenderer hud = new HudRenderer(settings);
        hud.setAtlas(worldRenderer.atlas());
        CraftingMenu craftingMenu = new CraftingMenu(settings);
        SettingsMenu menu = new SettingsMenu(settings);
        ChatOverlay chat = new ChatOverlay(settings);
        DeathScreen   deathScreen   = new DeathScreen(settings);
        VictoryScreen victoryScreen = new VictoryScreen(settings);
        StartScreen   startScreen   = new StartScreen(settings);
        input.grabCursor(false);

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glClearColor(0.5f, 0.7f, 1.0f, 1.0f);

        double last = org.lwjgl.glfw.GLFW.glfwGetTime();
        double fpsTimer = 0; int frames = 0;
        while (!window.shouldClose()) {
            double now = org.lwjgl.glfw.GLFW.glfwGetTime();
            float dt = (float) Math.min(0.05, now - last);
            last = now;

            input.poll();
            environment.update(dt);
            // Tick portal cooldown every frame
            if (portalCooldown > 0f) portalCooldown -= dt;
            boolean worldChangedThisFrame = false;
            // Active world/mobs/env based on dimension
            World  activeWorld = inNether ? netherWorld : world;
            MobManager activeMobs = inNether ? netherMobs : mobs;
            Environment activeEnv = inNether ? netherEnv : environment;

            if (startScreen.isOpen()) {
                StartScreen.Action action = startScreen.update(window.width(), window.height(),
                                                               input.cursorX(), input.cursorY(),
                                                               input.leftClick(), input);
                if (action == StartScreen.Action.CREATE_WORLD ||
                    action == StartScreen.Action.CREATE_RANDOM_WORLD) {
                    if (action == StartScreen.Action.CREATE_RANDOM_WORLD) {
                        currentSeed = System.nanoTime();
                    } else {
                        // Use player-entered seed if provided, otherwise fall back to default
                        String seedText = startScreen.seedText();
                        if (!seedText.isEmpty()) {
                            try {
                                currentSeed = Long.parseLong(seedText);
                            } catch (NumberFormatException e) {
                                currentSeed = WORLD_SEED;
                            }
                        } else {
                            currentSeed = WORLD_SEED;
                        }
                    }
                    world = new World(currentSeed, workers);
                    world.setRenderDistance(settings.renderDistance);
                    Vector3f spawn = prepareSpawn(world);
                    player = new Player(new Vector3f(spawn), settings);
                    milkFrog = MilkFrog.nearPlayer(world, player.position());
                    mobs.spawnNearPlayer(world, player.position());
                    // Reset nether state for the new game
                    netherWorld = null;
                    inNether = false;
                    portalTimer = 0f;
                    portalCooldown = 0f;
                    overworldReturnPos = null;
                    victoryScreen.close();
                    startScreen.close();
                    worldChangedThisFrame = true;
                    input.grabCursor(true);
                } else if (action == StartScreen.Action.QUIT) {
                    window.requestClose();
                }
            } else if (!deathScreen.isOpen() && player != null && player.isDead()) {
                deathScreen.open();
                menu.close();
                chat.close();
                console.close();
                input.grabCursor(false);
            }

            if (victoryScreen.isOpen()) {
                VictoryScreen.Action vAction = victoryScreen.update(window.width(), window.height(),
                                                                    input.cursorX(), input.cursorY(),
                                                                    input.leftClick());
                if (vAction == VictoryScreen.Action.CONTINUE) {
                    // Return to overworld and keep playing
                    if (overworldReturnPos != null) {
                        player.teleportTo(new Vector3f(overworldReturnPos.x,
                                                       overworldReturnPos.y + 0.1f,
                                                       overworldReturnPos.z));
                    }
                    inNether = false;
                    portalTimer = 0f;
                    portalCooldown = PORTAL_COOLDOWN_TIME;
                    victoryScreen.close();
                    worldChangedThisFrame = true;
                    input.grabCursor(true);
                } else if (vAction == VictoryScreen.Action.QUIT) {
                    window.requestClose();
                }
            }

            if (deathScreen.isOpen()) {
                DeathScreen.Action action = deathScreen.update(window.width(), window.height(),
                                                               input.cursorX(), input.cursorY(),
                                                               input.leftClick());
                if (action == DeathScreen.Action.REVIVE_AND_RESET) {
                    world = new World(currentSeed, workers);
                    world.setRenderDistance(settings.renderDistance);
                    Vector3f spawn = prepareSpawn(world);
                    player.respawn(new Vector3f(spawn));
                    milkFrog = MilkFrog.nearPlayer(world, player.position());
                    mobs.spawnNearPlayer(world, player.position());
                    // Always return to overworld on death
                    inNether = false;
                    portalTimer = 0f;
                    portalCooldown = 0f;
                    overworldReturnPos = null;
                    deathScreen.close();
                    worldChangedThisFrame = true;
                    input.grabCursor(true);
                } else if (action == DeathScreen.Action.QUIT) {
                    window.requestClose();
                }
            } else if (chat.isOpen()) {
                chat.update(input);
                if (!chat.isOpen()) input.grabCursor(true);
            } else if (console.active()) {
                console.update(input);
                String command = console.consumeSubmitted();
                if (command != null) {
                    environment.applyCommand(command);
                    input.grabCursor(true);
                } else if (!console.active()) {
                    input.grabCursor(true);
                }
            } else if (!menu.isOpen() && input.keyPressed(GLFW_KEY_SLASH)) {
                console.open(input);
                input.grabCursor(false);
            } else if (!menu.isOpen() && input.keyPressed(GLFW_KEY_T)) {
                chat.open(input);
                input.grabCursor(false);
            } else if (input.keyPressed(GLFW_KEY_E) && player != null
                       && !menu.isOpen() && !console.active() && !chat.isOpen()) {
                if (craftingMenu.isOpen()) { craftingMenu.close(player.inventory()); input.grabCursor(true); }
                else                       { craftingMenu.open();                    input.grabCursor(false); }
            } else if (input.keyPressed(GLFW_KEY_ESCAPE)) {
                if (craftingMenu.isOpen()) { craftingMenu.close(player.inventory()); input.grabCursor(true); }
                else if (menu.isOpen())    { menu.close(); input.grabCursor(true); }
                else                       { menu.open();  input.grabCursor(false); }
            }
            if (startScreen.isOpen()) {
                // Start screen owns input until a world is created.
            } else if (chat.isOpen()) {
                // Chat owns typing until closed.
            } else if (console.active()) {
                // Commands pause world input until submitted or cancelled.
            } else if (victoryScreen.isOpen()) {
                // Victory screen pauses everything until the player chooses.
            } else if (deathScreen.isOpen()) {
                // Death screen pauses the world until revive/reset or quit.
            } else if (worldChangedThisFrame) {
                // Do not let the menu button click also act as a world click.
            } else if (craftingMenu.isOpen()) {
                craftingMenu.update(window.width(), window.height(),
                                    input.cursorX(), input.cursorY(),
                                    input.leftClick(), input.rightClick(), player.inventory());
            } else if (menu.isOpen()) {
                menu.update(window.width(), window.height(),
                            input.cursorX(), input.cursorY(), input.leftClick(), input);
                if (menu.resumeRequested()) {
                    menu.close();
                    input.grabCursor(true);
                }
                // Apply render distance to both worlds when changed.
                if (world != null && world.renderDistance() != settings.renderDistance)
                    world.setRenderDistance(settings.renderDistance);
                if (netherWorld != null && netherWorld.renderDistance() != settings.renderDistance)
                    netherWorld.setRenderDistance(settings.renderDistance);
            } else {
                // If user clicks on the world after the menu released the cursor (e.g.
                // they alt-tabbed and clicked back in), re-grab the cursor.
                if (!input.cursorGrabbed() && input.leftClick()) input.grabCursor(true);
                if (input.leftClick() && input.cursorGrabbed() && player.tryAttack()) {
                    Camera hitCam = player.camera(window.aspect());
                    Block heldTool = player.inventory().selectedBlock();
                    float dmgMult = heldTool == Block.SWORD ? 2.5f
                                  : heldTool == Block.AXE   ? 1.8f : 1.0f;
                    activeMobs.tryHit(hitCam.pos, hitCam.forward, Player.ATTACK_REACH, dmgMult);
                }
                player.update(dt, input, activeWorld, blockEffects);
                if (!inNether && milkFrog != null) milkFrog.update(dt, world, player.position());
                boolean bossAliveBeforeUpdate = inNether && netherMobs.boss() != null
                                               && netherMobs.boss().isAlive();
                activeMobs.update(dt, activeWorld, player.position(), player);
                // Boss just died this frame -> show victory screen
                if (bossAliveBeforeUpdate && netherMobs.boss() == null
                        && !victoryScreen.isOpen()) {
                    victoryScreen.open();
                    menu.close();
                    chat.close();
                    console.close();
                    input.grabCursor(false);
                }
                activeWorld.update(player.position());

                // --- Nether portal detection ---
                if (portalCooldown <= 0f) {
                    Vector3f pp = player.position();
                    int fx = (int) Math.floor(pp.x);
                    int fy = (int) Math.floor(pp.y);
                    int fz = (int) Math.floor(pp.z);
                    boolean inPortal = activeWorld.getBlock(fx, fy, fz) == Block.NETHER_PORTAL
                                    || activeWorld.getBlock(fx, fy + 1, fz) == Block.NETHER_PORTAL;
                    if (inPortal) {
                        portalTimer += dt;
                        if (portalTimer >= PORTAL_CHARGE_TIME) {
                            portalTimer = 0f;
                            portalCooldown = PORTAL_COOLDOWN_TIME;
                            if (!inNether) {
                                // Save position and enter nether
                                overworldReturnPos = new Vector3f(pp);
                                boolean firstEntry = (netherWorld == null);
                                if (firstEntry) {
                                    netherWorld = new World(
                                            new NetherGenerator(currentSeed), workers);
                                    netherWorld.setRenderDistance(settings.renderDistance);
                                    // Force-load spawn chunks synchronously
                                    for (int dz = -3; dz <= 3; dz++)
                                        for (int dx = -3; dx <= 3; dx++)
                                            netherWorld.chunkAt(dx, dz);
                                }
                                Vector3f ns = findNetherSpawn(netherWorld);
                                player.teleportTo(ns);
                                // Build a return portal a few blocks away from spawn
                                int rpx = (int) Math.floor(ns.x) - 1;
                                int rpy = (int) Math.floor(ns.y) - 1;
                                int rpz = (int) Math.floor(ns.z) + 4;
                                PortalHelper.placePortalXY(netherWorld, rpx, rpy, rpz);
                                if (firstEntry) netherMobs.initNether(netherWorld, ns);
                                inNether = true;
                            } else {
                                // Return to overworld
                                Vector3f ret = overworldReturnPos != null
                                        ? overworldReturnPos
                                        : new Vector3f(SPAWN_X + 0.5f, 80f, SPAWN_Z + 0.5f);
                                player.teleportTo(new Vector3f(ret.x, ret.y + 0.1f, ret.z));
                                inNether = false;
                            }
                            worldChangedThisFrame = true;
                        }
                    } else {
                        portalTimer = Math.max(0f, portalTimer - dt * 2f);
                    }
                }
            }

            glViewport(0, 0, window.width(), window.height());
            // Re-evaluate active state after potential teleport this frame
            World  renderWorld = inNether ? netherWorld : world;
            MobManager renderMobs = inNether ? netherMobs : mobs;
            Environment renderEnv = inNether ? netherEnv : environment;
            Vector3f fog = (player != null && player.isUnderwater())
                ? new Vector3f(0.04f, 0.22f, 0.42f)
                : renderEnv.fogColor();
            glClearColor(fog.x, fog.y, fog.z, 1.0f);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            if (renderWorld != null && player != null) {
                Camera cam = applyDamageShake(player.camera(window.aspect()), player,
                                              (float) org.lwjgl.glfw.GLFW.glfwGetTime(),
                                              window.aspect(), settings);
                worldRenderer.render(renderWorld, cam, renderEnv, window.width(), window.height(),
                                     player.isUnderwater(), settings.rayTracingLighting);
                if (!inNether && milkFrog != null) milkFrogRenderer.render(milkFrog, cam);
                mobRenderer.render(renderMobs, cam);
                // Floating name tag above Zhuimu
                var zhuimuAlly = renderMobs.ally();
                if (zhuimuAlly != null && zhuimuAlly.isAlive()) {
                    Vector3f ap = zhuimuAlly.position();
                    float tagWY = ap.y + zhuimuAlly.height() + 0.40f;
                    Matrix4f pv = new Matrix4f(cam.proj).mul(cam.view);
                    Vector4f clip = pv.transform(new Vector4f(ap.x, tagWY, ap.z, 1.0f));
                    if (clip.w > 0.0f) {
                        float ndcX = clip.x / clip.w;
                        float ndcY = clip.y / clip.w;
                        if (ndcX >= -1.05f && ndcX <= 1.05f && ndcY >= -1.05f && ndcY <= 1.05f) {
                            int ww = window.width(), wh = window.height();
                            float sx = (ndcX + 1.0f) * 0.5f * ww;
                            float sy = (1.0f - ndcY) * 0.5f * wh;
                            float tsc = 1.8f * Math.max(0.1f, settings.guiScale);
                            String tagName = "Zhuimu";
                            float tw = tagName.length() * BitmapFont.ADVANCE * tsc;
                            hud.begin2D(ww, wh);
                            // Dark background plate
                            hud.rectPx(sx - tw * 0.5f - 3f, sy - 2f,
                                       tw + 6f, 9f * tsc + 4f, 0f, 0f, 0f, 0.55f);
                            // Drop shadow
                            hud.drawText(tagName, sx - tw * 0.5f + tsc * 0.5f,
                                         sy + tsc * 0.5f, tsc, 0f, 0f, 0f, 0.8f);
                            // Name in lime green
                            hud.drawText(tagName, sx - tw * 0.5f, sy, tsc,
                                         0.20f, 0.90f, 0.15f, 1.0f);
                            hud.end2D();
                        }
                    }
                }
                if (!inNether) weatherRenderer.render(world, cam, environment);
                blockEffects.render(cam, dt, player.breakingX(), player.breakingY(), player.breakingZ(),
                                    player.breakProgress());
                hud.render(window.width(), window.height(), player.inventory(),
                           player.health(), player.maxHealth());
                hud.renderWeatherOverlay(window.width(), window.height(), renderEnv,
                                         (float) org.lwjgl.glfw.GLFW.glfwGetTime());
                // Boss health bar (nether only)
                if (inNether && netherMobs.boss() != null) {
                    hud.renderBossBar(window.width(), window.height(),
                            netherMobs.boss().health(), 200f,
                            "Huanwoshiwanmeidao");
                }
                // Portal charge indicator
                if (portalTimer > 0f) {
                    hud.renderPortalBar(window.width(), window.height(),
                            portalTimer / PORTAL_CHARGE_TIME);
                }
                if (console.active()) {
                    hud.renderCommandConsole(window.width(), window.height(), console.text());
                }
            }
            if (craftingMenu.isOpen() && player != null)
                craftingMenu.render(window.width(), window.height(), hud,
                                    player.inventory(), input.cursorX(), input.cursorY());
            menu.render(hud, window.width(), window.height());
            chat.render(hud, window.width(), window.height());
            deathScreen.render(hud, window.width(), window.height());
            victoryScreen.render(hud, window.width(), window.height());
            startScreen.render(hud, window.width(), window.height());

            window.swap();
            frames++; fpsTimer += dt;
            if (fpsTimer >= 1.0) {
                window.setTitle("NailongCraft - " + frames + " fps");
                frames = 0; fpsTimer = 0;
            }
        }
        workers.shutdown();
        window.destroy();
    }

    /** Finds a safe standing spot near (0,0) in the nether world. */
    private static Vector3f findNetherSpawn(World netherWorld) {
        for (int r = 0; r <= 20; r++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    Vector3f s = spawnAtColumn(netherWorld, dx, dz);
                    if (s != null) return s;
                }
            }
        }
        return new Vector3f(0.5f, 15f + SPAWN_SKIN, 0.5f);
    }

    private static Vector3f prepareSpawn(World world) {
        Vector3f requested = new Vector3f(SPAWN_X + 0.5f, 80f, SPAWN_Z + 0.5f);
        loadSpawnChunks(world, requested);
        return findGroundSpawn(world, SPAWN_X, SPAWN_Z);
    }

    private static void loadSpawnChunks(World world, Vector3f pos) {
        // Force a 7x7 chunk area to exist synchronously so the spawn search
        // has enough land to work with even when the starting column is ocean.
        int spawnCx = (int) Math.floor(pos.x / Chunk.SX);
        int spawnCz = (int) Math.floor(pos.z / Chunk.SZ);
        for (int dz = -3; dz <= 3; dz++) {
            for (int dx = -3; dx <= 3; dx++) {
                world.chunkAt(spawnCx + dx, spawnCz + dz);
            }
        }
    }

    private static Vector3f findGroundSpawn(World world, int preferredX, int preferredZ) {
        for (int radius = 0; radius <= 48; radius++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    Vector3f spawn = spawnAtColumn(world, preferredX + dx, preferredZ + dz);
                    if (spawn != null) return spawn;
                }
            }
        }
        // Last resort: scan the preferred column from the top and place just above
        // the highest solid block, staying above water rather than inside it.
        for (int y = Chunk.SY - 3; y >= 0; y--) {
            Block b = world.getBlock(preferredX, y, preferredZ);
            if (b != Block.AIR) {
                return new Vector3f(preferredX + 0.5f, y + 1f + SPAWN_SKIN, preferredZ + 0.5f);
            }
        }
        return new Vector3f(preferredX + 0.5f, TerrainGenerator.SEA_LEVEL + 5f + SPAWN_SKIN,
                            preferredZ + 0.5f);
    }

    private static Vector3f spawnAtColumn(World world, int x, int z) {
        for (int y = Chunk.SY - 3; y >= 0; y--) {
            if (!isSpawnGround(world.getBlock(x, y, z))) continue;
            if (world.getBlock(x, y + 1, z) != Block.AIR) continue;
            if (world.getBlock(x, y + 2, z) != Block.AIR) continue;
            return new Vector3f(x + 0.5f, y + 1f + SPAWN_SKIN, z + 0.5f);
        }
        return null;
    }

    private static boolean isSpawnGround(Block block) {
        return switch (block) {
            case GRASS, DIRT, STONE, SAND, SNOW, ICE, SANDSTONE, DRY_GRASS, NETHERRACK -> true;
            default -> false;
        };
    }

    private static Camera applyDamageShake(Camera cam, Player player, float time,
                                           float aspect, Settings settings) {
        float shake = player.damageShake();
        if (shake <= 0f) return cam;

        float amp = 0.045f * shake * shake;
        Vector3f right = new Vector3f(cam.forward).cross(cam.up).normalize();
        Vector3f up = new Vector3f(cam.up);
        float sx = (float) Math.sin(time * 83.0f) * amp;
        float sy = (float) Math.cos(time * 97.0f) * amp * 0.65f;
        Vector3f shakenPos = new Vector3f(cam.pos)
            .add(new Vector3f(right).mul(sx))
            .add(new Vector3f(up).mul(sy));
        Vector3f shakenForward = new Vector3f(cam.forward)
            .add(new Vector3f(right).mul(sx * 0.65f))
            .add(new Vector3f(up).mul(sy * 0.65f))
            .normalize();
        return new Camera(shakenPos, shakenForward, settings.fov, aspect, 0.1f, 500f);
    }
}
