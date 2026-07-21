package com.minecraft.agentloader.api.gui;

import com.minecraft.agentloader.Log;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for creating Minecraft GUI screens with actual GuiButton, GuiTextField, etc.
 * Provides easy access to Minecraft's native GUI components.
 */
public abstract class MinecraftGui {

    protected Object minecraftGuiScreen; // The actual net.minecraft.client.gui.GuiScreen instance
    protected ClassLoader gameLoader;
    protected int width;
    protected int height;

    // Reflection cache
    private static Class<?> guiScreenClass;
    private static Class<?> guiButtonClass;
    private static Class<?> guiTextFieldClass;
    private static Method drawScreenMethod;
    private static Method initGuiMethod;
    private static Method actionPerformedMethod;

    // Button/component storage
    protected List<Object> buttonList = new ArrayList<>();

    /**
     * Called when GUI is initialized. Add your buttons here.
     */
    public abstract void initGui();

    /**
     * Called every frame to render the GUI.
     * @param mouseX Mouse X position
     * @param mouseY Mouse Y position
     * @param partialTicks Partial tick time
     */
    public abstract void drawScreen(int mouseX, int mouseY, float partialTicks);

    /**
     * Called when a button is clicked.
     * @param button The button that was clicked
     */
    protected void actionPerformed(GuiButton button) {}

    /**
     * Called when a key is typed.
     * @param character The character typed
     * @param keyCode The key code
     */
    protected void keyTyped(char character, int keyCode) {}

    /**
     * Called when mouse is clicked.
     * @param mouseX Mouse X position
     * @param mouseY Mouse Y position
     * @param button Mouse button (0=left, 1=right, 2=middle)
     */
    protected void mouseClicked(int mouseX, int mouseY, int button) {}

    /**
     * Called when a mouse button is released.
     * @param mouseX Mouse X position
     * @param mouseY Mouse Y position
     * @param button Mouse button (0=left, 1=right, 2=middle)
     */
    protected void mouseReleased(int mouseX, int mouseY, int button) {}

    /**
     * Called when the mouse wheel is scrolled.
     * @param delta Positive = scroll up, Negative = scroll down
     */
    protected void mouseScrolled(int delta) {}

    /**
     * Called when the GUI is closed for any reason (ESC, screen change, death, etc.).
     */
    protected void onGuiClosed() {}

