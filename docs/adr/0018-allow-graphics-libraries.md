# 0018. Allow graphics libraries in ClassicCraft

- **Status:** Accepted
- **Date:** 2026-10-20
- **Deciders:** Heath Logan Campbell
- **Tags:** constraints, rendering, fork

## Context

Retro Craft's defining constraint was *no libraries*
([0002](0002-pure-java-software-renderer.md)):
the renderer is hand-written on the CPU and the only dependency is the JDK. That was
the right constraint for a project whose purpose was to explain rendering from first
principles.

ClassicCraft is a fork with a second goal: keep the CPU renderer as the reference, but
also make the game run well. A GPU backend is the largest performance lever available,
and the JDK exposes no GPU API. The choice was therefore between "no GPU" and "allow a
graphics library".

## Decision

The fork keeps the CPU renderer dependency-free, but **graphics libraries are allowed
project-wide**.

- `World.render()` and the `int[]` framebuffer stay as they are, with no third-party
  rendering code.
- The optional GPU backend uses LWJGL 3 (GLFW + OpenGL), added as a normal Maven
  dependency.
- Future rendering work may use whatever library fits; it does not need another ADR to
  take a dependency.

Retro Craft's constraint is preserved where it is load-bearing - as a property of the
CPU renderer that explains how the raycast works - not as a rule for the whole fork.

## Alternatives considered

- **Stay dependency-free** - rejected: it caps rendering at CPU speed and rules out the
  GPU backend that motivated the fork.
- **Use only Java2D/AWT** - rejected: it can accelerate presentation and blitting, but
  not the per-pixel raycaster. It does not solve the actual cost.
- **Rewrite the CPU renderer with SIMD/panama** - rejected for now: it could win 4-8x,
  against ~1000x of GPU headroom, and would not free the simulation thread.

## Consequences

**Positive**

- The GPU backend is possible, and rendering stops competing with simulation for the
  CPU.
- The CPU renderer remains a portable, dependency-free oracle that the GPU output can
  be compared against.
- The fork can adopt a library when it removes a whole class of work, instead of
  reimplementing it.

**Negative**

- The "no libraries" claim no longer describes the project, only the CPU renderer.
  The README has to say this explicitly.
- The GPU build needs natives per platform (LWJGL classifiers) and a GLFW window; it
  cannot run as an applet.
- Two dependency profiles to keep working: CPU-only and GPU.

**Neutral**

- The CPU renderer is still the reference implementation, so the fork keeps the part
  of Retro Craft that was educational.

## References

- Code: `src/main/java/com/craftclassic/client/gpu/`
- [0016](0016-gpu-raycast-backend.md) for the backend itself
- [0002](0002-pure-java-software-renderer.md) for the constraint being scoped down
