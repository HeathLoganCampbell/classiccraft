# 0019. Raise the internal resolution to 413x260

- **Status:** Accepted
- **Date:** 2026-10-20
- **Deciders:** Heath Logan Campbell
- **Tags:** rendering, resolution

## Context

[0003](0003-low-internal-resolution.md) chose 318x200 because the CPU raycaster pays for
every pixel and the cost was `pixels x axes x steps`. That was correct for the CPU path,
where 63,600 rays is already the frame budget.

The GPU backend removed that constraint: 318x200 costs about 0.5 ms and the GPU is idle
for the rest of the frame. The internal resolution was a cost decision, not a style
decision (0003 says so), so raising it is the obvious quality lever.

Raising the resolution naively also widens the field of view, because the projection is
`(pixel - centre) / 90`. More pixels for the same focal length means a wider frustum,
not a sharper image.

## Decision

The internal target is **413x260**, 30% more than 318x200 on each axis.

- `Main.FOCAL` is derived from the width (`90 * 413/318 = 116.9`) instead of being a
  hard-coded 90, in both `World.render()` and `shaders/raycast.frag`. The field of view
  stays identical; only detail increases.
- `Main.SCALE` drops from 4 to 3, so the window stays about the same size
  (1239x780 instead of 1272x800) and the upscale remains integer/nearest.
- Sprites scale by `Main.RESOLUTION_SCALE` so their angular size is unchanged.
- The CPU renderer uses the same constants. It is 1.69x more expensive, which is
  acceptable for a reference path that already cannot hit 60 fps.

## Alternatives considered

- **Native resolution (1239x780)** - rejected: it abandons the chunky look entirely and
  gives up nearest-neighbour integer scaling.
- **Keep 318x200** - rejected: the GPU has an order of magnitude of headroom that is
  simply unused.
- **Bigger window with the same internal resolution** - rejected: it only makes the
  pixels larger, not the world more detailed.

## Consequences

**Positive**

- Noticeably more detail at the same window size and field of view.
- Frame cost goes from ~0.52 ms to ~0.58 ms, still far from the frame budget.
- The resolution is now a style choice rather than a performance ceiling.

**Negative**

- The CPU client costs 1.69x more per frame; it was already the slow path.
- UI text and the 5x6 font occupy a smaller fraction of the frame, so the overlay is
  proportionally smaller.
- Any future art that assumes 318x200 (sprite pixel sizes, HUD offsets) must use
  `RESOLUTION_SCALE` rather than hard-coded numbers.

**Neutral**

- The field of view and the look are unchanged; only sampling density changed.

## References

- Code: `src/main/java/com/craftclassic/client/Main.java` (`WIDTH`, `SCALE`, `FOCAL`)
- Code: `src/main/java/com/craftclassic/common/world/World.java` (`render`, `renderSprite`)
- Shader: `src/main/resources/shaders/raycast.frag`
