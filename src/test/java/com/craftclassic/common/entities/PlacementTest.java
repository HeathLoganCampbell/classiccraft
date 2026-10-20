package com.craftclassic.common.entities;

import com.craftclassic.client.input.Input;
import com.craftclassic.common.GameSettings;
import com.craftclassic.common.blocks.Block;
import com.craftclassic.common.utils.Location;
import com.craftclassic.common.utils.Vector;
import com.craftclassic.common.world.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.awt.event.MouseEvent;

import static org.junit.jupiter.api.Assertions.assertSame;

class PlacementTest
{
    @AfterEach
    void resetInput()
    {
        Input.setMouse(MouseEvent.BUTTON1, false);
        Input.setMouse(MouseEvent.BUTTON3, false);
        GameSettings.placeBlockTypeId = Block.DIRT.getId();
        GameSettings.tickScale = 1.0f;
        GameSettings.speedMultiplier = 1.0f;
        Player.packetSink = null;
    }

    private static Player spawn(World world)
    {
        Player player = new Player(null, "Test", 0);
        player.setLocation(new Location(world, 64 + 32.5f, 64 + 1.0f, 64 + 32.5f));
        player.setVelocity(new Vector(0f, 0f, 0f));
        world.addEntity(player);
        return player;
    }

    @Test
    void cannotPlaceOnTopOfWater()
    {
        World world = new World(64, 64, 64, 1L, false);
        Player player = spawn(world);

        world.setBlock(32, 40, 32, Block.WATER);
        player.setTargetBlock(new Location(world, 32, 40, 32));
        player.setTargetBlockPlace(new Location(world, 32, 39, 32));

        Input.setMouse(MouseEvent.BUTTON3, true);
        player.tick();

        assertSame(Block.AIR, world.getBlock(32, 39, 32), "water top is not a placement surface");
        assertSame(Block.WATER, world.getBlock(32, 40, 32));
    }

    @Test
    void canReplaceWaterAgainstASolidSide()
    {
        World world = new World(64, 64, 64, 1L, false);
        Player player = spawn(world);

        world.setBlock(32, 40, 32, Block.STONE);   // clicked face belongs to this block
        world.setBlock(31, 40, 32, Block.WATER);   // adjacent cell to place into

        player.setTargetBlock(new Location(world, 32, 40, 32));
        player.setTargetBlockPlace(new Location(world, 31, 40, 32));

        Input.setMouse(MouseEvent.BUTTON3, true);
        player.tick();

        assertSame(Block.DIRT, world.getBlock(31, 40, 32), "water is replaced by the placed block");
    }
}
