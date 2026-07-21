package com.minecraft.agentloader.api.events;

/**
 * Handler for incoming chat message events.
 */
public interface ChatEventHandler {
    /**
     * Called when a chat message is received.
     *
     * @param chatComponent The raw chat component object
     * @param formattedText The formatted text extracted from the component (with color codes)
     */
    void onChatMessage(Object chatComponent, String formattedText);
}

