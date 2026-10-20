package com.craftclassic.common.world;

import com.craftclassic.common.blocks.Block;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class RaycastTest
{
    private static final float CAMERA_X = 96.5f; // block x = 32
    private static final float CAMERA_Y = 95.5f; // block y = 31
    private static final float CAMERA_Z = 96.5f; // block z = 32

    private static World flatWorld()
    {
        // Flat generation puts stone at y = 63, far below the test rays.
        return new World(64, 64, 64, 1, false);
    }

    @Test
    void hitsTheBlockAheadAndReportsThePlacementCell()
    {
        World world = flatWorld();
        world.setBlock(32, 31, 33, Block.STONE);

        Raycast.Hit hit = Raycast.cast(world, CAMERA_X, CAMERA_Y, CAMERA_Z, 0, 0, 5);

        assertNotNull(hit);
        assertEquals(32, hit.getX());
        assertEquals(31, hit.getY());
        assertEquals(33, hit.getZ());

        assertEquals(32, hit.getPlaceX());
        assertEquals(31, hit.getPlaceY());
        assertEquals(32, hit.getPlaceZ());

        assertEquals(2, hit.getAxis());
        assertEquals(0.5f, hit.getDistance(), 1e-6f);
    }

    @Test
    void reportsAnXFaceWhenLookingAlongPositiveX()
    {
        World world = flatWorld();
        world.setBlock(33, 31, 32, Block.STONE);

        float yaw = (float) (Math.PI / 200.0); // sin(yaw * 100) = 1
        Raycast.Hit hit = Raycast.cast(world, CAMERA_X, CAMERA_Y, CAMERA_Z, yaw, 0, 5);

        assertNotNull(hit);
        assertEquals(33, hit.getX());
        assertEquals(0, hit.getAxis());
        assertEquals(32, hit.getPlaceX());
        assertEquals(31, hit.getPlaceY());
        assertEquals(32, hit.getPlaceZ());
    }

    @Test
    void reportsAYFaceWhenLookingDown()
    {
        World world = flatWorld();
        world.setBlock(32, 32, 32, Block.STONE);

        float pitch = (float) (-Math.PI / 200.0); // -sin(pitch * 100) = 1
        Raycast.Hit hit = Raycast.cast(world, CAMERA_X, CAMERA_Y, CAMERA_Z, 0, pitch, 5);

        assertNotNull(hit);
        assertEquals(32, hit.getY());
        assertEquals(1, hit.getAxis());
        assertEquals(32, hit.getPlaceX());
        assertEquals(31, hit.getPlaceY());
        assertEquals(32, hit.getPlaceZ());
    }

    @Test
    void reportsMinusOneWhenStartingInsideABlock()
    {
        World world = flatWorld();
        world.setBlock(32, 31, 32, Block.STONE);

        Raycast.Hit hit = Raycast.cast(world, CAMERA_X, CAMERA_Y, CAMERA_Z, 0, 0, 5);

        assertNotNull(hit);
        assertEquals(-1, hit.getAxis());
        assertEquals(0.0f, hit.getDistance(), 1e-6f);
        assertEquals(32, hit.getX());
        assertEquals(31, hit.getY());
        assertEquals(32, hit.getZ());
        // With no face to step back along, placement stays on the hit cell.
        assertEquals(32, hit.getPlaceX());
        assertEquals(31, hit.getPlaceY());
        assertEquals(32, hit.getPlaceZ());
    }

    @Test
    void returnsNullWhenNothingIsWithinReach()
    {
        World world = flatWorld();

        assertNull(Raycast.cast(world, CAMERA_X, CAMERA_Y, CAMERA_Z, 0, 0, 5));
    }

    @Test
    void reachIsExclusive()
    {
        World world = flatWorld();
        world.setBlock(32, 31, 33, Block.STONE); // hit at distance 0.5

        assertNull(Raycast.cast(world, CAMERA_X, CAMERA_Y, CAMERA_Z, 0, 0, 0.4f));
        assertNotNull(Raycast.cast(world, CAMERA_X, CAMERA_Y, CAMERA_Z, 0, 0, 0.6f));
    }

    @Test
    void stopsAtTheWorldEdge()
    {
        World world = flatWorld();

        // Block y = 31 is empty all the way to z = 63; the ray must give up
        // at the boundary rather than reading outside the array.
        assertNull(Raycast.cast(world, CAMERA_X, CAMERA_Y, CAMERA_Z, 0, 0, 100));
    }

    @Test
    void nonAirBlocksAreTargetableEvenWhenNotSelectable()
    {
        World world = flatWorld();
        world.setBlock(32, 31, 33, Block.WATER);

        Raycast.Hit hit = Raycast.cast(world, CAMERA_X, CAMERA_Y, CAMERA_Z, 0, 0, 5);

        assertNotNull(hit);
        assertEquals(33, hit.getZ());
    }

    @Test
    void defaultReachIsFiveBlocks()
    {
        assertEquals(5.0f, Raycast.DEFAULT_REACH);
    }
}
