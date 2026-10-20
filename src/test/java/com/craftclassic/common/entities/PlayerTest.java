package com.craftclassic.common.entities;

import com.craftclassic.client.input.Input;
import com.craftclassic.common.GameSettings;
import com.craftclassic.common.blocks.Block;
import com.craftclassic.common.network.packet.Packet;
import com.craftclassic.common.network.packet.packets.BlockPlacePacket;
import com.craftclassic.common.utils.Location;
import com.craftclassic.common.utils.Vector;
import com.craftclassic.common.world.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerTest
{
    private static final int PLAYER_BLOCK_X = 32;
    private static final int PLAYER_BLOCK_Y = 31;
    private static final int PLAYER_BLOCK_Z = 32;

    private World world;
    private Player player;
    private List<Packet> sent;

    private Consumer<Packet> previousSink;
    private int previousPlaceBlockType;
    private float previousTickScale;
    private float previousSpeedMultiplier;

    @BeforeEach
    void setUp()
    {
        previousSink = Player.packetSink;
        previousPlaceBlockType = GameSettings.placeBlockTypeId;
        previousTickScale = GameSettings.tickScale;
        previousSpeedMultiplier = GameSettings.speedMultiplier;

        sent = new ArrayList<>();
        Player.packetSink = sent::add;
        GameSettings.placeBlockTypeId = Block.DIRT.getId();
        GameSettings.tickScale = 1.0f;
        GameSettings.speedMultiplier = 1.0f;
        clearInput();

        world = new World(64, 64, 64, 1, false);
        player = new Player(null, "Tester", 0);
        player.setLocation(new Location(world, 96.5f, 95.5f, 96.5f));
        player.setVelocity(new Vector(0, 0, 0));
        world.addEntity(player);
    }

    @AfterEach
    void tearDown()
    {
        Player.packetSink = previousSink;
        GameSettings.placeBlockTypeId = previousPlaceBlockType;
        GameSettings.tickScale = previousTickScale;
        GameSettings.speedMultiplier = previousSpeedMultiplier;
        clearInput();
    }

    private static void clearInput()
    {
        Input.setKey(KeyEvent.VK_W, false);
        Input.setKey(KeyEvent.VK_A, false);
        Input.setKey(KeyEvent.VK_S, false);
        Input.setKey(KeyEvent.VK_D, false);
        Input.setKey(KeyEvent.VK_SPACE, false);
        Input.setKey(KeyEvent.VK_CONTROL, false);
        Input.setMouse(MouseEvent.BUTTON1, false);
        Input.setMouse(MouseEvent.BUTTON2, false);
        Input.setMouse(MouseEvent.BUTTON3, false);
    }

    private void aimAt(int blockX, int blockY, int blockZ)
    {
        player.setTargetBlock(new Location(world, blockX, blockY, blockZ));
    }

    private void aimPlacementAt(int blockX, int blockY, int blockZ)
    {
        player.setTargetBlockPlace(new Location(world, blockX, blockY, blockZ));
    }

    @Test
    void placingABlockInsideThePlayerIsRefused()
    {
        aimAt(PLAYER_BLOCK_X, PLAYER_BLOCK_Y - 1, PLAYER_BLOCK_Z);
        aimPlacementAt(PLAYER_BLOCK_X, PLAYER_BLOCK_Y, PLAYER_BLOCK_Z);

        Input.setMouse(MouseEvent.BUTTON3, true);
        player.tick();

        assertEquals(Block.AIR, world.getBlock(PLAYER_BLOCK_X, PLAYER_BLOCK_Y, PLAYER_BLOCK_Z));
        assertTrue(sent.isEmpty());
        assertFalse(Input.getMouse(MouseEvent.BUTTON3), "right click should be consumed");
    }

    @Test
    void placingABlockAwayFromThePlayerIsAccepted()
    {
        aimAt(PLAYER_BLOCK_X, PLAYER_BLOCK_Y - 1, PLAYER_BLOCK_Z);
        aimPlacementAt(40, 31, 32);

        Input.setMouse(MouseEvent.BUTTON3, true);
        player.tick();

        assertEquals(Block.DIRT, world.getBlock(40, 31, 32));
        assertEquals(1, sent.size());

        BlockPlacePacket packet = (BlockPlacePacket) sent.get(0);
        assertEquals(40, packet.getX());
        assertEquals(31, packet.getY());
        assertEquals(32, packet.getZ());
        assertEquals(Block.DIRT.getId(), packet.getBlockId());
    }

    @Test
    void placementUsesTheSelectedBlockType()
    {
        GameSettings.placeBlockTypeId = Block.STONE.getId();
        aimAt(PLAYER_BLOCK_X, PLAYER_BLOCK_Y - 1, PLAYER_BLOCK_Z);
        aimPlacementAt(41, 31, 32);

        Input.setMouse(MouseEvent.BUTTON3, true);
        player.tick();

        assertEquals(Block.STONE, world.getBlock(41, 31, 32));
        assertEquals(Block.STONE.getId(), ((BlockPlacePacket) sent.get(0)).getBlockId());
    }

    @Test
    void placementIsIgnoredWithoutATarget()
    {
        aimPlacementAt(40, 31, 32);

        Input.setMouse(MouseEvent.BUTTON3, true);
        player.tick();

        assertEquals(Block.AIR, world.getBlock(40, 31, 32));
        assertTrue(sent.isEmpty());
    }

    @Test
    void breakingABlockRemovesItAndSendsABlockPlacePacketForAir()
    {
        world.setBlock(40, 63, 40, Block.STONE);
        aimAt(40, 63, 40);

        Input.setMouse(MouseEvent.BUTTON1, true);
        player.tick();

        assertEquals(Block.AIR, world.getBlock(40, 63, 40));
        assertEquals(1, sent.size());

        BlockPlacePacket packet = (BlockPlacePacket) sent.get(0);
        assertEquals(40, packet.getX());
        assertEquals(63, packet.getY());
        assertEquals(40, packet.getZ());
        assertEquals(Block.AIR.getId(), packet.getBlockId());
    }

    @Test
    void bedrockCannotBeBroken()
    {
        world.setBlock(41, 63, 41, Block.BEDROCK);
        aimAt(41, 63, 41);

        Input.setMouse(MouseEvent.BUTTON1, true);
        player.tick();

        assertEquals(Block.BEDROCK, world.getBlock(41, 63, 41));
        assertTrue(sent.isEmpty());
    }

    @Test
    void breakingIsIgnoredWithoutATarget()
    {
        Input.setMouse(MouseEvent.BUTTON1, true);
        player.tick();

        assertTrue(sent.isEmpty());
    }

    @Test
    void sprintingFollowsTheControlKey()
    {
        Input.setKey(KeyEvent.VK_CONTROL, true);
        player.tick();
        assertTrue(player.isSprinting());

        Input.setKey(KeyEvent.VK_CONTROL, false);
        player.tick();
        assertFalse(player.isSprinting());
    }

    @Test
    void gravityPullsThePlayerDownWhenStandingInAir()
    {
        player.tick();

        assertEquals(0.0065f, player.getVelocity().getY(), 1e-6f);
        assertFalse(player.isOnGround());
    }

    @Test
    void tickScaleMultipliesGravity()
    {
        GameSettings.tickScale = 2.0f;

        player.tick();

        assertEquals(0.013f, player.getVelocity().getY(), 1e-6f);
    }
}
