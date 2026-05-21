package com.csa.minecraft.player;

import com.csa.minecraft.GameMode;
import com.csa.minecraft.Settings;
import com.csa.minecraft.world.Block;
import com.csa.minecraft.world.World;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Physics tests place the player at y=120, above terrain (~y=82 ceiling), so the only
 * solid blocks the AABB can touch are the ones each test sets up explicitly.
 */
class PhysicsTest {

    private static final int Y = 120;
    private static final float EPS = 0.05f;

    @Test
    void noCollisionInOpenAirLeavesPositionUnchangedByCollisionPass() {
        World w = new World(1L);
        Player p = new Player(new Vector3f(8.5f, Y, 8.5f));
        p.velocity().set(0, 0, 0);
        p.setOnGround(true);

        Physics.moveAndCollide(p, w, 0.05f);

        assertEquals(8.5f, p.position().x, EPS);
        assertEquals(Y,    p.position().y, EPS);
        assertEquals(8.5f, p.position().z, EPS);
        // moveAndCollide always recomputes onGround during the Y pass; with no block
        // below and no downward velocity it must clear the flag.
        assertFalse(p.isOnGround());
    }

    @Test
    void downwardMotionLandsOnBlockAndSetsOnGround() {
        World w = new World(1L);
        // Floor at y = Y-1 (block occupies [Y-1, Y) in world units).
        w.setBlock(8, Y - 1, 8, Block.STONE);
        // Surround with floor so the AABB's full footprint has support.
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                w.setBlock(8 + dx, Y - 1, 8 + dz, Block.STONE);

        Player p = new Player(new Vector3f(8.5f, Y + 0.5f, 8.5f));
        p.velocity().set(0, -10f, 0);

        Physics.moveAndCollide(p, w, 0.2f); // dy = -2 → would land on the block top at y=Y

        assertTrue(p.isOnGround(), "must register as onGround after Y collision resolved downward");
        assertEquals(0f, p.velocity().y, EPS, "vertical velocity zeroed on landing");
        assertTrue(p.position().y >= Y - EPS,
            "feet should not penetrate floor; got y=" + p.position().y);
    }

    @Test
    void hardLandingDealsFallDamageInSurvival() {
        World w = new World(1L);
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                w.setBlock(8 + dx, Y - 1, 8 + dz, Block.STONE);
        Player p = new Player(new Vector3f(8.5f, Y + 0.5f, 8.5f));
        p.velocity().set(0, -20f, 0);

        Physics.moveAndCollide(p, w, 0.2f);

        assertTrue(p.health() < p.maxHealth(), "high-speed landing should damage survival player");
        assertEquals(14f, p.health(), EPS);
    }

    @Test
    void hardLandingDoesNotDamageCreativePlayer() {
        World w = new World(1L);
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                w.setBlock(8 + dx, Y - 1, 8 + dz, Block.STONE);
        Settings settings = new Settings();
        settings.gameMode = GameMode.CREATIVE;
        Player p = new Player(new Vector3f(8.5f, Y + 0.5f, 8.5f), settings);
        p.velocity().set(0, -20f, 0);

        Physics.moveAndCollide(p, w, 0.2f);

        assertEquals(p.maxHealth(), p.health(), EPS);
    }

    @Test
    void horizontalMotionIsBlockedByWall() {
        World w = new World(1L);
        // Wall column at x=10 covering the player's full vertical extent (1 wide is enough
        // because the AABB only spans floor(pos.x - 0.3) .. floor(pos.x + 0.3) in X).
        for (int dy = 0; dy < 3; dy++) w.setBlock(10, Y + dy, 8, Block.STONE);

        Player p = new Player(new Vector3f(8.5f, Y, 8.5f));
        // Step large enough that the resulting AABB overlaps x=10 before resolution.
        p.velocity().set(40f, 0, 0);

        float startX = p.position().x;
        Physics.moveAndCollide(p, w, 0.05f);

        assertEquals(0f, p.velocity().x, EPS, "x velocity zeroed after wall hit");
        assertTrue(p.position().x > startX, "should make some progress before wall");
        // Player half-width = 0.3; right side must not overlap the block at x=10 (occupies [10, 11)).
        assertTrue(p.position().x + Player.WIDTH / 2 <= 10f + EPS,
            "must not penetrate the wall; right edge=" + (p.position().x + Player.WIDTH / 2));
    }

