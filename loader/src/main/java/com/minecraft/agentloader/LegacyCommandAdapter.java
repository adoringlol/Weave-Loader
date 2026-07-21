package com.minecraft.agentloader;

import com.minecraft.agentloader.api.Command;
import com.minecraft.agentloader.api.ModContext;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Registers old AgentMod commands with Weave's native, version-specific command bus.
 *
 * Weave 1.7 and 1.8 expose slightly different getSuggestions signatures. A tiny
 * adapter subclass is generated against the API present in the running client so
 * the core loader remains version-neutral while retaining Weave's full completion
 * pipeline.
 */
public final class LegacyCommandAdapter {
    private static final AtomicInteger nextId = new AtomicInteger();
    private static final ConcurrentHashMap<Integer, Entry> entries = new ConcurrentHashMap<>();

    private static volatile AdapterState state;
    private static volatile boolean unavailable;

    private LegacyCommandAdapter() {}

    public static boolean register(Command command, ModContext context, String... names) {
        String[] effective = names;
        if (effective == null || effective.length == 0) effective = command.getAliases();
        if (effective == null || effective.length == 0) {
            throw new IllegalArgumentException("Legacy command must provide at least one name");
        }

        String primary = normalize(effective[0]);
        Set<String> uniqueAliases = new LinkedHashSet<>();
        for (int i = 1; i < effective.length; i++) {
            String alias = normalize(effective[i]);
            if (!alias.equals(primary)) uniqueAliases.add(alias);
        }
        String[] aliases = uniqueAliases.toArray(new String[0]);

        try {
            AdapterState adapter = getState();
            if (adapter == null) return false;

            int id = nextId.incrementAndGet();
            entries.put(id, new Entry(command, context, primary));
            try {
                Object weaveCommand = adapter.constructor.newInstance(id, primary, aliases);
                Object commandArray = Array.newInstance(adapter.commandClass, 1);
                Array.set(commandArray, 0, weaveCommand);
                adapter.registerMethod.invoke(null, commandArray);
                Log.debug("Registered legacy command /" + primary + " with Weave completion support");
                return true;
            } catch (Throwable error) {
                entries.remove(id);
                throw error;
            }
        } catch (Throwable error) {
            Log.warn("Could not register /" + primary + " with Weave's command bus: " + rootMessage(error));
            return false;
        }
    }

    /** Called by generated Weave Command subclasses. */
    public static void execute(int id, String[] weaveArgs) {
        Entry entry = entries.get(id);
        if (entry == null) return;

        String[] values = weaveArgs == null ? new String[0] : weaveArgs;
        String label = values.length == 0 ? entry.primary : values[0];
        String[] legacyArgs = values.length <= 1
                ? new String[0]
                : Arrays.copyOfRange(values, 1, values.length);
        String raw = "/" + String.join(" ", values.length == 0 ? new String[]{entry.primary} : values);
        entry.command.execute(entry.context, label, legacyArgs, raw);
    }

    /** Called by generated Weave Command subclasses. */
    public static String[] suggestions(int id, String[] args) {
        Entry entry = entries.get(id);
        if (entry == null) return null;

        String[] values = args == null ? new String[0] : args;
        String raw = "/" + entry.primary + (values.length == 0 ? "" : " " + String.join(" ", values));
        List<String> suggestions = entry.command.onTabComplete(entry.primary, values, raw);
        return suggestions == null || suggestions.isEmpty() ? null : suggestions.toArray(new String[0]);
    }

    private static AdapterState getState() throws Exception {
        AdapterState current = state;
        if (current != null) return current;
        if (unavailable) return null;

        synchronized (LegacyCommandAdapter.class) {
            if (state != null) return state;
            if (unavailable) return null;

            try {
                state = createState();
                return state;
            } catch (Exception error) {
                unavailable = true;
                throw error;
            }
        }
    }

