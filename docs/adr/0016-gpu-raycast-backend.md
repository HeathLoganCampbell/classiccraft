# 0016. Optional GPU raycast backend

- **Status:** Accepted (experimental)
- **Date:** 2026-10-20
- **Deciders:** Heath Logan Campbell
- **Tags:** rendering, performance, gpu

## Context

[0002](0002-pure-java-software-renderer.md) chose a CPU raycaster, and
[0003](0003-low-internal-resolution.md) chose 318x200 because every pixel is a
ray. That gave an explainable renderer but capped the frame budget: the cost is
`pixels x axes x steps-to-first-hit`, and the simulation is tied to the same
loop ([0006](0006-tick-tied-to-render-loop.md)).

The algorithm, however, is the part of the project that maps most directly to a
GPU. It is already per-pixel, branch-light and texture-driven, and the world is
only 262,144 bytes. Modern GPUs are idle while one CPU core casts 63,600 rays.

Two things made a GPU path worth trying:

- the world is small enough to live in a single 64x64x64 `R8UI` 3D texture
  (256 KB), so there is no chunk streaming, meshing or LOD machinery to build;
- the block atlas is already a 16x768 strip of 16x48 slots, which is exactly
  what `texelFetch` wants.

## Decision

We add an **optional** GPU backend and keep the CPU renderer as the reference
implementation.

- `client.gpu.GpuMain` owns a GLFW window and the main loop. It does not load
  the AWT/applet shell.
- `client.gpu.GpuWorldRenderer` uploads `World.blockData` as a `R8UI` 3D
  texture when `World.blockVersion` changes, and renders one fullscreen
  triangle.
- `shaders/raycast.frag` rebuilds each ray from yaw/pitch and the same
  `(pixel - centre) / 90` projection as `World.render()`, walks the grid with a
  voxel DDA, samples the atlas with the same face rules, and applies the same
  face shade and fog. It writes colour plus ray distance, replacing
  `World.zBuffer`.
- The world renders into a 318x200 RGBA8 target and is nearest-upscaled to the
  window (`shaders/blit.frag`), which also draws the crosshair. The pixelated
  look is preserved; resolution is now a choice rather than a budget.
- The simulation stays at 200 ticks/second, decoupled from the refresh rate.
  `World.tick()` now skips non-physics blocks instead of allocating two
  `Location` objects for all 262,144 positions every tick.

Two pieces of the shell had to move so the GPU client can run without AWT:

- `GameSettings.placeBlockTypeId` replaces the static on `Minecraft`.
- `Player.packetSink` replaces the direct `Minecraft.INSTANCE.sendPacket(...)`
  calls, which also removes the last `common -> client` dependency in the
  simulation.

The CPU renderer had a per-frame state leak: `fogOfWar`, `skyboxColour` and
`maxSkyboxColour` were declared outside the pixel loops, so the first ray that
hit a block clamped the render distance for the entire frame. They are now per
pixel in both backends.

## Alternatives considered

- **Keep the CPU renderer, optimise it** - rejected as the only answer: a
  proper DDA, `byte[]` world, height-map skipping and row-parallel rendering
  are worth roughly 5-10x, against ~1000x of headroom on the GPU. Worth doing
  as a follow-up, not as a replacement.
- **GPU presentation only** (`BufferStrategy`, `VolatileImage`, the Java2D
  pipelines) - rejected: it removes blit overhead, not ray cost.
- **Mesh the world into VBOs** - rejected for this ADR: it is the conventional
  answer and would scale to larger worlds and real lighting, but it abandons
  the raycaster that the project exists to explain. It remains the right next
  step if the world grows.
- **Pure-Java GPU access** - impossible: the JDK exposes no graphics API.
  LWJGL 3 is the smallest dependency that provides GLFW and OpenGL.
- **WebGL/TeaVM port** - possible, but a second runtime to maintain.

## Consequences

**Positive**

- Rendering is no longer the bottleneck: 0.36-0.52 ms/frame at 318x200 on an
  M1 Pro through Metal-backed OpenGL 4.1 (~2,000-2,700 fps when serialised),
  versus a CPU path that has to fight for 60.
- The render distance is a constant, not a function of the first hit.
- The CPU renderer stays in the tree as an oracle: both can be rendered from
  the same world and camera and compared. The residual difference is sub-texel
  aliasing from `float` ray maths, not structure.
- The simulation no longer drags AWT/applet into a headless client.

**Negative**

- The GPU path needs LWJGL and a GLFW window. It does not run as an applet and
  cannot share the Swing shell; macOS also requires `-XstartOnFirstThread`.
- The "no libraries" claim now applies to the CPU build only. This is a
  deliberate, opt-in exception rather than a rewrite.
- Sprites, the debug overlay and networking are not ported yet. The depth
  attachment is in place for sprite depth testing.
- Two renderers means two things to keep in sync; the shader is the copy that
  can silently drift.

**Neutral**

- `blockVersion` is a coarse dirty flag: any edit re-uploads all 256 KB. That is
  ~0.01 ms and simpler than tracking dirty regions.
- The GPU backend uses a fullscreen triangle with no vertex buffer and no
  meshes; there is no geometry pipeline to extend later. That is intentional.

## References

- Code: `src/main/java/com/craftclassic/client/gpu/GpuMain.java`
- Code: `src/main/java/com/craftclassic/client/gpu/GpuWorldRenderer.java`
- Shaders: `src/main/resources/shaders/raycast.frag`, `blit.frag`
- Code: `src/main/java/com/craftclassic/common/world/World.java` (`render`, `tick`, `blockVersion`)
- Script: `run-gpu.sh`
