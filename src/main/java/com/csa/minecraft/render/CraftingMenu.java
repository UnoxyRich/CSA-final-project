package com.csa.minecraft.render;

import com.csa.minecraft.Settings;
import com.csa.minecraft.player.Inventory;
import com.csa.minecraft.world.Block;

import java.util.*;

public class CraftingMenu {

    // ── Recipes ──────────────────────────────────────────────────────────────
    // shape != null: shaped recipe, grid positions must match exactly (null slot = must be empty)
    // shape == null: shapeless recipe, only ingredient counts matter
    private record Recipe(Block[] shape, Map<Block, Integer> need, Block out, int count) {

        boolean matchesShaped(Block[] g, int[] gc) {
            if (shape == null) return false;
            for (int i = 0; i < 9; i++) {
                Block req = shape[i];
                Block got = g[i];
                boolean empty = got == null || got == Block.AIR || gc[i] <= 0;
                if (req == null || req == Block.AIR) {
                    if (!empty) return false;
                } else {
                    if (empty || got != req) return false;
                }
            }
            return true;
        }

        boolean matchesShapeless(Map<Block, Integer> found) {
            if (need == null) return false;
            if (!found.keySet().equals(need.keySet())) return false;
            for (Map.Entry<Block, Integer> e : need.entrySet())
                if (found.getOrDefault(e.getKey(), 0) < e.getValue()) return false;
            return true;
        }

        int totalNeeded() {
            if (need != null) { int t = 0; for (int v : need.values()) t += v; return t; }
            int t = 0; for (Block b : shape) if (b != null && b != Block.AIR) t++; return t;
        }
    }

    // P=PLANKS  S=STONE  K=STICK  grid index: [0][1][2] / [3][4][5] / [6][7][8]
    private static final List<Recipe> RECIPES = List.of(
        // Shapeless basic recipes (any arrangement)
        new Recipe(null, Map.of(Block.WOOD, 1),        Block.PLANKS,    4),
        new Recipe(null, Map.of(Block.CHERRY_WOOD, 1), Block.PLANKS,    4),
        new Recipe(null, Map.of(Block.SPRUCE_WOOD, 1), Block.PLANKS,    4),
        new Recipe(null, Map.of(Block.SAND, 1),        Block.GLASS,     1),
        new Recipe(null, Map.of(Block.SAND, 4),        Block.SANDSTONE, 2),
        new Recipe(null, Map.of(Block.PLANKS, 4),      Block.WOOD,      1),
        new Recipe(null, Map.of(Block.SNOW, 4),        Block.ICE,       1),
        new Recipe(null, Map.of(Block.DIRT, 9),        Block.STONE,     1),
        // Shaped tool recipes (exact grid positions required)
        // STICK:  _ P _  /  _ P _  /  _ _ _
        new Recipe(new Block[]{
            null, Block.PLANKS, null,
            null, Block.PLANKS, null,
            null, null,         null},
            null, Block.STICK, 4),
        // SWORD:  _ P _  /  _ P _  /  _ K _
        new Recipe(new Block[]{
            null, Block.PLANKS, null,
            null, Block.PLANKS, null,
            null, Block.STICK,  null},
            null, Block.SWORD, 1),
        // PICKAXE:  S S S  /  _ K _  /  _ K _
        new Recipe(new Block[]{
            Block.STONE, Block.STONE, Block.STONE,
            null,        Block.STICK, null,
            null,        Block.STICK, null},
            null, Block.PICKAXE, 1),
        // AXE:  S S _  /  S K _  /  _ K _
        new Recipe(new Block[]{
            Block.STONE, Block.STONE, null,
            Block.STONE, Block.STICK, null,
            null,        Block.STICK, null},
            null, Block.AXE, 1),
        // SHOVEL:  _ P _  /  _ K _  /  _ K _
        new Recipe(new Block[]{
            null, Block.PLANKS, null,
            null, Block.STICK,  null,
            null, Block.STICK,  null},
            null, Block.SHOVEL, 1)
    );

