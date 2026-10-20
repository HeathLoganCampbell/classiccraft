package com.craftclassic.common.events;

import com.craftclassic.common.blocks.Block;
import com.craftclassic.common.entities.Player;

/**
 * Fired when a client reports that it placed a block.
 *
 * <p>Like {@link PlayerBreakBlockEvent}, placement is client-authoritative:
 * the client has already changed its world, so plugins observe the event
 * rather than veto it.</p>
 */
public class PlayerPlaceBlockEvent
{
    private final Player player;
    private final int x, y, z;
    private final int blockId;

    public PlayerPlaceBlockEvent(Player player, int x, int y, int z, int blockId)
    {
        this.player = player;
        this.x = x;
        this.y = y;
        this.z = z;
        this.blockId = blockId;
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

    public int getBlockId()
    {
        return blockId;
    }

    public Block getBlock()
    {
        return Block.getBlockById(blockId);
    }

    @Override
    public String toString()
    {
        return "PlayerPlaceBlockEvent{player=" + player.getName()
                + ", x=" + x + ", y=" + y + ", z=" + z
                + ", blockId=" + blockId + '}';
    }
}
