# 0021. Game-feel numbers: speed, jump and air control

- **Status:** Accepted
- **Date:** 2026-10-20
- **Deciders:** Heath Logan Campbell
- **Tags:** gameplay, tuning

## Context

The original constants were tuned for a slow CPU client: walking was ~2.4 blocks/s at
60 TPS, a jump reached 1.67 blocks with 1.05 s of airtime, and air movement used the
ground damping (0.5) with full input, which allowed 3.28 blocks of steering in one jump.

Playtesting produced three complaints: walking felt slow, the jump felt floaty, and air
movement either steered like the ground or carried you in a straight line like a
projectile.

## Decision

At the 60 TPS reference rate:

| | value |
|---|---|
| walk speed | 2x (4.8 blocks/s) |
| jump impulse | 0.1365 per tick |
| gravity | 0.0065 per tick |
| jump result | 1.19 blocks, 0.68 s airtime |
| air drag | 0.94 per tick |
| air acceleration | 12% of ground |
| air steer | 2.21 blocks from a standing jump |
| forward momentum cancel | 12 ticks (0.2 s) |
| mouse sensitivity | 0.06 (2x the old 0.03) |

Air drag and acceleration are chosen together: 0.94 drag with 12% acceleration keeps the
same 0.08 blocks/tick top speed as walking, but lets the player cancel or redirect
momentum in about 0.2 s instead of 0.4 s.

## Alternatives considered

- **No air control** - rejected: jumps become committed arcs and cannot be corrected.
- **Full ground control in the air** - rejected: the original 3.28 blocks of steering
  felt like flying.
- **Keep 0.98 drag and raise acceleration** - rejected: it keeps the "thrown" feeling
  because momentum takes too long to cancel, and lets the player build speed in the air.

## Consequences

**Positive**

- Running jumps no longer accelerate in the air (3.96 -> 3.44 blocks).
- The jump is 10% higher than the first tuning pass and much shorter than the original.
- Air steering is visible but bounded; it cannot exceed walking speed.

**Negative**

- These are feel numbers, not derivations. They are expected to change after playtests.
- Gravity is shared with the CPU client, so its jump changed too.
- `--speed` scales horizontal movement only; jump height stays fixed.

**Neutral**

- All values are per tick at 60 TPS and are scaled by `tickScale` for other rates.

## References

- Code: `src/main/java/com/craftclassic/common/entities/Player.java`
- Code: `src/main/java/com/craftclassic/client/gpu/GpuMain.java` (`MOUSE_SENSITIVITY`)
