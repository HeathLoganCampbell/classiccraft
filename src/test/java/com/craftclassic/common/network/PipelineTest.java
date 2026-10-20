package com.craftclassic.common.network;

import com.craftclassic.common.network.packet.Packet;
import com.craftclassic.common.network.packet.PacketRegistry;
import com.craftclassic.common.network.packet.packets.BlockPlacePacket;
import com.craftclassic.common.network.pipeline.coders.PacketDecoder;
import com.craftclassic.common.network.pipeline.coders.PacketEncoder;
import com.craftclassic.common.network.pipeline.encapsulation.Decapsulator;
import com.craftclassic.common.network.pipeline.encapsulation.Encapsulator;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PipelineTest
{
    @Test
    void encapsulatorWritesALengthPrefixThenThePayload()
    {
        EmbeddedChannel channel = new EmbeddedChannel(new Encapsulator());
        ByteBuf length = null;
        ByteBuf body = null;

        try
        {
            assertTrue(channel.writeOutbound(Unpooled.buffer().writeInt(0x01020304)));

            length = channel.readOutbound();
            body = channel.readOutbound();

            assertNotNull(length);
            assertNotNull(body);
            assertEquals(4, length.readInt());
            assertEquals(0x01020304, body.readInt());
        }
        finally
        {
            release(length);
            release(body);
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void decapsulatorReadsAFrameOutOfTheLengthPrefix()
    {
        EmbeddedChannel channel = new EmbeddedChannel(new Decapsulator());
        ByteBuf payload = null;

        try
        {
            assertTrue(channel.writeInbound(Unpooled.buffer().writeInt(4).writeInt(42)));

            payload = channel.readInbound();
            assertNotNull(payload);
            assertEquals(42, payload.readInt());
        }
        finally
        {
            release(payload);
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void decapsulatorWaitsForAFullFrame()
    {
        EmbeddedChannel channel = new EmbeddedChannel(new Decapsulator());
        ByteBuf payload = null;

        try
        {
            assertFalse(channel.writeInbound(Unpooled.buffer().writeInt(4).writeShort(0x0102)));
            assertTrue(channel.writeInbound(Unpooled.buffer().writeShort(0x0304)));

            payload = channel.readInbound();
            assertNotNull(payload);
            assertEquals(0x01020304, payload.readInt());
        }
        finally
        {
            release(payload);
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void decapsulatorHandlesBackToBackFrames()
    {
        EmbeddedChannel channel = new EmbeddedChannel(new Decapsulator());
        ByteBuf first = null;
        ByteBuf second = null;

        try
        {
            ByteBuf frames = Unpooled.buffer()
                    .writeInt(4).writeInt(11)
                    .writeInt(4).writeInt(22);

            assertTrue(channel.writeInbound(frames));

            first = channel.readInbound();
            second = channel.readInbound();

            assertNotNull(first);
            assertNotNull(second);
            assertEquals(11, first.readInt());
            assertEquals(22, second.readInt());
        }
        finally
        {
            release(first);
            release(second);
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void packetEncoderWritesTheRegistryIdFirst()
    {
        EmbeddedChannel channel = new EmbeddedChannel(new PacketEncoder());
        ByteBuf wire = null;

        try
        {
            assertTrue(channel.writeOutbound(new BlockPlacePacket(1, 2, 3, 4)));

            wire = channel.readOutbound();
            assertNotNull(wire);
            assertEquals(6, wire.readInt()); // BlockPlacePacket
            assertEquals(4, wire.readInt()); // blockId
            assertEquals(1, wire.readInt()); // x
            assertEquals(2, wire.readInt()); // y
            assertEquals(3, wire.readInt()); // z
        }
        finally
        {
            release(wire);
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void packetDecoderCreatesThePacketFromTheRegistryId()
    {
        EmbeddedChannel channel = new EmbeddedChannel(new PacketDecoder(new PacketRegistry()));
        Packet packet = null;

        try
        {
            ByteBuf wire = Unpooled.buffer()
                    .writeInt(6)
                    .writeInt(4)
                    .writeInt(1)
                    .writeInt(2)
                    .writeInt(3);

            assertTrue(channel.writeInbound(wire));

            packet = channel.readInbound();
            assertInstanceOf(BlockPlacePacket.class, packet);

            BlockPlacePacket place = (BlockPlacePacket) packet;
            assertEquals(1, place.getX());
            assertEquals(2, place.getY());
            assertEquals(3, place.getZ());
            assertEquals(4, place.getBlockId());
        }
        finally
        {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void fullClientPipelineRoundTripsAPacketOverTheWireFormat()
    {
        PacketRegistry registry = new PacketRegistry();
        EmbeddedChannel outbound = new EmbeddedChannel(
                new Decapsulator(), new Encapsulator(),
                new PacketDecoder(registry), new PacketEncoder());

        ByteBuf length = null;
        ByteBuf body = null;
        EmbeddedChannel inbound = new EmbeddedChannel(
                new Decapsulator(), new Encapsulator(),
                new PacketDecoder(registry), new PacketEncoder());

        try
        {
            assertTrue(outbound.writeOutbound(new BlockPlacePacket(5, 6, 7, 8)));

            length = outbound.readOutbound();
            body = outbound.readOutbound();
            assertNotNull(length);
            assertNotNull(body);

            ByteBuf wire = Unpooled.buffer()
                    .writeInt(length.readInt())
                    .writeBytes(body);

            assertTrue(inbound.writeInbound(wire));

            Packet packet = inbound.readInbound();
            assertInstanceOf(BlockPlacePacket.class, packet);

            BlockPlacePacket place = (BlockPlacePacket) packet;
            assertEquals(5, place.getX());
            assertEquals(6, place.getY());
            assertEquals(7, place.getZ());
            assertEquals(8, place.getBlockId());
        }
        finally
        {
            release(length);
            release(body);
            outbound.finishAndReleaseAll();
            inbound.finishAndReleaseAll();
        }
    }

    private static void release(ByteBuf buffer)
    {
        if (buffer != null && buffer.refCnt() > 0)
            buffer.release();
    }
}
