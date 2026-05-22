package com.csa.minecraft.render;

import com.csa.minecraft.Environment;
import com.csa.minecraft.engine.Camera;
import com.csa.minecraft.engine.ChunkWorkerPool;
import com.csa.minecraft.engine.Mesh;
import com.csa.minecraft.engine.Shader;
import com.csa.minecraft.engine.Texture;
import com.csa.minecraft.world.Chunk;
import com.csa.minecraft.world.ChunkMesher;
import com.csa.minecraft.world.World;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL30.*;

public class WorldRenderer {
    private final Shader shader;
    private final Shader skyShader;
    private final Shader rayShader;
    private final Texture atlas;
    private final Texture photonNoise;
    private final Texture sunPortrait;
    private final Mesh screenQuad;
    private int rayVolumeTex = 0;
    private int rayVolumeW = 0, rayVolumeH = 0, rayVolumeD = 0;
    private int rayOriginX = Integer.MIN_VALUE, rayOriginY = Integer.MIN_VALUE, rayOriginZ = Integer.MIN_VALUE;
    private long rayBlockRevision = Long.MIN_VALUE;
    private int rayRingX = 0, rayRingZ = 0;
    private static final int MAX_RAY_VOLUME_XZ = 384;
    private static final int RAY_VOLUME_Y = Chunk.SY;
    private final ByteBuffer rayVolume = BufferUtils.createByteBuffer(MAX_RAY_VOLUME_XZ * RAY_VOLUME_Y * MAX_RAY_VOLUME_XZ);

    // Frustum culling: chunks whose bounding box is off-screen are skipped.
    private final FrustumIntersection frustum = new FrustumIntersection();

