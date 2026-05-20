package com.csa.minecraft.world;

public enum Block {
    AIR(false), GRASS(true), DIRT(true), STONE(true),
    WOOD(true), LEAVES(true), SAND(true), PLANKS(true), GLASS(true),
    CHERRY_WOOD(true), CHERRY_LEAVES(true), WATER(false),
    // Biome blocks appended after WATER so existing ordinals (and the
    // hard-coded water=11/glass=8/leaves=5 ids in the world shader) are stable.
    SNOW(true), ICE(true), CACTUS(true), SANDSTONE(true),
    SPRUCE_WOOD(true), SPRUCE_LEAVES(true), DRY_GRASS(true);

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
            case CHERRY_WOOD:   return face <= 1 ? 11 : 10;
            case CHERRY_LEAVES: return 12;
            case WATER: return 13;
            case SNOW:   return 14;
            case ICE:    return 15;
            case CACTUS: return face <= 1 ? 16 : 17;
            case SANDSTONE: return 18;
            case SPRUCE_WOOD:   return face <= 1 ? 20 : 19;
            case SPRUCE_LEAVES: return 21;
            case DRY_GRASS:     return face == 0 ? 22 : (face == 1 ? 0 : 23);
            default:     return 3;
        }
    }
}
