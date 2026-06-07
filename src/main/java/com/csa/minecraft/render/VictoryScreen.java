package com.csa.minecraft.render;

import com.csa.minecraft.Settings;

/**
 * Shown when the player defeats Huanwoshiwanmeidao.
 * Offers CONTINUE (keep playing in the overworld) or QUIT.
 */
public class VictoryScreen {
    public enum Action { NONE, CONTINUE, QUIT }

    private final Settings settings;
    private boolean open = false;

    private static final int PANEL_W  = 500;
    private static final int PANEL_H  = 310;
    private static final int BUTTON_W = 260;
    private static final int BUTTON_H = 44;

    public VictoryScreen(Settings settings) { this.settings = settings; }

    public boolean isOpen() { return open; }
    public void open()      { open = true;  }
    public void close()     { open = false; }

    public Action update(int viewW, int viewH, double cx, double cy, boolean clicked) {
        if (!open || !clicked) return Action.NONE;

        int panelW  = s(PANEL_W);
        int panelH  = s(PANEL_H);
        int panelX  = (viewW - panelW) / 2;
        int panelY  = (viewH - panelH) / 2;
        int btnW    = s(BUTTON_W);
        int btnH    = s(BUTTON_H);
        int btnX    = panelX + (panelW - btnW) / 2;
        int contY   = panelY + s(186);
        int quitY   = contY  + btnH + s(16);

        if (in(cx, cy, btnX, contY, btnW, btnH)) return Action.CONTINUE;
        if (in(cx, cy, btnX, quitY, btnW, btnH)) return Action.QUIT;
        return Action.NONE;
    }

    public void render(HudRenderer hud, int viewW, int viewH) {
        if (!open) return;

        hud.begin2D(viewW, viewH);

        // Full-screen dark purple overlay
        hud.rectPx(0, 0, viewW, viewH, 0.05f, 0.00f, 0.14f, 0.82f);

        int panelW = s(PANEL_W);
        int panelH = s(PANEL_H);
        int panelX = (viewW - panelW) / 2;
        int panelY = (viewH - panelH) / 2;

        // Panel background
        hud.rectPx(panelX, panelY, panelW, panelH, 0.06f, 0.04f, 0.10f, 0.97f);
        outline(hud, panelX, panelY, panelW, panelH, s(3), 0.92f, 0.78f, 0.10f, 1f);

        int titleScale = Math.max(1, Math.round(5f * settings.guiScale));
        int subScale   = Math.max(1, Math.round(2f * settings.guiScale));

        // Title: gold glow
        drawCentered(hud, "VICTORY!", panelX, panelW, panelY + s(22),
                     titleScale, 1.0f, 0.88f, 0.10f, 1f);

        // Boss name subdued below title
        drawCentered(hud, "Huanwoshiwanmeidao has been defeated.",
                     panelX, panelW, panelY + s(92), subScale,
                     0.78f, 0.55f, 1.0f, 1f);

        // Flavour line
        drawCentered(hud, "The Nether is yours.",
                     panelX, panelW, panelY + s(118), subScale,
                     1f, 1f, 1f, 0.85f);

        int btnW  = s(BUTTON_W);
        int btnH  = s(BUTTON_H);
        int btnX  = panelX + (panelW - btnW) / 2;
        int contY = panelY + s(186);
        int quitY = contY + btnH + s(16);

        drawButton(hud, btnX, contY, btnW, btnH, "CONTINUE",
                   0.15f, 0.48f, 0.18f, subScale);
        drawButton(hud, btnX, quitY, btnW, btnH, "QUIT",
                   0.40f, 0.12f, 0.12f, subScale);

        hud.end2D();
    }

    // --- helpers ---

    private void drawButton(HudRenderer hud, int x, int y, int w, int h,
                            String text, float r, float g, float b, int scale) {
        hud.rectPx(x, y, w, h, r, g, b, 1f);
        outline(hud, x, y, w, h, s(2), 1f, 1f, 1f, 1f);
        int tw = BitmapFont.textWidth(text) * scale;
        hud.drawText(text, x + (w - tw) / 2f,
                     y + (h - BitmapFont.H * scale) / 2f,
                     scale, 1f, 1f, 1f, 1f);
    }

    private void drawCentered(HudRenderer hud, String text,
                              int panelX, int panelW, int y, int scale,
                              float r, float g, float b, float a) {
        int tw = BitmapFont.textWidth(text) * scale;
        hud.drawText(text, panelX + (panelW - tw) / 2f, y, scale, r, g, b, a);
    }

    private int s(int v) {
        return Math.max(1, Math.round(v * settings.guiScale));
    }

    private static boolean in(double cx, double cy, int x, int y, int w, int h) {
        return cx >= x && cx < x + w && cy >= y && cy < y + h;
    }

    private static void outline(HudRenderer hud, int x, int y, int w, int h,
                                int t, float r, float g, float b, float a) {
        hud.rectPx(x,         y,         w, t, r, g, b, a);
        hud.rectPx(x,         y + h - t, w, t, r, g, b, a);
        hud.rectPx(x,         y,         t, h, r, g, b, a);
        hud.rectPx(x + w - t, y,         t, h, r, g, b, a);
    }
}
