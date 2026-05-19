package com.csa.minecraft.render;

import com.csa.minecraft.Environment;
import com.csa.minecraft.engine.Camera;
import com.csa.minecraft.engine.Mesh;
import com.csa.minecraft.engine.Shader;
import com.csa.minecraft.engine.Texture;
import com.csa.minecraft.world.Chunk;
import com.csa.minecraft.world.ChunkMesher;
import com.csa.minecraft.world.World;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import static org.lwjgl.opengl.GL11.*;

public class WorldRenderer {
    private final Shader shader;
    private final Shader skyShader;
    private final Texture atlas;
    private final Mesh screenQuad;

    private static final String VERT = """
        #version 330 core
        layout(location=0) in vec3 aPos;
        layout(location=1) in vec3 aNormal;
        layout(location=2) in vec2 aUV;
        layout(location=3) in float aAO;
        layout(location=4) in float aBlock;
        uniform mat4 uView;
        uniform mat4 uProj;
        uniform mat4 uModel;
        out vec2 vUV;
        out vec3 vWorldNormal;
        out vec3 vWorldPos;
        out float vAO;
        out float vBlock;
        out float vDist;
        void main(){
            vUV = aUV;
            vAO = aAO;
            vBlock = aBlock;
            vec4 worldPos = uModel * vec4(aPos, 1.0);
            vWorldPos = worldPos.xyz;
            vWorldNormal = normalize(mat3(uModel) * aNormal);
            vec4 viewPos = uView * worldPos;
            vDist = length(viewPos.xyz);
            gl_Position = uProj * viewPos;
        }
        """;
    private static final String FRAG = """
        #version 330 core
        in vec2 vUV;
        in vec3 vWorldNormal;
        in vec3 vWorldPos;
        in float vAO;
        in float vBlock;
        in float vDist;
        out vec4 fragColor;
        uniform sampler2D uAtlas;
        uniform vec3 uCamPos;
        uniform vec3 uFogColor;
        uniform vec3 uSkyTop;
        uniform float uFogStart;
        uniform float uFogEnd;
        uniform vec3 uSunDir;
        uniform vec3 uAmbient;
        uniform vec3 uSunColor;
        uniform float uSunIntensity;
        uniform float uWetness;
        uniform float uRain;
        uniform float uTime;
        uniform float uUnderwater;
        uniform int uRenderPass;
        void main(){
            float blockWater = 1.0 - smoothstep(0.25, 0.75, abs(vBlock - 11.0));
            float blockGlass = 1.0 - smoothstep(0.25, 0.75, abs(vBlock - 8.0));
            float transparent = max(blockWater, blockGlass);
            if (uRenderPass == 0 && transparent > 0.5) discard;
            if (uRenderPass == 1 && transparent < 0.5) discard;

            vec2 uv = vUV;
            uv += blockWater * vec2(
                sin(vWorldPos.x * 2.1 + uTime * 1.3) * 0.0035,
                cos(vWorldPos.z * 2.4 - uTime * 1.1) * 0.0035
            );
            vec4 c = texture(uAtlas, uv);
            if (c.a < 0.1) discard;
            vec3 N = normalize(vWorldNormal);
            vec3 V = normalize(uCamPos - vWorldPos);
            vec3 L = normalize(uSunDir);
            vec3 H = normalize(L + V);
            float NdotL = max(dot(N, L), 0.0);
            float halfLambert = NdotL * 0.5 + 0.5;
            float skyFacing = N.y * 0.5 + 0.5;
            float ao = mix(0.55, 1.0, vAO * vAO);
            float topWet = step(0.55, N.y) * uWetness;
            float glass = blockGlass;
            float water = blockWater;
            float leaf = 1.0 - smoothstep(0.3, 0.8, abs(vBlock - 5.0));

            vec3 skyBounce = mix(uFogColor, uSkyTop, skyFacing) * (0.13 + 0.15 * skyFacing);
            vec3 ambient = (uAmbient + skyBounce) * ao;
            vec3 diffuse = uSunColor * pow(halfLambert, 1.55) * uSunIntensity * ao;
            vec3 lit = c.rgb * (ambient + diffuse);

            float roughness = mix(0.82, 0.18, max(glass, topWet));
            float specPow = mix(18.0, 92.0, 1.0 - roughness);
            float spec = pow(max(dot(N, H), 0.0), specPow) * (0.12 + 0.50 * topWet + 0.85 * glass);
            float fresnel = pow(1.0 - max(dot(N, V), 0.0), 4.0);
            vec3 R = reflect(-V, N);
            vec3 env = mix(uFogColor, uSkyTop, clamp(R.y * 0.5 + 0.5, 0.0, 1.0));
            lit = mix(lit, lit * 0.82 + env * 0.48, glass * (0.35 + 0.35 * fresnel));
            vec3 waterTint = vec3(0.10, 0.34, 0.78);
            vec3 waterLit = c.rgb * (ambient * 0.85 + diffuse * 0.34) + env * (0.18 + fresnel * 0.28) + waterTint * 0.16;
            lit = mix(lit, waterLit, water);
            lit += uSunColor * spec * (0.7 + uSunIntensity);
            lit += leaf * vec3(0.03, 0.08, 0.03) * max(dot(-N, L), 0.0);
            lit = mix(lit, lit * vec3(0.82, 0.88, 0.94), uRain * 0.35);
            lit = mix(lit, lit * vec3(0.50, 0.78, 1.10) + vec3(0.00, 0.04, 0.12), uUnderwater);
            vec3 mapped = lit / (lit + vec3(0.62));
            lit = mix(lit * 0.86, mapped, 0.25 + uRain * 0.65);
            lit = pow(max(lit, vec3(0.0)), vec3(0.88));
            float fog = clamp((vDist - uFogStart) / max(0.0001, uFogEnd - uFogStart), 0.0, 1.0);
            fog = mix(fog, clamp(vDist / 34.0, 0.0, 1.0), uUnderwater);
            float alpha = mix(c.a, 0.38 + fresnel * 0.18, water);
            fragColor = vec4(mix(lit, uFogColor, fog), alpha);
        }
        """;

