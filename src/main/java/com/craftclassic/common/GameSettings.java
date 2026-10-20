package com.craftclassic.common;

/**
 * Client-wide settings that are not tied to any particular shell.
 *
 * <p>These used to live as statics on {@code client.Minecraft}; keeping them here
 * lets the simulation and the GPU client run without loading the AWT/applet
 * entry point (which no longer exists on JDK 26).</p>
 */
public final class GameSettings
{
    private GameSettings() {}

    /** Block id that right-click places. */
    public static int placeBlockTypeId = 1;

    /**
     * Multiplier on per-tick motion (walk/sprint acceleration, gravity, jump).
     *
     * <p>The constants in {@code Player.tick} were tuned against a tick tied to
     * a ~60 fps render loop. The GPU client ticks at a fixed rate, so it sets
     * this to {@code 60 / ticksPerSecond} to keep movement in blocks per second
     * constant no matter how often the simulation runs.</p>
     */
    public static float tickScale = 1.0f;

    /** User-facing multiplier for horizontal movement only. */
    public static float speedMultiplier = 1.0f;
}
