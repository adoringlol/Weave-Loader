package com.minecraft.agentloader;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.jar.JarFile;

/** Isolated legacy-mod loader with an ASM namespace bridge for thin old mods. */
final class LegacyModClassLoader extends URLClassLoader {
    // Construct these at runtime so Shadow does not rewrite the namespace markers.
    private static final String ASM_PREFIX = String.join("/", "org", "objectweb", "asm");
    private static final String WEAVE_ASM_PREFIX = String.join(
            "/", "net", "weavemc", "loader", "impl", "shaded", "asm");
    private static final String ASM_BINARY_PREFIX = String.join(".", "org", "objectweb", "asm") + ".";

    private final boolean remapAsmReferences;

    LegacyModClassLoader(File jar, ClassLoader parent) throws IOException {
        super(new URL[]{jar.toURI().toURL()}, parent);
        try (JarFile file = new JarFile(jar)) {
            remapAsmReferences = file.getEntry(ASM_PREFIX + "/ClassReader.class") == null;
        }
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        if (!remapAsmReferences || name.startsWith(ASM_BINARY_PREFIX)) {
            return super.findClass(name);
        }

        String resource = name.replace('.', '/') + ".class";
        try (InputStream input = findResource(resource) != null ? findResource(resource).openStream() : null) {
            if (input == null) return super.findClass(name);

            byte[] original = readAll(input);
            ClassReader reader = new ClassReader(original);
            ClassWriter writer = new ClassWriter(reader, 0);
            reader.accept(new ClassRemapper(writer, new Remapper() {
                @Override
                public String map(String internalName) {
                    if (internalName.equals(ASM_PREFIX) || internalName.startsWith(ASM_PREFIX + "/")) {
                        return WEAVE_ASM_PREFIX + internalName.substring(ASM_PREFIX.length());
                    }
                    return internalName;
                }
            }), 0);

            byte[] transformed = writer.toByteArray();
            return defineClass(name, transformed, 0, transformed.length);
        } catch (IOException e) {
            throw new ClassNotFoundException(name, e);
        }
    }

    private static byte[] readAll(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
        return output.toByteArray();
    }
}