    // Mesh building runs on the worker pool; only the GL upload stays on this
    // thread. Each frame the nearest dirty chunks (up to MESH_DISPATCH_MAX) are
    // handed to workers, and finished vertex arrays are uploaded here within a
    // time budget so a burst of completions can't spike one frame.
    private final ChunkWorkerPool workers;
    private static final int MESH_DISPATCH_MAX = 12;
    private static final long MESH_UPLOAD_BUDGET_NS = 3_000_000L;
    /** A chunk's vertex array, built off-thread, waiting for a GL upload here. */
    private record MeshResult(Chunk chunk, float[] data) {}
    private final ConcurrentLinkedQueue<MeshResult> meshResults = new ConcurrentLinkedQueue<>();

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
        uniform sampler2D uPhotonNoise;
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
            vec2 photonWaterUv = vWorldPos.xz * 0.018 + vec2(uTime * 0.018, -uTime * 0.011);
            vec2 photonWater = texture(uPhotonNoise, photonWaterUv).rg - 0.5;
            uv += blockWater * photonWater * 0.0065;
            vec4 c = texture(uAtlas, uv);
            if (c.a < 0.1) discard;
            vec3 N = normalize(vWorldNormal);
            if (blockWater > 0.5 && N.y > 0.4) {
                float h0 = texture(uPhotonNoise, photonWaterUv).r;
                float hx = texture(uPhotonNoise, photonWaterUv + vec2(0.012, 0.0)).r;
                float hz = texture(uPhotonNoise, photonWaterUv + vec2(0.0, 0.012)).r;
                vec3 waveNormal = normalize(vec3((h0 - hx) * 0.32, 1.0, (h0 - hz) * 0.32));
                N = normalize(mix(N, waveNormal, 0.65));
            }
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
        uniform sampler2D uPhotonNoise;
        uniform sampler2D uSunPortrait;
        uniform vec3 uSkyTop;
        uniform vec3 uSkyHorizon;
        uniform vec2 uSunScreen;
        uniform vec2 uMoonScreen;
        uniform vec3 uSunDiskColor;
        uniform float uSunVisible;
        uniform float uMoonVisible;
        uniform float uRain;
        uniform float uThunder;
        uniform float uNight;
        uniform float uTime;
        uniform float uAspect;
        uniform mat4 uInvProj;
        uniform mat4 uInvViewRot;
        float hash(float n){ return fract(sin(n) * 43758.5453); }
        void main(){
            float y = clamp(vPos.y * 0.5 + 0.5, 0.0, 1.0);
            vec3 sky = mix(uSkyHorizon, uSkyTop, pow(y, 0.72));
            float photonCloud = texture(uPhotonNoise, vPos * 0.10 + vec2(uTime * 0.004, 0.12)).r;
            vec2 p = vec2(vPos.x * uAspect, vPos.y);
            vec2 s = vec2(uSunScreen.x * uAspect, uSunScreen.y);
            vec2 m = vec2(uMoonScreen.x * uAspect, uMoonScreen.y);
            float d = length(p - s);
            vec2 portraitUv = (p - s) / vec2(0.18, 0.18) + vec2(0.5);
            float inPortrait = step(0.0, portraitUv.x) * step(portraitUv.x, 1.0)
                              * step(0.0, portraitUv.y) * step(portraitUv.y, 1.0);
            float portraitEdge = smoothstep(0.0, 0.08, portraitUv.x)
                               * smoothstep(0.0, 0.08, portraitUv.y)
                               * smoothstep(0.0, 0.08, 1.0 - portraitUv.x)
                               * smoothstep(0.0, 0.08, 1.0 - portraitUv.y);
            vec3 portrait = texture(uSunPortrait, vec2(portraitUv.x, 1.0 - portraitUv.y)).rgb;
            float portraitMask = inPortrait * portraitEdge;
            float glow = exp(-d * 4.2);
            float rayMask = max(0.0, 1.0 - d * 0.82);
            float rays = 0.0;
            vec2 rayDir = normalize(p - s + vec2(0.0001, 0.0001));
            for (int i = 0; i < 7; i++) {
                float a = float(i) * 1.047 + sin(uTime * 0.08) * 0.18;
                vec2 dir = vec2(cos(a), sin(a));
                float line = pow(max(dot(rayDir, dir), 0.0), 42.0);
                rays += line * rayMask;
            }
            float moonDist = length(p - m);
            float moonDisc = smoothstep(0.075, 0.0, moonDist);
            float moonGlow = exp(-moonDist * 3.6);
            // reconstruct the world-space view ray so stars stay pinned to the
            // sky rather than the screen, and drift slowly like real starlight
            vec4 viewRay = uInvProj * vec4(vPos, 1.0, 1.0);
            vec3 viewDir = normalize(viewRay.xyz / viewRay.w);
            vec3 skyDir = normalize(mat3(uInvViewRot) * viewDir);
            float az = atan(skyDir.z, skyDir.x) + uTime * 0.004;
            vec2 starCell = floor((vec2(az * 0.5, skyDir.y) + vec2(11.3, 4.7)) * 145.0);
            float starSeed = dot(starCell, vec2(12.9898, 78.233));
            float star = step(0.9965, hash(starSeed));
            star *= smoothstep(-0.05, 0.55, skyDir.y) * uNight * (1.0 - uRain);
            float twinkle = 0.6 + 0.4 * sin(uTime * 2.3 + starSeed);
            float cloudShade = uRain * (0.10 + 0.07 * sin(vPos.x * 9.0 + uTime * 0.35));
            cloudShade += uRain * smoothstep(0.42, 0.88, photonCloud) * 0.10;
            sky *= 1.0 - cloudShade - uThunder * 0.10;
            sky += vec3(0.62, 0.72, 1.0) * star * (0.25 + hash(starSeed + 4.0) * 0.75) * twinkle;
            sky += vec3(1.0, 0.70, 0.20) * uSunVisible * glow * 0.16;
            sky = mix(sky, portrait, uSunVisible * portraitMask);
            sky += vec3(1.0, 0.88, 0.58) * uSunVisible * rays * (1.0 - uRain) * 0.075;
            sky += vec3(0.62, 0.72, 0.98) * uMoonVisible * (moonDisc * 0.50 + moonGlow * 0.045);
            vec3 mappedSky = sky / (sky + vec3(0.58));
            sky = mix(sky, mappedSky, 0.25 + uRain * 0.55);
            sky = pow(max(sky, vec3(0.0)), vec3(0.82));
            fragColor = vec4(sky, 1.0);
        }
        """;

    private static final String RAY_FRAG = """
        #version 330 core
        in vec2 vPos;
        out vec4 fragColor;
        uniform sampler3D uVoxels;
        uniform sampler2D uAtlas;
        uniform sampler2D uPhotonNoise;
        uniform vec3 uCamPos;
        uniform vec3 uVolumeOrigin;
        uniform vec3 uVolumeSize;
        uniform vec3 uVolumeRingOffset;
        uniform int uAtlasSize;
        uniform mat4 uInvProj;
        uniform mat4 uInvViewRot;
        uniform vec3 uFogColor;
        uniform vec3 uSkyTop;
        uniform vec3 uSkyHorizon;
        uniform vec3 uSunDir;
        uniform vec3 uAmbient;
        uniform vec3 uSunColor;
        uniform float uSunIntensity;
        uniform float uWetness;
        uniform float uRain;
        uniform float uTime;
        uniform float uUnderwater;
        uniform float uFogEnd;
        uniform mat4 uView;
        uniform mat4 uProj;

        int blockAt(vec3 cell) {
            vec3 local = floor(cell - uVolumeOrigin);
            if (any(lessThan(local, vec3(0.0))) || any(greaterThanEqual(local, uVolumeSize))) return 0;
            vec3 wrapped = mod(local + uVolumeRingOffset, uVolumeSize);
            vec3 uv = (wrapped + vec3(0.5)) / uVolumeSize;
            return int(floor(texture(uVoxels, uv).r * 255.0 + 0.5));
        }

        bool translucent(int b) { return b == 8 || b == 11 || b == 13; }
        bool blocksLight(int b) { return b != 0 && b != 11 && b != 13; }
        bool leaves(int b) { return b == 5 || b == 10 || b == 17; }

        int faceFromNormal(vec3 n) {
            if (n.y > 0.5) return 0;
            if (n.y < -0.5) return 1;
            return 2;
        }

