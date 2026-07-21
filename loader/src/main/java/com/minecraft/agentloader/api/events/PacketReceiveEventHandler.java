package com.minecraft.agentloader.api.events;

/**
 * Handler for packet receive events.
 */
public interface PacketReceiveEventHandler {
    /**
     * Called when a packet is about to be received from the server.
     *
     * @param packet The packet object
     * @return true to cancel the packet receive, false to allow it
     */
    boolean onPacketReceive(Object packet);
}

