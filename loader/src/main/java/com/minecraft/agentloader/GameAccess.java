package com.minecraft.agentloader;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared, cached reflection helpers backing the convenience methods on {@link
 * com.minecraft.agentloader.api.ModContext}. Centralizes the obf/MCP dual-name field and
 * method resolution that mods used to hand-roll (~150 lines each, across 17 mods), and
 * caches every resolved {@link Field}/{@link Method} per (class, name) so the hot paths do
 * no repeated {@code Class.forName}/{@code getDeclaredMethod} work.
 *
 * <p>Everything here is defensive: a missing field/method yields a sane fallback rather than
 * throwing, so a single mapping miss degrades one value instead of crashing a mod. All access
 * resolves the live game classes through the thread-context classloader (the game runs on a
 * child loader), matching how {@link CommandHandler#getMinecraftInstance} already works.
 */
public final class GameAccess {
    private GameAccess() {}

    // Cache resolved members keyed by "className#member|name1,name2,...".
    private static final ConcurrentHashMap<String, Field> FIELD_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Method> METHOD_CACHE = new ConcurrentHashMap<>();
    // Sentinels so a confirmed miss is cached too (avoids re-scanning every frame).
    private static final Field NO_FIELD;
    private static final Method NO_METHOD;
    static {
        Field f = null;
        Method m = null;
        try { f = GameAccess.class.getDeclaredField("NO_FIELD"); } catch (Throwable ignored) {}
        try { m = GameAccess.class.getDeclaredMethod("toString"); } catch (Throwable ignored) {}
        NO_FIELD = f;
        NO_METHOD = m;
    }

    // Cached Minecraft singleton accessor (P3: avoids per-frame Class.forName + getMethod).
    private static volatile Method mcAccessor;
    private static volatile boolean mcAccessorResolved;

    // ------------------------------------------------------------------
    // Minecraft instance (cached)
    // ------------------------------------------------------------------

    /** The Minecraft singleton, resolved once and cached. Null if unavailable. */
    public static Object minecraft() {
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if (cl == null) cl = GameAccess.class.getClassLoader();
            if (!mcAccessorResolved) {
                synchronized (GameAccess.class) {
                    if (!mcAccessorResolved) {
                        try {
                            Class<?> mcClass = Class.forName("net.minecraft.client.Minecraft", true, cl);
                            mcAccessor = mcClass.getMethod("getMinecraft");
                        } catch (Throwable t) {
                            try {
                                Class<?> mcClass = Class.forName("ave", true, cl);
                                mcAccessor = mcClass.getMethod("A");
                            } catch (Throwable ignored) {}
                        }
                        mcAccessorResolved = true;
                    }
                }
            }
            return mcAccessor == null ? null : mcAccessor.invoke(null);
        } catch (Throwable t) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Generic resolution (Batch 6 utility + internal use)
    // ------------------------------------------------------------------

    /** Resolves the first matching field by walking the superclass chain; caches hit and miss. */
    public static Field resolveField(Object target, String... names) {
        if (target == null || names.length == 0) return null;
        String key = target.getClass().getName() + "#F|" + String.join(",", names);
        Field cached = FIELD_CACHE.get(key);
        if (cached != null) return cached == NO_FIELD ? null : cached;
        for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
            for (String name : names) {
                try {
                    Field f = c.getDeclaredField(name);
                    f.setAccessible(true);
                    FIELD_CACHE.put(key, f);
                    return f;
                } catch (NoSuchFieldException ignored) {}
            }
        }
        FIELD_CACHE.put(key, NO_FIELD);
        return null;
    }

    /** Resolves the first matching method (by name + arity) walking the superclass chain. */
    public static Method resolveMethod(Object target, int argCount, String... names) {
        if (target == null || names.length == 0) return null;
        String key = target.getClass().getName() + "#M" + argCount + "|" + String.join(",", names);
        Method cached = METHOD_CACHE.get(key);
        if (cached != null) return cached == NO_METHOD ? null : cached;
        for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
            for (String name : names) {
                for (Method m : c.getDeclaredMethods()) {
                    if (m.getName().equals(name) && m.getParameterCount() == argCount) {
                        m.setAccessible(true);
                        METHOD_CACHE.put(key, m);
                        return m;
                    }
                }
            }
        }
        METHOD_CACHE.put(key, NO_METHOD);
        return null;
    }

    public static Object getField(Object target, String... names) {
        Field f = resolveField(target, names);
        if (f == null) return null;
        try { return f.get(target); } catch (Throwable t) { return null; }
    }

    public static double getDouble(Object target, double fallback, String... names) {
        Field f = resolveField(target, names);
        if (f == null) return fallback;
        try { return f.getDouble(target); } catch (Throwable t) { return fallback; }
    }

    public static float getFloat(Object target, float fallback, String... names) {
        Field f = resolveField(target, names);
        if (f == null) return fallback;
        try { return f.getFloat(target); } catch (Throwable t) { return fallback; }
    }

    public static int getInt(Object target, int fallback, String... names) {
        Field f = resolveField(target, names);
        if (f == null) return fallback;
        try { return f.getInt(target); } catch (Throwable t) { return fallback; }
    }

    public static boolean getBoolean(Object target, boolean fallback, String... names) {
        Field f = resolveField(target, names);
        if (f == null) return fallback;
        try { return f.getBoolean(target); } catch (Throwable t) { return fallback; }
    }

    public static Object invoke(Object target, String methodName, Object... args) {
        Method m = resolveMethod(target, args.length, methodName);
        if (m == null) return null;
        try { return m.invoke(target, args); } catch (Throwable t) { return null; }
    }

    // ------------------------------------------------------------------
    // Player / entity state (Batch 1)
    // ------------------------------------------------------------------

    public static Object thePlayer() {
        return getField(minecraft(), "thePlayer", "field_71439_g");
    }

    public static Object theWorld() {
        return getField(minecraft(), "theWorld", "field_71441_e");
    }

    public static double[] entityPosition(Object entity) {
        if (entity == null) return null;
        return new double[]{
                getDouble(entity, 0, "posX", "field_70165_t"),
                getDouble(entity, 0, "posY", "field_70163_u"),
                getDouble(entity, 0, "posZ", "field_70161_v")
        };
    }

    public static float[] entityRotation(Object entity) {
        if (entity == null) return null;
        return new float[]{
                getFloat(entity, 0, "rotationYaw", "field_70177_z"),
                getFloat(entity, 0, "rotationPitch", "field_70125_A")
        };
    }

    public static double[] entityMotion(Object entity) {
        if (entity == null) return null;
        return new double[]{
                getDouble(entity, 0, "motionX", "field_70159_w"),
                getDouble(entity, 0, "motionY", "field_70181_x"),
                getDouble(entity, 0, "motionZ", "field_70179_y")
        };
    }

    public static float eyeHeight(Object entity) {
        Object r = invoke(entity, "getEyeHeight");
        if (r == null) r = invoke(entity, "func_70047_e");
        return (r instanceof Float) ? (Float) r : 1.62f;
    }

    public static boolean entityBool(Object entity, String mcp, String srg, boolean fallback) {
        Object r = invoke(entity, mcp);
        if (r == null) r = invoke(entity, srg);
        return (r instanceof Boolean) ? (Boolean) r : fallback;
    }

    public static int entityId(Object entity) {
        Object r = invoke(entity, "getEntityId");
        if (r == null) r = invoke(entity, "func_145782_y");
        if (r instanceof Integer) return (Integer) r;
        return getInt(entity, -1, "entityId", "field_145783_c");
    }

    public static UUID entityUuid(Object entity) {
        Object r = invoke(entity, "getUniqueID");
        if (r == null) r = invoke(entity, "getUniqueId");
        if (r == null) r = invoke(entity, "func_110124_au");
        return (r instanceof UUID) ? (UUID) r : null;
    }

    public static String entityName(Object entity) {
        Object r = invoke(entity, "getName");
        if (r == null) r = invoke(entity, "getCommandSenderName");
        if (r == null) r = invoke(entity, "func_70005_c_");
        return (r instanceof String) ? (String) r : null;
    }

    // ------------------------------------------------------------------
    // World / entities (Batch 2 — included since cheap and high-reuse)
    // ------------------------------------------------------------------

    /** CME-safe immutable snapshot of the loaded entity list. */
    public static List<Object> loadedEntities(Object world) {
        if (world == null) return Collections.emptyList();
        Object list = invoke(world, "getLoadedEntityList");
        if (!(list instanceof List)) list = getField(world, "loadedEntityList", "field_72996_f");
        if (!(list instanceof List)) list = getField(world, "playerEntities", "field_73010_i");
        if (!(list instanceof List)) return Collections.emptyList();
        try {
            Object[] copy = ((List<?>) list).toArray();
            List<Object> out = new ArrayList<>(copy.length);
            for (Object o : copy) if (o != null) out.add(o);
            return Collections.unmodifiableList(out);
        } catch (Throwable t) {
            return Collections.emptyList();
        }
    }

    public static Object entityById(Object world, int id) {
        Object r = invoke(world, "getEntityByID", id);
        if (r == null) r = invoke(world, "func_73045_a", id);
        return r;
    }
}

