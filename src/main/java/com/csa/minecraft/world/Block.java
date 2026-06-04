package com.csa.minecraft.world;

public enum Block {
    AIR(false), GRASS(true), DIRT(true), STONE(true),
    WOOD(true), LEAVES(true), SAND(true), PLANKS(true), GLASS(true),
    CHERRY_WOOD(true), CHERRY_LEAVES(true), WATER(false),
    // Biome blocks appended after WATER so existing ordinals (and the
    // hard-coded water=11/glass=8/leaves=5 ids in the world shader) are stable.
    SNOW(true), ICE(true), CACTUS(true), SANDSTONE(true),
    SPRUCE_WOOD(true), SPRUCE_LEAVES(true), DRY_GRASS(true),
    ROCKET(true),
    // Non-placeable crafting items (solid=false, isItem()=true)
    STICK(false), SWORD(false), PICKAXE(false), AXE(false), SHOVEL(false);

    public final boolean solid;
    Block(boolean s) { this.solid = s; }

    /** True for items that exist only as inventory/crafting ingredients and cannot be placed. */
    public boolean isItem() {
        return this == STICK || this == SWORD || this == PICKAXE
            || this == AXE   || this == SHOVEL;
    }

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
            case ROCKET:        return face == 1 ? 25 : 24;
            case STICK:    return 26;
            case SWORD:    return 27;
            case PICKAXE:  return 28;
            case AXE:      return 29;
            case SHOVEL:   return 30;
            default:     return 3;
        }
    }
}
