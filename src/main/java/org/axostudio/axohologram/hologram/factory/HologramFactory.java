package org.axostudio.axohologram.hologram.factory;

import org.axostudio.axohologram.AxoHologram;
import org.axostudio.axohologram.hologram.Hologram;
import org.axostudio.axohologram.hologram.impl.AxoHologramImpl;
import org.axostudio.axohologram.hologram.line.LineType;
import org.axostudio.axohologram.hologram.line.impl.BlockLineImpl;
import org.axostudio.axohologram.hologram.line.impl.ItemLineImpl;
import org.axostudio.axohologram.hologram.line.impl.TextLineImpl;
import org.axostudio.axohologram.hologram.page.HologramPage;
import org.bukkit.Location;

public final class HologramFactory {

    private HologramFactory() {
    }

    public static Hologram create(String id, Location location, AxoHologram plugin) {
        return create(id, LineType.TEXT, location, plugin);
    }

    public static Hologram create(String id, LineType type, Location location, AxoHologram plugin) {
        Hologram hologram = new AxoHologramImpl(id, location, plugin);
        HologramPage page = hologram.getPage(0);
        if (page == null) {
            return hologram;
        }

        if (!page.getLines().isEmpty()) {
            return hologram;
        }

        switch (type) {
            case TEXT -> {
                if (plugin.getConfigManager().getConfig().getBoolean("general.defaults.create-default-line", true)) {
                    String defaultLine = plugin.getConfigManager().getConfig()
                            .getString("general.defaults.create-default-line-format", "<white><id></white>")
                            .replace("<id>", id);
                    page.addLine(new TextLineImpl(defaultLine, plugin));
                }
            }
            case ITEM -> {
                String defaultItem = plugin.getConfigManager().getConfig()
                        .getString("general.defaults.create-default-item-material", "PAPER");
                page.addLine(new ItemLineImpl(defaultItem, plugin));
            }
            case BLOCK -> {
                String defaultBlock = plugin.getConfigManager().getConfig()
                        .getString("general.defaults.create-default-block-material", "STONE");
                page.addLine(new BlockLineImpl(defaultBlock, plugin));
            }
        }

        return hologram;
    }
}
