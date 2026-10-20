package com.craftclassic.common.network;

import com.craftclassic.common.network.packet.packets.PingPacket;
import com.craftclassic.common.network.player.PlayerConnection;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class PingPacketTest
{
    @Test
    void roundTripsTheTimestamp()
    {
        ByteBuf buffer = Unpooled.buffer();
        try
        {
            new PingPacket(123456789L).encode(buffer);

            PingPacket decoded = new PingPacket();
            decoded.decode(buffer);

            assertEquals(123456789L, decoded.getSentNanos());
        }
        finally
        {
            buffer.release();
        }
    }

    @Test
    void serverEchoesTheProbeOnTheSameConnection()
    {
        EmbeddedChannel channel = new EmbeddedChannel();
        PlayerConnection connection = new PlayerConnection(channel);

        new PingPacket(42L).process(connection);
        channel.flush();

        Object outbound = channel.readOutbound();
        assertInstanceOf(PingPacket.class, outbound);
        assertEquals(42L, ((PingPacket) outbound).getSentNanos());
    }
}
