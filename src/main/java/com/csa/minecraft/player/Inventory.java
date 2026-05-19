package com.csa.minecraft.player;

import com.csa.minecraft.world.Block;

public class Inventory {
    public final Block[] hotbar = {
        Block.GRASS, Block.DIRT, Block.STONE, Block.WOOD,
        Block.LEAVES, Block.SAND, Block.PLANKS, Block.GLASS, Block.WATER
    };
    public int selected = 0;
    public Block selectedBlock() { return hotbar[selected]; }
}
