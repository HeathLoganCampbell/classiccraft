package com.craftclassic.common.events;

import com.craftclassic.common.entities.Player;

/** Fired when a player's channel closes and they leave the server. */
public class PlayerDisconnectEvent
{
    private final Player player;

    public PlayerDisconnectEvent(Player player)
    {
        this.player = player;
    }

    public Player getPlayer()
    {
        return player;
    }

    @Override
    public String toString()
    {
        return "PlayerDisconnectEvent{player=" + player.getName() + "}";
    }
}
