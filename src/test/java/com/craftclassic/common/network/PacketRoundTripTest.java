package com.craftclassic.common.network;

import com.craftclassic.common.network.packet.Packet;
import com.craftclassic.common.network.packet.packets.BlockBreakPacket;
import com.craftclassic.common.network.packet.packets.BlockPlacePacket;
import com.craftclassic.common.network.packet.packets.ChatMessagePacket;
import com.craftclassic.common.network.packet.packets.EntityPositionPacket;
import com.craftclassic.common.network.packet.packets.EntitySpawnPacket;
import com.craftclassic.common.network.packet.packets.PlayerConnectPacket;
import com.craftclassic.common.network.packet.packets.PlayerDisconnectPacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PacketRoundTripTest
{
    private static <T extends Packet> T roundTrip(T packet, Supplier<T> factory)
    {
        ByteBuf buffer = Unpooled.buffer();
        try
        {
            packet.encode(buffer);

            T decoded = factory.get();
            decoded.decode(buffer);

            assertFalse(buffer.isReadable(), "decode should consume the whole packet");
            return decoded;
        }
        finally
        {
            buffer.release();
        }
    }

    @Test
    void blockPlaceRoundTrips()
    {
        BlockPlacePacket decoded = roundTrip(
                new BlockPlacePacket(12, -3, 45, 7),
                BlockPlacePacket::new);

        assertEquals(12, decoded.getX());
        assertEquals(-3, decoded.getY());
        assertEquals(45, decoded.getZ());
        assertEquals(7, decoded.getBlockId());
    }

    @Test
    void blockPlaceEncodesTheBlockIdFirst()
    {
        ByteBuf buffer = Unpooled.buffer();
        try
        {
            new BlockPlacePacket(1, 2, 3, 9).encode(buffer);

            assertEquals(9, buffer.readInt());
            assertEquals(1, buffer.readInt());
            assertEquals(2, buffer.readInt());
            assertEquals(3, buffer.readInt());
        }
        finally
        {
            buffer.release();
        }
    }

    @Test
    void blockBreakRoundTrips()
    {
        BlockBreakPacket decoded = roundTrip(
                new BlockBreakPacket(4, 5, 6, 0),
                BlockBreakPacket::new);

        assertEquals(4, decoded.getX());
        assertEquals(5, decoded.getY());
        assertEquals(6, decoded.getZ());
    }

    @Test
    void entityPositionRoundTrips()
    {
        EntityPositionPacket decoded = roundTrip(
                new EntityPositionPacket(77, 1.5, -2.25, 3.75),
                EntityPositionPacket::new);

        assertEquals(77, decoded.getEntityId());
        assertEquals(1.5, decoded.getX());
        assertEquals(-2.25, decoded.getY());
        assertEquals(3.75, decoded.getZ());
    }

    @Test
    void entitySpawnRoundTrips()
    {
        EntitySpawnPacket decoded = roundTrip(
                new EntitySpawnPacket(88, 10.5, 11.25, 12.75),
                EntitySpawnPacket::new);

        assertEquals(88, decoded.getEntityId());
        assertEquals(10.5, decoded.getX());
        assertEquals(11.25, decoded.getY());
        assertEquals(12.75, decoded.getZ());
    }

    @Test
    void playerConnectRoundTripsTheUsername()
    {
        PlayerConnectPacket decoded = roundTrip(
                new PlayerConnectPacket("Steve"),
                PlayerConnectPacket::new);

        assertEquals("Steve", decoded.getUsername());
    }

    @Test
    void chatMessageRoundTripsSenderAndMessage()
    {
        ChatMessagePacket decoded = roundTrip(
                new ChatMessagePacket("hello world"),
                ChatMessagePacket::new);

        assertEquals("", decoded.getSender());
        assertEquals("hello world", decoded.getMessage());
    }

    @Test
    void chatMessageRoundTripsAnEmptyMessage()
    {
        ChatMessagePacket decoded = roundTrip(
                new ChatMessagePacket(""),
                ChatMessagePacket::new);

        assertEquals("", decoded.getMessage());
    }

    @Test
    void chatRejectsAnOversizedString()
    {
        ByteBuf buffer = Unpooled.buffer();
        try
        {
            buffer.writeInt(ChatMessagePacket.MAX_LENGTH + 1);

            ChatMessagePacket packet = new ChatMessagePacket();
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> packet.decode(buffer));

            assertTrue(error.getMessage().contains("Invalid chat string length"));
        }
        finally
        {
            buffer.release();
        }
    }

    @Test
    void chatRejectsANegativeStringLength()
    {
        ByteBuf buffer = Unpooled.buffer();
        try
        {
            buffer.writeInt(-1);

            assertThrows(IllegalArgumentException.class,
                    () -> new ChatMessagePacket().decode(buffer));
        }
        finally
        {
            buffer.release();
        }
    }

    @Test
    void disconnectPacketIsEmpty()
    {
        ByteBuf buffer = Unpooled.buffer();
        try
        {
            new PlayerDisconnectPacket().encode(buffer);
            assertEquals(0, buffer.readableBytes());

            new PlayerDisconnectPacket().decode(buffer);
            assertEquals(0, buffer.readableBytes());
        }
        finally
        {
            buffer.release();
        }
    }
}
