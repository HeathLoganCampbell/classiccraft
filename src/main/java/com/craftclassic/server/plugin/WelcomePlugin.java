package com.craftclassic.server.plugin;

import com.craftclassic.common.entities.Player;
import com.craftclassic.common.network.packet.packets.ChatMessagePacket;
import com.craftclassic.common.network.player.PlayerConnection;

/** Sends the aqua welcome line to each player as they join. */
public final class WelcomePlugin implements ServerPlugin
{
    public static final String MESSAGE = "&bWelcome to the server";

    @Override
    public void onPlayerJoin(Player player)
    {
        PlayerConnection connection = player.getPlayerConnection();
        if (connection != null)
            connection.sendPacket(new ChatMessagePacket(MESSAGE));
    }
}
