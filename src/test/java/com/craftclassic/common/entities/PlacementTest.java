package com.craftclassic.common.entities;

import com.craftclassic.client.input.Input;
import com.craftclassic.common.GameSettings;
import com.craftclassic.common.blocks.Block;
import com.craftclassic.common.utils.Location;
import com.craftclassic.common.utils.Vector;
import com.craftclassic.common.world.Raycast;
import com.craftclassic.common.world.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.awt.event.MouseEvent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    void placingOnWaterReplacesTheWaterInsteadOfFloatingAboveIt()
    {
        World world = new World(64, 64, 64, 1L, false);
        Player player = spawn(world);

        // One block above the flat stone floor, inside the 5-block reach.
        player.setLocation(new Location(world, 64 + 32.5f, 124.5f, 64 + 32.5f));
        player.getLocation().setPitch(-0.016f);

        world.setBlock(32, 62, 32, Block.WATER);

        Location location = player.getLocation();
        Raycast.Hit hit = Raycast.cast(world, location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch(), Raycast.DEFAULT_REACH);

        assertNotNull(hit);
        assertEquals(63, hit.getY(), "ray passes through the water to the floor");
        assertEquals(62, hit.getPlaceY(), "placement cell is the water cell");

        player.setTargetBlock(new Location(world, hit.getX(), hit.getY(), hit.getZ()));
        player.setTargetBlockPlace(new Location(world, hit.getPlaceX(), hit.getPlaceY(), hit.getPlaceZ()));

        Input.setMouse(MouseEvent.BUTTON3, true);
        player.tick();

        assertSame(Block.DIRT, world.getBlock(32, 62, 32), "water becomes the placed block");
        assertSame(Block.AIR, world.getBlock(32, 61, 32), "nothing floats above the water surface");
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
