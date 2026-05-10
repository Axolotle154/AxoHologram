package org.axostudio.axohologram.importer;

import org.axostudio.axohologram.hologram.billboard.Billboard;
import org.axostudio.axohologram.hologram.visibility.VisibilityMode;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Vector;

import java.util.List;

public record SourceHologram(
        String source,
        String name,
        String worldName,
        Location location,
        boolean enabled,
        int visibilityDistance,
        VisibilityMode visibilityMode,
        Billboard billboard,
        float scaleX,
        float scaleY,
        float scaleZ,
        Vector translation,
        float shadowRadius,
        float shadowStrength,
        Color backgroundColor,
        boolean textShadow,
        boolean seeThrough,
        TextDisplay.TextAlignment alignment,
        long updateTextInterval,
        String displayAnimation,
        List<List<SourceLine>> pages
) {
    public SourceHologram {
        translation = translation == null ? new Vector() : translation.clone();
        pages = pages == null || pages.isEmpty() ? List.of(List.of()) : List.copyOf(pages);
    }
}
