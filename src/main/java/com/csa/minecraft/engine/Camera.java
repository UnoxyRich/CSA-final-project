package com.csa.minecraft.engine;

import org.joml.Matrix4f;
import org.joml.Vector3f;

public class Camera {
    public final Vector3f pos;
    public final Vector3f forward;
    public final Vector3f up = new Vector3f(0, 1, 0);
    public final Matrix4f view = new Matrix4f();
    public final Matrix4f proj = new Matrix4f();

    public Camera(Vector3f pos, Vector3f forward, float fovDeg, float aspect, float near, float far) {
        this.pos = pos; this.forward = forward;
        view.identity().lookAt(pos, new Vector3f(pos).add(forward), up);
        proj.identity().perspective((float) Math.toRadians(fovDeg), aspect, near, far);
    }
}
