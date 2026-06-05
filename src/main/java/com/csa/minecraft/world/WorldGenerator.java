package com.csa.minecraft.world;

/** Common interface for terrain generation, used by both overworld and nether. */
public interface WorldGenerator {
    void generate(Chunk c);
}
