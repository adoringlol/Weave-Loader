package com.minecraft.agentloader.api.gui;

import com.minecraft.agentloader.Log;
import java.lang.reflect.Method;

/**
 * Wrapper for Minecraft's GuiTextField class.
 */
public class GuiTextField {
    private final Object textFieldObject;

    // Cached reflection
    private static Method getTextMethod;
    private static Method setTextMethod;
    private static Method drawTextBoxMethod;
    private static Method setFocusedMethod;
    private static Method isFocusedMethod;
    private static Method setMaxStringLengthMethod;
    private static Method textboxKeyTypedMethod;
    private static Method mouseClickedMethod;
    private static Method updateCursorCounterMethod;

    public GuiTextField(Object textFieldObject) {
        this.textFieldObject = textFieldObject;
        initReflection();
    }

    public String getText() {
        try {
            return (String) getTextMethod.invoke(textFieldObject);
        } catch (Exception e) {
            Log.debug("GuiTextField failed to get text: " + e.getMessage());
            return "";
        }
    }

    public void setText(String text) {
        try {
            setTextMethod.invoke(textFieldObject, text);
        } catch (Exception e) {
            Log.debug("GuiTextField failed to set text: " + e.getMessage());
        }
    }

    public void setFocused(boolean focused) {
        try {
            setFocusedMethod.invoke(textFieldObject, focused);
        } catch (Exception e) {
            Log.debug("GuiTextField failed to set focused: " + e.getMessage());
        }
    }

    public boolean isFocused() {
        try {
            return (Boolean) isFocusedMethod.invoke(textFieldObject);
        } catch (Exception e) {
            Log.debug("GuiTextField failed to check focused: " + e.getMessage());
            return false;
        }
    }

    public void setMaxStringLength(int length) {
        try {
            setMaxStringLengthMethod.invoke(textFieldObject, length);
        } catch (Exception e) {
            Log.debug("GuiTextField failed to set max length: " + e.getMessage());
        }
    }

    /**
     * Draw the text field. Call this in your drawScreen method.
     */
    public void drawTextBox() {
        try {
            drawTextBoxMethod.invoke(textFieldObject);
        } catch (Exception e) {
            Log.debug("GuiTextField failed to draw: " + e.getMessage());
        }
    }

    /**
     * Handle key input. Call this in your keyTyped method.
     */
    public boolean textboxKeyTyped(char character, int keyCode) {
        try {
            return (Boolean) textboxKeyTypedMethod.invoke(textFieldObject, character, keyCode);
        } catch (Exception e) {
            Log.debug("GuiTextField failed to handle key: " + e.getMessage());
            return false;
        }
    }

    /**
     * Handle mouse click. Call this in your mouseClicked method.
     */
    public void mouseClicked(int mouseX, int mouseY, int button) {
        try {
            mouseClickedMethod.invoke(textFieldObject, mouseX, mouseY, button);
        } catch (Exception e) {
            Log.debug("GuiTextField failed to handle click: " + e.getMessage());
        }
    }

    /**
     * Update cursor animation. Call this in your updateScreen method.
     */
    public void updateCursorCounter() {
        try {
            updateCursorCounterMethod.invoke(textFieldObject);
        } catch (Exception e) {
            Log.debug("GuiTextField failed to update cursor: " + e.getMessage());
        }
    }

    public Object getTextFieldObject() {
        return textFieldObject;
    }

    private void initReflection() {
        if (getTextMethod != null) return;

        try {
            Class<?> textFieldClass = textFieldObject.getClass();

            try {
                getTextMethod = textFieldClass.getMethod("getText");
                setTextMethod = textFieldClass.getMethod("setText", String.class);
                drawTextBoxMethod = textFieldClass.getMethod("drawTextBox");
                setFocusedMethod = textFieldClass.getMethod("setFocused", boolean.class);
                isFocusedMethod = textFieldClass.getMethod("isFocused");
                setMaxStringLengthMethod = textFieldClass.getMethod("setMaxStringLength", int.class);
                textboxKeyTypedMethod = textFieldClass.getMethod("textboxKeyTyped", char.class, int.class);
                mouseClickedMethod = textFieldClass.getMethod("mouseClicked", int.class, int.class, int.class);
                updateCursorCounterMethod = textFieldClass.getMethod("updateCursorCounter");
            } catch (Exception e) {
                // Obfuscated names
                getTextMethod = textFieldClass.getMethod("b");
                setTextMethod = textFieldClass.getMethod("a", String.class);
                drawTextBoxMethod = textFieldClass.getMethod("g");
                setFocusedMethod = textFieldClass.getMethod("b", boolean.class);
                isFocusedMethod = textFieldClass.getMethod("m");
                setMaxStringLengthMethod = textFieldClass.getMethod("a", int.class);
                textboxKeyTypedMethod = textFieldClass.getMethod("a", char.class, int.class);
                mouseClickedMethod = textFieldClass.getMethod("a", int.class, int.class, int.class);
                updateCursorCounterMethod = textFieldClass.getMethod("e");
            }
        } catch (Exception e) {
            Log.error("GuiTextField failed to initialize reflection: " + e.getMessage(), e);
        }
    }
}
