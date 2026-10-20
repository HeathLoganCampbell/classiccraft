package com.craftclassic.common.entities;

import com.craftclassic.client.input.Input;
import com.craftclassic.common.GameSettings;
import com.craftclassic.common.blocks.Block;
import com.craftclassic.common.events.BreakEvent;
import com.craftclassic.common.events.PlaceEvent;
import com.craftclassic.common.network.packet.Packet;
import com.craftclassic.common.network.packet.packets.BlockPlacePacket;
import com.craftclassic.common.network.player.PlayerConnection;
import com.craftclassic.common.utils.Location;
import com.craftclassic.common.utils.Vector;

import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

public class Player extends LivingEntity
{
	public static final int PLAYER_HEIGHT = 12;

	private Location targetBlock;

	private Location targetBlockPlace;

	private PlayerConnection playerConnection;

	private boolean sprinting = false;

	/**
	 * Installed by whichever client is running; the simulation never talks to
	 * the network stack or to the applet shell directly.
	 */
	public static Consumer<Packet> packetSink;

	public Player(PlayerConnection playerConnection, String name, int id)
	{
		super(name, id);
		this.playerConnection = playerConnection;
	}

	public boolean isSprinting() {
		return sprinting;
	}

	public Location getTargetBlock() {
		return targetBlock;
	}

	public void setTargetBlock(Location targetBlock) {
		this.targetBlock = targetBlock;
	}

	@Override
	public void tick()
	{
		if (Input.getMouse(MouseEvent.BUTTON1) && this.getTargetBlock() != null)
		{
			if(getWorld().getBlock(this.getTargetBlock()).onBreak(new BreakEvent(this.getTargetBlock())))
			{
				BlockPlacePacket blockPlacePacket = new BlockPlacePacket((int) this.getTargetBlock().getX(),
						(int) this.getTargetBlock().getY(),
						(int) this.getTargetBlock().getZ(),
						Block.AIR.getId());
				getWorld().setBlock(this.getTargetBlock(), Block.AIR);
				if (packetSink != null) packetSink.accept(blockPlacePacket);
			}
			Input.setMouse(MouseEvent.BUTTON1, false);
		}

		this.sprinting = Input.getKey(KeyEvent.VK_CONTROL);

		if (Input.getMouse(MouseEvent.BUTTON3) && this.getTargetBlock() != null)
		{
			Block block = getWorld().getBlock(this.getTargetBlockPlace());

			if(!blockIntersectsPlayer(this.getTargetBlockPlace())
					&& block.onPlace(new PlaceEvent(this.getTargetBlockPlace()))) {
				BlockPlacePacket blockPlacePacket = new BlockPlacePacket((int) this.getTargetBlockPlace().getX(),
																		 (int) this.getTargetBlockPlace().getY(),
																		 (int) this.getTargetBlockPlace().getZ(),
						GameSettings.placeBlockTypeId);
				getWorld().setBlock(this.getTargetBlockPlace(), Block.getBlockById(GameSettings.placeBlockTypeId));
				if (packetSink != null) packetSink.accept(blockPlacePacket);
			}
			Input.setMouse(MouseEvent.BUTTON3, false);
		}

		Location playerLoc = this.getLocation();
		float sinYaw = (float)Math.sin(playerLoc.getYaw() * 100f);
		float cosYaw = (float)Math.cos(playerLoc.getYaw() * 100f);

		boolean onGround = this.isOnGround();

		float speed = 0.02f * GameSettings.tickScale * GameSettings.speedMultiplier;

		if(sprinting)
		{
			speed = 0.03f * GameSettings.tickScale * GameSettings.speedMultiplier;
		}

		// Air movement is a damped version of ground movement: the higher drag
		// lets you cancel and redirect momentum in ~0.25s instead of being
		// carried in a straight line, but the lower acceleration still caps you
		// at walking speed.
		if(!onGround)
		{
			speed *= 0.12f;
		}

		float accX = (float)((Input.getKey(KeyEvent.VK_D) ? 1 : 0) - (Input.getKey(KeyEvent.VK_A) ? 1 : 0)) * speed;
		float accZ = (float)((Input.getKey(KeyEvent.VK_W) ? 1 : 0) - (Input.getKey(KeyEvent.VK_S) ? 1 : 0)) * speed;

		float horizontalDrag = onGround ? 0.5f : 0.94f;
		this.getVelocity().scalar(horizontalDrag, 0.99f, horizontalDrag);

		float xVel = sinYaw * accZ + cosYaw * accX;
		float zVel = cosYaw * accZ - sinYaw * accX;
		// 1.6x the original 0.004: the old value gave a 1.67-block, 1.05 s jump,
		// which read as floaty. This lands nearer 1.1 blocks / 0.6 s.
		float yVel = 0.0065f * GameSettings.tickScale;

		this.getVelocity().add(new Vector(xVel, yVel, zVel));

		//x = 0, y = 1, z = 2
		for (int rawCollAxis = 0; rawCollAxis < 3; rawCollAxis++)
		{
			int collAxis = 0;
			if(rawCollAxis == 1) collAxis = 2;
			if(rawCollAxis == 2) collAxis = 1;

			float newPlayerX = playerLoc.getX();
			float newPlayerY = playerLoc.getY();
			float newPlayerZ = playerLoc.getZ();

			if(collAxis == 0)
			{

				newPlayerX += this.getVelocity().getX();
			}

			if(collAxis == 1)
			{
				newPlayerY += this.getVelocity().getY();
			}


			if(collAxis == 2)
			{
				newPlayerZ += this.getVelocity().getZ();
			}


			//height
			boolean collison = false;
			boolean collisonX = false;
			boolean collisonY = false;
			boolean collisonZ = false;

			for(int i12 = 0; i12 < PLAYER_HEIGHT; i12++)
			{
				//x
				int i13 = (int)(newPlayerX + (float)(i12 & 1) * 0.6f - 0.3f) - 64;
				//y
				int i14 = (int)(newPlayerY + (float)((i12 >> 2) - 1) * 0.8f + 0.65f) - 64;
				//z
				int i15 = (int)(newPlayerZ + (float)((i12 >> 1) & 1) * 0.6f - 0.3f) - 64;

				if (i13 < 0 || i14 < 0 || i15 < 0 || i13 >= 64 || i14 >= 64 || i15 >= 64 || playerLoc.getWorld().getBlock(i13, i14, i15) != Block.AIR)
				{
					if (collAxis != 1)
					{
						if(collAxis == 0) {
							collisonX = true;
						}

						if(collAxis == 2) {
							collisonZ = true;
						}
					}
					else
					{
						if (Input.getKey(KeyEvent.VK_SPACE))
						{
							Input.setKey(KeyEvent.VK_SPACE, false);
							this.getVelocity().setY(-0.1365f * GameSettings.tickScale);
							this.setOnJump(true);
							this.setJumpingTicks(0);
							this.setOnGround(false);

						}
						collison = true;
						collisonY = true;
					}
				}
			}

			if(!collisonX) playerLoc.setX(newPlayerX);
			if(!collisonY)
			{
				playerLoc.setY(newPlayerY);
				this.setOnGround(false);
			}
			else
			{
				if(this.getJumpingTicks() > 0)
				{
					this.setJumpingTicks(-1);
					this.setOnJump(false);
				}

				// Do not re-ground on the tick the jump was initiated: that
				// gave the next tick a full ground-acceleration burst in mid-air.
				if(!this.isOnJump()) this.setOnGround(true);
				if(this.isOnJump()) this.incrementJumpingTicks();
				if(!this.isOnJump() && this.isOnGround()) this.getVelocity().setY(0.0f);
			}

			if(!collisonZ) playerLoc.setZ(newPlayerZ);

			if(collison) return;
		}

	}

