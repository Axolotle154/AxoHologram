package org.axostudio.axohologram.integration;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.axostudio.axohologram.AxoHologram;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

public class PlaceholderAPIIntegration extends PlaceholderExpansion {

    private final AxoHologram plugin;

    public PlaceholderAPIIntegration(AxoHologram plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "axohologram";
    }

    @Override
    public @NotNull String getAuthor() {
        return "AxoStudio";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (params.equalsIgnoreCase("total")) {
            return String.valueOf(plugin.getHologramManager().getAllHolograms().size());
        }
        return null;
    }
}
