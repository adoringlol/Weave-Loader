package com.minecraft.agentloader.api.events;

/**
 * Handler for packet send events.
 */
public interface PacketEventHandler {
    /**
     * Called when a packet is about to be sent to the server.
     *
     * @param packet The packet object
     * @return true to cancel the packet send, false to allow it
     */
    boolean onPacketSend(Object packet);
}