    // ── Layout (logical px at gui = 1) ───────────────────────────────────────
    private static final float PW          = 424f;  // panel width
    private static final float PH          = 285f;  // panel height
    private static final float SLOT        = 48f;   // crafting slot size
    private static final float SGAP        = 4f;    // crafting slot gap
    private static final float SSTRIDE     = SLOT + SGAP;
    private static final float GX          = 24f;   // grid left (panel-relative)
    private static final float GY          = 52f;   // grid top
    private static final float ARROW_X     = GX + 3 * SSTRIDE + 10f;
    private static final float OUT_X       = ARROW_X + 42f;
    private static final float OUT_Y       = GY + SSTRIDE;  // vertically centred on grid
    private static final float INV_SLOT    = 40f;
    private static final float INV_GAP     = 2f;
    private static final float INV_STRIDE  = INV_SLOT + INV_GAP;
    private static final float INV_TOTAL_W = 9 * INV_SLOT + 8 * INV_GAP;
    private static final float INV_LX      = (PW - INV_TOTAL_W) / 2f;
    private static final float INV_TY      = GY + 3 * SSTRIDE + 22f;

    // ── State ────────────────────────────────────────────────────────────────
    private boolean open;
    private final Settings settings;
    private final Block[] grid       = new Block[9];
    private final int[]   gridCounts = new int[9];
    private Block held      = Block.AIR;
    private int   heldCount = 0;

    public CraftingMenu(Settings settings) {
        this.settings = settings;
        Arrays.fill(grid, Block.AIR);
    }

    public boolean isOpen() { return open; }
    public void open()      { open = true; }

    /** Close and return all grid / held items to the player inventory. */
    public void close(Inventory inv) {
        for (int i = 0; i < 9; i++) {
            if (grid[i] != null && grid[i] != Block.AIR && gridCounts[i] > 0) {
                for (int j = 0; j < gridCounts[i]; j++) inv.addBlock(grid[i]);
                grid[i] = Block.AIR;
                gridCounts[i] = 0;
            }
        }
        for (int i = 0; i < heldCount; i++) inv.addBlock(held);
        held = Block.AIR;
        heldCount = 0;
        open = false;
    }

    // ── Input ────────────────────────────────────────────────────────────────

    public void update(int w, int h, double cx, double cy,
                       boolean clicked, boolean rightClicked, Inventory inv) {
        if (!clicked && !rightClicked) return;
        boolean rc = rightClicked && !clicked; // true = right-click only
        float gui = Math.max(0.1f, settings.guiScale);
        float px  = (w - PW * gui) / 2f;
        float py  = (h - PH * gui) / 2f;

        // 3x3 crafting grid
        for (int i = 0; i < 9; i++) {
            float sx = px + (GX + (i % 3) * SSTRIDE) * gui;
            float sy = py + (GY + (i / 3) * SSTRIDE) * gui;
            if (hit(cx, cy, sx, sy, SLOT * gui)) { clickGrid(i, rc); return; }
        }

        // Output slot (right-click behaves same as left-click for output)
        Recipe match = findRecipe();
        if (match != null) {
            float ox = px + OUT_X * gui;
            float oy = py + OUT_Y * gui;
            if (hit(cx, cy, ox, oy, SLOT * gui)) { takeOutput(match, inv); return; }
        }

        // Inventory row
        for (int i = 0; i < 9; i++) {
            float sx = px + (INV_LX + i * INV_STRIDE) * gui;
            float sy = py + INV_TY * gui;
            if (hit(cx, cy, sx, sy, INV_SLOT * gui)) { clickInv(i, inv, rc); return; }
        }
    }

