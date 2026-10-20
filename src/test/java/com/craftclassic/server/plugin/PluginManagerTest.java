package com.craftclassic.server.plugin;

import com.craftclassic.common.entities.Player;
import com.craftclassic.common.events.PlayerBreakBlockEvent;
import com.craftclassic.common.events.PlayerDisconnectEvent;
import com.craftclassic.common.events.PlayerJoinEvent;
import com.craftclassic.common.events.PlayerMoveEvent;
import com.craftclassic.common.events.PlayerPlaceBlockEvent;
import com.craftclassic.common.events.ServerTickEvent;
import com.craftclassic.common.network.ConnectionManager;
import com.craftclassic.common.network.packet.packets.ChatMessagePacket;
import com.craftclassic.common.network.player.PlayerConnection;
import com.craftclassic.common.utils.Location;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class PluginManagerTest
{
    @TempDir
    Path directory;

    private ConnectionManager connections;
    private PluginManager manager;
    private int nextEntityId = 1;

    @BeforeEach
    void setUp()
    {
        connections = new ConnectionManager();
        manager = new PluginManager(connections);
    }

    @AfterEach
    void tearDown()
    {
        manager.close();
    }

    @Test
    void loadsFoldersThatContainAManifest()
    {
        writePlugin("good", "function onEnable(api) { api.log('good loaded'); }");
        writeFile("source", "index.js", "function onEnable(api) { api.log('no manifest'); }");

        assertEquals(1, manager.loadDirectory(directory));
        assertEquals(1, manager.size());
    }

    @Test
    void typeScriptEntryPointIsRejectedWithAnExplanation()
    {
        writeFile("typed", "plugin.json", """
                {"name": "typed", "main": "index.ts"}
                """);
        writeFile("typed", "index.ts", "function onEnable(): void {}");

        assertEquals(0, manager.loadDirectory(directory));
    }

    @Test
    void mainDefaultsToIndexJs()
    {
        writeFile("defaulted", "plugin.json", """
                {"name": "defaulted"}
                """);
        writeFile("defaulted", "index.js", "function onPlayerJoin(event) { server.log('ok'); }");

        assertEquals(1, manager.loadDirectory(directory));
    }

    @Test
    void missingDirectoryIsNotAnError()
    {
        assertEquals(0, manager.loadDirectory(directory.resolve("nope")));
    }

    @Test
    void playerJoinEventReachesTheScript()
    {
        writePlugin("join", """
                function onPlayerJoin(event) {
                  server.send(event.player.name, "hi " + event.player.name);
                }
                """);
        assertEquals(1, manager.loadDirectory(directory));

        Player alice = addPlayer("Alice");
        manager.firePlayerJoin(alice);

        ChatMessagePacket message = onlyChat(alice);
        assertEquals("", message.getSender());
        assertEquals("hi Alice", message.getMessage());
    }

    @Test
    void playerDisconnectEventReachesEveryPlayer()
    {
        writePlugin("quit", """
                function onPlayerDisconnect(event) {
                  server.broadcast("bye " + event.player.name);
                }
                """);
        assertEquals(1, manager.loadDirectory(directory));

        Player alice = addPlayer("Alice");
        Player bob = addPlayer("Bob");

        manager.firePlayerDisconnect(alice);

        assertEquals("bye Alice", onlyChat(alice).getMessage());
        assertEquals("bye Alice", onlyChat(bob).getMessage());
    }

    @Test
    void blockBreakAndPlaceEventsCarryCoordinatesAndBlockId()
    {
        writePlugin("blocks", """
                function onPlayerBreakBlock(event) {
                  server.send(event.player.name, "broke " + event.x + "," + event.y + "," + event.z);
                }
                function onPlayerPlaceBlock(event) {
                  server.send(event.player.name, "placed " + event.blockId);
                }
                """);
        assertEquals(1, manager.loadDirectory(directory));

        Player alice = addPlayer("Alice");

        manager.firePlayerBreakBlock(new PlayerBreakBlockEvent(alice, 1, 2, 3));
        assertEquals("broke 1,2,3", onlyChat(alice).getMessage());

        manager.firePlayerPlaceBlock(new PlayerPlaceBlockEvent(alice, 4, 5, 6, 3));
        assertEquals("placed 3", onlyChat(alice).getMessage());
    }

    @Test
    void moveEventCarriesFromAndTo()
    {
        writePlugin("move", """
                function onPlayerMove(event) {
                  server.send(event.player.name,
                    event.from.x + "," + event.from.y + "," + event.from.z
                    + " -> " + event.to.x + "," + event.to.y + "," + event.to.z);
                }
                """);
        assertEquals(1, manager.loadDirectory(directory));

        Player alice = addPlayer("Alice");
        manager.firePlayerMove(new PlayerMoveEvent(alice, 1, 2, 3, 4.5, 5.5, 6.5));

        assertEquals("1,2,3 -> 4.5,5.5,6.5", onlyChat(alice).getMessage());
    }

    @Test
    void serverTickHookCanImplementATimer()
    {
        writePlugin("howdy", """
                const INTERVAL_SECONDS = 10;
                let nextTick = 0;

                function onServerTick(event) {
                  if (event.tick < nextTick) return;
                  server.broadcast("Howdy!!");
                  nextTick = event.tick + event.ticksPerSecond * INTERVAL_SECONDS;
                }
                """);
        assertEquals(1, manager.loadDirectory(directory));

        Player alice = addPlayer("Alice");

        manager.fireServerTick(new ServerTickEvent(0, 20));
        assertEquals("Howdy!!", onlyChat(alice).getMessage());

        manager.fireServerTick(new ServerTickEvent(199, 20));
        assertTrue(chats(alice).isEmpty());

        manager.fireServerTick(new ServerTickEvent(200, 20));
        assertEquals("Howdy!!", onlyChat(alice).getMessage());
    }

    @Test
    void chatHookCanCancelAndBroadcast()
    {
        writePlugin("wave", """
                function onChat(player, message) {
                  if (message === "/wave") {
                    server.broadcast("&e" + player.name + " waves");
                    return false;
                  }
                  return true;
                }
                """);
        assertEquals(1, manager.loadDirectory(directory));

        Player alice = addPlayer("Alice");
        Player bob = addPlayer("Bob");

        assertFalse(manager.fireChat(alice, "/wave"));
        assertEquals("&eAlice waves", onlyChat(alice).getMessage());
        assertEquals("&eAlice waves", onlyChat(bob).getMessage());

        assertTrue(manager.fireChat(alice, "hello"));
    }

    @Test
    void blockIdConstantsAreAvailable()
    {
        writePlugin("ids", """
                function onChat(player, message) {
                  if (message === "/stone") {
                    server.send(player.name, "stone=" + BlockId.STONE);
                    return false;
                  }
                  return true;
                }
                """);
        assertEquals(1, manager.loadDirectory(directory));

        Player alice = addPlayer("Alice");
        assertFalse(manager.fireChat(alice, "/stone"));
        assertEquals("stone=3", onlyChat(alice).getMessage());
    }

    @Test
    void scriptErrorsAreLoggedButDoNotBreakTheServer()
    {
        writePlugin("broken", """
                function onChat(player, message) {
                  throw new Error("boom");
                }
                """);
        assertEquals(1, manager.loadDirectory(directory));

        Player alice = addPlayer("Alice");

        assertDoesNotThrow(() -> assertTrue(manager.fireChat(alice, "hello")));
    }

    @Test
    void disablingAHookStopsIt()
    {
        writePlugin("disable", """
                function onPlayerJoin(event) {
                  server.send(event.player.name, "joined");
                }
                function onDisable() {
                  server.log("disabled");
                }
                """);
        assertEquals(1, manager.loadDirectory(directory));

        manager.close();

        Player alice = addPlayer("Alice");
        manager.firePlayerJoin(alice);
        assertTrue(chats(alice).isEmpty());
    }

    @Test
    void hostClassesAreNotReachable()
    {
        writePlugin("escape", """
                function onEnable(api) {
                  api.log(Java.type("java.lang.System").getProperty("java.version"));
                }
                """);

        assertEquals(0, manager.loadDirectory(directory));
        assertEquals(0, manager.size());
    }

    @Test
    void nativePluginsReceiveTheSameEvents()
    {
        RecordingPlugin plugin = new RecordingPlugin();
        manager.register(plugin);

        Player alice = addPlayer("Alice");
        manager.firePlayerJoin(alice);
        manager.firePlayerDisconnect(alice);
        manager.firePlayerBreakBlock(new PlayerBreakBlockEvent(alice, 1, 2, 3));
        manager.firePlayerPlaceBlock(new PlayerPlaceBlockEvent(alice, 4, 5, 6, 7));
        manager.firePlayerMove(new PlayerMoveEvent(alice, 0, 0, 0, 1, 1, 1));
        manager.fireServerTick(new ServerTickEvent(42, 20));

        assertNotNull(plugin.join);
        assertEquals(alice, plugin.join.getPlayer());
        assertNotNull(plugin.disconnect);
        assertEquals(alice, plugin.disconnect.getPlayer());
        assertEquals(3, plugin.breakBlock.getZ());
        assertEquals(7, plugin.placeBlock.getBlockId());
        assertEquals(1.0, plugin.move.getToX(), 1e-9);
        assertEquals(42, plugin.serverTick.getTick());
        assertEquals(20, plugin.serverTick.getTicksPerSecond());
    }

    @Test
    void laterPluginsDoNotSeeAHiddenChatLine()
    {
        manager.register(new ServerPlugin()
        {
            @Override
            public boolean onChat(Player player, String message)
            {
                return false;
            }
        });

        RecordingPlugin second = new RecordingPlugin();
        manager.register(second);

        assertFalse(manager.fireChat(addPlayer("Alice"), "hidden"));
        assertFalse(second.chatCalled);
    }

    private void writePlugin(String folder, String script)
    {
        writeFile(folder, "plugin.json", """
                {"name": "%s", "version": "1.0.0", "main": "index.js"}
                """.formatted(folder));
        writeFile(folder, "index.js", script);
    }

    private void writeFile(String folder, String name, String contents)
    {
        try
        {
            Path target = directory.resolve(folder);
            Files.createDirectories(target);
            Files.writeString(target.resolve(name), contents);
        }
        catch (IOException error)
        {
            throw new java.io.UncheckedIOException(error);
        }
    }

    private Player addPlayer(String name)
    {
        EmbeddedChannel channel = new EmbeddedChannel();
        PlayerConnection connection = new PlayerConnection(channel);
        Player player = new Player(connection, name, nextEntityId++);
        connection.setPlayer(player);
        player.setLocation(new Location(null, 10, 20, 30));
        connections.getConnectedPlayers().put(connection, player);
        return player;
    }

    private static ChatMessagePacket onlyChat(Player player)
    {
        List<ChatMessagePacket> messages = chats(player);
        assertEquals(1, messages.size(), "expected one chat line");
        return messages.get(0);
    }

    private static List<ChatMessagePacket> chats(Player player)
    {
        EmbeddedChannel channel = (EmbeddedChannel) player.getPlayerConnection().getChannel();
        channel.flush();

        List<ChatMessagePacket> messages = new ArrayList<>();
        Object packet;
        while ((packet = channel.readOutbound()) != null)
        {
            if (packet instanceof ChatMessagePacket)
                messages.add((ChatMessagePacket) packet);
        }
        return messages;
    }

    private static final class RecordingPlugin implements ServerPlugin
    {
        private PlayerJoinEvent join;
        private PlayerDisconnectEvent disconnect;
        private PlayerBreakBlockEvent breakBlock;
        private PlayerPlaceBlockEvent placeBlock;
        private PlayerMoveEvent move;
        private ServerTickEvent serverTick;
        private boolean chatCalled;

        @Override
        public void onPlayerJoin(PlayerJoinEvent event)
        {
            this.join = event;
        }

        @Override
        public void onPlayerDisconnect(PlayerDisconnectEvent event)
        {
            this.disconnect = event;
        }

        @Override
        public void onPlayerBreakBlock(PlayerBreakBlockEvent event)
        {
            this.breakBlock = event;
        }

        @Override
        public void onPlayerPlaceBlock(PlayerPlaceBlockEvent event)
        {
            this.placeBlock = event;
        }

        @Override
        public void onPlayerMove(PlayerMoveEvent event)
        {
            this.move = event;
        }

        @Override
        public void onServerTick(ServerTickEvent event)
        {
            this.serverTick = event;
        }

        @Override
        public boolean onChat(Player player, String message)
        {
            this.chatCalled = true;
            return true;
        }
    }
}
