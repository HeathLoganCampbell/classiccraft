# 0025. Transparent, animated water

- **Status:** Accepted
- **Date:** 2026-10-20
- **Deciders:** Heath Logan Campbell
- **Tags:** rendering

## Context

The raycaster stops at the first non-air block, so water was an opaque wall: a lake hid
its own floor and looked like solid purple terrain. Water also used a single static
atlas tile, so it could not move.

The GPU path can afford to keep marching; the CPU path was chosen for a fixed ray
budget and is kept as the reference implementation.

## Decision

In `raycast.frag`, a ray that enters water does not stop:

- the water tile is sampled with an animated offset (`uTime`), and its brightness is
  modulated by two sine waves in world space so the surface visibly moves;
- the colour is composited front-to-back with `alpha = 0.55` per water cell;
- the ray continues to the next non-water block or the sky, which supplies the
  background;
- `oDepth` keeps the distance of the first surface (water included), so sprite depth
  testing still works.

Deep water still becomes opaque after a few cells (0.55, 0.80, 0.91 coverage). Lava is
unaffected: only the water texture slot is treated as transparent.

## Alternatives considered

- **A separate transparent pass** - rejected: it needs geometry, sorting and a second
  depth buffer, for one material.
- **Keep water opaque** - rejected: it hides shallow floors and reads as solid ground.
- **Refraction / screen-space distortion** - rejected: out of scope and much more
  expensive than a tint.

## Consequences

**Positive**

- Shallow water shows the brick/stone floor underneath through a moving tint.
- Water rays cost a few extra DDA steps; the rest of the frame is unchanged.
- No new draw calls or geometry.

**Negative**

- The CPU renderer still draws water opaque, so the two backends disagree.
- The water target raycast stops at the water surface, while the shader sees through it;
  the outline is skipped because water is not selectable, but the placement cell can
  differ from what is visually behind it.
- Animation is a procedural brightness wave, not a real water texture animation.

**Neutral**

- `uTime` is wall-clock seconds modulo 1000.

## References

- Shader: `src/main/resources/shaders/raycast.frag`
- Code: `src/main/java/com/craftclassic/client/gpu/GpuWorldRenderer.java` (`uTime`)
