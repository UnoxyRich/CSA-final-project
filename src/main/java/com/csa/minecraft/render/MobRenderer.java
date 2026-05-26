package com.csa.minecraft.render;

import com.csa.minecraft.engine.Camera;
import com.csa.minecraft.engine.Mesh;
import com.csa.minecraft.engine.Shader;
import com.csa.minecraft.entity.Cow;
import com.csa.minecraft.entity.Mob;
import com.csa.minecraft.entity.MobManager;
import com.csa.minecraft.entity.Pig;
import com.csa.minecraft.entity.ZombiePigman;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import static org.lwjgl.opengl.GL11.*;

public class MobRenderer {
    private final Shader shader;
    private final Mesh cube;
    // Blends every draw call toward red when a mob was recently hit (0=normal, 1=full red).
    private float hurtFactor = 0f;

    // Pig / pigman head - pink
    private static final float PIG_R = 0.93f, PIG_G = 0.62f, PIG_B = 0.62f;
    private static final float SNOUT_R = 0.97f, SNOUT_G = 0.73f, SNOUT_B = 0.71f;

    // Cow - dark body, white patches, tan muzzle
    private static final float COW_R = 0.16f, COW_G = 0.13f, COW_B = 0.11f;
    private static final float WHITE_R = 0.92f, WHITE_G = 0.90f, WHITE_B = 0.88f;
    private static final float MUZZLE_R = 0.76f, MUZZLE_G = 0.62f, MUZZLE_B = 0.50f;
    private static final float HORN_R = 0.87f, HORN_G = 0.84f, HORN_B = 0.76f;
    private static final float UDDER_R = 0.87f, UDDER_G = 0.68f, UDDER_B = 0.68f;

    // Zombie pigman - zombie green body, gold sword
    private static final float ZOM_R = 0.38f, ZOM_G = 0.58f, ZOM_B = 0.30f;
    private static final float GOLD_R = 0.90f, GOLD_G = 0.78f, GOLD_B = 0.15f;
    private static final float GOLD_D_R = 0.72f, GOLD_D_G = 0.58f, GOLD_D_B = 0.10f;

    // Shared
    private static final float EYE_R = 0.05f, EYE_G = 0.05f, EYE_B = 0.05f;

    private static final String VERT = """
        #version 330 core
        layout(location=0) in vec3 aPos;
        uniform mat4 uModel;
        uniform mat4 uView;
        uniform mat4 uProj;
        void main(){
            gl_Position = uProj * uView * uModel * vec4(aPos, 1.0);
        }
        """;
    private static final String FRAG = """
        #version 330 core
        out vec4 f;
        uniform vec4 uColor;
        void main(){ f = uColor; }
        """;

    public MobRenderer() {
        shader = new Shader(VERT, FRAG);
        cube = new Mesh();
        cube.upload(unitCube(), new int[]{3});
    }

    public void render(MobManager mobs, Camera cam) {
        if (mobs.pigs().isEmpty() && mobs.cows().isEmpty() && mobs.pigmen().isEmpty()) return;
        glDisable(GL_CULL_FACE);
        shader.use();
        shader.setMat4("uView", cam.view);
        shader.setMat4("uProj", cam.proj);
        for (Pig p          : mobs.pigs())   renderPig(p);
        for (Cow c          : mobs.cows())   renderCow(c);
        for (ZombiePigman z : mobs.pigmen()) renderZombiePigman(z);
        hurtFactor = 0f;
        for (Pig p          : mobs.pigs())   renderHealthBar(p, cam);
        for (Cow c          : mobs.cows())   renderHealthBar(c, cam);
        for (ZombiePigman z : mobs.pigmen()) renderHealthBar(z, cam);
        glEnable(GL_CULL_FACE);
    }

    // --- Pig ---

