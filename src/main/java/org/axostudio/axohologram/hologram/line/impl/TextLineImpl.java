package org.axostudio.axohologram.hologram.line.impl;

import org.axostudio.axohologram.AxoHologram;
import org.axostudio.axohologram.hologram.Hologram;
import org.axostudio.axohologram.hologram.billboard.Billboard;
import org.axostudio.axohologram.hologram.line.HologramLine;
import org.axostudio.axohologram.hologram.line.LineType;
import org.axostudio.axohologram.packet.HologramPacketManager;
import org.axostudio.axohologram.util.MiniMessageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Map;

public class TextLineImpl implements HologramLine {

    private final AxoHologram plugin;
    private volatile String content;
    private volatile Vector offset;
    private volatile double height;
    private volatile boolean heightOverride;
    private volatile Billboard billboard;
    private volatile boolean billboardOverride;
    private volatile String permission;

    public TextLineImpl(String content, AxoHologram plugin) {
        this.content = content;
        this.plugin = plugin;
        this.offset = new Vector(0, 0, 0);
        this.height = 0.0D;
        this.heightOverride = false;
        this.billboard = Billboard.fromString(plugin.getConfigManager().getConfig().getString("general.defaults.billboard", "center"));
        this.billboardOverride = false;
        this.permission = null;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    @Override
    public LineType getType() {
        return LineType.TEXT;
    }

    @Override
    public Vector getOffset() {
        return offset.clone();
    }

    @Override
    public void setOffset(Vector offset) {
        this.offset = offset == null ? new Vector() : offset.clone();
    }

    @Override
    public double getHeight() {
        return height;
    }

    @Override
    public void setHeight(double height) {
        this.height = Math.max(0.0D, height);
        this.heightOverride = true;
    }

    @Override
    public void clearHeight() {
        this.height = 0.0D;
        this.heightOverride = false;
    }

    @Override
    public boolean hasHeightOverride() {
        return heightOverride;
    }

    @Override
    public Billboard getBillboard() {
        return billboard;
    }

    @Override
    public void setBillboard(Billboard billboard) {
        this.billboard = billboard == null ? Billboard.CENTER : billboard;
        this.billboardOverride = true;
    }

    @Override
    public boolean hasBillboardOverride() {
        return billboardOverride;
    }

    @Override
    public String getPermission() {
        return permission;
    }

    @Override
    public void setPermission(String permission) {
        this.permission = permission;
    }

    @Override
    public boolean canView(Player player) {
        return permission == null || permission.isEmpty() || player.hasPermission(permission);
    }

    @Override
    public void spawn(Player player, Hologram hologram, int pageIndex, int lineIndex, Location location, Billboard billboard) {
        Component parsedContent = MiniMessageUtil.parse(content, player, hologram.getId());
        HologramPacketManager.spawnTextLine(player, hologram, pageIndex, lineIndex, location, parsedContent, billboard);
    }

    @Override
    public void update(Player player, Hologram hologram, int pageIndex, int lineIndex, Location location, Billboard billboard) {
        Component parsedContent = MiniMessageUtil.parse(content, player, hologram.getId());
        HologramPacketManager.updateTextLine(player, hologram, pageIndex, lineIndex, location, parsedContent, billboard);
    }

    @Override
    public void destroy(Player player, String hologramId, int pageIndex, int lineIndex) {
        HologramPacketManager.destroyLine(player, hologramId, pageIndex, lineIndex);
    }

    @Override
    public void serialize(ConfigurationSection section) {
        section.set("type", getType().name());
        section.set("content", content);
        ConfigurationSection offsetSection = section.createSection("offset");
        offsetSection.set("x", offset.getX());
        offsetSection.set("y", offset.getY());
        offsetSection.set("z", offset.getZ());
        section.set("height", heightOverride ? height : null);
        if (billboardOverride) {
            section.set("billboard", billboard.name());
        } else {
            section.set("billboard", null);
        }
        if (permission != null && !permission.isEmpty()) {
            section.set("permission", permission);
        } else {
            section.set("permission", null);
        }
    }

    public static TextLineImpl deserialize(ConfigurationSection section, AxoHologram plugin) {
        String content = section.getString("content", "");
        TextLineImpl line = new TextLineImpl(content, plugin);

        Vector offset = readOffset(section);
        if (offset != null) {
            line.setOffset(offset);
        }
        if (section.contains("height") || section.contains("line-height")) {
            line.setHeight(section.contains("height")
                    ? section.getDouble("height", 0.0D)
                    : section.getDouble("line-height", 0.0D));
        }
        if (section.contains("billboard")) {
            line.setBillboard(Billboard.fromString(section.getString("billboard")));
        }
        line.setPermission(section.getString("permission"));
        return line;
    }

    private static Vector readOffset(ConfigurationSection section) {
        ConfigurationSection offsetSection = section.getConfigurationSection("offset");
        if (offsetSection != null) {
            return new Vector(
                    offsetSection.getDouble("x", 0.0D),
                    offsetSection.getDouble("y", 0.0D),
                    offsetSection.getDouble("z", 0.0D)
            );
        }

        Object rawOffset = section.getValues(false).get("offset");
        if (rawOffset instanceof Map<?, ?> offsetMap) {
            return new Vector(
                    readDouble(offsetMap.get("x"), 0.0D),
                    readDouble(offsetMap.get("y"), 0.0D),
                    readDouble(offsetMap.get("z"), 0.0D)
            );
        }

        Map<String, Object> values = section.getValues(false);
        if (values.containsKey("offset.x") || values.containsKey("offset.y") || values.containsKey("offset.z")) {
            return new Vector(
                    readDouble(values.get("offset.x"), 0.0D),
                    readDouble(values.get("offset.y"), 0.0D),
                    readDouble(values.get("offset.z"), 0.0D)
            );
        }
        return null;
    }

    private static double readDouble(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text) {
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }
}
