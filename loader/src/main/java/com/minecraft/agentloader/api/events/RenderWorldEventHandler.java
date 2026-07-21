package com.minecraft.agentloader.api.events;

/**
 * Event handler for rendering in the 3D world space.
 */
public interface RenderWorldEventHandler {
    /**
     * Called every frame after the world is rendered, before GUI.
     * Allows rendering custom OpenGL shapes with the world projection matrix active.
     *
     * @param partialTicks The render partial ticks
     */
    void onRenderWorld(float partialTicks);
}

