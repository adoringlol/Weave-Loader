package com.minecraft.agentloader;

import com.minecraft.agentloader.api.GuiScreen;
import org.objectweb.asm.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory that generates Minecraft GuiScreen proxies that delegate to API GuiScreen instances.
 */
public final class GuiScreenFactory {
    private static Class<?> generatedScreenClass;
    private static Method displayGuiScreenMethod;
    private static Field screenManagerField;

    // Maps generated screen instances to their API delegates
    private static final Map<Object, GuiScreen> screenMap = new ConcurrentHashMap<>();

    /**
     * Open a GuiScreen by creating a Minecraft proxy and displaying it.
     */
    public static void openScreen(GuiScreen apiScreen) {
        try {
            ClassLoader gameLoader = Thread.currentThread().getContextClassLoader();
            if (gameLoader == null) return;

            Object mc = getMinecraftInstance(gameLoader);
            if (mc == null) return;

            if (generatedScreenClass == null) {
                generatedScreenClass = generateScreenClass(gameLoader);
            }

            if (generatedScreenClass == null) return;

            Object screenInstance = generatedScreenClass.newInstance();

            // Link the generated screen to the API screen
            screenMap.put(screenInstance, apiScreen);

            // Set up ScreenManager for the API screen
            if (screenManagerField == null) {
                screenManagerField = GuiScreen.class.getDeclaredField("screenManager");
                screenManagerField.setAccessible(true);
            }
            screenManagerField.set(apiScreen, new ScreenManagerImpl(gameLoader, screenInstance));

            // Call onOpen
            apiScreen.onOpen();

            if (displayGuiScreenMethod == null) {
                Class<?> mcClass = mc.getClass();
                try {
                    displayGuiScreenMethod = mcClass.getMethod("displayGuiScreen",
                        Class.forName("net.minecraft.client.gui.GuiScreen", true, gameLoader));
                } catch (Exception e) {
                    displayGuiScreenMethod = mcClass.getMethod("a",
                        Class.forName("axu", true, gameLoader));
                }
            }

            displayGuiScreenMethod.invoke(mc, screenInstance);

        } catch (Exception e) {
            Log.error("Failed to open legacy screen: " + e.getMessage(), e);
        }
    }

    /**
     * Get the API GuiScreen delegate for a generated screen instance.
     */
    public static GuiScreen getDelegate(Object screenInstance) {
        return screenMap.get(screenInstance);
    }

    /**
     * Remove a screen from the mapping (called when closed).
     */
    public static void removeScreen(Object screenInstance) {
        GuiScreen apiScreen = screenMap.remove(screenInstance);
        if (apiScreen != null) {
            apiScreen.onClose();
        }
    }

    private static Object getMinecraftInstance(ClassLoader gameLoader) throws Exception {
        try {
            Class<?> mcClass = Class.forName("net.minecraft.client.Minecraft", true, gameLoader);
            return mcClass.getMethod("getMinecraft").invoke(null);
        } catch (Exception e) {
            Class<?> mcClass = Class.forName("ave", true, gameLoader);
            return mcClass.getMethod("A").invoke(null);
        }
    }

    private static Class<?> generateScreenClass(ClassLoader gameLoader) {
        try {
            String guiScreenName;
            try {
                Class.forName("net.minecraft.client.gui.GuiScreen", true, gameLoader);
                guiScreenName = "net/minecraft/client/gui/GuiScreen";
            } catch (Exception e) {
                guiScreenName = "axu";
            }

            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
            String className = "com/minecraft/agentloader/GeneratedGuiScreen";

            cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, className, null, guiScreenName, null);

            // Constructor
            MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
            mv.visitCode();
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, guiScreenName, "<init>", "()V", false);
            mv.visitInsn(Opcodes.RETURN);
            mv.visitMaxs(1, 1);
            mv.visitEnd();

            // Override drawScreen
            String drawScreenName = guiScreenName.equals("axu") ? "a" : "drawScreen";
            mv = cw.visitMethod(Opcodes.ACC_PUBLIC, drawScreenName, "(IIF)V", null, new String[]{"java/io/IOException"});
            mv.visitCode();

