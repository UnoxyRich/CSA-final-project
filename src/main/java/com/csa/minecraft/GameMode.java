package com.csa.minecraft;

public enum GameMode {
    SURVIVAL,
    CREATIVE,
    SPECTATOR;

    public GameMode next() {
        GameMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
