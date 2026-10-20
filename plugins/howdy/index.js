// Says Howdy!! every 10 seconds. The server ticks at a fixed 20 TPS, so the
// interval in ticks is derived from the event rather than hard-coded.
const INTERVAL_SECONDS = 10;

let nextTick = 0;

function onServerTick(event) {
    if (event.tick < nextTick) {
        return;
    }

    server.broadcast("Howdy!!");
    nextTick = event.tick + event.ticksPerSecond * INTERVAL_SECONDS;
}
