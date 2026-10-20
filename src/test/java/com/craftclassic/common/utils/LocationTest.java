package com.craftclassic.common.utils;

import com.craftclassic.common.blocks.Block;
import com.craftclassic.common.world.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class LocationTest
{
    private static final float EPSILON = 1e-6f;

    @Test
    void positionOnlyConstructorStartsLookingStraightAhead()
    {
        Location location = new Location(null, 1, 2, 3);

        assertEquals(1, location.getX(), EPSILON);
        assertEquals(2, location.getY(), EPSILON);
        assertEquals(3, location.getZ(), EPSILON);
        assertEquals(0, location.getYaw(), EPSILON);
        assertEquals(0, location.getPitch(), EPSILON);
    }

    @Test
    void settersUpdateEveryComponent()
    {
        Location location = new Location(null, 0, 0, 0);

        location.setX(4);
        location.setY(5);
        location.setZ(6);
        location.setYaw(0.5f);
        location.setPitch(0.01f);

        assertEquals(4, location.getX(), EPSILON);
        assertEquals(5, location.getY(), EPSILON);
        assertEquals(6, location.getZ(), EPSILON);
        assertEquals(0.5f, location.getYaw(), EPSILON);
        assertEquals(0.01f, location.getPitch(), EPSILON);
    }

    @Test
    void pitchIsClampedToTheLookLimit()
    {
        Location location = new Location(null, 0, 0, 0);

        location.setPitch(10);
        assertEquals(0.016f, location.getPitch(), EPSILON);

        location.setPitch(-10);
        assertEquals(-0.016f, location.getPitch(), EPSILON);
    }

    @Test
    void pitchInsideTheLimitIsUnchanged()
    {
        Location location = new Location(null, 0, 0, 0);

        location.setPitch(0.005f);

        assertEquals(0.005f, location.getPitch(), EPSILON);
    }

    @Test
    void addAndSubtractOffsetThePosition()
    {
        Location location = new Location(null, 10, 20, 30);

        location.add(1, -2, 3);
        assertEquals(11, location.getX(), EPSILON);
        assertEquals(18, location.getY(), EPSILON);
        assertEquals(33, location.getZ(), EPSILON);

        location.subtract(1, -2, 3);
        assertEquals(10, location.getX(), EPSILON);
        assertEquals(20, location.getY(), EPSILON);
        assertEquals(30, location.getZ(), EPSILON);
    }

    @Test
    void cloneIsIndependentButSharesTheWorld()
    {
        World world = new World(64, 64, 64, 1, false);
        Location original = new Location(world, 10, 20, 30, 0.25f, 0.01f);

        Location copy = original.clone();

        assertNotSame(original, copy);
        assertSame(world, copy.getWorld());
        assertEquals(original.getX(), copy.getX(), EPSILON);
        assertEquals(original.getY(), copy.getY(), EPSILON);
        assertEquals(original.getZ(), copy.getZ(), EPSILON);
        assertEquals(original.getYaw(), copy.getYaw(), EPSILON);
        assertEquals(original.getPitch(), copy.getPitch(), EPSILON);

        copy.setX(99);
        assertEquals(10, original.getX(), EPSILON);
    }

    @Test
    void blockAccessorsDelegateToTheWorld()
    {
        World world = new World(64, 64, 64, 1, false);
        Location location = new Location(world, 10, 20, 30);

        assertEquals(Block.AIR, location.getBlock());

        location.setBlock(Block.DIRT);

        assertEquals(Block.DIRT, location.getBlock());
        assertEquals(Block.DIRT, world.getBlock(10, 20, 30));
    }
}
