package com.craftclassic.common.world;

import com.craftclassic.common.blocks.Block;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;

class WaterFlowTest
{
    @Test
    void waterSpreadsSidewaysAndDownButNotUp()
    {
        World world = new World(64, 64, 64, 1L, false);
        world.setBlock(10, 10, 10, Block.WATER);

        // First spread is scheduled and lands on the fourth tick; six is enough
        // for the first ring without depending on the exact phase.
        for (int i = 0; i < 6; i++)
            world.tick();

        assertSame(Block.WATER, world.getBlock(9, 10, 10));
        assertSame(Block.WATER, world.getBlock(11, 10, 10));
        assertSame(Block.WATER, world.getBlock(10, 10, 9));
        assertSame(Block.WATER, world.getBlock(10, 10, 11));
        assertSame(Block.WATER, world.getBlock(10, 11, 10)); // y grows downwards
        assertSame(Block.AIR, world.getBlock(10, 9, 10));    // never upwards
    }

    @Test
    void waterDoesNotReplaceSolidBlocks()
    {
        World world = new World(64, 64, 64, 1L, false);
        world.setBlock(10, 10, 10, Block.WATER);
        world.setBlock(11, 10, 10, Block.STONE);

        for (int i = 0; i < 6; i++)
            world.tick();

        assertSame(Block.STONE, world.getBlock(11, 10, 10));
    }
}
