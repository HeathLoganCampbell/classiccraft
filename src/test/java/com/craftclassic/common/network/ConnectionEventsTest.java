package com.craftclassic.common.network;

import com.craftclassic.common.entities.Player;
import com.craftclassic.common.events.PlayerBreakBlockEvent;
import com.craftclassic.common.events.PlayerMoveEvent;
import com.craftclassic.common.events.PlayerPlaceBlockEvent;
import com.craftclassic.common.network.packet.packets.BlockBreakPacket;
import com.craftclassic.common.network.packet.packets.BlockPlacePacket;
import com.craftclassic.common.network.packet.packets.EntityPositionPacket;
import com.craftclassic.common.network.player.PlayerConnection;
import com.craftclassic.common.utils.Location;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConnectionEventsTest
{
    private ConnectionManager previous;
    private ConnectionManager connections;
    private Player player;

    @BeforeEach
    void swapConnectionManager()
    {
        previous = ConnectionManager.INSTANCE;
        connections = new ConnectionManager();
        ConnectionManager.INSTANCE = connections;

        PlayerConnection connection = new PlayerConnection(new EmbeddedChannel());
        player = new Player(connection, "Alice", 1);
        connection.setPlayer(player);
        player.setLocation(new Location(null, 0, 0, 0));
        connections.getConnectedPlayers().put(connection, player);
    }

    @AfterEach
    void restoreConnectionManager()
    {
        ConnectionManager.INSTANCE = previous;
    }

    @Test
    void breakPacketsFireBreakEvents()
    {
        List<PlayerBreakBlockEvent> events = new ArrayList<>();
        connections.addBreakBlockListener(events::add);

        new BlockBreakPacket(1, 2, 3, 0).process(player.getPlayerConnection());

        assertEquals(1, events.size());
        assertEquals(player, events.get(0).getPlayer());
        assertEquals(1, events.get(0).getX());
        assertEquals(2, events.get(0).getY());
        assertEquals(3, events.get(0).getZ());
    }

    @Test
    void placePacketsFirePlaceEventsWithThePlacedBlock()
    {
        List<PlayerPlaceBlockEvent> events = new ArrayList<>();
        connections.addPlaceBlockListener(events::add);

        new BlockPlacePacket(4, 5, 6, 3).process(player.getPlayerConnection());

        assertEquals(1, events.size());
        assertEquals(player, events.get(0).getPlayer());
        assertEquals(4, events.get(0).getX());
        assertEquals(5, events.get(0).getY());
        assertEquals(6, events.get(0).getZ());
        assertEquals(3, events.get(0).getBlockId());
    }

    @Test
    void positionPacketsFireMoveEventsAndUpdateTheServerPlayer()
    {
        List<PlayerMoveEvent> events = new ArrayList<>();
        connections.addMoveListener(events::add);

        new EntityPositionPacket(0, 10.5, 20.5, 30.5).process(player.getPlayerConnection());

        assertEquals(1, events.size());
        PlayerMoveEvent event = events.get(0);
        assertEquals(player, event.getPlayer());
        assertEquals(0.0, event.getFromX());
        assertEquals(0.0, event.getFromY());
        assertEquals(0.0, event.getFromZ());
        assertEquals(10.5, event.getToX());
        assertEquals(20.5, event.getToY());
        assertEquals(30.5, event.getToZ());

        assertEquals(10.5f, player.getLocation().getX(), 1e-6f);
        assertEquals(20.5f, player.getLocation().getY(), 1e-6f);
        assertEquals(30.5f, player.getLocation().getZ(), 1e-6f);
    }

    @Test
    void removingAConnectionFiresDisconnect()
    {
        List<Player> disconnected = new ArrayList<>();
        connections.addDisconnectListener(disconnected::add);

        connections.removePlayerConnection(player.getPlayerConnection());

        assertEquals(1, disconnected.size());
        assertEquals(player, disconnected.get(0));
        assertEquals(0, connections.getConnectedPlayers().size());
    }
}
