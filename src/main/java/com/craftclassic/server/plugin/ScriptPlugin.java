package com.craftclassic.server.plugin;

import com.craftclassic.common.entities.Player;
import com.craftclassic.common.events.PlayerBreakBlockEvent;
import com.craftclassic.common.events.PlayerDisconnectEvent;
import com.craftclassic.common.events.PlayerJoinEvent;
import com.craftclassic.common.events.PlayerMoveEvent;
import com.craftclassic.common.events.PlayerPlaceBlockEvent;
import com.craftclassic.common.events.ServerTickEvent;
import com.craftclassic.common.network.ConnectionManager;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * One JavaScript file loaded into its own GraalJS context.
 *
 * <p>A polyglot {@link Context} is single-threaded, but joins arrive on Netty
 * event-loop threads while chat is processed on the server loop. Every entry
 * into the context therefore goes through {@link PluginManager}'s one plugin
 * thread. Calls block until the hook returns, which keeps ordering simple and
 * matches the existing "hooks must not block" contract.</p>
 */
final class ScriptPlugin implements ServerPlugin, AutoCloseable
{
    private final String name;
    private final ExecutorService executor;
    private final Context context;
    private final PluginApi api;

    private final Value onPlayerJoin;
    private final Value onPlayerDisconnect;
    private final Value onPlayerBreakBlock;
    private final Value onPlayerPlaceBlock;
    private final Value onPlayerMove;
    private final Value onServerTick;
    private final Value onChat;
    private final Value onDisable;

    private volatile boolean closed;

    ScriptPlugin(String name, Path source, ExecutorService executor, ConnectionManager connections)
            throws IOException
    {
        this.name = name;
        this.executor = executor;
        this.api = new PluginApi(this.name, connections);

        this.context = Context.newBuilder("js")
                .allowHostAccess(HostAccess.NONE)
                .allowHostClassLookup(className -> false)
                .allowCreateProcess(false)
                .allowCreateThread(false)
                // JDK 26 is newer than this GraalJS runtime's optimizing engine,
                // so Truffle falls back to the interpreter. That is fine for a
                // few server hooks; do not print the warning on every start.
                .option("engine.WarnInterpreterOnly", "false")
                .build();

        try
        {
            Hooks hooks = call(() ->
            {
                ProxyObject apiProxy = this.api.asProxy();
                this.context.getBindings("js").putMember("server", apiProxy);
                this.context.getBindings("js").putMember("BlockId", PluginApi.blockIds());

                Source script = Source.newBuilder("js", source.toFile())
                        .name(this.name)
                        .build();
                this.context.eval(script);

                Value enable = executable("onEnable");
                if (enable != null)
                    enable.execute(apiProxy);

                return new Hooks(
                        executable("onPlayerJoin"),
                        executable("onPlayerDisconnect"),
                        executable("onPlayerBreakBlock"),
                        executable("onPlayerPlaceBlock"),
                        executable("onPlayerMove"),
                        executable("onServerTick"),
                        executable("onChat"),
                        executable("onDisable"));
            });

            this.onPlayerJoin = hooks.playerJoin;
            this.onPlayerDisconnect = hooks.playerDisconnect;
            this.onPlayerBreakBlock = hooks.breakBlock;
            this.onPlayerPlaceBlock = hooks.placeBlock;
            this.onPlayerMove = hooks.move;
            this.onServerTick = hooks.serverTick;
            this.onChat = hooks.chat;
            this.onDisable = hooks.disable;
        }
        catch (RuntimeException error)
        {
            this.context.close(true);
            throw error;
        }
    }

    String getName()
    {
        return this.name;
    }

    @Override
    public void onPlayerJoin(PlayerJoinEvent event)
    {
        if (this.closed || this.onPlayerJoin == null)
            return;

        call(() ->
        {
            this.onPlayerJoin.execute(this.api.playerEvent(event.getPlayer()));
            return null;
        });
    }

