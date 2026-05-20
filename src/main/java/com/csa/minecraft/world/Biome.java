package com.csa.minecraft.world;

/**
 * Coarse biome categories. {@link TerrainGenerator#biomeAt} picks one per column
 * from temperature/humidity/elevation noise; the generator then uses it to choose
 * surface blocks and decoration. Biomes carry no state — they are a classification.
 */
public enum Biome {
    OCEAN,
    PLAINS,
    FOREST,
    DESERT,
    SAVANNA,
    TAIGA,
    SNOWY_TUNDRA,
    CHERRY_MOUNTAINS
}