        int tileIndex(int b, int face) {
            if (b == 1) return face == 0 ? 1 : (face == 1 ? 0 : 2);
            if (b == 2) return 0;
            if (b == 3) return 3;
            if (b == 4) return face <= 1 ? 5 : 4;
            if (b == 5) return 6;
            if (b == 6) return 7;
            if (b == 7) return 8;
            if (b == 8) return 9;
            if (b == 9) return face <= 1 ? 11 : 10;
            if (b == 10) return 12;
            if (b == 11) return 13;
            if (b == 12) return 14;
            if (b == 13) return 15;
            if (b == 14) return face <= 1 ? 16 : 17;
            if (b == 15) return 18;
            if (b == 16) return face <= 1 ? 20 : 19;
            if (b == 17) return 21;
            if (b == 18) return face == 0 ? 22 : (face == 1 ? 0 : 23);
            return 3;
        }

        vec2 faceUv(vec3 p, vec3 n) {
            vec3 q = p - n * 0.002;
            vec2 uv;
            if (abs(n.y) > 0.5) uv = q.xz;
            else if (abs(n.x) > 0.5) uv = q.zy;
            else uv = q.xy;
            return clamp(fract(uv), vec2(0.002), vec2(0.998));
        }

        vec4 blockTexel(int b, vec3 n, vec3 p) {
            int tile = tileIndex(b, faceFromNormal(n));
            vec2 cell = vec2(float(tile - (tile / uAtlasSize) * uAtlasSize), float(tile / uAtlasSize));
            vec2 uv = faceUv(p, n);
            if (b == 11 && n.y > 0.4) {
                vec2 waterUv = p.xz * 0.018 + vec2(uTime * 0.018, -uTime * 0.011);
                uv += (texture(uPhotonNoise, waterUv).rg - 0.5) * 0.05;
                uv = fract(uv);
            }
            return texture(uAtlas, (cell + uv) / float(uAtlasSize));
        }

        vec3 blockColor(int b, vec3 n, vec3 p) {
            return blockTexel(b, n, p).rgb;
        }

        vec3 skyColor(vec3 rd) {
            float y = clamp(rd.y * 0.5 + 0.5, 0.0, 1.0);
            vec3 sky = mix(uSkyHorizon, uSkyTop, pow(y, 0.72));
            float sun = pow(max(dot(rd, normalize(uSunDir)), 0.0), 560.0) * (1.0 - uRain);
            float glow = pow(max(dot(rd, normalize(uSunDir)), 0.0), 10.0) * (1.0 - uRain);
            sky += uSunColor * (sun * 3.2 + glow * 0.18) * uSunIntensity;
            sky = mix(sky, uFogColor, uRain * 0.22 + uUnderwater * 0.72);
            return sky;
        }

        bool trace(vec3 ro, vec3 rd, float maxDist, out vec3 hitPos, out vec3 normal, out int blockId, out float dist) {
            vec3 pos = ro + rd * 0.025;
            vec3 cell = floor(pos);
            vec3 stepDir = sign(rd);
            vec3 invDir = 1.0 / max(abs(rd), vec3(0.0001));
            vec3 nextBoundary = cell + step(0.0, rd);
            vec3 tMax = (nextBoundary - pos) / rd;
            tMax = max(tMax, vec3(0.0));
            vec3 tDelta = abs(invDir);
            vec3 lastNormal = vec3(0.0);
            float t = 0.0;
            for (int i = 0; i < 768; i++) {
                int b = blockAt(cell);
                if (b != 0) {
                    hitPos = ro + rd * t;
                    normal = lastNormal;
                    if (leaves(b) && blockTexel(b, normal, hitPos).a < 0.25) {
                        if (tMax.x < tMax.y && tMax.x < tMax.z) {
                            cell.x += stepDir.x; t = tMax.x; tMax.x += tDelta.x; lastNormal = vec3(-stepDir.x, 0.0, 0.0);
                        } else if (tMax.y < tMax.z) {
                            cell.y += stepDir.y; t = tMax.y; tMax.y += tDelta.y; lastNormal = vec3(0.0, -stepDir.y, 0.0);
                        } else {
                            cell.z += stepDir.z; t = tMax.z; tMax.z += tDelta.z; lastNormal = vec3(0.0, 0.0, -stepDir.z);
                        }
                        if (t > maxDist) break;
                        continue;
                    }
                    blockId = b;
                    dist = t;
                    return true;
                }
                if (tMax.x < tMax.y && tMax.x < tMax.z) {
                    cell.x += stepDir.x; t = tMax.x; tMax.x += tDelta.x; lastNormal = vec3(-stepDir.x, 0.0, 0.0);
                } else if (tMax.y < tMax.z) {
                    cell.y += stepDir.y; t = tMax.y; tMax.y += tDelta.y; lastNormal = vec3(0.0, -stepDir.y, 0.0);
                } else {
                    cell.z += stepDir.z; t = tMax.z; tMax.z += tDelta.z; lastNormal = vec3(0.0, 0.0, -stepDir.z);
                }
                if (t > maxDist) break;
            }
            return false;
        }

