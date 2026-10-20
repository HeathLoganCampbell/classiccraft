# 0020. Fixed 60 TPS client with tick-rate-independent movement

- **Status:** Accepted
- **Date:** 2026-10-20
- **Deciders:** Heath Logan Campbell
- **Tags:** simulation, game-loop

## Context

[0006](0006-tick-tied-to-render-loop.md) ticked once per rendered frame, which averaged
about 60 TPS on the CPU client. The GPU client has no rendering cost to tie the tick to,
so it initially ran a fixed 200 TPS. Every movement constant is expressed per tick, so
the player walked about 3.3x too fast.

The tick rate and the movement speed are separate concerns. Changing one must not
silently change the other.

## Decision

The client simulates at a fixed **60 ticks/second**, the rate the old frame-bound loop
averaged, using an accumulator. It runs at most four catch-up ticks per frame and drops
the backlog after a stall.

`GameSettings.tickScale` is `60 / ticksPerSecond`. `Player.tick` multiplies walk/sprint
acceleration, gravity and the jump impulse by it, so blocks per second stay constant
when `--tps` changes. `GameSettings.speedMultiplier` scales horizontal movement only.

## Alternatives considered

- **Keep 200 TPS** - rejected: movement was 3.3x too fast and the constants are tuned by
  feel at the old rate.
- **20 TPS on the client too** - rejected: the client has no interpolation, so 50 ms
  steps would be visible. The server can afford 20 TPS because it only owns state.
- **Rewrite the physics in per-second units** - the correct long-term shape, but it
  changes every constant and the collision integrator at once. `tickScale` preserves
  the existing behaviour at 60 TPS and can be replaced later.

## Consequences

**Positive**

- Movement speed is independent of frame rate and of tick rate.
- `--tps` changes simulation smoothness only; `--speed` is the explicit speed knob.
- One reference rate makes the constants comparable with the old client.

**Negative**

- The physics is still a per-tick difference equation, not a time-step integrator; the
  compensation is exact for terminal velocities, approximate while accelerating.
- No interpolation, so a 60 Hz simulation on a 120 Hz display repeats frames.
- Tick-based timers (fluids, TNT) still scale with the tick rate.

**Neutral**

- The client (60) and server (20) intentionally run at different rates.

## References

- Code: `src/main/java/com/craftclassic/client/gpu/GpuMain.java`
- Code: `src/main/java/com/craftclassic/common/entities/Player.java`
- Code: `src/main/java/com/craftclassic/common/GameSettings.java`
