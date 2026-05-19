package com.csa.minecraft.render;

import com.csa.minecraft.Environment;
import com.csa.minecraft.Settings;
import com.csa.minecraft.engine.Mesh;
import com.csa.minecraft.engine.Shader;
import com.csa.minecraft.player.Inventory;

import static org.lwjgl.opengl.GL11.*;

public class HudRenderer {
    private final Shader shader;
    private final Mesh quad;
    private final Settings settings;
    private int viewW = 1, viewH = 1;

    private static final String VERT = """
        #version 330 core
        layout(location=0) in vec2 aPos;
        uniform vec4 uRect; // x,y,w,h in NDC
        void main(){
            vec2 p = uRect.xy + aPos * uRect.zw;
            gl_Position = vec4(p, 0.0, 1.0);
        }
        """;
    private static final String FRAG = """
        #version 330 core
        out vec4 f;
        uniform vec4 uColor;
        void main(){ f = uColor; }
        """;

    public HudRenderer() { this(new Settings()); }
    public HudRenderer(Settings settings) {
        this.settings = settings;
        shader = new Shader(VERT, FRAG);
        quad = new Mesh();
        float[] verts = { 0,0,  1,0,  1,1,   0,0,  1,1,  0,1 };
        quad.upload(verts, new int[]{2});
    }

    public void render(int w, int h, Inventory inv) {
        begin2D(w, h);
        float gui = Math.max(0.1f, settings.guiScale);

        // crosshair (small white plus) — arm length scales with GUI scale
        float pxX = 2f / w, pxY = 2f / h;
        float armHalf = 10f * gui;
        rect(-pxX * 1, -pxY * armHalf, pxX * 2, pxY * armHalf * 2, 1,1,1,0.9f);
        rect(-pxX * armHalf, -pxY * 1, pxX * armHalf * 2, pxY * 2, 1,1,1,0.9f);

        // hotbar 9 slots along bottom
        int slots = 9;
        float slotPx = 50 * gui;
        float gapPx = 4 * gui;
        float totalPx = slots * slotPx + (slots - 1) * gapPx;
        float startX = -totalPx / w;
        float y = -1f + 20 * pxY;
        float slotW = (slotPx * 2) / w;
        float slotH = (slotPx * 2) / h;
        for (int i = 0; i < slots; i++) {
            float x = startX + i * ((slotPx + gapPx) * 2 / w);
            rect(x, y, slotW, slotH, 0.1f, 0.1f, 0.1f, 0.6f);
            // inner color = block tint
            float[] col = colorFor(inv.hotbar[i]);
            rect(x + slotW*0.15f, y + slotH*0.15f, slotW*0.7f, slotH*0.7f, col[0], col[1], col[2], 1f);
            if (i == inv.selected) {
                // highlight border
                rect(x, y, slotW, pxY * 3, 1,1,1,1);
                rect(x, y + slotH - pxY*3, slotW, pxY*3, 1,1,1,1);
                rect(x, y, pxX*3, slotH, 1,1,1,1);
                rect(x + slotW - pxX*3, y, pxX*3, slotH, 1,1,1,1);
            }
        }
        end2D();
    }

    public void renderWeatherOverlay(int w, int h, Environment env, float time) {
        if (env.status().isEmpty()) return;
        begin2D(w, h);
        drawText(env.status(), 18f, 18f * Math.max(1f, settings.guiScale), 2.2f * settings.guiScale,
                 1f, 1f, 1f, 0.9f);
        end2D();
    }

    public void renderCommandConsole(int w, int h, String text) {
        begin2D(w, h);
        float gui = Math.max(0.1f, settings.guiScale);
        float barH = 30f * gui;
        rectPx(0, h - barH, w, barH, 0.02f, 0.02f, 0.025f, 0.78f);
        drawText(text, 10f * gui, h - barH + 8f * gui, 2.0f * gui, 1f, 1f, 1f, 0.95f);
        end2D();
    }

    /** Enable 2D drawing state (alpha blend, no depth test) and bind the HUD shader. */
    public void begin2D(int w, int h) {
        this.viewW = Math.max(1, w);
        this.viewH = Math.max(1, h);
        glDisable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        shader.use();
    }

    /** Restore depth-test rendering state. */
    public void end2D() {
        glDisable(GL_BLEND);
        glEnable(GL_DEPTH_TEST);
    }

    public int viewportWidth()  { return viewW; }
    public int viewportHeight() { return viewH; }

    /** Draws a rect in NDC coords. */
    public void rect(float x, float y, float w, float h, float r, float g, float b, float a) {
        shader.setVec4("uRect", x, y, w, h);
        shader.setVec4("uColor", r, g, b, a);
        quad.draw();
    }

    /** Draws a rect specified in pixel coords (origin top-left of the window). */
    public void rectPx(float px, float py, float pw, float ph,
                       float r, float g, float b, float a) {
        float ndcW = 2f / viewW;
        float ndcH = 2f / viewH;
        float x = -1f + px * ndcW;
        float y =  1f - (py + ph) * ndcH;
        rect(x, y, pw * ndcW, ph * ndcH, r, g, b, a);
    }

    /**
     * Draws a string with the procedural 5x7 font. (px,py) is the top-left pixel of the
     * first glyph; {@code scale} multiplies pixel size (use 2-3 for readable menu text).
     */
    public void drawText(String s, float px, float py, float scale,
                         float r, float g, float b, float a) {
        float gx = px;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            final float ox = gx, oy = py, sc = scale;
            BitmapFont.pixelsOf(c, (cx, cy) ->
                rectPx(ox + cx * sc, oy + cy * sc, sc, sc, r, g, b, a));
            gx += BitmapFont.ADVANCE * scale;
        }
    }

    private float[] colorFor(com.csa.minecraft.world.Block b) {
        switch (b) {
            case GRASS:  return new float[]{0.30f, 0.69f, 0.31f};
            case DIRT:   return new float[]{0.55f, 0.35f, 0.17f};
            case STONE:  return new float[]{0.53f, 0.53f, 0.53f};
            case WOOD:   return new float[]{0.42f, 0.26f, 0.15f};
            case LEAVES: return new float[]{0.18f, 0.49f, 0.20f};
            case SAND:   return new float[]{0.91f, 0.82f, 0.54f};
            case PLANKS: return new float[]{0.72f, 0.54f, 0.29f};
            case GLASS:  return new float[]{0.81f, 0.92f, 1.00f};
            case CHERRY_WOOD:   return new float[]{0.55f, 0.33f, 0.33f};
            case CHERRY_LEAVES: return new float[]{0.95f, 0.48f, 0.70f};
            default:     return new float[]{1,1,1};
        }
    }
}
