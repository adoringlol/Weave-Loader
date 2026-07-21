package com.minecraft.agentloader;

import com.minecraft.agentloader.api.Command;
import java.lang.reflect.Method;

/**
 * Handles command execution and provides utility methods for accessing Minecraft.
 */
public class CommandHandler {

    private static Method addChatMethod;

    public static boolean handleCommand(Object message) {
        if (message == null)
            return false;
        String msg = message.toString().trim();

        if (msg.isEmpty() || !msg.startsWith("/"))
            return false;

        String[] parts = msg.split("\\s+");
        String label = parts[0].substring(1).toLowerCase();

        Log.debug("[CommandHandler] Looking up command: /" + label);

        // Use CommandRegistry instead of static map
        CommandRegistry.CommandEntry entry = CommandRegistry.getCommandEntry(label);
        if (entry != null) {
            Log.debug("[CommandHandler] Found command: " + entry.command.getClass().getSimpleName());
            String[] args = new String[parts.length - 1];
            System.arraycopy(parts, 1, args, 0, args.length);

            try {
                boolean result = entry.command.execute(entry.context, label, args, msg);
                Log.debug("[CommandHandler] /" + label + " returned: " + result);
                if (result) {
                    addToSentHistory(msg);
                }
                return result;
            } catch (Exception e) {
                Log.error("[CommandHandler] /" + label + " execution failed: " + e.getMessage(), e);
                return false;
            }
        }

        Log.debug("[CommandHandler] Command not found: /" + label);
        return false;
    }

    public static java.util.List<String> getTabCompletions(String message) {
        if (message == null)
            return java.util.Collections.emptyList();

        if (message.isEmpty() || !message.startsWith("/"))
            return java.util.Collections.emptyList();

        String[] parts = message.split(" ", -1);
        String label = parts[0].substring(1).toLowerCase();

        if (parts.length == 1) {
            // No space yet - return all matching command names (with "/" prefix so the chat box fills correctly)
            java.util.List<String> completions = new java.util.ArrayList<>();
            for (String key : CommandRegistry.getAllKeys()) {
                if (key.startsWith(label)) {
                    completions.add("/" + key);
                }
            }
            completions.sort(null);
            return completions;
        }

        CommandRegistry.CommandEntry entry = CommandRegistry.getCommandEntry(label);
        if (entry != null) {
            String[] args = new String[parts.length - 1];
            System.arraycopy(parts, 1, args, 0, args.length);
            return entry.command.onTabComplete(label, args, message);
        }

        return java.util.Collections.emptyList();
    }

    public static Object getMinecraftInstance(ClassLoader gameLoader) throws Exception {
        try {
            Class<?> mcClass = Class.forName("net.minecraft.client.Minecraft", true, gameLoader);
            return mcClass.getMethod("getMinecraft").invoke(null);
        } catch (Exception e) {
            Class<?> mcClass = Class.forName("ave", true, gameLoader);
            return mcClass.getMethod("A").invoke(null);
        }
    }

    public static void sendClientMessage(String message) {
        try {
            ClassLoader gameLoader = Thread.currentThread().getContextClassLoader();
            if (gameLoader == null)
                return;

            if (addChatMethod == null) {
                resolveChatMethod(gameLoader);
            }

            if (addChatMethod == null) {
                Log.debug("[CommandHandler] sendClientMessage: addChatMethod is null, gameLoader=" + gameLoader);
                return;
            }

            Object mc = getMinecraftInstance(gameLoader);
            if (mc == null)
                return;

            Object player = null;
            try {
                player = mc.getClass().getField("thePlayer").get(mc);
            } catch (Exception e) {
                try {
                    player = mc.getClass().getField("h").get(mc);
                } catch (Exception e2) {
                    return;
                }
            }
            if (player == null)
                return;

            Class<?>[] paramTypes = addChatMethod.getParameterTypes();
            if (paramTypes.length == 1) {
                Class<?> paramType = paramTypes[0];
                Object parameter = null;

                if (paramType == String.class) {
                    parameter = message;
                } else {
                    try {
                        Class<?> chatClass = Class.forName("net.minecraft.util.ChatComponentText", true, gameLoader);
                        parameter = chatClass.getConstructor(String.class).newInstance(message);
                    } catch (Exception e) {
                        try {
                            Class<?> chatClass = Class.forName("eu", true, gameLoader);
                            parameter = chatClass.getConstructor(String.class).newInstance(message);
                        } catch (Exception e2) {
                            return;
                        }
                    }
                }

                addChatMethod.invoke(player, parameter);
            }
        } catch (Exception e) {
            Log.debug("[CommandHandler] sendClientMessage failed: " + e.getClass().getName() + ": " + e.getMessage());
        }
    }

