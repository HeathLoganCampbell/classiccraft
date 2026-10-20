package com.craftclassic.server.command;

import com.craftclassic.common.entities.Player;
import com.craftclassic.common.network.ConnectionManager;
import com.craftclassic.common.network.packet.packets.ChatMessagePacket;

/**
 * Slash commands. Chat packets that start with '/' are routed here by
 * ChatMessagePacket.process instead of being broadcast.
 */
public final class CommandManager
{
    private CommandManager() {}

    public static boolean handle(Player player, String message)
    {
        if(message == null || !message.startsWith("/"))
            return false;

        String body = message.substring(1).trim();
        String[] parts = body.isEmpty() ? new String[0] : body.split("\\s+", 3);

        if(parts.length == 0)
        {
            reply(player, "&cTry /help");
            return true;
        }

        switch(parts[0].toLowerCase())
        {
            case "ping":
                reply(player, "&aPong!");
                return true;

            case "help":
                reply(player, "&eCommands: &f/ping, /msg <player> <text>, /list");
                return true;

            case "list":
                reply(player, "&eOnline: &f" + ConnectionManager.INSTANCE.playerNames());
                return true;

            case "msg":
            case "tell":
            case "w":
                message(player, parts);
                return true;

            default:
                reply(player, "&cUnknown command: /" + parts[0] + " &7(try /help)");
                return true;
        }
    }

    private static void message(Player sender, String[] parts)
    {
        if(parts.length < 3)
        {
            reply(sender, "&cUsage: /msg <player> <message>");
            return;
        }

        Player target = ConnectionManager.INSTANCE.findPlayer(parts[1]);
        if(target == null)
        {
            reply(sender, "&cNo player named " + parts[1]);
            return;
        }

        if(target == sender)
        {
            reply(sender, "&cYou cannot message yourself");
            return;
        }

        if(target.getPlayerConnection() != null)
            target.getPlayerConnection().sendPacket(
                    new ChatMessagePacket(sender.getName(), "&d[PM] " + parts[2]));

        reply(sender, "&d[PM -> " + target.getName() + "] " + parts[2]);
    }

    private static void reply(Player player, String message)
    {
        if(player.getPlayerConnection() != null)
            player.getPlayerConnection().sendPacket(new ChatMessagePacket("", message));
    }
}
