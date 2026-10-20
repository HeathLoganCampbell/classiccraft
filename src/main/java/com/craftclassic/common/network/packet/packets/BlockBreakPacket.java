package com.craftclassic.common.network.packet.packets;

import com.craftclassic.common.entities.Player;
import com.craftclassic.common.network.ConnectionManager;
import com.craftclassic.common.network.packet.Packet;
import com.craftclassic.common.network.player.PlayerConnection;
import io.netty.buffer.ByteBuf;

public class BlockBreakPacket extends Packet
{
    private int x, y, z;

    public BlockBreakPacket() {
    }

    public BlockBreakPacket(int x, int y, int z, int blockId)
    {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public void decode(ByteBuf buf)
    {
        this.x = buf.readInt();
        this.y = buf.readInt();
        this.z = buf.readInt();
    }

    @Override
    public void encode(ByteBuf buf)
    {
        buf.writeInt(this.x);
        buf.writeInt(this.y);
        buf.writeInt(this.z);
    }

    @Override
    public void process(PlayerConnection playerConnection)
    {
        Player player = playerConnection.getPlayer();
        if (player == null)
            return;

        ConnectionManager.INSTANCE.fireBreakBlock(player, this.x, this.y, this.z);
        ConnectionManager.INSTANCE.broadcastPacketBesidesSelf(player, this);
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }
}
