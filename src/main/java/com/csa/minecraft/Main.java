package com.csa.minecraft;

import com.csa.minecraft.engine.*;
import com.csa.minecraft.player.*;
import com.csa.minecraft.render.*;
import com.csa.minecraft.world.*;
import org.joml.Vector3f;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_SLASH;
import static org.lwjgl.opengl.GL11.*;

public class Main {
    public static void main(String[] args) {
        Window window = new Window("Minecraft (CSA)", 1280, 720);
        window.init();
        Input input = new Input(window.handle());

        Settings settings = new Settings();
        World world = new World(1337L);
        world.setRenderDistance(settings.renderDistance);
        Player player = new Player(new Vector3f(8, 80, 8), settings);
        Environment environment = new Environment();
        CommandConsole console = new CommandConsole();
        WorldRenderer worldRenderer = new WorldRenderer();
        WeatherRenderer weatherRenderer = new WeatherRenderer();
        BlockEffectsRenderer blockEffects = new BlockEffectsRenderer();
        HudRenderer hud = new HudRenderer(settings);
        SettingsMenu menu = new SettingsMenu(settings);

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

            if (console.active()) {
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
            if (console.active()) {
                // Commands pause world input until submitted or cancelled.
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
                world.update(player.position());
            }

            glViewport(0, 0, window.width(), window.height());
            Vector3f fog = player.isUnderwater() ? new Vector3f(0.04f, 0.22f, 0.42f) : environment.fogColor();
            glClearColor(fog.x, fog.y, fog.z, 1.0f);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            Camera cam = player.camera(window.aspect());
            worldRenderer.render(world, cam, environment, window.width(), window.height(), player.isUnderwater());
            weatherRenderer.render(world, cam, environment);
            blockEffects.render(cam, dt, player.breakingX(), player.breakingY(), player.breakingZ(),
                                player.breakProgress());
            hud.render(window.width(), window.height(), player.inventory());
            hud.renderWeatherOverlay(window.width(), window.height(), environment,
                                     (float) org.lwjgl.glfw.GLFW.glfwGetTime());
            if (console.active()) {
                hud.renderCommandConsole(window.width(), window.height(), console.text());
            }
            menu.render(hud, window.width(), window.height());

            window.swap();
            frames++; fpsTimer += dt;
            if (fpsTimer >= 1.0) {
                window.setTitle("Minecraft (CSA) - " + frames + " fps");
                frames = 0; fpsTimer = 0;
            }
        }
        window.destroy();
    }
}
