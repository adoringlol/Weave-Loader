package com.minecraft.agentloader.api.events;

/**
 * Handler for world change events.
 */
public interface WorldEventHandler {
    /**
     * Called when the world changes (disconnect, dimension change, etc.)
     */
    void onWorldChange();
}

