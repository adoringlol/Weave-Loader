package com.minecraft.agentloader;

import com.minecraft.agentloader.api.GuiScreen;
import com.minecraft.agentloader.api.ScreenManager;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Implementation of ScreenManager that provides GUI utilities to GuiScreen instances.
 */
class ScreenManagerImpl implements ScreenManager {
    private final ClassLoader gameLoader;
    private final Object minecraftGuiScreen;

    // Cached reflection methods
    private static Method drawRectMethod;
    private static Method drawStringMethod;
    private static Method getStringWidthMethod;
    private static Object fontRendererInstance;
    private static Method displayGuiScreenMethod;

    private static Class<?> minecraftClass;
    private static Class<?> scaledResolutionClass;
    private static Method getScaledWidthMethod;
    private static Method getScaledHeightMethod;
    private static volatile boolean reflectionInitialized;

    public ScreenManagerImpl(ClassLoader gameLoader, Object minecraftGuiScreen) {
        this.gameLoader = gameLoader;
        this.minecraftGuiScreen = minecraftGuiScreen;
        initReflection();
    }

    private synchronized void initReflection() {
        if (reflectionInitialized) {
            ensureFontRenderer();
            return;
        }

        try {
            // Get Minecraft instance
            Object mc = getMinecraftInstance();

            // Get GuiScreen class
            Class<?> guiScreenClass;
            try {
                guiScreenClass = Class.forName("net.minecraft.client.gui.GuiScreen", true, gameLoader);
            } catch (Exception e) {
                guiScreenClass = Class.forName("axu", true, gameLoader);
            }

            // Get Gui class (parent of GuiScreen)
            Class<?> guiClass;
            try {
                guiClass = Class.forName("net.minecraft.client.gui.Gui", true, gameLoader);
            } catch (Exception e) {
                guiClass = Class.forName("avv", true, gameLoader);
            }

            // Get drawRect method
            try {
                drawRectMethod = guiClass.getDeclaredMethod("drawRect", int.class, int.class, int.class, int.class, int.class);
            } catch (Exception e) {
                drawRectMethod = guiClass.getDeclaredMethod("a", int.class, int.class, int.class, int.class, int.class);
            }
            drawRectMethod.setAccessible(true);

            // Get Minecraft class and ScaledResolution
            try {
                minecraftClass = Class.forName("net.minecraft.client.Minecraft", true, gameLoader);
            } catch (Exception e) {
                minecraftClass = Class.forName("ave", true, gameLoader);
            }

            try {
                scaledResolutionClass = Class.forName("net.minecraft.client.gui.ScaledResolution", true, gameLoader);
            } catch (Exception e) {
                scaledResolutionClass = Class.forName("avo", true, gameLoader);
            }

            try {
                getScaledWidthMethod = scaledResolutionClass.getMethod("getScaledWidth");
                getScaledHeightMethod = scaledResolutionClass.getMethod("getScaledHeight");
            } catch (Exception e) {
                getScaledWidthMethod = scaledResolutionClass.getMethod("a");
                getScaledHeightMethod = scaledResolutionClass.getMethod("b");
            }

            // Get displayGuiScreen method
            try {
                displayGuiScreenMethod = minecraftClass.getMethod("displayGuiScreen", guiScreenClass);
            } catch (Exception e) {
                displayGuiScreenMethod = minecraftClass.getMethod("a", guiScreenClass);
            }

            // Generated GuiScreens are linked before Minecraft calls
            // setWorldAndResolution(), so their inherited fontRendererObj is
            // still null here. Resolve Minecraft.fontRendererObj instead and
            // retry lazily if the client has not populated it yet.
            ensureFontRenderer();
            reflectionInitialized = true;

        } catch (Exception e) {
            Log.error("Failed to initialize legacy ScreenManager reflection: " + e.getMessage(), e);
        }
    }

