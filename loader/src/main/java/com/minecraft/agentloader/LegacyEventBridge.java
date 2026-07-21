package com.minecraft.agentloader;

import com.minecraft.agentloader.api.gui.MinecraftGuiFactory;
import net.weavemc.api.event.EventBus;
import net.weavemc.api.lunar.LunarClient;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Translates versioned Weave events into the abandoned AgentMod event API.
 * Event types are resolved once by name so the core loader does not link against
 * Minecraft-version-specific API classes.
 */
final class LegacyEventBridge {
    private final Map<String, Method> methodCache = new ConcurrentHashMap<>();
    private final AtomicBoolean startupNotificationSent = new AtomicBoolean();

    @SuppressWarnings({"rawtypes", "unchecked"})
    void subscribe() throws ClassNotFoundException {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) loader = LegacyEventBridge.class.getClassLoader();

        subscribe(loader, "net.weavemc.api.event.ChatEvent$Received", this::onChatReceived);
        subscribe(loader, "net.weavemc.api.event.ChatEvent$Sent", this::onChatSent);
        subscribe(loader, "net.weavemc.api.event.RenderGameOverlayEvent$Post", this::onOverlay);
        subscribe(loader, "net.weavemc.api.event.WorldEvent$Load", this::onWorldLoad);
        subscribe(loader, "net.weavemc.api.event.WorldEvent$Unload", this::onWorldUnload);
        subscribe(loader, "net.weavemc.api.event.PacketEvent$Send", this::onPacketSend);
        subscribe(loader, "net.weavemc.api.event.PacketEvent$Receive", this::onPacketReceive);
        subscribe(loader, "net.weavemc.api.event.RenderWorldEvent", this::onRenderWorld);
        subscribe(loader, "net.weavemc.api.event.ShutdownEvent", this::onShutdown);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void subscribe(ClassLoader loader, String className, Consumer<Object> handler)
            throws ClassNotFoundException {
        Class eventClass = Class.forName(className, false, loader);
        EventBus.subscribe(eventClass, (Consumer) handler);
    }

    private void onChatReceived(Object event) {
        Object component = invoke(event, "getMessage");
        if (component == null) return;
        Object formatted = invoke(component, "getFormattedText");
        if (formatted == null) formatted = invoke(component, "d");
        EventDispatcher.dispatchChatMessage(component, formatted == null ? component.toString() : formatted.toString());
    }

    private void onChatSent(Object event) {
        Object value = invoke(event, "getMessage");
        if (value != null && CommandHandler.handleCommand(value.toString())) cancel(event);
    }

    private void onOverlay(Object event) {
        EventDispatcher.dispatchRenderOverlay(floatValue(invoke(event, "getPartialTicks")));
        MinecraftGuiFactory.renderActiveGuiTick();
    }

    private void onWorldLoad(Object event) {
        EventDispatcher.dispatchWorldChange();
        if (startupNotificationSent.compareAndSet(false, true)) sendStartupNotification();
    }

    private void onWorldUnload(Object event) {
        EventDispatcher.dispatchWorldChange();
    }

    private void onPacketSend(Object event) {
        if (EventDispatcher.dispatchPacketSend(invoke(event, "getPacket"))) cancel(event);
    }

    private void onPacketReceive(Object event) {
        if (EventDispatcher.dispatchPacketReceive(invoke(event, "getPacket"))) cancel(event);
    }

    private void onRenderWorld(Object event) {
        EventDispatcher.dispatchRenderWorld(floatValue(invoke(event, "getPartialTicks")));
    }

    private void onShutdown(Object event) {
        ModLoader.shutdownMods();
    }

    private void cancel(Object event) {
        invoke(event, "setCancelled", boolean.class, true);
    }

    private Object invoke(Object target, String name) {
        if (target == null) return null;
        try {
            String key = target.getClass().getName() + '#' + name + "()";
            Method method = methodCache.computeIfAbsent(key, ignored -> findMethod(target.getClass(), name));
            return method == null ? null : method.invoke(target);
        } catch (Throwable error) {
            Log.debug("Legacy event bridge could not invoke " + name + ": " + error.getMessage());
            return null;
        }
    }

    private Object invoke(Object target, String name, Class<?> parameterType, Object value) {
        if (target == null) return null;
        try {
            String key = target.getClass().getName() + '#' + name + '(' + parameterType.getName() + ')';
            Method method = methodCache.computeIfAbsent(
                    key, ignored -> findMethod(target.getClass(), name, parameterType));
            return method == null ? null : method.invoke(target, value);
        } catch (Throwable error) {
            Log.debug("Legacy event bridge could not invoke " + name + ": " + error.getMessage());
            return null;
        }
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        try {
            Method method = type.getMethod(name, parameterTypes);
            method.setAccessible(true);
            return method;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static float floatValue(Object value) {
        return value instanceof Number ? ((Number) value).floatValue() : 0.0F;
    }

    private void sendStartupNotification() {
        final int count = ModLoader.getLoadedMods().size();
        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(1000L);
                LunarClient.sendNotification(
                        "Loaded Successfully",
                        "Loaded " + count + " legacy mod" + (count == 1 ? "" : "s") + " successfully!"
                );
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }, "Weave-Legacy-Notification");
        thread.setDaemon(true);
        thread.start();
    }
}
