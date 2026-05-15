package org.axostudio.axohologram.hologram.line.impl;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.axostudio.axohologram.AxoHologram;
import org.axostudio.axohologram.hologram.Hologram;
import org.axostudio.axohologram.hologram.billboard.Billboard;
import org.axostudio.axohologram.hologram.line.HologramLine;
import org.axostudio.axohologram.hologram.line.LineType;
import org.axostudio.axohologram.packet.HologramPacketManager;
import org.axostudio.axohologram.util.MiniMessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.util.Vector;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ItemLineImpl implements HologramLine {

    private static final int HEAD_CACHE_LIMIT = 128;
    private static final Pattern PLAYER_HEAD_PATTERN = Pattern.compile("(?i)^(?:minecraft:)?player_head\\s*\\((.*)\\)\\s*$");
    private static final Pattern UUID_WITHOUT_DASHES_PATTERN = Pattern.compile("^[0-9a-fA-F]{32}$");
    private static final Pattern TEXTURE_HASH_PATTERN = Pattern.compile("^[0-9a-fA-F]{40,}$");
    private static final Pattern PLAYER_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{3,16}$");

    private final AxoHologram plugin;
    private final Map<String, ItemStack> resolvedHeadCache = new ConcurrentHashMap<>();
    private final Set<String> warnedHeadValues = ConcurrentHashMap.newKeySet();
    private volatile String content;
    private volatile ItemStack itemStack;
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

    public ItemLineImpl(String content, AxoHologram plugin) {
        this.plugin = plugin;
        this.content = normalizeContent(content);
        this.itemStack = parseItemStack(this.content, null, null);
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
        return content;
    }

    public void setContent(String content) {
        String normalizedContent = normalizeContent(content);
        ItemStack parsedItemStack = parseItemStack(normalizedContent, null, null);
        this.content = normalizedContent;
        this.itemStack = parsedItemStack;
        clearResolutionState();
    }

    public ItemStack getItemStack() {
        return itemStack.clone();
    }

    public void setItemStack(ItemStack itemStack) {
        this.itemStack = normalizeItemStack(itemStack);
        this.content = this.itemStack.getType().name();
        clearResolutionState();
    }

    public boolean requiresDynamicRefresh() {
        String headIdentifier = extractPlayerHeadIdentifier(content);
        return headIdentifier != null && MiniMessageUtil.hasDynamicPlaceholders(headIdentifier);
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
        HologramPacketManager.spawnItemLine(player, hologram, pageIndex, lineIndex, location, this, resolveItemStack(player, hologram, lineIndex), billboard);
    }

    @Override
    public void update(Player player, Hologram hologram, int pageIndex, int lineIndex, Location location, Billboard billboard) {
        HologramPacketManager.updateItemLine(player, hologram, pageIndex, lineIndex, location, this, resolveItemStack(player, hologram, lineIndex), billboard);
    }

    @Override
    public void destroy(Player player, String hologramId, int pageIndex, int lineIndex) {
        HologramPacketManager.destroyLine(player, hologramId, pageIndex, lineIndex);
    }

    @Override
    public void serialize(ConfigurationSection section) {
        section.set("type", getType().name());
        section.set("content", content);
        section.set("item", isSimpleItemStack(itemStack) ? null : itemStack);
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

    public static ItemLineImpl deserialize(ConfigurationSection section, AxoHologram plugin) {
        ItemStack stack = readItemStack(section);
        String content = section.getString("content", stack == null ? "" : stack.getType().name());
        ItemLineImpl line = new ItemLineImpl(content, plugin);
        if (stack != null) {
            line.itemStack = normalizeItemStack(stack);
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

    private ItemStack resolveItemStack(Player player, Hologram hologram, int lineIndex) {
        if (!requiresDynamicRefresh()) {
            return getItemStack();
        }

        try {
            return parseItemStack(content, player, hologram);
        } catch (IllegalArgumentException exception) {
            warnInvalidDynamicHead(hologram, lineIndex, exception.getMessage());
            return itemStack.clone();
        }
    }

    private void warnInvalidDynamicHead(Hologram hologram, int lineIndex, String reason) {
        String key = content + "|" + reason;
        if (!warnedHeadValues.add(key)) {
            return;
        }

        String hologramId = hologram == null ? "unknown" : hologram.getId();
        int displayLine = lineIndex + 1;
        plugin.getLogger().warning("Invalid PLAYER_HEAD placeholder result in hologram '" + hologramId
                + "', line " + displayLine + ": " + reason);
    }

    private void clearResolutionState() {
        resolvedHeadCache.clear();
        warnedHeadValues.clear();
    }

    private ItemStack parseItemStack(String rawContent, Player player, Hologram hologram) {
        String itemContent = stripItemPrefix(rawContent);
        String headIdentifier = extractPlayerHeadIdentifier(itemContent);
        if (headIdentifier != null) {
            return createPlayerHead(headIdentifier, player, hologram);
        }
        return new ItemStack(parseMaterial(itemContent));
    }

    private ItemStack createPlayerHead(String rawIdentifier, Player player, Hologram hologram) {
        if (rawIdentifier == null || rawIdentifier.isBlank()) {
            throw new IllegalArgumentException("PLAYER_HEAD identifier cannot be empty.");
        }
        if (player == null && MiniMessageUtil.hasDynamicPlaceholders(rawIdentifier)) {
            return new ItemStack(Material.PLAYER_HEAD);
        }

        String resolvedIdentifier = player == null
                ? rawIdentifier.trim()
                : MiniMessageUtil.resolvePlaceholders(rawIdentifier, player, hologram == null ? null : hologram.getId()).trim();
        if (resolvedIdentifier.isEmpty()) {
            throw new IllegalArgumentException("PLAYER_HEAD placeholder returned an empty value.");
        }

        if (resolvedHeadCache.size() > HEAD_CACHE_LIMIT) {
            resolvedHeadCache.clear();
        }
        return resolvedHeadCache.computeIfAbsent(resolvedIdentifier, value -> createResolvedPlayerHead(value, player)).clone();
    }

    private static ItemStack createResolvedPlayerHead(String identifier, Player viewer) {
        ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
        if (!(stack.getItemMeta() instanceof SkullMeta skullMeta)) {
            return stack;
        }

        String textureValue = explicitTextureValue(identifier);
        if (textureValue != null) {
            applyTextureValue(skullMeta, textureValue);
            stack.setItemMeta(skullMeta);
            return stack;
        }

        UUID uuid = parseUuid(identifier);
        if (uuid != null) {
            skullMeta.setPlayerProfile(resolveUuidProfile(uuid, viewer));
            stack.setItemMeta(skullMeta);
            return stack;
        }

        if (PLAYER_NAME_PATTERN.matcher(identifier).matches()) {
            skullMeta.setPlayerProfile(resolveNameProfile(identifier, viewer));
            stack.setItemMeta(skullMeta);
            return stack;
        }

        textureValue = implicitTextureValue(identifier);
        if (textureValue != null) {
            applyTextureValue(skullMeta, textureValue);
            stack.setItemMeta(skullMeta);
            return stack;
        }

        throw new IllegalArgumentException("Unsupported PLAYER_HEAD identifier: " + identifier);
    }

    private static void applyTextureValue(SkullMeta skullMeta, String textureValue) {
        PlayerProfile profile = Bukkit.createProfile(UUID.nameUUIDFromBytes(("axohologram:" + textureValue).getBytes(StandardCharsets.UTF_8)));
        profile.setProperty(new ProfileProperty("textures", textureValue));
        skullMeta.setPlayerProfile(profile);
    }

    private static PlayerProfile resolveUuidProfile(UUID uuid, Player viewer) {
        if (viewer != null && viewer.getUniqueId().equals(uuid)) {
            return viewer.getPlayerProfile();
        }

        Player onlinePlayer = Bukkit.getPlayer(uuid);
        if (onlinePlayer != null) {
            return onlinePlayer.getPlayerProfile();
        }

        PlayerProfile profile = Bukkit.createProfile(uuid);
        profile.completeFromCache(false, false);
        return profile;
    }

    private static PlayerProfile resolveNameProfile(String name, Player viewer) {
        if (viewer != null && viewer.getName().equalsIgnoreCase(name)) {
            return viewer.getPlayerProfile();
        }

        Player onlinePlayer = Bukkit.getPlayerExact(name);
        if (onlinePlayer != null) {
            return onlinePlayer.getPlayerProfile();
        }

        PlayerProfile profile = Bukkit.createProfile(name);
        profile.completeFromCache(false, false);
        return profile;
    }

    private static String explicitTextureValue(String identifier) {
        String trimmed = identifier.trim();
        for (String prefix : new String[]{"base64:", "value:", "texture:", "textures:", "skin:"}) {
            if (trimmed.regionMatches(true, 0, prefix, 0, prefix.length())) {
                return normalizeTextureValue(trimmed.substring(prefix.length()).trim());
            }
        }
        if (trimmed.regionMatches(true, 0, "url:", 0, "url:".length())) {
            return textureUrlToValue(trimmed.substring("url:".length()).trim());
        }
        return null;
    }

    private static String implicitTextureValue(String identifier) {
        String trimmed = identifier.trim();
        if (isTextureUrl(trimmed)) {
            return textureUrlToValue(trimmed);
        }
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return encodeTextureJson(trimmed);
        }
        if (isBase64TextureValue(trimmed)) {
            return trimmed;
        }
        if (TEXTURE_HASH_PATTERN.matcher(trimmed).matches()) {
            return textureUrlToValue("https://textures.minecraft.net/texture/" + trimmed);
        }
        return null;
    }

    private static String normalizeTextureValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("PLAYER_HEAD texture value cannot be empty.");
        }

        String trimmed = value.trim();
        if (isTextureUrl(trimmed)) {
            return textureUrlToValue(trimmed);
        }
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return encodeTextureJson(trimmed);
        }
        if (TEXTURE_HASH_PATTERN.matcher(trimmed).matches()) {
            return textureUrlToValue("https://textures.minecraft.net/texture/" + trimmed);
        }
        if (!isBase64TextureValue(trimmed)) {
            throw new IllegalArgumentException("Invalid PLAYER_HEAD texture value.");
        }
        return trimmed;
    }

    private static String textureUrlToValue(String rawUrl) {
        String url = rawUrl.startsWith("http://") || rawUrl.startsWith("https://")
                ? rawUrl
                : "https://" + rawUrl;
        if (!isTextureUrl(url)) {
            throw new IllegalArgumentException("Invalid PLAYER_HEAD texture URL.");
        }
        return encodeTextureJson("{\"textures\":{\"SKIN\":{\"url\":\"" + url + "\"}}}");
    }

    private static String encodeTextureJson(String json) {
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean isBase64TextureValue(String value) {
        try {
            String decoded = new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
            return decoded.contains("\"textures\"") && decoded.contains("\"skin\"");
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean isTextureUrl(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return normalized.startsWith("http://textures.minecraft.net/texture/")
                || normalized.startsWith("https://textures.minecraft.net/texture/")
                || normalized.startsWith("textures.minecraft.net/texture/");
    }

    private static UUID parseUuid(String value) {
        String trimmed = value.trim();
        try {
            if (UUID_WITHOUT_DASHES_PATTERN.matcher(trimmed).matches()) {
                trimmed = trimmed.substring(0, 8) + "-"
                        + trimmed.substring(8, 12) + "-"
                        + trimmed.substring(12, 16) + "-"
                        + trimmed.substring(16, 20) + "-"
                        + trimmed.substring(20);
            }
            return UUID.fromString(trimmed);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static String extractPlayerHeadIdentifier(String rawContent) {
        String itemContent = stripItemPrefix(rawContent);
        if (itemContent == null) {
            return null;
        }
        Matcher matcher = PLAYER_HEAD_PATTERN.matcher(itemContent);
        return matcher.matches() ? matcher.group(1).trim() : null;
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

    private static Material parseMaterial(String content) {
        String normalized = stripItemPrefix(content);
        if (normalized == null || normalized.isBlank()) {
            throw new IllegalArgumentException("Item line content cannot be empty.");
        }

        normalized = normalized.regionMatches(true, 0, "minecraft:", 0, "minecraft:".length())
                ? normalized.substring("minecraft:".length())
                : normalized;
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

    private static String normalizeContent(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Item line content cannot be empty.");
        }
        return content.trim();
    }

    private static float normalizeScale(float scale) {
        return scale <= 0.0F ? 1.0F : scale;
    }

    private static String stripItemPrefix(String content) {
        if (content == null) {
            return null;
        }

        String trimmed = content.trim();
        for (String prefix : new String[]{"#item:", "item:", "#icon:", "icon:", "[item]:", "[icon]:", "[item]", "[icon]"}) {
            if (trimmed.regionMatches(true, 0, prefix, 0, prefix.length())) {
                return trimmed.substring(prefix.length()).trim();
            }
        }
        return trimmed;
    }
}
