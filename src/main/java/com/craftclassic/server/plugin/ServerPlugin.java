package com.craftclassic.server.plugin;

import com.craftclassic.common.entities.Player;
import com.craftclassic.common.events.PlayerBreakBlockEvent;
import com.craftclassic.common.events.PlayerDisconnectEvent;
import com.craftclassic.common.events.PlayerJoinEvent;
import com.craftclassic.common.events.PlayerMoveEvent;
import com.craftclassic.common.events.PlayerPlaceBlockEvent;
import com.craftclassic.common.events.ServerTickEvent;

/**
 * Minimal server extension point. Hooks run on the server thread that handles
 * login (or the plugin thread for JavaScript plugins), so they must not block.
 */
public interface ServerPlugin
{
    default void onPlayerJoin(PlayerJoinEvent event) {}

    default void onPlayerDisconnect(PlayerDisconnectEvent event) {}

    default void onPlayerBreakBlock(PlayerBreakBlockEvent event) {}

    default void onPlayerPlaceBlock(PlayerPlaceBlockEvent event) {}

    default void onPlayerMove(PlayerMoveEvent event) {}

    default void onServerTick(ServerTickEvent event) {}

    /**
     * @return false to drop the chat line before commands and broadcast
     */
    default boolean onChat(Player player, String message)
    {
        return true;
    }

    default void onDisable() {}
}
