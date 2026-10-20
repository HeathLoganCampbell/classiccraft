package com.craftclassic.common.network.packet.packets;

import com.craftclassic.common.entities.Player;
import com.craftclassic.common.network.ConnectionManager;
import com.craftclassic.common.network.packet.Packet;
import com.craftclassic.common.network.player.PlayerConnection;
import io.netty.buffer.ByteBuf;

import java.nio.charset.StandardCharsets;

/**
 * Chat line. The client sends only the text; the server stamps the sender and
 * broadcasts the same packet to every connection, including the author.
 */
public class ChatMessagePacket extends Packet
{
    public static final int MAX_LENGTH = 256;

    private String sender = "";
    private String message = "";

    public ChatMessagePacket() {}

    public ChatMessagePacket(String message)
    {
        this.message = message;
    }

    public ChatMessagePacket(String sender, String message)
    {
        this.sender = sender;
        this.message = message;
    }

    @Override
    public void decode(ByteBuf buf)
    {
        this.sender = readString(buf);
        this.message = readString(buf);
    }

    @Override
    public void encode(ByteBuf buf)
    {
        writeString(buf, this.sender);
        writeString(buf, this.message);
    }

    @Override
    public void process(PlayerConnection playerConnection)
    {
        Player player = playerConnection.getPlayer();
        if(player == null)
            return;

        // Plugins see every line before the built-in commands do, so they can
        // implement their own "/commands" by returning false.
        if(!ConnectionManager.INSTANCE.allowChat(player, this.message))
            return;

        // Slash commands are handled by the server, not broadcast.
        if(ConnectionManager.INSTANCE.handleCommand(player, this.message))
            return;

        this.sender = player.getName();
        ConnectionManager.INSTANCE.broadcastPacket(this);
    }

    private static void writeString(ByteBuf buf, String value)
    {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }

    private static String readString(ByteBuf buf)
    {
        int length = buf.readInt();
        if(length < 0 || length > MAX_LENGTH)
            throw new IllegalArgumentException("Invalid chat string length: " + length);

        return buf.readCharSequence(length, StandardCharsets.UTF_8).toString();
    }

    public String getSender() { return sender; }
    public String getMessage() { return message; }

    @Override
    public String toString()
    {
        return "ChatMessagePacket{sender='" + sender + "', message='" + message + "'}";
    }
}
