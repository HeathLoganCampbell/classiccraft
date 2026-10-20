package com.craftclassic.common.network;

import com.craftclassic.common.entities.Entity;
import com.craftclassic.common.world.World;

/**
 * Where the shared packet handler delivers world/entity/chat updates.
 *
 * <p>The handler cannot reference {@code client.Minecraft} directly: the GPU
 * client does not load that class (and the applet shell no longer exists on
 * modern JDKs). Each client installs itself here instead.</p>
 */
public final class ClientBridge
{
    public interface View
    {
        World getWorld();

        void addEntity(Entity entity);

        void onChatMessage(String sender, String message);

        default void onPing(long sentNanos) {}
    }

    /** Set by whichever client is running; null until then. */
    public static volatile View view;

    private ClientBridge() {}
}
