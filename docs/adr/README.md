# Architecture Decision Records

Retro Craft started in June 2019 and has ~126 commits across seven years, almost all
by one developer. Most of its important decisions were made in commit messages and
code, and were never written down. These ADRs reconstruct them.

They were written **retrospectively** in October 2026 by reading the history and the
current source. Dates are when a decision first appears in git history, not
necessarily when it was deliberated. Each record states what the code does today,
including the trade-offs that are now visible with hindsight.

## Convention

- One decision per file, numbered, never renumbered.
- Accepted records are immutable. If a decision changes, add a new record and mark the
  old one `Superseded by NNNN`.
- Use [template.md](template.md) for new records.

## Index

| # | Decision | Status |
|---|----------|--------|
| [0001](0001-record-architecture-decisions.md) | Record architecture decisions | Accepted |
| [0002](0002-pure-java-software-renderer.md) | Render on the CPU with no graphics library | Accepted |
| [0003](0003-low-internal-resolution.md) | Render at 318x200 and upscale 4x | Accepted |
| [0004](0004-fixed-size-world-array.md) | World as a flat fixed-size Block array | Accepted |
| [0005](0005-polymorphic-blocks.md) | Model blocks as polymorphic classes | Accepted |
| [0006](0006-tick-tied-to-render-loop.md) | Drive the client tick from the render loop | Accepted (known compromise) |
| [0007](0007-texture-atlas-and-bitmap-font.md) | Load PNG atlases and draw a bitmap font | Accepted |
| [0008](0008-noise-world-generation.md) | Generate worlds with layered noise and WorldTypes | Accepted |
| [0009](0009-netty-framed-packets.md) | Use Netty with length-prefixed packets | Accepted |
| [0010](0010-single-threaded-server-loop.md) | Hand packets to one game-loop thread | Accepted |
| [0011](0011-client-authoritative-movement.md) | Trust the client for movement | Accepted |
| [0012](0012-applet-shell.md) | Keep the Applet shell behind an adapter | Accepted (legacy) |
| [0013](0013-maven-build.md) | Build with Maven | Accepted |
| [0014](0014-shared-common-module.md) | Share one `common` package between client and server | Accepted |
| [0015](0015-render-players-as-sprites.md) | Render other players as 2D sprites | Accepted |
| [0016](0016-gpu-raycast-backend.md) | Optional GPU raycast backend | Accepted (experimental) |
| [0017](0017-server-fixed-20-tps.md) | Run the server at a fixed 20 TPS | Accepted |
| [0018](0018-allow-graphics-libraries.md) | Allow graphics libraries in ClassicCraft | Accepted |
| [0019](0019-internal-resolution-413x260.md) | Raise the internal resolution to 413x260 | Accepted |
| [0020](0020-fixed-60-tps-client.md) | Fixed 60 TPS client with rate-independent movement | Accepted |
| [0021](0021-game-feel-numbers.md) | Game-feel numbers: speed, jump and air control | Accepted |
| [0022](0022-placement-inside-player.md) | Refuse placement inside the player | Accepted |
| [0023](0023-frame-pacing.md) | Decouple presentation with a frame cap | Accepted |
| [0024](0024-chat.md) | Chat over the existing packet pipeline | Accepted |
| [0025](0025-transparent-animated-water.md) | Transparent, animated water | Accepted |
| [0026](0026-server-plugin-hook.md) | Server plugin hook | Accepted |
| [0027](0027-graalvm-javascript-plugins.md) | JavaScript server plugins with GraalJS | Accepted |
