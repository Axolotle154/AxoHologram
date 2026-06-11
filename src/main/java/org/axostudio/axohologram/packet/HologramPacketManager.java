package org.axostudio.axohologram.packet;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.axostudio.axohologram.AxoHologram;
import org.axostudio.axohologram.animation.RenderedDisplayAnimation;
import org.axostudio.axohologram.hologram.Hologram;
import org.axostudio.axohologram.hologram.action.HologramClickType;
import org.axostudio.axohologram.hologram.billboard.Billboard;
import org.axostudio.axohologram.hologram.line.HologramLine;
import org.axostudio.axohologram.hologram.line.impl.BlockLineImpl;
import org.axostudio.axohologram.hologram.line.impl.ItemLineImpl;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class HologramPacketManager {

    private static final Map<UUID, Map<LineKey, UUID>> PLAYER_LINE_ENTITIES = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<LineKey, UUID>> PLAYER_LINE_INTERACTIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<LineKey, TextLineState>> PLAYER_LINE_TEXTS = new ConcurrentHashMap<>();
    private static final Map<UUID, TrackedDisplay> TRACKED_DISPLAYS = new ConcurrentHashMap<>();
    private static final Map<UUID, DisplayState> DISPLAY_STATES = new ConcurrentHashMap<>();
    private static final Map<UUID, TextStyleState> TEXT_STYLE_STATES = new ConcurrentHashMap<>();
    private static final Set<UUID> TRACKED_ENTITY_IDS = ConcurrentHashMap.newKeySet();
    private static final PlainTextComponentSerializer PLAIN_TEXT = PlainTextComponentSerializer.plainText();

    private HologramPacketManager() {
    }

    private record LineKey(String hologramId, int pageIndex, int lineIndex) {
    }

    private record TextLineState(Component text, float widthScale, float heightScale, int lineWidth, float interactionWidth, float interactionHeight) {
    }

    private record InteractionSize(float width, float height) {
    }

    private record DisplayState(
            int interpolationDuration,
            Display.Billboard billboard,
            float viewRange,
            float shadowRadius,
            float shadowStrength,
            float scaleX,
            float scaleY,
            float scaleZ,
            float rollOffset,
            boolean flipItemModel,
            boolean brightnessEnabled,
            int brightnessBlock,
            int brightnessSky
    ) {
    }

    private record TextStyleState(
            boolean shadowed,
            boolean seeThrough,
            TextDisplay.TextAlignment alignment,
            Color backgroundColor,
            int lineWidth
    ) {
    }

    public record TrackedDisplay(UUID viewerId, String hologramId, int pageIndex, int lineIndex) {
    }

    public static void spawnTextLine(Player player, Hologram hologram, int pageIndex, int lineIndex, Location location, Component text, Billboard billboard) {
        if (player == null) {
            return;
        }
        spawnTextLine(player.getUniqueId(), hologram, pageIndex, lineIndex, location, text, billboard);
    }

    public static void spawnTextLine(UUID viewerId, Hologram hologram, int pageIndex, int lineIndex, Location location, Component text, Billboard billboard) {
        if (location == null || location.getWorld() == null) {
            return;
        }

        UUID trackedEntityId = playerLines(viewerId).get(new LineKey(hologram.getId(), pageIndex, lineIndex));
        if (trackedEntityId != null && Bukkit.getEntity(trackedEntityId) instanceof TextDisplay display && display.isValid()) {
            updateTextLine(viewerId, hologram, pageIndex, lineIndex, location, text, billboard);
            return;
        }

        Component displayText = normalizeText(text);
        LineKey key = new LineKey(hologram.getId(), pageIndex, lineIndex);
        TextLineState textState = createTextLineState(displayText, hologram);
        Location spawnLocation = location.clone();
        destroyLine(viewerId, hologram.getId(), pageIndex, lineIndex);
        scheduler().runAtLocation(spawnLocation, () -> {
            if (spawnLocation.getWorld() == null) {
                return;
            }

            RenderedDisplayAnimation renderedAnimation = renderDisplayAnimation(hologram, spawnLocation);
            TextDisplay display = spawnLocation.getWorld().spawn(spawnLocation, TextDisplay.class, entity -> {
                configureDisplay(entity, hologram, null, renderedAnimation, billboard);
                applyTextStyle(entity, hologram);
                entity.text(displayText);
            });

            if (!trackLine(viewerId, hologram.getId(), pageIndex, lineIndex, display)) {
                return;
            }
            playerTexts(viewerId).put(key, textState);
            syncInteraction(viewerId, hologram, pageIndex, lineIndex, renderedAnimation.location(),
                    textState.interactionWidth(),
                    textState.interactionHeight());
        });
    }

    public static void updateTextLine(Player player, Hologram hologram, int pageIndex, int lineIndex, Location location, Component text, Billboard billboard) {
        if (player == null) {
            return;
        }
        updateTextLine(player.getUniqueId(), hologram, pageIndex, lineIndex, location, text, billboard);
    }

    public static void updateTextLine(UUID viewerId, Hologram hologram, int pageIndex, int lineIndex, Location location, Component text, Billboard billboard) {
        LineKey key = new LineKey(hologram.getId(), pageIndex, lineIndex);
        UUID entityId = playerLines(viewerId).get(key);
        if (entityId == null) {
            spawnTextLine(viewerId, hologram, pageIndex, lineIndex, location, text, billboard);
            return;
        }

        if (!(Bukkit.getEntity(entityId) instanceof TextDisplay display) || !display.isValid()) {
            destroyLine(viewerId, hologram.getId(), pageIndex, lineIndex);
            spawnTextLine(viewerId, hologram, pageIndex, lineIndex, location, text, billboard);
            return;
        }

        Component displayText = normalizeText(text);
        TextLineState previousTextState = getTextLineState(viewerId, key);
        boolean textChanged = previousTextState == null || !Objects.equals(previousTextState.text(), displayText);
        TextRenderSettings textRenderSettings = resolveTextRenderSettings(hologram);
        boolean renderSettingsChanged = previousTextState == null
                || Float.compare(previousTextState.widthScale(), textRenderSettings.widthScale()) != 0
                || Float.compare(previousTextState.heightScale(), textRenderSettings.heightScale()) != 0
                || previousTextState.lineWidth() != textRenderSettings.lineWidth();
        TextLineState textState = textChanged || renderSettingsChanged
                ? createTextLineState(displayText, hologram)
                : previousTextState;
        Location targetLocation = location.clone();
        if (!scheduler().runAtEntity(display, () -> {
            RenderedDisplayAnimation renderedAnimation = renderDisplayAnimation(hologram, targetLocation);
            updateDisplay(display, hologram, null, renderedAnimation, billboard);
            applyTextStyle(display, hologram);
            if (textChanged) {
                display.text(displayText);
            }
            if (textChanged || renderSettingsChanged) {
                playerTexts(viewerId).put(key, textState);
            }
            syncInteraction(viewerId, hologram, pageIndex, lineIndex, renderedAnimation.location(),
                    textState.interactionWidth(),
                    textState.interactionHeight());
            showEntity(viewerId, display.getUniqueId());
        })) {
            destroyLine(viewerId, hologram.getId(), pageIndex, lineIndex);
            spawnTextLine(viewerId, hologram, pageIndex, lineIndex, location, text, billboard);
        }
    }

    public static void spawnItemLine(Player player, Hologram hologram, int pageIndex, int lineIndex, Location location, ItemLineImpl line, ItemStack itemStack, Billboard billboard) {
        if (player == null) {
            return;
        }
        spawnItemLine(player.getUniqueId(), hologram, pageIndex, lineIndex, location, line, itemStack, billboard);
    }

    public static void spawnItemLine(UUID viewerId, Hologram hologram, int pageIndex, int lineIndex, Location location, ItemLineImpl line, ItemStack itemStack, Billboard billboard) {
        if (location == null || location.getWorld() == null) {
            return;
        }

        UUID trackedEntityId = playerLines(viewerId).get(new LineKey(hologram.getId(), pageIndex, lineIndex));
        if (trackedEntityId != null && Bukkit.getEntity(trackedEntityId) instanceof ItemDisplay display && display.isValid()) {
            updateItemLine(viewerId, hologram, pageIndex, lineIndex, location, line, itemStack, billboard);
            return;
        }

        Location spawnLocation = location.clone();
        ItemStack stack = itemStack.clone();
        destroyLine(viewerId, hologram.getId(), pageIndex, lineIndex);
        scheduler().runAtLocation(spawnLocation, () -> {
            if (spawnLocation.getWorld() == null) {
                return;
            }

            RenderedDisplayAnimation renderedAnimation = renderDisplayAnimation(hologram, spawnLocation);
            ItemDisplay display = spawnLocation.getWorld().spawn(spawnLocation, ItemDisplay.class, entity -> {
                configureItemDisplay(entity, hologram, line, renderedAnimation, stack, billboard);
                entity.setItemStack(stack);
            });

            trackLine(viewerId, hologram.getId(), pageIndex, lineIndex, display);
            syncInteraction(viewerId, hologram, pageIndex, lineIndex, renderedAnimation.location(),
                    resolveDisplayInteractionSize(hologram, line),
                    resolveDisplayInteractionSize(hologram, line));
        });
    }

    public static void updateItemLine(Player player, Hologram hologram, int pageIndex, int lineIndex, Location location, ItemLineImpl line, ItemStack itemStack, Billboard billboard) {
        if (player == null) {
            return;
        }
        updateItemLine(player.getUniqueId(), hologram, pageIndex, lineIndex, location, line, itemStack, billboard);
    }

    public static void updateItemLine(UUID viewerId, Hologram hologram, int pageIndex, int lineIndex, Location location, ItemLineImpl line, ItemStack itemStack, Billboard billboard) {
        LineKey key = new LineKey(hologram.getId(), pageIndex, lineIndex);
        UUID entityId = playerLines(viewerId).get(key);
        if (entityId == null) {
            spawnItemLine(viewerId, hologram, pageIndex, lineIndex, location, line, itemStack, billboard);
            return;
        }

        if (!(Bukkit.getEntity(entityId) instanceof ItemDisplay display) || !display.isValid()) {
            destroyLine(viewerId, hologram.getId(), pageIndex, lineIndex);
            spawnItemLine(viewerId, hologram, pageIndex, lineIndex, location, line, itemStack, billboard);
            return;
        }

        Location targetLocation = location.clone();
        ItemStack stack = itemStack.clone();
        if (!scheduler().runAtEntity(display, () -> {
            RenderedDisplayAnimation renderedAnimation = renderDisplayAnimation(hologram, targetLocation);
            updateItemDisplay(display, hologram, line, renderedAnimation, stack, billboard);
            display.setItemStack(stack);
            syncInteraction(viewerId, hologram, pageIndex, lineIndex, renderedAnimation.location(),
                    resolveDisplayInteractionSize(hologram, line),
                    resolveDisplayInteractionSize(hologram, line));
            showEntity(viewerId, display.getUniqueId());
        })) {
            destroyLine(viewerId, hologram.getId(), pageIndex, lineIndex);
            spawnItemLine(viewerId, hologram, pageIndex, lineIndex, location, line, itemStack, billboard);
        }
    }

    public static void spawnBlockLine(Player player, Hologram hologram, int pageIndex, int lineIndex, Location location, BlockLineImpl line, BlockData blockData, Billboard billboard) {
        if (player == null) {
            return;
        }
        spawnBlockLine(player.getUniqueId(), hologram, pageIndex, lineIndex, location, line, blockData, billboard);
    }

    public static void spawnBlockLine(UUID viewerId, Hologram hologram, int pageIndex, int lineIndex, Location location, BlockLineImpl line, BlockData blockData, Billboard billboard) {
        if (location == null || location.getWorld() == null) {
            return;
        }

        UUID trackedEntityId = playerLines(viewerId).get(new LineKey(hologram.getId(), pageIndex, lineIndex));
        if (trackedEntityId != null && Bukkit.getEntity(trackedEntityId) instanceof BlockDisplay display && display.isValid()) {
            updateBlockLine(viewerId, hologram, pageIndex, lineIndex, location, line, blockData, billboard);
            return;
        }

        Location spawnLocation = location.clone();
        destroyLine(viewerId, hologram.getId(), pageIndex, lineIndex);
        scheduler().runAtLocation(spawnLocation, () -> {
            if (spawnLocation.getWorld() == null) {
                return;
            }

            RenderedDisplayAnimation renderedAnimation = renderDisplayAnimation(hologram, spawnLocation);
            BlockDisplay display = spawnLocation.getWorld().spawn(spawnLocation, BlockDisplay.class, entity -> {
                configureDisplay(entity, hologram, line, renderedAnimation, billboard);
                entity.setBlock(blockData);
            });

            trackLine(viewerId, hologram.getId(), pageIndex, lineIndex, display);
            syncInteraction(viewerId, hologram, pageIndex, lineIndex, renderedAnimation.location(),
                    resolveDisplayInteractionSize(hologram, line),
                    resolveDisplayInteractionSize(hologram, line));
        });
    }

    public static void updateBlockLine(Player player, Hologram hologram, int pageIndex, int lineIndex, Location location, BlockLineImpl line, BlockData blockData, Billboard billboard) {
        if (player == null) {
            return;
        }
        updateBlockLine(player.getUniqueId(), hologram, pageIndex, lineIndex, location, line, blockData, billboard);
    }

    public static void updateBlockLine(UUID viewerId, Hologram hologram, int pageIndex, int lineIndex, Location location, BlockLineImpl line, BlockData blockData, Billboard billboard) {
        LineKey key = new LineKey(hologram.getId(), pageIndex, lineIndex);
        UUID entityId = playerLines(viewerId).get(key);
        if (entityId == null) {
            spawnBlockLine(viewerId, hologram, pageIndex, lineIndex, location, line, blockData, billboard);
            return;
        }

        if (!(Bukkit.getEntity(entityId) instanceof BlockDisplay display) || !display.isValid()) {
            destroyLine(viewerId, hologram.getId(), pageIndex, lineIndex);
            spawnBlockLine(viewerId, hologram, pageIndex, lineIndex, location, line, blockData, billboard);
            return;
        }

        Location targetLocation = location.clone();
        if (!scheduler().runAtEntity(display, () -> {
            RenderedDisplayAnimation renderedAnimation = renderDisplayAnimation(hologram, targetLocation);
            updateDisplay(display, hologram, line, renderedAnimation, billboard);
            display.setBlock(blockData);
            syncInteraction(viewerId, hologram, pageIndex, lineIndex, renderedAnimation.location(),
                    resolveDisplayInteractionSize(hologram, line),
                    resolveDisplayInteractionSize(hologram, line));
            showEntity(viewerId, display.getUniqueId());
        })) {
            destroyLine(viewerId, hologram.getId(), pageIndex, lineIndex);
            spawnBlockLine(viewerId, hologram, pageIndex, lineIndex, location, line, blockData, billboard);
        }
    }

    public static boolean updateLineDisplayState(Player player, Hologram hologram, int pageIndex, int lineIndex, Location location, Billboard billboard, HologramLine line) {
        if (player == null) {
            return false;
        }
        return updateLineDisplayState(player.getUniqueId(), hologram, pageIndex, lineIndex, location, billboard, line);
    }

    public static boolean updateLineDisplayState(UUID viewerId, Hologram hologram, int pageIndex, int lineIndex, Location location, Billboard billboard, HologramLine line) {
        if (viewerId == null || hologram == null || location == null || location.getWorld() == null) {
            return false;
        }

        Map<LineKey, UUID> lines = PLAYER_LINE_ENTITIES.get(viewerId);
        if (lines == null) {
            return false;
        }

        LineKey key = new LineKey(hologram.getId(), pageIndex, lineIndex);
        UUID entityId = lines.get(key);
        if (entityId == null) {
            return false;
        }

        if (!(Bukkit.getEntity(entityId) instanceof Display display) || !display.isValid()) {
            destroyLine(viewerId, hologram.getId(), pageIndex, lineIndex);
            return false;
        }

        Location targetLocation = location.clone();
        InteractionSize interactionSize = resolveExistingInteractionSize(viewerId, key, display, hologram, line);
        if (!scheduler().runAtEntity(display, () -> {
            RenderedDisplayAnimation renderedAnimation = renderDisplayAnimation(hologram, targetLocation);
            updateDisplay(display, hologram, line, renderedAnimation, billboard);
            syncInteraction(viewerId, hologram, pageIndex, lineIndex, renderedAnimation.location(),
                    interactionSize.width(),
                    interactionSize.height());
            showEntity(viewerId, display.getUniqueId());
        })) {
            destroyLine(viewerId, hologram.getId(), pageIndex, lineIndex);
            return false;
        }
        return true;
    }

    public static void destroyLine(Player player, String hologramId, int pageIndex, int lineIndex) {
        if (player == null) {
            return;
        }
        destroyLine(player.getUniqueId(), hologramId, pageIndex, lineIndex);
    }

    public static void destroyLine(UUID viewerId, String hologramId, int pageIndex, int lineIndex) {
        LineKey key = new LineKey(hologramId, pageIndex, lineIndex);
        removeTrackedLineEntity(playerLines(viewerId), key);
        removeTrackedLineEntity(playerInteractions(viewerId), key);
        removeTextLineState(viewerId, key);
    }

    public static void destroyAllHologramLinesForPlayer(Player player) {
        if (player == null) {
            return;
        }
        destroyAllHologramLinesForPlayer(player.getUniqueId());
    }

    public static void destroyAllHologramLinesForPlayer(UUID viewerId) {
        Map<LineKey, UUID> lineIds = PLAYER_LINE_ENTITIES.remove(viewerId);
        if (lineIds != null) {
            for (UUID entityId : lineIds.values()) {
                removeEntity(entityId);
            }
        }

        Map<LineKey, UUID> interactionIds = PLAYER_LINE_INTERACTIONS.remove(viewerId);
        if (interactionIds != null) {
            for (UUID entityId : interactionIds.values()) {
                removeEntity(entityId);
            }
        }
        PLAYER_LINE_TEXTS.remove(viewerId);
    }

    public static void destroyAllHologramLines(Player player, String hologramId) {
        if (player == null) {
            return;
        }
        destroyAllHologramLines(player.getUniqueId(), hologramId);
    }

    public static void destroyAllHologramLines(UUID viewerId, String hologramId) {
        destroyMatchingLines(viewerId, hologramId, null, null);
    }

    public static void hideAllHologramLines(Player player, String hologramId) {
        if (player == null || hologramId == null) {
            return;
        }

        UUID viewerId = player.getUniqueId();
        scheduler().runAtEntity(player, () -> {
            hideMatchingEntities(player, viewerId, PLAYER_LINE_ENTITIES.get(viewerId), hologramId, true);
            hideMatchingEntities(player, viewerId, PLAYER_LINE_INTERACTIONS.get(viewerId), hologramId, false);
        });
    }

    public static void hideAllHologramLinesNow(Player player, String hologramId) {
        if (player == null || hologramId == null) {
            return;
        }

        UUID viewerId = player.getUniqueId();
        hideMatchingEntities(player, viewerId, PLAYER_LINE_ENTITIES.get(viewerId), hologramId, true);
        hideMatchingEntities(player, viewerId, PLAYER_LINE_INTERACTIONS.get(viewerId), hologramId, false);
    }

    public static boolean reshowAllHologramLines(Player player, String hologramId) {
        if (player == null) {
            return false;
        }
        return reshowAllHologramLines(player.getUniqueId(), hologramId);
    }

    public static boolean reshowAllHologramLines(UUID viewerId, String hologramId) {
        boolean lineEntitiesReady = reshowMatchingEntities(viewerId, PLAYER_LINE_ENTITIES.get(viewerId), hologramId, true);
        reshowMatchingEntities(viewerId, PLAYER_LINE_INTERACTIONS.get(viewerId), hologramId, false);
        return lineEntitiesReady;
    }

    public static void destroyAllTrackedEntities() {
        Set<UUID> entityIds = new HashSet<>(TRACKED_ENTITY_IDS);
        for (Map<LineKey, UUID> trackedLines : PLAYER_LINE_ENTITIES.values()) {
            entityIds.addAll(trackedLines.values());
        }
        for (Map<LineKey, UUID> trackedInteractions : PLAYER_LINE_INTERACTIONS.values()) {
            entityIds.addAll(trackedInteractions.values());
        }

        PLAYER_LINE_ENTITIES.clear();
        PLAYER_LINE_INTERACTIONS.clear();
        PLAYER_LINE_TEXTS.clear();
        TRACKED_ENTITY_IDS.clear();
        TRACKED_DISPLAYS.clear();
        DISPLAY_STATES.clear();
        TEXT_STYLE_STATES.clear();

        for (UUID entityId : entityIds) {
            removeEntityFromWorld(entityId);
        }
    }

    public static void destroyOtherPages(Player player, String hologramId, int visiblePageIndex) {
        if (player == null) {
            return;
        }
        destroyOtherPages(player.getUniqueId(), hologramId, visiblePageIndex);
    }

    public static void destroyOtherPages(UUID viewerId, String hologramId, int visiblePageIndex) {
        Map<LineKey, UUID> lines = PLAYER_LINE_ENTITIES.get(viewerId);
        Map<LineKey, UUID> interactions = PLAYER_LINE_INTERACTIONS.get(viewerId);
        Map<LineKey, TextLineState> texts = PLAYER_LINE_TEXTS.get(viewerId);
        boolean noLines = lines == null || lines.isEmpty();
        boolean noInteractions = interactions == null || interactions.isEmpty();
        boolean noTexts = texts == null || texts.isEmpty();
        if (noLines && noInteractions && noTexts) {
            return;
        }

        destroyOtherPages(lines, hologramId, visiblePageIndex);
        destroyOtherPages(interactions, hologramId, visiblePageIndex);
        destroyOtherPageTextStates(viewerId, texts, hologramId, visiblePageIndex);
    }

    public static void destroyLinesExcept(Player player, String hologramId, int pageIndex, Set<Integer> keepLines) {
        if (player == null) {
            return;
        }
        destroyLinesExcept(player.getUniqueId(), hologramId, pageIndex, keepLines);
    }

    public static void destroyLinesExcept(UUID viewerId, String hologramId, int pageIndex, Set<Integer> keepLines) {
        destroyMatchingLines(viewerId, hologramId, pageIndex, keepLines);
    }

    private static void destroyMatchingLines(UUID viewerId, String hologramId, Integer pageIndex, Set<Integer> keepLines) {
        Map<LineKey, UUID> lines = PLAYER_LINE_ENTITIES.get(viewerId);
        Map<LineKey, UUID> interactions = PLAYER_LINE_INTERACTIONS.get(viewerId);
        destroyMatchingLines(lines, hologramId, pageIndex, keepLines);
        destroyMatchingLines(interactions, hologramId, pageIndex, keepLines);
        destroyMatchingTextStates(viewerId, hologramId, pageIndex, keepLines);
    }

    private static void destroyMatchingLines(Map<LineKey, UUID> trackedEntities, String hologramId, Integer pageIndex, Set<Integer> keepLines) {
        if (trackedEntities == null || trackedEntities.isEmpty()) {
            return;
        }

        for (Map.Entry<LineKey, UUID> entry : new HashSet<>(trackedEntities.entrySet())) {
            LineKey key = entry.getKey();
            boolean sameHologram = key.hologramId().equals(hologramId);
            boolean samePage = pageIndex == null || key.pageIndex() == pageIndex;
            boolean shouldKeep = keepLines != null && pageIndex != null && key.pageIndex() == pageIndex && keepLines.contains(key.lineIndex());
            boolean shouldDestroy = sameHologram && samePage && !shouldKeep;
            boolean shouldDestroyOtherPage = keepLines != null && pageIndex != null && sameHologram && key.pageIndex() != pageIndex;

            if (shouldDestroy || shouldDestroyOtherPage) {
                removeTrackedLineEntity(trackedEntities, key);
            }
        }
    }

    private static void destroyMatchingTextStates(UUID viewerId, String hologramId, Integer pageIndex, Set<Integer> keepLines) {
        Map<LineKey, TextLineState> texts = PLAYER_LINE_TEXTS.get(viewerId);
        if (texts == null || texts.isEmpty()) {
            return;
        }

        for (LineKey key : new HashSet<>(texts.keySet())) {
            boolean sameHologram = key.hologramId().equals(hologramId);
            boolean samePage = pageIndex == null || key.pageIndex() == pageIndex;
            boolean shouldKeep = keepLines != null && pageIndex != null && key.pageIndex() == pageIndex && keepLines.contains(key.lineIndex());
            boolean shouldDestroy = sameHologram && samePage && !shouldKeep;
            boolean shouldDestroyOtherPage = keepLines != null && pageIndex != null && sameHologram && key.pageIndex() != pageIndex;

            if (shouldDestroy || shouldDestroyOtherPage) {
                texts.remove(key);
            }
        }

        if (texts.isEmpty()) {
            PLAYER_LINE_TEXTS.remove(viewerId, texts);
        }
    }

    private static boolean reshowMatchingEntities(UUID viewerId, Map<LineKey, UUID> trackedEntities, String hologramId, boolean removeTextState) {
        if (trackedEntities == null || trackedEntities.isEmpty()) {
            return false;
        }

        boolean found = false;
        boolean allValid = true;
        for (Map.Entry<LineKey, UUID> entry : new HashSet<>(trackedEntities.entrySet())) {
            LineKey key = entry.getKey();
            if (!key.hologramId().equals(hologramId)) {
                continue;
            }

            found = true;
            UUID entityId = entry.getValue();
            Entity entity = Bukkit.getEntity(entityId);
            if (entity == null || !entity.isValid()) {
                allValid = false;
                removeTrackedLineEntity(trackedEntities, key);
                if (removeTextState) {
                    removeTextLineState(viewerId, key);
                }
                continue;
            }

            showEntity(viewerId, entityId);
        }
        return found && allValid;
    }

    private static void hideMatchingEntities(Player player, UUID viewerId, Map<LineKey, UUID> trackedEntities, String hologramId, boolean removeTextState) {
        AxoHologram plugin = AxoHologram.getInstance();
        if (plugin == null || trackedEntities == null || trackedEntities.isEmpty()) {
            return;
        }

        for (Map.Entry<LineKey, UUID> entry : new HashSet<>(trackedEntities.entrySet())) {
            LineKey key = entry.getKey();
            if (!key.hologramId().equals(hologramId)) {
                continue;
            }

            UUID entityId = entry.getValue();
            Entity entity = Bukkit.getEntity(entityId);
            if (entity == null || !entity.isValid()) {
                removeTrackedLineEntity(trackedEntities, key);
                if (removeTextState) {
                    removeTextLineState(viewerId, key);
                }
                continue;
            }

            player.hideEntity(plugin, entity);
        }
    }

    private static Map<LineKey, UUID> playerLines(UUID viewerId) {
        return PLAYER_LINE_ENTITIES.computeIfAbsent(viewerId, ignored -> new ConcurrentHashMap<>());
    }

    private static Map<LineKey, UUID> playerInteractions(UUID viewerId) {
        return PLAYER_LINE_INTERACTIONS.computeIfAbsent(viewerId, ignored -> new ConcurrentHashMap<>());
    }

    private static Map<LineKey, TextLineState> playerTexts(UUID viewerId) {
        return PLAYER_LINE_TEXTS.computeIfAbsent(viewerId, ignored -> new ConcurrentHashMap<>());
    }

    private static TextLineState getTextLineState(UUID viewerId, LineKey key) {
        Map<LineKey, TextLineState> texts = PLAYER_LINE_TEXTS.get(viewerId);
        return texts == null ? null : texts.get(key);
    }

    private static void removeTextLineState(UUID viewerId, LineKey key) {
        Map<LineKey, TextLineState> texts = PLAYER_LINE_TEXTS.get(viewerId);
        if (texts == null) {
            return;
        }

        texts.remove(key);
        if (texts.isEmpty()) {
            PLAYER_LINE_TEXTS.remove(viewerId, texts);
        }
    }

    private static boolean trackLine(UUID viewerId, String hologramId, int pageIndex, int lineIndex, Display display) {
        Player player = Bukkit.getPlayer(viewerId);
        if (player == null || !player.isOnline()) {
            display.remove();
            return false;
        }

        TRACKED_ENTITY_IDS.add(display.getUniqueId());
        TRACKED_DISPLAYS.put(display.getUniqueId(), new TrackedDisplay(viewerId, hologramId, pageIndex, lineIndex));
        UUID previousEntityId = playerLines(viewerId).put(new LineKey(hologramId, pageIndex, lineIndex), display.getUniqueId());
        removeReplacedEntity(previousEntityId, display.getUniqueId());
        showEntity(viewerId, display.getUniqueId());
        return true;
    }

    private static void trackInteraction(UUID viewerId, String hologramId, int pageIndex, int lineIndex, Interaction interaction) {
        Player player = Bukkit.getPlayer(viewerId);
        if (player == null || !player.isOnline()) {
            interaction.remove();
            return;
        }

        TRACKED_ENTITY_IDS.add(interaction.getUniqueId());
        TRACKED_DISPLAYS.put(interaction.getUniqueId(), new TrackedDisplay(viewerId, hologramId, pageIndex, lineIndex));
        UUID previousEntityId = playerInteractions(viewerId).put(new LineKey(hologramId, pageIndex, lineIndex), interaction.getUniqueId());
        removeReplacedEntity(previousEntityId, interaction.getUniqueId());
        showEntity(viewerId, interaction.getUniqueId());
    }

    private static void removeReplacedEntity(UUID previousEntityId, UUID currentEntityId) {
        if (previousEntityId != null && !previousEntityId.equals(currentEntityId)) {
            removeEntity(previousEntityId);
        }
    }

    public static TrackedDisplay getTrackedDisplay(UUID entityId) {
        return entityId == null ? null : TRACKED_DISPLAYS.get(entityId);
    }

    private static void showEntity(UUID viewerId, UUID entityId) {
        AxoHologram plugin = AxoHologram.getInstance();
        if (plugin == null) {
            return;
        }

        Player player = Bukkit.getPlayer(viewerId);
        if (player == null || !player.isOnline()) {
            return;
        }

        scheduler().runAtEntity(player, () -> {
            Entity entity = Bukkit.getEntity(entityId);
            if (entity != null) {
                player.showEntity(plugin, entity);
            }
        });
    }

    private static void configureDisplay(Display display, Hologram hologram, HologramLine line, RenderedDisplayAnimation renderedAnimation, Billboard billboard) {
        configureDisplay(display, hologram, line, renderedAnimation, billboard, false);
    }

    private static void configureItemDisplay(ItemDisplay display, Hologram hologram, HologramLine line, RenderedDisplayAnimation renderedAnimation, ItemStack itemStack, Billboard billboard) {
        configureDisplay(display, hologram, line, renderedAnimation, billboard, shouldFlipItemModel(itemStack));
    }

    private static void configureDisplay(Display display, Hologram hologram, HologramLine line, RenderedDisplayAnimation renderedAnimation, Billboard billboard, boolean flipItemModel) {
        display.setVisibleByDefault(false);
        display.setPersistent(false);
        display.setInvulnerable(true);
        display.setGravity(false);
        applyDisplayState(display, hologram, line, renderedAnimation, billboard, true, flipItemModel);
        display.setInterpolationDelay(0);
    }

    private static void updateDisplay(Display display, Hologram hologram, HologramLine line, RenderedDisplayAnimation renderedAnimation, Billboard billboard) {
        applyDisplayState(display, hologram, line, renderedAnimation, billboard, true);
    }

    private static void updateItemDisplay(ItemDisplay display, Hologram hologram, HologramLine line, RenderedDisplayAnimation renderedAnimation, ItemStack itemStack, Billboard billboard) {
        applyDisplayState(display, hologram, line, renderedAnimation, billboard, true, shouldFlipItemModel(itemStack));
    }

    private static void applyDisplayState(Display display, Hologram hologram, HologramLine line, RenderedDisplayAnimation renderedAnimation, Billboard billboard, boolean allowTeleport) {
        applyDisplayState(display, hologram, line, renderedAnimation, billboard, allowTeleport, false);
    }

    private static void applyDisplayState(Display display, Hologram hologram, HologramLine line, RenderedDisplayAnimation renderedAnimation, Billboard billboard, boolean allowTeleport, boolean flipItemModel) {
        if (renderedAnimation == null || renderedAnimation.location() == null || renderedAnimation.location().getWorld() == null) {
            return;
        }

        Location targetLocation = renderedAnimation.location();
        if (allowTeleport && shouldTeleport(display, targetLocation)) {
            display.teleportAsync(targetLocation);
        }

        if (shouldUpdateRotation(display, targetLocation)) {
            display.setRotation(targetLocation.getYaw(), targetLocation.getPitch());
        }

        DisplayState state = createDisplayState(display, hologram, line, renderedAnimation, billboard, flipItemModel);
        DisplayState previousState = DISPLAY_STATES.get(display.getUniqueId());
        if (Objects.equals(previousState, state)) {
            return;
        }

        if (previousState == null || previousState.interpolationDuration() != state.interpolationDuration()) {
            display.setInterpolationDuration(state.interpolationDuration());
        }
        if (previousState == null || previousState.billboard() != state.billboard()) {
            display.setBillboard(state.billboard());
        }
        if (previousState == null || Float.compare(previousState.viewRange(), state.viewRange()) != 0) {
            display.setViewRange(state.viewRange());
        }
        if (previousState == null || Float.compare(previousState.shadowRadius(), state.shadowRadius()) != 0) {
            display.setShadowRadius(state.shadowRadius());
        }
        if (previousState == null || Float.compare(previousState.shadowStrength(), state.shadowStrength()) != 0) {
            display.setShadowStrength(state.shadowStrength());
        }
        if (shouldUpdateTransformation(previousState, state)) {
            display.setTransformation(buildTransformation(state));
        }
        if (state.brightnessEnabled()) {
            if (!hasSameBrightness(previousState, state)) {
                display.setBrightness(new Display.Brightness(state.brightnessBlock(), state.brightnessSky()));
            }
        } else if (previousState != null && previousState.brightnessEnabled()) {
            display.setBrightness(null);
        }

        DISPLAY_STATES.put(display.getUniqueId(), state);
    }

    private static void syncInteraction(UUID viewerId, Hologram hologram, int pageIndex, int lineIndex, Location location, float width, float height) {
        if (!hasInteractiveActions(hologram)) {
            destroyInteraction(viewerId, hologram.getId(), pageIndex, lineIndex);
            return;
        }
        if (location == null || location.getWorld() == null) {
            return;
        }

        LineKey key = new LineKey(hologram.getId(), pageIndex, lineIndex);
        UUID interactionId = playerInteractions(viewerId).get(key);
        Location interactionLocation = resolveInteractionLocation(location, height);
        float interactionWidth = clampInteractionDimension(width);
        float interactionHeight = clampInteractionDimension(height);
        scheduler().runAtLocation(interactionLocation, () -> {
            if (interactionLocation.getWorld() == null) {
                return;
            }

            if (interactionId != null && Bukkit.getEntity(interactionId) instanceof Interaction interaction && interaction.isValid()) {
                updateInteraction(interaction, interactionLocation, interactionWidth, interactionHeight);
                showEntity(viewerId, interaction.getUniqueId());
                return;
            }

            if (interactionId != null) {
                removeEntity(interactionId);
            }

            Interaction interaction = interactionLocation.getWorld().spawn(interactionLocation, Interaction.class, entity -> {
                configureInteraction(entity, interactionLocation, interactionWidth, interactionHeight);
            });
            trackInteraction(viewerId, hologram.getId(), pageIndex, lineIndex, interaction);
        });
    }

    private static void destroyInteraction(UUID viewerId, String hologramId, int pageIndex, int lineIndex) {
        removeTrackedLineEntity(playerInteractions(viewerId), new LineKey(hologramId, pageIndex, lineIndex));
    }

    private static void configureInteraction(Interaction interaction, Location location, float width, float height) {
        interaction.setVisibleByDefault(false);
        interaction.setPersistent(false);
        interaction.setInvulnerable(true);
        interaction.setGravity(false);
        interaction.setResponsive(true);
        interaction.setInteractionWidth(width);
        interaction.setInteractionHeight(height);
        interaction.setRotation(location.getYaw(), location.getPitch());
    }

    private static void updateInteraction(Interaction interaction, Location location, float width, float height) {
        if (shouldTeleport(interaction, location)) {
            interaction.teleportAsync(location);
        }
        if (shouldUpdateRotation(interaction, location)) {
            interaction.setRotation(location.getYaw(), location.getPitch());
        }
        if (Float.compare(interaction.getInteractionWidth(), width) != 0) {
            interaction.setInteractionWidth(width);
        }
        if (Float.compare(interaction.getInteractionHeight(), height) != 0) {
            interaction.setInteractionHeight(height);
        }
        if (!interaction.isResponsive()) {
            interaction.setResponsive(true);
        }
    }

    private static boolean shouldTeleport(Entity entity, Location target) {
        Location current = entity.getLocation();
        return current.getWorld() != target.getWorld()
                || Double.compare(current.getX(), target.getX()) != 0
                || Double.compare(current.getY(), target.getY()) != 0
                || Double.compare(current.getZ(), target.getZ()) != 0;
    }

    private static boolean shouldUpdateRotation(Entity entity, Location target) {
        Location current = entity.getLocation();
        return Float.compare(current.getYaw(), target.getYaw()) != 0
                || Float.compare(current.getPitch(), target.getPitch()) != 0;
    }

    private static void applyTextStyle(TextDisplay display, Hologram hologram) {
        TextStyleState state = new TextStyleState(
                hologram.hasTextShadow(),
                hologram.isSeeThrough(),
                hologram.getAlignment(),
                hologram.getBackgroundColor(),
                resolveTextLineWidth()
        );
        TextStyleState previousState = TEXT_STYLE_STATES.get(display.getUniqueId());
        if (Objects.equals(previousState, state)) {
            return;
        }

        if (previousState == null || previousState.shadowed() != state.shadowed()) {
            display.setShadowed(state.shadowed());
        }
        if (previousState == null || previousState.seeThrough() != state.seeThrough()) {
            display.setSeeThrough(state.seeThrough());
        }
        if (previousState == null || previousState.alignment() != state.alignment()) {
            display.setAlignment(state.alignment());
        }
        if (previousState == null || previousState.lineWidth() != state.lineWidth()) {
            display.setLineWidth(state.lineWidth());
        }
        if (previousState == null) {
            display.setTextOpacity((byte) 255);
            display.setDefaultBackground(false);
        }
        if (previousState == null || !Objects.equals(previousState.backgroundColor(), state.backgroundColor())) {
            display.setBackgroundColor(state.backgroundColor());
        }
        TEXT_STYLE_STATES.put(display.getUniqueId(), state);
    }

    private static float resolveViewRange(Hologram hologram) {
        if (hologram.getViewDistance() > 0) {
            return hologram.getViewDistance();
        }
        return (float) AxoHologram.getInstance().getConfigManager().getConfig().getDouble("general.view-distance", 48.0D);
    }

    private static InteractionSize resolveTextInteractionSize(Component text, Hologram hologram) {
        String plainText = PLAIN_TEXT.serialize(normalizeText(text));
        int maxLineLength = 1;
        String[] lines = plainText.split("\\R", -1);
        for (String line : lines) {
            maxLineLength = Math.max(maxLineLength, line.length());
        }

        int lineCount = Math.max(1, lines.length);
        TextRenderSettings settings = resolveTextRenderSettings(hologram);
        float width = Math.max(0.6F, (maxLineLength * 0.12F + 0.35F) * settings.widthScale());
        float height = Math.max(0.35F, (0.30F + (Math.max(0, lineCount - 1) * 0.25F)) * settings.heightScale());
        return new InteractionSize(width, height);
    }

    private static TextLineState createTextLineState(Component text, Hologram hologram) {
        InteractionSize interactionSize = resolveTextInteractionSize(text, hologram);
        TextRenderSettings settings = resolveTextRenderSettings(hologram);
        return new TextLineState(
                normalizeText(text),
                settings.widthScale(),
                settings.heightScale(),
                settings.lineWidth(),
                interactionSize.width(),
                interactionSize.height()
        );
    }

    private static TextRenderSettings resolveTextRenderSettings(Hologram hologram) {
        float textWidthScale = resolveTextHorizontalScale();
        float widthScale = Math.max(0.5F, hologram.getScaleX()) * textWidthScale;
        float heightScale = Math.max(0.5F, hologram.getScaleY());
        return new TextRenderSettings(widthScale, heightScale, resolveTextLineWidth());
    }

    private static float resolveTextHorizontalScale() {
        AxoHologram plugin = AxoHologram.getInstance();
        double configured = plugin == null
                ? 1.12D
                : plugin.getConfigManager().getConfig().getDouble("general.defaults.text-rendering.horizontal-scale", 1.12D);
        return clampFloat((float) configured, 0.25F, 4.0F);
    }

    private static int resolveTextLineWidth() {
        AxoHologram plugin = AxoHologram.getInstance();
        int configured = plugin == null
                ? 2048
                : plugin.getConfigManager().getConfig().getInt("general.defaults.text-rendering.line-width", 2048);
        return Math.max(1, Math.min(configured, 8192));
    }

    private static boolean isCurrentTextLineState(TextLineState state, Hologram hologram) {
        TextRenderSettings settings = resolveTextRenderSettings(hologram);
        return Float.compare(state.widthScale(), settings.widthScale()) == 0
                && Float.compare(state.heightScale(), settings.heightScale()) == 0
                && state.lineWidth() == settings.lineWidth();
    }

    private static Component normalizeText(Component text) {
        return text == null ? Component.empty() : text;
    }

    private static float resolveDisplayInteractionSize(Hologram hologram, HologramLine line) {
        float hologramScale = Math.max(hologram.getScaleX(), Math.max(hologram.getScaleY(), hologram.getScaleZ()));
        return Math.max(0.75F, hologramScale * resolveMaxLineScale(line));
    }

    private static InteractionSize resolveExistingInteractionSize(UUID viewerId, LineKey key, Display display, Hologram hologram, HologramLine line) {
        if (display instanceof TextDisplay textDisplay) {
            TextLineState textState = getTextLineState(viewerId, key);
            if (textState != null && isCurrentTextLineState(textState, hologram)) {
                return new InteractionSize(textState.interactionWidth(), textState.interactionHeight());
            }

            Component text = textDisplay.text();
            TextLineState refreshedTextState = createTextLineState(text, hologram);
            playerTexts(viewerId).put(key, refreshedTextState);
            return new InteractionSize(refreshedTextState.interactionWidth(), refreshedTextState.interactionHeight());
        }

        float interactionSize = resolveDisplayInteractionSize(hologram, line);
        return new InteractionSize(interactionSize, interactionSize);
    }

    private static Location resolveInteractionLocation(Location location, float height) {
        return location.clone().subtract(0.0D, height * 0.5D, 0.0D);
    }

    private static float clampInteractionDimension(float value) {
        return Math.max(0.1F, Math.min(value, 32.0F));
    }

    private static DisplayState createDisplayState(Display display, Hologram hologram, HologramLine line, RenderedDisplayAnimation renderedAnimation, Billboard billboard, boolean flipItemModel) {
        float multiplier = Math.max(0.01F, renderedAnimation.scaleMultiplier());
        float textHorizontalScale = display instanceof TextDisplay ? resolveTextHorizontalScale() : 1.0F;
        boolean brightnessEnabled = hologram.getBrightnessBlock() >= 0 || hologram.getBrightnessSky() >= 0;
        int brightnessBlock = brightnessEnabled ? (hologram.getBrightnessBlock() >= 0 ? hologram.getBrightnessBlock() : 15) : -1;
        int brightnessSky = brightnessEnabled ? (hologram.getBrightnessSky() >= 0 ? hologram.getBrightnessSky() : 15) : -1;
        return new DisplayState(
                renderedAnimation.interpolationDuration(),
                billboard.toPaperBillboard(),
                resolveViewRange(hologram),
                hologram.getShadowRadius(),
                hologram.getShadowStrength(),
                hologram.getScaleX() * resolveLineScaleX(line) * multiplier * textHorizontalScale,
                hologram.getScaleY() * resolveLineScaleY(line) * multiplier,
                hologram.getScaleZ() * resolveLineScaleZ(line) * multiplier,
                renderedAnimation.rollOffset(),
                flipItemModel,
                brightnessEnabled,
                brightnessBlock,
                brightnessSky
        );
    }

    private static Transformation buildTransformation(DisplayState state) {
        return new Transformation(
                new Vector3f(0.0F, 0.0F, 0.0F),
                new Quaternionf().rotateZ((float) Math.toRadians(state.rollOffset())),
                new Vector3f(state.scaleX(), state.scaleY(), state.scaleZ()),
                state.flipItemModel() ? new Quaternionf().rotateY((float) Math.PI) : new Quaternionf()
        );
    }

    private static float resolveLineScaleX(HologramLine line) {
        if (line instanceof ItemLineImpl itemLine) {
            return itemLine.getScaleX();
        }
        if (line instanceof BlockLineImpl blockLine) {
            return blockLine.getScaleX();
        }
        return 1.0F;
    }

    private static float resolveLineScaleY(HologramLine line) {
        if (line instanceof ItemLineImpl itemLine) {
            return itemLine.getScaleY();
        }
        if (line instanceof BlockLineImpl blockLine) {
            return blockLine.getScaleY();
        }
        return 1.0F;
    }

    private static float resolveLineScaleZ(HologramLine line) {
        if (line instanceof ItemLineImpl itemLine) {
            return itemLine.getScaleZ();
        }
        if (line instanceof BlockLineImpl blockLine) {
            return blockLine.getScaleZ();
        }
        return 1.0F;
    }

    private static float resolveMaxLineScale(HologramLine line) {
        return Math.max(resolveLineScaleX(line), Math.max(resolveLineScaleY(line), resolveLineScaleZ(line)));
    }

    private static boolean shouldFlipItemModel(ItemStack itemStack) {
        return itemStack != null && itemStack.getType() == Material.PLAYER_HEAD;
    }

    private static RenderedDisplayAnimation renderDisplayAnimation(Hologram hologram, Location location) {
        AxoHologram plugin = AxoHologram.getInstance();
        if (plugin != null && plugin.getAnimationManager() != null) {
            return plugin.getAnimationManager().renderDisplayAnimation(hologram, location);
        }
        return new RenderedDisplayAnimation(location.clone(), 1.0F, 0.0F, 0);
    }

    public static void hideAllTrackedEntitiesForPlayer(Player player) {
        AxoHologram plugin = AxoHologram.getInstance();
        if (plugin == null || player == null) {
            return;
        }

        scheduler().runAtEntity(player, () -> {
            for (UUID entityId : new HashSet<>(TRACKED_ENTITY_IDS)) {
                Entity entity = Bukkit.getEntity(entityId);
                if (entity != null) {
                    player.hideEntity(plugin, entity);
                }
            }
        });
    }

    private static boolean hasInteractiveActions(Hologram hologram) {
        if (hologram.getPages().size() > 1) {
            return true;
        }

        for (HologramClickType clickType : HologramClickType.values()) {
            if (!hologram.getActions(clickType).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static void destroyOtherPages(Map<LineKey, UUID> trackedEntities, String hologramId, int visiblePageIndex) {
        if (trackedEntities == null || trackedEntities.isEmpty()) {
            return;
        }

        for (Map.Entry<LineKey, UUID> entry : new HashSet<>(trackedEntities.entrySet())) {
            LineKey key = entry.getKey();
            if (!key.hologramId().equals(hologramId) || key.pageIndex() == visiblePageIndex) {
                continue;
            }

            removeTrackedLineEntity(trackedEntities, key);
        }
    }

    private static void destroyOtherPageTextStates(UUID viewerId, Map<LineKey, TextLineState> texts, String hologramId, int visiblePageIndex) {
        if (texts == null || texts.isEmpty()) {
            return;
        }

        for (LineKey key : new HashSet<>(texts.keySet())) {
            if (!key.hologramId().equals(hologramId) || key.pageIndex() == visiblePageIndex) {
                continue;
            }

            texts.remove(key);
        }

        if (texts.isEmpty()) {
            PLAYER_LINE_TEXTS.remove(viewerId, texts);
        }
    }

    private static void removeTrackedLineEntity(Map<LineKey, UUID> trackedEntities, LineKey key) {
        if (trackedEntities == null) {
            return;
        }

        UUID entityId = trackedEntities.remove(key);
        if (entityId != null) {
            removeEntity(entityId);
        }
    }

    private static void removeEntity(UUID entityId) {
        TRACKED_ENTITY_IDS.remove(entityId);
        TRACKED_DISPLAYS.remove(entityId);
        DISPLAY_STATES.remove(entityId);
        TEXT_STYLE_STATES.remove(entityId);
        removeEntityFromWorld(entityId);
    }

    private static void removeEntityFromWorld(UUID entityId) {
        Entity entity = Bukkit.getEntity(entityId);
        if (entity == null) {
            return;
        }

        AxoHologram plugin = AxoHologram.getInstance();
        if (plugin != null && !plugin.getSchedulerUtil().isFolia() && Bukkit.isPrimaryThread()) {
            entity.remove();
            return;
        }

        scheduler().runAtEntity(entity, entity::remove);
    }

    private static boolean shouldUpdateTransformation(DisplayState previousState, DisplayState state) {
        return previousState == null
                || Float.compare(previousState.scaleX(), state.scaleX()) != 0
                || Float.compare(previousState.scaleY(), state.scaleY()) != 0
                || Float.compare(previousState.scaleZ(), state.scaleZ()) != 0
                || Float.compare(previousState.rollOffset(), state.rollOffset()) != 0
                || previousState.flipItemModel() != state.flipItemModel();
    }

    private static boolean hasSameBrightness(DisplayState previousState, DisplayState state) {
        return previousState != null
                && previousState.brightnessEnabled()
                && previousState.brightnessBlock() == state.brightnessBlock()
                && previousState.brightnessSky() == state.brightnessSky();
    }

    private static float clampFloat(float value, float min, float max) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(value, max));
    }

    private record TextRenderSettings(float widthScale, float heightScale, int lineWidth) {
    }

    private static org.axostudio.axohologram.util.SchedulerUtil scheduler() {
        return AxoHologram.getInstance().getSchedulerUtil();
    }
}
