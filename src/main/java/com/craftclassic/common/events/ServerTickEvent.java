package com.craftclassic.common.events;

/**
 * Fired once per server simulation tick.
 *
 * <p>The server runs at a fixed {@code ticksPerSecond} (20 today), so a plugin
 * can schedule wall-clock behaviour with {@code tick % (ticksPerSecond * n)}.
 * This is a high-frequency notification; keep handlers cheap.</p>
 */
public class ServerTickEvent
{
    private final long tick;
    private final int ticksPerSecond;

    public ServerTickEvent(long tick, int ticksPerSecond)
    {
        this.tick = tick;
        this.ticksPerSecond = ticksPerSecond;
    }

    public long getTick()
    {
        return tick;
    }

    public int getTicksPerSecond()
    {
        return ticksPerSecond;
    }

    @Override
    public String toString()
    {
        return "ServerTickEvent{tick=" + tick + ", ticksPerSecond=" + ticksPerSecond + '}';
    }
}
