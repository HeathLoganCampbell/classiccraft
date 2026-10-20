package com.craftclassic.server.plugin;

import com.craftclassic.common.entities.Player;
import com.craftclassic.common.events.PlayerJoinEvent;
import com.craftclassic.common.events.PlayerJoinEvent;
import com.craftclassic.common.network.packet.packets.ChatMessagePacket;
import com.craftclassic.common.network.player.PlayerConnection;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WelcomePluginTest
{
    @Test
    void sendsTheAquaWelcomeToTheJoiningPlayer()
    {
        EmbeddedChannel channel = new EmbeddedChannel();
        PlayerConnection connection = new PlayerConnection(channel);
        Player player = new Player(connection, "Alice", 1);
        connection.setPlayer(player);

        new WelcomePlugin().onPlayerJoin(new PlayerJoinEvent(player));
        channel.flush();

        Object outbound = channel.readOutbound();
        assertInstanceOf(ChatMessagePacket.class, outbound);

        ChatMessagePacket packet = (ChatMessagePacket) outbound;
        assertEquals("&bWelcome to the server", packet.getMessage());
        assertEquals("", packet.getSender());
    }

    @Test
    void pluginManagerFiresJoinHooks()
    {
        PluginManager manager = new PluginManager();
        boolean[] fired = { false };

        manager.register(new ServerPlugin()
        {
            @Override
            public void onPlayerJoin(PlayerJoinEvent event)
            {
                fired[0] = true;
            }
        });

        manager.firePlayerJoin(new Player(null, "Bob", 2));
        assertTrue(fired[0]);
    }
}