        bool traceSolid(vec3 ro, vec3 rd, float maxDist, out vec3 hitPos, out vec3 normal, out int blockId, out float dist) {
            vec3 pos = ro + rd * 0.025;
            vec3 cell = floor(pos);
            vec3 stepDir = sign(rd);
            vec3 invDir = 1.0 / max(abs(rd), vec3(0.0001));
            vec3 nextBoundary = cell + step(0.0, rd);
            vec3 tMax = (nextBoundary - pos) / rd;
            tMax = max(tMax, vec3(0.0));
            vec3 tDelta = abs(invDir);
            vec3 lastNormal = vec3(0.0);
            float t = 0.0;
            for (int i = 0; i < 768; i++) {
                int b = blockAt(cell);
                if (b != 0 && !translucent(b)) {
                    hitPos = ro + rd * t;
                    normal = lastNormal;
                    if (leaves(b) && blockTexel(b, normal, hitPos).a < 0.25) {
                        // keep walking through alpha-cutout leaf holes
                    } else {
                        blockId = b;
                        dist = t;
                        return true;
                    }
                }
                if (tMax.x < tMax.y && tMax.x < tMax.z) {
                    cell.x += stepDir.x; t = tMax.x; tMax.x += tDelta.x; lastNormal = vec3(-stepDir.x, 0.0, 0.0);
                } else if (tMax.y < tMax.z) {
                    cell.y += stepDir.y; t = tMax.y; tMax.y += tDelta.y; lastNormal = vec3(0.0, -stepDir.y, 0.0);
                } else {
                    cell.z += stepDir.z; t = tMax.z; tMax.z += tDelta.z; lastNormal = vec3(0.0, 0.0, -stepDir.z);
                }
                if (t > maxDist) break;
            }
            return false;
        }

        float shadow(vec3 p, vec3 l) {
            vec3 hp, hn; int hb; float hd;
            bool hit = trace(p + l * 0.10, l, 42.0, hp, hn, hb, hd);
            if (!hit) return 1.0;
            if (hb == 5 || hb == 10 || hb == 17) return 0.58;
            if (hb == 8) return 0.70;
            if (hb == 11 || hb == 13) return 0.82;
            return 0.18;
        }

        float voxelAO(vec3 p, vec3 n) {
            float occ = 0.0;
            vec3 base = floor(p + n * 0.28);
            occ += blocksLight(blockAt(base + vec3( 1, 0, 0))) ? 1.0 : 0.0;
            occ += blocksLight(blockAt(base + vec3(-1, 0, 0))) ? 1.0 : 0.0;
            occ += blocksLight(blockAt(base + vec3( 0, 1, 0))) ? 1.0 : 0.0;
            occ += blocksLight(blockAt(base + vec3( 0,-1, 0))) ? 1.0 : 0.0;
            occ += blocksLight(blockAt(base + vec3( 0, 0, 1))) ? 1.0 : 0.0;
            occ += blocksLight(blockAt(base + vec3( 0, 0,-1))) ? 1.0 : 0.0;
            return clamp(1.0 - occ * 0.105, 0.42, 1.0);
        }

        vec3 shadeHit(vec3 p, vec3 n, vec3 rd, int b) {
            vec3 l = normalize(uSunDir);
            vec3 v = normalize(-rd);
            vec3 base = blockColor(b, n, p);
            float ndl = max(dot(n, l), 0.0);
            float sky = clamp(n.y * 0.5 + 0.5, 0.0, 1.0);
            float ao = voxelAO(p, n);
            float sh = shadow(p, l);
            vec3 bounce = mix(uFogColor, uSkyTop, sky) * (0.18 + sky * 0.20);
            vec3 color = base * ((uAmbient + bounce) * ao + uSunColor * ndl * sh * uSunIntensity * 1.42);
            float wet = uWetness * step(0.45, n.y);
            float specPower = mix(24.0, 110.0, wet);
            vec3 h = normalize(l + v);
            float spec = pow(max(dot(n, h), 0.0), specPower) * (0.10 + wet * 0.70) * sh;
            color += uSunColor * spec * (0.8 + uSunIntensity);

            if (b == 8 || b == 11 || b == 13) {
                vec3 reflDir = reflect(rd, n);
                vec3 rp, rn; int rb; float rdist;
                vec3 refl = skyColor(reflDir);
                if (trace(p + n * 0.12, reflDir, 34.0, rp, rn, rb, rdist)) {
                    refl = blockColor(rb, rn, rp) * (uAmbient + uSunColor * max(dot(rn, l), 0.0) * shadow(rp, l));
                    refl = mix(refl, skyColor(reflDir), clamp(rdist / 34.0, 0.0, 1.0));
                }
                float fresnel = pow(1.0 - max(dot(n, v), 0.0), 4.0);
                vec3 tp, tn; int tb; float tdist;
                vec3 trans = skyColor(rd);
                if (traceSolid(p + rd * 0.85, rd, uFogEnd, tp, tn, tb, tdist)) {
                    float tsh = shadow(tp, l);
                    float tao = voxelAO(tp, tn);
                    float tndl = max(dot(tn, l), 0.0);
                    vec3 tbase = blockColor(tb, tn, tp);
                    trans = tbase * ((uAmbient + mix(uFogColor, uSkyTop, clamp(tn.y * 0.5 + 0.5, 0.0, 1.0)) * 0.22) * tao
                                   + uSunColor * tndl * tsh * uSunIntensity);
                    trans = mix(trans, skyColor(rd), clamp(tdist / max(1.0, uFogEnd), 0.0, 1.0));
                }
                if (b == 11) {
                    vec3 waterTint = vec3(0.06, 0.25, 0.80);
                    color = mix(trans * vec3(0.62, 0.82, 1.18) + waterTint * 0.14, color, 0.34);
                    color = mix(color, refl, 0.18 + fresnel * 0.34);
                } else {
                    color = mix(trans, color, b == 13 ? 0.42 : 0.24);
                    color = mix(color, refl, 0.16 + fresnel * 0.46);
                }
            }

            vec3 mapped = color / (color + vec3(0.58));
            color = mix(color, mapped, 0.32 + uRain * 0.48);
            return pow(max(color, vec3(0.0)), vec3(0.86));
        }

