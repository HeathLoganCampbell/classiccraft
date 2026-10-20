package com.craftclassic.common.events;

import com.craftclassic.common.entities.Player;

/**
 * Fired for each client position update, before it is relayed to other
 * players. Movement is client-authoritative and sent every tick, so this is a
 * high-frequency notification rather than a veto point.
 */
public class PlayerMoveEvent
{
    private final Player player;
    private final double fromX, fromY, fromZ;
    private final double toX, toY, toZ;

    public PlayerMoveEvent(Player player,
                           double fromX, double fromY, double fromZ,
                           double toX, double toY, double toZ)
    {
        this.player = player;
        this.fromX = fromX;
        this.fromY = fromY;
        this.fromZ = fromZ;
        this.toX = toX;
        this.toY = toY;
        this.toZ = toZ;
    }

    public Player getPlayer()
    {
        return player;
    }

    public double getFromX()
    {
        return fromX;
    }

    public double getFromY()
    {
        return fromY;
    }

    public double getFromZ()
    {
        return fromZ;
    }

    public double getToX()
    {
        return toX;
    }

    public double getToY()
    {
        return toY;
    }

    public double getToZ()
    {
        return toZ;
    }

    @Override
    public String toString()
    {
        return "PlayerMoveEvent{player=" + player.getName()
                + ", from=(" + fromX + "," + fromY + "," + fromZ + ')'
                + ", to=(" + toX + "," + toY + "," + toZ + ")}";
    }
}
