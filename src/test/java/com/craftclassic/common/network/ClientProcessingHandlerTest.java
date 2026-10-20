package com.craftclassic.common.network;

import com.craftclassic.common.blocks.Block;
import com.craftclassic.common.entities.Entity;
import com.craftclassic.common.network.packet.packets.BlockBreakPacket;
import com.craftclassic.common.network.packet.packets.BlockPlacePacket;
import com.craftclassic.common.network.packet.packets.ChatMessagePacket;
import com.craftclassic.common.network.packet.packets.EntityPositionPacket;
import com.craftclassic.common.network.packet.packets.EntitySpawnPacket;
import com.craftclassic.common.network.packet.packets.PlayerConnectPacket;
import com.craftclassic.common.network.pipeline.handlers.ClientProcessingHandler;
import com.craftclassic.common.utils.Location;
import com.craftclassic.common.world.World;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientProcessingHandlerTest
{
    private ClientBridge.View previousView;
    private RecordingView view;

    @BeforeEach
    void setUp()
    {
        previousView = ClientBridge.view;
        view = new RecordingView();
        ClientBridge.view = view;
    }

    @AfterEach
    void tearDown()
    {
        ClientBridge.view = previousView;
    }

    @Test
    void channelActiveSendsTheConnectPacket()
    {
        EmbeddedChannel channel = new EmbeddedChannel(new ClientProcessingHandler("Alex"));

        try
        {
            PlayerConnectPacket connect = channel.readOutbound();
            assertNotNull(connect);
            assertEquals("Alex", connect.getUsername());
        }
        finally
        {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void chatMessagesReachTheClientView()
    {
        EmbeddedChannel channel = channel();

        try
        {
            ChatMessagePacket packet = new ChatMessagePacket("hello");
            channel.writeInbound(packet);

            assertEquals(1, view.chats.size());
            // A freshly built packet has no sender yet; the server stamps it
            // in ChatMessagePacket.process before broadcasting.
            assertEquals(": hello", view.chats.get(0));
        }
        finally
        {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void spawnPacketsCreateOtherPlayers()
    {
        EmbeddedChannel channel = channel();

        try
        {
            channel.writeInbound(new EntitySpawnPacket(7, 10.5, 20.25, 30.75));

            assertEquals(1, view.added.size());
            Entity entity = view.added.get(0);
            assertEquals(7, entity.getEntityId());
            assertEquals(10.5f, entity.getLocation().getX(), 1e-6f);
            assertEquals(20.25f, entity.getLocation().getY(), 1e-6f);
            assertEquals(30.75f, entity.getLocation().getZ(), 1e-6f);
        }
        finally
        {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void spawnPacketsForEntityZeroAreIgnored()
    {
        EmbeddedChannel channel = channel();

        try
        {
            channel.writeInbound(new EntitySpawnPacket(0, 1, 2, 3));

            assertTrue(view.added.isEmpty());
        }
        finally
        {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void positionPacketsMoveTheMatchingEntity()
    {
        Entity entity = new Entity("Bunny", 7);
        entity.setLocation(new Location(view.world, 0, 0, 0));
        view.world.addEntity(entity);
        EmbeddedChannel channel = channel();

        try
        {
            channel.writeInbound(new EntityPositionPacket(7, 4.5, 5.25, 6.75));

            assertEquals(4.5f, entity.getLocation().getX(), 1e-6f);
            assertEquals(5.25f, entity.getLocation().getY(), 1e-6f);
            assertEquals(6.75f, entity.getLocation().getZ(), 1e-6f);
        }
        finally
        {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void positionPacketsForUnknownEntitiesAreIgnored()
    {
        EmbeddedChannel channel = channel();

        try
        {
            channel.writeInbound(new EntityPositionPacket(404, 1, 2, 3));

            assertTrue(view.world.getEntities().isEmpty());
        }
        finally
        {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void blockPlacePacketsChangeTheWorld()
    {
        EmbeddedChannel channel = channel();

        try
        {
            channel.writeInbound(new BlockPlacePacket(12, 13, 14, Block.DIRT.getId()));

            assertEquals(Block.DIRT, view.world.getBlock(12, 13, 14));
        }
        finally
        {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void blockBreakPacketsClearTheWorld()
    {
        view.world.setBlock(12, 13, 14, Block.STONE);
        EmbeddedChannel channel = channel();

        try
        {
            channel.writeInbound(new BlockBreakPacket(12, 13, 14, 0));

            assertEquals(Block.AIR, view.world.getBlock(12, 13, 14));
        }
        finally
        {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void packetsAreDroppedWhenNoClientIsAttached()
    {
        ClientBridge.view = null;
        EmbeddedChannel channel = channel();

        try
        {
            channel.writeInbound(new BlockPlacePacket(12, 13, 14, Block.DIRT.getId()));

            assertEquals(Block.AIR, view.world.getBlock(12, 13, 14));
            assertTrue(view.chats.isEmpty());
        }
        finally
        {
            channel.finishAndReleaseAll();
        }
    }

    private static EmbeddedChannel channel()
    {
        return new EmbeddedChannel(new ClientProcessingHandler("Tester"));
    }

    private static final class RecordingView implements ClientBridge.View
    {
        private final World world = new World(64, 64, 64, 1, false);
        private final List<Entity> added = new ArrayList<>();
        private final List<String> chats = new ArrayList<>();

        @Override
        public World getWorld()
        {
            return world;
        }

        @Override
        public void addEntity(Entity entity)
        {
            added.add(entity);
        }

        @Override
        public void onChatMessage(String sender, String message)
        {
            chats.add(sender + ": " + message);
        }
    }
}
