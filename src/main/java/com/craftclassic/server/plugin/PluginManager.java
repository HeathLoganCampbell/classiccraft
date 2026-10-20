package com.craftclassic.server.plugin;

import com.craftclassic.common.entities.Player;

import java.util.ArrayList;
import java.util.List;

public final class PluginManager
{
    private final List<ServerPlugin> plugins = new ArrayList<>();

    public void register(ServerPlugin plugin)
    {
        this.plugins.add(plugin);
    }

    public void firePlayerJoin(Player player)
    {
        for (ServerPlugin plugin : this.plugins)
            plugin.onPlayerJoin(player);
    }
}
