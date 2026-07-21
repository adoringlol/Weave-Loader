package com.minecraft.agentloader;

/**
 * Minimal levelled logger for the loader. Debug output is OFF by default and gated behind
 * {@code -Dweave.legacyAgentMods.debug=true}, so the per-command / per-transformed-class chatter that
 * used to run unconditionally (and cost String-building + console I/O on hot paths) is silent
 * in normal use. Warnings and errors always print.
 *
 * <p>Intentionally tiny and dependency-free — the loader is a javaagent and must not drag in
 * a logging framework.
 */
public final class Log {
    private Log() {}

    public static final boolean DEBUG = Boolean.getBoolean("weave.legacyAgentMods.debug")
            || Boolean.getBoolean("agentloader.debug");

    private static final String PREFIX = "[AgentLoader] ";

    /** Verbose tracing — compiled-out cost is a single boolean check when disabled. */
    public static void debug(String msg) {
        if (DEBUG) System.out.println(PREFIX + msg);
    }

    public static void info(String msg) {
        System.out.println(PREFIX + msg);
    }

    public static void warn(String msg) {
        System.err.println(PREFIX + msg);
    }

    public static void error(String msg, Throwable t) {
        System.err.println(PREFIX + msg);
        if (t != null && DEBUG) t.printStackTrace();
    }

    public static void error(String msg) {
        error(msg, null);
    }
}
