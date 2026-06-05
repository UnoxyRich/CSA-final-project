package com.csa.minecraft.world;

/**
 * Detects a completed 4x5 nether-portal frame (obsidian border, air/portal
 * interior) in either the XY plane (Z-facing) or ZY plane (X-facing).
 * When a valid frame is found its interior is filled with NETHER_PORTAL blocks.
 */
public class PortalHelper {

    /**
     * Called from World.setBlock() when an obsidian block is placed.
     * Checks all possible 4x5 frame positions that include (wx,wy,wz).
     * @return true if a portal was activated
     */
    public static boolean checkAndActivate(World world, int wx, int wy, int wz) {
        return tryPortalXY(world, wx, wy, wz) || tryPortalZY(world, wx, wy, wz);
    }

    // --- XY-plane portal (frame extends along X and Y; z=wz is constant) ---

    private static boolean tryPortalXY(World world, int wx, int wy, int wz) {
        // The placed block can be anywhere in the 4-wide x 5-tall outer frame.
        // Try every possible bottom-left corner that includes (wx, wy).
        for (int offX = 0; offX <= 3; offX++) {
            for (int offY = 0; offY <= 4; offY++) {
                int cx = wx - offX;
                int cy = wy - offY;
                if (isValidFrameXY(world, cx, cy, wz)) {
                    fillFrameXY(world, cx, cy, wz);
                    return true;
                }
            }
        }
        return false;
    }

    // Corners are OPTIONAL (matching Minecraft behaviour).
    // Only the 10 non-corner border blocks are required to be obsidian.
    private static boolean isValidFrameXY(World world, int cx, int cy, int cz) {
        // Inner bottom (y=cy): only the 2 non-corner blocks
        if (!isObs(world, cx + 1, cy,     cz)) return false;
        if (!isObs(world, cx + 2, cy,     cz)) return false;
        // Inner top (y=cy+4)
        if (!isObs(world, cx + 1, cy + 4, cz)) return false;
        if (!isObs(world, cx + 2, cy + 4, cz)) return false;
        // Left col (x=cx) and right col (x=cx+3): all 3 middle rows
        for (int y = cy + 1; y <= cy + 3; y++) {
            if (!isObs(world, cx,     y, cz)) return false;
            if (!isObs(world, cx + 3, y, cz)) return false;
        }
        // Interior (2x3): must all be air or already nether portal
        for (int x = cx + 1; x <= cx + 2; x++) {
            for (int y = cy + 1; y <= cy + 3; y++) {
                Block b = world.getBlock(x, y, cz);
                if (b != Block.AIR && b != Block.NETHER_PORTAL) return false;
            }
        }
        return true;
    }

    private static void fillFrameXY(World world, int cx, int cy, int cz) {
        for (int x = cx + 1; x <= cx + 2; x++)
            for (int y = cy + 1; y <= cy + 3; y++)
                world.setBlock(x, y, cz, Block.NETHER_PORTAL);
    }

    // --- ZY-plane portal (frame extends along Z and Y; x=wx is constant) ---

    private static boolean tryPortalZY(World world, int wx, int wy, int wz) {
        for (int offZ = 0; offZ <= 3; offZ++) {
            for (int offY = 0; offY <= 4; offY++) {
                int cz = wz - offZ;
                int cy = wy - offY;
                if (isValidFrameZY(world, wx, cy, cz)) {
                    fillFrameZY(world, wx, cy, cz);
                    return true;
                }
            }
        }
        return false;
    }

    // Corners optional for the ZY-plane frame too.
    private static boolean isValidFrameZY(World world, int cx, int cy, int cz) {
        // Inner bottom and top (only non-corner z positions)
        if (!isObs(world, cx, cy,     cz + 1)) return false;
        if (!isObs(world, cx, cy,     cz + 2)) return false;
        if (!isObs(world, cx, cy + 4, cz + 1)) return false;
        if (!isObs(world, cx, cy + 4, cz + 2)) return false;
        // Left col (z=cz) and right col (z=cz+3): all 3 middle rows
        for (int y = cy + 1; y <= cy + 3; y++) {
            if (!isObs(world, cx, y, cz))     return false;
            if (!isObs(world, cx, y, cz + 3)) return false;
        }
        for (int z = cz + 1; z <= cz + 2; z++) {
            for (int y = cy + 1; y <= cy + 3; y++) {
                Block b = world.getBlock(cx, y, z);
                if (b != Block.AIR && b != Block.NETHER_PORTAL) return false;
            }
        }
        return true;
    }

    private static void fillFrameZY(World world, int cx, int cy, int cz) {
        for (int z = cz + 1; z <= cz + 2; z++)
            for (int y = cy + 1; y <= cy + 3; y++)
                world.setBlock(cx, y, z, Block.NETHER_PORTAL);
    }

    private static boolean isObs(World world, int x, int y, int z) {
        return world.getBlock(x, y, z) == Block.OBSIDIAN;
    }

    /**
     * Places a complete 4x5 obsidian frame + portal interior at (cx,cy,cz).
     * The frame is built in the XY plane (along X and Y, z=cz).
     * Used to create a return portal in the nether.
     */
    public static void placePortalXY(World world, int cx, int cy, int cz) {
        // Bottom and top rows
        for (int x = cx; x < cx + 4; x++) {
            world.setBlock(x, cy,     cz, Block.OBSIDIAN);
            world.setBlock(x, cy + 4, cz, Block.OBSIDIAN);
        }
        // Side columns
        for (int y = cy + 1; y <= cy + 3; y++) {
            world.setBlock(cx,     y, cz, Block.OBSIDIAN);
            world.setBlock(cx + 3, y, cz, Block.OBSIDIAN);
        }
        // Interior: nether portal (no setBlock call that re-triggers checkAndActivate
        // since NETHER_PORTAL placement does not call checkAndActivate)
        for (int x = cx + 1; x <= cx + 2; x++)
            for (int y = cy + 1; y <= cy + 3; y++)
                world.setBlock(x, y, cz, Block.NETHER_PORTAL);
    }
}
