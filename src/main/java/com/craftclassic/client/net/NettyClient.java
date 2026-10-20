package com.craftclassic.client.net;

import com.craftclassic.common.network.packet.Packet;
import com.craftclassic.common.network.pipeline.ClientNettyPipeline;
import com.craftclassic.server.RetroCraftServer;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;

public final class NettyClient {
    public static final String DEFAULT_HOST = "127.0.0.1";
    public static final int DEFAULT_PORT = RetroCraftServer.PORT;

    private final String host;
    private final int port;
    private final String username;

    public Channel serverChannel;

    public NettyClient()
    {
        this(DEFAULT_HOST, DEFAULT_PORT, "Player");
    }

    public NettyClient(String host, int port, String username)
    {
        this.host = host;
        this.port = port;
        this.username = username;
    }

    /**
     * Connects without throwing. Returns false when no server is listening,
     * which lets the GPU client fall back to single-player.
     */
    public boolean connect()
    {
        EventLoopGroup group = new NioEventLoopGroup();
        Bootstrap b = new Bootstrap();

        b.group(group)
                .channel(NioSocketChannel.class)
                .handler(new ClientNettyPipeline(this.username));

        try {
            ChannelFuture f = b.connect(this.host, this.port).sync();
            if (!f.isSuccess()) {
                group.shutdownGracefully();
                return false;
            }
            this.serverChannel = f.channel();
            return true;
        } catch (Exception e) {
            group.shutdownGracefully();
            return false;
        }
    }

    public void run()
    {
        if (!connect())
            throw new RuntimeException("Could not connect to " + this.host + ":" + this.port);
    }

    public boolean isConnected()
    {
        return this.serverChannel != null && this.serverChannel.isActive();
    }

    public Channel getServerChannel()
    {
        return serverChannel;
    }

    public void sendPacket(Packet packet)
    {
        if (this.isConnected())
            this.serverChannel.writeAndFlush(packet);
    }
}

