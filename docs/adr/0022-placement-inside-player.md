# 0022. Refuse placement inside the player

- **Status:** Accepted
- **Date:** 2026-10-20
- **Deciders:** Heath Logan Campbell
- **Tags:** gameplay, interaction

## Context

The CPU renderer discovered the targeted block as a side effect of `World.render()`.
The GPU client never runs that loop, so `targetBlock` stayed null and every click did
nothing. A raycast restored break/place, but placement itself had no rule for the cell
the player occupies:

- allowing it placed a block inside the collision box, which trapped the player and
  blocked the next jump;
- lifting the player on top was tried and rejected as a teleport.

## Decision

`common.world.Raycast` casts one ray through the crosshair with the same yaw/pitch
scale, block origin and 5-block reach as the CPU renderer, and reports the hit cell plus
the adjacent cell for placement.

Placement is refused when the target cell overlaps the player's collision box: 0.6 wide,
from 0.15 above the origin to 1.45 below it. Consequently:

- looking down while standing targets the cell inside the body and does nothing;
- at the top of a jump the cell is clear, so placing underneath works and you land on the
  new block.

The targeted block's face edges are highlighted in yellow, matching the CPU outline.
Selectability is packed into bit 7 of the GPU world texture so water and lava are not
outlined.

## Alternatives considered

- **Lift the player onto the placed block** - rejected: it is a teleport, and it let the
  player pillar upward without jumping.
- **Allow overlap and push out** - rejected: the collision system has no resolution pass
  and the result is a stuck player.
- **Only allow placement at the first hit** - rejected: the adjacent cell is what makes
  building possible.

## Consequences

**Positive**

- Placement matches the expected Minecraft rule: no block inside the player, place under
  yourself while jumping.
- Breaking and placing work again on the GPU path, with visible feedback.
- The raycast is shared, so any future renderer gets the same targeting.

**Negative**

- The jump window for placing underneath is short: the body must clear a full block, so
  only the top ~0.2 blocks of the jump qualify (about 8 ticks).
- The collision box check is an AABB approximation of the 12-point collision sampling;
  near block edges a corner may disagree.
- The raycast stops at the first non-air block, including water, while the shader now
  renders through water; targets can differ visually.

**Neutral**

- The interaction reach is 5 blocks, matching the old `readDistance`.

## References

- Code: `src/main/java/com/craftclassic/common/world/Raycast.java`
- Code: `src/main/java/com/craftclassic/common/entities/Player.java` (`blockIntersectsPlayer`)
- Code: `src/main/java/com/craftclassic/client/gpu/GpuMain.java` (`updateTargetBlock`)