    private void clickGrid(int idx, boolean rc) {
        Block sb = grid[idx];
        int   sc = (sb != null && sb != Block.AIR) ? gridCounts[idx] : 0;
        if (held == Block.AIR) {
            if (sc > 0) {
                if (rc) {
                    // Right-click empty hand: pick up half (rounded up)
                    int take = (sc + 1) / 2;
                    held = sb; heldCount = take;
                    gridCounts[idx] -= take;
                    if (gridCounts[idx] == 0) grid[idx] = Block.AIR;
                } else {
                    held = sb; heldCount = sc; grid[idx] = Block.AIR; gridCounts[idx] = 0;
                }
            }
        } else if (rc) {
            // Right-click while holding: place exactly one item
            if (sb == null || sb == Block.AIR || sc == 0) {
                grid[idx] = held; gridCounts[idx] = 1; heldCount--;
                if (heldCount == 0) held = Block.AIR;
            } else if (sb == held && sc < Inventory.MAX_STACK) {
                gridCounts[idx]++; heldCount--;
                if (heldCount == 0) held = Block.AIR;
            }
            // Different type: do nothing on right-click
        } else if (sb == null || sb == Block.AIR || sc == 0) {
            grid[idx] = held; gridCounts[idx] = heldCount; held = Block.AIR; heldCount = 0;
        } else if (sb == held) {
            int add = Math.min(Inventory.MAX_STACK - sc, heldCount);
            gridCounts[idx] += add; heldCount -= add;
            if (heldCount == 0) held = Block.AIR;
        } else {
            Block tb = grid[idx]; int tc = gridCounts[idx];
            grid[idx] = held; gridCounts[idx] = heldCount;
            held = tb; heldCount = tc;
        }
    }

    private void clickInv(int idx, Inventory inv, boolean rc) {
        Block sb = inv.hotbar[idx];
        int   sc = (sb != null && sb != Block.AIR) ? inv.counts[idx] : 0;
        if (held == Block.AIR) {
            if (sc > 0) {
                if (rc) {
                    // Right-click empty hand: pick up half (rounded up)
                    int take = (sc + 1) / 2;
                    held = sb; heldCount = take;
                    inv.counts[idx] -= take;
                    if (inv.counts[idx] == 0) inv.hotbar[idx] = Block.AIR;
                } else {
                    held = sb; heldCount = sc; inv.hotbar[idx] = Block.AIR; inv.counts[idx] = 0;
                }
            }
        } else if (rc) {
            // Right-click while holding: place exactly one item
            if (sb == null || sb == Block.AIR || sc == 0) {
                inv.hotbar[idx] = held; inv.counts[idx] = 1; heldCount--;
                if (heldCount == 0) held = Block.AIR;
            } else if (sb == held && sc < Inventory.MAX_STACK) {
                inv.counts[idx]++; heldCount--;
                if (heldCount == 0) held = Block.AIR;
            }
            // Different type: do nothing on right-click
        } else if (sb == null || sb == Block.AIR || sc == 0) {
            inv.hotbar[idx] = held; inv.counts[idx] = heldCount; held = Block.AIR; heldCount = 0;
        } else if (sb == held) {
            int add = Math.min(Inventory.MAX_STACK - sc, heldCount);
            inv.counts[idx] += add; heldCount -= add;
            if (heldCount == 0) held = Block.AIR;
        } else {
            Block tb = inv.hotbar[idx]; int tc = inv.counts[idx];
            inv.hotbar[idx] = held; inv.counts[idx] = heldCount;
            held = tb; heldCount = tc;
        }
    }

    private void takeOutput(Recipe recipe, Inventory inv) {
        if (held != Block.AIR && held != recipe.out()) return;
        if (held != Block.AIR && heldCount + recipe.count() > Inventory.MAX_STACK) return;
        if (recipe.shape() != null) {
            // Shaped: consume exactly one item from each required slot
            for (int i = 0; i < 9; i++) {
                Block req = recipe.shape()[i];
                if (req != null && req != Block.AIR) {
                    gridCounts[i]--;
                    if (gridCounts[i] == 0) grid[i] = Block.AIR;
                }
            }
        } else {
            // Shapeless: consume by aggregate ingredient count
            Map<Block, Integer> rem = new HashMap<>(recipe.need());
            for (int i = 0; i < 9; i++) {
                if (grid[i] == null || grid[i] == Block.AIR) continue;
                int need = rem.getOrDefault(grid[i], 0);
                if (need > 0) {
                    int take = Math.min(need, gridCounts[i]);
                    gridCounts[i] -= take;
                    rem.put(grid[i], need - take);
                    if (gridCounts[i] == 0) grid[i] = Block.AIR;
                }
            }
        }
        if (held == Block.AIR) { held = recipe.out(); heldCount = recipe.count(); }
        else                    { heldCount += recipe.count(); }
    }

