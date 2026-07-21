package net.weavemc.loader.impl.bootstrap.transformer

import net.weavemc.internals.asm
import net.weavemc.internals.internalNameOf
import net.weavemc.internals.visitAsm
import net.weavemc.loader.impl.bootstrap.PublicButInternal
import net.weavemc.loader.impl.mixin.LoaderClassWriter
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.LabelNode
import java.net.URL
import java.net.URLClassLoader

@PublicButInternal
public interface URLClassLoaderAccessor {
    public val weaveBacking: ClassLoader
    public fun addWeaveURL(url: URL)
}

@PublicButInternal
public object URLClassLoaderTransformer : SafeTransformer {
    override fun transform(loader: ClassLoader?, className: String, originalClass: ByteArray): ByteArray? {
        if (loader == null) return null

        val reader = ClassReader(originalClass)
        if (reader.superName != internalNameOf<URLClassLoader>()) return null

        val node = ClassNode()
        reader.accept(node, 0)

        node.interfaces.add(internalNameOf<URLClassLoaderAccessor>())

        // public URLClassLoader getWeaveBacking()
        node.visitMethod(Opcodes.ACC_PUBLIC, "getWeaveBacking", "()Ljava/lang/ClassLoader;", null, null).visitAsm {
            aload(0)
            areturn
        }

        val defaultIgnoredPackages = listOf(
            "net.weavemc.loader.impl.bootstrap.",
            "me.xtrm.klog.",
            "kotlin."
        )

        node.visitMethod(
            Opcodes.ACC_PRIVATE or Opcodes.ACC_FINAL,
            "weave\$shouldIgnore",
            "(Ljava/lang/String;)Z",
            null,
            null
        ).visitAsm {
            val ignored = LabelNode()

            defaultIgnoredPackages.forEach { pkg ->
                aload(1)
                ldc(pkg)
                invokevirtual("java/lang/String", "startsWith", "(Ljava/lang/String;)Z")
                ifne(ignored)
            }

            iconst_0
            ireturn

            +ignored
            iconst_1
            ireturn
        }

        // public void addWeaveURL(URL url)
        node.visitMethod(Opcodes.ACC_PUBLIC, "addWeaveURL", "(Ljava/net/URL;)V", null, null).visitAsm {
            aload(0)
            aload(1)
            invokevirtual(node.name, "addURL", "(Ljava/net/URL;)V")
            _return
        }

        listOf("loadClass", "findClass")
            .mapNotNull { t -> node.methods.find { it.name == t } }
            .forEach { method ->
                method.instructions.insert(asm {
                    val notIgnored = LabelNode()

                    // if (weave$shouldIgnore(name))
                    aload(0)
                    aload(1)
                    invokespecial(node.name, "weave\$shouldIgnore", "(Ljava/lang/String;)Z")
                    ifeq(notIgnored)

                    // return ClassLoader.getSystemClassLoader().loadClass(name);
                    invokestatic("java/lang/ClassLoader", "getSystemClassLoader", "()Ljava/lang/ClassLoader;")
                    aload(1)
                    invokevirtual("java/lang/ClassLoader", "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;")
                    areturn

                    +notIgnored
                })
            }

        return LoaderClassWriter(loader, reader, ClassWriter.COMPUTE_FRAMES).also { node.accept(it) }.toByteArray()
    }
}
