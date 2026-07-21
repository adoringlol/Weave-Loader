package com.minecraft.agentloader.api.transform;

import org.objectweb.asm.ClassReader;

/**
 * A mod-supplied bytecode transformer for a single Minecraft class. Implement this and pass
 * it to {@link com.minecraft.agentloader.api.ModContext#transformClass}. The loader resolves
 * the target class, registers a one-shot {@link java.lang.instrument.ClassFileTransformer},
 * calls {@code retransformClasses}, and removes the transformer — you only write the visitor.
 *
 * <p>This generalizes the runtime block-modification pattern (e.g. endstone-outline rewriting
 * {@code Block.isOpaqueCube}/{@code getBlockLayer} and {@code BlockRendererDispatcher.renderBlock})
 * so mods no longer hand-roll the instrumentation plumbing, the frame-safe ClassWriter, or the
 * helper-classloader injection.
 */
public interface ClassTransformer {
    /**
     * @param cr  a ClassReader over the target's current bytecode
     * @param ctx helpers: a frame-safe ClassWriter, the MC classloader, idempotency check, dump
     * @return the new class bytes, or {@code null} to leave the class unchanged
     */
    byte[] transform(ClassReader cr, TransformContext ctx);
}

