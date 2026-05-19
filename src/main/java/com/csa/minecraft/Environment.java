package com.csa.minecraft;

import org.joml.Vector3f;

public class Environment {
    public enum Weather {
        CLEAR, RAIN, THUNDER
    }

    private Weather weather = Weather.CLEAR;
    private String status = "WEATHER: CLEAR";
    private float statusTimer = 3f;

    public Weather weather() {
        return weather;
    }

    public float rainStrength() {
        return weather == Weather.CLEAR ? 0f : (weather == Weather.RAIN ? 0.72f : 1f);
    }

    public float thunderStrength() {
        return weather == Weather.THUNDER ? 1f : 0f;
    }

    public float wetness() {
        return weather == Weather.CLEAR ? 0f : (weather == Weather.RAIN ? 0.62f : 0.86f);
    }

    public Vector3f fogColor() {
        return switch (weather) {
            case CLEAR -> new Vector3f(0.70f, 0.84f, 1.00f);
            case RAIN -> new Vector3f(0.48f, 0.56f, 0.64f);
            case THUNDER -> new Vector3f(0.31f, 0.34f, 0.40f);
        };
    }

    public Vector3f skyTop() {
        return switch (weather) {
            case CLEAR -> new Vector3f(0.42f, 0.66f, 0.98f);
            case RAIN -> new Vector3f(0.35f, 0.43f, 0.52f);
            case THUNDER -> new Vector3f(0.16f, 0.18f, 0.23f);
        };
    }

    public Vector3f skyHorizon() {
        return switch (weather) {
            case CLEAR -> new Vector3f(0.84f, 0.92f, 1.00f);
            case RAIN -> new Vector3f(0.58f, 0.63f, 0.68f);
            case THUNDER -> new Vector3f(0.36f, 0.38f, 0.43f);
        };
    }

    public Vector3f ambient() {
        return switch (weather) {
            case CLEAR -> new Vector3f(0.46f, 0.50f, 0.57f);
            case RAIN -> new Vector3f(0.34f, 0.37f, 0.43f);
            case THUNDER -> new Vector3f(0.26f, 0.28f, 0.34f);
        };
    }

    public Vector3f sunColor() {
        return switch (weather) {
            case CLEAR -> new Vector3f(1.02f, 0.96f, 0.82f);
            case RAIN -> new Vector3f(0.68f, 0.70f, 0.72f);
            case THUNDER -> new Vector3f(0.43f, 0.45f, 0.50f);
        };
    }

    public float sunIntensity() {
        return switch (weather) {
            case CLEAR -> 1.08f;
            case RAIN -> 0.52f;
            case THUNDER -> 0.24f;
        };
    }

    public boolean applyCommand(String raw) {
        String command = raw.trim().toLowerCase();
        if (command.startsWith("/")) command = command.substring(1).trim();
        String[] parts = command.split("\\s+");
        if (parts.length >= 2 && parts[0].equals("weather")) {
            return setWeather(parts[1]);
        }
        flash("UNKNOWN COMMAND");
        return false;
    }

    private boolean setWeather(String name) {
        switch (name) {
            case "clear", "sun", "sunny" -> weather = Weather.CLEAR;
            case "rain", "rainy" -> weather = Weather.RAIN;
            case "thunder", "storm" -> weather = Weather.THUNDER;
            default -> {
                flash("USE /WEATHER CLEAR RAIN OR THUNDER");
                return false;
            }
        }
        flash("WEATHER: " + weather.name());
        return true;
    }

    public void update(float dt) {
        statusTimer = Math.max(0f, statusTimer - dt);
    }

    public String status() {
        return statusTimer > 0f ? status : "";
    }

    private void flash(String text) {
        status = text;
        statusTimer = 4f;
    }
}
