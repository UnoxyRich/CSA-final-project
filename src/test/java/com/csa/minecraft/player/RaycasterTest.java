package com.csa.minecraft.player;

import com.csa.minecraft.world.Block;
import com.csa.minecraft.world.World;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests run in air space (y=120) above the terrain ceiling (~y=82) so that the only
 * solid voxels in range are the ones the test explicitly places.
 */
class RaycasterTest {

    private static final int Y = 120;

    @Test
    void hitsBlockAlongPositiveZ() {
        World w = new World(1L);
        w.setBlock(5, Y, 5, Block.STONE);

        Raycaster.Hit h = Raycaster.cast(w,
            new Vector3f(5.5f, Y + 0.5f, 2.5f),
            new Vector3f(0, 0, 1),
            10f);

        assertNotNull(h);
        assertEquals(5, h.x);
        assertEquals(Y, h.y);
        assertEquals(5, h.z);
        // Placement cell is the empty cell on the entry face — one step back along Z.
        assertEquals(5, h.px);
        assertEquals(Y, h.py);
        assertEquals(4, h.pz);
    }

    @Test
    void hitsBlockAlongNegativeX() {
        World w = new World(1L);
        w.setBlock(0, Y, 0, Block.STONE);

        Raycaster.Hit h = Raycaster.cast(w,
            new Vector3f(5.5f, Y + 0.5f, 0.5f),
            new Vector3f(-1, 0, 0),
            10f);

        assertNotNull(h);
        assertEquals(0, h.x);
        assertEquals(0, h.z);
        // Entering from +X side: placement cell is one cell in +X direction.
        assertEquals(1, h.px);
    }

    @Test
    void returnsNullWhenNothingInRange() {
        World w = new World(1L); // no blocks placed in air at y=120
        Raycaster.Hit h = Raycaster.cast(w,
            new Vector3f(0.5f, Y + 0.5f, 0.5f),
            new Vector3f(1, 0, 0),
            5f);
        assertNull(h);
    }

    @Test
    void respectsMaxDistance() {
        World w = new World(1L);
        w.setBlock(20, Y, 0, Block.STONE);
        // Ray has only 5 units of reach but block is 20 away.
        Raycaster.Hit h = Raycaster.cast(w,
            new Vector3f(0.5f, Y + 0.5f, 0.5f),
            new Vector3f(1, 0, 0),
            5f);
        assertNull(h);
    }

    @Test
    void downwardRayHitsTopFaceAndPlacementIsAbove() {
        World w = new World(1L);
        w.setBlock(0, Y, 0, Block.STONE);

        Raycaster.Hit h = Raycaster.cast(w,
            new Vector3f(0.5f, Y + 5.5f, 0.5f),
            new Vector3f(0, -1, 0),
            10f);

        assertNotNull(h);
        assertEquals(0, h.x);
        assertEquals(Y, h.y);
        assertEquals(0, h.z);
        // Hit the top face → placement cell is directly above.
        assertEquals(Y + 1, h.py);
    }

    @Test
    void rayStartingInsideBlockReturnsThatBlockImmediately() {
        World w = new World(1L);
        w.setBlock(3, Y, 3, Block.STONE);
        Raycaster.Hit h = Raycaster.cast(w,
            new Vector3f(3.5f, Y + 0.5f, 3.5f),
            new Vector3f(1, 0, 0),
            10f);
        assertNotNull(h);
        assertEquals(3, h.x);
        assertEquals(Y, h.y);
        assertEquals(3, h.z);
    }
}
