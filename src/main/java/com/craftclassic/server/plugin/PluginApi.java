package com.craftclassic.server.plugin;

import com.craftclassic.common.blocks.Block;
import com.craftclassic.common.entities.Player;
import com.craftclassic.common.network.ConnectionManager;
import com.craftclassic.common.network.packet.packets.ChatMessagePacket;
import com.craftclassic.common.network.player.PlayerConnection;
import com.craftclassic.common.utils.Location;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The object plugins see as {@code server} in JavaScript.
 *
 * <p>Only proxies cross the language boundary: plugins get no host class
 * access, so {@code Java.type(...)} cannot reach the JVM. Everything a plugin
 * needs is a plain function on this object.</p>
 */
final class PluginApi
{
    private final String pluginName;
    private final ConnectionManager connections;

    PluginApi(String pluginName, ConnectionManager connections)
    {
        this.pluginName = pluginName;
        this.connections = connections;
    }

    ProxyObject asProxy()
    {
        Map<String, Object> members = new LinkedHashMap<>();

        members.put("broadcast", (ProxyExecutable) arguments ->
        {
            this.connections.broadcastPacket(
                    new ChatMessagePacket("", chatSafe(string(arguments, 0, "message"))));
            return null;
        });

        members.put("send", (ProxyExecutable) arguments ->
        {
            String playerName = string(arguments, 0, "player name");
            String message = chatSafe(string(arguments, 1, "message"));

            Player target = this.connections.findPlayer(playerName);
            if (target == null)
                return false;

            PlayerConnection connection = target.getPlayerConnection();
            if (connection == null)
                return false;

            connection.sendPacket(new ChatMessagePacket("", message));
            return true;
        });

        members.put("players", (ProxyExecutable) arguments ->
        {
            List<Object> names = new ArrayList<>();
            for (Player player : this.connections.getConnectedPlayers().values())
                names.add(player.getName());

            return ProxyArray.fromList(names);
        });

        members.put("log", (ProxyExecutable) arguments ->
        {
            System.out.println("[plugin:" + this.pluginName + "] "
                    + string(arguments, 0, "message"));
            return null;
        });

        return ProxyObject.fromMap(members);
    }

    /** Snapshot of a player, safe to hand to a plugin on the plugin thread. */
    ProxyObject player(Player player)
    {
        Map<String, Object> members = new LinkedHashMap<>();
        members.put("name", player.getName());
        members.put("entityId", player.getEntityId());

        Location location = player.getLocation();
        if (location != null)
        {
            members.put("x", location.getX());
            members.put("y", location.getY());
            members.put("z", location.getZ());
        }

        return ProxyObject.fromMap(members);
    }

    /** {@code { player: Player }} event shape shared by join and disconnect. */
    ProxyObject playerEvent(Player player)
    {
        Map<String, Object> members = new LinkedHashMap<>();
        members.put("player", player(player));
        return ProxyObject.fromMap(members);
    }

    /** {@code { player, x, y, z }} for block events. */
    ProxyObject blockEvent(Player player, int x, int y, int z)
    {
        Map<String, Object> members = new LinkedHashMap<>();
        members.put("player", player(player));
        members.put("x", x);
        members.put("y", y);
        members.put("z", z);
        return ProxyObject.fromMap(members);
    }

    /** {@code { player, x, y, z, blockId }} for placement. */
    ProxyObject placeBlockEvent(Player player, int x, int y, int z, int blockId)
    {
        Map<String, Object> members = new LinkedHashMap<>();
        members.put("player", player(player));
        members.put("x", x);
        members.put("y", y);
        members.put("z", z);
        members.put("blockId", blockId);
        return ProxyObject.fromMap(members);
    }

    /** {@code { tick, ticksPerSecond }} for the server tick. */
    ProxyObject tickEvent(long tick, int ticksPerSecond)
    {
        Map<String, Object> members = new LinkedHashMap<>();
        members.put("tick", tick);
        members.put("ticksPerSecond", ticksPerSecond);
        return ProxyObject.fromMap(members);
    }

    /** {@code { player, from: {x,y,z}, to: {x,y,z} }} for movement. */
    ProxyObject moveEvent(Player player,
                          double fromX, double fromY, double fromZ,
                          double toX, double toY, double toZ)
    {
        Map<String, Object> members = new LinkedHashMap<>();
        members.put("player", player(player));
        members.put("from", position(fromX, fromY, fromZ));
        members.put("to", position(toX, toY, toZ));
        return ProxyObject.fromMap(members);
    }

    /** Block id constants, so scripts can write {@code BlockId.STONE}. */
    static ProxyObject blockIds()
    {
        Map<String, Object> ids = new LinkedHashMap<>();
        ids.put("AIR", Block.AIR.getId());
        ids.put("DIRT", Block.DIRT.getId());
        ids.put("GRASS", Block.GRASS.getId());
        ids.put("STONE", Block.STONE.getId());
        ids.put("LAVA", Block.LAVA.getId());
        ids.put("WATER", Block.WATER.getId());
        ids.put("BEDROCK", Block.BEDROCK.getId());
        ids.put("LOG", Block.LOG.getId());
        ids.put("LEAVES", Block.LEAVES.getId());
        ids.put("BRICK", Block.BRICK.getId());
        ids.put("COBBLESTONE", Block.COBBLESTONE.getId());
        ids.put("GLASS", Block.GLASS.getId());
        ids.put("TNT", Block.TNT.getId());
        ids.put("SAND", Block.SAND.getId());
        return ProxyObject.fromMap(ids);
    }

    private static ProxyObject position(double x, double y, double z)
    {
        Map<String, Object> members = new LinkedHashMap<>();
        members.put("x", x);
        members.put("y", y);
        members.put("z", z);
        return ProxyObject.fromMap(members);
    }

    private static String string(Value[] arguments, int index, String name)
    {
        if (arguments.length <= index || arguments[index].isNull())
            throw new IllegalArgumentException("expected a " + name);

        return arguments[index].asString();
    }

    /**
     * The chat packet writes a byte length that clients cap at 256, so cut by
     * UTF-8 bytes rather than characters (a line of emoji can be longer than it
     * looks).
     */
    static String chatSafe(String message)
    {
        if (message == null)
            return "";

        if (message.getBytes(StandardCharsets.UTF_8).length <= ChatMessagePacket.MAX_LENGTH)
            return message;

        int end = message.length();
        while (end > 0 && message.substring(0, end).getBytes(StandardCharsets.UTF_8).length
                > ChatMessagePacket.MAX_LENGTH)
            end--;

        return message.substring(0, end);
    }
}
