package com.csa.minecraft.render;

import com.csa.minecraft.Settings;
import com.csa.minecraft.engine.Input;

import static org.lwjgl.glfw.GLFW.*;

public class StartScreen {
    public enum Action {
        NONE,
        CREATE_WORLD,
        CREATE_RANDOM_WORLD,
        QUIT
    }

    private final Settings settings;
    private boolean open = true;
    // Digits (and optional leading minus) typed into the seed field
    private String seedInput = "";

    private static final int PANEL_W = 460;
    private static final int PANEL_H = 390;
    private static final int BUTTON_W = 270;
    private static final int BUTTON_H = 42;

    public StartScreen(Settings settings) {
        this.settings = settings;
    }

    public boolean isOpen() { return open; }
    public void close() { open = false; }

    /** The raw seed string the player typed, empty string if nothing was entered. */
    public String seedText() { return seedInput; }

    public Action update(int viewW, int viewH, double cursorPx, double cursorPy,
                         boolean clicked, Input input) {
        if (!open) return Action.NONE;

        // Process keyboard: only digits, optional leading minus, backspace, enter
        if (input != null) {
            String chars = input.consumeTypedChars();
            for (int i = 0; i < chars.length(); i++) {
                char c = chars.charAt(i);
                if (c == '-' && seedInput.isEmpty()) {
                    seedInput += c;
                } else if (c >= '0' && c <= '9' && seedInput.length() < 18) {
                    seedInput += c;
                }
            }
            if (input.keyPressed(GLFW_KEY_BACKSPACE) && !seedInput.isEmpty()) {
                seedInput = seedInput.substring(0, seedInput.length() - 1);
            }
            // Enter / numpad-enter launches with the current seed
            if (input.keyPressed(GLFW_KEY_ENTER) || input.keyPressed(GLFW_KEY_KP_ENTER)) {
                return Action.CREATE_WORLD;
            }
        }

        if (!clicked) return Action.NONE;

        int panelW = s(PANEL_W);
        int panelH = s(PANEL_H);
        int panelX = (viewW - panelW) / 2;
        int panelY = (viewH - panelH) / 2;
        int btnW = s(BUTTON_W);
        int btnH = s(BUTTON_H);
        int btnX = panelX + (panelW - btnW) / 2;
        int createY = panelY + s(160);
        int randomY = createY + btnH + s(16);
        int quitY = randomY + btnH + s(16);

        if (in(cursorPx, cursorPy, btnX, createY, btnW, btnH)) return Action.CREATE_WORLD;
        if (in(cursorPx, cursorPy, btnX, randomY, btnW, btnH)) return Action.CREATE_RANDOM_WORLD;
        if (in(cursorPx, cursorPy, btnX, quitY, btnW, btnH)) return Action.QUIT;
        return Action.NONE;
    }

    public void render(HudRenderer hud, int viewW, int viewH) {
        if (!open) return;

        hud.begin2D(viewW, viewH);
        hud.rectPx(0, 0, viewW, viewH, 0.05f, 0.10f, 0.14f, 1f);

        int panelW = s(PANEL_W);
        int panelH = s(PANEL_H);
        int panelX = (viewW - panelW) / 2;
        int panelY = (viewH - panelH) / 2;
        int titleScale = Math.max(1, Math.round(5f * settings.guiScale));
        int textScale = Math.max(1, Math.round(2f * settings.guiScale));

        hud.rectPx(panelX, panelY, panelW, panelH, 0.10f, 0.12f, 0.13f, 0.96f);
        outline(hud, panelX, panelY, panelW, panelH, s(2), 0.95f, 0.95f, 0.95f, 0.95f);

        drawCentered(hud, "NAILONGCRAFT", panelX, panelW, panelY + s(28), titleScale,
                     0.95f, 0.95f, 0.95f, 1f);

        // Seed input section
        int seedLabelY = panelY + s(68);
        int seedFieldY = panelY + s(86);
        int fieldW = panelW - s(40);
        int fieldX = panelX + s(20);
        int fieldH = s(28);

        drawCentered(hud, "SEED (LEAVE BLANK FOR DEFAULT):", panelX, panelW,
                     seedLabelY, textScale, 0.72f, 0.88f, 1f, 0.95f);
        hud.rectPx(fieldX, seedFieldY, fieldW, fieldH, 0.04f, 0.06f, 0.08f, 0.92f);
        outline(hud, fieldX, seedFieldY, fieldW, fieldH, s(1), 0.45f, 0.65f, 1f, 0.9f);

        String display = seedInput.isEmpty() ? "(DEFAULT)" : seedInput;
        float seedAlpha = seedInput.isEmpty() ? 0.4f : 1f;
        float seedR = seedInput.isEmpty() ? 0.7f : 1f;
        float seedG = seedInput.isEmpty() ? 0.7f : 1f;
        float seedB = seedInput.isEmpty() ? 0.7f : 0.85f;
        hud.drawText(display, fieldX + s(7),
                     seedFieldY + (fieldH - BitmapFont.H * textScale) / 2f,
                     textScale, seedR, seedG, seedB, seedAlpha);

        drawCentered(hud, "CREATE A WORLD", panelX, panelW, panelY + s(126), textScale,
                     0.72f, 0.88f, 1f, 0.95f);

        int btnW = s(BUTTON_W);
        int btnH = s(BUTTON_H);
        int btnX = panelX + (panelW - btnW) / 2;
        int createY = panelY + s(160);
        int randomY = createY + btnH + s(16);
        int quitY = randomY + btnH + s(16);

        drawButton(hud, btnX, createY, btnW, btnH, "CREATE WORLD", 0.22f, 0.42f, 0.24f, textScale);
        drawButton(hud, btnX, randomY, btnW, btnH, "RANDOM WORLD", 0.22f, 0.34f, 0.50f, textScale);
        drawButton(hud, btnX, quitY, btnW, btnH, "QUIT", 0.45f, 0.16f, 0.16f, textScale);

        hud.end2D();
    }

    private void drawButton(HudRenderer hud, int x, int y, int w, int h, String text,
                            float r, float g, float b, int scale) {
        hud.rectPx(x, y, w, h, r, g, b, 1f);
        outline(hud, x, y, w, h, s(2), 1f, 1f, 1f, 1f);
        int textW = BitmapFont.textWidth(text) * scale;
        hud.drawText(text, x + (w - textW) / 2f,
                     y + (h - BitmapFont.H * scale) / 2f,
                     scale, 1f, 1f, 1f, 1f);
    }

    private void drawCentered(HudRenderer hud, String text, int x, int w, int y, int scale,
                              float r, float g, float b, float a) {
        int textW = BitmapFont.textWidth(text) * scale;
        hud.drawText(text, x + (w - textW) / 2f, y, scale, r, g, b, a);
    }

    private int s(int v) {
        return Math.max(1, Math.round(v * settings.guiScale));
    }

    private static boolean in(double cx, double cy, int x, int y, int w, int h) {
        return cx >= x && cx < x + w && cy >= y && cy < y + h;
    }

    private static void outline(HudRenderer hud, int x, int y, int w, int h, int thickness,
                                float r, float g, float b, float a) {
        hud.rectPx(x, y, w, thickness, r, g, b, a);
        hud.rectPx(x, y + h - thickness, w, thickness, r, g, b, a);
        hud.rectPx(x, y, thickness, h, r, g, b, a);
        hud.rectPx(x + w - thickness, y, thickness, h, r, g, b, a);
    }
}