        void main(){
            vec4 viewRay = uInvProj * vec4(vPos, 1.0, 1.0);
            vec3 rd = normalize(mat3(uInvViewRot) * normalize(viewRay.xyz / viewRay.w));
            vec3 ro = uCamPos;
            vec3 hp, hn; int hb; float hd;
            if (!trace(ro, rd, uFogEnd, hp, hn, hb, hd)) {
                gl_FragDepth = 1.0;
                fragColor = vec4(skyColor(rd), 1.0);
                return;
            }
            vec3 color = shadeHit(hp, hn, rd, hb);
            float fog = clamp(hd / max(1.0, uFogEnd), 0.0, 1.0);
            fog = smoothstep(0.72, 1.0, fog);
            fog = mix(fog, clamp(hd / 31.0, 0.0, 1.0), uUnderwater);
            color = mix(color, uFogColor, fog);
            vec4 clip = uProj * uView * vec4(hp, 1.0);
            gl_FragDepth = clip.z / clip.w * 0.5 + 0.5;
            fragColor = vec4(color, 1.0);
        }
        """;

    public WorldRenderer(ChunkWorkerPool workers) {
        this.workers = workers;
        shader = new Shader(VERT, FRAG);
        skyShader = new Shader(SKY_VERT, SKY_FRAG);
        rayShader = new Shader(SKY_VERT, RAY_FRAG);
        atlas = Texture.buildBlockAtlas();
        photonNoise = Texture.buildPhotonNoise();
        sunPortrait = Files.isRegularFile(Path.of("images.jpg"))
            ? Texture.loadImage(Path.of("images.jpg"), false, true)
            : Texture.buildPhotonNoise();
        screenQuad = new Mesh();
        screenQuad.upload(new float[]{
            -1, -1,  1, -1,  1, 1,
            -1, -1,  1,  1, -1, 1
        }, new int[]{2});
    }

    public void render(World world, Camera cam, Environment env, int width, int height, boolean underwater) {
        render(world, cam, env, width, height, underwater, false);
    }

    public void render(World world, Camera cam, Environment env, int width, int height,
                       boolean underwater, boolean rayTracingLighting) {
        double t = System.currentTimeMillis() * 0.001;
        Vector3f sunWorld = env.sunDirection();
        Vector3f lightWorld = env.lightDirection();
        renderSky(cam, env, sunWorld, width, height, (float) t, underwater);

        if (rayTracingLighting) {
            renderRayTraced(world, cam, env, (float) t, underwater);
            return;
        }

        shader.use();
        shader.setMat4("uView", cam.view);
        shader.setMat4("uProj", cam.proj);
        atlas.bind(0);
        shader.setInt("uAtlas", 0);
        photonNoise.bind(1);
        shader.setInt("uPhotonNoise", 1);
        shader.setVec3("uCamPos", cam.pos.x, cam.pos.y, cam.pos.z);

        float farBlocks = world.renderDistance() * (float) Chunk.SX;
        float fogStart = Math.max(0f, farBlocks - 1.5f * Chunk.SX);
        shader.setFloat("uFogStart", fogStart);
        shader.setFloat("uFogEnd", farBlocks);
        Vector3f fog = underwater ? new Vector3f(0.04f, 0.22f, 0.42f) : env.fogColor();
        Vector3f skyTop = underwater ? new Vector3f(0.02f, 0.16f, 0.34f) : env.skyTop();
        shader.setVec3("uFogColor", fog.x, fog.y, fog.z);
        shader.setVec3("uSkyTop", skyTop.x, skyTop.y, skyTop.z);

        shader.setVec3("uSunDir", lightWorld.x, lightWorld.y, lightWorld.z);

        Vector3f ambient = env.ambient();
        Vector3f sunColor = env.sunColor();
        shader.setVec3("uAmbient", ambient.x, ambient.y, ambient.z);
        shader.setVec3("uSunColor", sunColor.x, sunColor.y, sunColor.z);
        shader.setFloat("uSunIntensity", env.sunIntensity());
        shader.setFloat("uWetness", env.wetness());
        shader.setFloat("uRain", env.rainStrength());
        shader.setFloat("uTime", (float) t);
        shader.setFloat("uUnderwater", underwater ? 1f : 0f);

        // Update the frustum once, then rebuild a budgeted number of dirty
        // meshes — both before the two draw passes so this frame's draws are
        // current.
        frustum.set(new Matrix4f(cam.proj).mul(cam.view));
        updateMeshes(world, cam);

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
            if (c.mesh == null || c.mesh.vertexCount() == 0) continue;
            float minX = c.cx * Chunk.SX, minZ = c.cz * Chunk.SZ;
            if (!frustum.testAab(minX, 0f, minZ,
                                 minX + Chunk.SX, Chunk.SY, minZ + Chunk.SZ)) continue;
            model.identity().translate(c.cx * Chunk.SX, 0, c.cz * Chunk.SZ);
            shader.setMat4("uModel", model);
            c.mesh.draw();
        }
    }

    // Uploads vertex arrays that workers have finished, then hands the nearest
    // dirty chunks to the pool to be (re)built. The frustum is ignored when
    // picking chunks so off-screen edits still get remeshed when their turn
    // comes. Heavy meshing work is off-thread; only the GL upload runs here.
    private void updateMeshes(World world, Camera cam) {
        // Upload finished meshes, oldest first, capped by a time budget. The
        // budget check is after each upload, so at least one always lands.
        long uploadStart = System.nanoTime();
        MeshResult result;
        while ((result = meshResults.poll()) != null) {
            Chunk c = result.chunk();
            c.meshing = false;
            if (c.unloaded) continue; // chunk was unloaded while its job ran
            if (c.mesh == null) c.mesh = new Mesh();
            // glBufferData orphans the old store, so reusing the Mesh avoids
            // churning GL buffer/VAO objects (a driver-side stall) every build.
            c.mesh.upload(result.data(), ChunkMesher.LAYOUT);
            if (System.nanoTime() - uploadStart >= MESH_UPLOAD_BUDGET_NS) break;
        }

        // Pick the nearest dirty, not-already-meshing chunks for this frame.
        Chunk[] nearest = new Chunk[MESH_DISPATCH_MAX];
        long[] bestDist = new long[MESH_DISPATCH_MAX];
        java.util.Arrays.fill(bestDist, Long.MAX_VALUE);
        int count = 0;

        int pcx = (int) Math.floor(cam.pos.x / Chunk.SX);
        int pcz = (int) Math.floor(cam.pos.z / Chunk.SZ);
        for (Chunk c : world.loaded()) {
            if (!c.dirty || c.meshing) continue;
            long ddx = c.cx - pcx, ddz = c.cz - pcz;
            long d = ddx * ddx + ddz * ddz;
            for (int i = 0; i < MESH_DISPATCH_MAX; i++) {
                if (d < bestDist[i]) {
                    for (int j = MESH_DISPATCH_MAX - 1; j > i; j--) {
                        bestDist[j] = bestDist[j - 1];
                        nearest[j] = nearest[j - 1];
                    }
                    bestDist[i] = d;
                    nearest[i] = c;
                    if (count < MESH_DISPATCH_MAX) count++;
                    break;
                }
            }
        }
        // Dispatch to the worker pool. dirty is cleared *before* dispatch so an
        // edit during the build re-sets it and the chunk is simply remeshed
        // once this in-flight job returns (meshing guards against double work).
        for (int i = 0; i < count; i++) {
            Chunk c = nearest[i];
            c.dirty = false;
            c.meshing = true;
            workers.submit(() -> meshResults.add(new MeshResult(c, ChunkMesher.buildMesh(c, world))));
        }
    }

    public void render(World world, Camera cam, Environment env, int width, int height) {
        render(world, cam, env, width, height, false, false);
    }

    private void renderRayTraced(World world, Camera cam, Environment env, float time, boolean underwater) {
        int volumeXZ = Math.min(MAX_RAY_VOLUME_XZ, Math.max(72, world.renderDistance() * Chunk.SX * 2));
        int half = volumeXZ / 2;
        int originX = (int) Math.floor(cam.pos.x) - half;
        int originY = 0;
        int originZ = (int) Math.floor(cam.pos.z) - half;
        updateRayVolume(world, originX, originY, originZ, volumeXZ, RAY_VOLUME_Y, volumeXZ);

        glDisable(GL_DEPTH_TEST);
        glDepthMask(true);
        rayShader.use();
        glActiveTexture(GL_TEXTURE2);
        glBindTexture(GL_TEXTURE_3D, rayVolumeTex);
        rayShader.setInt("uVoxels", 2);
        atlas.bind(0);
        rayShader.setInt("uAtlas", 0);
        rayShader.setInt("uAtlasSize", atlas.atlasSize);
        photonNoise.bind(1);
        rayShader.setInt("uPhotonNoise", 1);
        rayShader.setVec3("uCamPos", cam.pos.x, cam.pos.y, cam.pos.z);
        rayShader.setVec3("uVolumeOrigin", originX, originY, originZ);
        rayShader.setVec3("uVolumeSize", volumeXZ, RAY_VOLUME_Y, volumeXZ);
        rayShader.setVec3("uVolumeRingOffset", rayRingX, 0, rayRingZ);
        rayShader.setMat4("uInvProj", new Matrix4f(cam.proj).invert());
        rayShader.setMat4("uInvViewRot", new Matrix4f(cam.view).setTranslation(0, 0, 0).invert());
        rayShader.setMat4("uView", cam.view);
        rayShader.setMat4("uProj", cam.proj);

        float farBlocks = world.renderDistance() * (float) Chunk.SX;
        Vector3f fog = underwater ? new Vector3f(0.04f, 0.22f, 0.42f) : env.fogColor();
        Vector3f skyTop = underwater ? new Vector3f(0.02f, 0.16f, 0.34f) : env.skyTop();
        Vector3f skyHorizon = underwater ? new Vector3f(0.04f, 0.24f, 0.45f) : env.skyHorizon();
        Vector3f ambient = env.ambient();
        Vector3f sunColor = env.sunColor();
        Vector3f lightWorld = env.lightDirection();
        rayShader.setVec3("uFogColor", fog.x, fog.y, fog.z);
        rayShader.setVec3("uSkyTop", skyTop.x, skyTop.y, skyTop.z);
        rayShader.setVec3("uSkyHorizon", skyHorizon.x, skyHorizon.y, skyHorizon.z);
        rayShader.setVec3("uSunDir", lightWorld.x, lightWorld.y, lightWorld.z);
        rayShader.setVec3("uAmbient", ambient.x, ambient.y, ambient.z);
        rayShader.setVec3("uSunColor", sunColor.x, sunColor.y, sunColor.z);
        rayShader.setFloat("uSunIntensity", env.sunIntensity());
        rayShader.setFloat("uWetness", env.wetness());
        rayShader.setFloat("uRain", env.rainStrength());
        rayShader.setFloat("uTime", time);
        rayShader.setFloat("uUnderwater", underwater ? 1f : 0f);
        rayShader.setFloat("uFogEnd", farBlocks);
        screenQuad.draw();
        glDepthMask(true);
        glEnable(GL_DEPTH_TEST);
    }

    private void updateRayVolume(World world, int originX, int originY, int originZ,
                                 int volumeW, int volumeH, int volumeD) {
        boolean resized = ensureRayVolumeTexture(volumeW, volumeH, volumeD);
        long blockRevision = world.blockRevision();
        boolean invalid = resized
            || rayOriginX == Integer.MIN_VALUE
            || rayOriginY != originY
            || rayBlockRevision != blockRevision
            || Math.abs(originX - rayOriginX) >= volumeW
            || Math.abs(originZ - rayOriginZ) >= volumeD;

        if (invalid) {
            rayRingX = 0;
            rayRingZ = 0;
            rayOriginX = originX;
            rayOriginY = originY;
            rayOriginZ = originZ;
            rayBlockRevision = blockRevision;
            uploadWrappedRegion(world, originX, originY, originZ,
                                volumeW, volumeH, volumeD,
                                0, 0, volumeW, volumeD);
            return;
        }

        int dx = originX - rayOriginX;
        int dz = originZ - rayOriginZ;
        if (dx == 0 && dz == 0) return;

        rayOriginX = originX;
        rayOriginZ = originZ;
        rayRingX = wrap(rayRingX + dx, volumeW);
        rayRingZ = wrap(rayRingZ + dz, volumeD);

        if (dx > 0) {
            uploadWrappedRegion(world, originX, originY, originZ,
                                volumeW, volumeH, volumeD,
                                volumeW - dx, 0, dx, volumeD);
        } else if (dx < 0) {
            uploadWrappedRegion(world, originX, originY, originZ,
                                volumeW, volumeH, volumeD,
                                0, 0, -dx, volumeD);
        }
        if (dz > 0) {
            uploadWrappedRegion(world, originX, originY, originZ,
                                volumeW, volumeH, volumeD,
                                0, volumeD - dz, volumeW, dz);
        } else if (dz < 0) {
            uploadWrappedRegion(world, originX, originY, originZ,
                                volumeW, volumeH, volumeD,
                                0, 0, volumeW, -dz);
        }
    }

    private void uploadWrappedRegion(World world, int originX, int originY, int originZ,
                                     int volumeW, int volumeH, int volumeD,
                                     int localX, int localZ, int width, int depth) {
        int z = localZ;
        int remainingDepth = depth;
        while (remainingDepth > 0) {
            int texZ = wrap(z + rayRingZ, volumeD);
            int zCount = Math.min(remainingDepth, volumeD - texZ);
            int x = localX;
            int remainingWidth = width;
            while (remainingWidth > 0) {
                int texX = wrap(x + rayRingX, volumeW);
                int xCount = Math.min(remainingWidth, volumeW - texX);
                uploadContiguousRegion(world, originX, originY, originZ,
                                       volumeH, x, z, xCount, zCount, texX, texZ);
                x += xCount;
                remainingWidth -= xCount;
            }
            z += zCount;
            remainingDepth -= zCount;
        }
    }

    private void uploadContiguousRegion(World world, int originX, int originY, int originZ,
                                        int volumeH, int localX, int localZ,
                                        int width, int depth, int texX, int texZ) {
        rayVolume.clear();
        for (int z = 0; z < depth; z++) {
            int wz = originZ + localZ + z;
            for (int y = 0; y < volumeH; y++) {
                int wy = originY + y;
                for (int x = 0; x < width; x++) {
                    int wx = originX + localX + x;
                    rayVolume.put((byte) world.getBlock(wx, wy, wz).ordinal());
                }
            }
        }
        rayVolume.flip();
        glActiveTexture(GL_TEXTURE2);
        glBindTexture(GL_TEXTURE_3D, rayVolumeTex);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        glTexSubImage3D(GL_TEXTURE_3D, 0, texX, 0, texZ,
                        width, volumeH, depth,
                        GL_RED, GL_UNSIGNED_BYTE, rayVolume);
    }

    private boolean ensureRayVolumeTexture(int volumeW, int volumeH, int volumeD) {
        if (rayVolumeTex == 0) rayVolumeTex = glGenTextures();
        glActiveTexture(GL_TEXTURE2);
        glBindTexture(GL_TEXTURE_3D, rayVolumeTex);
        glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE);
        if (rayVolumeW == volumeW && rayVolumeH == volumeH && rayVolumeD == volumeD) return false;
        rayVolumeW = volumeW;
        rayVolumeH = volumeH;
        rayVolumeD = volumeD;
        glTexImage3D(GL_TEXTURE_3D, 0, GL_R8,
                     volumeW, volumeH, volumeD,
                     0, GL_RED, GL_UNSIGNED_BYTE, (ByteBuffer) null);
        return true;
    }

    private static int wrap(int value, int size) {
        int r = value % size;
        return r < 0 ? r + size : r;
    }

    private void renderSky(Camera cam, Environment env, Vector3f sunWorld, int width, int height, float time, boolean underwater) {
        glDisable(GL_DEPTH_TEST);
        glDepthMask(false);
        skyShader.use();
        photonNoise.bind(1);
        skyShader.setInt("uPhotonNoise", 1);
        sunPortrait.bind(2);
        skyShader.setInt("uSunPortrait", 2);
        Vector3f top = underwater ? new Vector3f(0.02f, 0.16f, 0.34f) : env.skyTop();
        Vector3f horizon = underwater ? new Vector3f(0.04f, 0.24f, 0.45f) : env.skyHorizon();
        skyShader.setVec3("uSkyTop", top.x, top.y, top.z);
        skyShader.setVec3("uSkyHorizon", horizon.x, horizon.y, horizon.z);
        float[] sunScreen = projectDirection(cam, sunWorld);
        float[] moonScreen = projectDirection(cam, env.moonDirection());
        skyShader.setVec2("uSunScreen", sunScreen[1], sunScreen[2]);
        skyShader.setVec2("uMoonScreen", moonScreen[1], moonScreen[2]);
        Vector3f sunColor = env.sunColor();
        skyShader.setVec3("uSunDiskColor", sunColor.x, sunColor.y, sunColor.z);
        skyShader.setFloat("uSunVisible", sunScreen[0] * env.sunDiscStrength() * (1f - env.rainStrength() * 0.75f) * (underwater ? 0f : 1f));
        skyShader.setFloat("uMoonVisible", moonScreen[0] * env.moonDiscStrength() * (1f - env.rainStrength() * 0.65f) * (underwater ? 0f : 1f));
        skyShader.setFloat("uRain", env.rainStrength());
        skyShader.setFloat("uThunder", env.thunderStrength());
        skyShader.setFloat("uNight", underwater ? 0f : env.nightAmount());
        skyShader.setFloat("uTime", time);
        skyShader.setFloat("uAspect", width / (float) Math.max(1, height));
        // matrices to rebuild the world-space view ray for sky-anchored stars
        skyShader.setMat4("uInvProj", new Matrix4f(cam.proj).invert());
        skyShader.setMat4("uInvViewRot", new Matrix4f(cam.view).setTranslation(0, 0, 0).invert());
        screenQuad.draw();
        glDepthMask(true);
        glEnable(GL_DEPTH_TEST);
    }

    private float[] projectDirection(Camera cam, Vector3f dir) {
        Vector4f clip = new Vector4f(
            cam.pos.x + dir.x * 1000f,
            cam.pos.y + dir.y * 1000f,
            cam.pos.z + dir.z * 1000f,
            1f
        );
        cam.view.transform(clip);
        cam.proj.transform(clip);
        float visible = clip.w > 0f ? 1f : 0f;
        float sx = visible > 0f ? clip.x / clip.w : 10f;
        float sy = visible > 0f ? clip.y / clip.w : 10f;
        return new float[]{ visible, sx, sy };
    }
}