    /**
     * Add a button to the GUI.
     * @param id Button ID
     * @param x X position
     * @param y Y position
     * @param width Button width
     * @param height Button height
     * @param text Button text
     * @return The created button
     */
    protected GuiButton addButton(int id, int x, int y, int width, int height, String text) {
        try {
            if (guiButtonClass == null) {
                initReflection();
            }

            // Create GuiButton instance
            Constructor<?> ctor = guiButtonClass.getConstructor(
                int.class, int.class, int.class, int.class, int.class, String.class
            );
            Object buttonObj = ctor.newInstance(id, x, y, width, height, text);

            buttonList.add(buttonObj);

            GuiButton wrapper = new GuiButton(buttonObj, id, text);
            return wrapper;

        } catch (Exception e) {
            Log.error("Failed to create legacy GUI button: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Add a button with default size (200x20).
     */
    protected GuiButton addButton(int id, int x, int y, String text) {
        return addButton(id, x, y, 200, 20, text);
    }

    /**
     * Create a text field.
     * @param id Field ID
     * @param x X position
     * @param y Y position
     * @param width Field width
     * @param height Field height
     * @return The created text field
     */
    protected GuiTextField createTextField(int id, int x, int y, int width, int height) {
        try {
            if (guiTextFieldClass == null) {
                initReflection();
            }

            // Get FontRenderer from Minecraft
            Object mc = getMinecraftInstance();
            Object fontRenderer = mc.getClass().getField("fontRendererObj").get(mc);

            // Create GuiTextField
            Constructor<?> ctor = guiTextFieldClass.getConstructor(
                int.class, fontRenderer.getClass(), int.class, int.class, int.class, int.class
            );
            Object textFieldObj = ctor.newInstance(id, fontRenderer, x, y, width, height);

            return new GuiTextField(textFieldObj);

        } catch (Exception e) {
            Log.error("Failed to create legacy GUI text field: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Draw default background (dirt pattern).
     */
    protected void drawDefaultBackground() {
        try {
            if (minecraftGuiScreen != null) {
                Method method = guiScreenClass.getMethod("drawDefaultBackground");
                method.invoke(minecraftGuiScreen);
            }
        } catch (Exception e) {
            // Fallback: draw dark overlay
            drawRect(0, 0, width, height, 0xCC000000);
        }
    }

    /**
     * Draw centered string.
     */
    protected void drawCenteredString(String text, int x, int y, int color) {
        try {
            if (minecraftGuiScreen != null) {
                Object mc = getMinecraftInstance();
                Object fontRenderer = mc.getClass().getField("fontRendererObj").get(mc);

                Method method = guiScreenClass.getMethod("drawCenteredString",
                    fontRenderer.getClass(), String.class, int.class, int.class, int.class);
                method.invoke(minecraftGuiScreen, fontRenderer, text, x, y, color);
            }
        } catch (Exception e) {
            drawString(text, x, y, color);
        }
    }

    /**
     * Draw string.
     */
    protected void drawString(String text, int x, int y, int color) {
        try {
            if (minecraftGuiScreen != null) {
                Object mc = getMinecraftInstance();
                Object fontRenderer = mc.getClass().getField("fontRendererObj").get(mc);

                Method method = fontRenderer.getClass().getMethod("drawStringWithShadow",
                    String.class, float.class, float.class, int.class);
                method.invoke(fontRenderer, text, (float) x, (float) y, color);
            }
        } catch (Exception e) {
            Log.debug("Legacy GUI failed to draw string: " + e.getMessage());
        }
    }

    /**
     * Draw rectangle.
     */
    protected void drawRect(int left, int top, int right, int bottom, int color) {
        try {
            Class<?> guiClass = Class.forName("net.minecraft.client.gui.Gui", true, gameLoader);
            Method method = guiClass.getDeclaredMethod("drawRect",
                int.class, int.class, int.class, int.class, int.class);
            method.setAccessible(true);
            method.invoke(null, left, top, right, bottom, color);
        } catch (Exception e) {
            try {
                Class<?> guiClass = Class.forName("avv", true, gameLoader);
                Method method = guiClass.getDeclaredMethod("a",
                    int.class, int.class, int.class, int.class, int.class);
                method.setAccessible(true);
                method.invoke(null, left, top, right, bottom, color);
            } catch (Exception e2) {
                Log.debug("Legacy GUI failed to draw rect: " + e2.getMessage());
            }
        }
    }

    /**
     * Close this GUI and return to previous screen.
     */
    protected void close() {
        try {
            Object mc = getMinecraftInstance();
            Method method = mc.getClass().getMethod("displayGuiScreen",
                Class.forName("net.minecraft.client.gui.GuiScreen", true, gameLoader));
            method.invoke(mc, (Object) null);
        } catch (Exception e) {
            try {
                Object mc = getMinecraftInstance();
                Method method = mc.getClass().getMethod("a",
                    Class.forName("axu", true, gameLoader));
                method.invoke(mc, (Object) null);
            } catch (Exception e2) {
                Log.debug("Legacy GUI failed to close: " + e2.getMessage());
            }
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

    private void initReflection() throws Exception {
        gameLoader = Thread.currentThread().getContextClassLoader();

        try {
            guiScreenClass = Class.forName("net.minecraft.client.gui.GuiScreen", true, gameLoader);
            guiButtonClass = Class.forName("net.minecraft.client.gui.GuiButton", true, gameLoader);
            guiTextFieldClass = Class.forName("net.minecraft.client.gui.GuiTextField", true, gameLoader);
        } catch (Exception e) {
            guiScreenClass = Class.forName("axu", true, gameLoader);
            guiButtonClass = Class.forName("axw", true, gameLoader);
            guiTextFieldClass = Class.forName("axz", true, gameLoader);
        }
    }

    // Internal methods called by generated bytecode
    void _setScreen(Object screen) {
        this.minecraftGuiScreen = screen;
        this.gameLoader = Thread.currentThread().getContextClassLoader();
    }

    void _setSize(int width, int height) {
        this.width = width;
        this.height = height;
    }
}