    private void renderPig(Pig pig) {
        hurtFactor = Math.min(1f, pig.hurtTimer() / 0.3f);
        Vector3f p = pig.position();
        float leg = 0.45f * (float) Math.sin(pig.animTime());
        Matrix4f base = new Matrix4f().translate(p.x, p.y, p.z).rotateY(pig.yaw());

        // Legs — diagonal pairs swing together (trot gait)
        legCuboid(base, -0.22f, 0.40f, -0.22f, 0.22f, 0.40f, 0.22f, +leg, PIG_R, PIG_G, PIG_B);
        legCuboid(base, +0.22f, 0.40f, -0.22f, 0.22f, 0.40f, 0.22f, -leg, PIG_R, PIG_G, PIG_B);
        legCuboid(base, -0.22f, 0.40f, +0.22f, 0.22f, 0.40f, 0.22f, -leg, PIG_R, PIG_G, PIG_B);
        legCuboid(base, +0.22f, 0.40f, +0.22f, 0.22f, 0.40f, 0.22f, +leg, PIG_R, PIG_G, PIG_B);
        // Body
        cuboid(base,  0f,    0.64f,  0.03f, 0.85f, 0.50f, 0.90f, PIG_R,   PIG_G,   PIG_B);
        // Head (faces -Z before yaw)
        cuboid(base,  0f,    0.76f, -0.62f, 0.54f, 0.44f, 0.50f, PIG_R,   PIG_G,   PIG_B);
        // Snout
        cuboid(base,  0f,    0.68f, -0.92f, 0.28f, 0.18f, 0.12f, SNOUT_R, SNOUT_G, SNOUT_B);
        // Ears
        cuboid(base, -0.20f, 1.05f, -0.62f, 0.14f, 0.14f, 0.12f, PIG_R,   PIG_G,   PIG_B);
        cuboid(base, +0.20f, 1.05f, -0.62f, 0.14f, 0.14f, 0.12f, PIG_R,   PIG_G,   PIG_B);
        // Eyes
        cuboid(base, -0.13f, 0.81f, -0.88f, 0.07f, 0.06f, 0.03f, EYE_R,   EYE_G,   EYE_B);
        cuboid(base, +0.13f, 0.81f, -0.88f, 0.07f, 0.06f, 0.03f, EYE_R,   EYE_G,   EYE_B);
    }

    // --- Cow ---

    private void renderCow(Cow cow) {
        hurtFactor = Math.min(1f, cow.hurtTimer() / 0.3f);
        Vector3f p = cow.position();
        float leg = 0.40f * (float) Math.sin(cow.animTime());
        Matrix4f base = new Matrix4f().translate(p.x, p.y, p.z).rotateY(cow.yaw());

        // Legs
        legCuboid(base, -0.24f, 0.62f, -0.30f, 0.24f, 0.62f, 0.24f, +leg, COW_R,   COW_G,   COW_B);
        legCuboid(base, +0.24f, 0.62f, -0.30f, 0.24f, 0.62f, 0.24f, -leg, COW_R,   COW_G,   COW_B);
        legCuboid(base, -0.24f, 0.62f, +0.30f, 0.24f, 0.62f, 0.24f, -leg, COW_R,   COW_G,   COW_B);
        legCuboid(base, +0.24f, 0.62f, +0.30f, 0.24f, 0.62f, 0.24f, +leg, COW_R,   COW_G,   COW_B);
        // Body
        cuboid(base,  0f,    0.89f,  0f,    0.90f, 0.56f, 1.10f, COW_R,   COW_G,   COW_B);
        // White patches on body surface
        cuboid(base,  0.00f, 1.18f, +0.22f, 0.40f, 0.06f, 0.40f, WHITE_R, WHITE_G, WHITE_B);
        cuboid(base, -0.47f, 0.89f, -0.10f, 0.06f, 0.38f, 0.32f, WHITE_R, WHITE_G, WHITE_B);
        // Head
        cuboid(base,  0f,    1.01f, -0.78f, 0.52f, 0.50f, 0.52f, COW_R,   COW_G,   COW_B);
        // Muzzle
        cuboid(base,  0f,    0.89f, -1.04f, 0.38f, 0.26f, 0.14f, MUZZLE_R,MUZZLE_G,MUZZLE_B);
        // Horns
        cuboid(base, -0.24f, 1.38f, -0.78f, 0.08f, 0.24f, 0.08f, HORN_R,  HORN_G,  HORN_B);
        cuboid(base, +0.24f, 1.38f, -0.78f, 0.08f, 0.24f, 0.08f, HORN_R,  HORN_G,  HORN_B);
        // Udder
        cuboid(base,  0f,    0.55f, +0.32f, 0.40f, 0.18f, 0.36f, UDDER_R, UDDER_G, UDDER_B);
        // Eyes
        cuboid(base, -0.20f, 1.09f, -1.05f, 0.09f, 0.08f, 0.03f, EYE_R,   EYE_G,   EYE_B);
        cuboid(base, +0.20f, 1.09f, -1.05f, 0.09f, 0.08f, 0.03f, EYE_R,   EYE_G,   EYE_B);
    }

