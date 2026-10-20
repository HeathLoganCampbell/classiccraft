# 0023. Decouple presentation with a frame cap

- **Status:** Accepted
- **Date:** 2026-10-20
- **Deciders:** Heath Logan Campbell
- **Tags:** rendering, performance

## Context

The GPU renderer takes about 0.6 ms per frame at 413x260. With vsync on, the loop is
limited by the display (60 or 120 Hz) and the GPU spends most of the frame idle. There
are two reasons to go faster: verifying headroom under load, and matching high-refresh
displays without relying on the compositor.

## Decision

`--max-fps N` turns vsync off and paces frames with a nanosecond deadline after
`glfwSwapBuffers`, parking for the remainder. `--log-fps` prints the measured rate once
per second. The simulation stays at its fixed 60 TPS; only presentation speeds up.

Short overruns are absorbed by the next deadline; a backlog larger than four frames is
dropped rather than chased.

## Alternatives considered

- **Always uncapped** - rejected: it burns CPU/GPU for no visible benefit and produces
  tearing with no upper bound.
- **Always vsync** - rejected: it hides the actual headroom and prevents high-refresh
  testing.

## Consequences

**Positive**

- Measured ~500 fps at 413x260 on an M1 Pro, with the simulation still at 60 TPS.
- The cap is opt-in; the default remains vsync for normal play.

**Negative**

- Tearing is possible with vsync off.
- Frame pacing is best-effort: `parkNanos` can overshoot, so the title shows a few frames
  under the target.
- More frames means more `updateTargetBlock` raycasts and mouse handling, which is
  measurable but small (one ray per frame).

**Neutral**

- Rendering and simulation rates are now independently configurable.

## References

- Code: `src/main/java/com/craftclassic/client/gpu/GpuMain.java` (`--max-fps`, `--log-fps`)
