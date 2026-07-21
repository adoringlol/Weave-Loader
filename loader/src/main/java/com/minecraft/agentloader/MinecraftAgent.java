package com.minecraft.agentloader;

import java.lang.instrument.Instrumentation;

/**
 * Compatibility holder for legacy agent mods.
 *
 * The original project used this class as a second Java-agent entry point. In the
 * Weave fork, Weave owns bootstrap and instrumentation; this class only preserves
 * the binary API used by the legacy helpers and existing mods.
 */
public final class MinecraftAgent {
    public static volatile Instrumentation instrumentation;

    private MinecraftAgent() {}

    static void initialize(Instrumentation value) {
        instrumentation = value;

        // The old V2 mods resolve this holder explicitly through the system
        // classloader. Lunar also loads Weave inside its transformed game loader,
        // which creates a second copy of this class. Keep both copies pointed at
        // Weave's Instrumentation instance without starting a second javaagent.
        try {
            ClassLoader systemLoader = ClassLoader.getSystemClassLoader();
            Class<?> systemCopy = Class.forName(MinecraftAgent.class.getName(), true, systemLoader);
            if (systemCopy != MinecraftAgent.class) {
                systemCopy.getField("instrumentation").set(null, value);
                Log.debug("Published Instrumentation to the system-classloader compatibility holder");
            }
        } catch (Throwable error) {
            Log.warn("Could not publish Instrumentation to the system classloader: " + error.getMessage());
        }
    }
}
