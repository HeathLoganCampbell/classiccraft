package com.craftclassic.common.blocks;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockRegistryTest
{
    @Test
    void builtInBlockIdsRoundTrip()
    {
        Map<Integer, Block> expected = Map.ofEntries(
                Map.entry(0, Block.AIR),
                Map.entry(1, Block.DIRT),
                Map.entry(2, Block.GRASS),
                Map.entry(3, Block.STONE),
                Map.entry(4, Block.LAVA),
                Map.entry(5, Block.WATER),
                Map.entry(6, Block.BEDROCK),
                Map.entry(7, Block.LOG),
                Map.entry(8, Block.LEAVES),
                Map.entry(9, Block.BRICK),
                Map.entry(10, Block.COBBLESTONE),
                Map.entry(11, Block.GLASS),
                Map.entry(12, Block.TNT),
                Map.entry(13, Block.SAND)
        );

        expected.forEach((id, block) -> {
            assertEquals(id, block.getId(), block.getName());
            assertSame(block, Block.getBlockById(id), block.getName());
        });
    }

    @Test
    void unknownIdFallsBackToDirt()
    {
        assertSame(Block.DIRT, Block.getBlockById(9999));
        assertSame(Block.DIRT, Block.getBlockById(-1));
    }

    @Test
    void namesMatchTheBlocksFile()
    {
        assertEquals("Air", Block.AIR.getName());
        assertEquals("Dirt", Block.DIRT.getName());
        assertEquals("Grass", Block.GRASS.getName());
        assertEquals("Stone", Block.STONE.getName());
        assertEquals("Lava", Block.LAVA.getName());
        assertEquals("Water", Block.WATER.getName());
        assertEquals("Bedrock", Block.BEDROCK.getName());
        assertEquals("Log", Block.LOG.getName());
        assertEquals("Leaves", Block.LEAVES.getName());
        assertEquals("Brick", Block.BRICK.getName());
        assertEquals("CobbleStone", Block.COBBLESTONE.getName());
        assertEquals("Glass", Block.GLASS.getName());
        assertEquals("TNT", Block.TNT.getName());
        assertEquals("Sand", Block.SAND.getName());
    }

    @Test
    void textureIdsSelectTheAtlasStrip()
    {
        // saved-texturess.png is 16 px wide and 768 px tall, i.e. 16 strips of 48 px.
        assertEquals(-1, Block.AIR.getTextureId());
        assertEquals(0, Block.GRASS.getTextureId());
        assertEquals(1, Block.DIRT.getTextureId());
        assertEquals(3, Block.GLASS.getTextureId());
        assertEquals(4, Block.STONE.getTextureId());
        assertEquals(5, Block.BRICK.getTextureId());
        assertEquals(7, Block.LOG.getTextureId());
        assertEquals(8, Block.LEAVES.getTextureId());
        assertEquals(9, Block.BEDROCK.getTextureId());
        assertEquals(10, Block.TNT.getTextureId());
        assertEquals(11, Block.COBBLESTONE.getTextureId());
        assertEquals(12, Block.LAVA.getTextureId());
        assertEquals(13, Block.WATER.getTextureId());
        assertEquals(14, Block.SAND.getTextureId());
    }

    @Test
    void airIsNotSolidAndCanBeReplaced()
    {
        assertFalse(Block.AIR.isSolid());
        // onPlace is asked of the existing block: a non-solid one may be
        // replaced, which is why building against air is allowed.
        assertTrue(Block.AIR.onPlace(null));
    }

    @Test
    void liquidsArePassableAndNotSelectable()
    {
        assertFalse(Block.WATER.isSolid());
        assertFalse(Block.WATER.isSelectable());
        assertFalse(Block.WATER.isBreakable());
        assertTrue(Block.WATER.onPlace(null));

        assertFalse(Block.LAVA.isSolid());
        assertFalse(Block.LAVA.isSelectable());
        assertFalse(Block.LAVA.isBreakable());
        assertTrue(Block.LAVA.onPlace(null));
    }

    @Test
    void sandFallsButStoneDoesNot()
    {
        assertTrue(Block.SAND.isPhysics());
        assertFalse(Block.STONE.isPhysics());
        assertFalse(Block.DIRT.isPhysics());
    }

    @Test
    void solidBlocksRefusePlacementThroughTheirDefaultHandler()
    {
        assertTrue(Block.STONE.isSolid());
        assertFalse(Block.STONE.onPlace(null));
        assertFalse(Block.DIRT.onPlace(null));
    }

    @Test
    void ordinaryBlocksAreBreakableAndSelectableByDefault()
    {
        assertTrue(Block.STONE.isBreakable());
        assertTrue(Block.STONE.isSelectable());
        assertTrue(Block.GLASS.isBreakable());
        assertTrue(Block.TNT.isBreakable());
    }

    @Test
    void flagsAreMutable()
    {
        Block block = new Block(90, "Test", 2);

        block.setBreakable(false);
        block.setSolid(false);
        block.setSelectable(false);
        block.setPhysics(true);
        block.setName("Renamed");
        block.setTextureId(42);

        assertFalse(block.isBreakable());
        assertFalse(block.isSolid());
        assertFalse(block.isSelectable());
        assertTrue(block.isPhysics());
        assertEquals("Renamed", block.getName());
        assertEquals(42, block.getTextureId());
        assertEquals(90, block.getId());
    }
}
