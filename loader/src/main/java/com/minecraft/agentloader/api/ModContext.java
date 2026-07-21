package com.minecraft.agentloader.api;

import com.minecraft.agentloader.api.events.*;
import com.minecraft.agentloader.api.gui.MinecraftGui;
import java.io.File;

/**
 * Context provided to mods during initialization.
 * Provides access to the agent's API for registering commands,
 * event handlers, and accessing utilities.
 */
public interface ModContext {
    /**
     * Register a command with one or more aliases.
     *
     * @param command The command implementation
     * @param aliases Command names (e.g., "timer", "mcctimer")
     */
    void registerCommand(Command command, String... aliases);

    /**
     * Register a command using its built-in aliases.
     * Uses Command.getAliases() to determine command names.
     *
     * @param command The command implementation with getAliases() defined
     */
    default void registerCommand(Command command) {
        String[] aliases = command.getAliases();
        if (aliases.length > 0) {
            registerCommand(command, aliases);
        } else {
            throw new IllegalArgumentException("Command must have at least one alias via getAliases() or provide aliases manually");
        }
    }

    /**
     * Register a chat message event handler.
     * Handler will be called when chat messages are received.
     *
     * @param handler The chat event handler
     */
    void registerChatListener(ChatEventHandler handler);

    /**
     * Register a render overlay event handler.
     * Handler will be called each frame to render overlays.
     *
     * @param handler The render event handler
     */
    void registerRenderOverlay(RenderEventHandler handler);

    /**
     * Register a world change event handler.
     * Handler will be called when the world changes (disconnect, dimension change).
     *
     * @param handler The world event handler
     */
    void registerWorldListener(WorldEventHandler handler);

    /**
     * Register a packet event handler.
     * Handler will be called when packets are about to be sent.
     *
     * @param handler The packet event handler
     */
    void registerPacketListener(PacketEventHandler handler);

    /**
     * Register a packet receive event handler.
     * Handler will be called when packets are about to be received from the server.
     *
     * @param handler The packet receive event handler
     */
    void registerPacketReceiveListener(PacketReceiveEventHandler handler);

    /**
     * Register a 3D world render event handler.
     * Handler will be called each frame after entities are rendered, in 3D projection context.
     *
     * @param handler The render world event handler
     */
    void registerRenderWorldListener(RenderWorldEventHandler handler);

    /**
     * Get the Minecraft instance via reflection.
     * Works with both obfuscated and deobfuscated clients.
     *
     * @return The Minecraft instance object
     * @throws Exception If the instance cannot be obtained
     */
    Object getMinecraftInstance() throws Exception;

    /**
     * Send a message to the player's chat.
     *
     * @param message The message to send
     */
    void sendClientMessage(String message);

    /**
     * Get the data directory for this mod.
     * Located at ~/.minecraft/agentmods-data/{modId}/
     *
     * @return The mod's data directory
     */
    File getModDataDir();

    /**
     * Open a GUI screen.
     *
     * @param screen The GUI screen to open
     */
    void openScreen(GuiScreen screen);

    /**
     * Open a Minecraft GUI with native buttons and text fields.
     *
     * @param gui The Minecraft GUI to open
     */
    void openMinecraftGui(MinecraftGui gui);

    /**
     * Show a Lunar Client in-game toast notification with a title and message.
     * Falls back to a chat message if not running on Lunar Client.
     *
     * @param title   Bold header text of the notification
     * @param message Body text of the notification
     */
    void sendNotification(String title, String message);

    /**
     * Show a Lunar Client in-game toast notification with a message only.
     * Falls back to a chat message if not running on Lunar Client.
     *
     * @param message Body text of the notification
     */
    default void sendNotification(String message) {
        sendNotification("", message);
    }

    /**
     * Get the loader API version.
     * Abstract - old loader's ModContextImpl does not implement this, so calling it
     * on the old loader always throws AbstractMethodError regardless of mod edits.
     *
     * @return The loader API version
     */
    int getLoaderVersion();

    // ==================================================================
    // Convenience game-state accessors (all default methods — safe on any
    // loader: the body ships in the interface, so an older ModContextImpl
    // inherits it and no AbstractMethodError can occur). Each resolves the
    // game member reflectively via the cached GameAccess helper and returns
    // a null/fallback on miss rather than throwing into mod code.
    //
    // These replace the ~150 lines of hand-rolled reflection every mod used
    // to copy. Introduced in loader v30+.
    // ==================================================================

    // --- Player / local state ---

    /** @return the local player (EntityPlayerSP) object, or null. */
    default Object getThePlayer() {
        return com.minecraft.agentloader.GameAccess.thePlayer();
    }

    /** @return the client world object, or null. */
    default Object getWorld() {
        return com.minecraft.agentloader.GameAccess.theWorld();
    }

