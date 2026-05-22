package com.csa.minecraft.render;

import com.csa.minecraft.NailongChatClient;
import com.csa.minecraft.Settings;
import com.csa.minecraft.engine.Input;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.lwjgl.glfw.GLFW.*;

public class ChatOverlay {
    private final Settings settings;
    private final NailongChatClient client = new NailongChatClient();
    private final List<String> messages = new ArrayList<>();
    private boolean open = false;
    private boolean waiting = false;
    private String draft = "";
    private CompletableFuture<String> pending;

    public ChatOverlay(Settings settings) {
        this.settings = settings;
    }

    public boolean isOpen() { return open; }

    public void open(Input input) {
        open = true;
        input.clearTypedChars();
    }

    public void close() {
        open = false;
        draft = "";
    }

    public void update(Input input) {
        if (!open) return;
        pollPending();

        String chars = input.consumeTypedChars();
        for (int i = 0; i < chars.length(); i++) {
            char c = chars.charAt(i);
            if (c >= 32 && c < 127 && draft.length() < 120) draft += c;
        }
        if (input.keyPressed(GLFW_KEY_BACKSPACE) && !draft.isEmpty()) {
            draft = draft.substring(0, draft.length() - 1);
        }
        if (input.keyPressed(GLFW_KEY_ESCAPE)) {
            close();
        } else if ((input.keyPressed(GLFW_KEY_ENTER) || input.keyPressed(GLFW_KEY_KP_ENTER)) && !waiting) {
            send();
        }
    }

    public void render(HudRenderer hud, int viewW, int viewH) {
        if (!open) return;
        pollPending();

        hud.begin2D(viewW, viewH);
        float gui = Math.max(0.1f, settings.guiScale);
        float panelW = Math.min(viewW - 40f * gui, 620f * gui);
        float panelH = 170f * gui;
        float x = 20f * gui;
        float y = viewH - panelH - 24f * gui;
        float scale = Math.max(1f, 2f * gui);

        hud.rectPx(x, y, panelW, panelH, 0.02f, 0.025f, 0.03f, 0.82f);
        hud.rectPx(x, y, panelW, 2f * gui, 0.55f, 0.82f, 1f, 0.95f);
        hud.drawText("NAILONG CHAT", x + 10f * gui, y + 10f * gui, scale, 1f, 1f, 1f, 1f);

        int lines = 0;
        int start = Math.max(0, messages.size() - 5);
        for (int i = start; i < messages.size(); i++) {
            hud.drawText(sanitize(messages.get(i), 48), x + 10f * gui,
                         y + (34f + lines * 18f) * gui, scale,
                         i % 2 == 0 ? 0.75f : 1f, 1f, i % 2 == 0 ? 0.85f : 0.75f, 1f);
            lines++;
        }

        String prompt = waiting ? "WAITING..." : "YOU: " + draft;
        hud.rectPx(x + 8f * gui, y + panelH - 32f * gui, panelW - 16f * gui, 24f * gui,
                   0.0f, 0.0f, 0.0f, 0.45f);
        hud.drawText(sanitize(prompt, 58), x + 14f * gui, y + panelH - 25f * gui,
                     scale, 1f, 1f, 1f, 1f);
        hud.end2D();
    }

    private void send() {
        String text = draft.trim();
        if (text.isEmpty()) return;
        messages.add("YOU: " + text);
        draft = "";
        waiting = true;
        pending = client.send(settings.deepSeekApiKey, text);
    }

    private void pollPending() {
        if (pending != null && pending.isDone()) {
            messages.add("NAILONG: " + pending.join());
            pending = null;
            waiting = false;
            while (messages.size() > 24) messages.remove(0);
        }
    }

    private static String sanitize(String text, int max) {
        String upper = text.toUpperCase().replaceAll("[^A-Z0-9 .:/+%-]", " ");
        return upper.length() <= max ? upper : upper.substring(0, max - 3) + "...";
    }
}
