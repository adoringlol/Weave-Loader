package com.minecraft.agentloader.api.gui;

import com.minecraft.agentloader.Log;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Wrapper for Minecraft's GuiButton class.
 */
public class GuiButton {
    private final Object buttonObject;
    private final int id;
    private String displayString;

    // Cached reflection
    private static Field displayStringField;
    private static Field enabledField;
    private static Field visibleField;

    public GuiButton(Object buttonObject, int id, String displayString) {
        this.buttonObject = buttonObject;
        this.id = id;
        this.displayString = displayString;
    }

    public int getId() {
        return id;
    }

    public String getDisplayString() {
        return displayString;
    }

    public void setDisplayString(String text) {
        this.displayString = text;
        try {
            if (displayStringField == null) {
                try {
                    displayStringField = buttonObject.getClass().getField("displayString");
                } catch (Exception e) {
                    displayStringField = buttonObject.getClass().getField("j");
                }
                displayStringField.setAccessible(true);
            }
            displayStringField.set(buttonObject, text);
        } catch (Exception e) {
            Log.debug("GuiButton failed to set display string: " + e.getMessage());
        }
    }

    public void setEnabled(boolean enabled) {
        try {
            if (enabledField == null) {
                try {
                    enabledField = buttonObject.getClass().getField("enabled");
                } catch (Exception e) {
                    enabledField = buttonObject.getClass().getField("n");
                }
                enabledField.setAccessible(true);
            }
            enabledField.set(buttonObject, enabled);
        } catch (Exception e) {
            Log.debug("GuiButton failed to set enabled: " + e.getMessage());
        }
    }

    public void setVisible(boolean visible) {
        try {
            if (visibleField == null) {
                try {
                    visibleField = buttonObject.getClass().getField("visible");
                } catch (Exception e) {
                    visibleField = buttonObject.getClass().getField("o");
                }
                visibleField.setAccessible(true);
            }
            visibleField.set(buttonObject, visible);
        } catch (Exception e) {
            Log.debug("GuiButton failed to set visible: " + e.getMessage());
        }
    }

    public Object getButtonObject() {
        return buttonObject;
    }
}
