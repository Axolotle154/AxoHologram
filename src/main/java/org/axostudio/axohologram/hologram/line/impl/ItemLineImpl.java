package org.axostudio.axohologram.hologram.line.impl;

import org.axostudio.axohologram.AxoHologram;
import org.axostudio.axohologram.hologram.Hologram;
import org.axostudio.axohologram.hologram.billboard.Billboard;
import org.axostudio.axohologram.hologram.line.HologramLine;
import org.axostudio.axohologram.hologram.line.LineType;
import org.axostudio.axohologram.packet.HologramPacketManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class ItemLineImpl implements HologramLine {

    private final AxoHologram plugin;
    private volatile ItemStack itemStack;
    private volatile Vector offset;
    private volatile double height;
    private volatile boolean heightOverride;
    private volatile Billboard billboard;
    private volatile boolean billboardOverride;
    private volatile String permission;

    public ItemLineImpl(String content, AxoHologram plugin) {
        this.plugin = plugin;
        this.itemStack = new ItemStack(parseMaterial(content));
        this.offset = new Vector(0, 0, 0);
        this.height = 0.0D;
        this.heightOverride = false;
        this.billboard = Billboard.fromString(plugin.getConfigManager().getConfig().getString("general.defaults.billboard", "center"));
        this.billboardOverride = false;
        this.permission = null;
    }

    public String getContent() {
        return itemStack.getType().name();
    }

    public void setContent(String content) {
        this.itemStack = new ItemStack(parseMaterial(content));
    }

    public ItemStack getItemStack() {
        return itemStack.clone();
    }

    public void setItemStack(ItemStack itemStack) {
        this.itemStack = normalizeItemStack(itemStack);
    }

    @Override
    public LineType getType() {
        return LineType.ITEM;
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
        HologramPacketManager.spawnItemLine(player, hologram, pageIndex, lineIndex, location, getItemStack(), billboard);
    }

    @Override
    public void update(Player player, Hologram hologram, int pageIndex, int lineIndex, Location location, Billboard billboard) {
        HologramPacketManager.updateItemLine(player, hologram, pageIndex, lineIndex, location, getItemStack(), billboard);
    }

    @Override
    public void destroy(Player player, String hologramId, int pageIndex, int lineIndex) {
        HologramPacketManager.destroyLine(player, hologramId, pageIndex, lineIndex);
    }

    @Override
    public void serialize(ConfigurationSection section) {
        section.set("type", getType().name());
        section.set("content", itemStack.getType().name());
        section.set("item", isSimpleItemStack(itemStack) ? null : itemStack);
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

    public static ItemLineImpl deserialize(ConfigurationSection section, AxoHologram plugin) {
        ItemStack stack = readItemStack(section);
        String content = stack == null ? section.getString("content", "") : stack.getType().name();
        ItemLineImpl line = new ItemLineImpl(content, plugin);
        if (stack != null) {
            line.setItemStack(stack);
        }

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

    private static ItemStack readItemStack(ConfigurationSection section) {
        ItemStack directStack = section.getItemStack("item");
        if (directStack != null) {
            return normalizeItemStack(directStack);
        }

        Object rawItem = section.get("item");
        if (rawItem instanceof ItemStack itemStack) {
            return normalizeItemStack(itemStack);
        }
        if (rawItem instanceof Map<?, ?> map) {
            return readItemStackMap(map);
        }

        ConfigurationSection itemSection = section.getConfigurationSection("item");
        if (itemSection != null) {
            return readItemStackMap(itemSection.getValues(false));
        }
        return null;
    }

    private static ItemStack readItemStackMap(Map<?, ?> map) {
        try {
            Map<String, Object> serialized = new LinkedHashMap<>();
            map.forEach((key, value) -> serialized.put(String.valueOf(key), value));
            ItemStack stack = ItemStack.deserialize(serialized);
            return normalizeItemStack(stack);
        } catch (RuntimeException exception) {
            Object id = firstPresent(map, "id", "type", "material");
            Material material = parseMaterial(id == null ? null : String.valueOf(id));
            int amount = Math.max(1, (int) readDouble(firstPresent(map, "count", "amount"), 1.0D));
            return new ItemStack(material, amount);
        }
    }

    private static Object firstPresent(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            if (map.containsKey(key)) {
                return map.get(key);
            }
        }
        return null;
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

    private static Material parseMaterial(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Item line content cannot be empty.");
        }

        String normalized = content.startsWith("minecraft:") ? content.substring("minecraft:".length()) : content;
        Material material = Material.matchMaterial(normalized);
        if (material == null) {
            material = Material.matchMaterial(normalized.toUpperCase(Locale.ROOT));
        }
        if (material == null || material.isAir() || !material.isItem()) {
            throw new IllegalArgumentException("Invalid item material: " + content);
        }
        return material;
    }

    private static ItemStack normalizeItemStack(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir() || !itemStack.getType().isItem()) {
            throw new IllegalArgumentException("Invalid item stack.");
        }
        return itemStack.clone();
    }

    private static boolean isSimpleItemStack(ItemStack itemStack) {
        return itemStack.getAmount() == 1 && !itemStack.hasItemMeta();
    }
}
