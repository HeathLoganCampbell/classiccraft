package com.craftclassic.common.events;

import com.craftclassic.common.entities.Player;

/**
 * Fired when a client reports that it broke a block.
 *
 * <p>Movement and block changes are client-authoritative, so this is a
 * notification rather than a veto: the client has already removed the block.
 * Plugins can react (count it, announce it, score it) but cannot undo it.</p>
 */
public class PlayerBreakBlockEvent
{
    private final Player player;
    private final int x, y, z;

    public PlayerBreakBlockEvent(Player player, int x, int y, int z)
    {
        this.player = player;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Player getPlayer()
    {
        return player;
    }

    public int getX()
    {
        return x;
    }

    public int getY()
    {
        return y;
    }

    public int getZ()
    {
        return z;
    }

    @Override
    public String toString()
    {
        return "PlayerBreakBlockEvent{player=" + player.getName()
                + ", x=" + x + ", y=" + y + ", z=" + z + '}';
    }
}
