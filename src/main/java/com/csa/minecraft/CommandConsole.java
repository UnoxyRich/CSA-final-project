package com.csa.minecraft;

import com.csa.minecraft.engine.Input;

import static org.lwjgl.glfw.GLFW.*;

public class CommandConsole {
    private final StringBuilder text = new StringBuilder();
    private boolean active;
    private String submitted;

    public boolean active() {
        return active;
    }

    public void open(Input input) {
        active = true;
        text.setLength(0);
        text.append('/');
        input.clearTypedChars();
    }

    public void close() {
        active = false;
        text.setLength(0);
    }

    public void update(Input input) {
        submitted = null;
        if (!active) return;

        String chars = input.consumeTypedChars();
        for (int i = 0; i < chars.length(); i++) {
            char c = chars.charAt(i);
            if (c >= 32 && c < 127 && text.length() < 80) text.append(c);
        }
        if (input.keyPressed(GLFW_KEY_BACKSPACE) && text.length() > 1) {
            text.deleteCharAt(text.length() - 1);
        }
        if (input.keyPressed(GLFW_KEY_ENTER) || input.keyPressed(GLFW_KEY_KP_ENTER)) {
            submitted = text.toString();
            close();
        } else if (input.keyPressed(GLFW_KEY_ESCAPE)) {
            close();
        }
    }

    public String consumeSubmitted() {
        String out = submitted;
        submitted = null;
        return out;
    }

    public String text() {
        return text.toString();
    }
}
