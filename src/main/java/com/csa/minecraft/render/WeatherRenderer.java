package com.csa.minecraft.render;

import com.csa.minecraft.Environment;
import com.csa.minecraft.engine.Camera;
import com.csa.minecraft.engine.Mesh;
import com.csa.minecraft.engine.Shader;
import com.csa.minecraft.world.Block;
import com.csa.minecraft.world.Chunk;
import com.csa.minecraft.world.World;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.lwjgl.opengl.GL11.*;

public class WeatherRenderer {
    private final Shader shader;
    private final Mesh mesh;
    private final List<Drop> drops = new ArrayList<>();
    private final Random rng = new Random(8192);

    private static final String VERT = """
        #version 330 core
        layout(location=0) in vec3 aPos;
        layout(location=1) in float aAlpha;
        uniform mat4 uView;
        uniform mat4 uProj;
        out float vAlpha;
        out float vDist;
        void main(){
            vec4 viewPos = uView * vec4(aPos, 1.0);
            vDist = length(viewPos.xyz);
            vAlpha = aAlpha;
            gl_Position = uProj * viewPos;
        }
        """;

    private static final String FRAG = """
        #version 330 core
        in float vAlpha;
        in float vDist;
        out vec4 fragColor;
        uniform vec3 uFogColor;
        uniform float uFogStart;
        uniform float uFogEnd;
        uniform float uThunder;
        void main(){
            vec3 rainColor = mix(vec3(0.58, 0.68, 0.80), vec3(0.72, 0.82, 0.96), 1.0 - uThunder);
            float fog = clamp((vDist - uFogStart) / max(0.0001, uFogEnd - uFogStart), 0.0, 1.0);
            fragColor = vec4(mix(rainColor, uFogColor, fog), vAlpha * (1.0 - fog * 0.55));
        }
        """;

    public WeatherRenderer() {
        shader = new Shader(VERT, FRAG);
        mesh = new Mesh();
    }

    public void render(World world, Camera cam, Environment env) {
        float rain = env.rainStrength();
        if (rain <= 0.01f) {
            drops.clear();
            return;
        }

        List<Float> data = new ArrayList<>(9000);
        buildRain(world, cam, rain, data);
        if (data.isEmpty()) return;

        mesh.upload(toArray(data), new int[]{3, 1});

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDepthMask(false);
        glDisable(GL_CULL_FACE);

        shader.use();
        shader.setMat4("uView", cam.view);
        shader.setMat4("uProj", cam.proj);
        float farBlocks = world.renderDistance() * (float) Chunk.SX;
        shader.setFloat("uFogStart", Math.max(0f, farBlocks - 1.3f * Chunk.SX));
        shader.setFloat("uFogEnd", farBlocks);
        Vector3f fog = env.fogColor();
        shader.setVec3("uFogColor", fog.x, fog.y, fog.z);
        shader.setFloat("uThunder", env.thunderStrength());
        mesh.draw();

        glEnable(GL_CULL_FACE);
        glDepthMask(true);
        glDisable(GL_BLEND);
    }

    private void buildRain(World world, Camera cam, float rain, List<Float> data) {
        long nowNanos = System.nanoTime();
        float dt = frameDt(nowNanos);
        int targetDrops = (int) (420 + 360 * rain);
        while (drops.size() < targetDrops) drops.add(newDrop(cam, true));
        while (drops.size() > targetDrops) drops.remove(drops.size() - 1);

        float radius = 19.0f;
        float topBand = Math.min(Chunk.SY - 2f, cam.pos.y + 20.0f);
        float bottomBand = Math.max(1.0f, cam.pos.y - 10.0f);
        float width = 0.035f + rain * 0.018f;
        float length = 1.65f + rain * 0.95f;
        float windX = 0.22f + rain * 0.24f;
        float windZ = -0.10f;

        for (Drop drop : drops) {
            drop.y -= drop.speed * dt;
            drop.x += windX * dt * 1.8f;
            drop.z += windZ * dt * 1.8f;
            float dx = drop.x - cam.pos.x;
            float dz = drop.z - cam.pos.z;
            float dist2 = dx * dx + dz * dz;
            if (drop.y < bottomBand || drop.y > Chunk.SY - 2f || dist2 > radius * radius) {
                respawn(drop, cam, false);
                continue;
            }
            if (!hasOpenSky(world, (int) Math.floor(drop.x), (int) Math.ceil(drop.y), (int) Math.floor(drop.z))) {
                respawn(drop, cam, false);
                continue;
            }

            float edgeFade = 1.0f - (float) Math.sqrt(dist2) / radius;
            float alpha = rain * (0.10f + 0.32f * edgeFade) * drop.alpha;
            Vector3f top = new Vector3f(drop.x, drop.y, drop.z);
            Vector3f bottom = new Vector3f(drop.x - windX, drop.y - length * drop.lenScale, drop.z - windZ);
            addStreak(data, top, bottom, width * drop.widthScale, 0f, 1f, alpha);
            addStreak(data, top, bottom, width * drop.widthScale, 1f, 0f, alpha * 0.55f);
        }
    }

