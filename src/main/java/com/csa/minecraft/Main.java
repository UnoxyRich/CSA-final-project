package com.csa.minecraft;

import com.csa.minecraft.engine.*;
import com.csa.minecraft.entity.MilkFrog;
import com.csa.minecraft.player.*;
import com.csa.minecraft.render.*;
import com.csa.minecraft.world.*;
import org.joml.Vector3f;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_SLASH;
import static org.lwjgl.opengl.GL11.*;

public class Main {
    private static final long WORLD_SEED = 1337L;
    private static final int SPAWN_X = 8;
    private static final int SPAWN_Z = 8;
    private static final float SPAWN_SKIN = 0.01f;

    public static void main(String[] args) {
        Window window = new Window("Minecraft (CSA)", 1280, 720);
        window.init();
        Input input = new Input(window.handle());

        Settings settings = new Settings();
        World world = null;
        Player player = null;
        MilkFrog milkFrog = null;
        long currentSeed = WORLD_SEED;
        Environment environment = new Environment();
        CommandConsole console = new CommandConsole();
        WorldRenderer worldRenderer = new WorldRenderer();
        WeatherRenderer weatherRenderer = new WeatherRenderer();
        MilkFrogRenderer milkFrogRenderer = new MilkFrogRenderer();
        BlockEffectsRenderer blockEffects = new BlockEffectsRenderer();
        HudRenderer hud = new HudRenderer(settings);
        SettingsMenu menu = new SettingsMenu(settings);
        DeathScreen deathScreen = new DeathScreen(settings);
        StartScreen startScreen = new StartScreen(settings);
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
            boolean worldChangedThisFrame = false;

            if (startScreen.isOpen()) {
                StartScreen.Action action = startScreen.update(window.width(), window.height(),
                                                               input.cursorX(), input.cursorY(),
                                                               input.leftClick());
                if (action == StartScreen.Action.CREATE_WORLD ||
                    action == StartScreen.Action.CREATE_RANDOM_WORLD) {
                    currentSeed = action == StartScreen.Action.CREATE_RANDOM_WORLD
                        ? System.nanoTime()
                        : WORLD_SEED;
                    world = new World(currentSeed);
                    world.setRenderDistance(settings.renderDistance);
                    Vector3f spawn = prepareSpawn(world);
                    player = new Player(new Vector3f(spawn), settings);
                    milkFrog = MilkFrog.nearPlayer(world, player.position());
                    startScreen.close();
                    worldChangedThisFrame = true;
                    input.grabCursor(true);
                } else if (action == StartScreen.Action.QUIT) {
                    window.requestClose();
                }
            } else if (!deathScreen.isOpen() && player != null && player.isDead()) {
                deathScreen.open();
                menu.close();
                console.close();
                input.grabCursor(false);
            }

            if (deathScreen.isOpen()) {
                DeathScreen.Action action = deathScreen.update(window.width(), window.height(),
                                                               input.cursorX(), input.cursorY(),
                                                               input.leftClick());
                if (action == DeathScreen.Action.REVIVE_AND_RESET) {
                    world = new World(currentSeed);
                    world.setRenderDistance(settings.renderDistance);
                    Vector3f spawn = prepareSpawn(world);
                    player.respawn(new Vector3f(spawn));
                    milkFrog = MilkFrog.nearPlayer(world, player.position());
                    deathScreen.close();
                    worldChangedThisFrame = true;
                    input.grabCursor(true);
                } else if (action == DeathScreen.Action.QUIT) {
                    window.requestClose();
                }
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
            } else if (input.keyPressed(GLFW_KEY_ESCAPE)) {
                // ESC toggles the settings menu. Opening the menu ungrabs the cursor;
                // closing it re-grabs and pauses are released.
                if (menu.isOpen()) { menu.close(); input.grabCursor(true); }
                else               { menu.open();  input.grabCursor(false); }
            }
            if (startScreen.isOpen()) {
                // Start screen owns input until a world is created.
            } else if (console.active()) {
                // Commands pause world input until submitted or cancelled.
            } else if (deathScreen.isOpen()) {
                // Death screen pauses the world until revive/reset or quit.
            } else if (worldChangedThisFrame) {
                // Do not let the menu button click also act as a world click.
            } else if (menu.isOpen()) {
                menu.update(window.width(), window.height(),
                            input.cursorX(), input.cursorY(), input.leftClick());
                if (menu.resumeRequested()) {
                    menu.close();
                    input.grabCursor(true);
                }
                // Apply any settings that have downstream state (render distance).
                if (world.renderDistance() != settings.renderDistance) {
                    world.setRenderDistance(settings.renderDistance);
                }
            } else {
                // If user clicks on the world after the menu released the cursor (e.g.
                // they alt-tabbed and clicked back in), re-grab the cursor.
                if (!input.cursorGrabbed() && input.leftClick()) input.grabCursor(true);
                player.update(dt, input, world, blockEffects);
                milkFrog.update(dt, world, player.position());
                world.update(player.position());
            }

            glViewport(0, 0, window.width(), window.height());
            Vector3f fog = player != null && player.isUnderwater()
                ? new Vector3f(0.04f, 0.22f, 0.42f)
                : environment.fogColor();
            glClearColor(fog.x, fog.y, fog.z, 1.0f);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            if (world != null && player != null) {
                Camera cam = applyDamageShake(player.camera(window.aspect()), player,
                                              (float) org.lwjgl.glfw.GLFW.glfwGetTime(),
                                              window.aspect(), settings);
                worldRenderer.render(world, cam, environment, window.width(), window.height(),
                                     player.isUnderwater(), settings.rayTracingLighting);
                milkFrogRenderer.render(milkFrog, cam);
                weatherRenderer.render(world, cam, environment);
                blockEffects.render(cam, dt, player.breakingX(), player.breakingY(), player.breakingZ(),
                                    player.breakProgress());
                hud.render(window.width(), window.height(), player.inventory(),
                           player.health(), player.maxHealth());
                hud.renderWeatherOverlay(window.width(), window.height(), environment,
                                         (float) org.lwjgl.glfw.GLFW.glfwGetTime());
                if (console.active()) {
                    hud.renderCommandConsole(window.width(), window.height(), console.text());
                }
            }
            menu.render(hud, window.width(), window.height());
            deathScreen.render(hud, window.width(), window.height());
            startScreen.render(hud, window.width(), window.height());

            window.swap();
            frames++; fpsTimer += dt;
            if (fpsTimer >= 1.0) {
                window.setTitle("Minecraft (CSA) - " + frames + " fps");
                frames = 0; fpsTimer = 0;
            }
        }
        window.destroy();
    }

    private static Vector3f prepareSpawn(World world) {
        Vector3f requested = new Vector3f(SPAWN_X + 0.5f, 80f, SPAWN_Z + 0.5f);
        loadSpawnChunks(world, requested);
        return findGroundSpawn(world, SPAWN_X, SPAWN_Z);
    }

    private static void loadSpawnChunks(World world, Vector3f pos) {
        // Generation is budgeted per frame, so force the spawn chunk and its
        // neighbors to exist up front — the player must not spawn into air.
        int spawnCx = (int) Math.floor(pos.x / Chunk.SX);
        int spawnCz = (int) Math.floor(pos.z / Chunk.SZ);
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                world.chunkAt(spawnCx + dx, spawnCz + dz);
            }
        }
    }

    private static Vector3f findGroundSpawn(World world, int preferredX, int preferredZ) {
        for (int radius = 0; radius <= 12; radius++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    Vector3f spawn = spawnAtColumn(world, preferredX + dx, preferredZ + dz);
                    if (spawn != null) return spawn;
                }
            }
        }
        return new Vector3f(preferredX + 0.5f, TerrainGenerator.SEA_LEVEL + 1f + SPAWN_SKIN,
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
            case GRASS, DIRT, STONE, SAND, SNOW, ICE, SANDSTONE, DRY_GRASS -> true;
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
