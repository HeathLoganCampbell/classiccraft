"use strict";
function onEnable(api) {
  api.log("wave plugin ready - try /wave");
}
function onPlayerJoin(event) {
  server.send(event.player.name, "&7Say &f/wave&7 to greet the server.");
}
function onPlayerDisconnect(event) {
  server.log(`${event.player.name} left`);
}
function onPlayerBreakBlock(event) {
  if (event.y === 63) {
    server.log(`${event.player.name} broke the top layer at ${event.x},${event.y},${event.z}`);
  }
}
function onChat(player, message) {
  if (message.trim().toLowerCase() === "/wave") {
    server.broadcast(`&e${player.name} waves at everyone`);
    return false;
  }
  return true;
}
function onDisable() {
  server.log("wave plugin stopped");
}