    private static void addToSentHistory(String msg) {
        try {
            ClassLoader gameLoader = Thread.currentThread().getContextClassLoader();
            if (gameLoader == null) return;

            Object mc = getMinecraftInstance(gameLoader);
            if (mc == null) return;

            Object ingameGUI = null;
            for (String fieldName : new String[]{"ingameGUI", "q", "field_71456_i"}) {
                try {
                    java.lang.reflect.Field f = mc.getClass().getDeclaredField(fieldName);
                    f.setAccessible(true);
                    ingameGUI = f.get(mc);
                    if (ingameGUI != null) break;
                } catch (Exception ignored) {}
            }
            if (ingameGUI == null) return;

            Object chatGUI = null;
            for (String methodName : new String[]{"getChatGUI", "e", "func_110320_d"}) {
                try {
                    java.lang.reflect.Method m = ingameGUI.getClass().getMethod(methodName);
                    chatGUI = m.invoke(ingameGUI);
                    if (chatGUI != null) break;
                } catch (Exception ignored) {}
            }
            if (chatGUI == null) return;

            for (String methodName : new String[]{"addToSentMessages", "a", "func_146231_a"}) {
                try {
                    java.lang.reflect.Method m = chatGUI.getClass().getMethod(methodName, String.class);
                    m.invoke(chatGUI, msg);
                    return;
                } catch (Exception ignored) {}
            }

            for (java.lang.reflect.Field f : chatGUI.getClass().getDeclaredFields()) {
                if (java.util.List.class.isAssignableFrom(f.getType())) {
                    try {
                        f.setAccessible(true);
                        @SuppressWarnings("unchecked")
                        java.util.List<String> list = (java.util.List<String>) f.get(chatGUI);
                        if (list != null && !list.isEmpty()) {
                            list.add(msg);
                            return;
                        }
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception e) {
            Log.debug("[CommandHandler] addToSentHistory failed: " + e.getMessage());
        }
    }

    private static void resolveChatMethod(ClassLoader gameLoader) {
        try {
            Class<?> playerClass = null;
            try {
                playerClass = Class.forName("net.minecraft.client.entity.EntityPlayerSP", true, gameLoader);
            } catch (Exception e) {
                try {
                    playerClass = Class.forName("bew", true, gameLoader);
                } catch (Exception e2) {
                    return;
                }
            }

            Class<?> chatCompInterface = null;
            try {
                chatCompInterface = Class.forName("net.minecraft.util.IChatComponent", true, gameLoader);
            } catch (Exception e) {
                try {
                    chatCompInterface = Class.forName("eu", true, gameLoader);
                } catch (Exception e2) {
                    chatCompInterface = null;
                }
            }

            if (chatCompInterface != null) {
                try {
                    addChatMethod = playerClass.getMethod("addChatMessage", chatCompInterface);
                    Log.debug("[CommandHandler] resolveChatMethod (addChatMessage IChatComponent): " + addChatMethod);
                    return;
                } catch (Exception ignored) {}

                try {
                    addChatMethod = playerClass.getMethod("a", chatCompInterface);
                    Log.debug("[CommandHandler] resolveChatMethod (obfuscated IChatComponent): " + addChatMethod);
                    return;
                } catch (Exception ignored) {}
            }

            try {
                addChatMethod = playerClass.getMethod("addChatMessage", String.class);
                Log.debug("[CommandHandler] resolveChatMethod (addChatMessage String): " + addChatMethod);
                return;
            } catch (Exception ignored) {}

            try {
                addChatMethod = playerClass.getMethod("a", String.class);
                Log.debug("[CommandHandler] resolveChatMethod (obfuscated String): " + addChatMethod);
                return;
            } catch (Exception ignored) {}

            try {
                addChatMethod = playerClass.getMethod("sendChatMessage", String.class);
                Log.debug("[CommandHandler] resolveChatMethod (sendChatMessage String): " + addChatMethod);
                return;
            } catch (Exception ignored) {}

            try {
                addChatMethod = playerClass.getMethod("e", String.class);
                Log.debug("[CommandHandler] resolveChatMethod (obfuscated sendChatMessage): " + addChatMethod);
            } catch (Exception e) {
                Log.debug("[CommandHandler] resolveChatMethod failed: " + e.getClass().getName() + ": " + e.getMessage());
            }
        } catch (Exception e) {
            Log.debug("[CommandHandler] resolveChatMethod failed: " + e.getClass().getName() + ": " + e.getMessage());
        }
    }
}
