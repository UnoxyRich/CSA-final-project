package com.csa.minecraft;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class NailongChatClient {
    private static final URI CHAT_URI = URI.create("https://api.deepseek.com/chat/completions");
    private static final String MODEL = "deepseek-v4-flash";

    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();
    private final List<Message> history = new ArrayList<>();

    public CompletableFuture<String> send(String apiKey, String userText) {
        if (apiKey == null || apiKey.isBlank()) {
            return CompletableFuture.completedFuture("SET DEEPSEEK API KEY IN SETTINGS FIRST");
        }
        String trimmed = userText == null ? "" : userText.trim();
        if (trimmed.isEmpty()) {
            return CompletableFuture.completedFuture("TYPE A MESSAGE FIRST");
        }

        history.add(new Message("user", trimmed));
        String body = requestBody();
        HttpRequest request = HttpRequest.newBuilder(CHAT_URI)
            .timeout(Duration.ofSeconds(35))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + apiKey.trim())
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(response -> {
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    String message = extractJsonString(response.body(), "message");
                    return message.isBlank()
                        ? "DEEPSEEK ERROR " + response.statusCode()
                        : "DEEPSEEK " + response.statusCode() + ": " + message;
                }
                String answer = extractContent(response.body());
                if (answer.isBlank()) answer = "NAILONG DID NOT ANSWER";
                history.add(new Message("assistant", answer));
                trimHistory();
                return answer;
            })
            .exceptionally(e -> "CHAT ERROR " + clean(e.getMessage()));
    }

    private String requestBody() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"model\":\"").append(MODEL).append("\",\"messages\":[");
        appendMessage(sb, "system",
            "You are Nailong, a friendly funny in-game companion. Keep replies short, playful, and useful.");
        for (Message m : history) appendMessage(sb.append(','), m.role, m.content);
        sb.append("],\"thinking\":{\"type\":\"disabled\"},\"temperature\":0.8,\"max_tokens\":160}");
        return sb.toString();
    }

    private static void appendMessage(StringBuilder sb, String role, String content) {
        sb.append("{\"role\":\"").append(role).append("\",\"content\":\"")
          .append(jsonEscape(content)).append("\"}");
    }

    private void trimHistory() {
        while (history.size() > 12) history.remove(0);
    }

    private static String extractContent(String json) {
        return extractJsonString(json, "content");
    }

    private static String extractJsonString(String json, String field) {
        String key = "\"" + field + "\"";
        int keyPos = json.indexOf(key);
        if (keyPos < 0) return "";
        int colon = json.indexOf(':', keyPos + key.length());
        if (colon < 0) return "";
        int start = json.indexOf('"', colon + 1);
        if (start < 0) return "";
        StringBuilder out = new StringBuilder();
        boolean esc = false;
        for (int i = start + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (esc) {
                switch (c) {
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case '"', '\\', '/' -> out.append(c);
                    default -> out.append(c);
                }
                esc = false;
            } else if (c == '\\') {
                esc = true;
            } else if (c == '"') {
                break;
            } else {
                out.append(c);
            }
        }
        return clean(out.toString());
    }

    private static String clean(String text) {
        if (text == null) return "";
        return text.replaceAll("\\s+", " ").trim();
    }

    private static String jsonEscape(String s) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    private record Message(String role, String content) {}
}
