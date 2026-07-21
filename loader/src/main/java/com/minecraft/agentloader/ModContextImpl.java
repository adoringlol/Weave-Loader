package com.minecraft.agentloader;

import com.minecraft.agentloader.api.Command;
import com.minecraft.agentloader.api.GuiScreen;
import com.minecraft.agentloader.api.ModContext;
import com.minecraft.agentloader.api.events.ChatEventHandler;
import com.minecraft.agentloader.api.events.PacketEventHandler;
import com.minecraft.agentloader.api.events.PacketReceiveEventHandler;
import com.minecraft.agentloader.api.events.RenderEventHandler;
import com.minecraft.agentloader.api.events.RenderWorldEventHandler;
import com.minecraft.agentloader.api.events.WorldEventHandler;
import com.minecraft.agentloader.api.gui.MinecraftGui;
import com.minecraft.agentloader.api.gui.MinecraftGuiFactory;
import com.minecraft.agentloader.api.transform.ClassTransformer;
import net.weavemc.api.lunar.LunarClient;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

/** Weave-backed implementation of the abandoned loader's ModContext API. */
final class ModContextImpl implements ModContext {
    private final File modDataDir;

    ModContextImpl(String modId) {
        modDataDir = new File(new File(new File(ModLoader.getWeaveDir(), "mod-data"), ModLoader.getGameVersion()), modId);
        migrateLegacyData(modId, modDataDir);
        if (!modDataDir.isDirectory() && !modDataDir.mkdirs()) {
            Log.warn("Could not create legacy mod data directory: " + modDataDir);
        }
    }

    private static void migrateLegacyData(String modId, File destination) {
        File source = new File(new File(ModLoader.getMinecraftDir(), "agentmods-data"), modId);
        if (!source.isDirectory()) return;

        Path sourceRoot = source.toPath();
        Path destinationRoot = destination.toPath();
        Path marker = destinationRoot.resolve(".legacy-data-migrated");
        if (Files.isRegularFile(marker)) return;
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.forEach(path -> {
                Path relative = sourceRoot.relativize(path);
                Path target = destinationRoot.resolve(relative);
                try {
                    if (Files.isDirectory(path)) {
                        Files.createDirectories(target);
                    } else {
                        Files.createDirectories(target.getParent());
                        if (!Files.exists(target)) {
                            Files.copy(path, target, StandardCopyOption.COPY_ATTRIBUTES);
                        }
                    }
                } catch (IOException error) {
                    throw new LegacyDataMigrationException(error);
                }
            });
            Files.write(marker, new byte[0]);
            Log.info("Migrated legacy data for " + modId + " to " + destination);
        } catch (LegacyDataMigrationException error) {
            Log.warn("Could not fully migrate legacy data for " + modId + ": " + error.getCause().getMessage());
        } catch (IOException error) {
            Log.warn("Could not migrate legacy data for " + modId + ": " + error.getMessage());
        }
    }

    private static final class LegacyDataMigrationException extends RuntimeException {
        LegacyDataMigrationException(IOException cause) {
            super(cause);
        }
    }

    @Override
    public void registerCommand(Command command, String... aliases) {
        if (!LegacyCommandAdapter.register(command, this, aliases)) {
            CommandRegistry.register(command, this, aliases);
        }
    }

    @Override public void registerChatListener(ChatEventHandler handler) { EventDispatcher.registerChatListener(handler); }
    @Override public void registerRenderOverlay(RenderEventHandler handler) { EventDispatcher.registerRenderOverlay(handler); }
    @Override public void registerWorldListener(WorldEventHandler handler) { EventDispatcher.registerWorldListener(handler); }
    @Override public void registerPacketListener(PacketEventHandler handler) { EventDispatcher.registerPacketListener(handler); }
    @Override public void registerPacketReceiveListener(PacketReceiveEventHandler handler) { EventDispatcher.registerPacketReceiveListener(handler); }
    @Override public void registerRenderWorldListener(RenderWorldEventHandler handler) { EventDispatcher.registerRenderWorldListener(handler); }

    @Override
    public Object getMinecraftInstance() throws Exception {
        return CommandHandler.getMinecraftInstance(Thread.currentThread().getContextClassLoader());
    }

    @Override public void sendClientMessage(String message) { CommandHandler.sendClientMessage(message); }
    @Override public File getModDataDir() { return modDataDir; }
    @Override public void openScreen(GuiScreen screen) { GuiScreenFactory.openScreen(screen); }
    @Override public void openMinecraftGui(MinecraftGui gui) { MinecraftGuiFactory.openGui(gui); }

    @Override
    public void sendNotification(String title, String message) {
        if (!LunarClient.sendNotification(title, message)) {
            String chat = title == null || title.isEmpty()
                    ? "§b" + message
                    : "§b[" + title + "] §f" + message;
            sendClientMessage(chat);
        }
    }

    @Override public int getLoaderVersion() { return ModLoader.LOADER_VERSION; }

    @Override
    public boolean transformClass(String[] classNameCandidates, ClassTransformer transformer) {
        return TransformService.transformClass(classNameCandidates, transformer);
    }

    @Override public void exposeHelperClass(Class<?> helperClass) { TransformService.exposeHelperClass(helperClass); }
    @Override public void rebuildChunks() { TransformService.rebuildChunks(); }
}
