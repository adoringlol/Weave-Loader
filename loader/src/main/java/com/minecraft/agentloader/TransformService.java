package com.minecraft.agentloader;

import com.minecraft.agentloader.api.transform.ClassTransformer;
import com.minecraft.agentloader.api.transform.TransformContext;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

/**
 * Loader-side engine for the runtime class-transformation API exposed via
 * {@link com.minecraft.agentloader.api.ModContext#transformClass}. Generalizes the
 * instrumentation plumbing that mods (endstone-outline) used to hand-roll ~600 lines of:
 * one-shot {@link ClassFileTransformer} + {@code retransformClasses}, a frame-safe
 * {@link ClassWriter} bound to the MC classloader, helper-class injection into the bootstrap
 * loader, and the chunk-rebuild trigger.
 */
final class TransformService {
    private TransformService() {}

    private static Instrumentation inst() {
        return MinecraftAgent.instrumentation;
    }

    private static ClassLoader mcLoader() {
        Object mc = GameAccess.minecraft();
        if (mc != null) return mc.getClass().getClassLoader();
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        return cl != null ? cl : ClassLoader.getSystemClassLoader();
    }

    // ------------------------------------------------------------------
    // transformClass
    // ------------------------------------------------------------------

    static boolean transformClass(String[] candidates, ClassTransformer transformer) {
        Instrumentation inst = inst();
        if (inst == null) {
            Log.warn("transformClass: no Instrumentation");
            return false;
        }
        ClassLoader cl = mcLoader();
        Class<?> target = null;
        for (String name : candidates) {
            try { target = Class.forName(name, false, cl); break; } catch (Throwable ignored) {}
        }
        if (target == null) {
            Log.warn("transformClass: none of " + java.util.Arrays.toString(candidates) + " found in " + cl);
            return false;
        }
        final String internal = target.getName().replace('.', '/');
        final ClassLoader fcl = cl;
        ClassFileTransformer cft = new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader loader, String className, Class<?> cbr,
                                    ProtectionDomain pd, byte[] bytes) {
                if (className == null || !className.equals(internal)) return null;
                try {
                    ClassReader cr = new ClassReader(bytes);
                    return transformer.transform(cr, new ContextImpl(internal, fcl, bytes));
                } catch (Throwable t) {
                    Log.error("transformClass ASM failed for " + internal + ": " + t, t);
                    return null;
                }
            }
        };
        inst.addTransformer(cft, true);
        try {
            inst.retransformClasses(target);
            return true;
        } catch (Throwable t) {
            Log.error("retransform failed for " + internal + ": " + t, t);
            return false;
        } finally {
            inst.removeTransformer(cft);
        }
    }

    /** A frame-safe ClassWriter bound to a specific classloader (lifted from the proven
     *  endstone-outline getCommonSuperClass override). */
    static ClassWriter safeWriter(ClassReader cr, final ClassLoader cl) {
        return new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES) {
            @Override
            protected String getCommonSuperClass(String type1, String type2) {
                try {
                    ClassLoader loader = cl != null ? cl : ClassLoader.getSystemClassLoader();
                    Class<?> c1 = Class.forName(type1.replace('/', '.'), false, loader);
                    Class<?> c2 = Class.forName(type2.replace('/', '.'), false, loader);
                    if (c1.isAssignableFrom(c2)) return type1;
                    if (c2.isAssignableFrom(c1)) return type2;
                    if (c1.isInterface() || c2.isInterface()) return "java/lang/Object";
                    Class<?> c = c1;
                    do { c = c.getSuperclass(); } while (c != null && !c.isAssignableFrom(c2));
                    return c == null ? "java/lang/Object" : c.getName().replace('.', '/');
                } catch (Throwable t) {
                    return "java/lang/Object";
                }
            }
        };
    }

    private static final class ContextImpl implements TransformContext {
        private final String internal;
        private final ClassLoader cl;
        private final byte[] original;
        ContextImpl(String internal, ClassLoader cl, byte[] original) {
            this.internal = internal; this.cl = cl; this.original = original;
        }
        @Override public ClassWriter safeWriter(ClassReader cr) { return TransformService.safeWriter(cr, cl); }
        @Override public ClassLoader minecraftClassLoader() { return cl; }
        @Override public String internalName() { return internal; }
        @Override public boolean alreadyContainsCall(final String methodName) {
            final boolean[] found = {false};
            try {
                new ClassReader(original).accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override public MethodVisitor visitMethod(int a, String n, String d, String s, String[] e) {
                        return new MethodVisitor(Opcodes.ASM9) {
                            @Override public void visitMethodInsn(int op, String o, String mn, String md, boolean itf) {
                                if (methodName.equals(mn)) found[0] = true;
                            }
                        };
                    }
                }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            } catch (Throwable ignored) {}
            return found[0];
        }
        @Override public void debugDump(byte[] patched, String label) {
            try (FileOutputStream fos = new FileOutputStream(
                    new File(System.getProperty("user.home"), label + ".class"))) {
                fos.write(patched);
            } catch (Throwable ignored) {}
        }
    }

    // ------------------------------------------------------------------
    // exposeHelperClass — stage a mod helper into the bootstrap classloader
    // so bytecode injected into MC classes can call it.
    // ------------------------------------------------------------------

    static void exposeHelperClass(Class<?> helperClass) {
        Instrumentation inst = inst();
        if (inst == null || helperClass == null) return;
        try {
            String resPath = helperClass.getName().replace('.', '/') + ".class";
            byte[] bytes;
            try (InputStream is = helperClass.getClassLoader().getResourceAsStream(resPath)) {
                if (is == null) {
                    Log.warn("exposeHelperClass: " + resPath + " not found");
                    return;
                }
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
                bytes = baos.toByteArray();
            }
            File tmpJar = File.createTempFile("agent-helper-", ".jar");
            tmpJar.deleteOnExit();
            try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(tmpJar))) {
                jos.putNextEntry(new ZipEntry(resPath));
                jos.write(bytes);
                jos.closeEntry();
            }
            try {
                inst.appendToBootstrapClassLoaderSearch(new JarFile(tmpJar));
            } catch (Throwable bootErr) {
                inst.appendToSystemClassLoaderSearch(new JarFile(tmpJar));
            }
        } catch (Throwable t) {
            Log.error("exposeHelperClass failed: " + t, t);
        }
    }

    // ------------------------------------------------------------------
    // rebuildChunks — force a world re-render (loadRenderers / markBlockRange...)
    // ------------------------------------------------------------------

    static void rebuildChunks() {
        try {
            Object mc = GameAccess.minecraft();
            if (mc == null) return;
            Field rgField = null;
            for (Class<?> c = mc.getClass(); c != null && rgField == null; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (f.getName().equals("renderGlobal") || f.getName().equals("field_71438_f")) {
                        f.setAccessible(true);
                        rgField = f;
                        break;
                    }
                }
            }
            if (rgField == null) return;
            Object rg = rgField.get(mc);
            if (rg == null) return;
            try {
                Method m = rg.getClass().getMethod("loadRenderers");
                m.invoke(rg);
            } catch (NoSuchMethodException e) {
                try {
                    Method m = rg.getClass().getMethod("markBlockRangeForRenderUpdate",
                            int.class, int.class, int.class, int.class, int.class, int.class);
                    m.invoke(rg, -1000, 0, -1000, 1000, 256, 1000);
                } catch (NoSuchMethodException ignored) {}
            }
        } catch (Throwable t) {
            Log.debug("rebuildChunks failed: " + t);
        }
    }
}