    // Count total items per block type currently in the 3x3 grid.
    private Map<Block, Integer> countGrid() {
        Map<Block, Integer> found = new HashMap<>();
        for (int i = 0; i < 9; i++) {
            if (grid[i] != null && grid[i] != Block.AIR && gridCounts[i] > 0)
                found.merge(grid[i], gridCounts[i], Integer::sum);
        }
        return found;
    }

    // Shaped recipes are checked first (exact position match).
    // Shapeless recipes fall back to count-based matching, highest total wins.
    private Recipe findRecipe() {
        for (Recipe r : RECIPES) {
            if (r.shape() != null && r.matchesShaped(grid, gridCounts)) return r;
        }
        Map<Block, Integer> found = countGrid();
        if (found.isEmpty()) return null;
        Recipe best = null;
        int bestTotal = -1;
        for (Recipe r : RECIPES) {
            if (r.shape() == null && r.matchesShapeless(found) && r.totalNeeded() > bestTotal) {
                best = r; bestTotal = r.totalNeeded();
            }
        }
        return best;
    }

    private static boolean hit(double cx, double cy, float rx, float ry, float size) {
        return cx >= rx && cx < rx + size && cy >= ry && cy < ry + size;
    }

    // ── Rendering ────────────────────────────────────────────────────────────

    public void render(int w, int h, HudRenderer hud, Inventory inv, double curX, double curY) {
        float gui = Math.max(0.1f, settings.guiScale);
        float px  = (w - PW * gui) / 2f;
        float py  = (h - PH * gui) / 2f;

        hud.begin2D(w, h);

        // Panel
        hud.rectPx(px, py, PW * gui, PH * gui, 0.10f, 0.10f, 0.12f, 0.94f);
        float b = 2f * gui;
        hud.rectPx(px - b, py - b, PW * gui + 2*b, b,             0.45f, 0.45f, 0.55f, 1f);
        hud.rectPx(px - b, py + PH * gui, PW * gui + 2*b, b,      0.45f, 0.45f, 0.55f, 1f);
        hud.rectPx(px - b, py - b, b, PH * gui + 2*b,             0.45f, 0.45f, 0.55f, 1f);
        hud.rectPx(px + PW * gui, py - b, b, PH * gui + 2*b,      0.45f, 0.45f, 0.55f, 1f);

        // Title
        hud.drawText("CRAFTING", px + GX * gui, py + 14f * gui,
                     2.2f * gui, 1f, 1f, 1f, 0.9f);

        // 3x3 crafting grid
        for (int i = 0; i < 9; i++) {
            float sx = px + (GX + (i % 3) * SSTRIDE) * gui;
            float sy = py + (GY + (i / 3) * SSTRIDE) * gui;
            float s  = SLOT * gui;
            Block b2 = grid[i]; int c2 = (b2 != null && b2 != Block.AIR) ? gridCounts[i] : 0;
            drawSlot(hud, sx, sy, s, b2, c2, false, gui);
        }

        // Arrow
        float arrowX  = px + ARROW_X * gui;
        float arrowCY = py + (GY + SSTRIDE + SLOT / 2f) * gui;
        hud.drawText(">", arrowX,            arrowCY - 4f * gui, 2.4f * gui, 0.9f, 0.8f, 0.1f, 1f);
        hud.drawText(">", arrowX + 12f * gui, arrowCY - 4f * gui, 2.4f * gui, 1f,  1f,  0.3f, 1f);

        // Output slot
        Recipe match = findRecipe();
        float ox = px + OUT_X * gui;
        float oy = py + OUT_Y * gui;
        float os = SLOT * gui;
        Block outB  = (match != null) ? match.out()   : Block.AIR;
        int   outC  = (match != null) ? match.count() : 0;
        drawSlot(hud, ox, oy, os, outB, outC, match != null, gui);

        // Divider
        float divY = py + (INV_TY - 12f) * gui;
        hud.rectPx(px + 14f * gui, divY, (PW - 28f) * gui, gui, 0.35f, 0.35f, 0.40f, 0.8f);
        hud.drawText("INVENTORY", px + INV_LX * gui, divY - 12f * gui,
                     1.5f * gui, 0.65f, 0.65f, 0.70f, 0.8f);

        // Player inventory row
        for (int i = 0; i < 9; i++) {
            float sx = px + (INV_LX + i * INV_STRIDE) * gui;
            float sy = py + INV_TY * gui;
            float s  = INV_SLOT * gui;
            Block b2 = inv.hotbar[i];
            int   c2 = (b2 != null && b2 != Block.AIR) ? inv.counts[i] : 0;
            drawSlot(hud, sx, sy, s, b2, c2, i == inv.selected, gui);
        }

        // Hint text
        hud.drawText("E / ESC to close",
                     px + (PW / 2f - 58f) * gui, py + (PH - 14f) * gui,
                     1.3f * gui, 0.50f, 0.50f, 0.55f, 0.75f);

        // Recipes hint on the right
        renderRecipeHints(hud, px, py, gui);

        // Held item follows cursor
        if (held != null && held != Block.AIR && heldCount > 0) {
            float hs = SLOT * gui;
            hud.drawBlockIconPx((float)curX - hs / 2f, (float)curY - hs / 2f, hs, held, 1f);
            if (heldCount > 1) {
                String cs = String.valueOf(heldCount);
                float sc = Math.max(1f, gui * 1.5f);
                hud.drawText(cs, (float)curX + hs / 2f - cs.length() * BitmapFont.ADVANCE * sc - 2f,
                             (float)curY + hs / 2f - 9f * sc - 2f, sc, 1f, 1f, 1f, 1f);
            }
        }

        hud.end2D();
    }

