package com.craftclassic.common.world;

import com.craftclassic.common.blocks.Block;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldGenerationTest
{
    private static final int SIZE = 64;

    @Test
    void naturalWorldIsDeterministicForASeed()
    {
        World first = new World(SIZE, SIZE, SIZE, 42, true);
        World second = new World(SIZE, SIZE, SIZE, 42, true);

        assertEquals(hash(first), hash(second));
    }

    @Test
    void differentSeedsProduceDifferentWorlds()
    {
        World a = new World(SIZE, SIZE, SIZE, 42, true);
        World b = new World(SIZE, SIZE, SIZE, 7, true);

        assertNotEquals(hash(a), hash(b));
    }

    @Test
    void naturalWorldHasABedrockFloor()
    {
        World world = new World(SIZE, SIZE, SIZE, 42, true);

        for (int z = 0; z < SIZE; z++)
            for (int x = 0; x < SIZE; x++)
                assertEquals(Block.BEDROCK, world.getBlock(x, 63, z),
                        "expected bedrock at " + x + ",63," + z);
    }

    @Test
    void naturalWorldContainsTerrainAndWater()
    {
        World world = new World(SIZE, SIZE, SIZE, 42, true);
        Set<Block> seen = new HashSet<>();

        for (int y = 0; y < SIZE; y++)
            for (int z = 0; z < SIZE; z++)
                for (int x = 0; x < SIZE; x++)
                    seen.add(world.getBlock(x, y, z));

        assertTrue(seen.contains(Block.STONE), "expected stone");
        assertTrue(seen.contains(Block.GRASS), "expected grass");
        assertTrue(seen.contains(Block.DIRT), "expected dirt");
        assertTrue(seen.contains(Block.WATER), "expected water");
        assertTrue(seen.contains(Block.AIR), "expected air");
    }

    @Test
    void naturalWorldNeverFillsTheTopRow()
    {
        World world = new World(SIZE, SIZE, SIZE, 42, true);

        for (int z = 0; z < SIZE; z++)
            for (int x = 0; x < SIZE; x++)
                assertEquals(Block.AIR, world.getBlock(x, 0, z),
                        "top of the world should stay open sky");
    }

    private static long hash(World world)
    {
        long hash = 1125899906842597L;
        for (Block block : world.blockData)
        {
            hash = 31 * hash + (block == null ? 0 : block.getId());
        }
        return hash;
    }
}