            // Call super.drawScreen first
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitVarInsn(Opcodes.ILOAD, 1);
            mv.visitVarInsn(Opcodes.ILOAD, 2);
            mv.visitVarInsn(Opcodes.FLOAD, 3);
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, guiScreenName, drawScreenName, "(IIF)V", false);

            // GuiScreenFactory.onRender(this, mouseX, mouseY, partialTicks)
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitVarInsn(Opcodes.ILOAD, 1);
            mv.visitVarInsn(Opcodes.ILOAD, 2);
            mv.visitVarInsn(Opcodes.FLOAD, 3);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "com/minecraft/agentloader/GuiScreenFactory",
                "onRender", "(Ljava/lang/Object;IIF)V", false);

            mv.visitInsn(Opcodes.RETURN);
            mv.visitMaxs(4, 4);
            mv.visitEnd();

            // Override keyTyped
            String keyTypedName = guiScreenName.equals("axu") ? "a" : "keyTyped";
            mv = cw.visitMethod(Opcodes.ACC_PROTECTED, keyTypedName, "(CI)V", null,
                new String[]{"java/io/IOException"});
            mv.visitCode();

            // boolean handled = GuiScreenFactory.onKeyPressed(this, character, keyCode)
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitVarInsn(Opcodes.ILOAD, 1);
            mv.visitVarInsn(Opcodes.ILOAD, 2);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "com/minecraft/agentloader/GuiScreenFactory",
                "onKeyPressed", "(Ljava/lang/Object;CI)Z", false);

            Label notHandled = new Label();
            mv.visitJumpInsn(Opcodes.IFEQ, notHandled);
            mv.visitInsn(Opcodes.RETURN);

            mv.visitLabel(notHandled);
            mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitVarInsn(Opcodes.ILOAD, 1);
            mv.visitVarInsn(Opcodes.ILOAD, 2);
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, guiScreenName, keyTypedName, "(CI)V", false);
            mv.visitInsn(Opcodes.RETURN);

            mv.visitMaxs(3, 3);
            mv.visitEnd();

            // Override mouseClicked
            String mouseClickedName = guiScreenName.equals("axu") ? "a" : "mouseClicked";
            mv = cw.visitMethod(Opcodes.ACC_PROTECTED, mouseClickedName, "(III)V", null,
                new String[]{"java/io/IOException"});
            mv.visitCode();

            // boolean handled = GuiScreenFactory.onMouseClick(this, mouseX, mouseY, button)
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitVarInsn(Opcodes.ILOAD, 1);
            mv.visitVarInsn(Opcodes.ILOAD, 2);
            mv.visitVarInsn(Opcodes.ILOAD, 3);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "com/minecraft/agentloader/GuiScreenFactory",
                "onMouseClick", "(Ljava/lang/Object;III)Z", false);

            Label notHandledClick = new Label();
            mv.visitJumpInsn(Opcodes.IFEQ, notHandledClick);
            mv.visitInsn(Opcodes.RETURN);

            mv.visitLabel(notHandledClick);
            mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitVarInsn(Opcodes.ILOAD, 1);
            mv.visitVarInsn(Opcodes.ILOAD, 2);
            mv.visitVarInsn(Opcodes.ILOAD, 3);
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, guiScreenName, mouseClickedName, "(III)V", false);
            mv.visitInsn(Opcodes.RETURN);

            mv.visitMaxs(4, 4);
            mv.visitEnd();

            // Override handleMouseInput
            String handleMouseName = guiScreenName.equals("axu") ? "k" : "handleMouseInput";
            mv = cw.visitMethod(Opcodes.ACC_PUBLIC, handleMouseName, "()V", null,
                new String[]{"java/io/IOException"});
            mv.visitCode();

            // Call super first
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, guiScreenName, handleMouseName, "()V", false);

            // GuiScreenFactory.onMouseInput(this)
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "com/minecraft/agentloader/GuiScreenFactory",
                "onMouseInput", "(Ljava/lang/Object;)V", false);

            mv.visitInsn(Opcodes.RETURN);
            mv.visitMaxs(1, 1);
            mv.visitEnd();

            // Override doesGuiPauseGame
            String pauseMethodName = guiScreenName.equals("axu") ? "c" : "doesGuiPauseGame";
            mv = cw.visitMethod(Opcodes.ACC_PUBLIC, pauseMethodName, "()Z", null, null);
            mv.visitCode();

            // return GuiScreenFactory.shouldPauseGame(this)
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "com/minecraft/agentloader/GuiScreenFactory",
                "shouldPauseGame", "(Ljava/lang/Object;)Z", false);
            mv.visitInsn(Opcodes.IRETURN);

            mv.visitMaxs(1, 1);
            mv.visitEnd();

            // Override onGuiClosed
            String onClosedName = guiScreenName.equals("axu") ? "m" : "onGuiClosed";
            mv = cw.visitMethod(Opcodes.ACC_PUBLIC, onClosedName, "()V", null, null);
            mv.visitCode();

            // Call super first
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, guiScreenName, onClosedName, "()V", false);

            // GuiScreenFactory.onClosed(this)
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "com/minecraft/agentloader/GuiScreenFactory",
                "onClosed", "(Ljava/lang/Object;)V", false);

            mv.visitInsn(Opcodes.RETURN);
            mv.visitMaxs(1, 1);
            mv.visitEnd();

            cw.visitEnd();

            byte[] classBytes = cw.toByteArray();
            ClassLoader modLoader = GuiScreenFactory.class.getClassLoader();
            return new CompositeClassLoader(gameLoader, modLoader).defineClass(className.replace('/', '.'), classBytes);

        } catch (Exception e) {
            Log.error("Failed to generate legacy screen class: " + e.getMessage(), e);
            return null;
        }
    }

    // Static delegate methods called from generated bytecode

    public static void onRender(Object screenInstance, int mouseX, int mouseY, float partialTicks) {
        GuiScreen delegate = getDelegate(screenInstance);
        if (delegate != null) {
            try {
                delegate.render(mouseX, mouseY, partialTicks);
            } catch (Exception e) {
                Log.debug("Error in legacy screen render: " + e.getMessage());
            }
        }
    }

    public static boolean onKeyPressed(Object screenInstance, char character, int keyCode) {
        GuiScreen delegate = getDelegate(screenInstance);
        if (delegate != null) {
            try {
                return delegate.onKeyPressed(character, keyCode);
            } catch (Exception e) {
                Log.debug("Error in legacy screen key press: " + e.getMessage());
            }
        }
        return false;
    }

    public static boolean onMouseClick(Object screenInstance, int mouseX, int mouseY, int button) {
        GuiScreen delegate = getDelegate(screenInstance);
        if (delegate != null) {
            try {
                return delegate.onMouseClick(mouseX, mouseY, button);
            } catch (Exception e) {
                Log.debug("Error in legacy screen mouse click: " + e.getMessage());
            }
        }
        return false;
    }

    public static void onMouseInput(Object screenInstance) {
        GuiScreen delegate = getDelegate(screenInstance);
        if (delegate != null) {
            try {
                // Get mouse wheel delta from Mouse class
                ClassLoader gameLoader = Thread.currentThread().getContextClassLoader();
                Class<?> mouseClass;
                try {
                    mouseClass = Class.forName("org.lwjgl.input.Mouse", true, gameLoader);
                } catch (Exception e) {
                    return;
                }

                Method getEventDWheel = mouseClass.getMethod("getEventDWheel");
                int dWheel = (Integer) getEventDWheel.invoke(null);

                if (dWheel != 0) {
                    Method getEventX = mouseClass.getMethod("getEventX");
                    Method getEventY = mouseClass.getMethod("getEventY");
                    int mouseX = (Integer) getEventX.invoke(null);
                    int mouseY = (Integer) getEventY.invoke(null);

                    delegate.onMouseScroll(mouseX, mouseY, dWheel);
                }
            } catch (Exception e) {
                Log.debug("Error in legacy screen mouse input: " + e.getMessage());
            }
        }
    }

    public static boolean shouldPauseGame(Object screenInstance) {
        GuiScreen delegate = getDelegate(screenInstance);
        if (delegate != null) {
            try {
                return delegate.shouldPauseGame();
            } catch (Exception e) {
                Log.debug("Error in legacy shouldPauseGame: " + e.getMessage());
            }
        }
        return true;
    }

    public static void onClosed(Object screenInstance) {
        removeScreen(screenInstance);
    }

    private static class CompositeClassLoader extends ClassLoader {
        private final ClassLoader gameLoader;
        private final ClassLoader modLoader;

        public CompositeClassLoader(ClassLoader gameLoader, ClassLoader modLoader) {
            super(null);
            this.gameLoader = gameLoader;
            this.modLoader = modLoader;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            Class<?> c = findLoadedClass(name);
            if (c != null) return c;

            try {
                c = modLoader.loadClass(name);
                if (resolve) resolveClass(c);
                return c;
            } catch (ClassNotFoundException e) {}

            try {
                c = gameLoader.loadClass(name);
                if (resolve) resolveClass(c);
                return c;
            } catch (ClassNotFoundException e) {}

            return super.loadClass(name, resolve);
        }

        public Class<?> defineClass(String name, byte[] b) {
            return defineClass(name, b, 0, b.length);
        }
    }
}
