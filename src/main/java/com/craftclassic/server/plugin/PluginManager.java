package com.craftclassic.server.plugin;

import com.craftclassic.common.entities.Player;
import com.craftclassic.common.events.PlayerBreakBlockEvent;
import com.craftclassic.common.events.PlayerDisconnectEvent;
import com.craftclassic.common.events.PlayerJoinEvent;
import com.craftclassic.common.events.PlayerMoveEvent;
import com.craftclassic.common.events.PlayerPlaceBlockEvent;
import com.craftclassic.common.events.ServerTickEvent;
import com.craftclassic.common.network.ConnectionManager;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Holds built-in Java plugins and JavaScript plugins found in the plugins
 * directory. JavaScript contexts share one daemon plugin thread, so a plugin
 * hook can safely be called from Netty's login thread and the server loop.
 */
public final class PluginManager implements AutoCloseable
{
    private final List<ServerPlugin> plugins = new CopyOnWriteArrayList<>();
    private final List<ScriptPlugin> scripts = new ArrayList<>();
    private final ConnectionManager connections;
    private final ExecutorService pluginThread;

    public PluginManager()
    {
        this(ConnectionManager.INSTANCE);
    }

    public PluginManager(ConnectionManager connections)
    {
        this.connections = connections;
        this.pluginThread = Executors.newSingleThreadExecutor(runnable ->
        {
            Thread thread = new Thread(runnable, "classiccraft-plugins");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void register(ServerPlugin plugin)
    {
        if (plugin != null)
            this.plugins.add(plugin);
    }

    /**
     * Loads every direct child folder that contains a {@code plugin.json}.
     * Folders without a manifest are ignored, so a plugin's source, build
     * output and docs can live together without being picked up separately.
     *
     * <p>The manifest names the JavaScript entry point. TypeScript must be
     * compiled to {@code .js} first; a {@code .ts} entry point is rejected with
     * an explanatory error rather than quietly skipped.</p>
     *
     * @return how many plugins loaded successfully
     */
    public int loadDirectory(Path directory)
    {
        if (directory == null || !Files.isDirectory(directory))
            return 0;

        List<Path> folders;
        try (var listing = Files.list(directory))
        {
            folders = listing
                    .filter(Files::isDirectory)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }
        catch (IOException error)
        {
            System.err.println("Plugins:  could not read " + directory + ": " + error.getMessage());
            return 0;
        }

        int loaded = 0;
        for (Path folder : folders)
        {
            Path manifest = folder.resolve("plugin.json");
            if (!Files.isRegularFile(manifest))
                continue;

            try
            {
                ScriptPlugin plugin = loadPlugin(folder, manifest);
                this.scripts.add(plugin);
                this.plugins.add(plugin);
                loaded++;
            }
            catch (IOException | RuntimeException error)
            {
                System.err.println("Plugins:  failed to load " + folder.getFileName()
                        + ": " + describe(error));
            }
        }

        return loaded;
    }

    private ScriptPlugin loadPlugin(Path folder, Path manifest) throws IOException
    {
        JsonElement root = JsonParser.parseString(Files.readString(manifest));
        if (!root.isJsonObject())
            throw new IllegalArgumentException("plugin.json must contain a JSON object");

        JsonObject object = root.getAsJsonObject();

        Path pluginDirectory = folder.toAbsolutePath().normalize();
        String name = string(object, "name", folder.getFileName().toString());
        String main = string(object, "main", "index.js");

        if (main.toLowerCase(Locale.ROOT).endsWith(".ts"))
            throw new IllegalArgumentException(
                    "main is a TypeScript file; compile it to .js before starting the server");

        Path source = pluginDirectory.resolve(main).normalize();
        if (!source.startsWith(pluginDirectory))
            throw new IllegalArgumentException("main must stay inside the plugin folder");

        if (!Files.isRegularFile(source))
            throw new IllegalArgumentException("main file not found: " + main);

        return new ScriptPlugin(name, source, this.pluginThread, this.connections);
    }

    private static String string(JsonObject object, String field, String fallback)
    {
        JsonElement value = object.get(field);
        if (value == null || value.isJsonNull())
            return fallback;

        String text = value.getAsString();
        return text == null || text.isBlank() ? fallback : text;
    }

    public void firePlayerJoin(Player player)
    {
        PlayerJoinEvent event = new PlayerJoinEvent(player);

        for (ServerPlugin plugin : this.plugins)
        {
            try
            {
                plugin.onPlayerJoin(event);
            }
            catch (RuntimeException error)
            {
                System.err.println("Plugins:  join hook failed: " + describe(error));
            }
        }
    }

    public void firePlayerDisconnect(Player player)
    {
        PlayerDisconnectEvent event = new PlayerDisconnectEvent(player);

        for (ServerPlugin plugin : this.plugins)
        {
            try
            {
                plugin.onPlayerDisconnect(event);
            }
            catch (RuntimeException error)
            {
                System.err.println("Plugins:  disconnect hook failed: " + describe(error));
            }
        }
    }

    public void firePlayerBreakBlock(PlayerBreakBlockEvent event)
    {
        for (ServerPlugin plugin : this.plugins)
        {
            try
            {
                plugin.onPlayerBreakBlock(event);
            }
            catch (RuntimeException error)
            {
                System.err.println("Plugins:  break hook failed: " + describe(error));
            }
        }
    }

    public void firePlayerPlaceBlock(PlayerPlaceBlockEvent event)
    {
        for (ServerPlugin plugin : this.plugins)
        {
            try
            {
                plugin.onPlayerPlaceBlock(event);
            }
            catch (RuntimeException error)
            {
                System.err.println("Plugins:  place hook failed: " + describe(error));
            }
        }
    }

    public void firePlayerMove(PlayerMoveEvent event)
    {
        for (ServerPlugin plugin : this.plugins)
        {
            try
            {
                plugin.onPlayerMove(event);
            }
            catch (RuntimeException error)
            {
                System.err.println("Plugins:  move hook failed: " + describe(error));
            }
        }
    }

    public void fireServerTick(ServerTickEvent event)
    {
        for (ServerPlugin plugin : this.plugins)
        {
            try
            {
                plugin.onServerTick(event);
            }
            catch (RuntimeException error)
            {
                System.err.println("Plugins:  tick hook failed: " + describe(error));
            }
        }
    }

    /**
     * @return true when every plugin allows the line to continue to commands
     *         and broadcast
     */
    public boolean fireChat(Player player, String message)
    {
        for (ServerPlugin plugin : this.plugins)
        {
            try
            {
                if (!plugin.onChat(player, message))
                    return false;
            }
            catch (RuntimeException error)
            {
                // A broken plugin must not silence the whole server.
                System.err.println("Plugins:  chat hook failed: " + describe(error));
            }
        }

        return true;
    }

    public int size()
    {
        return this.plugins.size();
    }

    @Override
    public void close()
    {
        for (ScriptPlugin script : this.scripts)
            script.close();

        for (ServerPlugin plugin : this.plugins)
        {
            if (plugin instanceof ScriptPlugin)
                continue;

            try
            {
                plugin.onDisable();
            }
            catch (RuntimeException error)
            {
                System.err.println("Plugins:  disable hook failed: " + describe(error));
            }
        }

        this.scripts.clear();
        this.plugins.clear();

        this.pluginThread.shutdown();
        try
        {
            if (!this.pluginThread.awaitTermination(2, TimeUnit.SECONDS))
                this.pluginThread.shutdownNow();
        }
        catch (InterruptedException interrupted)
        {
            Thread.currentThread().interrupt();
            this.pluginThread.shutdownNow();
        }
    }

    private static String describe(Throwable error)
    {
        String message = error.getMessage();
        return message == null || message.isBlank()
                ? error.getClass().getSimpleName()
                : message;
    }
}
