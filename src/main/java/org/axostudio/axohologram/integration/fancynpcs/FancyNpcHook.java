package org.axostudio.axohologram.integration.fancynpcs;

import de.oliver.fancynpcs.api.FancyNpcsPlugin;
import de.oliver.fancynpcs.api.Npc;
import de.oliver.fancynpcs.api.NpcData;
import de.oliver.fancynpcs.api.NpcManager;
import org.axostudio.axohologram.integration.npc.NpcHook;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class FancyNpcHook implements NpcHook {

    private static final String PLUGIN_NAME = "FancyNpcs";

    @Override
    public String getPluginName() {
        return PLUGIN_NAME;
    }

    @Override
    public boolean isAvailable() {
        return Bukkit.getPluginManager().isPluginEnabled(PLUGIN_NAME) && getNpcManager() != null;
    }

    @Override
    public boolean exists(String npcName) {
        return findNpc(npcName) != null;
    }

    @Override
    public Optional<Location> getLocation(String npcName) {
        Npc npc = findNpc(npcName);
        if (npc == null) {
            return Optional.empty();
        }

        NpcData data = npc.getData();
        if (data == null || data.getLocation() == null) {
            return Optional.empty();
        }

        return Optional.of(data.getLocation().clone());
    }

    @Override
    public Collection<String> getNpcNames() {
        NpcManager npcManager = getNpcManager();
        if (npcManager == null) {
            return List.of();
        }

        return npcManager.getAllNpcs().stream()
                .map(this::resolveNpcName)
                .filter(Objects::nonNull)
                .filter(name -> !name.isBlank())
                .distinct()
                .sorted(Comparator.comparing(String::toLowerCase))
                .toList();
    }

    private Npc findNpc(String npcName) {
        if (npcName == null || npcName.isBlank()) {
            return null;
        }

        NpcManager npcManager = getNpcManager();
        if (npcManager == null) {
            return null;
        }

        return npcManager.getNpc(npcName);
    }

    private NpcManager getNpcManager() {
        if (!Bukkit.getPluginManager().isPluginEnabled(PLUGIN_NAME)) {
            return null;
        }

        FancyNpcsPlugin plugin = FancyNpcsPlugin.get();
        return plugin == null ? null : plugin.getNpcManager();
    }

    private String resolveNpcName(Npc npc) {
        NpcData data = npc.getData();
        if (data == null) {
            return null;
        }

        if (data.getName() != null && !data.getName().isBlank()) {
            return data.getName();
        }
        return data.getId();
    }
}