    private void renderRecipeHints(HudRenderer hud, float px, float py, float gui) {
        float rx = px + (OUT_X + SLOT + 14f) * gui;
        float ry = py + GY * gui;
        hud.drawText("Recipes:", rx, ry, 1.5f * gui, 0.8f, 0.8f, 0.5f, 0.9f);
        String[] hints = {
            "1 Wood -> 4 Planks",
            "1 Sand -> 1 Glass",
            "4 Sand -> 2 Sandstone",
            "4 Planks -> 1 Wood",
            "4 Snow -> 1 Ice",
            "9 Dirt -> 1 Stone",
            "--- Tools (shaped) ---",
            "Stick:  _P_ / _P_ / ___",
            "Sword:  _P_ / _P_ / _K_",
            "Pick:  SSS / _K_ / _K_",
            "Axe:   SS_ / SK_ / _K_",
            "Shovel: _P_ / _K_ / _K_",
        };
        for (int i = 0; i < hints.length; i++) {
            hud.drawText(hints[i], rx, ry + (14f + i * 13f) * gui,
                         1.2f * gui, 0.65f, 0.65f, 0.65f, 0.8f);
        }
    }

    private void drawSlot(HudRenderer hud, float px, float py, float size,
                          Block block, int count, boolean highlight, float gui) {
        float bg = highlight ? 0.28f : 0.14f;
        hud.rectPx(px, py, size, size, bg, bg, bg + 0.06f, 0.92f);
        // Border
        float bd = Math.max(1f, gui);
        hud.rectPx(px,           py,            size, bd,   0.45f, 0.45f, 0.55f, 0.8f);
        hud.rectPx(px, py + size - bd,          size, bd,   0.20f, 0.20f, 0.25f, 0.8f);
        hud.rectPx(px,           py,            bd,   size, 0.45f, 0.45f, 0.55f, 0.8f);
        hud.rectPx(px + size - bd, py,          bd,   size, 0.20f, 0.20f, 0.25f, 0.8f);

        if (block != null && block != Block.AIR && count > 0) {
            float pad = size * 0.08f;
            hud.drawBlockIconPx(px + pad, py + pad, size - pad * 2f, block, 1f);
            if (count > 1) {
                String cs = String.valueOf(count);
                float sc = Math.max(1f, gui * 1.4f);
                float tx = px + size - cs.length() * BitmapFont.ADVANCE * sc - 2f;
                float ty = py + size - 8f * sc - 2f;
                hud.drawText(cs, tx + sc, ty + sc, sc, 0f, 0f, 0f, 0.85f);
                hud.drawText(cs, tx, ty, sc, 1f, 1f, 1f, 1f);
            }
        }
    }
}
