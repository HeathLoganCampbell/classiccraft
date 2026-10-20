# Server plugins

ClassicCraft server plugins are JavaScript, hosted by GraalJS. TypeScript is
supported by compiling it to JavaScript before the server starts.

The server scans the top-level `plugins/` folder for **plugin folders**. A folder
is only loaded when it contains a `plugin.json` manifest:

```
plugins/
├── README.md
├── classiccraft.d.ts        # editor types for TypeScript
├── package.json             # optional TypeScript build
├── tsconfig.json
└── wave/                    # one plugin
    ├── plugin.json
    ├── index.ts             # source (not loaded)
    └── index.js             # entry point (loaded)
```

The server loads folders in name order. Files in `plugins/` itself, and folders
without `plugin.json`, are ignored. This keeps source, notes and build output
together.

## plugin.json

```json
{
  "name": "wave",
  "version": "1.0.0",
  "description": "Greets players and adds a /wave command",
  "main": "index.js"
}
```

| Field | Required | Meaning |
|---|---|---|
| `name` | no | Used in logs. Defaults to the folder name. |
| `version` | no | Informational only. |
| `description` | no | Informational only. |
| `main` | no | JavaScript entry point, relative to the plugin folder. Defaults to `index.js`. Must stay inside the folder. |

`main` must be JavaScript. A `.ts` entry point is rejected with an error telling
you to compile it first; the loader never executes TypeScript directly.

The server reads `plugins/` relative to its working directory. Override it with
the `classiccraft.plugins` system property:

```bash
JAVA_TOOL_OPTIONS="-Dclassiccraft.plugins=/absolute/path/to/plugins" ./run-server.sh
```

or pass it to `java` yourself when launching `com.craftclassic.server.Main`.

## Events

Declare these functions at the top level of the entry file. Every hook is
optional. Hooks run one at a time on a single plugin thread, in plugin-load order.

| Hook | Argument | When |
|---|---|---|
| `onEnable(server)` | API object | Once, after the file is evaluated. |
| `onPlayerJoin(event)` | `PlayerJoinEvent` | After a player logs in and can receive packets. |
| `onPlayerDisconnect(event)` | `PlayerDisconnectEvent` | When the player's connection closes. |
| `onPlayerBreakBlock(event)` | `PlayerBreakBlockEvent` | A client reports breaking a block. |
| `onPlayerPlaceBlock(event)` | `PlayerPlaceBlockEvent` | A client reports placing a block. |
| `onPlayerMove(event)` | `PlayerMoveEvent` | A client position update, before it is relayed. |
| `onServerTick(event)` | `ServerTickEvent` | Once per simulation tick (20 per second). |
| `onChat(player, message)` | `PluginPlayer`, `string` | Before built-in commands and chat broadcast. Return `false` to cancel. |
| `onDisable()` | none | When the server shuts down. |

### Event shapes

```ts
interface PluginPlayer {
    readonly name: string;
    readonly entityId: number;
    readonly x?: number;   // present when the player has a location
    readonly y?: number;
    readonly z?: number;
}

interface PlayerJoinEvent {
    readonly player: PluginPlayer;
}

interface PlayerDisconnectEvent {
    readonly player: PluginPlayer;
}

interface PlayerBreakBlockEvent {
    readonly player: PluginPlayer;
    readonly x: number;    // world block coordinates
    readonly y: number;
    readonly z: number;
}

interface PlayerPlaceBlockEvent extends PlayerBreakBlockEvent {
    readonly blockId: number;
}

interface PlayerMoveEvent {
    readonly player: PluginPlayer;
    readonly from: { x: number; y: number; z: number };
    readonly to: { x: number; y: number; z: number };
}

interface ServerTickEvent {
    readonly tick: number;
    readonly ticksPerSecond: number;
}
```

Movement, breaking and placing are **client-authoritative**: the client has
already changed its world by the time the server sees the packet. These events
are notifications for scoring, logging, announcements and similar reactions; they
cannot veto the action. Chat is the exception: returning `false` from `onChat`
does drop the line.

`onPlayerMove` is high-frequency (clients send a position update every tick).
`onServerTick` runs 20 times per second. Keep both handlers cheap, and do not add
them unless you need them.

### Chat and commands

