package com.craftclassic.common.events;

import com.craftclassic.common.entities.Player;

/** Fired after a player has been added to the server and can receive packets. */
public class PlayerJoinEvent
{
    private final Player player;

    public PlayerJoinEvent(Player player)
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
        return "PlayerJoinEvent{player=" + player.getName() + "}";
    }
}
