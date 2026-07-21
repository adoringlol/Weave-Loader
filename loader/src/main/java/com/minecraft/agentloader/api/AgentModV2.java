package com.minecraft.agentloader.api;

/**
 * V2 mod interface for the new mod loader.
 * Mods implementing this interface will ONLY load on loader v2+.
 * Old loaders scan only for AgentMod, so they will never discover AgentModV2 mods.
 *
 * New mods should:
 *   1. Implement AgentModV2 instead of AgentMod
 *   2. Register in META-INF/services/com.minecraft.agentloader.api.AgentModV2
 *
 * Old mods that implement AgentMod directly will continue to work on both loaders.
 */
public interface AgentModV2 extends AgentMod {
}

