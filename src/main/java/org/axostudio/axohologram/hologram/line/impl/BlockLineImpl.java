package org.axostudio.axohologram.hologram.line.impl;

import org.axostudio.axohologram.AxoHologram;
import org.axostudio.axohologram.hologram.Hologram;
import org.axostudio.axohologram.hologram.billboard.Billboard;
import org.axostudio.axohologram.hologram.line.HologramLine;
import org.axostudio.axohologram.hologram.line.LineType;
import org.axostudio.axohologram.packet.HologramPacketManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Map;

public class BlockLineImpl implements HologramLine {

    private final AxoHologram plugin;
    private volatile BlockData blockData;
    private volatile Vector offset;
    private volatile double height;
    private volatile boolean heightOverride;
    private volatile float scaleX;
    private volatile float scaleY;
    private volatile float scaleZ;
    private volatile boolean scaleOverride;
    private volatile Billboard billboard;
    private volatile boolean billboardOverride;
    private volatile String permission;

    public BlockLineImpl(String content, AxoHologram plugin) {
        this.plugin = plugin;
        this.blockData = parseBlockData(content);
        this.offset = new Vector(0, 0, 0);
        this.height = 0.0D;
        this.heightOverride = false;
        this.scaleX = 1.0F;
        this.scaleY = 1.0F;
        this.scaleZ = 1.0F;
        this.scaleOverride = false;
        this.billboard = Billboard.fromString(plugin.getConfigManager().getConfig().getString("general.defaults.billboard", "center"));
        this.billboardOverride = false;
        this.permission = null;
    }

    public String getContent() {
        return blockData.getAsString();
    }

    public void setContent(String content) {
        this.blockData = parseBlockData(content);
    }

    @Override
    public LineType getType() {
        return LineType.BLOCK;
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

    public float getScaleX() {
        return scaleX;
    }

    public float getScaleY() {
        return scaleY;
    }

    public float getScaleZ() {
        return scaleZ;
    }

    public void setScale(float scale) {
        setScale(scale, scale, scale);
    }

    public void setScale(float scaleX, float scaleY, float scaleZ) {
        this.scaleX = normalizeScale(scaleX);
        this.scaleY = normalizeScale(scaleY);
        this.scaleZ = normalizeScale(scaleZ);
        this.scaleOverride = true;
    }

    public void clearScale() {
        this.scaleX = 1.0F;
        this.scaleY = 1.0F;
        this.scaleZ = 1.0F;
        this.scaleOverride = false;
    }

    public boolean hasScaleOverride() {
        return scaleOverride;
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
        HologramPacketManager.spawnBlockLine(player, hologram, pageIndex, lineIndex, location, this, blockData, billboard);
    }

    @Override
    public void update(Player player, Hologram hologram, int pageIndex, int lineIndex, Location location, Billboard billboard) {
        HologramPacketManager.updateBlockLine(player, hologram, pageIndex, lineIndex, location, this, blockData, billboard);
    }

    @Override
    public void destroy(Player player, String hologramId, int pageIndex, int lineIndex) {
        HologramPacketManager.destroyLine(player, hologramId, pageIndex, lineIndex);
    }

    @Override
    public void serialize(ConfigurationSection section) {
        section.set("type", getType().name());
        section.set("content", blockData.getAsString());
        ConfigurationSection offsetSection = section.createSection("offset");
        offsetSection.set("x", offset.getX());
        offsetSection.set("y", offset.getY());
        offsetSection.set("z", offset.getZ());
        section.set("height", heightOverride ? height : null);
        if (scaleOverride) {
            ConfigurationSection scaleSection = section.createSection("scale");
            scaleSection.set("x", scaleX);
            scaleSection.set("y", scaleY);
            scaleSection.set("z", scaleZ);
        } else {
            section.set("scale", null);
        }
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

    public static BlockLineImpl deserialize(ConfigurationSection section, AxoHologram plugin) {
        String content = section.getString("content", "");
        BlockLineImpl line = new BlockLineImpl(content, plugin);

        Vector offset = readOffset(section);
        if (offset != null) {
            line.setOffset(offset);
        }
        if (section.contains("height") || section.contains("line-height")) {
            line.setHeight(section.contains("height")
                    ? section.getDouble("height", 0.0D)
                    : section.getDouble("line-height", 0.0D));
        }
        float[] scale = readScale(section);
        if (scale != null) {
            line.setScale(scale[0], scale[1], scale[2]);
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

    private static float[] readScale(ConfigurationSection section) {
        ConfigurationSection scaleSection = section.getConfigurationSection("scale");
        if (scaleSection != null) {
            return new float[]{
                    (float) scaleSection.getDouble("x", 1.0D),
                    (float) scaleSection.getDouble("y", 1.0D),
                    (float) scaleSection.getDouble("z", 1.0D)
            };
        }

        Object rawScale = section.get("scale");
        if (rawScale instanceof Number number) {
            float scale = number.floatValue();
            return new float[]{scale, scale, scale};
        }
        if (rawScale instanceof String text) {
            try {
                float scale = Float.parseFloat(text);
                return new float[]{scale, scale, scale};
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        if (rawScale instanceof Map<?, ?> scaleMap) {
            return new float[]{
                    (float) readDouble(scaleMap.get("x"), 1.0D),
                    (float) readDouble(scaleMap.get("y"), 1.0D),
                    (float) readDouble(scaleMap.get("z"), 1.0D)
            };
        }

        Map<String, Object> values = section.getValues(false);
        if (values.containsKey("scale.x") || values.containsKey("scale.y") || values.containsKey("scale.z")) {
            return new float[]{
                    (float) readDouble(values.get("scale.x"), 1.0D),
                    (float) readDouble(values.get("scale.y"), 1.0D),
                    (float) readDouble(values.get("scale.z"), 1.0D)
            };
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

    private static BlockData parseBlockData(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Block line content cannot be empty.");
        }

        BlockData blockData = Bukkit.createBlockData(content);
        if (blockData.getMaterial().isAir()) {
            throw new IllegalArgumentException("Invalid block data: " + content);
        }
        return blockData;
    }

    private static float normalizeScale(float scale) {
        return scale <= 0.0F ? 1.0F : scale;
    }
}
