package com.craftclassic.common.blocks;

import com.craftclassic.common.entities.Entity;
import com.craftclassic.common.events.BreakEvent;
import com.craftclassic.common.utils.Location;
import com.craftclassic.common.world.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockBehaviourTest
{
    @Test
    void bedrockSurvivesBeingHit()
    {
        World world = new World(64, 64, 64, 1, false);
        world.setBlock(10, 20, 10, Block.BEDROCK);
        Location location = new Location(world, 10, 20, 10);

        assertFalse(Block.BEDROCK.onBreak(new BreakEvent(location)));
        assertEquals(Block.BEDROCK, world.getBlock(10, 20, 10));
    }

    @Test
    void ordinaryBlocksAcceptBeingHit()
    {
        World world = new World(64, 64, 64, 1, false);
        Location location = new Location(world, 10, 20, 10);

        assertTrue(Block.STONE.onBreak(new BreakEvent(location)));
        assertTrue(Block.DIRT.onBreak(new BreakEvent(location)));
    }

    @Test
    void tntClearsItsFourByFourByFourBlastCube()
    {
        World world = new World(64, 64, 64, 1, false);
        int cx = 20, cy = 20, cz = 20;

        for (int y = cy - 2; y <= cy + 1; y++)
            for (int x = cx - 2; x <= cx + 1; x++)
                for (int z = cz - 2; z <= cz + 1; z++)
                    world.setBlock(x, y, z, Block.STONE);

        world.setBlock(cx, cy, cz, Block.TNT);
        // Just outside the blast cube, on both sides of each axis.
        world.setBlock(cx + 2, cy, cz, Block.STONE);
        world.setBlock(cx - 3, cy, cz, Block.STONE);
        world.setBlock(cx, cy + 2, cz, Block.STONE);
        world.setBlock(cx, cy - 3, cz, Block.STONE);
        world.setBlock(cx, cy, cz + 2, Block.STONE);
        world.setBlock(cx, cy, cz - 3, Block.STONE);

        Location tnt = new Location(world, cx, cy, cz);
        assertFalse(Block.TNT.onBreak(new BreakEvent(tnt)));

        for (int y = cy - 2; y <= cy + 1; y++)
            for (int x = cx - 2; x <= cx + 1; x++)
                for (int z = cz - 2; z <= cz + 1; z++)
                    assertEquals(Block.AIR, world.getBlock(x, y, z),
                            "blast should clear " + x + "," + y + "," + z);

        assertEquals(Block.STONE, world.getBlock(cx + 2, cy, cz));
        assertEquals(Block.STONE, world.getBlock(cx - 3, cy, cz));
        assertEquals(Block.STONE, world.getBlock(cx, cy + 2, cz));
        assertEquals(Block.STONE, world.getBlock(cx, cy - 3, cz));
        assertEquals(Block.STONE, world.getBlock(cx, cy, cz + 2));
        assertEquals(Block.STONE, world.getBlock(cx, cy, cz - 3));
    }

    @Test
    void tntIgnoresBreakEventReturnValueSoItAlwaysExplodes()
    {
        World world = new World(64, 64, 64, 1, false);
        world.setBlock(20, 20, 20, Block.TNT);
        world.setBlock(19, 20, 20, Block.TNT);
        Location tnt = new Location(world, 20, 20, 20);

        Block.TNT.onBreak(new BreakEvent(tnt));

        assertEquals(Block.AIR, world.getBlock(20, 20, 20));
        assertEquals(Block.AIR, world.getBlock(19, 20, 20));
    }

    @Test
    void sandFallsOneCellPerTickWhenSupportedByAir()
    {
        World world = new World(64, 64, 64, 1, false);
        world.setBlock(10, 20, 10, Block.SAND);

        Block.SAND.onTick(new Location(world, 10, 20, 10));

        assertEquals(Block.AIR, world.getBlock(10, 20, 10));
        assertEquals(Block.SAND, world.getBlock(10, 21, 10));
    }

    @Test
    void sandStaysPutOnASolidBlock()
    {
        World world = new World(64, 64, 64, 1, false);
        world.setBlock(10, 20, 10, Block.SAND);
        world.setBlock(10, 21, 10, Block.STONE);

        Block.SAND.onTick(new Location(world, 10, 20, 10));

        assertEquals(Block.SAND, world.getBlock(10, 20, 10));
        assertEquals(Block.STONE, world.getBlock(10, 21, 10));
    }

    @Test
    void nonPhysicsBlocksDoNotRunOnTick()
    {
        CountingBlock block = new CountingBlock(false);

        Block.STONE.onTick(new Location(null, 0, 0, 0));
        block.onTick(new Location(null, 0, 0, 0));

        // Stone's base implementation only does work when isPhysics() is set.
        assertEquals(0, block.ticks);
    }

    @Test
    void worldTickOnlyDrivesPhysicsBlocksAndEntities()
    {
        World world = new World(64, 64, 64, 1, false);

        CountingBlock physicsBlock = new CountingBlock(true);
        CountingBlock staticBlock = new CountingBlock(false);

        world.setBlock(10, 20, 10, physicsBlock);
        world.setBlock(11, 20, 10, staticBlock);

        CountingEntity entity = new CountingEntity();
        world.addEntity(entity);

        world.tick();

        assertEquals(1, physicsBlock.ticks);
        assertEquals(0, staticBlock.ticks);
        assertEquals(1, entity.ticks);
    }

    private static final class CountingBlock extends Block
    {
        private int ticks;

        private CountingBlock(boolean physics)
        {
            super(physics ? 91 : 92, physics ? "PhysicsTest" : "StaticTest", 0);
            setPhysics(physics);
        }

        @Override
        public void onTick(Location loc)
        {
            if (isPhysics())
                ticks++;
        }
    }

    private static final class CountingEntity extends Entity
    {
        private int ticks;

        private CountingEntity()
        {
            super("Counter", 1234);
        }

        @Override
        public void tick()
        {
            ticks++;
        }
    }
}
