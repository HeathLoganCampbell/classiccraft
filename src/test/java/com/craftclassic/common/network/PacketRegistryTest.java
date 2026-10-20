package com.craftclassic.common.network;

import com.craftclassic.common.network.packet.Packet;
import com.craftclassic.common.network.packet.PacketRegistry;
import com.craftclassic.common.network.packet.packets.BlockBreakPacket;
import com.craftclassic.common.network.packet.packets.BlockPlacePacket;
import com.craftclassic.common.network.packet.packets.ChatMessagePacket;
import com.craftclassic.common.network.packet.packets.PingPacket;
import com.craftclassic.common.network.packet.packets.EntityPositionPacket;
import com.craftclassic.common.network.packet.packets.EntitySpawnPacket;
import com.craftclassic.common.network.packet.packets.PlayerConnectPacket;
import com.craftclassic.common.network.packet.packets.PlayerDisconnectPacket;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

class PacketRegistryTest
{
    private final PacketRegistry registry = new PacketRegistry();

    @Test
    void idsAreStableOnTheWire()
    {
        Map<Integer, Class<? extends Packet>> expected = Map.of(
                1, PlayerConnectPacket.class,
                2, PlayerDisconnectPacket.class,
                3, EntityPositionPacket.class,
                4, EntitySpawnPacket.class,
                5, BlockBreakPacket.class,
                6, BlockPlacePacket.class,
                7, ChatMessagePacket.class);

        expected.forEach((id, type) ->
                assertInstanceOf(type, registry.createPacket(id), "packet id " + id));
    }

    @Test
    void everyPacketKnowsItsOwnId()
    {
        assertEquals(1, registry.getPacketId(new PlayerConnectPacket("P")));
        assertEquals(2, registry.getPacketId(new PlayerDisconnectPacket()));
        assertEquals(3, registry.getPacketId(new EntityPositionPacket()));
        assertEquals(4, registry.getPacketId(new EntitySpawnPacket()));
        assertEquals(5, registry.getPacketId(new BlockBreakPacket()));
        assertEquals(6, registry.getPacketId(new BlockPlacePacket()));
        assertEquals(7, registry.getPacketId(new ChatMessagePacket()));
        assertEquals(8, registry.getPacketId(new PingPacket()));
    }

    @Test
    void unknownIdsHaveNoPacket()
    {
        assertNull(registry.createPacket(0));
        assertNull(registry.createPacket(999));
        assertNull(registry.createPacket(-1));
    }

    @Test
    void createPacketReturnsAFreshInstance()
    {
        Packet first = registry.createPacket(6);
        Packet second = registry.createPacket(6);

        assertNotNull(first);
        assertNotNull(second);
        assertNotSame(first, second);
    }
}