    /** @return the local player's {posX,posY,posZ}, or null if unavailable. */
    default double[] getPlayerPosition() {
        return com.minecraft.agentloader.GameAccess.entityPosition(getThePlayer());
    }

    /** @return any entity's {posX,posY,posZ}, or null. */
    default double[] getEntityPosition(Object entity) {
        return com.minecraft.agentloader.GameAccess.entityPosition(entity);
    }

    /** @return the local player's {yaw,pitch}, or null. */
    default float[] getPlayerRotation() {
        return com.minecraft.agentloader.GameAccess.entityRotation(getThePlayer());
    }

    /** @return any entity's {yaw,pitch}, or null. */
    default float[] getEntityRotation(Object entity) {
        return com.minecraft.agentloader.GameAccess.entityRotation(entity);
    }

    /** @return any entity's {motionX,motionY,motionZ}, or null. */
    default double[] getMotion(Object entity) {
        return com.minecraft.agentloader.GameAccess.entityMotion(entity);
    }

    /** @return the entity's eye height, or 1.62 if unavailable. */
    default float getEyeHeight(Object entity) {
        return com.minecraft.agentloader.GameAccess.eyeHeight(entity);
    }

    /** @return whether the entity is sprinting (false on miss). */
    default boolean isSprinting(Object entity) {
        return com.minecraft.agentloader.GameAccess.entityBool(entity, "isSprinting", "func_70051_ag", false);
    }

    /** @return whether the entity is sneaking (false on miss). */
    default boolean isSneaking(Object entity) {
        return com.minecraft.agentloader.GameAccess.entityBool(entity, "isSneaking", "func_70093_af", false);
    }

    /** @return the entity's network id, or -1. */
    default int getEntityId(Object entity) {
        return com.minecraft.agentloader.GameAccess.entityId(entity);
    }

    /** @return the entity's UUID, or null. */
    default java.util.UUID getEntityUuid(Object entity) {
        return com.minecraft.agentloader.GameAccess.entityUuid(entity);
    }

    /** @return the entity's name, or null. */
    default String getEntityName(Object entity) {
        return com.minecraft.agentloader.GameAccess.entityName(entity);
    }

    // --- World / entities ---

    /** @return an immutable, CME-safe snapshot of the loaded entities (never null). */
    default java.util.List<Object> getLoadedEntities() {
        return com.minecraft.agentloader.GameAccess.loadedEntities(getWorld());
    }

    /** @return the loaded entity with this id, or null. */
    default Object getEntityById(int id) {
        return com.minecraft.agentloader.GameAccess.entityById(getWorld(), id);
    }

    // --- Generic reflection utility (Batch 6): collapses the per-mod
    //     getField/getDouble/.../invoke bridges. Names are tried in order
    //     (MCP first, obf fallback), walking the superclass chain, cached. ---

    /** Read a field by trying each candidate name; null on miss. */
    default Object reflectField(Object target, String... nameCandidates) {
        return com.minecraft.agentloader.GameAccess.getField(target, nameCandidates);
    }

    default double reflectDouble(Object target, double fallback, String... names) {
        return com.minecraft.agentloader.GameAccess.getDouble(target, fallback, names);
    }

    default float reflectFloat(Object target, float fallback, String... names) {
        return com.minecraft.agentloader.GameAccess.getFloat(target, fallback, names);
    }

    default int reflectInt(Object target, int fallback, String... names) {
        return com.minecraft.agentloader.GameAccess.getInt(target, fallback, names);
    }

    default boolean reflectBoolean(Object target, boolean fallback, String... names) {
        return com.minecraft.agentloader.GameAccess.getBoolean(target, fallback, names);
    }

    /** Invoke a no-/single-/multi-arg method by name; null on miss. */
    default Object reflectInvoke(Object target, String methodName, Object... args) {
        return com.minecraft.agentloader.GameAccess.invoke(target, methodName, args);
    }

    // ==================================================================
    // Runtime class transformation (block-modification) API.
    // These need loader-private Instrumentation, so they are abstract and
    // implemented in ModContextImpl. Calling them on a loader older than the
    // one that introduced them throws AbstractMethodError — gate with
    // getLoaderVersion(). Introduced in loader v30+.
    // ==================================================================

    /**
     * Resolve a target class by any of the candidate names (obf first, MCP fallback) and
     * retransform it through {@code transformer}. The loader handles the one-shot
     * ClassFileTransformer, retransformClasses, error isolation, and removal.
     *
     * @return true if a target was found and retransformed.
     */
    boolean transformClass(String[] classNameCandidates,
                           com.minecraft.agentloader.api.transform.ClassTransformer transformer);

    /**
     * Make {@code helperClass} reachable from bytecode injected into Minecraft classes by
     * staging it into the bootstrap (fallback system) classloader. Needed when injected code
     * calls back into a mod-provided helper.
     */
    void exposeHelperClass(Class<?> helperClass);

    /** Force a world re-render after a block-appearance change. */
    void rebuildChunks();
}

