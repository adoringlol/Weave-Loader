package com.minecraft.agentloader.api;

/**
 * Interface for custom commands that can be registered by mods.
 */
public interface Command {
    /**
     * Execute the command.
     *
     * @param context The mod context (for sending messages, etc.)
     * @param label The command label used (e.g., "/timer")
     * @param args Command arguments (split by spaces)
     * @param rawMessage The full raw message
     * @return true if the command was handled and should not be sent to server
     */
    boolean execute(ModContext context, String label, String[] args, String rawMessage);

    /**
     * Provide tab completion suggestions.
     *
     * @param label The command label
     * @param args Current arguments being typed
     * @param rawMessage The full raw message
     * @return List of completion suggestions
     */
    default java.util.List<String> onTabComplete(String label, String[] args, String rawMessage) {
        return java.util.Collections.emptyList();
    }

    /**
     * Get command aliases.
     * Return an array of aliases for this command.
     * The first alias is considered the primary command name.
     *
     * @return Array of command aliases (e.g., ["play", "p", "resume"])
     */
    default String[] getAliases() {
        return new String[0];
    }
}

