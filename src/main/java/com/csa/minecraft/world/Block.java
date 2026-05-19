package com.csa.minecraft.world;

public enum Block {
    AIR(false), GRASS(true), DIRT(true), STONE(true),
    WOOD(true), LEAVES(true), SAND(true), PLANKS(true), GLASS(true);

    public final boolean solid;
    Block(boolean s) { this.solid = s; }

    public static Block byId(int id) {
        Block[] vs = values();
        return (id >= 0 && id < vs.length) ? vs[id] : AIR;
    }

    /** Returns atlas tile index for face: 0=top,1=bottom,2=side. */
    public int atlasIndex(int face) {
        switch (this) {
            case DIRT:   return 0;
            case GRASS:  return face == 0 ? 1 : (face == 1 ? 0 : 2);
            case STONE:  return 3;
            case WOOD:   return face <= 1 ? 5 : 4;
            case LEAVES: return 6;
            case SAND:   return 7;
            case PLANKS: return 8;
            case GLASS:  return 9;
            default:     return 3;
        }
    }
}