    @Test
    void fallingThroughEmptySpaceLeavesOnGroundFalse() {
        World w = new World(1L);
        Player p = new Player(new Vector3f(8.5f, Y, 8.5f));
        p.velocity().set(0, -5f, 0);
        p.setOnGround(true);

        Physics.moveAndCollide(p, w, 0.05f);

        assertFalse(p.isOnGround(), "no block below → should not be onGround");
        assertTrue(p.position().y < Y, "should have moved downward");
        assertEquals(-5f, p.velocity().y, EPS, "no collision → vy preserved");
    }

    @Test
    void upwardMotionIsBlockedByCeiling() {
        World w = new World(1L);
        // Ceiling at y = Y+2 (player height is 1.8; placing at Y+3 would leave a gap
        // the AABB doesn't reach in a single step).
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                w.setBlock(8 + dx, Y + 2, 8 + dz, Block.STONE);

        Player p = new Player(new Vector3f(8.5f, Y, 8.5f));
        p.velocity().set(0, 20f, 0); // dy=1.0 in dt=0.05 → head reaches y=2.8 → overlaps Y+2

        Physics.moveAndCollide(p, w, 0.05f);

        assertEquals(0f, p.velocity().y, EPS);
        assertFalse(p.isOnGround(), "ceiling hit (dy>0) must not set onGround");
        assertTrue(p.position().y + Player.HEIGHT <= Y + 2 + EPS,
            "head must not penetrate ceiling");
    }

    @Test
    void restingOnGroundProducesNoVerticalJitter() {
        World w = new World(1L);
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                w.setBlock(8 + dx, Y - 1, 8 + dz, Block.STONE);

        Player p = new Player(new Vector3f(8.5f, Y, 8.5f));

        // Settle for a couple of frames, applying gravity like Player.update does.
        for (int i = 0; i < 3; i++) {
            p.velocity().y -= Player.GRAVITY * 0.05f;
            Physics.moveAndCollide(p, w, 0.05f);
        }
        float settledY = p.position().y;

        // Every subsequent resting frame must land on the exact same Y — the camera
        // sits at pos.y + EYE, so any drift here is a visible screen shake.
        for (int i = 0; i < 60; i++) {
            p.velocity().y -= Player.GRAVITY * 0.05f;
            Physics.moveAndCollide(p, w, 0.05f);
            assertEquals(settledY, p.position().y, 0f,
                "resting Y must be perfectly stable; jittered on frame " + i);
            assertTrue(p.isOnGround(), "must stay onGround every resting frame");
        }
    }

    @Test
    void preExistingOverlapWithZeroVelocityResolvesWithoutHanging() {
        World w = new World(1L);
        // Block placed inside the player's AABB with no velocity — the old 0.01
        // step-back used Math.signum(0)==0 and looped forever. Must simply return.
        w.setBlock(8, Y, 8, Block.STONE);
        Player p = new Player(new Vector3f(8.5f, Y, 8.5f));
        p.velocity().set(0, 0, 0);

        assertTimeoutPreemptively(java.time.Duration.ofSeconds(2),
            () -> Physics.moveAndCollide(p, w, 0.05f),
            "zero-velocity overlap must not loop forever");
    }

    @Test
    void zMotionResolvesIndependentlyOfX() {
        World w = new World(1L);
        // Wall on +Z side at z=10. Place it across enough X to remain in front of the
        // player even after X advances this step.
        for (int dy = 0; dy < 3; dy++)
            for (int dx = -2; dx <= 8; dx++)
                w.setBlock(8 + dx, Y + dy, 10, Block.STONE);

        Player p = new Player(new Vector3f(8.5f, Y, 8.5f));
        // X velocity stays modest (no x wall in the way), Z velocity is large enough
        // that the resulting AABB overlaps z=10.
        p.velocity().set(10f, 0, 40f);

        Physics.moveAndCollide(p, w, 0.05f);

        assertEquals(0f, p.velocity().z, EPS, "z blocked by wall");
        assertEquals(10f, p.velocity().x, EPS, "x unaffected by z wall");
        assertTrue(p.position().x > 8.5f, "x should have advanced");
    }
}