    private static final String SKY_VERT = """
        #version 330 core
        layout(location=0) in vec2 aPos;
        out vec2 vPos;
        void main(){
            vPos = aPos;
            gl_Position = vec4(aPos, 0.0, 1.0);
        }
        """;
    private static final String SKY_FRAG = """
        #version 330 core
        in vec2 vPos;
        out vec4 fragColor;
        uniform vec3 uSkyTop;
        uniform vec3 uSkyHorizon;
        uniform vec2 uSunScreen;
        uniform float uSunVisible;
        uniform float uRain;
        uniform float uThunder;
        uniform float uTime;
        uniform float uAspect;
        float hash(float n){ return fract(sin(n) * 43758.5453); }
        void main(){
            float y = clamp(vPos.y * 0.5 + 0.5, 0.0, 1.0);
            vec3 sky = mix(uSkyHorizon, uSkyTop, pow(y, 0.72));
            vec2 p = vec2(vPos.x * uAspect, vPos.y);
            vec2 s = vec2(uSunScreen.x * uAspect, uSunScreen.y);
            float d = length(p - s);
            float sunDisc = smoothstep(0.08, 0.0, d);
            float glow = exp(-d * 3.2);
            float rayMask = max(0.0, 1.0 - d * 0.82);
            float rays = 0.0;
            for (int i = 0; i < 7; i++) {
                float a = float(i) * 1.047 + sin(uTime * 0.08) * 0.18;
                vec2 dir = vec2(cos(a), sin(a));
                float line = pow(max(dot(normalize(p - s), dir), 0.0), 42.0);
                rays += line * rayMask;
            }
            float cloudShade = uRain * (0.10 + 0.07 * sin(vPos.x * 9.0 + uTime * 0.35));
            sky *= 1.0 - cloudShade - uThunder * 0.10;
            sky += vec3(1.0, 0.84, 0.50) * uSunVisible * (sunDisc * 0.72 + glow * 0.12);
            sky += vec3(1.0, 0.88, 0.58) * uSunVisible * rays * (1.0 - uRain) * 0.075;
            vec3 mappedSky = sky / (sky + vec3(0.58));
            sky = mix(sky, mappedSky, 0.25 + uRain * 0.55);
            sky = pow(max(sky, vec3(0.0)), vec3(0.82));
            fragColor = vec4(sky, 1.0);
        }
        """;

    public WorldRenderer() {
        shader = new Shader(VERT, FRAG);
        skyShader = new Shader(SKY_VERT, SKY_FRAG);
        atlas = Texture.buildBlockAtlas();
        screenQuad = new Mesh();
        screenQuad.upload(new float[]{
            -1, -1,  1, -1,  1, 1,
            -1, -1,  1,  1, -1, 1
        }, new int[]{2});
    }

