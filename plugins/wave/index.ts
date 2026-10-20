// Author this file, then run `npm run build` in this folder to produce wave.js.
// The server only loads the compiled .js.

function onEnable(api: PluginServer): void {
    api.log("wave plugin ready - try /wave");
}

function onPlayerJoin(event: PlayerJoinEvent): void {
    server.send(event.player.name, "&7Say &f/wave&7 to greet the server.");
}

function onPlayerDisconnect(event: PlayerDisconnectEvent): void {
    server.log(`${event.player.name} left`);
}

function onPlayerBreakBlock(event: PlayerBreakBlockEvent): void {
    // The server has no world yet, so the event carries coordinates only.
    if (event.y === 63) {
        server.log(`${event.player.name} broke the top layer at ${event.x},${event.y},${event.z}`);
    }
}

function onChat(player: PluginPlayer, message: string): boolean {
    if (message.trim().toLowerCase() === "/wave") {
        server.broadcast(`&e${player.name} waves at everyone`);
        return false; // handled here; do not broadcast "/wave" or run commands
    }

    return true;
}

function onDisable(): void {
    server.log("wave plugin stopped");
}
