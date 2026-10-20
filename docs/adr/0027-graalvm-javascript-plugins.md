# 0027. JavaScript server plugins with GraalJS

- **Status:** Accepted
- **Date:** 2026-10-21
- **Deciders:** Heath Logan Campbell
- **Tags:** server, extensibility, scripting

## Context

[ADR 0026](0026-server-plugin-hook.md) added a `ServerPlugin` interface and a
built-in welcome plugin. Plugins were still Java classes compiled into the server,
so writing one required a JDK, a rebuild and a restart.

The server already exposes join, chat, command, block and movement traffic on a
single fixed-rate loop. A scripting layer can sit on those same points without
touching the transport. GraalJS is attractive because it runs in-process on the
JVM, needs no Node runtime and can be locked down more tightly than an embedded
`ScriptEngine`.

## Decision

Server plugins are JavaScript files hosted by GraalJS (`org.graalvm.polyglot`
plus GraalJS). Each plugin is a folder under the top-level `plugins/` directory
with a `plugin.json` manifest naming its JavaScript entry point:

```
plugins/wave/
├── plugin.json
├── index.ts     # source, not loaded
└── index.js     # main
```

Only direct child folders with a manifest are loaded, in name order. TypeScript
is compiled to JavaScript ahead of time (esbuild in `plugins/package.json`);
the loader rejects a `.ts` entry point instead of trying to interpret it.

The plugin API is a small set of JavaScript globals:

- **Events:** `onEnable(server)`, `onPlayerJoin(PlayerJoinEvent)`,
  `onPlayerDisconnect(PlayerDisconnectEvent)`,
  `onPlayerBreakBlock(PlayerBreakBlockEvent)`,
  `onPlayerPlaceBlock(PlayerPlaceBlockEvent)`,
  `onPlayerMove(PlayerMoveEvent)`, `onServerTick(ServerTickEvent)`,
  `onChat(player, message)` (return `false` to cancel), `onDisable()`.
- **Host functions:** `server.broadcast`, `server.send`, `server.players`,
  `server.log`, plus a `BlockId` constant table.

Events reuse the existing `common.events` style: the server creates a Java event
object, then converts it to a small proxy object for JavaScript. Plugins never
receive host objects.

`ChatMessagePacket.process` runs the chat filter before built-in commands, so a
plugin can implement slash commands by handling the line and returning `false`.
`BlockBreakPacket`, `BlockPlacePacket` and `EntityPositionPacket` fire their
events before relaying to other clients. `ConnectionManager.removePlayerConnection`
fires disconnect, now also reached from `ServerProcessingHandler.channelInactive`
(previously a closed connection was never removed).

All JavaScript contexts are entered from one daemon plugin thread. A GraalJS
`Context` is single-threaded, but joins arrive on Netty event loops and chat on
the server loop; serializing calls keeps every context confined and the ordering
deterministic. Hooks block their caller, matching the existing plugin contract.

The context has `HostAccess.NONE`, no class lookup, no process creation and no
thread creation. Plugins are still trusted local code: there is no CPU or memory
limit, and a slow hook stalls the plugin thread.

## Alternatives considered

- **Node.js subprocess with JSON-RPC** - rejected: another runtime, process
  lifecycle and marshalling layer for a small API; harder to deploy.
- **Java `ScriptEngine` (Nashorn or a standalone JS engine)** - rejected:
  Nashorn was removed, and GraalJS gives a maintained ECMAScript implementation
  with a supported embedding API.
- **Lua (LuaJ) or Python (GraalPy)** - viable, but JavaScript/TypeScript has the
  widest reach and the best editor tooling for plugin authors.
- **Java plugins with a URLClassLoader** - keeps full JVM power but requires a
  rebuild and has no isolation; the original problem.
- **Load `.js` files directly from `plugins/` with no manifest** - rejected:
  source, build output and docs would all be candidates, and there would be no
  place for name/version/description.

## Consequences

**Positive**

- Plugins are data: add a folder, restart, done, no rebuild.
- One small host API and proxy objects keep the JVM out of reach.
- TypeScript authors get types through `plugins/classiccraft.d.ts`; the compiled
  JavaScript is the only runtime artifact.
- The same event objects serve native `ServerPlugin` implementations and scripts.

**Negative**

- GraalJS adds a sizeable dependency tree and startup cost.
- The pinned GraalJS runtime is older than JDK 26, so Truffle falls back to the
  interpreter and prints a warning (suppressed in `ScriptPlugin`). This is fine
  for a handful of hooks but not for heavy per-pixel or per-tick work.
- The plugin API is server-only; the client world and renderer are not exposed.
- Client-authoritative block/move events cannot be vetoed, only observed.
- No hot reload, unloading or per-plugin classloader; a restart is required.

**Neutral**

- Hooks are serialized on one thread, so a slow plugin delays all plugins.
- The plugin folder is discovered from the working directory by default; the
  `classiccraft.plugins` system property overrides it.

## References

- Code: `src/main/java/com/craftclassic/server/plugin/`
- Code: `src/main/java/com/craftclassic/common/events/`
- Guide: `plugins/README.md`
- Example: `plugins/wave/`
