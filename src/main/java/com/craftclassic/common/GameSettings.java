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
}
