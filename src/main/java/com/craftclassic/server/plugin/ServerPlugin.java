package com.craftclassic.server.plugin;

import com.craftclassic.common.entities.Player;

/**
 * Minimal server extension point. Hooks run on the server thread that handles
 * login, so they must not block.
 */
public interface ServerPlugin
{
    default void onPlayerJoin(Player player) {}
}
