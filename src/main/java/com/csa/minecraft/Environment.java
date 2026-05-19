package com.csa.minecraft;

import org.joml.Vector3f;

public class Environment {
    public enum Weather {
        CLEAR, RAIN, THUNDER
    }

    public enum TimePhase {
        SUNRISE(0),
        NOON(6000),
        SUNSET(12000),
        NIGHT(14000),
        MIDNIGHT(18000),
        DAWN(23000);

        final int ticks;

        TimePhase(int ticks) {
            this.ticks = ticks;
        }
    }

    private static final float TWO_PI = (float) (Math.PI * 2.0);
    private static final float DAY_TICKS = 24000f;
    private static final float DAY_LENGTH_SECONDS = 480f;

    private Weather weather = Weather.CLEAR;
    private float dayTime = TimePhase.NOON.ticks / DAY_TICKS;
    private boolean daylightCycle = true;
    private String status = "WEATHER: CLEAR";
    private float statusTimer = 3f;

    public Weather weather() {
        return weather;
    }

    public boolean daylightCycle() {
        return daylightCycle;
    }

    public int timeTicks() {
        int ticks = Math.round(dayTime * DAY_TICKS) % (int) DAY_TICKS;
        return ticks < 0 ? ticks + (int) DAY_TICKS : ticks;
    }

    public String phaseName() {
        TimePhase nearest = TimePhase.SUNRISE;
        float best = Float.MAX_VALUE;
        for (TimePhase phase : TimePhase.values()) {
            float phaseTime = phase.ticks / DAY_TICKS;
            float d = circularDistance(dayTime, phaseTime);
            if (d < best) {
                best = d;
                nearest = phase;
            }
        }
        return nearest.name();
    }

    public Vector3f sunDirection() {
        float a = dayTime * TWO_PI;
        float x = (float) Math.cos(a) * 0.92f;
        float y = (float) Math.sin(a);
        return new Vector3f(x, y, 0.22f).normalize();
    }

    public Vector3f moonDirection() {
        Vector3f sun = sunDirection();
        return new Vector3f(-sun.x, -sun.y, -sun.z).normalize();
    }

