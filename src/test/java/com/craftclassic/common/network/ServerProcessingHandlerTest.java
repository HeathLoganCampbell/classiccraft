package com.craftclassic.common.network;

import com.craftclassic.common.entities.Player;
import com.craftclassic.common.network.pipeline.handlers.ServerProcessingHandler;
import com.craftclassic.common.network.player.PlayerConnection;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServerProcessingHandlerTest
{
    private ConnectionManager previous;

    @BeforeEach
    void swapConnectionManager()
    {
        previous = ConnectionManager.INSTANCE;
        ConnectionManager.INSTANCE = new ConnectionManager();
    }

    @AfterEach
    void restoreConnectionManager()
    {
        ConnectionManager.INSTANCE = previous;
    }

    @Test
    void closingTheChannelRemovesThePlayerAndFiresDisconnect()
    {
        PacketProcessor processor = new PacketProcessor();
        EmbeddedChannel channel = new EmbeddedChannel(new ServerProcessingHandler(processor));

        ChannelHandlerContext context = channel.pipeline()
                .context(ServerProcessingHandler.class);
        processor.createPlayerConnection(context);

        PlayerConnection connection = processor.getPlayerConnection(context);
        Player player = new Player(connection, "Alice", 1);
        connection.setPlayer(player);
        ConnectionManager.INSTANCE.getConnectedPlayers().put(connection, player);

        List<Player> disconnected = new ArrayList<>();
        ConnectionManager.INSTANCE.addDisconnectListener(disconnected::add);

        channel.close();
        channel.runPendingTasks();

        assertEquals(1, disconnected.size());
        assertEquals(player, disconnected.get(0));
        assertEquals(0, ConnectionManager.INSTANCE.getConnectedPlayers().size());
    }
}
