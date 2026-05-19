package com.csa.minecraft.engine;

import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.NULL;

public class Window {
    private final String title;
    private int width, height;
    private long handle;

    public Window(String title, int w, int h) {
        this.title = title; this.width = w; this.height = h;
    }

    public void init() {
        if (!glfwInit()) throw new IllegalStateException("GLFW init failed");
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);

        handle = glfwCreateWindow(width, height, title, NULL, NULL);
        if (handle == NULL) throw new RuntimeException("window create failed");

        GLFWVidMode vid = glfwGetVideoMode(glfwGetPrimaryMonitor());
        if (vid != null) glfwSetWindowPos(handle, (vid.width()-width)/2, (vid.height()-height)/2);

        glfwSetFramebufferSizeCallback(handle, (w, nw, nh) -> {
            width = Math.max(1, nw);
            height = Math.max(1, nh);
        });

        glfwMakeContextCurrent(handle);
        glfwSwapInterval(1);
        glfwShowWindow(handle);
        GL.createCapabilities();
        refreshFramebufferSize();
    }

    public boolean shouldClose() { return glfwWindowShouldClose(handle); }
    public void swap() {
        glfwSwapBuffers(handle);
        glfwPollEvents();
        refreshFramebufferSize();
    }
    public void destroy() { glfwDestroyWindow(handle); glfwTerminate(); }
    public long handle() { return handle; }
    public int width() { return width; }
    public int height() { return height; }
    public float aspect() { return (float) width / Math.max(1, height); }
    public void setTitle(String t) { glfwSetWindowTitle(handle, t); }

    private void refreshFramebufferSize() {
        int[] fbw = new int[1], fbh = new int[1];
        glfwGetFramebufferSize(handle, fbw, fbh);
        width = Math.max(1, fbw[0]);
        height = Math.max(1, fbh[0]);
    }
}
