package com.csa.minecraft.render;

import com.csa.minecraft.engine.Camera;
import com.csa.minecraft.engine.Mesh;
import com.csa.minecraft.engine.Shader;
import com.csa.minecraft.world.Block;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import static org.lwjgl.opengl.GL11.*;

public class BlockEffectsRenderer {
    private static final int MAX_PARTICLES = 240;
    private static final float PARTICLE_GRAVITY = 18f;

    private final Shader shader;
    private final Mesh mesh;
    private final List<Particle> particles = new ArrayList<>();
    private final Random rng = new Random(42);

    private static final String VERT = """
        #version 330 core
        layout(location=0) in vec3 aPos;
        layout(location=1) in vec4 aColor;
        uniform mat4 uView;
        uniform mat4 uProj;
        out vec4 vColor;
        void main(){
            vColor = aColor;
            gl_Position = uProj * uView * vec4(aPos, 1.0);
        }
        """;

    private static final String FRAG = """
        #version 330 core
        in vec4 vColor;
        out vec4 fragColor;
        void main(){ fragColor = vColor; }
        """;

    public BlockEffectsRenderer() {
        shader = new Shader(VERT, FRAG);
        mesh = new Mesh();
    }

    public void spawnBlockBreak(int x, int y, int z, Block block) {
        float[] base = colorFor(block);
        for (int i = 0; i < 28; i++) {
            while (particles.size() >= MAX_PARTICLES && !particles.isEmpty()) particles.remove(0);
            float px = x + 0.15f + rng.nextFloat() * 0.70f;
            float py = y + 0.15f + rng.nextFloat() * 0.70f;
            float pz = z + 0.15f + rng.nextFloat() * 0.70f;
            Vector3f vel = new Vector3f(
                (rng.nextFloat() - 0.5f) * 4.2f,
                2.4f + rng.nextFloat() * 3.0f,
                (rng.nextFloat() - 0.5f) * 4.2f
            );
            float size = 0.065f + rng.nextFloat() * 0.055f;
            float shade = 0.72f + rng.nextFloat() * 0.34f;
            particles.add(new Particle(new Vector3f(px, py, pz), vel,
                base[0] * shade, base[1] * shade, base[2] * shade, size,
                0.55f + rng.nextFloat() * 0.35f));
        }
    }

    public void render(Camera cam, float dt, int breakX, int breakY, int breakZ, float breakProgress) {
        List<Float> data = new ArrayList<>(particles.size() * 216);
        updateAndBuildParticles(dt, data);
        if (breakProgress > 0f) buildBreakOverlay(breakX, breakY, breakZ, breakProgress, data);
        if (data.isEmpty()) return;

        mesh.upload(toArray(data), new int[]{3, 4});

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDepthMask(false);
        glDisable(GL_CULL_FACE);

        shader.use();
        shader.setMat4("uView", cam.view);
        shader.setMat4("uProj", cam.proj);
        mesh.draw();

        glEnable(GL_CULL_FACE);
        glDepthMask(true);
        glDisable(GL_BLEND);
    }

    private void updateAndBuildParticles(float dt, List<Float> data) {
        Iterator<Particle> it = particles.iterator();
        while (it.hasNext()) {
            Particle p = it.next();
            p.life -= dt;
            if (p.life <= 0f) {
                it.remove();
                continue;
            }
            p.vel.y -= PARTICLE_GRAVITY * dt;
            p.pos.fma(dt, p.vel);
            float a = Math.min(1f, p.life / 0.35f);
            addCube(data, p.pos.x, p.pos.y, p.pos.z, p.size, p.r, p.g, p.b, a);
        }
    }

    private void buildBreakOverlay(int x, int y, int z, float progress, List<Float> data) {
        float p = Math.max(0f, Math.min(1f, progress));
        float alpha = 0.18f + p * 0.38f;
        float inset = 0.018f;
        float width = 0.022f + p * 0.018f;
        int count = 2 + (int) Math.floor(p * 7f);

        for (int face = 0; face < 6; face++) {
            addCrackLine(data, x, y, z, face, 0.18f, 0.18f, 0.82f, 0.82f, width, inset, alpha);
            addCrackLine(data, x, y, z, face, 0.18f, 0.82f, 0.82f, 0.18f, width, inset, alpha);
            for (int i = 0; i < count; i++) {
                float t = (i + 1f) / (count + 1f);
                float wobble = (float) Math.sin((i + 1) * 12.9898f + face * 78.233f) * 0.08f;
                addCrackLine(data, x, y, z, face,
                    0.50f, 0.50f,
                    Math.max(0.10f, Math.min(0.90f, t + wobble)),
                    Math.max(0.10f, Math.min(0.90f, 1f - t * 0.55f - wobble)),
                    width, inset, alpha);
            }
        }
    }

