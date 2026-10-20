package com.craftclassic.common.network.pipeline.handlers;

import com.craftclassic.common.network.ClientBridge;
import com.craftclassic.common.blocks.Block;
import com.craftclassic.common.entities.Entity;
import com.craftclassic.common.network.packet.Packet;
import com.craftclassic.common.network.packet.packets.*;
import com.craftclassic.common.utils.Location;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

public class ClientProcessingHandler extends SimpleChannelInboundHandler<Packet>
{
    private final String username;

    public ClientProcessingHandler()
    {
        this("Player");
    }

    public ClientProcessingHandler(String username)
    {
        this.username = username;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
    {
        if(!ctx.channel().isActive())
        {
            return;
        }

        cause.printStackTrace();
        System.out.println("Closing connection for client - " + ctx);
        ctx.close();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx)
    {
        PlayerConnectPacket playerConnectPacket = new PlayerConnectPacket(this.username);
        ctx.writeAndFlush(playerConnectPacket);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, Packet packet)
    {
        ClientBridge.View view = ClientBridge.view;
        if(view == null)
            return;

        if(packet instanceof PingPacket)
        {
            view.onPing(((PingPacket) packet).getSentNanos());
            return;
        }

        if(packet instanceof ChatMessagePacket)
        {
            ChatMessagePacket chat = (ChatMessagePacket) packet;
            view.onChatMessage(chat.getSender(), chat.getMessage());
            return;
        }

        if(packet instanceof EntitySpawnPacket)
        {
            EntitySpawnPacket entitySpawnPacket = (EntitySpawnPacket) packet;

            boolean isSelf = entitySpawnPacket.getEntityId() == 0;
            if(isSelf)
            {
                return;
            }

            Entity bunny = new Entity("Bunny", entitySpawnPacket.getEntityId());
            bunny.setLocation(new Location(view.getWorld(),
                    (float) entitySpawnPacket.getX(),
                    (float) entitySpawnPacket.getY(),
                    (float) entitySpawnPacket.getZ()));
            view.addEntity(bunny);
        }

        if(packet instanceof EntityPositionPacket)
        {
            EntityPositionPacket entityPositionPacket = (EntityPositionPacket) packet;
            for (Entity entity : view.getWorld().getEntities())
            {
                if (entity.getEntityId() == entityPositionPacket.getEntityId())
                {
                    Location location = entity.getLocation();
                    location.setX((float) entityPositionPacket.getX());
                    location.setY((float) entityPositionPacket.getY());
                    location.setZ((float) entityPositionPacket.getZ());
                    break;
                }
            }
        }

        if(packet instanceof BlockPlacePacket)
        {
            BlockPlacePacket blockPlacePacket = (BlockPlacePacket) packet;

            Block block = Block.getBlockById(blockPlacePacket.getBlockId());
            view.getWorld().setBlock(blockPlacePacket.getX(), blockPlacePacket.getY(), blockPlacePacket.getZ(), block);
        }

        if(packet instanceof BlockBreakPacket)
        {
            BlockBreakPacket blockBreakPacket = (BlockBreakPacket) packet;

            view.getWorld().setBlock(blockBreakPacket.getX(), blockBreakPacket.getY(), blockBreakPacket.getZ(), Block.AIR);
        }
    }
}
