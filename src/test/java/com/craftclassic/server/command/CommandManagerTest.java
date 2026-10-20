package com.craftclassic.server.command;

import com.craftclassic.common.entities.Player;
import com.craftclassic.common.network.ConnectionManager;
import com.craftclassic.common.network.packet.packets.ChatMessagePacket;
import com.craftclassic.common.network.player.PlayerConnection;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandManagerTest
{
    private final List<PlayerConnection> connections = new ArrayList<>();

    @BeforeEach
    void installHandler()
    {
        ConnectionManager.INSTANCE.setCommandHandler(CommandManager::handle);
    }

    @AfterEach
    void removePlayers()
    {
        for (PlayerConnection connection : connections)
            ConnectionManager.INSTANCE.removePlayerConnection(connection);

        connections.clear();
    }

    private static PlayerConnection connect(String name)
    {
        EmbeddedChannel channel = new EmbeddedChannel();
        PlayerConnection connection = new PlayerConnection(channel);
        ConnectionManager.INSTANCE.startPlayer(connection, name);
        drain(channel);
        return connection;
    }

    private static List<Object> drain(EmbeddedChannel channel)
    {
        channel.flush();
        List<Object> packets = new ArrayList<>();
        Object packet;
        while ((packet = channel.readOutbound()) != null)
            packets.add(packet);
        return packets;
    }

    private static List<ChatMessagePacket> chats(List<Object> packets)
    {
        List<ChatMessagePacket> chats = new ArrayList<>();
        for (Object packet : packets)
        {
            if (packet instanceof ChatMessagePacket)
                chats.add((ChatMessagePacket) packet);
        }
        return chats;
    }

    @Test
    void pingRepliesInsteadOfBroadcasting()
    {
        PlayerConnection alice = connect("Alice");
        PlayerConnection bob = connect("Bob");
        connections.add(alice);
        connections.add(bob);

        drain((EmbeddedChannel) alice.getChannel());
        drain((EmbeddedChannel) bob.getChannel());

        new ChatMessagePacket("/ping").process(alice);

        List<ChatMessagePacket> replies = chats(drain((EmbeddedChannel) alice.getChannel()));
        assertEquals(1, replies.size());
        assertEquals("&aPong!", replies.get(0).getMessage());
        assertEquals("", replies.get(0).getSender());

        assertTrue(chats(drain((EmbeddedChannel) bob.getChannel())).isEmpty(),
                "commands must not be broadcast");
    }

    @Test
    void msgRoutesPrivatelyToTheTarget()
    {
        PlayerConnection alice = connect("Alice");
        PlayerConnection bob = connect("Bob");
        connections.add(alice);
        connections.add(bob);

        drain((EmbeddedChannel) alice.getChannel());
        drain((EmbeddedChannel) bob.getChannel());

        new ChatMessagePacket("/msg Bob hello there").process(alice);

        List<ChatMessagePacket> toBob = chats(drain((EmbeddedChannel) bob.getChannel()));
        assertEquals(1, toBob.size());
        assertEquals("Alice", toBob.get(0).getSender());
        assertEquals("&d[PM] hello there", toBob.get(0).getMessage());

        List<ChatMessagePacket> toAlice = chats(drain((EmbeddedChannel) alice.getChannel()));
        assertEquals(1, toAlice.size());
        assertEquals("&d[PM -> Bob] hello there", toAlice.get(0).getMessage());
    }

    @Test
    void unknownCommandRepliesWithHelpHint()
    {
        PlayerConnection connection = new PlayerConnection(new EmbeddedChannel());
        Player alice = new Player(connection, "Alice", 1);
        connection.setPlayer(alice);

        CommandManager.handle(alice, "/nope");

        EmbeddedChannel channel = (EmbeddedChannel) connection.getChannel();
        List<ChatMessagePacket> replies = chats(drain(channel));
        assertEquals(1, replies.size());
        assertTrue(replies.get(0).getMessage().startsWith("&cUnknown command"));
    }
}
