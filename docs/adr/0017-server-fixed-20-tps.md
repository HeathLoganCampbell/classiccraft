# 0017. Run the server at a fixed 20 TPS

- **Status:** Accepted
- **Date:** 2026-10-20
- **Deciders:** Heath Logan Campbell
- **Tags:** networking, simulation, performance

## Context

[0010](0010-single-threaded-server-loop.md) put all game state on one thread and
scheduled it with `Thread.sleep(50)` *after* the work. That is 20 TPS only when there
is no work at all: with 3 ms of packet processing the period is 53 ms, and sleep
granularity adds more. Measured with a 3 ms handler, the old loop managed **17.5 TPS**
and the error is unbounded in the size of the handler.

The tick rate is a design choice, not a hardware limit. ClassicCraft is a casual voxel
game, not a competitive shooter. 50 ms is the point where movement and block edits
still feel responsive, while being far cheaper than 60 Hz. Nothing in the game needs
sub-50 ms simulation: there are no hitscan weapons, no physics that must resolve within
a frame, and no rollback netcode.

## Decision

The server loop is fixed-rate at 20 TPS. It keeps a nanosecond deadline and advances it
by exactly `1_000_000_000 / 20` ns per tick, so processing time is subtracted from the
sleep instead of being added to the period:

```
processPackets();
tick();
flushPackets();

nextTick += TICK_NANOS;
park until nextTick;
```

- Short overruns are absorbed automatically: if the deadline has already passed, the
  loop does not sleep and the next iteration starts immediately.
- A backlog larger than five ticks is dropped by resetting the deadline, so a GC pause
  or a suspended laptop cannot replay a burst of ticks.
- `LockSupport.parkNanos` is used instead of `Thread.sleep`; it throws no checked
  exception and leaves the interrupt flag set, so the loop exits cleanly.

The `tick(...)` hook is empty today: the server does not own a `World`. It is where
world and entity simulation will go.

## Alternatives considered

- **Keep `sleep(50)` after the work** ([0010](0010-single-threaded-server-loop.md)) -
  rejected: it runs below 20 TPS under any load, and the error is unbounded.
- **60 TPS** - rejected for now: three times the server cost for a difference this game
  does not need. The client still renders at 60 fps; only the simulation is 20 Hz.
- **Catch up on every missed tick** - rejected: after a stall it replays seconds of
  simulation at once, which is worse than dropping the backlog.
- **`ScheduledExecutorService`** - rejected: another thread and queue for a loop that
  already owns its thread.

## Consequences

**Positive**

- Stable 20 TPS average regardless of handler time: measured **19.98 TPS** with 3 ms of
  work per tick, against 17.5 for the old loop.
- No burst catch-up, and the remaining time in the 50 ms window is slept.
- Still one thread and no world locks; [0010](0010-single-threaded-server-loop.md)'s
  threading decision is unchanged.

**Negative**

- Input latency is up to 50 ms by design. That is the accepted trade for a casual game,
  but it rules out competitive-shooter timing.
- Tick-bound timers (fluids, TNT) run three times slower on the server than on the
  60 TPS client, so cross-machine timing is approximate.
- A handler that exceeds 250 ms loses its ticks permanently.
- 20 TPS is a constant, not a setting; changing it changes gameplay timing.

**Neutral**

- The client and server intentionally run at different rates: 60 for input and camera,
  20 for authoritative state.

## References

- Code: `src/main/java/com/craftclassic/server/RetroCraftServer.java`
- Supersedes the scheduling detail, not the threading model, of
  [0010](0010-single-threaded-server-loop.md)
