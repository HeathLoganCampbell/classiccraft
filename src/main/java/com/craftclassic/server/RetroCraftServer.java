package com.craftclassic.server;

import com.craftclassic.common.entities.Player;
import com.craftclassic.common.network.ConnectionManager;
import com.craftclassic.common.network.packet.Packet;
import com.craftclassic.common.network.pipeline.ServerNettyPipeline;
import com.craftclassic.common.network.player.PlayerConnection;
import com.craftclassic.server.command.CommandManager;
import com.craftclassic.server.plugin.PluginManager;
import com.craftclassic.server.plugin.WelcomePlugin;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.locks.LockSupport;

public class RetroCraftServer implements Runnable
{
    public static final int PORT = 8256;

    /** Simulation rate. The loop is fixed-rate: rendering is not involved. */
    public static final int TICKS_PER_SECOND = 20;

    private final PluginManager plugins = new PluginManager();

    private static final long TICK_NANOS = 1_000_000_000L / TICKS_PER_SECOND;

    /** Drop the backlog instead of replaying a burst after a long stall. */
    private static final long MAX_BACKLOG_NANOS = TICK_NANOS * 5;

    public RetroCraftServer()
    {
        this.plugins.register(new WelcomePlugin());
        ConnectionManager.INSTANCE.setCommandHandler(CommandManager::handle);
    }

    public void start()
    {
        this.run();
    }

    @Override
    public void run()
    {
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap bootstrap = new ServerBootstrap()
                    .group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ServerNettyPipeline())
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childOption(ChannelOption.IP_TOS, 0x18);

            // Start the server.
            bootstrap
                    .bind(PORT)
                    .addListener((ChannelFutureListener) future -> {
                        final Channel channel = future.channel();
                        if (future.isSuccess()) {
                            System.out.println("Listening on " + channel.localAddress());
                        }
                    })
                    .sync();

            ConnectionManager.INSTANCE.addJoinListener(this.plugins::firePlayerJoin);

            // Fixed-rate loop: the deadline advances by exactly one tick, so
            // processing time is subtracted from the sleep instead of being
            // added to the period. Sleep(50) after the work ran at ~19.9 TPS
            // and drifted further with every handler that took longer.
            long nextTick = System.nanoTime();
            long tick = 0;

            while(!Thread.currentThread().isInterrupted())
            {
                processPackets();
                tick(tick++);
                flushPackets();

                nextTick += TICK_NANOS;

                long now = System.nanoTime();

                if(nextTick > now)
                {
                    // parkNanos, not sleep: no checked exception, and it keeps
                    // the interrupt flag set so the loop exits cleanly.
                    LockSupport.parkNanos(nextTick - now);
                }
                else if(now - nextTick > MAX_BACKLOG_NANOS)
                {
                    nextTick = now;
                }
            }
            // Wait until the server socket is closed.
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            // Shut down all event loops to terminate all threads.
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

    private void processPackets()
    {
        for (Map.Entry<PlayerConnection, Player> entry : ConnectionManager.INSTANCE.getConnectedPlayers().entrySet())
        {
            PlayerConnection playerConnection = entry.getKey();
            Queue<Packet> recievedPackets = playerConnection.getRecievedPackets();
            Packet packet;

            while((packet = recievedPackets.poll()) != null)
            {
                packet.process(playerConnection);
            }
        }
    }

    /**
     * One simulation step. The server does not own a World yet, so this is
     * where world/entity ticking will go; packet-driven state changes happen
     * in {@link #processPackets()}.
     */
    private void tick(long tick)
    {
    }

    private void flushPackets()
    {
        for (Map.Entry<PlayerConnection, Player> entry : ConnectionManager.INSTANCE.getConnectedPlayers().entrySet())
        {
            entry.getKey().flush();
        }
    }
}