    private void addCrackLine(List<Float> data, int x, int y, int z, int face,
                              float u0, float v0, float u1, float v1,
                              float width, float inset, float alpha) {
        Vector3f a = facePoint(x, y, z, face, u0, v0, inset);
        Vector3f b = facePoint(x, y, z, face, u1, v1, inset);
        Vector3f along = new Vector3f(b).sub(a);
        if (along.lengthSquared() < 0.0001f) return;
        along.normalize();
        Vector3f normal = faceNormal(face);
        Vector3f side = new Vector3f(along).cross(normal).normalize().mul(width);
        Vector3f p0 = new Vector3f(a).sub(side);
        Vector3f p1 = new Vector3f(a).add(side);
        Vector3f p2 = new Vector3f(b).add(side);
        Vector3f p3 = new Vector3f(b).sub(side);
        addQuad(data, p0, p1, p2, p3, 0f, 0f, 0f, alpha);
    }

    private Vector3f facePoint(int x, int y, int z, int face, float u, float v, float inset) {
        return switch (face) {
            case 0 -> new Vector3f(x + 1f + inset, y + v, z + u);
            case 1 -> new Vector3f(x - inset, y + v, z + 1f - u);
            case 2 -> new Vector3f(x + u, y + 1f + inset, z + 1f - v);
            case 3 -> new Vector3f(x + u, y - inset, z + v);
            case 4 -> new Vector3f(x + 1f - u, y + v, z + 1f + inset);
            default -> new Vector3f(x + u, y + v, z - inset);
        };
    }

    private Vector3f faceNormal(int face) {
        return switch (face) {
            case 0 -> new Vector3f(1, 0, 0);
            case 1 -> new Vector3f(-1, 0, 0);
            case 2 -> new Vector3f(0, 1, 0);
            case 3 -> new Vector3f(0, -1, 0);
            case 4 -> new Vector3f(0, 0, 1);
            default -> new Vector3f(0, 0, -1);
        };
    }

    private void addCube(List<Float> data, float x, float y, float z, float s,
                         float r, float g, float b, float a) {
        float h = s * 0.5f;
        Vector3f p000 = new Vector3f(x - h, y - h, z - h);
        Vector3f p001 = new Vector3f(x - h, y - h, z + h);
        Vector3f p010 = new Vector3f(x - h, y + h, z - h);
        Vector3f p011 = new Vector3f(x - h, y + h, z + h);
        Vector3f p100 = new Vector3f(x + h, y - h, z - h);
        Vector3f p101 = new Vector3f(x + h, y - h, z + h);
        Vector3f p110 = new Vector3f(x + h, y + h, z - h);
        Vector3f p111 = new Vector3f(x + h, y + h, z + h);
        addQuad(data, p101, p111, p110, p100, r, g, b, a);
        addQuad(data, p000, p010, p011, p001, r, g, b, a);
        addQuad(data, p011, p111, p101, p001, r, g, b, a);
        addQuad(data, p000, p100, p110, p010, r, g, b, a);
        addQuad(data, p010, p110, p111, p011, r, g, b, a);
        addQuad(data, p000, p001, p101, p100, r, g, b, a);
    }

    private void addQuad(List<Float> data, Vector3f p0, Vector3f p1, Vector3f p2, Vector3f p3,
                         float r, float g, float b, float a) {
        addVertex(data, p0, r, g, b, a);
        addVertex(data, p1, r, g, b, a);
        addVertex(data, p2, r, g, b, a);
        addVertex(data, p0, r, g, b, a);
        addVertex(data, p2, r, g, b, a);
        addVertex(data, p3, r, g, b, a);
    }

    private void addVertex(List<Float> data, Vector3f p, float r, float g, float b, float a) {
        data.add(p.x);
        data.add(p.y);
        data.add(p.z);
        data.add(r);
        data.add(g);
        data.add(b);
        data.add(a);
    }

    private float[] toArray(List<Float> data) {
        float[] out = new float[data.size()];
        for (int i = 0; i < out.length; i++) out[i] = data.get(i);
        return out;
    }

    private float[] colorFor(Block b) {
        return switch (b) {
            case GRASS -> new float[]{0.28f, 0.62f, 0.24f};
            case DIRT -> new float[]{0.45f, 0.28f, 0.13f};
            case STONE -> new float[]{0.50f, 0.50f, 0.50f};
            case WOOD -> new float[]{0.45f, 0.27f, 0.14f};
            case LEAVES -> new float[]{0.16f, 0.42f, 0.16f};
            case SAND -> new float[]{0.82f, 0.72f, 0.45f};
            case PLANKS -> new float[]{0.62f, 0.43f, 0.20f};
            case GLASS -> new float[]{0.72f, 0.86f, 1.00f};
            default -> new float[]{1f, 1f, 1f};
        };
    }

    private static class Particle {
        final Vector3f pos;
        final Vector3f vel;
        final float r, g, b, size;
        float life;

        Particle(Vector3f pos, Vector3f vel, float r, float g, float b, float size, float life) {
            this.pos = pos;
            this.vel = vel;
            this.r = r;
            this.g = g;
            this.b = b;
            this.size = size;
            this.life = life;
        }
    }
}
