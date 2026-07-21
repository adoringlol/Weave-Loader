package com.minecraft.agentloader.api;

/**
 * Main interface that all agent mods must implement.
 * Mods are discovered via ServiceLoader, so implementations must be declared
 * in META-INF/services/com.minecraft.agentloader.api.AgentMod
 */
public interface AgentMod {
    /**
     * Called when the mod is initialized by the agent.
     * Register commands, event handlers, and perform setup here.
     *
     * @param context Provides access to the agent's API
     */
    void onInitialize(ModContext context);

    /**
     * Unique identifier for this mod (e.g., "com.example.mymod")
     *
     * @return The mod ID
     */
    String getModId();

    /**
     * Version string for this mod (e.g., "1.0.0")
     *
     * @return The mod version
     */
    String getVersion();

    /**
     * Called when the mod is being shut down.
     * Cleanup resources here if needed.
     */
    default void onShutdown() {}
}

