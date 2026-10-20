# 0026. Server plugin hook

- **Status:** Accepted
- **Date:** 2026-10-21
- **Deciders:** Heath Logan Campbell
- **Tags:** server, extensibility

## Context

The server had no extension point: adding a welcome message meant editing
`ConnectionManager.startPlayer`, mixing server policy with connection/transport code.
More join-time behaviour (announcements, spawn kits, motd) would keep accreting there.

## Decision

A minimal `ServerPlugin` interface with `onPlayerJoin(Player)`, a `PluginManager` that
holds plugins, and a `ConnectionManager` join-listener list. `RetroCraftServer`
registers the built-in `WelcomePlugin`, which sends
`&bWelcome to the server` to the joining player as a `ChatMessagePacket`.

Hooks run on the thread that processes login (the Netty event loop), so they must not
block. The plugin is given the `Player`, from which it can get the `PlayerConnection`
and send packets.

## Alternatives considered

- **Hard-code the welcome in `ConnectionManager`** - rejected: every future feature would
  edit the transport layer.
- **Full event bus with priorities/cancellation** - rejected: one hook does not justify
  the machinery.
- **Classloader-based plugin jars** - rejected: no isolation or versioning needs yet;
  plugins are compiled into the server.

## Consequences

**Positive**

- Join-time behaviour is a small, testable class.
- Adding a plugin is two lines in `RetroCraftServer`.
- The welcome message exercises the existing chat packet and colour codes.

**Negative**

- No unloading, isolation or per-plugin configuration.
- A slow plugin runs on the login thread and delays that connection.
- Only join is exposed; leave/chat hooks would need new methods and fire sites.

**Neutral**

- The plugin list is built at server construction; there is no discovery mechanism.

## References

- Code: `src/main/java/com/craftclassic/server/plugin/`
- Code: `src/main/java/com/craftclassic/common/network/ConnectionManager.java`
- Test: `src/test/java/com/craftclassic/server/plugin/WelcomePluginTest.java`
