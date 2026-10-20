package com.craftclassic.common.network;

import com.craftclassic.common.entities.Player;
import com.craftclassic.common.network.packet.Packet;
import com.craftclassic.common.network.packet.packets.EntitySpawnPacket;
import com.craftclassic.common.network.player.PlayerConnection;
import com.craftclassic.common.utils.Location;

import java.util.Map;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class ConnectionManager
{
    public static ConnectionManager INSTANCE = new ConnectionManager();

    private final Map<PlayerConnection, Player> connectionPlayerMap = new ConcurrentHashMap<>();
    private final List<Consumer<Player>> joinListeners = new CopyOnWriteArrayList<>();
    private volatile CommandHandler commandHandler;

    @FunctionalInterface
    public interface CommandHandler
    {
        boolean handle(Player player, String message);
    }

    public void setCommandHandler(CommandHandler handler)
    {
        this.commandHandler = handler;
    }

    public boolean handleCommand(Player player, String message)
    {
        CommandHandler handler = this.commandHandler;
        return handler != null && handler.handle(player, message);
    }

    public Player findPlayer(String name)
    {
        for(Player player : this.connectionPlayerMap.values())
        {
            if(player.getName().equalsIgnoreCase(name))
                return player;
        }
        return null;
    }

    public String playerNames()
    {
        if(this.connectionPlayerMap.isEmpty())
            return "(none)";

        StringBuilder names = new StringBuilder();
        for(Player player : this.connectionPlayerMap.values())
        {
            if(names.length() > 0)
                names.append(", ");
            names.append(player.getName());
        }
        return names.toString();
    }

    public void startPlayer(PlayerConnection playerConnection, String username)
    {
        int entityId = new Random().nextInt(10000);

        Player player = new Player(playerConnection, username, entityId);
        playerConnection.setPlayer(player);
        player.setLocation(new Location(null, 95, 2, 95));

        this.connectionPlayerMap.put(player.getPlayerConnection(), player);

        broadcastPacketBesidesSelf(player, new EntitySpawnPacket(player.getEntityId(), player.getLocation()));
        playerConnection.sendPacket(new EntitySpawnPacket(0, player.getLocation()));

        // send exist players to player
        getConnectedPlayers().forEach((otherPlayerConnection, otherPlayer) -> {
            if(otherPlayer != player)
            {
                playerConnection.sendPacket(new EntitySpawnPacket(otherPlayer.getEntityId(), otherPlayer.getLocation()));
            }
        });

        for (Consumer<Player> listener : this.joinListeners)
            listener.accept(player);
    }

    public void addJoinListener(Consumer<Player> listener)
    {
        this.joinListeners.add(listener);
    }

    public Map<PlayerConnection, Player> getConnectedPlayers()
    {
        return connectionPlayerMap;
    }

    public void broadcastPacket(Packet packet)
    {
        connectionPlayerMap.forEach((playerConnection, player) -> playerConnection.sendPacket(packet));
    }

    public void broadcastPacketBesidesSelf(Player selfPlayer, Packet packet)
    {
        connectionPlayerMap.forEach((playerConnection, player) -> {
            if(selfPlayer != player)
            {
                playerConnection.sendPacket(packet);
            }
        });
    }

    public void removePlayerConnection(PlayerConnection playerConnection)
    {
        this.connectionPlayerMap.remove(playerConnection);
    }
}
