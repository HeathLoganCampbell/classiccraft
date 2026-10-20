package com.craftclassic.common.world;

import com.craftclassic.common.blocks.Block;

/**
 * One ray through the crosshair, for clients that do not run {@link World#render()}.
 *
 * <p>The GPU backend draws with a shader, so it never executes the CPU pixel loop
 * that used to discover the targeted block. This repeats the centre-pixel ray
 * (yaw/pitch scaled by 100, block = floor(camera) - 64) with the same 5-block
 * reach, and also reports the adjacent cell used for placement.</p>
 */
public final class Raycast
{
    public static final float DEFAULT_REACH = 5.0f;

    private static final int WORLD_SIZE = 64;
    private static final int WORLD_ORIGIN = 64;

    private Raycast() {}

    public static final class Hit
    {
        private final int x, y, z;
        private final int placeX, placeY, placeZ;
        private final int axis;
        private final float distance;

        Hit(int x, int y, int z, int placeX, int placeY, int placeZ, int axis, float distance)
        {
            this.x = x;
            this.y = y;
            this.z = z;
            this.placeX = placeX;
            this.placeY = placeY;
            this.placeZ = placeZ;
            this.axis = axis;
            this.distance = distance;
        }

        public int getX() { return x; }
        public int getY() { return y; }
        public int getZ() { return z; }

        /** Cell to place into: the hit cell, moved one step back along the face axis. */
        public int getPlaceX() { return placeX; }
        public int getPlaceY() { return placeY; }
        public int getPlaceZ() { return placeZ; }

        /** 0 = x face, 1 = y face, 2 = z face, -1 = started inside a block. */
        public int getAxis() { return axis; }

        public float getDistance() { return distance; }
    }

    public static Hit cast(World world, float camX, float camY, float camZ,
                           float yaw, float pitch, float reach)
    {
        float sinYaw = (float) Math.sin(yaw * 100f);
        float cosYaw = (float) Math.cos(yaw * 100f);
        float sinPitch = (float) Math.sin(pitch * 100f);
        float cosPitch = (float) Math.cos(pitch * 100f);

        // World.render() with vpixelLoc = hpixelLoc = 0, i.e. the centre pixel.
        float dirX = cosPitch * sinYaw;
        float dirY = -sinPitch;
        float dirZ = cosPitch * cosYaw;

        float floorX = (float) Math.floor(camX);
        float floorY = (float) Math.floor(camY);
        float floorZ = (float) Math.floor(camZ);

        int cellX = (int) floorX - WORLD_ORIGIN;
        int cellY = (int) floorY - WORLD_ORIGIN;
        int cellZ = (int) floorZ - WORLD_ORIGIN;

        int stepX = dirX >= 0.0f ? 1 : -1;
        int stepY = dirY >= 0.0f ? 1 : -1;
        int stepZ = dirZ >= 0.0f ? 1 : -1;

        float tDeltaX = 1.0f / Math.max(Math.abs(dirX), 1e-6f);
        float tDeltaY = 1.0f / Math.max(Math.abs(dirY), 1e-6f);
        float tDeltaZ = 1.0f / Math.max(Math.abs(dirZ), 1e-6f);

        float tMaxX = (dirX > 0.0f ? floorX + 1.0f - camX : camX - floorX) * tDeltaX;
        float tMaxY = (dirY > 0.0f ? floorY + 1.0f - camY : camY - floorY) * tDeltaY;
        float tMaxZ = (dirZ > 0.0f ? floorZ + 1.0f - camZ : camZ - floorZ) * tDeltaZ;

        float distance = 0.0f;
        int axis = -1;

        while (distance < reach)
        {
            if (cellX < 0 || cellY < 0 || cellZ < 0
                    || cellX >= WORLD_SIZE || cellY >= WORLD_SIZE || cellZ >= WORLD_SIZE)
                return null;

            if (world.getBlock(cellX, cellY, cellZ) != Block.AIR)
            {
                int placeX = cellX;
                int placeY = cellY;
                int placeZ = cellZ;

                if (axis == 0) placeX -= stepX;
                if (axis == 1) placeY -= stepY;
                if (axis == 2) placeZ -= stepZ;

                return new Hit(cellX, cellY, cellZ, placeX, placeY, placeZ, axis, distance);
            }

            if (tMaxX < tMaxY)
            {
                if (tMaxX < tMaxZ) { cellX += stepX; distance = tMaxX; tMaxX += tDeltaX; axis = 0; }
                else               { cellZ += stepZ; distance = tMaxZ; tMaxZ += tDeltaZ; axis = 2; }
            }
            else
            {
                if (tMaxY < tMaxZ) { cellY += stepY; distance = tMaxY; tMaxY += tDeltaY; axis = 1; }
                else               { cellZ += stepZ; distance = tMaxZ; tMaxZ += tDeltaZ; axis = 2; }
            }
        }

        return null;
    }
}
