package org.axostudio.axohologram.importer;

import org.axostudio.axohologram.hologram.billboard.Billboard;
import org.axostudio.axohologram.hologram.line.LineType;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

public record SourceLine(
        LineType type,
        String content,
        ItemStack itemStack,
        BlockData blockData,
        Vector offset,
        Double height,
        Billboard billboard,
        String permission
) {
    public SourceLine {
        offset = offset == null ? new Vector() : offset.clone();
        itemStack = itemStack == null ? null : itemStack.clone();
    }
}
