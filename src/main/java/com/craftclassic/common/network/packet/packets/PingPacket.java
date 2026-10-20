package com.craftclassic.common.network.packet.packets;

import com.craftclassic.common.network.packet.Packet;
import com.craftclassic.common.network.player.PlayerConnection;
import io.netty.buffer.ByteBuf;

/**
 * Round-trip probe. The server echoes it back untouched on the Netty thread so
 * the measured latency is the network round trip, not the 20 TPS tick queue.
 */
public class PingPacket extends Packet
{
    private long sentNanos;

    public PingPacket() {}

    public PingPacket(long sentNanos)
    {
        this.sentNanos = sentNanos;
    }

    @Override
    public void decode(ByteBuf buf)
    {
        this.sentNanos = buf.readLong();
    }

    @Override
    public void encode(ByteBuf buf)
    {
        buf.writeLong(this.sentNanos);
    }

    @Override
    public void process(PlayerConnection playerConnection)
    {
        playerConnection.sendPacket(this);
    }

    public long getSentNanos()
    {
        return sentNanos;
    }
}
