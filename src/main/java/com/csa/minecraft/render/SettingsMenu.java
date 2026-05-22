package com.csa.minecraft.render;

import com.csa.minecraft.Settings;
import com.csa.minecraft.engine.Input;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Pause-screen overlay with sliders + a toggle + a resume button. Everything lays out in
 * pixel coordinates (origin top-left) inside a centered panel; click detection uses the
 * same pixel-space rects, so the menu scales correctly when the window resizes.
 *
 * All layout values pass through {@link #s(int)} which multiplies by {@code settings.guiScale}
 * so the menu honors the GUI scale setting live.
 *
 * Call order each frame (when open):
 *   menu.update(viewW, viewH, cursorPx, cursorPy, clickPressed, input)
 *   menu.render(hud, viewW, viewH)
 *
 * {@link #resumeRequested()} flips to true on the frame the user clicks "RESUME".
 */
public class SettingsMenu {
    private final Settings settings;
    private boolean open = false;
    private boolean resumeRequested = false;
    private boolean apiKeyEditing = false;

    private static final int PANEL_W = 520, PANEL_H = 548;
    private static final int ROW_H   = 46;
    private static final int LABEL_SCALE = 2;     // 5x7 glyph → 10x14 px before GUI scale
    private static final int VALUE_SCALE = 2;
    private static final int TITLE_SCALE = 4;
    private static final int PADDING = 18;

    public SettingsMenu(Settings settings) { this.settings = settings; }

    public boolean isOpen() { return open; }
    public void open() { open = true; resumeRequested = false; }
    public void close() { open = false; apiKeyEditing = false; }
    public boolean resumeRequested() { return resumeRequested; }

    /** Scale a base pixel constant by the current GUI scale, never below 1. */
    private int s(int v) { return Math.max(1, Math.round(v * settings.guiScale)); }

    public void update(int viewW, int viewH, double cursorPx, double cursorPy, boolean clicked, Input input) {
        if (!open) return;
        resumeRequested = false;
        int panelW = s(PANEL_W), panelH = s(PANEL_H);
        int panelX = (viewW - panelW) / 2;
        int panelY = (viewH - panelH) / 2;
        int padding = s(PADDING);
        int rowH = s(ROW_H);
        int titleScale = Math.max(1, Math.round(TITLE_SCALE * settings.guiScale));
        int titleH = BitmapFont.H * titleScale;
        int rowsTop = panelY + padding + titleH + padding;

        // Row 0 — mouse sensitivity
        handleSlider(cursorPx, cursorPy, clicked, panelX, panelW, rowsTop + 0 * rowH,
                     Settings.SENS_MIN, Settings.SENS_MAX,
                     v -> settings.mouseSensitivity = v);

        // Row 1 — invert Y
        if (clicked && inToggleRect(cursorPx, cursorPy, panelX, panelW, rowsTop + 1 * rowH)) {
            settings.invertY = !settings.invertY;
        }

        // Row 2 — FOV
        handleSlider(cursorPx, cursorPy, clicked, panelX, panelW, rowsTop + 2 * rowH,
                     Settings.FOV_MIN, Settings.FOV_MAX,
                     v -> settings.fov = v);

        // Row 3 — render distance (snap to int)
        handleSlider(cursorPx, cursorPy, clicked, panelX, panelW, rowsTop + 3 * rowH,
                     Settings.DIST_MIN, Settings.DIST_MAX,
                     v -> settings.renderDistance = Math.round(v));

        // Row 4 — GUI scale
        handleSlider(cursorPx, cursorPy, clicked, panelX, panelW, rowsTop + 4 * rowH,
                     Settings.GUI_MIN, Settings.GUI_MAX,
                     v -> settings.guiScale = v);

        // Row 5 — ray traced lighting
        if (clicked && inToggleRect(cursorPx, cursorPy, panelX, panelW, rowsTop + 5 * rowH)) {
            settings.rayTracingLighting = !settings.rayTracingLighting;
        }

        // Row 6 — game mode
        if (clicked && inModeRect(cursorPx, cursorPy, panelX, panelW, rowsTop + 6 * rowH)) {
            settings.gameMode = settings.gameMode.next();
        }

        int keyY = rowsTop + 7 * rowH;
        if (clicked) apiKeyEditing = inApiKeyRect(cursorPx, cursorPy, panelX, panelW, keyY);
        if (apiKeyEditing) updateApiKey(input);

        // Resume button
        int btnW = s(160), btnH = s(36);
        int btnX = panelX + (panelW - btnW) / 2;
        int btnY = panelY + panelH - padding - btnH;
        if (clicked && in(cursorPx, cursorPy, btnX, btnY, btnW, btnH)) {
            resumeRequested = true;
        }
    }

    private void updateApiKey(Input input) {
        if (input.commandOrControlDown() && input.keyPressed(GLFW_KEY_V)) {
            appendApiKeyText(input.clipboardText());
            input.clearTypedChars();
        } else {
            appendApiKeyText(input.consumeTypedChars());
        }
        if (input.keyPressed(GLFW_KEY_BACKSPACE) && !settings.deepSeekApiKey.isEmpty()) {
            settings.deepSeekApiKey = settings.deepSeekApiKey.substring(0, settings.deepSeekApiKey.length() - 1);
        }
        if (input.keyPressed(GLFW_KEY_ENTER) || input.keyPressed(GLFW_KEY_KP_ENTER)
                || input.keyPressed(GLFW_KEY_ESCAPE)) {
            apiKeyEditing = false;
        }
    }

    private void appendApiKeyText(String text) {
        for (int i = 0; i < text.length() && settings.deepSeekApiKey.length() < 160; i++) {
            char c = text.charAt(i);
            if (c >= 32 && c < 127) settings.deepSeekApiKey += c;
        }
    }

    private interface FloatSetter { void set(float v); }

    private void handleSlider(double cx, double cy, boolean clicked,
                              int panelX, int panelW, int rowY,
                              float min, float max, FloatSetter setter) {
        int padding = s(PADDING);
        int trackX = panelX + padding;
        int trackY = rowY + s(26);
        int trackW = panelW - 2 * padding - s(110);
        int trackH = s(12);
        // Hit rect is generous around the track so clicks near the bar still register.
        if (clicked && in(cx, cy, trackX - s(6), trackY - s(14), trackW + s(12), trackH + s(28))) {
            float t = (float) ((cx - trackX) / (double) trackW);
            t = Math.max(0f, Math.min(1f, t));
            setter.set(min + t * (max - min));
        }
    }

    private boolean inToggleRect(double cx, double cy, int panelX, int panelW, int rowY) {
        int padding = s(PADDING);
        int boxW = s(80), boxH = s(26);
        int boxX = panelX + panelW - padding - boxW;
        int boxY = rowY + s(14);
        return in(cx, cy, boxX, boxY, boxW, boxH);
    }

    private boolean inModeRect(double cx, double cy, int panelX, int panelW, int rowY) {
        int padding = s(PADDING);
        int boxW = s(150), boxH = s(26);
        int boxX = panelX + panelW - padding - boxW;
        int boxY = rowY + s(14);
        return in(cx, cy, boxX, boxY, boxW, boxH);
    }

    private boolean inApiKeyRect(double cx, double cy, int panelX, int panelW, int rowY) {
        int padding = s(PADDING);
        int boxW = panelW - 2 * padding;
        int boxH = s(28);
        int boxX = panelX + padding;
        int boxY = rowY + s(18);
        return in(cx, cy, boxX, boxY, boxW, boxH);
    }

    private static boolean in(double cx, double cy, int x, int y, int w, int h) {
        return cx >= x && cx < x + w && cy >= y && cy < y + h;
    }

    public void render(HudRenderer hud, int viewW, int viewH) {
        if (!open) return;
        hud.begin2D(viewW, viewH);

        // Full-screen dim
        hud.rectPx(0, 0, viewW, viewH, 0f, 0f, 0f, 0.55f);

        int panelW = s(PANEL_W), panelH = s(PANEL_H);
        int panelX = (viewW - panelW) / 2;
        int panelY = (viewH - panelH) / 2;
        int padding = s(PADDING);
        int rowH = s(ROW_H);
        int titleScale = Math.max(1, Math.round(TITLE_SCALE * settings.guiScale));
        int labelScale = Math.max(1, Math.round(LABEL_SCALE * settings.guiScale));
        int valueScale = Math.max(1, Math.round(VALUE_SCALE * settings.guiScale));
        int titleH = BitmapFont.H * titleScale;

        // Panel + outline
        hud.rectPx(panelX, panelY, panelW, panelH, 0.10f, 0.10f, 0.12f, 0.95f);
        outline(hud, panelX, panelY, panelW, panelH, s(2), 1, 1, 1, 0.9f);

        // Title centered horizontally
        String title = "SETTINGS";
        int titleW = BitmapFont.textWidth(title) * titleScale;
        hud.drawText(title, panelX + (panelW - titleW) / 2f, panelY + padding,
                     titleScale, 1, 1, 1, 1);

        int rowsTop = panelY + padding + titleH + padding;
        drawSliderRow(hud, panelX, panelW, rowsTop + 0 * rowH, "MOUSE SENSITIVITY",
                      Settings.SENS_MIN, Settings.SENS_MAX, settings.mouseSensitivity,
                      String.format("%.2f", settings.mouseSensitivity),
                      labelScale, valueScale);
        drawToggleRow(hud, panelX, panelW, rowsTop + 1 * rowH, "INVERT Y", settings.invertY,
                      labelScale, valueScale);
        drawSliderRow(hud, panelX, panelW, rowsTop + 2 * rowH, "FOV",
                      Settings.FOV_MIN, Settings.FOV_MAX, settings.fov,
                      String.valueOf(Math.round(settings.fov)),
                      labelScale, valueScale);
        drawSliderRow(hud, panelX, panelW, rowsTop + 3 * rowH, "RENDER DISTANCE",
                      Settings.DIST_MIN, Settings.DIST_MAX, settings.renderDistance,
                      String.valueOf(settings.renderDistance),
                      labelScale, valueScale);
        drawSliderRow(hud, panelX, panelW, rowsTop + 4 * rowH, "GUI SCALE",
                      Settings.GUI_MIN, Settings.GUI_MAX, settings.guiScale,
                      String.format("%.2f", settings.guiScale),
                      labelScale, valueScale);
        drawToggleRow(hud, panelX, panelW, rowsTop + 5 * rowH, "RAY TRACING", settings.rayTracingLighting,
                      labelScale, valueScale);
        drawModeRow(hud, panelX, panelW, rowsTop + 6 * rowH, "MODE", settings.gameMode.name(),
                    labelScale, valueScale);
        drawApiKeyRow(hud, panelX, panelW, rowsTop + 7 * rowH, labelScale, valueScale);

        // Resume button
        int btnW = s(160), btnH = s(36);
        int btnX = panelX + (panelW - btnW) / 2;
        int btnY = panelY + panelH - padding - btnH;
        hud.rectPx(btnX, btnY, btnW, btnH, 0.25f, 0.45f, 0.25f, 1f);
        outline(hud, btnX, btnY, btnW, btnH, s(2), 1, 1, 1, 1);
        String resume = "RESUME";
        int rw = BitmapFont.textWidth(resume) * valueScale;
        hud.drawText(resume, btnX + (btnW - rw) / 2f,
                     btnY + (btnH - BitmapFont.H * valueScale) / 2f,
                     valueScale, 1, 1, 1, 1);

        hud.end2D();
    }

    private void drawApiKeyRow(HudRenderer hud, int panelX, int panelW, int rowY,
                               int labelScale, int valueScale) {
        int padding = s(PADDING);
        hud.drawText("DEEPSEEK API KEY", panelX + padding, rowY, labelScale, 1, 1, 1, 1);
        int boxW = panelW - 2 * padding;
        int boxH = s(28);
        int boxX = panelX + padding;
        int boxY = rowY + s(18);
        hud.rectPx(boxX, boxY, boxW, boxH, 0.06f, 0.07f, 0.08f, 1f);
        outline(hud, boxX, boxY, boxW, boxH, s(2),
                apiKeyEditing ? 0.45f : 1f,
                apiKeyEditing ? 0.75f : 1f,
                apiKeyEditing ? 0.95f : 1f,
                1f);
        String text = settings.deepSeekApiKey.isEmpty()
            ? "CLICK TO ENTER KEY"
            : ("SAVED " + settings.deepSeekApiKey.length() + " CHARS");
        if (apiKeyEditing) text = settings.deepSeekApiKey.isEmpty() ? "TYPING" : mask(settings.deepSeekApiKey);
        hud.drawText(text, boxX + s(8), boxY + s(7), valueScale, 1, 1, 1, 0.95f);
    }

    private static String mask(String value) {
        int visible = Math.min(4, value.length());
        int hidden = Math.min(18, Math.max(0, value.length() - visible));
        return "-".repeat(hidden) + value.substring(value.length() - visible);
    }

    private void drawSliderRow(HudRenderer hud, int panelX, int panelW, int rowY,
                               String label, float min, float max, float current,
                               String valueText, int labelScale, int valueScale) {
        int padding = s(PADDING);
        hud.drawText(label, panelX + padding, rowY, labelScale, 1, 1, 1, 1);

        int trackX = panelX + padding;
        int trackY = rowY + s(26);
        int trackW = panelW - 2 * padding - s(110);
        int trackH = s(12);
        hud.rectPx(trackX, trackY, trackW, trackH, 0.25f, 0.25f, 0.28f, 1f);
        float t = (current - min) / (max - min);
        t = Math.max(0f, Math.min(1f, t));
        hud.rectPx(trackX, trackY, trackW * t, trackH, 0.45f, 0.75f, 0.95f, 1f);
        int knobW = s(10);
        hud.rectPx(trackX + trackW * t - knobW / 2f, trackY - s(4),
                   knobW, trackH + s(8), 1, 1, 1, 1);

        int valX = trackX + trackW + s(14);
        hud.drawText(valueText, valX, rowY + s(14), valueScale, 1, 1, 1, 1);
    }

    private void drawToggleRow(HudRenderer hud, int panelX, int panelW, int rowY,
                               String label, boolean on, int labelScale, int valueScale) {
        int padding = s(PADDING);
        hud.drawText(label, panelX + padding, rowY, labelScale, 1, 1, 1, 1);
        int boxW = s(80), boxH = s(26);
        int boxX = panelX + panelW - padding - boxW;
        int boxY = rowY + s(14);
        if (on) hud.rectPx(boxX, boxY, boxW, boxH, 0.25f, 0.55f, 0.30f, 1f);
        else    hud.rectPx(boxX, boxY, boxW, boxH, 0.55f, 0.25f, 0.25f, 1f);
        outline(hud, boxX, boxY, boxW, boxH, s(2), 1, 1, 1, 1);
        String txt = on ? "ON" : "OFF";
        int tw = BitmapFont.textWidth(txt) * valueScale;
        hud.drawText(txt, boxX + (boxW - tw) / 2f,
                     boxY + (boxH - BitmapFont.H * valueScale) / 2f,
                     valueScale, 1, 1, 1, 1);
    }

    private void drawModeRow(HudRenderer hud, int panelX, int panelW, int rowY,
                             String label, String mode, int labelScale, int valueScale) {
        int padding = s(PADDING);
        hud.drawText(label, panelX + padding, rowY, labelScale, 1, 1, 1, 1);
        int boxW = s(150), boxH = s(26);
        int boxX = panelX + panelW - padding - boxW;
        int boxY = rowY + s(14);
        hud.rectPx(boxX, boxY, boxW, boxH, 0.22f, 0.34f, 0.48f, 1f);
        outline(hud, boxX, boxY, boxW, boxH, s(2), 1, 1, 1, 1);
        int tw = BitmapFont.textWidth(mode) * valueScale;
        hud.drawText(mode, boxX + (boxW - tw) / 2f,
                     boxY + (boxH - BitmapFont.H * valueScale) / 2f,
                     valueScale, 1, 1, 1, 1);
    }

    private static void outline(HudRenderer hud, int x, int y, int w, int h, int thickness,
                                float r, float g, float b, float a) {
        hud.rectPx(x, y, w, thickness, r, g, b, a);
        hud.rectPx(x, y + h - thickness, w, thickness, r, g, b, a);
        hud.rectPx(x, y, thickness, h, r, g, b, a);
        hud.rectPx(x + w - thickness, y, thickness, h, r, g, b, a);
    }
}
