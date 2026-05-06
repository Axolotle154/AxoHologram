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

public class BlockLineImpl implements HologramLine {

    private final AxoHologram plugin;
    private volatile BlockData blockData;
    private volatile Vector offset;
    private volatile Billboard billboard;
    private volatile boolean billboardOverride;
    private volatile String permission;

    public BlockLineImpl(String content, AxoHologram plugin) {
        this.plugin = plugin;
        this.blockData = parseBlockData(content);
        this.offset = new Vector(0, 0, 0);
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
        HologramPacketManager.spawnBlockLine(player, hologram, pageIndex, lineIndex, location, blockData, billboard);
    }

    @Override
    public void update(Player player, Hologram hologram, int pageIndex, int lineIndex, Location location, Billboard billboard) {
        HologramPacketManager.updateBlockLine(player, hologram, pageIndex, lineIndex, location, blockData, billboard);
    }

    @Override
    public void destroy(Player player, String hologramId, int pageIndex, int lineIndex) {
        HologramPacketManager.destroyLine(player, hologramId, pageIndex, lineIndex);
    }

    @Override
    public void serialize(ConfigurationSection section) {
        section.set("type", getType().name());
        section.set("content", blockData.getAsString());
        section.set("offset.x", offset.getX());
        section.set("offset.y", offset.getY());
        section.set("offset.z", offset.getZ());
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

        if (section.isConfigurationSection("offset") || section.contains("offset.x")) {
            double x = section.getDouble("offset.x", 0);
            double y = section.getDouble("offset.y", 0);
            double z = section.getDouble("offset.z", 0);
            line.setOffset(new Vector(x, y, z));
        }
        if (section.contains("billboard")) {
            line.setBillboard(Billboard.fromString(section.getString("billboard")));
        }
        line.setPermission(section.getString("permission"));
        return line;
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
}
