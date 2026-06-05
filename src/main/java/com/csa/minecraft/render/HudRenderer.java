package com.csa.minecraft.render;

import com.csa.minecraft.Environment;
import com.csa.minecraft.GameMode;
import com.csa.minecraft.Settings;
import com.csa.minecraft.engine.Mesh;
import com.csa.minecraft.engine.Shader;
import com.csa.minecraft.engine.Texture;
import com.csa.minecraft.player.Inventory;
import com.csa.minecraft.world.Block;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;

public class HudRenderer {
    private static final int ATLAS_SIZE = 8; // 8x8 tile grid

    // Flat-colour shader (crosshair, slot backgrounds, health bar, borders)
    private final Shader shader;
    // Textured shader (block icons in hotbar slots)
    private final Shader texShader;
    private final Mesh   quad;
    private final Settings settings;
    private Texture atlas = null;
    private int viewW = 1, viewH = 1;

    private static final String VERT = """
        #version 330 core
        layout(location=0) in vec2 aPos;
        uniform vec4 uRect;
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

    // Textured variant: samples one tile from the block atlas
    private static final String TEX_VERT = """
        #version 330 core
        layout(location=0) in vec2 aPos;
        uniform vec4 uRect;
        uniform vec4 uUV;
        out vec2 vUV;
        void main(){
            vec2 p = uRect.xy + aPos * uRect.zw;
            vUV = uUV.xy + aPos * (uUV.zw - uUV.xy);
            gl_Position = vec4(p, 0.0, 1.0);
        }
        """;
    private static final String TEX_FRAG = """
        #version 330 core
        in vec2 vUV;
        out vec4 f;
        uniform sampler2D uTex;
        uniform float uAlpha;
        void main(){
            vec4 c = texture(uTex, vUV);
            f = vec4(c.rgb, c.a * uAlpha);
        }
        """;

    public HudRenderer() { this(new Settings()); }
    public HudRenderer(Settings settings) {
        this.settings = settings;
        shader    = new Shader(VERT, FRAG);
        texShader = new Shader(TEX_VERT, TEX_FRAG);
        quad = new Mesh();
        float[] verts = { 0,0,  1,0,  1,1,   0,0,  1,1,  0,1 };
        quad.upload(verts, new int[]{2});
    }

    /** Supply the block atlas so hotbar slots can show real block textures. */
    public void setAtlas(Texture t) { this.atlas = t; }

    public void render(int w, int h, Inventory inv) {
        render(w, h, inv, 20f, 20f);
    }

    public void render(int w, int h, Inventory inv, float health, float maxHealth) {
        begin2D(w, h);
        float gui = Math.max(0.1f, settings.guiScale);

        // Crosshair
        float pxX = 2f / w, pxY = 2f / h;
        float armHalf = 10f * gui;
        rect(-pxX, -pxY * armHalf, pxX * 2, pxY * armHalf * 2, 1,1,1,0.9f);
        rect(-pxX * armHalf, -pxY, pxX * armHalf * 2, pxY * 2, 1,1,1,0.9f);

        drawHealthBar(w, h, gui, health, maxHealth);

        // Hotbar
        boolean creative = settings.gameMode == GameMode.CREATIVE;
        int slots = 9;
        float slotPx  = 50 * gui;
        float gapPx   = 4  * gui;
        float totalPx = slots * slotPx + (slots - 1) * gapPx;
        float startX  = -totalPx / w;
        float yNdc    = -1f + 20 * pxY;
        float slotW   = (slotPx * 2) / w;
        float slotH   = (slotPx * 2) / h;
        // Pixel-space geometry for count text and texture drawing
        float slotLeftBase = (w - totalPx) / 2f;
        float slotTopPy    = h - 20 - slotPx; // pixels from top of window
        float countScale   = Math.max(1f, gui * 1.5f);
        float pad          = slotPx * 0.12f;   // icon inset in pixels

        for (int i = 0; i < slots; i++) {
            float x = startX + i * ((slotPx + gapPx) * 2 / w);

            // Slot background
            rect(x, yNdc, slotW, slotH, 0.1f, 0.1f, 0.1f, 0.6f);

            // Resolve display block and count
            Block displayBlock;
            int count;
            if (creative) {
                displayBlock = Inventory.CREATIVE_HOTBAR[i];
                count = -1; // infinite
            } else {
                displayBlock = inv.hotbar[i];
                count = (displayBlock != null && displayBlock != Block.AIR) ? inv.counts[i] : 0;
            }

            // Block icon
            if (displayBlock != null && displayBlock != Block.AIR) {
                float alpha = (creative || count > 0) ? 1f : 0.3f;
                float slotLeftPx = slotLeftBase + i * (slotPx + gapPx);
                if (atlas != null) {
                    // Draw real block texture (top face = face 0)
                    drawBlockIcon(slotLeftPx + pad, slotTopPy + pad,
                                  slotPx - pad * 2, slotPx - pad * 2,
                                  displayBlock, alpha);
                } else {
                    // Fallback: flat colour
                    float[] col = colorFor(displayBlock);
                    rect(x + slotW * 0.15f, yNdc + slotH * 0.15f,
                         slotW * 0.7f, slotH * 0.7f,
                         col[0] * alpha, col[1] * alpha, col[2] * alpha, alpha);
                }
            }

            // Stack count (bottom-right corner, survival only)
            if (!creative && count > 0) {
                String countStr = String.valueOf(count);
                float slotLeft = slotLeftBase + i * (slotPx + gapPx);
                float charW    = BitmapFont.ADVANCE * countScale;
                float textX    = slotLeft + slotPx - countStr.length() * charW - 3 * gui;
                float textY    = slotTopPy + slotPx - 9 * countScale - 3 * gui;
                // Shadow
                drawText(countStr, textX + countScale, textY + countScale,
                         countScale, 0f, 0f, 0f, 0.85f);
                // Colour: red <= 5, yellow <= 16, white otherwise
                float[] tc = count <= 5  ? new float[]{1f, 0.35f, 0.35f}
                           : count <= 16 ? new float[]{1f, 0.85f, 0.20f}
                           : new float[]{1f, 1f, 1f};
                drawText(countStr, textX, textY, countScale, tc[0], tc[1], tc[2], 1f);
            }

            // Selection border
            if (i == inv.selected) {
                rect(x, yNdc, slotW, pxY * 3, 1,1,1,1);
                rect(x, yNdc + slotH - pxY*3, slotW, pxY*3, 1,1,1,1);
                rect(x, yNdc, pxX*3, slotH, 1,1,1,1);
                rect(x + slotW - pxX*3, yNdc, pxX*3, slotH, 1,1,1,1);
            }
        }
        end2D();
    }

    /** Public wrapper: draw a block icon at pixel coords with uniform size. */
    public void drawBlockIconPx(float px, float py, float size, Block block, float alpha) {
        if (atlas == null || block == null || block == Block.AIR) return;
        drawBlockIcon(px, py, size, size, block, alpha);
    }

    /**
     * Renders the top-face atlas tile of the given block into a pixel-space rect.
     * (px, py) = top-left corner in pixels from the top of the window.
     */
    private void drawBlockIcon(float px, float py, float pw, float ph,
                               Block block, float alpha) {
        int tile = block.atlasIndex(0); // 0 = top face
        float step = 1f / ATLAS_SIZE;
        float u0 = (tile % ATLAS_SIZE) * step;
        float v0 = (tile / ATLAS_SIZE) * step;
        float u1 = u0 + step;
        float v1 = v0 + step;

        // Convert pixel rect to NDC
        float ndcW  = 2f / viewW;
        float ndcH  = 2f / viewH;
        float ndcX  = -1f + px * ndcW;
        float ndcY  =  1f - (py + ph) * ndcH;
        float ndcPW = pw * ndcW;
        float ndcPH = ph * ndcH;

        atlas.bind(4);
        texShader.use();
        texShader.setInt("uTex", 4);
        texShader.setVec4("uRect", ndcX, ndcY, ndcPW, ndcPH);
        texShader.setVec4("uUV",   u0, v0, u1, v1);
        texShader.setFloat("uAlpha", alpha);
        quad.draw();
        shader.use(); // restore colour shader for subsequent calls
    }

    private void drawHealthBar(int w, int h, float gui, float health, float maxHealth) {
        float barW = 182f * gui;
        float barH = 12f * gui;
        float x    = (w - barW) * 0.5f;
        float y    = h - 92f * gui;
        float pct  = maxHealth <= 0f ? 0f : Math.max(0f, Math.min(1f, health / maxHealth));
        rectPx(x - 2f*gui, y - 2f*gui, barW + 4f*gui, barH + 4f*gui, 0.02f, 0.02f, 0.025f, 0.78f);
        rectPx(x, y, barW,        barH, 0.18f, 0.03f, 0.03f, 0.92f);
        rectPx(x, y, barW * pct,  barH, 0.82f, 0.06f, 0.08f, 1f);
        rectPx(x, y, barW, Math.max(1f, 2f*gui), 1f, 1f, 1f, 0.35f);
        rectPx(x, y + barH - Math.max(1f, 2f*gui), barW, Math.max(1f, 2f*gui), 0f, 0f, 0f, 0.35f);
    }

    public void renderWeatherOverlay(int w, int h, Environment env, float time) {
        if (env.status().isEmpty()) return;
        begin2D(w, h);
        drawText(env.status(), 18f, 18f * Math.max(1f, settings.guiScale),
                 2.2f * settings.guiScale, 1f, 1f, 1f, 0.9f);
        end2D();
    }

    /** Renders a prominent boss health bar at the top centre of the screen. */
    public void renderBossBar(int w, int h, float health, float maxHealth, String name) {
        begin2D(w, h);
        float gui = Math.max(0.1f, settings.guiScale);
        float barW = 320f * gui;
        float barH = 14f * gui;
        float x = (w - barW) * 0.5f;
        float y = 22f * gui;
        float pct = maxHealth <= 0f ? 0f : Math.max(0f, Math.min(1f, health / maxHealth));
        rectPx(x - 2*gui, y - 2*gui, barW + 4*gui, barH + 4*gui, 0.04f, 0.02f, 0.06f, 0.92f);
        rectPx(x, y, barW, barH, 0.15f, 0.02f, 0.02f, 0.90f);
        rectPx(x, y, barW * pct, barH, 0.62f, 0.05f, 0.85f, 1f);
        float tsc = 1.9f * gui;
        float tw  = name.length() * BitmapFont.ADVANCE * tsc;
        drawText(name, (w - tw) * 0.5f + tsc, y + barH + 5f*gui + tsc, tsc, 0f,  0f,  0f,  0.8f);
        drawText(name, (w - tw) * 0.5f,       y + barH + 5f*gui,       tsc, 0.9f, 0.6f, 1.0f, 1f);
        end2D();
    }

    /** Renders a portal charge indicator (fills a small purple bar below crosshair). */
    public void renderPortalBar(int w, int h, float fraction) {
        begin2D(w, h);
        float gui = Math.max(0.1f, settings.guiScale);
        float barW = 80f * gui;
        float barH = 6f  * gui;
        float x = (w - barW) * 0.5f;
        float y = h * 0.5f + 18f * gui;
        rectPx(x, y, barW, barH, 0.10f, 0.02f, 0.20f, 0.75f);
        rectPx(x, y, barW * fraction, barH, 0.55f, 0.05f, 0.95f, 1f);
        end2D();
    }

    public void renderCommandConsole(int w, int h, String text) {
        begin2D(w, h);
        float gui   = Math.max(0.1f, settings.guiScale);
        float barH  = 30f * gui;
        rectPx(0, h - barH, w, barH, 0.02f, 0.02f, 0.025f, 0.78f);
        drawText(text, 10f*gui, h - barH + 8f*gui, 2.0f*gui, 1f, 1f, 1f, 0.95f);
        end2D();
    }

    public void begin2D(int w, int h) {
        this.viewW = Math.max(1, w);
        this.viewH = Math.max(1, h);
        glDisable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        shader.use();
    }

    public void end2D() {
        glDisable(GL_BLEND);
        glEnable(GL_DEPTH_TEST);
    }

    public int viewportWidth()  { return viewW; }
    public int viewportHeight() { return viewH; }

    public void rect(float x, float y, float w, float h,
                     float r, float g, float b, float a) {
        shader.setVec4("uRect",  x, y, w, h);
        shader.setVec4("uColor", r, g, b, a);
        quad.draw();
    }

    public void rectPx(float px, float py, float pw, float ph,
                       float r, float g, float b, float a) {
        float ndcW = 2f / viewW;
        float ndcH = 2f / viewH;
        float x = -1f + px * ndcW;
        float y =  1f - (py + ph) * ndcH;
        rect(x, y, pw * ndcW, ph * ndcH, r, g, b, a);
    }

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

    private float[] colorFor(Block b) {
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
            case WATER:         return new float[]{0.22f, 0.45f, 0.90f};
            case SNOW:          return new float[]{0.95f, 0.97f, 0.99f};
            case ICE:           return new float[]{0.65f, 0.81f, 0.93f};
            case CACTUS:        return new float[]{0.31f, 0.49f, 0.21f};
            case SANDSTONE:     return new float[]{0.89f, 0.82f, 0.60f};
            case SPRUCE_WOOD:   return new float[]{0.23f, 0.16f, 0.10f};
            case SPRUCE_LEAVES: return new float[]{0.12f, 0.29f, 0.16f};
            case DRY_GRASS:     return new float[]{0.60f, 0.67f, 0.30f};
            case ROCKET:        return new float[]{0.85f, 0.87f, 0.93f};
            case OBSIDIAN:      return new float[]{0.08f, 0.04f, 0.15f};
            case NETHERRACK:    return new float[]{0.42f, 0.13f, 0.13f};
            case NETHER_PORTAL: return new float[]{0.35f, 0.05f, 0.80f};
            case STICK:    return new float[]{0.55f, 0.35f, 0.17f};
            case SWORD:    return new float[]{0.69f, 0.74f, 0.80f};
            case PICKAXE:  return new float[]{0.60f, 0.60f, 0.60f};
            case AXE:      return new float[]{0.60f, 0.60f, 0.60f};
            case SHOVEL:   return new float[]{0.62f, 0.62f, 0.62f};
            default:            return new float[]{1, 1, 1};
        }
    }
}
