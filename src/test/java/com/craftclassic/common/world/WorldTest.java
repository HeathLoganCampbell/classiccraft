package com.craftclassic.common.world;

import com.craftclassic.common.blocks.Block;
import com.craftclassic.common.entities.Entity;
import com.craftclassic.common.utils.Location;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldTest
{
    @Test
    void flatWorldLaysOneStoneLayerOnTheFloor()
    {
        World world = new World(64, 64, 64, 1, false);

        for (int z = 0; z < 64; z += 7)
        {
            for (int x = 0; x < 64; x += 7)
            {
                assertEquals(Block.STONE, world.getBlock(x, 63, z));
                assertEquals(Block.AIR, world.getBlock(x, 62, z));
            }
        }

        assertEquals(64 * 64, world.blockVersion);
    }

    @Test
    void dimensionsAndSeedAreExposed()
    {
        World world = new World(64, 64, 64, 12345L, false);

        assertEquals(64, world.getWidth());
        assertEquals(64, world.getHeight());
        assertEquals(12345L, world.getSeed());
    }

    @Test
    void heightCanBeChanged()
    {
        World world = new World(64, 64, 64, 1, false);

        world.setHeight(32);

        assertEquals(32, world.getHeight());
    }

    @Test
    void unsetCellsReadAsAir()
    {
        World world = new World(64, 64, 64, 1, false);

        assertSame(Block.AIR, world.getBlock(1, 1, 1));
    }

    @Test
    void setBlockStoresTheExactBlockInstance()
    {
        World world = new World(64, 64, 64, 1, false);
        Block custom = new Block(80, "Custom", 0);

        world.setBlock(10, 20, 30, custom);

        assertSame(custom, world.getBlock(10, 20, 30));
        assertNotSame(Block.AIR, world.getBlock(10, 20, 30));
    }

    @Test
    void blockVersionTracksEveryRawBlockChange()
    {
        World world = new World(64, 64, 64, 1, false);
        long before = world.blockVersion;

        world.setBlock(10, 20, 30, Block.DIRT);
        assertEquals(before + 1, world.blockVersion);

        world.setBlock(11, 20, 30, Block.STONE);
        assertEquals(before + 2, world.blockVersion);
    }

    @Test
    void locationSetBlockUsesWorldCoordinates()
    {
        World world = new World(64, 64, 64, 1, false);
        long before = world.blockVersion;
        Location location = new Location(world, 10, 20, 30);

        world.setBlock(location, Block.DIRT);

        assertEquals(Block.DIRT, world.getBlock(10, 20, 30));
        assertEquals(Block.DIRT, world.getBlock(location));
        assertEquals(before + 1, world.blockVersion);
    }

    @Test
    void outOfBoundsLocationsReadAsBedrock()
    {
        World world = new World(64, 64, 64, 1, false);

        assertSame(Block.BEDROCK, world.getBlock(new Location(world, -1, 10, 10)));
        assertSame(Block.BEDROCK, world.getBlock(new Location(world, 10, -1, 10)));
        assertSame(Block.BEDROCK, world.getBlock(new Location(world, 10, 10, -1)));
        assertSame(Block.BEDROCK, world.getBlock(new Location(world, 65, 10, 10)));
        assertSame(Block.BEDROCK, world.getBlock(new Location(world, 10, 65, 10)));
        assertSame(Block.BEDROCK, world.getBlock(new Location(world, 10, 10, 65)));
    }

    @Test
    void outOfBoundsLocationWritesAreIgnored()
    {
        World world = new World(64, 64, 64, 1, false);
        long before = world.blockVersion;

        world.setBlock(new Location(world, -1, 10, 10), Block.DIRT);
        world.setBlock(new Location(world, 10, -1, 10), Block.DIRT);
        world.setBlock(new Location(world, 10, 10, -1), Block.DIRT);
        world.setBlock(new Location(world, 65, 10, 10), Block.DIRT);
        world.setBlock(new Location(world, 10, 65, 10), Block.DIRT);
        world.setBlock(new Location(world, 10, 10, 65), Block.DIRT);

        assertEquals(before, world.blockVersion);
    }

    @Test
    void entitiesStartEmptyAndAreAdded()
    {
        World world = new World(64, 64, 64, 1, false);
        Entity entity = new Entity("Test", 1);

        assertTrue(world.getEntities().isEmpty());

        world.addEntity(entity);

        assertEquals(1, world.getEntities().size());
        assertSame(entity, world.getEntities().get(0));
    }
}