    private boolean hasOpenSky(World world, int x, int y, int z) {
        for (int yy = Math.max(0, y); yy < Chunk.SY; yy++) {
            Block b = world.getBlock(x, yy, z);
            if (b.solid && b != Block.GLASS && b != Block.LEAVES) return false;
        }
        return true;
    }

    private void addStreak(List<Float> data, Vector3f top, Vector3f bottom,
                           float width, float ox, float oz, float alpha) {
        float hx = ox * width;
        float hz = oz * width;
        addVertex(data, top.x - hx, top.y, top.z - hz, alpha * 0.10f);
        addVertex(data, bottom.x - hx, bottom.y, bottom.z - hz, alpha);
        addVertex(data, bottom.x + hx, bottom.y, bottom.z + hz, alpha);
        addVertex(data, top.x - hx, top.y, top.z - hz, alpha * 0.10f);
        addVertex(data, bottom.x + hx, bottom.y, bottom.z + hz, alpha);
        addVertex(data, top.x + hx, top.y, top.z + hz, alpha * 0.10f);
    }

    private void addVertex(List<Float> data, float x, float y, float z, float alpha) {
        data.add(x);
        data.add(y);
        data.add(z);
        data.add(alpha);
    }

    private float[] toArray(List<Float> data) {
        float[] out = new float[data.size()];
        for (int i = 0; i < out.length; i++) out[i] = data.get(i);
        return out;
    }

    private float lastFrameSeconds = -1f;

    private float frameDt(long nowNanos) {
        float now = nowNanos * 0.000000001f;
        if (lastFrameSeconds < 0f) {
            lastFrameSeconds = now;
            return 0.016f;
        }
        float dt = Math.max(0.001f, Math.min(0.05f, now - lastFrameSeconds));
        lastFrameSeconds = now;
        return dt;
    }

    private Drop newDrop(Camera cam, boolean randomY) {
        Drop d = new Drop();
        respawn(d, cam, randomY);
        return d;
    }

    private void respawn(Drop d, Camera cam, boolean randomY) {
        float radius = 19.0f;
        float angle = rng.nextFloat() * (float) Math.PI * 2f;
        float dist = (float) Math.sqrt(rng.nextFloat()) * radius;
        d.x = cam.pos.x + (float) Math.cos(angle) * dist;
        d.z = cam.pos.z + (float) Math.sin(angle) * dist;
        float top = Math.min(Chunk.SY - 2f, cam.pos.y + 20.0f);
        float bottom = Math.max(1.0f, cam.pos.y - 10.0f);
        d.y = randomY ? bottom + rng.nextFloat() * Math.max(1f, top - bottom) : top;
        d.speed = 18f + rng.nextFloat() * 14f;
        d.alpha = 0.65f + rng.nextFloat() * 0.45f;
        d.lenScale = 0.75f + rng.nextFloat() * 0.65f;
        d.widthScale = 0.75f + rng.nextFloat() * 0.55f;
    }

    private static class Drop {
        float x, y, z;
        float speed;
        float alpha;
        float lenScale;
        float widthScale;
    }
}
