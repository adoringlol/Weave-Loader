package com.minecraft.agentloader.api;

/**
 * Simplified GUI screen that mods can extend.
 * Automatically handles screen creation, rendering, and input.
 */
public abstract class GuiScreen {

    /**
     * Called when the screen is opened.
     */
    public void onOpen() {}

    /**
     * Called when the screen is closed.
     */
    public void onClose() {}

    /**
     * Called every frame to render the screen.
     *
     * @param mouseX Mouse X position (scaled)
     * @param mouseY Mouse Y position (scaled)
     * @param partialTicks Partial tick time
     */
    public abstract void render(int mouseX, int mouseY, float partialTicks);

    /**
     * Called when a key is pressed.
     *
     * @param character The character typed
     * @param keyCode The key code
     * @return true to consume the key event
     */
    public boolean onKeyPressed(char character, int keyCode) {
        // ESC key (keyCode 1) closes the screen by default
        if (keyCode == 1) {
            close();
            return true;
        }
        return false;
    }

    /**
     * Called when the mouse is clicked.
     *
     * @param mouseX Mouse X position (scaled)
     * @param mouseY Mouse Y position (scaled)
     * @param button Mouse button (0=left, 1=right, 2=middle)
     * @return true to consume the click event
     */
    public boolean onMouseClick(int mouseX, int mouseY, int button) {
        return false;
    }

    /**
     * Called when the mouse wheel is scrolled.
     *
     * @param mouseX Mouse X position (scaled)
     * @param mouseY Mouse Y position (scaled)
     * @param delta Scroll delta (positive=up, negative=down)
     * @return true to consume the scroll event
     */
    public boolean onMouseScroll(int mouseX, int mouseY, int delta) {
        return false;
    }

    /**
     * Whether this GUI should pause the game.
     * Default: true
     */
    public boolean shouldPauseGame() {
        return true;
    }

    /**
     * Close this screen.
     */
    public void close() {
        if (screenManager != null) {
            screenManager.closeScreen(this);
        }
    }

    /**
     * Draw a rectangle on screen.
     */
    protected void drawRect(int left, int top, int right, int bottom, int color) {
        if (screenManager != null) {
            screenManager.drawRect(left, top, right, bottom, color);
        }
    }

    /**
     * Draw a string with shadow.
     */
    protected void drawString(String text, float x, float y, int color) {
        if (screenManager != null) {
            screenManager.drawString(text, x, y, color);
        }
    }

    /**
     * Get the width of a string.
     */
    protected int getStringWidth(String text) {
        if (screenManager != null) {
            return screenManager.getStringWidth(text);
        }
        return text.length() * 6; // Fallback estimate
    }

    /**
     * Get the screen width (scaled).
     */
    protected int getScreenWidth() {
        if (screenManager != null) {
            return screenManager.getScreenWidth();
        }
        return 0;
    }

    /**
     * Get the screen height (scaled).
     */
    protected int getScreenHeight() {
        if (screenManager != null) {
            return screenManager.getScreenHeight();
        }
        return 0;
    }

    // Internal - set by ScreenManager
    ScreenManager screenManager;
}