    public Vector3f lightDirection() {
        return daylight() > 0.12f || twilight() > 0.45f ? sunDirection() : moonDirection();
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

    public float nightAmount() {
        return night();
    }

    public float sunDiscStrength() {
        return smoothstep(-0.12f, 0.18f, sunDirection().y);
    }

    public float moonDiscStrength() {
        return smoothstep(-0.10f, 0.16f, moonDirection().y) * (0.35f + 0.65f * night());
    }

    public Vector3f fogColor() {
        float day = daylight();
        float dusk = twilight();
        Vector3f clear = mix(new Vector3f(0.035f, 0.045f, 0.090f),
                             new Vector3f(0.70f, 0.84f, 1.00f), day);
        clear = mix(clear, new Vector3f(0.86f, 0.54f, 0.38f), dusk * 0.55f);
        return weatherFog(clear);
    }

    public Vector3f skyTop() {
        float day = daylight();
        float dusk = twilight();
        Vector3f clear = mix(new Vector3f(0.015f, 0.025f, 0.075f),
                             new Vector3f(0.42f, 0.66f, 0.98f), day);
        clear = mix(clear, new Vector3f(0.22f, 0.34f, 0.72f), dusk * 0.70f);
        return weatherSky(clear);
    }

    public Vector3f skyHorizon() {
        float day = daylight();
        float dusk = twilight();
        Vector3f clear = mix(new Vector3f(0.055f, 0.070f, 0.135f),
                             new Vector3f(0.84f, 0.92f, 1.00f), day);
        clear = mix(clear, new Vector3f(1.00f, 0.56f, 0.27f), dusk * 0.82f);
        return weatherSky(clear);
    }

    public Vector3f ambient() {
        float day = daylight();
        float dusk = twilight();
        Vector3f clear = mix(new Vector3f(0.105f, 0.120f, 0.175f),
                             new Vector3f(0.46f, 0.50f, 0.57f), day);
        clear = mix(clear, new Vector3f(0.31f, 0.30f, 0.36f), dusk * 0.55f);
        return switch (weather) {
            case CLEAR -> clear;
            case RAIN -> multiply(clear, 0.76f);
            case THUNDER -> multiply(clear, 0.52f);
        };
    }

    public Vector3f sunColor() {
        float day = daylight();
        float dusk = twilight();
        Vector3f clear = mix(new Vector3f(0.46f, 0.53f, 0.78f),
                             new Vector3f(1.02f, 0.96f, 0.82f), day);
        clear = mix(clear, new Vector3f(1.18f, 0.62f, 0.34f), dusk * 0.86f);
        return switch (weather) {
            case CLEAR -> clear;
            case RAIN -> multiply(mix(clear, new Vector3f(0.68f, 0.70f, 0.72f), 0.55f), 0.82f);
            case THUNDER -> multiply(mix(clear, new Vector3f(0.43f, 0.45f, 0.50f), 0.72f), 0.62f);
        };
    }

    public float sunIntensity() {
        float clear = daylight() * 1.08f + twilight() * 0.34f + night() * 0.18f;
        return switch (weather) {
            case CLEAR -> clear;
            case RAIN -> clear * 0.52f;
            case THUNDER -> clear * 0.24f;
        };
    }

    public boolean applyCommand(String raw) {
        String command = raw.trim().toLowerCase();
        if (command.startsWith("/")) command = command.substring(1).trim();
        String[] parts = command.split("\\s+");
        if (parts.length >= 2 && parts[0].equals("weather")) {
            return setWeather(parts[1]);
        }
        if (parts.length >= 2 && parts[0].equals("time")) {
            return applyTimeCommand(parts);
        }
        if (parts.length >= 3 && parts[0].equals("gamerule") && parts[1].equals("dodaylightcycle")) {
            return setDaylightCycle(parts[2]);
        }
        if (parts.length >= 2 && parts[0].equals("daylightcycle")) {
            return setDaylightCycle(parts[1]);
        }
        flash("UNKNOWN COMMAND");
        return false;
    }

    private boolean applyTimeCommand(String[] parts) {
        if (parts.length >= 3 && parts[1].equals("set")) {
            return setTime(parts[2]);
        }
        if (parts.length >= 3 && parts[1].equals("add")) {
            try {
                addTicks(Float.parseFloat(parts[2]));
                flashTime();
                return true;
            } catch (NumberFormatException e) {
                flash("USE /TIME ADD <TICKS>");
                return false;
            }
        }
        if (parts.length >= 2 && parts[1].equals("query")) {
            flashTime();
            return true;
        }
        flash("USE /TIME SET NOON DAWN MIDNIGHT SUNSET SUNRISE NIGHT");
        return false;
    }

    private boolean setTime(String name) {
        switch (name) {
            case "sunrise", "day" -> setTicks(TimePhase.SUNRISE.ticks);
            case "noon", "midday" -> setTicks(TimePhase.NOON.ticks);
            case "sunset", "dusk" -> setTicks(TimePhase.SUNSET.ticks);
            case "night" -> setTicks(TimePhase.NIGHT.ticks);
            case "midnight" -> setTicks(TimePhase.MIDNIGHT.ticks);
            case "dawn" -> setTicks(TimePhase.DAWN.ticks);
            default -> {
                try {
                    setTicks(Float.parseFloat(name));
                } catch (NumberFormatException e) {
                    flash("USE /TIME SET NOON DAWN MIDNIGHT SUNSET SUNRISE NIGHT");
                    return false;
                }
            }
        }
        flashTime();
        return true;
    }

    private boolean setDaylightCycle(String value) {
        switch (value) {
            case "true", "on", "1" -> daylightCycle = true;
            case "false", "off", "0" -> daylightCycle = false;
            default -> {
                flash("USE /GAMERULE DODAYLIGHTCYCLE TRUE OR FALSE");
                return false;
            }
        }
        flash("DAYLIGHT CYCLE: " + (daylightCycle ? "ON" : "OFF"));
        return true;
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
        if (daylightCycle) {
            dayTime = wrap(dayTime + dt / DAY_LENGTH_SECONDS);
        }
    }

    public String status() {
        return statusTimer > 0f ? status : "";
    }

    private void flashTime() {
        flash("TIME: " + phaseName() + " " + timeTicks());
    }

    private void flash(String text) {
        status = text;
        statusTimer = 4f;
    }

    private void setTicks(float ticks) {
        dayTime = wrap(ticks / DAY_TICKS);
    }

    private void addTicks(float ticks) {
        dayTime = wrap(dayTime + ticks / DAY_TICKS);
    }

    private float daylight() {
        return smoothstep(-0.08f, 0.42f, sunDirection().y);
    }

    private float night() {
        return 1f - smoothstep(-0.32f, 0.10f, sunDirection().y);
    }

    private float twilight() {
        float horizon = 1f - clamp(Math.abs(sunDirection().y) / 0.42f);
        return clamp(horizon) * (0.55f + 0.45f * (1f - night()));
    }

    private Vector3f weatherSky(Vector3f clear) {
        return switch (weather) {
            case CLEAR -> clear;
            case RAIN -> mix(multiply(desaturate(clear, 0.40f), 0.72f),
                             new Vector3f(0.30f, 0.36f, 0.44f), 0.14f);
            case THUNDER -> mix(multiply(desaturate(clear, 0.60f), 0.45f),
                                new Vector3f(0.09f, 0.10f, 0.14f), 0.18f);
        };
    }

    private Vector3f weatherFog(Vector3f clear) {
        return switch (weather) {
            case CLEAR -> clear;
            case RAIN -> mix(multiply(desaturate(clear, 0.35f), 0.76f),
                             new Vector3f(0.42f, 0.49f, 0.57f), 0.22f);
            case THUNDER -> mix(multiply(desaturate(clear, 0.60f), 0.50f),
                                new Vector3f(0.18f, 0.20f, 0.25f), 0.26f);
        };
    }

    private static Vector3f desaturate(Vector3f c, float amount) {
        float luma = c.x * 0.2126f + c.y * 0.7152f + c.z * 0.0722f;
        return mix(c, new Vector3f(luma, luma, luma), amount);
    }

    private static Vector3f mix(Vector3f a, Vector3f b, float t) {
        float u = clamp(t);
        return new Vector3f(
            a.x + (b.x - a.x) * u,
            a.y + (b.y - a.y) * u,
            a.z + (b.z - a.z) * u
        );
    }

    private static Vector3f multiply(Vector3f c, float scalar) {
        return new Vector3f(c.x * scalar, c.y * scalar, c.z * scalar);
    }

    private static float circularDistance(float a, float b) {
        float d = Math.abs(a - b);
        return Math.min(d, 1f - d);
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = clamp((x - edge0) / (edge1 - edge0));
        return t * t * (3f - 2f * t);
    }

    private static float wrap(float value) {
        float out = value % 1f;
        return out < 0f ? out + 1f : out;
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
