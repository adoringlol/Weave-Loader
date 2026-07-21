package com.minecraft.agentloader.api.events;

/**
 * Handler for render overlay events.
 */
public interface RenderEventHandler {
    /**
     * Called each frame to render overlays on the HUD.
     *
     * @param partialTicks The partial tick time for smooth rendering
     */
    void onRenderOverlay(float partialTicks);
}

