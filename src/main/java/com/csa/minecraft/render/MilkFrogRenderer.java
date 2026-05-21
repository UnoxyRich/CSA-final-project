package com.csa.minecraft.render;

import com.csa.minecraft.engine.Camera;
import com.csa.minecraft.engine.Mesh;
import com.csa.minecraft.engine.Shader;
import com.csa.minecraft.entity.MilkFrog;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import static org.lwjgl.opengl.GL11.*;

public class MilkFrogRenderer {
    private final Shader shader;
    private final Mesh cube;
    private static final float YELLOW_R = 0.86f, YELLOW_G = 0.70f, YELLOW_B = 0.22f;
    private static final float SHADOW_YELLOW_R = 0.72f, SHADOW_YELLOW_G = 0.52f, SHADOW_YELLOW_B = 0.18f;
    private static final float BELLY_R = 0.86f, BELLY_G = 0.80f, BELLY_B = 0.66f;
    private static final float HAND_R = 0.36f, HAND_G = 0.33f, HAND_B = 0.27f;

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

    public MilkFrogRenderer() {
        shader = new Shader(VERT, FRAG);
        cube = new Mesh();
        cube.upload(unitCube(), new int[]{3});
    }

    public void render(MilkFrog frog, Camera cam) {
        if (frog == null) return;

        glDisable(GL_CULL_FACE);
        shader.use();
        shader.setMat4("uView", cam.view);
        shader.setMat4("uProj", cam.proj);

        Vector3f p = frog.position();
        float bob = Math.max(0f, (float) Math.sin(frog.hopTime())) * 0.06f;
        Matrix4f base = new Matrix4f()
            .translate(p.x, p.y + bob, p.z)
            .rotateY(frog.yaw());

        // Legs and chunky upright body.
        cuboid(base, -0.28f, 0.20f, 0.10f, 0.26f, 0.40f, 0.30f,
               SHADOW_YELLOW_R, SHADOW_YELLOW_G, SHADOW_YELLOW_B, 1f);
        cuboid(base,  0.28f, 0.20f, 0.10f, 0.26f, 0.40f, 0.30f,
               SHADOW_YELLOW_R, SHADOW_YELLOW_G, SHADOW_YELLOW_B, 1f);
        cuboid(base, 0f, 0.73f, 0.06f, 0.88f, 1.02f, 0.62f, YELLOW_R, YELLOW_G, YELLOW_B, 1f);
        cuboid(base, 0f, 0.64f, -0.29f, 0.58f, 0.68f, 0.045f, BELLY_R, BELLY_G, BELLY_B, 1f);

        // Rounded-looking head cap built from stacked blocks.
        cuboid(base, 0f, 1.36f, -0.10f, 0.78f, 0.44f, 0.52f, YELLOW_R, YELLOW_G, YELLOW_B, 1f);
        cuboid(base, 0f, 1.58f, -0.06f, 0.58f, 0.22f, 0.42f, YELLOW_R, YELLOW_G, YELLOW_B, 1f);

        // Closed laughing eyes.
        cuboid(base, -0.22f, 1.42f, -0.38f, 0.20f, 0.035f, 0.035f, 0.02f, 0.02f, 0.02f, 1f);
        cuboid(base,  0.22f, 1.42f, -0.38f, 0.20f, 0.035f, 0.035f, 0.02f, 0.02f, 0.02f, 1f);

        // Big black mouth with a small white tooth strip and dark red throat.
        cuboid(base, 0f, 1.24f, -0.40f, 0.42f, 0.24f, 0.045f, 0.02f, 0.01f, 0.012f, 1f);
        cuboid(base, 0f, 1.30f, -0.425f, 0.34f, 0.045f, 0.030f, 0.95f, 0.95f, 0.92f, 1f);
        cuboid(base, 0f, 1.18f, -0.43f, 0.22f, 0.08f, 0.030f, 0.16f, 0.02f, 0.04f, 1f);

        // Side arms and gray hands resting on the belly.
        cuboid(base, -0.52f, 0.80f, -0.04f, 0.18f, 0.56f, 0.24f,
               SHADOW_YELLOW_R, SHADOW_YELLOW_G, SHADOW_YELLOW_B, 1f);
        cuboid(base,  0.52f, 0.80f, -0.04f, 0.18f, 0.56f, 0.24f,
               SHADOW_YELLOW_R, SHADOW_YELLOW_G, SHADOW_YELLOW_B, 1f);
        cuboid(base, -0.22f, 0.46f, -0.36f, 0.22f, 0.16f, 0.10f, HAND_R, HAND_G, HAND_B, 1f);
        cuboid(base,  0.22f, 0.46f, -0.36f, 0.22f, 0.16f, 0.10f, HAND_R, HAND_G, HAND_B, 1f);
        cuboid(base, -0.08f, 0.38f, -0.38f, 0.08f, 0.14f, 0.08f, HAND_R, HAND_G, HAND_B, 1f);
        cuboid(base,  0.08f, 0.38f, -0.38f, 0.08f, 0.14f, 0.08f, HAND_R, HAND_G, HAND_B, 1f);
        glEnable(GL_CULL_FACE);
    }

    private void cuboid(Matrix4f base, float x, float y, float z,
                        float sx, float sy, float sz,
                        float r, float g, float b, float a) {
        Matrix4f model = new Matrix4f(base).translate(x, y, z).scale(sx, sy, sz);
        shader.setMat4("uModel", model);
        shader.setVec4("uColor", r, g, b, a);
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
