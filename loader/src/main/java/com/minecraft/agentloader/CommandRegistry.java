package com.minecraft.agentloader;

import com.minecraft.agentloader.api.Command;
import com.minecraft.agentloader.api.ModContext;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe command registry used by the legacy-command translation layer. */
public final class CommandRegistry {
    private static final ConcurrentHashMap<String, CommandEntry> commands = new ConcurrentHashMap<>();

    private CommandRegistry() {}

    public static void register(Command command, ModContext context, String... aliases) {
        if (command == null) throw new IllegalArgumentException("command cannot be null");
        String[] effective = aliases;
        if (effective == null || effective.length == 0) effective = command.getAliases();
        if (effective == null || effective.length == 0) {
            throw new IllegalArgumentException("Legacy command must provide at least one name");
        }

        CommandEntry entry = new CommandEntry(command, context);
        for (String alias : effective) {
            String key = normalize(alias);
            commands.put(key, entry);
            Log.debug("Registered legacy command /" + key);
        }
    }

    public static CommandEntry getCommandEntry(String alias) {
        return alias == null ? null : commands.get(normalize(alias));
    }

    public static Set<String> getAllKeys() {
        return commands.keySet();
    }

    public static void unregister(String alias) {
        if (alias != null) commands.remove(normalize(alias));
    }

    private static String normalize(String alias) {
        String value = alias == null ? "" : alias.trim();
        while (value.startsWith("/")) value = value.substring(1);
        if (value.isEmpty()) throw new IllegalArgumentException("Legacy command name cannot be empty");
        return value.toLowerCase(Locale.ROOT);
    }

    public static final class CommandEntry {
        public final Command command;
        public final ModContext context;

        CommandEntry(Command command, ModContext context) {
            this.command = command;
            this.context = context;
        }
    }
}
