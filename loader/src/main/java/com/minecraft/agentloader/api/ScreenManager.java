package com.minecraft.agentloader.api;

/**
 * Internal interface for managing GUI screens.
 * Implemented by the mod loader - mods should not implement this.
 */
public interface ScreenManager {
    void closeScreen(GuiScreen screen);
    void drawRect(int left, int top, int right, int bottom, int color);
    void drawString(String text, float x, float y, int color);
    int getStringWidth(String text);
    int getScreenWidth();
    int getScreenHeight();
}

