package com.minecraft.agentloader;

import com.minecraft.agentloader.api.events.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe dispatcher for routing events to registered mod handlers.
 * Uses CopyOnWriteArrayList to allow concurrent event dispatch from different threads
 * (render thread, client thread, network thread).
 */
public class EventDispatcher {
    private static final List<ChatEventHandler> chatHandlers = new CopyOnWriteArrayList<>();
    private static final List<RenderEventHandler> renderHandlers = new CopyOnWriteArrayList<>();
    private static final List<WorldEventHandler> worldHandlers = new CopyOnWriteArrayList<>();
    private static final List<PacketEventHandler> packetHandlers = new CopyOnWriteArrayList<>();
    private static final List<PacketReceiveEventHandler> packetReceiveHandlers = new CopyOnWriteArrayList<>();
    private static final List<RenderWorldEventHandler> renderWorldHandlers = new CopyOnWriteArrayList<>();
    public static boolean isEndstoneOverlayEnabled = false;


    // Fast-fail cache for dynamic mod hooks (prevents ClassNotFoundException spam when mods are absent)
    private static final java.util.Map<String, Boolean> missingModCache = new java.util.concurrent.ConcurrentHashMap<>();
    // Cache resolved Class, Field, and Method objects to avoid repeated reflection lookups
    private static final java.util.Map<String, Class<?>> classCache = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<String, java.lang.reflect.Field> fieldCache = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<String, java.lang.reflect.Method> methodCache = new java.util.concurrent.ConcurrentHashMap<>();

    // Tracks exceptions so we only print their stack trace once to avoid console spam and lag
    private static final java.util.Map<String, Boolean> errorPrinted = new java.util.concurrent.ConcurrentHashMap<>();

