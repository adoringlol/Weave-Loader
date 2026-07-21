package com.minecraft.agentloader;

import com.minecraft.agentloader.api.AgentMod;
import com.minecraft.agentloader.api.AgentModV2;
import com.minecraft.agentloader.api.ModContext;

import java.io.File;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.jar.JarFile;

/** Loads the abandoned agent-loader's mods inside Weave without a second agent. */
public final class ModLoader {
    public static final int LOADER_VERSION = 38;

    private static final List<LoadedMod> loadedMods = new ArrayList<>();
    private static volatile String gameVersion = "1.8.9";

    private ModLoader() {}

    public static synchronized List<LoadedMod> getLoadedMods() {
        return new ArrayList<>(loadedMods);
    }

    public static synchronized void loadMods(String version) {
        gameVersion = version == null || version.trim().isEmpty() ? "1.8.9" : version.trim();
        File modsDir = getWeaveModsDir();
        File versionDir = new File(modsDir, gameVersion);
        if (!versionDir.isDirectory() && !versionDir.mkdirs()) {
            Log.warn("Could not create Weave version mods directory: " + versionDir);
            return;
        }

        Set<File> candidates = new LinkedHashSet<>();
        addJars(candidates, modsDir);
        addJars(candidates, versionDir);
        List<File> jars = new ArrayList<>();
        for (File candidate : candidates) {
            if (isLegacyAgentMod(candidate)) jars.add(candidate);
        }
        jars.sort(Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));

        if (jars.isEmpty()) {
            Log.info("No legacy AgentMods found in " + modsDir.getAbsolutePath());
            return;
        }

        Log.info("Found " + jars.size() + " legacy AgentMod JAR(s) in Weave's mods folders");
        for (File jar : jars) {
            try {
                loadMod(jar);
            } catch (Throwable error) {
                Log.error("Failed to load legacy mod from " + jar.getName(), error);
            }
        }
        Log.info("Successfully loaded " + loadedMods.size() + " legacy mod(s)");
    }

    private static void addJars(Set<File> target, File directory) {
        File[] files = directory.listFiles((dir, name) -> name.toLowerCase().endsWith(".jar"));
        if (files == null) return;
        Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        target.addAll(Arrays.asList(files));
    }

    private static boolean isLegacyAgentMod(File file) {
        try (JarFile jar = new JarFile(file)) {
            return jar.getEntry("META-INF/services/com.minecraft.agentloader.api.AgentMod") != null
                    || jar.getEntry("META-INF/services/com.minecraft.agentloader.api.AgentModV2") != null;
        } catch (Exception error) {
            Log.warn("Could not inspect " + file.getName() + ": " + error.getMessage());
            return false;
        }
    }

    private static void loadMod(File jar) throws Exception {
        LegacyModClassLoader loader = new LegacyModClassLoader(jar, AgentMod.class.getClassLoader());
        List<AgentMod> discovered = new ArrayList<>();
        Set<String> discoveredClasses = new HashSet<>();

        for (AgentMod mod : ServiceLoader.load(AgentMod.class, loader)) {
            if (discoveredClasses.add(mod.getClass().getName())) discovered.add(mod);
        }
        for (AgentModV2 mod : ServiceLoader.load(AgentModV2.class, loader)) {
            if (discoveredClasses.add(mod.getClass().getName())) discovered.add(mod);
        }

        if (discovered.isEmpty()) {
            loader.close();
            Log.warn("No AgentMod service found in " + jar.getName());
            return;
        }

        for (AgentMod mod : discovered) {
            ModContext context = new ModContextImpl(mod.getModId());
            mod.onInitialize(context);
            loadedMods.add(new LoadedMod(mod, loader));
            Log.info("Loaded legacy mod " + mod.getModId() + " v" + mod.getVersion());
        }
    }

    public static synchronized void shutdownMods() {
        for (LoadedMod loaded : loadedMods) {
            try {
                loaded.mod.onShutdown();
            } catch (Throwable error) {
                Log.error("Error shutting down " + loaded.mod.getModId(), error);
            }
            try {
                loaded.classLoader.close();
            } catch (Exception ignored) {}
        }
        loadedMods.clear();
    }

    public static File getMinecraftDir() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String home = System.getProperty("user.home");
        if (os.contains("win")) {
            String appdata = System.getenv("APPDATA");
            if (appdata != null) return new File(appdata, ".minecraft");
        } else if (os.contains("mac")) {
            return new File(home, "Library/Application Support/minecraft");
        }
        return new File(home, ".minecraft");
    }

    public static File getWeaveDir() {
        return new File(System.getProperty("user.home"), ".weave");
    }

    public static File getWeaveModsDir() {
        return new File(getWeaveDir(), "mods");
    }

    public static String getGameVersion() {
        return gameVersion;
    }

    public static final class LoadedMod {
        private final AgentMod mod;
        private final URLClassLoader classLoader;

        LoadedMod(AgentMod mod, URLClassLoader classLoader) {
            this.mod = mod;
            this.classLoader = classLoader;
        }

        public AgentMod getMod() { return mod; }
        public URLClassLoader getClassLoader() { return classLoader; }
    }
}
