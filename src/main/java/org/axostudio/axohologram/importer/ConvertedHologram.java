package org.axostudio.axohologram.importer;

import org.axostudio.axohologram.hologram.billboard.Billboard;
import org.axostudio.axohologram.hologram.line.LineType;
import org.axostudio.axohologram.hologram.visibility.VisibilityMode;
import org.axostudio.axohologram.util.ColorUtil;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ConvertedHologram {

    private final String id;
    private final String sourceName;
    private final String worldName;
    private final Location location;
    private final boolean enabled;
    private final int visibilityDistance;
    private final VisibilityMode visibilityMode;
    private final Billboard billboard;
    private final float scaleX;
    private final float scaleY;
    private final float scaleZ;
    private final Vector translation;
    private final float shadowRadius;
    private final float shadowStrength;
    private final Color backgroundColor;
    private final boolean textShadow;
    private final boolean seeThrough;
    private final TextDisplay.TextAlignment alignment;
    private final long updateTextInterval;
    private final String displayAnimation;
    private final List<List<ConvertedLine>> pages;

    public ConvertedHologram(
            String id,
            String sourceName,
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
            List<List<ConvertedLine>> pages
    ) {
        this.id = id;
        this.sourceName = sourceName;
        this.worldName = worldName;
        this.location = location == null ? null : location.clone();
        this.enabled = enabled;
        this.visibilityDistance = visibilityDistance;
        this.visibilityMode = visibilityMode == null ? VisibilityMode.ALL : visibilityMode;
        this.billboard = billboard == null ? Billboard.CENTER : billboard;
        this.scaleX = Math.max(0.01F, scaleX);
        this.scaleY = Math.max(0.01F, scaleY);
        this.scaleZ = Math.max(0.01F, scaleZ);
        this.translation = translation == null ? new Vector() : translation.clone();
        this.shadowRadius = Math.max(0.0F, shadowRadius);
        this.shadowStrength = Math.max(0.0F, shadowStrength);
        this.backgroundColor = backgroundColor;
        this.textShadow = textShadow;
        this.seeThrough = seeThrough;
        this.alignment = alignment == null ? TextDisplay.TextAlignment.CENTER : alignment;
        this.updateTextInterval = updateTextInterval;
        this.displayAnimation = displayAnimation == null || displayAnimation.isBlank() ? null : displayAnimation.trim();
        this.pages = pages == null || pages.isEmpty() ? List.of(List.of()) : List.copyOf(pages);
    }

    public String id() {
        return id;
    }

    public String sourceName() {
        return sourceName;
    }

    public int lineCount() {
        int count = 0;
        for (List<ConvertedLine> page : pages) {
            count += page.size();
        }
        return count;
    }

    public YamlConfiguration toConfiguration() {
        YamlConfiguration config = new YamlConfiguration();
        serialize(config);
        return config;
    }

    public void serialize(ConfigurationSection section) {
        Location targetLocation = location;
        boolean compactTextHologram = isCompactTextHologram();
        section.set("enabled", enabled ? null : false);
        section.set("location.world", targetLocation != null && targetLocation.getWorld() != null ? targetLocation.getWorld().getName() : worldName);
        section.set("location.x", targetLocation == null ? 0.0D : targetLocation.getX());
        section.set("location.y", targetLocation == null ? 0.0D : targetLocation.getY());
        section.set("location.z", targetLocation == null ? 0.0D : targetLocation.getZ());
        section.set("location.yaw", targetLocation == null ? 0.0F : targetLocation.getYaw());
        section.set("location.pitch", targetLocation == null ? 0.0F : targetLocation.getPitch());
        section.set("translation.x", translation.getX());
        section.set("translation.y", translation.getY());
        section.set("translation.z", translation.getZ());
        section.set("visibility.mode", visibilityMode.name());
        section.set("visibility.distance", visibilityDistance > 0 ? visibilityDistance : null);
        section.set("scale.x", scaleX);
        section.set("scale.y", scaleY);
        section.set("scale.z", scaleZ);
        section.set("billboard", billboard.name());
        section.set("shadow.strength", shadowStrength);
        section.set("shadow.radius", shadowRadius);
        section.set("style.background", backgroundColor == null ? null : ColorUtil.toHex(backgroundColor));
        section.set("style.text-shadow", textShadow);
        section.set("style.see-through", seeThrough);
        section.set("style.alignment", alignment.name());
        if (compactTextHologram) {
            section.set("update-text-interval", updateTextInterval > 0L ? updateTextInterval : null);
        } else {
            section.set("text.update-interval", updateTextInterval > 0L ? updateTextInterval : null);
        }
        section.set("display-animation-enabled", displayAnimation != null);
        section.set("display-animation", displayAnimation);
        section.set("default-page", 1);

        if (compactTextHologram) {
            section.set("type", "TEXT");
            section.set("text", collectCompactText());
            section.set("pages", null);
            return;
        }

        List<Map<String, Object>> serializedPages = new ArrayList<>();
        for (List<ConvertedLine> page : pages) {
            YamlConfiguration pageConfig = new YamlConfiguration();
            List<Map<String, Object>> serializedLines = new ArrayList<>();
            for (ConvertedLine line : page) {
                YamlConfiguration lineConfig = new YamlConfiguration();
                line.serialize(lineConfig);
                serializedLines.add(lineConfig.getValues(false));
            }
            pageConfig.set("lines", serializedLines);
            serializedPages.add(pageConfig.getValues(false));
        }
        section.set("pages", serializedPages);
    }

    private boolean isCompactTextHologram() {
        return pages.size() == 1 && isCompactTextPage(pages.getFirst());
    }

    private boolean isCompactTextPage(List<ConvertedLine> page) {
        if (page == null || page.isEmpty()) {
            return false;
        }

        for (ConvertedLine line : page) {
            if (line == null || line.type() != LineType.TEXT) {
                return false;
            }
            Vector lineOffset = line.offset();
            if (lineOffset.getX() != 0.0D || lineOffset.getY() != 0.0D || lineOffset.getZ() != 0.0D) {
                return false;
            }
            if (line.billboard() != null) {
                return false;
            }
            if (line.permission() != null && !line.permission().isBlank()) {
                return false;
            }
        }

        return true;
    }

    private List<String> collectCompactText() {
        List<ConvertedLine> page = pages.getFirst();
        List<String> textLines = new ArrayList<>(page.size());
        for (ConvertedLine line : page) {
            textLines.add(line.content() == null ? "" : line.content());
        }
        return textLines;
    }

    public record ConvertedLine(
            LineType type,
            String content,
            ItemStack itemStack,
            BlockData blockData,
            Vector offset,
            Double height,
            Billboard billboard,
            String permission
    ) {
        public ConvertedLine {
            offset = offset == null ? new Vector() : offset.clone();
            itemStack = itemStack == null ? null : itemStack.clone();
        }

        private void serialize(ConfigurationSection section) {
            section.set("type", type.name());
            String lineContent = switch (type) {
                case TEXT -> content == null ? "" : content;
                case ITEM -> itemStack == null ? content : itemStack.getType().name();
                case BLOCK -> blockData == null ? content : blockData.getAsString();
            };
            section.set("content", lineContent);
            if (type == LineType.ITEM && itemStack != null && (itemStack.getAmount() != 1 || itemStack.hasItemMeta())) {
                section.set("item", itemStack);
            }

            ConfigurationSection offsetSection = section.createSection("offset");
            offsetSection.set("x", offset.getX());
            offsetSection.set("y", offset.getY());
            offsetSection.set("z", offset.getZ());
            section.set("height", height);
            section.set("billboard", billboard == null ? null : billboard.name());
            section.set("permission", permission == null || permission.isBlank() ? null : permission);
        }
    }
}