    @Override
    public void onPlayerDisconnect(PlayerDisconnectEvent event)
    {
        if (this.closed || this.onPlayerDisconnect == null)
            return;

        call(() ->
        {
            this.onPlayerDisconnect.execute(this.api.playerEvent(event.getPlayer()));
            return null;
        });
    }

    @Override
    public void onPlayerBreakBlock(PlayerBreakBlockEvent event)
    {
        if (this.closed || this.onPlayerBreakBlock == null)
            return;

        call(() ->
        {
            this.onPlayerBreakBlock.execute(this.api.blockEvent(
                    event.getPlayer(), event.getX(), event.getY(), event.getZ()));
            return null;
        });
    }

    @Override
    public void onPlayerPlaceBlock(PlayerPlaceBlockEvent event)
    {
        if (this.closed || this.onPlayerPlaceBlock == null)
            return;

        call(() ->
        {
            this.onPlayerPlaceBlock.execute(this.api.placeBlockEvent(
                    event.getPlayer(), event.getX(), event.getY(), event.getZ(), event.getBlockId()));
            return null;
        });
    }

    @Override
    public void onPlayerMove(PlayerMoveEvent event)
    {
        if (this.closed || this.onPlayerMove == null)
            return;

        call(() ->
        {
            this.onPlayerMove.execute(this.api.moveEvent(event.getPlayer(),
                    event.getFromX(), event.getFromY(), event.getFromZ(),
                    event.getToX(), event.getToY(), event.getToZ()));
            return null;
        });
    }

    @Override
    public void onServerTick(ServerTickEvent event)
    {
        if (this.closed || this.onServerTick == null)
            return;

        call(() ->
        {
            this.onServerTick.execute(this.api.tickEvent(
                    event.getTick(), event.getTicksPerSecond()));
            return null;
        });
    }

    /**
     * Returning {@code false} from the JS hook cancels normal chat broadcast.
     * Anything else (including no return value) lets the line through.
     */
    @Override
    public boolean onChat(Player player, String message)
    {
        if (this.closed || this.onChat == null)
            return true;

        return call(() ->
        {
            Value result = this.onChat.execute(this.api.player(player), message);
            return !(result.isBoolean() && !result.asBoolean());
        });
    }

    @Override
    public void close()
    {
        if (this.closed)
            return;

        this.closed = true;

        try
        {
            call(() ->
            {
                if (this.onDisable != null)
                    this.onDisable.execute();

                return null;
            });
        }
        catch (RuntimeException error)
        {
            System.err.println("Plugin " + this.name + ": onDisable failed: " + error.getMessage());
        }
        finally
        {
            try
            {
                this.context.close(true);
            }
            catch (RuntimeException error)
            {
                System.err.println("Plugin " + this.name + ": close failed: " + error.getMessage());
            }
        }
    }

    private Value executable(String member)
    {
        Value value = this.context.getBindings("js").getMember(member);
        return value != null && value.canExecute() ? value : null;
    }

    private <T> T call(Callable<T> action)
    {
        Future<T> future = this.executor.submit(action);

        try
        {
            return future.get();
        }
        catch (InterruptedException interrupted)
        {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while running plugin " + this.name, interrupted);
        }
        catch (ExecutionException failure)
        {
            Throwable cause = failure.getCause();

            if (cause instanceof RuntimeException)
                throw (RuntimeException) cause;

            throw new IllegalStateException("Plugin " + this.name + " failed", cause);
        }
    }

    private static final class Hooks
    {
        private final Value playerJoin;
        private final Value playerDisconnect;
        private final Value breakBlock;
        private final Value placeBlock;
        private final Value move;
        private final Value serverTick;
        private final Value chat;
        private final Value disable;

        private Hooks(Value playerJoin, Value playerDisconnect, Value breakBlock,
                      Value placeBlock, Value move, Value serverTick, Value chat, Value disable)
        {
            this.playerJoin = playerJoin;
            this.playerDisconnect = playerDisconnect;
            this.breakBlock = breakBlock;
            this.placeBlock = placeBlock;
            this.move = move;
            this.serverTick = serverTick;
            this.chat = chat;
            this.disable = disable;
        }
    }
}
