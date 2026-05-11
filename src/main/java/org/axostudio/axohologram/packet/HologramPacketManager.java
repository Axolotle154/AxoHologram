package org.axostudio.axohologram.packet;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.axostudio.axohologram.AxoHologram;
import org.axostudio.axohologram.animation.RenderedDisplayAnimation;
import org.axostudio.axohologram.hologram.Hologram;
import org.axostudio.axohologram.hologram.action.HologramClickType;
import org.axostudio.axohologram.hologram.billboard.Billboard;
import org.bukkit.Bukkit;
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
    private static final Set<UUID> TRACKED_ENTITY_IDS = ConcurrentHashMap.newKeySet();
    private static final PlainTextComponentSerializer PLAIN_TEXT = PlainTextComponentSerializer.plainText();

    private HologramPacketManager() {
    }

    private record LineKey(String hologramId, int pageIndex, int lineIndex) {
    }

    private record TextLineState(Component text, float scale, float interactionWidth, float interactionHeight) {
    }

    private record InteractionSize(float width, float height) {
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

        Component displayText = normalizeText(text);
        LineKey key = new LineKey(hologram.getId(), pageIndex, lineIndex);
        TextLineState textState = createTextLineState(displayText, hologram);
        Location spawnLocation = location.clone();
        destroyLine(viewerId, hologram.getId(), pageIndex, lineIndex);
        scheduler().runAtLocation(spawnLocation, () -> {
            if (spawnLocation.getWorld() == null) {
                return;
            }

            TextDisplay display = spawnLocation.getWorld().spawn(spawnLocation, TextDisplay.class, entity -> {
                configureDisplay(entity, hologram, spawnLocation, billboard);
                applyTextStyle(entity, hologram);
                entity.text(displayText);
            });

            if (!trackLine(viewerId, hologram.getId(), pageIndex, lineIndex, display)) {
                return;
            }
            playerTexts(viewerId).put(key, textState);
            syncInteraction(viewerId, hologram, pageIndex, lineIndex, spawnLocation,
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
        boolean scaleChanged = previousTextState == null || Float.compare(previousTextState.scale(), resolveTextInteractionScale(hologram)) != 0;
        TextLineState textState = textChanged || scaleChanged
                ? createTextLineState(displayText, hologram)
                : previousTextState;
        Location targetLocation = location.clone();
        if (!scheduler().runAtEntity(display, () -> {
            updateDisplay(display, hologram, targetLocation, billboard);
            applyTextStyle(display, hologram);
            if (textChanged) {
                display.text(displayText);
            }
            if (textChanged || scaleChanged) {
                playerTexts(viewerId).put(key, textState);
            }
            syncInteraction(viewerId, hologram, pageIndex, lineIndex, targetLocation,
                    textState.interactionWidth(),
                    textState.interactionHeight());
        })) {
            destroyLine(viewerId, hologram.getId(), pageIndex, lineIndex);
            spawnTextLine(viewerId, hologram, pageIndex, lineIndex, location, text, billboard);
        }
    }

    public static void spawnItemLine(Player player, Hologram hologram, int pageIndex, int lineIndex, Location location, ItemStack itemStack, Billboard billboard) {
        if (player == null) {
            return;
        }
        spawnItemLine(player.getUniqueId(), hologram, pageIndex, lineIndex, location, itemStack, billboard);
    }

    public static void spawnItemLine(UUID viewerId, Hologram hologram, int pageIndex, int lineIndex, Location location, ItemStack itemStack, Billboard billboard) {
        if (location == null || location.getWorld() == null) {
            return;
        }

        Location spawnLocation = location.clone();
        ItemStack stack = itemStack.clone();
        destroyLine(viewerId, hologram.getId(), pageIndex, lineIndex);
        scheduler().runAtLocation(spawnLocation, () -> {
            if (spawnLocation.getWorld() == null) {
                return;
            }

            ItemDisplay display = spawnLocation.getWorld().spawn(spawnLocation, ItemDisplay.class, entity -> {
                configureItemDisplay(entity, hologram, spawnLocation, stack, billboard);
                entity.setItemStack(stack);
            });

            trackLine(viewerId, hologram.getId(), pageIndex, lineIndex, display);
            syncInteraction(viewerId, hologram, pageIndex, lineIndex, spawnLocation,
                    resolveDisplayInteractionSize(hologram),
                    resolveDisplayInteractionSize(hologram));
        });
    }

    public static void updateItemLine(Player player, Hologram hologram, int pageIndex, int lineIndex, Location location, ItemStack itemStack, Billboard billboard) {
        if (player == null) {
            return;
        }
        updateItemLine(player.getUniqueId(), hologram, pageIndex, lineIndex, location, itemStack, billboard);
    }

    public static void updateItemLine(UUID viewerId, Hologram hologram, int pageIndex, int lineIndex, Location location, ItemStack itemStack, Billboard billboard) {
        LineKey key = new LineKey(hologram.getId(), pageIndex, lineIndex);
        UUID entityId = playerLines(viewerId).get(key);
        if (entityId == null) {
            spawnItemLine(viewerId, hologram, pageIndex, lineIndex, location, itemStack, billboard);
            return;
        }

        if (!(Bukkit.getEntity(entityId) instanceof ItemDisplay display) || !display.isValid()) {
            destroyLine(viewerId, hologram.getId(), pageIndex, lineIndex);
            spawnItemLine(viewerId, hologram, pageIndex, lineIndex, location, itemStack, billboard);
            return;
        }

        Location targetLocation = location.clone();
        ItemStack stack = itemStack.clone();
        if (!scheduler().runAtEntity(display, () -> {
            updateItemDisplay(display, hologram, targetLocation, stack, billboard);
            display.setItemStack(stack);
            syncInteraction(viewerId, hologram, pageIndex, lineIndex, targetLocation,
                    resolveDisplayInteractionSize(hologram),
                    resolveDisplayInteractionSize(hologram));
        })) {
            destroyLine(viewerId, hologram.getId(), pageIndex, lineIndex);
            spawnItemLine(viewerId, hologram, pageIndex, lineIndex, location, itemStack, billboard);
        }
    }

    public static void spawnBlockLine(Player player, Hologram hologram, int pageIndex, int lineIndex, Location location, BlockData blockData, Billboard billboard) {
        if (player == null) {
            return;
        }
        spawnBlockLine(player.getUniqueId(), hologram, pageIndex, lineIndex, location, blockData, billboard);
    }

    public static void spawnBlockLine(UUID viewerId, Hologram hologram, int pageIndex, int lineIndex, Location location, BlockData blockData, Billboard billboard) {
        if (location == null || location.getWorld() == null) {
            return;
        }

        Location spawnLocation = location.clone();
        destroyLine(viewerId, hologram.getId(), pageIndex, lineIndex);
        scheduler().runAtLocation(spawnLocation, () -> {
            if (spawnLocation.getWorld() == null) {
                return;
            }

            BlockDisplay display = spawnLocation.getWorld().spawn(spawnLocation, BlockDisplay.class, entity -> {
                configureDisplay(entity, hologram, spawnLocation, billboard);
                entity.setBlock(blockData);
            });

            trackLine(viewerId, hologram.getId(), pageIndex, lineIndex, display);
            syncInteraction(viewerId, hologram, pageIndex, lineIndex, spawnLocation,
                    resolveDisplayInteractionSize(hologram),
                    resolveDisplayInteractionSize(hologram));
        });
    }

    public static void updateBlockLine(Player player, Hologram hologram, int pageIndex, int lineIndex, Location location, BlockData blockData, Billboard billboard) {
        if (player == null) {
            return;
        }
        updateBlockLine(player.getUniqueId(), hologram, pageIndex, lineIndex, location, blockData, billboard);
    }

    public static void updateBlockLine(UUID viewerId, Hologram hologram, int pageIndex, int lineIndex, Location location, BlockData blockData, Billboard billboard) {
        LineKey key = new LineKey(hologram.getId(), pageIndex, lineIndex);
        UUID entityId = playerLines(viewerId).get(key);
        if (entityId == null) {
            spawnBlockLine(viewerId, hologram, pageIndex, lineIndex, location, blockData, billboard);
            return;
        }

        if (!(Bukkit.getEntity(entityId) instanceof BlockDisplay display) || !display.isValid()) {
            destroyLine(viewerId, hologram.getId(), pageIndex, lineIndex);
            spawnBlockLine(viewerId, hologram, pageIndex, lineIndex, location, blockData, billboard);
            return;
        }

        Location targetLocation = location.clone();
        if (!scheduler().runAtEntity(display, () -> {
            updateDisplay(display, hologram, targetLocation, billboard);
            display.setBlock(blockData);
            syncInteraction(viewerId, hologram, pageIndex, lineIndex, targetLocation,
                    resolveDisplayInteractionSize(hologram),
                    resolveDisplayInteractionSize(hologram));
        })) {
            destroyLine(viewerId, hologram.getId(), pageIndex, lineIndex);
            spawnBlockLine(viewerId, hologram, pageIndex, lineIndex, location, blockData, billboard);
        }
    }

    public static boolean updateLineDisplayState(Player player, Hologram hologram, int pageIndex, int lineIndex, Location location, Billboard billboard) {
        if (player == null) {
            return false;
        }
        return updateLineDisplayState(player.getUniqueId(), hologram, pageIndex, lineIndex, location, billboard);
    }

    public static boolean updateLineDisplayState(UUID viewerId, Hologram hologram, int pageIndex, int lineIndex, Location location, Billboard billboard) {
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
        InteractionSize interactionSize = resolveExistingInteractionSize(viewerId, key, display, hologram);
        if (!scheduler().runAtEntity(display, () -> {
            updateDisplay(display, hologram, targetLocation, billboard);
            syncInteraction(viewerId, hologram, pageIndex, lineIndex, targetLocation,
                    interactionSize.width(),
                    interactionSize.height());
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

    private static void configureDisplay(Display display, Hologram hologram, Location location, Billboard billboard) {
        configureDisplay(display, hologram, location, billboard, false);
    }

    private static void configureItemDisplay(ItemDisplay display, Hologram hologram, Location location, ItemStack itemStack, Billboard billboard) {
        configureDisplay(display, hologram, location, billboard, shouldFlipItemModel(itemStack));
    }

    private static void configureDisplay(Display display, Hologram hologram, Location location, Billboard billboard, boolean flipItemModel) {
        display.setVisibleByDefault(false);
        display.setPersistent(false);
        display.setInvulnerable(true);
        display.setGravity(false);
        applyDisplayState(display, hologram, location, billboard, true, flipItemModel);
        display.setInterpolationDelay(0);
    }

    private static void updateDisplay(Display display, Hologram hologram, Location location, Billboard billboard) {
        applyDisplayState(display, hologram, location, billboard, true);
    }

    private static void updateItemDisplay(ItemDisplay display, Hologram hologram, Location location, ItemStack itemStack, Billboard billboard) {
        applyDisplayState(display, hologram, location, billboard, true, shouldFlipItemModel(itemStack));
    }

    private static void applyDisplayState(Display display, Hologram hologram, Location location, Billboard billboard, boolean allowTeleport) {
        applyDisplayState(display, hologram, location, billboard, allowTeleport, false);
    }

    private static void applyDisplayState(Display display, Hologram hologram, Location location, Billboard billboard, boolean allowTeleport, boolean flipItemModel) {
        if (location == null || location.getWorld() == null) {
            return;
        }

        RenderedDisplayAnimation renderedAnimation = renderDisplayAnimation(hologram, location);
        Location targetLocation = renderedAnimation.location();
        if (allowTeleport && shouldTeleport(display, targetLocation)) {
            display.teleportAsync(targetLocation);
        }

        if (shouldUpdateRotation(display, targetLocation)) {
            display.setRotation(targetLocation.getYaw(), targetLocation.getPitch());
        }

        display.setInterpolationDelay(0);
        display.setInterpolationDuration(renderedAnimation.interpolationDuration());
        display.setBillboard(billboard.toPaperBillboard());
        display.setViewRange(resolveViewRange(hologram));
        display.setShadowRadius(hologram.getShadowRadius());
        display.setShadowStrength(hologram.getShadowStrength());
        display.setTransformation(buildTransformation(hologram, renderedAnimation.scaleMultiplier(), renderedAnimation.rollOffset(), flipItemModel));

        if (hologram.getBrightnessBlock() >= 0 || hologram.getBrightnessSky() >= 0) {
            int block = hologram.getBrightnessBlock() >= 0 ? hologram.getBrightnessBlock() : 15;
            int sky = hologram.getBrightnessSky() >= 0 ? hologram.getBrightnessSky() : 15;
            display.setBrightness(new Display.Brightness(block, sky));
        }
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
        Location interactionLocation = resolveInteractionLocation(resolveAnimatedLocation(hologram, location), height);
        float interactionWidth = clampInteractionDimension(width);
        float interactionHeight = clampInteractionDimension(height);
        scheduler().runAtLocation(interactionLocation, () -> {
            if (interactionLocation.getWorld() == null) {
                return;
            }

            if (interactionId != null && Bukkit.getEntity(interactionId) instanceof Interaction interaction && interaction.isValid()) {
                updateInteraction(interaction, interactionLocation, interactionWidth, interactionHeight);
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
        display.setShadowed(hologram.hasTextShadow());
        display.setSeeThrough(hologram.isSeeThrough());
        display.setAlignment(hologram.getAlignment());
        display.setTextOpacity((byte) 255);
        if (hologram.getBackgroundColor() != null) {
            display.setDefaultBackground(false);
            display.setBackgroundColor(hologram.getBackgroundColor());
        } else {
            display.setDefaultBackground(false);
            display.setBackgroundColor(null);
        }
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
        float scale = resolveTextInteractionScale(hologram);
        float width = Math.max(0.6F, (maxLineLength * 0.12F + 0.35F) * scale);
        float height = Math.max(0.35F, (0.30F + (Math.max(0, lineCount - 1) * 0.25F)) * scale);
        return new InteractionSize(width, height);
    }

    private static TextLineState createTextLineState(Component text, Hologram hologram) {
        InteractionSize interactionSize = resolveTextInteractionSize(text, hologram);
        return new TextLineState(
                normalizeText(text),
                resolveTextInteractionScale(hologram),
                interactionSize.width(),
                interactionSize.height()
        );
    }

    private static float resolveTextInteractionScale(Hologram hologram) {
        return Math.max(0.5F, hologram.getScale());
    }

    private static Component normalizeText(Component text) {
        return text == null ? Component.empty() : text;
    }

    private static float resolveDisplayInteractionSize(Hologram hologram) {
        return Math.max(0.75F, hologram.getScale());
    }

    private static InteractionSize resolveExistingInteractionSize(UUID viewerId, LineKey key, Display display, Hologram hologram) {
        if (display instanceof TextDisplay textDisplay) {
            TextLineState textState = getTextLineState(viewerId, key);
            if (textState != null) {
                return new InteractionSize(textState.interactionWidth(), textState.interactionHeight());
            }

            Component text = textDisplay.text();
            return resolveTextInteractionSize(text, hologram);
        }

        float interactionSize = resolveDisplayInteractionSize(hologram);
        return new InteractionSize(interactionSize, interactionSize);
    }

    private static Location resolveInteractionLocation(Location location, float height) {
        return location.clone().subtract(0.0D, height * 0.5D, 0.0D);
    }

    private static float clampInteractionDimension(float value) {
        return Math.max(0.1F, Math.min(value, 32.0F));
    }

    private static Transformation buildTransformation(Hologram hologram, float scaleMultiplier, float rollOffset, boolean flipItemModel) {
        float multiplier = Math.max(0.01F, scaleMultiplier);
        return new Transformation(
                new Vector3f(0.0F, 0.0F, 0.0F),
                new Quaternionf().rotateZ((float) Math.toRadians(rollOffset)),
                new Vector3f(
                        hologram.getScaleX() * multiplier,
                        hologram.getScaleY() * multiplier,
                        hologram.getScaleZ() * multiplier
                ),
                flipItemModel ? new Quaternionf().rotateY((float) Math.PI) : new Quaternionf()
        );
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

    private static Location resolveAnimatedLocation(Hologram hologram, Location location) {
        return renderDisplayAnimation(hologram, location).location();
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

    private static org.axostudio.axohologram.util.SchedulerUtil scheduler() {
        return AxoHologram.getInstance().getSchedulerUtil();
    }
}