    private Object getMinecraftInstance() throws Exception {
        try {
            Class<?> mcClass = Class.forName("net.minecraft.client.Minecraft", true, gameLoader);
            return mcClass.getMethod("getMinecraft").invoke(null);
        } catch (Exception e) {
            Class<?> mcClass = Class.forName("ave", true, gameLoader);
            return mcClass.getMethod("A").invoke(null);
        }
    }

    @Override
    public void closeScreen(GuiScreen screen) {
        try {
            initReflection();
            Object mc = getMinecraftInstance();
            if (displayGuiScreenMethod != null) {
                displayGuiScreenMethod.invoke(mc, (Object) null);
            }
        } catch (Exception e) {
            Log.debug("Failed to close legacy screen: " + e.getMessage());
        }
    }

    @Override
    public void drawRect(int left, int top, int right, int bottom, int color) {
        try {
            initReflection();
            if (drawRectMethod != null) {
                drawRectMethod.invoke(null, left, top, right, bottom, color);
            }
        } catch (Exception e) {
            Log.debug("Failed to draw legacy rect: " + e.getMessage());
        }
    }

    @Override
    public void drawString(String text, float x, float y, int color) {
        try {
            if (ensureFontRenderer()) {
                drawStringMethod.invoke(fontRendererInstance, text, x, y, color);
            }
        } catch (Exception e) {
            Log.debug("Failed to draw legacy string: " + e.getMessage());
        }
    }

    @Override
    public int getStringWidth(String text) {
        try {
            if (ensureFontRenderer()) {
                return (Integer) getStringWidthMethod.invoke(fontRendererInstance, text);
            }
        } catch (Exception e) {
            Log.debug("Failed to get legacy string width: " + e.getMessage());
        }
        return text.length() * 6;
    }

    @Override
    public int getScreenWidth() {
        try {
            initReflection();
            Object mc = getMinecraftInstance();
            Object scaledRes = scaledResolutionClass.getConstructor(minecraftClass).newInstance(mc);
            return (Integer) getScaledWidthMethod.invoke(scaledRes);
        } catch (Exception e) {
            Log.debug("Failed to get legacy screen width: " + e.getMessage());
            return 0;
        }
    }

    @Override
    public int getScreenHeight() {
        try {
            initReflection();
            Object mc = getMinecraftInstance();
            Object scaledRes = scaledResolutionClass.getConstructor(minecraftClass).newInstance(mc);
            return (Integer) getScaledHeightMethod.invoke(scaledRes);
        } catch (Exception e) {
            Log.debug("Failed to get legacy screen height: " + e.getMessage());
            return 0;
        }
    }

    private synchronized boolean ensureFontRenderer() {
        if (fontRendererInstance != null && drawStringMethod != null
                && getStringWidthMethod != null) {
            return true;
        }

        try {
            Object mc = getMinecraftInstance();
            Object renderer = readField(mc, "fontRendererObj", "fontRenderer", "k");
            if (renderer == null && minecraftGuiScreen != null) {
                renderer = readField(minecraftGuiScreen, "fontRendererObj", "m");
            }
            if (renderer == null) {
                return false;
            }

            Class<?> fontRendererClass = renderer.getClass();
            try {
                drawStringMethod = fontRendererClass.getMethod(
                        "drawStringWithShadow", String.class, float.class, float.class, int.class);
            } catch (Exception e) {
                drawStringMethod = fontRendererClass.getMethod(
                        "a", String.class, float.class, float.class, int.class);
            }
            try {
                getStringWidthMethod = fontRendererClass.getMethod("getStringWidth", String.class);
            } catch (Exception e) {
                getStringWidthMethod = fontRendererClass.getMethod("a", String.class);
            }
            fontRendererInstance = renderer;
            return true;
        } catch (Exception error) {
            Log.debug("Legacy FontRenderer is not ready: " + error.getMessage());
            return false;
        }
    }

    private static Object readField(Object target, String... names) {
        if (target == null) {
            return null;
        }
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            for (String name : names) {
                try {
                    Field field = type.getDeclaredField(name);
                    field.setAccessible(true);
                    Object value = field.get(target);
                    if (value != null) {
                        return value;
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }
}
