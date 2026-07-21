package com.minecraft.agentloader.api.transform;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

/**
 * Toolkit handed to a {@link ClassTransformer}. Absorbs the parts of runtime bytecode
 * patching that are hard to get right: a {@link ClassWriter} whose {@code getCommonSuperClass}
 * resolves against the Minecraft classloader (the #1 cause of ASM frame-computation crashes on
 * obfuscated jars), the MC classloader itself, an idempotency check, and an optional class dump.
 */
public interface TransformContext {

    /**
     * A frame-safe {@link ClassWriter} ({@code COMPUTE_FRAMES}) bound to the Minecraft
     * classloader. Use this instead of {@code new ClassWriter(cr, COMPUTE_FRAMES)} — the
     * default writer resolves common superclasses with its own loader and throws on obfuscated
     * game types.
     */
    ClassWriter safeWriter(ClassReader cr);

    /** The classloader the target Minecraft class was loaded from. */
    ClassLoader minecraftClassLoader();

    /** The internal name (slashes) of the class being transformed, e.g. {@code "afh"}. */
    String internalName();

    /**
     * Scan the current bytecode for an existing call to {@code methodName}; lets a transformer
     * skip a class it already patched (return {@code null} from {@link ClassTransformer#transform}
     * if this returns true), avoiding double injection on re-transform.
     */
    boolean alreadyContainsCall(String methodName);

    /** Optionally dump patched bytecode to {@code <user.home>/<label>.class} for debugging. */
    void debugDump(byte[] patched, String label);
}