    // --- Zombie Pigman ---

    private void renderZombiePigman(ZombiePigman z) {
        hurtFactor = Math.min(1f, z.hurtTimer() / 0.3f);
        Vector3f p = z.position();
        float legSwing = 0.50f * (float) Math.sin(z.animTime());
        float arm = z.armSwing();
        Matrix4f base = new Matrix4f().translate(p.x, p.y, p.z).rotateY(z.yaw());

        // Legs
        legCuboid(base, -0.15f, 0.75f, 0f, 0.28f, 0.75f, 0.28f, +legSwing, ZOM_R, ZOM_G, ZOM_B);
        legCuboid(base, +0.15f, 0.75f, 0f, 0.28f, 0.75f, 0.28f, -legSwing, ZOM_R, ZOM_G, ZOM_B);
        // Torso
        cuboid(base, 0f, 1.14f, 0f, 0.54f, 0.78f, 0.32f, ZOM_R, ZOM_G, ZOM_B);
        // Left arm (swings opposite to sword arm)
        legCuboid(base, -0.40f, 1.53f, 0f, 0.26f, 0.72f, 0.26f, -arm, ZOM_R, ZOM_G, ZOM_B);
        // Right arm + sword share the same pivot so the sword follows the arm
        Matrix4f armPivot = new Matrix4f(base).translate(0.40f, 1.53f, 0f).rotateX(arm);
        draw(new Matrix4f(armPivot).translate(0f,    -0.36f, 0f).scale(0.26f, 0.72f, 0.26f), ZOM_R,    ZOM_G,    ZOM_B);
        draw(new Matrix4f(armPivot).translate(0.08f, -1.02f,-0.04f).scale(0.07f, 0.80f, 0.07f), GOLD_R,   GOLD_G,   GOLD_B);
        draw(new Matrix4f(armPivot).translate(0.08f, -0.62f,-0.04f).scale(0.30f, 0.07f, 0.10f), GOLD_D_R, GOLD_D_G, GOLD_D_B);
        // Pig head on top
        cuboid(base,  0f,    1.77f,  0f,    0.54f, 0.46f, 0.54f, PIG_R,   PIG_G,   PIG_B);
        // Snout (protrudes toward -Z = front of mob)
        cuboid(base,  0f,    1.67f, -0.30f, 0.28f, 0.20f, 0.12f, SNOUT_R, SNOUT_G, SNOUT_B);
        // Ears
        cuboid(base, -0.21f, 2.05f,  0f,    0.14f, 0.18f, 0.14f, PIG_R,   PIG_G,   PIG_B);
        cuboid(base, +0.21f, 2.05f,  0f,    0.14f, 0.18f, 0.14f, PIG_R,   PIG_G,   PIG_B);
        // Eyes
        cuboid(base, -0.14f, 1.83f, -0.28f, 0.08f, 0.08f, 0.03f, EYE_R,   EYE_G,   EYE_B);
        cuboid(base, +0.14f, 1.83f, -0.28f, 0.08f, 0.08f, 0.03f, EYE_R,   EYE_G,   EYE_B);
    }

    // --- Health bars ---

    private static final float BAR_W = 0.8f;
    private static final float BAR_H = 0.10f;

