package com.csa.minecraft.engine;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL30.glGenerateMipmap;
import static org.lwjgl.stb.STBImage.*;
import static org.lwjgl.system.MemoryUtil.memAlloc;
import static org.lwjgl.system.MemoryUtil.memFree;

public class Texture {
    private final int id;
    public final int atlasSize; // tiles per row
    public final int tilePx;

    public Texture(int id, int atlasSize, int tilePx) {
        this.id = id; this.atlasSize = atlasSize; this.tilePx = tilePx;
    }

    public void bind(int unit) {
        glActiveTexture(GL_TEXTURE0 + unit);
        glBindTexture(GL_TEXTURE_2D, id);
    }

    public static Texture buildBlockAtlas() {
        Path pack = Path.of("Default_HD_128x_Demo_1.8.2.4.zip");
        if (Files.isRegularFile(pack)) {
            try {
                return buildFromResourcePack(pack);
            } catch (Exception e) {
                System.err.println("Failed to load resource pack textures, using procedural atlas: " + e.getMessage());
            }
        }
        return buildProcedural();
    }

    /** Build a procedural texture atlas: 4x4 tiles, each 16x16. */
    public static Texture buildProcedural() {
        int tile = 16, cols = 4;
        int size = tile * cols;
        ByteBuffer buf = org.lwjgl.BufferUtils.createByteBuffer(size * size * 4);
        int[][] palettes = new int[16][];
        // tile colors keyed by atlas index (see Block.atlasIndex)
        palettes[0]  = colors(0x8b5a2b, 0x9c6a3b); // dirt
        palettes[1]  = colors(0x4caf50, 0x60c060); // grass top
        palettes[2]  = colors(0x6b4423, 0x7c5230); // grass side (dirt-ish)
        palettes[3]  = colors(0x888888, 0x9a9a9a); // stone
        palettes[4]  = colors(0x6b4226, 0x7c5230); // wood side (bark)
        palettes[5]  = colors(0xc28850, 0xd09660); // wood top
        palettes[6]  = colors(0x2e7d32, 0x3aa040); // leaves
        palettes[7]  = colors(0xe8d28a, 0xf0dc9a); // sand
        palettes[8]  = colors(0xb88a4a, 0xc89a5a); // planks
        palettes[9]  = colors(0xcfeaff, 0xdff4ff); // glass
        palettes[10] = colors(0x5f3b38, 0x7a4a48); // cherry log side
        palettes[11] = colors(0xd8a6a6, 0xf0c2c2); // cherry log top
        palettes[12] = colors(0xf3a4c8, 0xffc1d9); // cherry leaves
        for (int i = 13; i < 16; i++) palettes[i] = colors(0xff00ff, 0x000000);

        Random rng = new Random(7);
        for (int ti = 0; ti < cols * cols; ti++) {
            int tx = ti % cols, ty = ti / cols;
            int[] pal = palettes[ti];
            for (int y = 0; y < tile; y++) {
                for (int x = 0; x < tile; x++) {
                    int c = pal[rng.nextInt(pal.length)];
                    // darken edges slightly for cell definition
                    if (x == 0 || y == 0) c = darken(c, 0.85f);
                    int px = tx * tile + x;
                    int py = ty * tile + y;
                    int idx = (py * size + px) * 4;
                    buf.put(idx,   (byte)((c >> 16) & 0xff));
                    buf.put(idx+1, (byte)((c >>  8) & 0xff));
                    buf.put(idx+2, (byte)( c        & 0xff));
                    buf.put(idx+3, (byte)0xff);
                }
            }
        }

        int id = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, id);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, size, size, 0, GL_RGBA, GL_UNSIGNED_BYTE, buf);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glGenerateMipmap(GL_TEXTURE_2D);
        return new Texture(id, cols, tile);
    }

    private static Texture buildFromResourcePack(Path pack) throws Exception {
        int tile = 128, cols = 4;
        int size = tile * cols;
        ByteBuffer atlas = org.lwjgl.BufferUtils.createByteBuffer(size * size * 4);

        try (ZipFile zip = new ZipFile(pack.toFile())) {
            int grassTint = sampleTint(loadExact(zip, "assets/minecraft/textures/colormap/grass.png"), 0x91bd59);
            int foliageTint = sampleTint(loadExact(zip, "assets/minecraft/textures/colormap/foliage.png"), 0x77ab2f);
            Image grassSide = composeOverlay(
                load(zip, "grass_block_side", "grass_side"),
                tint(load(zip, "grass_block_side_overlay", "grass_side_overlay"), grassTint)
            );
            putTile(atlas, size, tile, cols, 0, load(zip, "dirt"));
            putTile(atlas, size, tile, cols, 1, tint(load(zip, "grass_block_top", "grass_top"), grassTint));
            putTile(atlas, size, tile, cols, 2, grassSide);
            putTile(atlas, size, tile, cols, 3, load(zip, "stone"));
            putTile(atlas, size, tile, cols, 4, load(zip, "oak_log", "log_oak"));
            putTile(atlas, size, tile, cols, 5, load(zip, "oak_log_top", "log_oak_top"));
            putTile(atlas, size, tile, cols, 6, tint(load(zip, "oak_leaves", "leaves_oak"), foliageTint));
            putTile(atlas, size, tile, cols, 7, load(zip, "sand"));
            putTile(atlas, size, tile, cols, 8, load(zip, "oak_planks", "planks_oak"));
            putTile(atlas, size, tile, cols, 9, load(zip, "glass"));
            putTile(atlas, size, tile, cols, 10, load(zip, "cherry_log"));
            putTile(atlas, size, tile, cols, 11, load(zip, "cherry_log_top"));
            putTile(atlas, size, tile, cols, 12, load(zip, "cherry_leaves"));
        }

        int id = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, id);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, size, size, 0, GL_RGBA, GL_UNSIGNED_BYTE, atlas);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glGenerateMipmap(GL_TEXTURE_2D);
        return new Texture(id, cols, tile);
    }

    private static Image load(ZipFile zip, String... names) throws Exception {
        ZipEntry entry = null;
        for (String name : names) {
            entry = entry(zip, "assets/minecraft/textures/block/" + name + ".png");
            if (entry == null) entry = entry(zip, "assets/minecraft/textures/blocks/" + name + ".png");
            if (entry != null) break;
        }
        if (entry == null) throw new IllegalArgumentException("missing texture " + String.join("/", names));
        return load(zip, entry);
    }

    private static Image loadExact(ZipFile zip, String path) throws Exception {
        ZipEntry entry = entry(zip, path);
        if (entry == null) throw new IllegalArgumentException("missing texture " + path);
        return load(zip, entry);
    }

    private static Image load(ZipFile zip, ZipEntry entry) throws Exception {
        byte[] bytes;
        try (var in = zip.getInputStream(entry)) {
            bytes = in.readAllBytes();
        }
        ByteBuffer encoded = memAlloc(bytes.length);
        encoded.put(bytes).flip();
        int[] w = new int[1], h = new int[1], comp = new int[1];
        ByteBuffer rgba = stbi_load_from_memory(encoded, w, h, comp, 4);
        memFree(encoded);
        if (rgba == null) throw new IllegalArgumentException("could not decode " + entry.getName());
        return new Image(rgba, w[0], h[0], true);
    }

    private static ZipEntry entry(ZipFile zip, String path) {
        return zip.getEntry(path);
    }

    private static void putTile(ByteBuffer atlas, int atlasPx, int tilePx, int cols, int tile, Image src) {
        int tx = tile % cols;
        int ty = tile / cols;
        for (int y = 0; y < tilePx; y++) {
            int sy = src.h - 1 - Math.min(src.h - 1, y * src.h / tilePx);
            for (int x = 0; x < tilePx; x++) {
                int sx = Math.min(src.w - 1, x * src.w / tilePx);
                int srcIdx = (sy * src.w + sx) * 4;
                int dstX = tx * tilePx + x;
                int dstY = ty * tilePx + y;
                int dstIdx = (dstY * atlasPx + dstX) * 4;
                atlas.put(dstIdx,     src.rgba.get(srcIdx));
                atlas.put(dstIdx + 1, src.rgba.get(srcIdx + 1));
                atlas.put(dstIdx + 2, src.rgba.get(srcIdx + 2));
                atlas.put(dstIdx + 3, src.rgba.get(srcIdx + 3));
            }
        }
        free(src);
    }

    private static Image tint(Image src, int tint) {
        float tr = ((tint >> 16) & 0xff) / 255f;
        float tg = ((tint >> 8) & 0xff) / 255f;
        float tb = (tint & 0xff) / 255f;
        ByteBuffer out = org.lwjgl.BufferUtils.createByteBuffer(src.w * src.h * 4);
        for (int i = 0; i < src.w * src.h; i++) {
            int idx = i * 4;
            out.put(idx,     (byte) clamp(((src.rgba.get(idx) & 0xff) * tr)));
            out.put(idx + 1, (byte) clamp(((src.rgba.get(idx + 1) & 0xff) * tg)));
            out.put(idx + 2, (byte) clamp(((src.rgba.get(idx + 2) & 0xff) * tb)));
            out.put(idx + 3, src.rgba.get(idx + 3));
        }
        free(src);
        return new Image(out, src.w, src.h, false);
    }

    private static Image composeOverlay(Image base, Image overlay) {
        int w = base.w, h = base.h;
        ByteBuffer out = org.lwjgl.BufferUtils.createByteBuffer(w * h * 4);
        for (int y = 0; y < h; y++) {
            int oy = Math.min(overlay.h - 1, y * overlay.h / h);
            for (int x = 0; x < w; x++) {
                int ox = Math.min(overlay.w - 1, x * overlay.w / w);
                int bi = (y * w + x) * 4;
                int oi = (oy * overlay.w + ox) * 4;
                float a = (overlay.rgba.get(oi + 3) & 0xff) / 255f;
                out.put(bi,     (byte) blend(base.rgba.get(bi) & 0xff, overlay.rgba.get(oi) & 0xff, a));
                out.put(bi + 1, (byte) blend(base.rgba.get(bi + 1) & 0xff, overlay.rgba.get(oi + 1) & 0xff, a));
                out.put(bi + 2, (byte) blend(base.rgba.get(bi + 2) & 0xff, overlay.rgba.get(oi + 2) & 0xff, a));
                out.put(bi + 3, base.rgba.get(bi + 3));
            }
        }
        free(base);
        free(overlay);
        return new Image(out, w, h, false);
    }

    private static int sampleTint(Image colormap, int fallback) {
        if (colormap.w <= 0 || colormap.h <= 0) return fallback;
        float temperature = 0.8f;
        float downfall = 0.4f;
        int x = Math.min(colormap.w - 1, Math.round((1f - temperature) * (colormap.w - 1)));
        int y = Math.min(colormap.h - 1, Math.round((1f - downfall * temperature) * (colormap.h - 1)));
        int idx = (y * colormap.w + x) * 4;
        int r = colormap.rgba.get(idx) & 0xff;
        int g = colormap.rgba.get(idx + 1) & 0xff;
        int b = colormap.rgba.get(idx + 2) & 0xff;
        free(colormap);
        return (r << 16) | (g << 8) | b;
    }

    private static int blend(int base, int over, float alpha) {
        return clamp(base * (1f - alpha) + over * alpha);
    }

    private static int clamp(float v) {
        return Math.max(0, Math.min(255, Math.round(v)));
    }

    private static void free(Image image) {
        if (image.nativeMemory) stbi_image_free(image.rgba);
    }

    private static int[] colors(int... cs) { return cs; }
    private static int darken(int c, float f) {
        int r = (int)(((c>>16)&0xff)*f), g = (int)(((c>>8)&0xff)*f), b = (int)((c&0xff)*f);
        return (r<<16)|(g<<8)|b;
    }

    private record Image(ByteBuffer rgba, int w, int h, boolean nativeMemory) {}
}
