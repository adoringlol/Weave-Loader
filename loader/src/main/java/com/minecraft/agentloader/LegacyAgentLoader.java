package com.minecraft.agentloader;

import java.lang.instrument.Instrumentation;
import java.util.concurrent.atomic.AtomicBoolean;

/** Entry point for the old loader's non-auth functionality inside Weave. */
public final class LegacyAgentLoader {
    private static final AtomicBoolean initialized = new AtomicBoolean();

    private LegacyAgentLoader() {}

    public static void initialize(Instrumentation instrumentation, String gameVersion) {
        if (!Boolean.parseBoolean(System.getProperty("weave.legacyAgentMods.enabled", "true"))) {
            Log.info("Legacy agentmod compatibility is disabled");
            return;
        }
        if (!initialized.compareAndSet(false, true)) return;

        MinecraftAgent.initialize(instrumentation);
        try {
            new LegacyEventBridge().subscribe();
        } catch (ClassNotFoundException error) {
            initialized.set(false);
            throw new IllegalStateException("The version-specific Weave API was not available", error);
        }
        ModLoader.loadMods(gameVersion);

        try {
            ModContextImpl builtins = new ModContextImpl("agentloader");
            builtins.registerCommand(new ModsCommand(), "mods", "modlist");
        } catch (Throwable error) {
            Log.error("Failed to register legacy /mods command", error);
        }
    }
}