    private void renderHealthBar(Mob mob, Camera cam) {
        float ratio = mob.health() / mob.maxHealth();

        Vector3f pos = mob.position();
        float cx = pos.x;
        float cy = pos.y + mob.height() + 0.28f;
        float cz = pos.z;

        // Camera right vector (perpendicular to forward in XZ plane)
        float rx = -cam.forward.z, rz = cam.forward.x;
        float rLen = (float) Math.sqrt(rx * rx + rz * rz);
        if (rLen < 1e-6f) return;
        rx /= rLen; rz /= rLen;

        // Billboard matrix: col0=right, col1=worldUp, col2=forward, col3=translation
        Matrix4f bb = new Matrix4f(
            rx, 0f, rz, 0f,
            0f, 1f, 0f, 0f,
            cam.forward.x, cam.forward.y, cam.forward.z, 0f,
            cx, cy, cz, 1f
        );

        // Dark background
        draw(new Matrix4f(bb).scale(BAR_W, BAR_H, 0.01f), 0.10f, 0.10f, 0.10f);

        if (ratio <= 0f) return;
        float healthW = BAR_W * ratio;
        // Offset so the health fill starts from the left edge of the background bar
        float xOff = BAR_W * (ratio - 1f) / 2f;

        float hr, hg, hb;
        if      (ratio > 0.6f) { hr = 0.20f; hg = 0.80f; hb = 0.20f; }
        else if (ratio > 0.3f) { hr = 0.90f; hg = 0.75f; hb = 0.10f; }
        else                   { hr = 0.85f; hg = 0.15f; hb = 0.15f; }

        draw(new Matrix4f(bb).translate(xOff, 0f, -0.02f).scale(healthW, BAR_H * 0.75f, 0.001f), hr, hg, hb);
    }

    // --- Helpers ---

    private void cuboid(Matrix4f base, float x, float y, float z,
                        float sx, float sy, float sz,
                        float r, float g, float b) {
        draw(new Matrix4f(base).translate(x, y, z).scale(sx, sy, sz), r, g, b);
    }

    // Pivot-based rotation: the limb top is at (hx,hy,hz); it hangs sh/2 below the pivot.
    private void legCuboid(Matrix4f base, float hx, float hy, float hz,
                           float sw, float sh, float sd, float angle,
                           float r, float g, float b) {
        draw(new Matrix4f(base)
                .translate(hx, hy, hz)
                .rotateX(angle)
                .translate(0f, -sh / 2f, 0f)
                .scale(sw, sh, sd),
             r, g, b);
    }

    private void draw(Matrix4f model, float r, float g, float b) {
        shader.setMat4("uModel", model);
        float fr = r + (1f - r) * hurtFactor * 0.9f;
        float fg = g * (1f - hurtFactor * 0.75f);
        float fb = b * (1f - hurtFactor * 0.75f);
        shader.setVec4("uColor", fr, fg, fb, 1f);
        cube.draw();
    }

    private static float[] unitCube() {
        return new float[]{
            -0.5f,-0.5f, 0.5f,  0.5f,-0.5f, 0.5f,  0.5f, 0.5f, 0.5f,
            -0.5f,-0.5f, 0.5f,  0.5f, 0.5f, 0.5f, -0.5f, 0.5f, 0.5f,
             0.5f,-0.5f,-0.5f, -0.5f,-0.5f,-0.5f, -0.5f, 0.5f,-0.5f,
             0.5f,-0.5f,-0.5f, -0.5f, 0.5f,-0.5f,  0.5f, 0.5f,-0.5f,
            -0.5f,-0.5f,-0.5f, -0.5f,-0.5f, 0.5f, -0.5f, 0.5f, 0.5f,
            -0.5f,-0.5f,-0.5f, -0.5f, 0.5f, 0.5f, -0.5f, 0.5f,-0.5f,
             0.5f,-0.5f, 0.5f,  0.5f,-0.5f,-0.5f,  0.5f, 0.5f,-0.5f,
             0.5f,-0.5f, 0.5f,  0.5f, 0.5f,-0.5f,  0.5f, 0.5f, 0.5f,
            -0.5f, 0.5f, 0.5f,  0.5f, 0.5f, 0.5f,  0.5f, 0.5f,-0.5f,
            -0.5f, 0.5f, 0.5f,  0.5f, 0.5f,-0.5f, -0.5f, 0.5f,-0.5f,
            -0.5f,-0.5f,-0.5f,  0.5f,-0.5f,-0.5f,  0.5f,-0.5f, 0.5f,
            -0.5f,-0.5f,-0.5f,  0.5f,-0.5f, 0.5f, -0.5f,-0.5f, 0.5f
        };
    }
}