    public static boolean checkDynamicModBoolean(String className, String methodName, Object arg) {
        if (missingModCache.containsKey(className)) return false;
        try {
            java.lang.reflect.Method m = methodCache.get(methodName);
            if (m == null) {
                Class<?> clazz = classCache.get(className);
                if (clazz == null) {
                    clazz = Class.forName(className, true, ClassLoader.getSystemClassLoader());
                    classCache.put(className, clazz);
                }
                m = clazz.getDeclaredMethod(methodName, Object.class);
                methodCache.put(methodName, m);
            }
            return (Boolean) m.invoke(null, arg);
        } catch (ClassNotFoundException e) {
            missingModCache.put(className, true);
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean checkDynamicModFieldBoolean(String className, String fieldName) {
        if (missingModCache.containsKey(className)) return false;
        try {
            String key = className + fieldName; // no separator needed, these are unique enough
            java.lang.reflect.Field f = fieldCache.get(key);
            if (f == null) {
                Class<?> clazz = classCache.get(className);
                if (clazz == null) {
                    clazz = Class.forName(className, true, ClassLoader.getSystemClassLoader());
                    classCache.put(className, clazz);
                }
                f = clazz.getField(fieldName);
                fieldCache.put(key, f);
            }
            return f.getBoolean(null);
        } catch (ClassNotFoundException e) {
            missingModCache.put(className, true);
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    // Registration methods

    public static void registerChatListener(ChatEventHandler handler) {
        chatHandlers.add(handler);
    }

    public static void registerRenderOverlay(RenderEventHandler handler) {
        renderHandlers.add(handler);
    }

    public static void registerWorldListener(WorldEventHandler handler) {
        worldHandlers.add(handler);
    }

    public static void registerPacketListener(PacketEventHandler handler) {
        packetHandlers.add(handler);
    }

    public static void registerPacketReceiveListener(PacketReceiveEventHandler handler) {
        packetReceiveHandlers.add(handler);
    }

    public static void registerRenderWorldListener(RenderWorldEventHandler handler) {
        renderWorldHandlers.add(handler);
    }

    // Dispatch methods (called from bytecode injections)

    /**
     * Dispatch chat message to all registered handlers.
     * Called from ChatInterceptor.
     */
    public static void dispatchChatMessage(Object chatComponent, String formattedText) {
        if (chatHandlers.isEmpty()) return;
        for (ChatEventHandler handler : chatHandlers) {
            try {
                handler.onChatMessage(chatComponent, formattedText);
            } catch (Exception e) {
                String key = handler.getClass().getName() + ":" + e.getClass().getName();
                if (errorPrinted.putIfAbsent(key, true) == null) {
                    Log.error("Error in legacy chat handler: " + e.getMessage(), e);
                }
            }
        }
    }

    /**
     * Dispatch render overlay to all registered handlers.
     * Called from bytecode injection in GuiIngame.
     */
    public static void dispatchRenderOverlay(float partialTicks) {
        if (renderHandlers.isEmpty()) return;
        for (RenderEventHandler handler : renderHandlers) {
            try {
                handler.onRenderOverlay(partialTicks);
            } catch (Exception e) {
                String key = handler.getClass().getName() + ":" + e.getClass().getName();
                if (errorPrinted.putIfAbsent(key, true) == null) {
                    Log.error("Error in legacy render handler: " + e.getMessage(), e);
                }
            }
        }
    }

    /**
     * Dispatch world change to all registered handlers.
     * Called from bytecode injection in Minecraft.
     */
    public static void dispatchWorldChange() {
        if (worldHandlers.isEmpty()) return;
        for (WorldEventHandler handler : worldHandlers) {
            try {
                handler.onWorldChange();
            } catch (Exception e) {
                String key = handler.getClass().getName() + ":" + e.getClass().getName();
                if (errorPrinted.putIfAbsent(key, true) == null) {
                    Log.error("Error in legacy world handler: " + e.getMessage(), e);
                }
            }
        }
    }

    private static String detectMinecraftVersion(ClassLoader gameLoader) {
        // 1. System/launch properties — set by Lunar, MultiMC, Forge, etc.
        for (String prop : new String[]{
                "minecraft.version", "launch.minecraft.version",
                "minecraftVersion", "mc.version", "game.version"}) {
            String val = System.getProperty(prop);
            if (val != null && looksLikeVersion(val)) {
                Log.debug("Version via property '" + prop + "': " + val);
                return val;
            }
        }

        try {
            Object mc = CommandHandler.getMinecraftInstance(gameLoader);
            if (mc != null) {
                // 2. getVersion() method (deobf + searge)
                for (String methodName : new String[]{"getVersion", "func_82755_b"}) {
                    try {
                        Object result = mc.getClass().getMethod(methodName).invoke(mc);
                        if (result != null && looksLikeVersion(result.toString())) {
                            Log.debug("Version via " + methodName + "(): " + result);
                            return result.toString();
                        }
                    } catch (Exception ignored) {}
                }

                // 3. Scan every String field on the full class hierarchy (instance + static)
                Class<?> clazz = mc.getClass();
                while (clazz != null && !clazz.equals(Object.class)) {
                    for (java.lang.reflect.Field f : clazz.getDeclaredFields()) {
                        if (f.getType() != String.class) continue;
                        try {
                            f.setAccessible(true);
                            boolean isStatic = java.lang.reflect.Modifier.isStatic(f.getModifiers());
                            Object val = f.get(isStatic ? null : mc);
                            if (val != null && looksLikeVersion(val.toString())) {
                                Log.debug("Version via field '"
                                        + clazz.getSimpleName() + "." + f.getName() + "': " + val);
                                return val.toString();
                            }
                        } catch (Exception ignored) {}
                    }
                    clazz = clazz.getSuperclass();
                }
            }

            // 4. Forge
            try {
                Class<?> forge = Class.forName("net.minecraftforge.fml.common.FMLCommonHandler", true, gameLoader);
                Object result = forge.getMethod("getBranch").invoke(null);
                if (result != null && looksLikeVersion(result.toString())) {
                    Log.debug("Version via Forge FMLCommonHandler: " + result);
                    return result.toString();
                }
            } catch (Exception ignored) {}

        } catch (Exception e) {
            Log.debug("Version detection error: " + e.getMessage());
        }

        Log.debug("Could not detect Minecraft version");
        return null;
    }

    private static boolean looksLikeVersion(String s) {
        return s != null && s.matches("^1\\.\\d+(\\.\\d+)?.*");
    }

    /**
     * Dispatch packet send to all registered handlers.
     * Called from ChatInterceptor.
     *
     * @return true if any handler cancelled the packet
     */
    public static boolean dispatchPacketSend(Object packet) {
        if (packetHandlers.isEmpty()) return false;
        for (PacketEventHandler handler : packetHandlers) {
            try {
                if (handler.onPacketSend(packet)) {
                    return true;
                }
            } catch (Exception e) {
                String key = handler.getClass().getName() + ":" + e.getClass().getName();
                if (errorPrinted.putIfAbsent(key, true) == null) {
                    Log.error("Error in legacy packet handler: " + e.getMessage(), e);
                }
            }
        }
        return false;
    }

    /**
     * Dispatch packet receive to all registered handlers.
     * Called from NetworkManager hook.
     *
     * @return true if any handler cancelled the packet
     */
    public static boolean dispatchPacketReceive(Object packet) {
        if (packetReceiveHandlers.isEmpty()) return false;
        for (PacketReceiveEventHandler handler : packetReceiveHandlers) {
            try {
                if (handler.onPacketReceive(packet)) {
                    return true;
                }
            } catch (Exception e) {
                String key = handler.getClass().getName() + ":" + e.getClass().getName();
                if (errorPrinted.putIfAbsent(key, true) == null) {
                    Log.error("Error in legacy packet receive handler: " + e.getMessage(), e);
                }
            }
        }
        return false;
    }

    /**
     * Dispatch render world to all registered handlers.
     * Called from RenderGlobal hook after entities are rendered.
     */
    private static boolean renderWorldFiredOnce = false;
    public static void dispatchRenderWorld(float partialTicks) {
        if (!renderWorldFiredOnce) {
            renderWorldFiredOnce = true;
            Log.debug("dispatchRenderWorld fired; handlers=" + renderWorldHandlers.size());
        }
        if (renderWorldHandlers.isEmpty()) return;
        for (RenderWorldEventHandler handler : renderWorldHandlers) {
            try {
                handler.onRenderWorld(partialTicks);
            } catch (Exception e) {
                String key = handler.getClass().getName() + ":" + e.getClass().getName();
                if (errorPrinted.putIfAbsent(key, true) == null) {
                    Log.error("Error in legacy render-world handler: " + e.getMessage(), e);
                }
            }
        }
    }
}