    private static AdapterState createState() throws Exception {
        ClassLoader gameLoader = Thread.currentThread().getContextClassLoader();
        if (gameLoader == null) gameLoader = LegacyCommandAdapter.class.getClassLoader();

        Class<?> commandClass = Class.forName("net.weavemc.api.command.Command", false, gameLoader);
        Class<?> commandBus = Class.forName("net.weavemc.api.command.CommandBus", true, gameLoader);
        Method register = commandBus.getMethod("register", Array.newInstance(commandClass, 0).getClass());
        if (!Modifier.isStatic(register.getModifiers())) {
            throw new IllegalStateException("Weave CommandBus.register is not static");
        }

        Constructor<?> superConstructor = commandClass.getConstructor(
                String.class, String[].class, boolean.class, boolean.class, boolean.class);
        Method suggestionsMethod = Arrays.stream(commandClass.getMethods())
                .filter(method -> method.getName().equals("getSuggestions"))
                .filter(method -> method.getParameterCount() >= 1)
                .filter(method -> method.getParameterTypes()[0] == String[].class)
                .findFirst()
                .orElseThrow(() -> new NoSuchMethodException("Weave Command.getSuggestions"));

        String generatedName = "com.minecraft.agentloader.generated.LegacyWeaveCommand"
                + suggestionsMethod.getParameterCount();
        byte[] bytecode = generateAdapter(
                generatedName.replace('.', '/'),
                Type.getInternalName(commandClass),
                Type.getConstructorDescriptor(superConstructor),
                suggestionsMethod);
        Class<?> adapterClass = new AdapterClassLoader(gameLoader).define(generatedName, bytecode);
        Constructor<?> constructor = adapterClass.getConstructor(int.class, String.class, String[].class);
        return new AdapterState(commandClass, register, constructor);
    }

    private static byte[] generateAdapter(
            String generatedName,
            String commandName,
            String commandConstructorDescriptor,
            Method suggestionsMethod
    ) {
        String runtimeName = Type.getInternalName(LegacyCommandAdapter.class);
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, generatedName, null, commandName, null);
        writer.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "legacyId", "I", null, null).visitEnd();

        MethodVisitor method = writer.visitMethod(
                Opcodes.ACC_PUBLIC,
                "<init>",
                "(ILjava/lang/String;[Ljava/lang/String;)V",
                null,
                null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ALOAD, 2);
        method.visitVarInsn(Opcodes.ALOAD, 3);
        method.visitInsn(Opcodes.ICONST_1);
        method.visitInsn(Opcodes.ICONST_1);
        method.visitInsn(Opcodes.ICONST_0);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, commandName, "<init>", commandConstructorDescriptor, false);
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ILOAD, 1);
        method.visitFieldInsn(Opcodes.PUTFIELD, generatedName, "legacyId", "I");
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(6, 4);
        method.visitEnd();

        method = writer.visitMethod(
                Opcodes.ACC_PUBLIC,
                "execute",
                "([Ljava/lang/String;)V",
                null,
                null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitFieldInsn(Opcodes.GETFIELD, generatedName, "legacyId", "I");
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                runtimeName,
                "execute",
                "(I[Ljava/lang/String;)V",
                false);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(2, 2);
        method.visitEnd();

        String suggestionDescriptor = Type.getMethodDescriptor(suggestionsMethod);
        method = writer.visitMethod(
                Opcodes.ACC_PUBLIC,
                "getSuggestions",
                suggestionDescriptor,
                null,
                null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitFieldInsn(Opcodes.GETFIELD, generatedName, "legacyId", "I");
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                runtimeName,
                "suggestions",
                "(I[Ljava/lang/String;)[Ljava/lang/String;",
                false);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(2, suggestionsMethod.getParameterCount() + 1);
        method.visitEnd();

        writer.visitEnd();
        return writer.toByteArray();
    }

    private static String normalize(String value) {
        String normalized = value == null ? "" : value.trim();
        while (normalized.startsWith("/")) normalized = normalized.substring(1);
        if (normalized.isEmpty()) throw new IllegalArgumentException("Legacy command name cannot be empty");
        return normalized.toLowerCase(Locale.ROOT);
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        String message = current.getMessage();
        return message == null || message.isEmpty() ? current.getClass().getSimpleName() : message;
    }

    private static final class AdapterClassLoader extends ClassLoader {
        AdapterClassLoader(ClassLoader parent) {
            super(parent);
        }

        Class<?> define(String name, byte[] bytecode) {
            return defineClass(name, bytecode, 0, bytecode.length);
        }
    }

    private static final class AdapterState {
        final Class<?> commandClass;
        final Method registerMethod;
        final Constructor<?> constructor;

        AdapterState(Class<?> commandClass, Method registerMethod, Constructor<?> constructor) {
            this.commandClass = commandClass;
            this.registerMethod = registerMethod;
            this.constructor = constructor;
        }
    }

    private static final class Entry {
        final Command command;
        final ModContext context;
        final String primary;

        Entry(Command command, ModContext context, String primary) {
            this.command = command;
            this.context = context;
            this.primary = primary;
        }
    }
}
