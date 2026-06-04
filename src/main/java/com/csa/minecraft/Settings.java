package com.csa.minecraft;

public class Settings {
    public float mouseSensitivity = 0.15f;
    public boolean invertY = false;
    public float fov = 70f;
    public int renderDistance = 4;
    public float guiScale = 1.0f;
    public GameMode gameMode = GameMode.SURVIVAL;
    public boolean rayTracingLighting = false;
    public String deepSeekApiKey = "";

    public static final float SENS_MIN = 0.05f, SENS_MAX = 0.50f;
    public static final float FOV_MIN  = 50f,   FOV_MAX  = 110f;
    public static final int   DIST_MIN = 2,     DIST_MAX = 640;
    public static final float GUI_MIN  = 0.5f,  GUI_MAX  = 2.0f;
}
