package com.csa.minecraft.render;

import java.util.HashMap;
import java.util.Map;

/**
 * Procedural 5x7 bitmap font. Each glyph is stored as a 35-bit mask, row-major,
 * MSB = top-left. Use {@link #pixelsOf} to iterate set pixels for rendering.
 *
 * Covers A-Z, 0-9, space, ".", "-", ":", "/", "+", "%". Unknown chars render as
 * a blank space (no glyph drawn).
 */
public class BitmapFont {
    public static final int W = 5, H = 7;
    /** Recommended horizontal advance between glyphs (one column of padding). */
    public static final int ADVANCE = W + 1;
    /** Recommended vertical advance between lines. */
    public static final int LINE = H + 2;

    private static final Map<Character, int[]> GLYPHS = new HashMap<>();

    private static void g(char c, String spec) {
        // spec is 7 lines of 5 chars each, separated by \n. '.' = off, anything else = on.
        String[] rows = spec.split("\n");
        if (rows.length != H) throw new IllegalArgumentException("bad glyph for " + c);
        int[] r = new int[H];
        for (int y = 0; y < H; y++) {
            if (rows[y].length() != W) throw new IllegalArgumentException("bad row for " + c);
            int bits = 0;
            for (int x = 0; x < W; x++) if (rows[y].charAt(x) != '.') bits |= (1 << (W - 1 - x));
            r[y] = bits;
        }
        GLYPHS.put(c, r);
    }

    static {
        g('A', ".XXX.\nX...X\nX...X\nXXXXX\nX...X\nX...X\nX...X");
        g('B', "XXXX.\nX...X\nX...X\nXXXX.\nX...X\nX...X\nXXXX.");
        g('C', ".XXXX\nX....\nX....\nX....\nX....\nX....\n.XXXX");
        g('D', "XXXX.\nX...X\nX...X\nX...X\nX...X\nX...X\nXXXX.");
        g('E', "XXXXX\nX....\nX....\nXXXX.\nX....\nX....\nXXXXX");
        g('F', "XXXXX\nX....\nX....\nXXXX.\nX....\nX....\nX....");
        g('G', ".XXXX\nX....\nX....\nX.XXX\nX...X\nX...X\n.XXX.");
        g('H', "X...X\nX...X\nX...X\nXXXXX\nX...X\nX...X\nX...X");
        g('I', "XXXXX\n..X..\n..X..\n..X..\n..X..\n..X..\nXXXXX");
        g('J', "..XXX\n...X.\n...X.\n...X.\n...X.\nX..X.\n.XX..");
        g('K', "X...X\nX..X.\nX.X..\nXX...\nX.X..\nX..X.\nX...X");
        g('L', "X....\nX....\nX....\nX....\nX....\nX....\nXXXXX");
        g('M', "X...X\nXX.XX\nX.X.X\nX...X\nX...X\nX...X\nX...X");
        g('N', "X...X\nXX..X\nX.X.X\nX.X.X\nX..XX\nX...X\nX...X");
        g('O', ".XXX.\nX...X\nX...X\nX...X\nX...X\nX...X\n.XXX.");
        g('P', "XXXX.\nX...X\nX...X\nXXXX.\nX....\nX....\nX....");
        g('Q', ".XXX.\nX...X\nX...X\nX...X\nX.X.X\nX..X.\n.XX.X");
        g('R', "XXXX.\nX...X\nX...X\nXXXX.\nX.X..\nX..X.\nX...X");
        g('S', ".XXXX\nX....\nX....\n.XXX.\n....X\n....X\nXXXX.");
        g('T', "XXXXX\n..X..\n..X..\n..X..\n..X..\n..X..\n..X..");
        g('U', "X...X\nX...X\nX...X\nX...X\nX...X\nX...X\n.XXX.");
        g('V', "X...X\nX...X\nX...X\nX...X\nX...X\n.X.X.\n..X..");
        g('W', "X...X\nX...X\nX...X\nX...X\nX.X.X\nXX.XX\nX...X");
        g('X', "X...X\nX...X\n.X.X.\n..X..\n.X.X.\nX...X\nX...X");
        g('Y', "X...X\nX...X\n.X.X.\n..X..\n..X..\n..X..\n..X..");
        g('Z', "XXXXX\n....X\n...X.\n..X..\n.X...\nX....\nXXXXX");
        g('0', ".XXX.\nX...X\nX..XX\nX.X.X\nXX..X\nX...X\n.XXX.");
        g('1', "..X..\n.XX..\n..X..\n..X..\n..X..\n..X..\n.XXX.");
        g('2', ".XXX.\nX...X\n....X\n...X.\n..X..\n.X...\nXXXXX");
        g('3', ".XXX.\nX...X\n....X\n..XX.\n....X\nX...X\n.XXX.");
        g('4', "...X.\n..XX.\n.X.X.\nX..X.\nXXXXX\n...X.\n...X.");
        g('5', "XXXXX\nX....\nXXXX.\n....X\n....X\nX...X\n.XXX.");
        g('6', ".XXX.\nX....\nX....\nXXXX.\nX...X\nX...X\n.XXX.");
        g('7', "XXXXX\n....X\n...X.\n..X..\n.X...\n.X...\n.X...");
        g('8', ".XXX.\nX...X\nX...X\n.XXX.\nX...X\nX...X\n.XXX.");
        g('9', ".XXX.\nX...X\nX...X\n.XXXX\n....X\n....X\n.XXX.");
        g(' ', ".....\n.....\n.....\n.....\n.....\n.....\n.....");
        g('.', ".....\n.....\n.....\n.....\n.....\n..X..\n..X..");
        g('-', ".....\n.....\n.....\nXXXXX\n.....\n.....\n.....");
        g(':', ".....\n..X..\n..X..\n.....\n..X..\n..X..\n.....");
        g('/', "....X\n....X\n...X.\n..X..\n.X...\nX....\nX....");
        g('+', ".....\n..X..\n..X..\nXXXXX\n..X..\n..X..\n.....");
        g('%', "XX..X\nXX.X.\n..X..\n.X...\nX.XX.\n..XXX\n.....");
    }

    public interface PixelConsumer { void px(int x, int y); }

    /** Iterate set pixels of {@code c}. Pixel coords are (col, row) within a 5x7 cell. */
    public static void pixelsOf(char c, PixelConsumer out) {
        int[] rows = GLYPHS.get(Character.toUpperCase(c));
        if (rows == null) return;
        for (int y = 0; y < H; y++) {
            int r = rows[y];
            for (int x = 0; x < W; x++) {
                if ((r & (1 << (W - 1 - x))) != 0) out.px(x, y);
            }
        }
    }

    /** Pixel width of a string at the recommended advance (no trailing padding column). */
    public static int textWidth(String s) {
        return s.isEmpty() ? 0 : s.length() * ADVANCE - 1;
    }
}