	/**
	 * True when a block in this cell would overlap the player's collision box.
	 * The box matches Player.tick's collision sample offsets: 0.6 wide and
	 * 1.6 tall, from 0.15 above the origin to 1.45 below it (y grows down).
	 * Placement inside it is refused, which is what stops blocks appearing in
	 * the player and what makes "place under me" require a jump.
	 */
	private boolean blockIntersectsPlayer(Location block)
	{
		Location location = this.getLocation();

		final float halfWidth = 0.3f;
		final float top = 0.15f;
		final float bottom = 1.45f;

		float minX = location.getX() - halfWidth;
		float maxX = location.getX() + halfWidth;
		float minY = location.getY() - top;
		float maxY = location.getY() + bottom;
		float minZ = location.getZ() - halfWidth;
		float maxZ = location.getZ() + halfWidth;

		// World block coordinates are camera coordinates minus 64.
		float blockMinX = block.getX() + 64.0f;
		float blockMinY = block.getY() + 64.0f;
		float blockMinZ = block.getZ() + 64.0f;

		return maxX > blockMinX && minX < blockMinX + 1.0f
				&& maxY > blockMinY && minY < blockMinY + 1.0f
				&& maxZ > blockMinZ && minZ < blockMinZ + 1.0f;
	}

	public Location getTargetBlockPlace() {
		return targetBlockPlace;
	}

	public void setTargetBlockPlace(Location targetBlockPlace) {
		this.targetBlockPlace = targetBlockPlace;
	}

	public PlayerConnection getPlayerConnection() {
		return playerConnection;
	}
}
