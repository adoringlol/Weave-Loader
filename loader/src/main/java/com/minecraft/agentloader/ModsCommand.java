package com.minecraft.agentloader;

import com.minecraft.agentloader.api.Command;
import com.minecraft.agentloader.api.ModContext;
import java.util.List;

/**
 * Built-in command to list loaded mods.
 */
public class ModsCommand implements Command {

    @Override
    public boolean execute(ModContext context, String label, String[] args, String rawMessage) {
        List<ModLoader.LoadedMod> mods = ModLoader.getLoadedMods();

        if (mods.isEmpty()) {
            context.sendClientMessage("\u00a7cNo mods loaded!");
            return true;
        }

        context.sendClientMessage("\u00a7a\u00a7lLoaded Mods \u00a77(" + mods.size() + ")\u00a7r");
        context.sendClientMessage("\u00a77\u00a7m--------------------");

        for (ModLoader.LoadedMod loadedMod : mods) {
            String modId = loadedMod.getMod().getModId();
            String version = loadedMod.getMod().getVersion();
            context.sendClientMessage("\u00a7e" + modId + " \u00a77v" + version);
        }

        context.sendClientMessage("\u00a77\u00a7m--------------------");
        return true;
    }

    @Override
    public String[] getAliases() {
        return new String[]{"mods", "modlist"};
    }
}