    public void render(World world, Camera cam, Environment env, int width, int height, boolean underwater) {
        double t = System.currentTimeMillis() * 0.001;
        Vector3f sunWorld = sunDirection(t);
        renderSky(cam, env, sunWorld, width, height, (float) t, underwater);

        shader.use();
        shader.setMat4("uView", cam.view);
        shader.setMat4("uProj", cam.proj);
        atlas.bind(0);
        shader.setInt("uAtlas", 0);
        shader.setVec3("uCamPos", cam.pos.x, cam.pos.y, cam.pos.z);

        float farBlocks = world.renderDistance() * (float) Chunk.SX;
        float fogStart = Math.max(0f, farBlocks - 1.5f * Chunk.SX);
        shader.setFloat("uFogStart", fogStart);
        shader.setFloat("uFogEnd", farBlocks);
        Vector3f fog = underwater ? new Vector3f(0.04f, 0.22f, 0.42f) : env.fogColor();
        Vector3f skyTop = underwater ? new Vector3f(0.02f, 0.16f, 0.34f) : env.skyTop();
        shader.setVec3("uFogColor", fog.x, fog.y, fog.z);
        shader.setVec3("uSkyTop", skyTop.x, skyTop.y, skyTop.z);

        shader.setVec3("uSunDir", sunWorld.x, sunWorld.y, sunWorld.z);

        Vector3f ambient = env.ambient();
        Vector3f sunColor = env.sunColor();
        shader.setVec3("uAmbient", ambient.x, ambient.y, ambient.z);
        shader.setVec3("uSunColor", sunColor.x, sunColor.y, sunColor.z);
        shader.setFloat("uSunIntensity", env.sunIntensity());
        shader.setFloat("uWetness", env.wetness());
        shader.setFloat("uRain", env.rainStrength());
        shader.setFloat("uTime", (float) t);
        shader.setFloat("uUnderwater", underwater ? 1f : 0f);

        Matrix4f model = new Matrix4f();
        shader.setInt("uRenderPass", 0);
        glDisable(GL_BLEND);
        glDepthMask(true);
        drawChunks(world, model);

        shader.setInt("uRenderPass", 1);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDepthMask(false);
        drawChunks(world, model);
        glDepthMask(true);
        glDisable(GL_BLEND);
    }

    private void drawChunks(World world, Matrix4f model) {
        for (Chunk c : world.loaded()) {
            if (c.dirty) ChunkMesher.rebuild(c, world);
            if (c.mesh == null || c.mesh.vertexCount() == 0) continue;
            model.identity().translate(c.cx * Chunk.SX, 0, c.cz * Chunk.SZ);
            shader.setMat4("uModel", model);
            c.mesh.draw();
        }
    }

    public void render(World world, Camera cam, Environment env, int width, int height) {
        render(world, cam, env, width, height, false);
    }

    private void renderSky(Camera cam, Environment env, Vector3f sunWorld, int width, int height, float time, boolean underwater) {
        glDisable(GL_DEPTH_TEST);
        glDepthMask(false);
        skyShader.use();
        Vector3f top = underwater ? new Vector3f(0.02f, 0.16f, 0.34f) : env.skyTop();
        Vector3f horizon = underwater ? new Vector3f(0.04f, 0.24f, 0.45f) : env.skyHorizon();
        skyShader.setVec3("uSkyTop", top.x, top.y, top.z);
        skyShader.setVec3("uSkyHorizon", horizon.x, horizon.y, horizon.z);
        Vector4f sunClip = new Vector4f(
            cam.pos.x + sunWorld.x * 1000f,
            cam.pos.y + sunWorld.y * 1000f,
            cam.pos.z + sunWorld.z * 1000f,
            1f
        );
        cam.view.transform(sunClip);
        cam.proj.transform(sunClip);
        float visible = sunClip.w > 0f ? 1f : 0f;
        float sx = visible > 0f ? sunClip.x / sunClip.w : 10f;
        float sy = visible > 0f ? sunClip.y / sunClip.w : 10f;
        skyShader.setVec2("uSunScreen", sx, sy);
        skyShader.setFloat("uSunVisible", visible * (1f - env.rainStrength() * 0.75f));
        skyShader.setFloat("uRain", env.rainStrength());
        skyShader.setFloat("uThunder", env.thunderStrength());
        skyShader.setFloat("uTime", time);
        skyShader.setFloat("uAspect", width / (float) Math.max(1, height));
        screenQuad.draw();
        glDepthMask(true);
        glEnable(GL_DEPTH_TEST);
    }

    private Vector3f sunDirection(double t) {
        float sx = (float) Math.cos(t * 0.08) * 0.58f;
        float sy = (float) (0.68f + Math.sin(t * 0.08) * 0.18f);
        float sz = (float) Math.sin(t * 0.08) * 0.58f;
        return new Vector3f(sx, sy, sz).normalize();
    }
}
