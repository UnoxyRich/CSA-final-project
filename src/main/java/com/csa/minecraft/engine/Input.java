package com.csa.minecraft.engine;

import static org.lwjgl.glfw.GLFW.*;

public class Input {
    private final long window;
    private final StringBuilder typedChars = new StringBuilder();
    private double lastX, lastY;
    private double curX, curY;
    private float dx, dy;
    private boolean firstMouse = true;
    private boolean cursorGrabbed = false;
    private boolean[] prevKeys = new boolean[GLFW_KEY_LAST + 1];
    private boolean[] curKeys = new boolean[GLFW_KEY_LAST + 1];
    private boolean prevLeft, prevRight, curLeft, curRight;

    protected Input() {
        this.window = 0;
    }

    public Input(long window) {
        this.window = window;
        grabCursor(true);
        glfwSetCharCallback(window, (w, codepoint) -> {
            if (codepoint >= 32 && codepoint < 127) typedChars.append((char) codepoint);
        });
        // No key/mouse-button callbacks here: the menu state machine in Main owns the
        // cursor-grab transitions. Keys/buttons are polled in poll() below.
    }

    public void grabCursor(boolean grab) {
        cursorGrabbed = grab;
        glfwSetInputMode(window, GLFW_CURSOR, grab ? GLFW_CURSOR_DISABLED : GLFW_CURSOR_NORMAL);
        firstMouse = true;
    }
    public boolean cursorGrabbed() { return cursorGrabbed; }

    public void poll() {
        System.arraycopy(curKeys, 0, prevKeys, 0, curKeys.length);
        for (int k = 32; k <= GLFW_KEY_LAST; k++) {
            try { curKeys[k] = glfwGetKey(window, k) == GLFW_PRESS; } catch (Exception ignored) {}
        }
        prevLeft = curLeft; prevRight = curRight;
        curLeft = glfwGetMouseButton(window, GLFW_MOUSE_BUTTON_LEFT) == GLFW_PRESS;
        curRight = glfwGetMouseButton(window, GLFW_MOUSE_BUTTON_RIGHT) == GLFW_PRESS;

        double[] xp = new double[1], yp = new double[1];
        glfwGetCursorPos(window, xp, yp);
        double rawX = xp[0], rawY = yp[0];
        // GLFW returns the cursor in *logical* (screen) coordinates, but our viewport and
        // HUD/menu layout use *framebuffer* pixels. On macOS Retina these differ by 2x —
        // if we don't rescale, click hit-tests miss the visible widgets. Mouse-look dx/dy
        // is kept in raw coords so sensitivity feel doesn't change with window DPI.
        int[] ww = new int[1], wh = new int[1];
        int[] fbw = new int[1], fbh = new int[1];
        glfwGetWindowSize(window, ww, wh);
        glfwGetFramebufferSize(window, fbw, fbh);
        double sx = ww[0] > 0 ? (double) fbw[0] / ww[0] : 1.0;
        double sy = wh[0] > 0 ? (double) fbh[0] / wh[0] : 1.0;
        curX = rawX * sx;
        curY = rawY * sy;
        if (firstMouse) { lastX = rawX; lastY = rawY; firstMouse = false; dx = 0; dy = 0; }
        else { dx = (float)(rawX - lastX); dy = (float)(rawY - lastY); lastX = rawX; lastY = rawY; }
        if (!cursorGrabbed) { dx = 0; dy = 0; }
    }

    public boolean key(int k) { return curKeys[k]; }
    public boolean keyPressed(int k) { return curKeys[k] && !prevKeys[k]; }
    public boolean leftClick() { return curLeft && !prevLeft; }
    public boolean rightClick() { return curRight && !prevRight; }
    public boolean leftDown() { return curLeft; }
    public float mouseDX() { return dx; }
    public float mouseDY() { return dy; }
    public double cursorX() { return curX; }
    public double cursorY() { return curY; }

    public String consumeTypedChars() {
        String s = typedChars.toString();
        typedChars.setLength(0);
        return s;
    }

    public void clearTypedChars() {
        typedChars.setLength(0);
    }
}