`onChat` runs before the built-in `/ping`, `/msg`, `/help` and `/list` commands.
A plugin can implement its own slash commands by matching the message, doing its
work and returning `false`. Returning anything else (including `undefined`) lets
the line continue to the built-in command handler and then to normal broadcast.

Colour codes are the usual `&` codes, e.g. `&e`, `&a`, `&c`. Outgoing plugin chat
is capped at the packet's 256-byte limit.

## Host API

The global `server` object is also passed to `onEnable`:

| Method | Returns | Meaning |
|---|---|---|
| `server.broadcast(message)` | `void` | Chat line to every connected player. |
| `server.send(playerName, message)` | `boolean` | Private chat line; `false` if the player is offline. |
| `server.players()` | `string[]` | Connected player names. |
| `server.log(message)` | `void` | Console output prefixed with `[plugin:<name>]`. |

`BlockId` is available as a global with the built-in ids:

```js
server.log("stone is " + BlockId.STONE); // 3
```

Available values: `AIR`, `DIRT`, `GRASS`, `STONE`, `LAVA`, `WATER`, `BEDROCK`,
`LOG`, `LEAVES`, `BRICK`, `COBBLESTONE`, `GLASS`, `TNT`, `SAND`.

## JavaScript example

`plugins/greeter/plugin.json`:

```json
{ "name": "greeter", "main": "index.js" }
```

`plugins/greeter/index.js`:

```js
function onEnable(api) {
  api.log("greeter ready");
}

function onPlayerJoin(event) {
  server.send(event.player.name, "&aWelcome, " + event.player.name + "!");
}

function onPlayerDisconnect(event) {
  server.broadcast("&7" + event.player.name + " left the game");
}

function onChat(player, message) {
  if (message.trim().toLowerCase() === "/greet") {
    server.broadcast("&e" + player.name + " says hello");
    return false;
  }
  return true;
}
```

## Timers: say something every 10 seconds

`onServerTick` is the timer primitive. `event.ticksPerSecond` is 20, so ten
seconds is 200 ticks. `plugins/howdy/index.js` is a complete example:

```js
const INTERVAL_SECONDS = 10;
let nextTick = 0;

function onServerTick(event) {
  if (event.tick < nextTick) {
    return;
  }

  server.broadcast("Howdy!!");
  nextTick = event.tick + event.ticksPerSecond * INTERVAL_SECONDS;
}
```

Computing `nextTick` from the current tick (rather than `tick % 200`) keeps the
interval after a stall instead of replaying missed announcements in a burst.

## TypeScript

The `wave` plugin is the TypeScript example. The TypeScript source is
`plugins/wave/index.ts`; the compiled `index.js` next to it is what the server
loads.

```bash
cd plugins
npm install
npm run build      # esbuild wave/index.ts -> wave/index.js
npm run typecheck  # tsc --noEmit against classiccraft.d.ts
```

Copy the `wave` folder and its two config files as a starting point. Add
`"main": "index.js"` to its `plugin.json` and keep the compiled output beside the
source.

A TypeScript plugin is the same API with types:

```ts
function onPlayerBreakBlock(event: PlayerBreakBlockEvent): void {
  // The server has no world yet, so it knows where, but not what, was broken.
  server.log(`${event.player.name} broke a block at ${event.x},${event.y},${event.z}`);
}

function onPlayerPlaceBlock(event: PlayerPlaceBlockEvent): void {
  if (event.blockId === BlockId.STONE) {
    server.log(`${event.player.name} placed stone at ${event.x},${event.y},${event.z}`);
  }
}
```

## Trust and limitations

- Plugins are **trusted local code**, like a server mod. The JS context has no
  host class access (`Java.type(...)` is blocked), no process creation and no
  thread creation, but there is no CPU or memory sandbox.
- All hooks are serialized on one plugin thread. A slow or endless hook stalls
  every plugin and the packet-processing caller; keep hooks short.
- Plugins load at server start. Editing a file requires a restart; there is no
  hot reload or unloading.
- A failing plugin is logged and does not stop the server or other plugins.
- The chat, join, disconnect, break, place, move and server-tick events are the
  entire v1 surface. There is no world API on the server yet, so block events
  carry coordinates rather than `Block` objects.
