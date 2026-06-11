package org.axostudio.axohologram.command;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.axostudio.axohologram.AxoHologram;
import org.axostudio.axohologram.hologram.Hologram;
import org.axostudio.axohologram.hologram.action.HologramAction;
import org.axostudio.axohologram.hologram.action.HologramActionExecutor;
import org.axostudio.axohologram.hologram.action.HologramActionType;
import org.axostudio.axohologram.hologram.action.HologramClickType;
import org.axostudio.axohologram.hologram.billboard.Billboard;
import org.axostudio.axohologram.hologram.impl.AxoHologramImpl;
import org.axostudio.axohologram.hologram.line.HologramLine;
import org.axostudio.axohologram.hologram.line.LineType;
import org.axostudio.axohologram.hologram.line.impl.BlockLineImpl;
import org.axostudio.axohologram.hologram.line.impl.ItemLineImpl;
import org.axostudio.axohologram.hologram.line.impl.TextLineImpl;
import org.axostudio.axohologram.hologram.page.HologramPage;
import org.axostudio.axohologram.hologram.page.impl.AxoHologramPageImpl;
import org.axostudio.axohologram.hologram.visibility.VisibilityMode;
import org.axostudio.axohologram.importer.HologramImporter;
import org.axostudio.axohologram.importer.ImportResult;
import org.axostudio.axohologram.media.MapFrameData;
import org.axostudio.axohologram.media.MediaHologram;
import org.axostudio.axohologram.media.MediaSettings;
import org.axostudio.axohologram.media.MediaType;
import org.axostudio.axohologram.media.ProcessedMedia;
import org.axostudio.axohologram.util.ColorUtil;
import org.axostudio.axohologram.util.MessageUtil;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Vector;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class HologramCommand implements BasicCommand {

    private static final List<String> PAGE_ACTIONS = List.of("add", "delete", "remove", "default", "set");
    private static final List<String> LINE_ACTIONS = List.of("add", "delete", "set", "offset", "height", "scale");
    private static final List<String> NPC_ACTIONS = List.of("link", "unlink", "info");
    private static final List<String> ACTION_ACTIONS = List.of("add", "remove", "list");
    private static final List<String> BACKUP_ACTIONS = List.of("create", "restore");
    private static final List<String> MEDIA_CREATE_TYPES = List.of("text", "item", "block", "image", "video");
    private static final List<String> VISIBILITY_MODES = List.of("all", "manual", "permission");
    private static final List<String> SHADOW_ACTIONS = List.of("strength", "radius");
    private static final List<String> ALIGNMENTS = List.of("center", "left", "right");
    private static final List<String> BOOLEAN_VALUES = List.of("true", "false");
    private static final List<String> CLICK_TYPES = List.of("left", "right", "any_click");
    private static final List<String> PAGE_ACTION_VALUES = List.of("next", "previous", "1", "2");
    private static final List<String> COLOR_SUGGESTIONS = ColorUtil.commonColorSuggestions();
    private static final List<String> ROOT_ADMIN_SUBCOMMANDS = List.of(
            "version",
            "ver",
            "create",
            "clone",
            "delete",
            "remove",
            "movehere",
            "moveto",
            "position",
            "center",
            "rotate",
            "rotatepitch",
            "offset",
            "translate",
            "teleport",
            "list",
            "info",
            "import",
            "reload",
            "play",
            "pause",
            "stop",
            "backup",
            "page",
            "line",
            "addline",
            "setline",
            "removeline",
            "insertbefore",
            "insertafter",
            "permission",
            "npc",
            "linkwithnpc",
            "unlinkwithnpc",
            "viewdistance",
            "visibilitydistance",
            "visibility",
            "scale",
            "resize",
            "size",
            "billboard",
            "shadow",
            "shadowstrength",
            "shadowradius",
            "background",
            "textshadow",
            "seethrough",
            "brightness",
            "align",
            "textalignment",
            "updatetextinterval",
            "action"
    );
    private static final List<String> LINE_TYPE_NAMES = createLineTypeNames();
    private static final Set<String> EXACT_LINE_TYPE_NAMES = Set.copyOf(LINE_TYPE_NAMES);
    private static final List<String> BILLBOARD_NAMES = createBillboardNames();
    private static final List<String> ACTION_TYPE_NAMES = createActionTypeNames();
    private static final List<String> ITEM_MATERIAL_SUGGESTIONS = createMaterialSuggestions(false);
    private static final List<String> BLOCK_MATERIAL_SUGGESTIONS = createMaterialSuggestions(true);
    private static final List<String> DEFAULT_LINE_HEIGHT_SUGGESTIONS = List.of("default", "0", "0.25", "0.65", "1.0");
    private static final List<String> DEFAULT_LINE_SCALE_SUGGESTIONS = List.of("default", "1", "1.5", "2");
    private static final List<String> BRIGHTNESS_CHANNELS = List.of("block", "sky");
    private static final List<String> SHADOW_VALUE_SUGGESTIONS = List.of("0.0", "0.5", "1.0");
    private static final List<String> HOLOGRAM_SCALE_SUGGESTIONS = List.of("0.5", "1.0", "2.0");
    private static final List<String> MEDIA_SIZE_SUGGESTIONS = List.of("1", "2", "3", "4", "6", "8");
    private static final List<String> UPDATE_TEXT_INTERVAL_SUGGESTIONS = List.of("2s", "40", "5s", "100", "default");

    private final AxoHologram plugin;

    public HologramCommand(AxoHologram plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        if (args.length == 0) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("root-usage"));
            return;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create" -> handleCreate(sender, args);
            case "clone", "copy" -> handleClone(sender, args);
            case "delete", "remove" -> handleDelete(sender, args);
            case "move", "movehere" -> handleMoveHere(sender, args);
            case "moveto" -> handleMoveTo(sender, args);
            case "position" -> handlePosition(sender, args);
            case "center" -> handleCenter(sender, args);
            case "rotate" -> handleRotate(sender, args);
            case "rotatepitch" -> handleRotatePitch(sender, args);
            case "offset", "translate" -> handleOffset(sender, args);
            case "teleport" -> handleTeleport(sender, args);
            case "list" -> handleList(sender, args);
            case "info" -> handleInfo(sender, args);
            case "import" -> handleImport(sender, args);
            case "reload" -> handleReload(sender, args);
            case "play" -> handlePlay(sender, args);
            case "pause" -> handlePause(sender, args);
            case "stop" -> handleStop(sender, args);
            case "backup" -> handleBackup(sender, args);
            case "version", "ver" -> handleVersion(sender);
            case "page" -> handlePage(sender, args);
            case "line" -> handleLine(sender, args);
            case "addline" -> handleAddLineAlias(sender, args);
            case "setline" -> handleSetLineAlias(sender, args);
            case "removeline", "deleteline" -> handleRemoveLineAlias(sender, args);
            case "insertbefore" -> handleInsertLineAlias(sender, args, false);
            case "insertafter" -> handleInsertLineAlias(sender, args, true);
            case "permission" -> handlePermission(sender, args);
            case "npc" -> handleNpc(sender, args);
            case "linkwithnpc" -> handleNpcLinkAlias(sender, args);
            case "unlinkwithnpc" -> handleNpcUnlinkAlias(sender, args);
            case "viewdistance", "visibilitydistance" -> handleViewDistance(sender, args);
            case "visibility" -> handleVisibility(sender, args);
            case "scale" -> handleScale(sender, args);
            case "resize", "size" -> handleResize(sender, args);
            case "billboard" -> handleBillboard(sender, args);
            case "shadow" -> handleShadow(sender, args);
            case "shadowstrength" -> handleShadowAlias(sender, args, true);
            case "shadowradius" -> handleShadowAlias(sender, args, false);
            case "background" -> handleBackground(sender, args);
            case "textshadow" -> handleTextShadow(sender, args);
            case "seethrough" -> handleSeeThrough(sender, args);
            case "brightness" -> handleBrightness(sender, args);
            case "align", "textalignment" -> handleAlign(sender, args);
            case "updatetextinterval" -> handleUpdateTextInterval(sender, args);
            case "action" -> handleAction(sender, args);
            default -> MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("unknown-subcommand"));
        }
    }

    private void handleCreate(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("create-usage"));
            return;
        }

        MediaType mediaType = parseMediaCreateType(args[1]);
        if (mediaType != null) {
            handleCreateMedia(sender, player, args, mediaType);
            return;
        }

        if (!requirePermission(sender, "axohologram.create")) {
            return;
        }

        LineType createType = LineType.TEXT;
        String id;
        if (args.length >= 3) {
            LineType parsedType = parseCreateType(args[1]);
            if (parsedType != null) {
                createType = parsedType;
                id = args[2];
            } else {
                MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("create-usage"));
                return;
            }
        } else {
            if (isCreateTypeName(args[1])) {
                MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("create-usage"));
                return;
            }
            id = args[1];
        }

        if (!plugin.getHologramManager().isValidHologramId(id)) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("invalid-hologram-id").replace("<hologram_id>", id));
            return;
        }
        if (plugin.getHologramManager().getHologram(id) != null
                || (plugin.getMediaManager() != null && plugin.getMediaManager().getHologram(id) != null)) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("create-fail-exists").replace("<hologram_id>", id));
            return;
        }

        Hologram hologram = plugin.getHologramManager().createHologram(id, createType, player.getLocation());
        if (hologram == null) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("create-fail-exists").replace("<hologram_id>", id));
            return;
        }

        applyCreateMessageToHologram(hologram, createType);
        MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("create-success")
                .replace("<hologram_id>", id)
                .replace("<type>", createType.name().toLowerCase(Locale.ROOT)));
        String nextStepKey = switch (createType) {
            case TEXT -> "create-next-step-text";
            case ITEM -> "create-next-step-item";
            case BLOCK -> "create-next-step-block";
        };
        MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString(nextStepKey).replace("<hologram_id>", id));
    }

    private void handleCreateMedia(CommandSender sender, Player player, String[] args, MediaType mediaType) {
        String permission = mediaType == MediaType.VIDEO ? "axohologram.create.video" : "axohologram.create.image";
        if (!requirePermission(sender, permission, "axohologram.create")) {
            return;
        }
        if (args.length < 4) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString(
                    mediaType == MediaType.VIDEO ? "media-create-video-usage" : "media-create-image-usage"));
            return;
        }
        if (plugin.getMediaManager() == null) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("media-system-disabled"));
            return;
        }

        String id = args[2];
        String url = args[3];
        if (!plugin.getHologramManager().isValidHologramId(id)) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("invalid-hologram-id").replace("<hologram_id>", id));
            return;
        }
        if (plugin.getHologramManager().getHologram(id) != null || plugin.getMediaManager().getHologram(id) != null) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("create-fail-exists").replace("<hologram_id>", id));
            return;
        }

        MediaSettings settings = MediaSettings.defaults(mediaType, plugin.getConfigManager().getMedia());
        Location mediaLocation = player.getLocation();
        mediaLocation.setPitch(0.0F);
        CompletableFuture<MediaHologram> future = mediaType == MediaType.VIDEO
                ? plugin.getMediaManager().createVideoHologram(id, url, mediaLocation, settings)
                : plugin.getMediaManager().createImageHologram(id, url, mediaLocation, settings);

        MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("media-create-started")
                .replace("<hologram_id>", id)
                .replace("<type>", mediaType.name().toLowerCase(Locale.ROOT)));
        future.whenComplete((hologram, throwable) -> sendAsyncCommandMessage(sender, throwable == null
                ? plugin.getConfigManager().getMessages().getString("media-create-success")
                .replace("<hologram_id>", id)
                .replace("<type>", mediaType.name().toLowerCase(Locale.ROOT))
                : plugin.getConfigManager().getMessages().getString("media-create-failed")
                .replace("<hologram_id>", id)
                .replace("<reason>", rootCauseMessage(throwable))));
    }

    private void handleDelete(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "axohologram.remove", "axohologram.delete")) {
            return;
        }
        if (args.length < 2) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("delete-usage"));
            return;
        }

        if (plugin.getMediaManager() != null && plugin.getMediaManager().removeHologram(args[1])) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("delete-success").replace("<hologram_id>", args[1]));
            return;
        }

        Hologram hologram = requireHologram(sender, args[1]);
        if (hologram == null) {
            return;
        }

        plugin.getHologramManager().deleteHologram(hologram.getId());
        MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("delete-success").replace("<hologram_id>", hologram.getId()));
    }

    private void handleClone(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "axohologram.create")) {
            return;
        }
        if (args.length < 3) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("clone-usage"));
            return;
        }

        Hologram sourceHologram = requireHologram(sender, args[1]);
        if (sourceHologram == null) {
            return;
        }

        String targetId = args[2];
        if (!plugin.getHologramManager().isValidHologramId(targetId)) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("invalid-hologram-id").replace("<hologram_id>", targetId));
            return;
        }
        if (plugin.getHologramManager().getHologram(targetId) != null
                || (plugin.getMediaManager() != null && plugin.getMediaManager().getHologram(targetId) != null)) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("create-fail-exists").replace("<hologram_id>", targetId));
            return;
        }

        YamlConfiguration serialized = new YamlConfiguration();
        sourceHologram.serialize(serialized);
        Hologram clonedHologram = AxoHologramImpl.deserialize(targetId, serialized, plugin);
        if (clonedHologram == null || !plugin.getHologramManager().registerImportedHologram(clonedHologram, false)) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("clone-fail")
                    .replace("<source_hologram_id>", sourceHologram.getId())
                    .replace("<target_hologram_id>", targetId));
            return;
        }

        MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("clone-success")
                .replace("<source_hologram_id>", sourceHologram.getId())
                .replace("<target_hologram_id>", targetId));
    }

    private void handleMoveHere(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null || !requirePermission(sender, "axohologram.command.edit", "axohologram.hologram.move", "axohologram.edit")) {
            return;
        }
        if (args.length < 2) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("move-usage"));
            return;
        }

        MediaHologram mediaHologram = findMediaHologram(args[1]);
        if (mediaHologram != null) {
            Location target = player.getLocation();
            target.setPitch(0.0F);
            moveMediaHologram(mediaHologram, target);
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("move-success").replace("<hologram_id>", mediaHologram.getId()));
            return;
        }

        Hologram hologram = requireHologram(sender, args[1]);
        if (hologram == null) {
            return;
        }
        if (!requireManualPositioning(sender, hologram)) {
            return;
        }

        hologram.setLocation(player.getLocation());
        MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("move-success").replace("<hologram_id>", hologram.getId()));
    }

    private void handleMoveTo(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "axohologram.command.edit", "axohologram.hologram.move", "axohologram.edit")) {
            return;
        }
        if (args.length < 5) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("moveto-usage"));
            return;
        }

        MediaHologram mediaHologram = findMediaHologram(args[1]);
        if (mediaHologram != null) {
            moveMediaTo(sender, mediaHologram, args);
            return;
        }

        Hologram hologram = requireHologram(sender, args[1]);
        if (hologram == null) {
            return;
        }
        if (!requireManualPositioning(sender, hologram)) {
            return;
        }

        try {
            double x = Double.parseDouble(args[2]);
            double y = Double.parseDouble(args[3]);
            double z = Double.parseDouble(args[4]);
            Location current = hologram.getLocation();
            Player player = sender instanceof Player onlinePlayer ? onlinePlayer : null;
            if (player == null && current.getWorld() == null) {
                MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("world-unavailable").replace("<world>", hologram.getWorldName()));
                return;
            }

            Location target = new Location(
                    player != null ? player.getWorld() : current.getWorld(),
                    x,
                    y,
                    z,
                    current.getYaw(),
                    current.getPitch()
            );
            if (args.length >= 6) {
                target.setYaw(Float.parseFloat(args[5]));
            }
            if (args.length >= 7) {
                target.setPitch(Float.parseFloat(args[6]));
            }

            hologram.setLocation(target);
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("moveto-success")
                    .replace("<hologram_id>", hologram.getId())
                    .replace("<x>", formatDecimal(x))
                    .replace("<y>", formatDecimal(y))
                    .replace("<z>", formatDecimal(z)));
        } catch (NumberFormatException exception) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("invalid-location-number"));
        }
    }

    private void handlePosition(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "axohologram.command.edit", "axohologram.hologram.move", "axohologram.edit")) {
            return;
        }
        if (args.length < 2) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("position-usage"));
            return;
        }

        MediaHologram mediaHologram = findMediaHologram(args[1]);
        if (mediaHologram != null) {
            sendMediaPosition(sender, mediaHologram);
            return;
        }

        Hologram hologram = requireHologram(sender, args[1]);
        if (hologram == null) {
            return;
        }

        Location location = hologram.getLocation();
        Vector translation = hologram.getOffset();
        MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("position-info")
                .replace("<hologram_id>", hologram.getId())
                .replace("<world>", hologram.getWorldName() == null ? "unknown" : hologram.getWorldName())
                .replace("<x>", formatDecimal(location.getX()))
                .replace("<y>", formatDecimal(location.getY()))
                .replace("<z>", formatDecimal(location.getZ()))
                .replace("<yaw>", formatDecimal(location.getYaw()))
                .replace("<pitch>", formatDecimal(location.getPitch()))
                .replace("<tx>", formatDecimal(translation.getX()))
                .replace("<ty>", formatDecimal(translation.getY()))
                .replace("<tz>", formatDecimal(translation.getZ())));
    }

    private void handleCenter(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "axohologram.command.edit", "axohologram.hologram.move", "axohologram.edit")) {
            return;
        }
        if (args.length < 2) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("center-usage"));
            return;
        }

        MediaHologram mediaHologram = findMediaHologram(args[1]);
        if (mediaHologram != null) {
            Location centered = centerMediaLocation(mediaHologram);
            moveMediaHologram(mediaHologram, centered);
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("center-success")
                    .replace("<hologram_id>", mediaHologram.getId())
                    .replace("<x>", formatDecimal(centered.getX()))
                    .replace("<z>", formatDecimal(centered.getZ())));
            return;
        }

        Hologram hologram = requireHologram(sender, args[1]);
        if (hologram == null) {
            return;
        }
        if (!requireManualPositioning(sender, hologram)) {
            return;
        }

        Location centered = hologram.getLocation();
        centered.setX(Math.floor(centered.getX()) + 0.5D);
        centered.setZ(Math.floor(centered.getZ()) + 0.5D);
        hologram.setLocation(centered);
        MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("center-success")
                .replace("<hologram_id>", hologram.getId())
                .replace("<x>", formatDecimal(centered.getX()))
                .replace("<z>", formatDecimal(centered.getZ())));
    }

    private void handleRotate(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "axohologram.command.edit", "axohologram.hologram.move", "axohologram.edit")) {
            return;
        }
        if (args.length < 3) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("rotate-usage"));
            return;
        }

        MediaHologram mediaHologram = findMediaHologram(args[1]);
        if (mediaHologram != null) {
            try {
                float yaw = Float.parseFloat(args[2]);
                Location location = mediaHologram.getLocation();
                location.setYaw(yaw);
                moveMediaHologram(mediaHologram, location);
                MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("rotate-success")
                        .replace("<hologram_id>", mediaHologram.getId())
                        .replace("<degrees>", args[2]));
            } catch (NumberFormatException exception) {
                MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("invalid-rotation-number"));
            }
            return;
        }

        Hologram hologram = requireHologram(sender, args[1]);
        if (hologram == null) {
            return;
        }
        if (!requireManualPositioning(sender, hologram)) {
            return;
        }

        try {
            float yaw = Float.parseFloat(args[2]);
            Location location = hologram.getLocation();
            location.setYaw(yaw);
            hologram.setLocation(location);
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("rotate-success")
                    .replace("<hologram_id>", hologram.getId())
                    .replace("<degrees>", args[2]));
        } catch (NumberFormatException exception) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("invalid-rotation-number"));
        }
    }

    private void handleRotatePitch(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "axohologram.command.edit", "axohologram.hologram.move", "axohologram.edit")) {
            return;
        }
        if (args.length < 3) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("rotatepitch-usage"));
            return;
        }

        MediaHologram mediaHologram = findMediaHologram(args[1]);
        if (mediaHologram != null) {
            try {
                float pitch = Float.parseFloat(args[2]);
                Location location = mediaHologram.getLocation();
                location.setPitch(pitch);
                moveMediaHologram(mediaHologram, location);
                MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("rotatepitch-success")
                        .replace("<hologram_id>", mediaHologram.getId())
                        .replace("<degrees>", args[2]));
            } catch (NumberFormatException exception) {
                MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("invalid-rotation-number"));
            }
            return;
        }

        Hologram hologram = requireHologram(sender, args[1]);
        if (hologram == null) {
            return;
        }
        if (!requireManualPositioning(sender, hologram)) {
            return;
        }

        try {
            float pitch = Float.parseFloat(args[2]);
            Location location = hologram.getLocation();
            location.setPitch(pitch);
            hologram.setLocation(location);
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("rotatepitch-success")
                    .replace("<hologram_id>", hologram.getId())
                    .replace("<degrees>", args[2]));
        } catch (NumberFormatException exception) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("invalid-rotation-number"));
        }
    }

    private void handleOffset(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "axohologram.command.edit", "axohologram.hologram.move", "axohologram.edit")) {
            return;
        }
        if (args.length < 5) {
            String messageKey = args[0].equalsIgnoreCase("translate") ? "translate-usage" : "hologram-offset-usage";
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString(messageKey, plugin.getConfigManager().getMessages().getString("hologram-offset-usage")));
            return;
        }

        Hologram hologram = requireHologram(sender, args[1]);
        if (hologram == null) {
            return;
        }
        if (!requireManualPositioning(sender, hologram)) {
            return;
        }

        try {
            double x = Double.parseDouble(args[2]);
            double y = Double.parseDouble(args[3]);
            double z = Double.parseDouble(args[4]);
            hologram.setOffset(new Vector(x, y, z));
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("hologram-offset-success")
                    .replace("<hologram_id>", hologram.getId())
                    .replace("<x>", args[2])
                    .replace("<y>", args[3])
                    .replace("<z>", args[4]));
        } catch (NumberFormatException exception) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("invalid-offset-number"));
        }
    }

    private void handleTeleport(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null || !requirePermission(sender, "axohologram.teleport")) {
            return;
        }
        if (args.length < 2) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("teleport-usage"));
            return;
        }

        Hologram hologram = requireHologram(sender, args[1]);
        if (hologram == null) {
            return;
        }

        Location location = hologram.getLocation();
        if (location.getWorld() == null) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("world-unavailable").replace("<world>", hologram.getWorldName()));
            return;
        }

        player.teleportAsync(location);
        MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("teleport-success").replace("<hologram_id>", hologram.getId()));
    }

    private void handleList(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "axohologram.list")) {
            return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("menu")) {
            Player player = requirePlayer(sender);
            if (player != null) {
                plugin.getHologramListMenu().open(player, 0);
            }
            return;
        }

        List<Hologram> holograms = new ArrayList<>(plugin.getHologramManager().getAllHolograms());
        List<MediaHologram> mediaHolograms = plugin.getMediaManager() == null
                ? List.of()
                : new ArrayList<>(plugin.getMediaManager().getAllMediaHolograms());
        if (holograms.isEmpty() && mediaHolograms.isEmpty()) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("list-empty"));
            return;
        }

        MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("list-header").replace("<count>", String.valueOf(holograms.size() + mediaHolograms.size())));
        for (Hologram hologram : holograms) {
            Location location = hologram.getLocation();
            String worldName = hologram.getWorldName() == null ? "unknown" : hologram.getWorldName();
            String locationString = String.format(Locale.US, "%.1f, %.1f, %.1f in %s", location.getX(), location.getY(), location.getZ(), worldName);
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("list-entry")
                    .replace("<hologram_id>", hologram.getId())
                    .replace("<location>", locationString));
        }
        for (MediaHologram hologram : mediaHolograms) {
            Location location = hologram.getLocation();
            String worldName = hologram.getWorldName() == null ? "unknown" : hologram.getWorldName();
            String locationString = String.format(Locale.US, "%.1f, %.1f, %.1f in %s", location.getX(), location.getY(), location.getZ(), worldName);
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("media-list-entry")
                    .replace("<hologram_id>", hologram.getId())
                    .replace("<type>", hologram.getType().name())
                    .replace("<state>", hologram.getState().name())
                    .replace("<location>", locationString));
        }
    }

    private void handleImport(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "axohologram.import")) {
            return;
        }
        if (args.length < 2) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("import-usage"));
            return;
        }

        String source = args[1].toLowerCase(Locale.ROOT);
        if (source.equals("auto")) {
            ImportResult result = plugin.getImportManager().importAuto();
            sendImportResult(sender, result);
            return;
        }

        if (args.length < 3) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("import-usage"));
            return;
        }

        HologramImporter importer = plugin.getImportManager().importer(source);
        if (importer == null) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("import-source-not-found")
                    .replace("<source>", args[1]));
            return;
        }
        if (!importer.isAvailable()) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("import-source-unavailable")
                    .replace("<source>", importer.displayName()));
            return;
        }

        ImportResult result = args[2].equalsIgnoreCase("all")
                ? importer.importAll()
                : importer.importHologram(args[2]);
        sendImportResult(sender, result);
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "axohologram.info", "axohologram.list")) {
            return;
        }
        if (args.length < 2) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("info-usage"));
            return;
        }

        String id = args[1];
        MediaHologram media = plugin.getMediaManager() == null ? null : plugin.getMediaManager().getHologram(id);
        if (media != null) {
            sendMediaInfo(sender, media);
            return;
        }

        Hologram hologram = requireHologram(sender, id);
        if (hologram == null) {
            return;
        }
        Location location = hologram.getLocation();
        MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("info-normal")
                .replace("<hologram_id>", hologram.getId())
                .replace("<world>", hologram.getWorldName() == null ? "unknown" : hologram.getWorldName())
                .replace("<x>", formatDecimal(location.getX()))
                .replace("<y>", formatDecimal(location.getY()))
                .replace("<z>", formatDecimal(location.getZ()))
                .replace("<pages>", String.valueOf(hologram.getPages().size()))
                .replace("<view_distance>", hologram.getViewDistance() > 0 ? String.valueOf(hologram.getViewDistance()) : "default"));
    }

    private void handleReload(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "axohologram.reload")) {
            return;
        }

        if (args.length >= 2) {
            String id = args[1];
            boolean reloaded = (plugin.getMediaManager() != null && plugin.getMediaManager().reloadHologram(id))
                    || plugin.getHologramManager().reloadHologram(id);
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString(reloaded ? "reload-id-success" : "reload-id-failed")
                    .replace("<hologram_id>", id));
            return;
        }

        plugin.reloadPluginState();
        MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("reload-success"));
    }

    private void handlePlay(CommandSender sender, String[] args) {
        handleVideoControl(sender, args, "axohologram.video.play", "video-play-usage", "video-play-success", "play");
    }

    private void handlePause(CommandSender sender, String[] args) {
        handleVideoControl(sender, args, "axohologram.video.pause", "video-pause-usage", "video-pause-success", "pause");
    }

    private void handleStop(CommandSender sender, String[] args) {
        handleVideoControl(sender, args, "axohologram.video.stop", "video-stop-usage", "video-stop-success", "stop");
    }

    private void handleVideoControl(CommandSender sender, String[] args, String permission, String usageKey, String successKey, String action) {
        if (!requirePermission(sender, permission)) {
            return;
        }
        if (args.length < 2) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString(usageKey));
            return;
        }

        boolean success = switch (action) {
            case "play" -> plugin.getMediaManager() != null && plugin.getMediaManager().playVideo(args[1]);
            case "pause" -> plugin.getMediaManager() != null && plugin.getMediaManager().pauseVideo(args[1]);
            case "stop" -> plugin.getMediaManager() != null && plugin.getMediaManager().stopVideo(args[1]);
            default -> false;
        };
        if (!success) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("media-video-not-ready").replace("<hologram_id>", args[1]));
            return;
        }
        MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString(successKey).replace("<hologram_id>", args[1]));
    }

    private void handleBackup(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "axohologram.admin")) {
            return;
        }
        if (args.length < 2) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("backup-usage"));
            return;
        }

        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "create" -> {
                try {
                    String name = plugin.getBackupManager().createBackup().getName();
                    MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("backup-create-success")
                            .replace("<file>", name));
                } catch (IOException exception) {
                    MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("backup-create-failed")
                            .replace("<reason>", exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage()));
                }
            }
            case "restore" -> {
                if (args.length < 3) {
                    MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("backup-restore-usage"));
                    return;
                }
                boolean exists = plugin.getBackupManager().restoreBackup(args[2]);
                MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString(exists ? "backup-restore-prepared" : "backup-restore-missing")
                        .replace("<file>", args[2]));
            }
            default -> MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("backup-usage"));
        }
    }

    private void handleVersion(CommandSender sender) {
        MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("version-info")
                .replace("<version>", plugin.getPluginMeta().getVersion()));
    }

    private void handleNpc(CommandSender sender, String[] args) {
        if (args.length < 2) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("npc-usage"));
            return;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "link" -> handleNpcLink(sender, args);
            case "unlink" -> handleNpcUnlink(sender, args);
            case "info" -> handleNpcInfo(sender, args);
            default -> MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("npc-usage"));
        }
    }

    private void handleNpcLinkAlias(CommandSender sender, String[] args) {
        if (args.length < 3) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("linkwithnpc-usage"));
            return;
        }

        String[] delegated = new String[args.length + 1];
        delegated[0] = "npc";
        delegated[1] = "link";
        delegated[2] = args[1];
        System.arraycopy(args, 2, delegated, 3, args.length - 2);
        handleNpcLink(sender, delegated);
    }

    private void handleNpcUnlinkAlias(CommandSender sender, String[] args) {
        if (args.length < 2) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("unlinkwithnpc-usage"));
            return;
        }

        handleNpcUnlink(sender, new String[]{"npc", "unlink", args[1]});
    }

    private void handleNpcLink(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "axohologram.command.npc", "axohologram.npc.edit")) {
            return;
        }
        if (args.length < 4) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("npc-link-usage"));
            return;
        }
        if (plugin.getNpcLinkService() == null || !plugin.getNpcLinkService().isAvailable()) {
            String unavailableMessage = plugin.getConfigManager().getMessages().getString(
                    "npc-plugin-unavailable",
                    plugin.getConfigManager().getMessages().getString("fancynpcs-unavailable")
            );
            MessageUtil.sendMessage(sender, unavailableMessage);
            return;
        }

        Hologram hologram = requireHologram(sender, args[2]);
        if (hologram == null) {
            return;
        }

        String npcName = String.join(" ", Arrays.copyOfRange(args, 3, args.length)).trim();
        if (!plugin.getNpcLinkService().link(hologram, npcName)) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("npc-not-found").replace("<npc_name>", npcName));
            return;
        }

        String npcPlugin = plugin.getNpcLinkService().getProviderName(npcName);
        String successMessage = plugin.getConfigManager().getMessages().getString(
                "npc-link-success-provider",
                "<prefix><green>Linked hologram '<hologram_id>' to <npc_plugin> NPC '<npc_name>'.</green>"
        );
        MessageUtil.sendMessage(sender, successMessage
                .replace("<hologram_id>", hologram.getId())
                .replace("<npc_plugin>", npcPlugin)
                .replace("<npc_name>", npcName));
    }

    private void handleNpcUnlink(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "axohologram.command.npc", "axohologram.npc.edit")) {
            return;
        }
        if (args.length < 3) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("npc-unlink-usage"));
            return;
        }

        Hologram hologram = requireHologram(sender, args[2]);
        if (hologram == null) {
            return;
        }

        if (plugin.getNpcLinkService() != null) {
            plugin.getNpcLinkService().unlink(hologram);
        } else {
            hologram.setLinkedNpc(null);
        }

        MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("npc-unlink-success")
                .replace("<hologram_id>", hologram.getId()));
    }

    private void handleNpcInfo(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "axohologram.npc.info", "axohologram.command.npc", "axohologram.npc.edit")) {
            return;
        }
        if (args.length < 3) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("npc-info-usage"));
            return;
        }

        Hologram hologram = requireHologram(sender, args[2]);
        if (hologram == null) {
            return;
        }

        String linkedNpc = hologram.getLinkedNpc();
        if (linkedNpc == null) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("npc-info-unlinked")
                    .replace("<hologram_id>", hologram.getId()));
            return;
        }

        if (plugin.getNpcLinkService() != null && plugin.getNpcLinkService().isLinkedNpcAvailable(hologram)) {
            String npcPlugin = plugin.getNpcLinkService().getProviderName(linkedNpc);
            String linkedMessage = plugin.getConfigManager().getMessages().getString(
                    "npc-info-linked-provider",
                    "<prefix><aqua>Hologram '<hologram_id>' is linked to <npc_plugin> NPC '<npc_name>'.</aqua>"
            );
            MessageUtil.sendMessage(sender, linkedMessage
                    .replace("<hologram_id>", hologram.getId())
                    .replace("<npc_plugin>", npcPlugin)
                    .replace("<npc_name>", linkedNpc));
            return;
        }

        MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("npc-info-missing")
                .replace("<npc_name>", linkedNpc));
    }

    private void handlePage(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "axohologram.command.edit", "axohologram.page.edit", "axohologram.edit")) {
            return;
        }
        if (args.length < 3) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("page-usage"));
            return;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        Hologram hologram = requireHologram(sender, args[2]);
        if (hologram == null) {
            return;
        }

        switch (action) {
            case "add" -> {
                int pageNumber = hologram.getPages().size() + 1;
                hologram.addPage(new AxoHologramPageImpl());
                MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("page-add-success")
                        .replace("<hologram_id>", hologram.getId())
                        .replace("<page_number>", String.valueOf(pageNumber)));
            }
            case "remove", "delete" -> {
                if (args.length < 4) {
                    MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("page-remove-usage"));
                    return;
                }

                Integer pageNumber = parsePositiveInt(sender, args[3], "invalid-page-number");
                if (pageNumber == null) {
                    return;
                }
                if (hologram.getPages().size() <= 1) {
                    MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("page-remove-last"));
                    return;
                }

                int pageIndex = pageNumber - 1;
                if (hologram.getPage(pageIndex) == null) {
                    MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("page-not-found")
                            .replace("<hologram_id>", hologram.getId())
                            .replace("<page_number>", String.valueOf(pageNumber)));
                    return;
                }

                hologram.removePage(pageIndex);
                MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("page-remove-success")
                        .replace("<hologram_id>", hologram.getId())
                        .replace("<page_number>", String.valueOf(pageNumber)));
            }
            case "set", "default" -> {
                if (args.length < 4) {
                    MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("page-set-usage"));
                    return;
                }

                Integer pageNumber = parsePositiveInt(sender, args[3], "invalid-page-number");
                if (pageNumber == null) {
                    return;
                }

                int pageIndex = pageNumber - 1;
                if (hologram.getPage(pageIndex) == null) {
                    MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("page-not-found")
                            .replace("<hologram_id>", hologram.getId())
                            .replace("<page_number>", String.valueOf(pageNumber)));
                    return;
                }

                hologram.setDefaultPageIndex(pageIndex);
                MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("page-set-success")
                        .replace("<hologram_id>", hologram.getId())
                        .replace("<page_number>", String.valueOf(pageNumber)));
            }
            default -> MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("page-usage"));
        }
    }

    private void handleLine(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "axohologram.command.line", "axohologram.line.edit", "axohologram.edit")) {
            return;
        }
        if (args.length < 4) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("line-usage"));
            return;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        Hologram hologram = requireHologram(sender, args[2]);
        if (hologram == null) {
            return;
        }

        Integer pageNumber = parsePositiveInt(sender, args[3], "invalid-page-number");
        if (pageNumber == null) {
            return;
        }

        HologramPage page = hologram.getPage(pageNumber - 1);
        if (page == null) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("page-not-found")
                    .replace("<hologram_id>", hologram.getId())
                    .replace("<page_number>", String.valueOf(pageNumber)));
            return;
        }

        switch (action) {
            case "add" -> handleLineAdd(sender, args, hologram, page, pageNumber);
            case "remove", "delete" -> handleLineRemove(sender, args, hologram, page, pageNumber);
            case "set" -> handleLineSet(sender, args, hologram, page, pageNumber);
            case "offset" -> handleLineOffset(sender, args, hologram, page, pageNumber);
            case "height" -> handleLineHeight(sender, args, hologram, page, pageNumber);
            case "scale" -> handleLineScale(sender, args, hologram, page, pageNumber);
            default -> MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("line-usage"));
        }
    }

    private void handleAddLineAlias(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "axohologram.command.line", "axohologram.line.edit", "axohologram.edit")) {
            return;
        }
        if (args.length < 5) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("addline-usage"));
            return;
        }

        Hologram hologram = requireHologram(sender, args[1]);
        if (hologram == null) {
            return;
        }

        Integer pageNumber = parsePositiveInt(sender, args[2], "invalid-page-number");
        if (pageNumber == null) {
            return;
        }

        HologramPage page = requirePage(sender, hologram, pageNumber);
        if (page == null) {
            return;
        }

        handleLineAdd(sender, new String[]{"line", "add", hologram.getId(), String.valueOf(pageNumber), args[3], joinArgs(args, 4)}, hologram, page, pageNumber);
    }

    private void handleSetLineAlias(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "axohologram.command.line", "axohologram.line.edit", "axohologram.edit")) {
            return;
        }
        if (args.length < 5) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("setline-usage"));
            return;
        }

        Hologram hologram = requireHologram(sender, args[1]);
        if (hologram == null) {
            return;
        }

        Integer pageNumber = parsePositiveInt(sender, args[2], "invalid-page-number");
        if (pageNumber == null) {
            return;
        }

        HologramPage page = requirePage(sender, hologram, pageNumber);
        if (page == null) {
            return;
        }

        handleLineSet(sender, new String[]{"line", "set", hologram.getId(), String.valueOf(pageNumber), args[3], joinArgs(args, 4)}, hologram, page, pageNumber);
    }

    private void handleRemoveLineAlias(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "axohologram.command.line", "axohologram.line.edit", "axohologram.edit")) {
            return;
        }
        if (args.length < 4) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("removeline-usage"));
            return;
        }

        Hologram hologram = requireHologram(sender, args[1]);
        if (hologram == null) {
            return;
        }

        Integer pageNumber = parsePositiveInt(sender, args[2], "invalid-page-number");
        if (pageNumber == null) {
            return;
        }

        HologramPage page = requirePage(sender, hologram, pageNumber);
        if (page == null) {
            return;
        }

        handleLineRemove(sender, new String[]{"line", "delete", hologram.getId(), String.valueOf(pageNumber), args[3]}, hologram, page, pageNumber);
    }

    private void handleInsertLineAlias(CommandSender sender, String[] args, boolean after) {
        if (!requirePermission(sender, "axohologram.command.line", "axohologram.line.edit", "axohologram.edit")) {
            return;
        }
        String usageKey = after ? "insertafter-usage" : "insertbefore-usage";
        if (args.length < 6) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString(usageKey));
            return;
        }

        Hologram hologram = requireHologram(sender, args[1]);
        if (hologram == null) {
            return;
        }

        Integer pageNumber = parsePositiveInt(sender, args[2], "invalid-page-number");
        if (pageNumber == null) {
            return;
        }

        HologramPage page = requirePage(sender, hologram, pageNumber);
        if (page == null) {
            return;
        }

        Integer lineNumber = parsePositiveInt(sender, args[3], "invalid-line-number");
        if (lineNumber == null) {
            return;
        }

        int anchorIndex = lineNumber - 1;
        if (page.getLine(anchorIndex) == null) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("line-not-found")
                    .replace("<hologram_id>", hologram.getId())
                    .replace("<page_number>", String.valueOf(pageNumber))
                    .replace("<line_number>", String.valueOf(lineNumber)));
            return;
        }

        LineType lineType = parseLineType(sender, args[4]);
        if (lineType == null) {
            return;
        }

        HologramLine line = createLine(sender, lineType, joinArgs(args, 5));
        if (line == null) {
            return;
        }

        int targetIndex = after ? anchorIndex + 1 : anchorIndex;
        page.insertLine(targetIndex, line);
        markHologramContentChanged(hologram);
        plugin.getHologramManager().saveHologram(hologram);
        hologram.refreshViewers();
        plugin.getHologramManager().restartRefreshTask();
        MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString(after ? "insertafter-success" : "insertbefore-success")
                .replace("<hologram_id>", hologram.getId())
                .replace("<page_number>", String.valueOf(pageNumber))
                .replace("<line_number>", String.valueOf(lineNumber)));
    }

    private void handleLineAdd(CommandSender sender, String[] args, Hologram hologram, HologramPage page, int pageNumber) {
        if (args.length < 6) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("line-add-usage"));
            return;
        }

        LineType lineType;
        try {
            lineType = LineType.valueOf(args[4].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("invalid-line-type").replace("<type>", args[4]));
            return;
        }

        String content = String.join(" ", Arrays.copyOfRange(args, 5, args.length));
        HologramLine line = createLine(sender, lineType, content);
        if (line == null) {
            return;
        }

        page.addLine(line);
        markHologramContentChanged(hologram);
        plugin.getHologramManager().saveHologram(hologram);
        hologram.refreshViewers();
        plugin.getHologramManager().restartRefreshTask();
        MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("line-add-success")
                .replace("<hologram_id>", hologram.getId())
                .replace("<page_number>", String.valueOf(pageNumber)));
    }

    private void handleLineRemove(CommandSender sender, String[] args, Hologram hologram, HologramPage page, int pageNumber) {
        if (args.length < 5) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("line-remove-usage"));
            return;
        }

        Integer lineNumber = parsePositiveInt(sender, args[4], "invalid-line-number");
        if (lineNumber == null) {
            return;
        }

        int lineIndex = lineNumber - 1;
        if (page.getLine(lineIndex) == null) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("line-not-found")
                    .replace("<hologram_id>", hologram.getId())
                    .replace("<page_number>", String.valueOf(pageNumber))
                    .replace("<line_number>", String.valueOf(lineNumber)));
            return;
        }

        page.removeLine(lineIndex);
        markHologramContentChanged(hologram);
        plugin.getHologramManager().saveHologram(hologram);
        hologram.refreshViewers();
        plugin.getHologramManager().restartRefreshTask();
        MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("line-remove-success")
                .replace("<hologram_id>", hologram.getId())
                .replace("<page_number>", String.valueOf(pageNumber))
                .replace("<line_number>", String.valueOf(lineNumber)));
    }

    private void handleLineSet(CommandSender sender, String[] args, Hologram hologram, HologramPage page, int pageNumber) {
        if (args.length < 6) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("line-set-usage"));
            return;
        }

        Integer lineNumber = parsePositiveInt(sender, args[4], "invalid-line-number");
        if (lineNumber == null) {
            return;
        }

        int lineIndex = lineNumber - 1;
        HologramLine line = page.getLine(lineIndex);
        String content = String.join(" ", Arrays.copyOfRange(args, 5, args.length));
        if (line == null) {
            if (lineIndex != page.getLines().size()) {
                MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("line-not-found")
                        .replace("<hologram_id>", hologram.getId())
                        .replace("<page_number>", String.valueOf(pageNumber))
                        .replace("<line_number>", String.valueOf(lineNumber)));
                return;
            }

            HologramLine newLine = createLine(sender, LineType.TEXT, content);
            if (newLine == null) {
                return;
            }
            page.setLine(lineIndex, newLine);
        } else if (!updateLineContent(sender, line, content)) {
            return;
        }

        markHologramContentChanged(hologram);
        plugin.getHologramManager().saveHologram(hologram);
        hologram.refreshViewers();
        plugin.getHologramManager().restartRefreshTask();
        MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("line-set-success")
                .replace("<hologram_id>", hologram.getId())
                .replace("<page_number>", String.valueOf(pageNumber))
                .replace("<line_number>", String.valueOf(lineNumber)));
    }

    private void handleLineOffset(CommandSender sender, String[] args, Hologram hologram, HologramPage page, int pageNumber) {
        if (args.length < 8) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("offset-usage"));
            return;
        }

        Integer lineNumber = parsePositiveInt(sender, args[4], "invalid-line-number");
        if (lineNumber == null) {
            return;
        }

        HologramLine line = page.getLine(lineNumber - 1);
        if (line == null) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("line-not-found")
                    .replace("<hologram_id>", hologram.getId())
                    .replace("<page_number>", String.valueOf(pageNumber))
                    .replace("<line_number>", String.valueOf(lineNumber)));
            return;
        }

        try {
            double x = Double.parseDouble(args[5]);
            double y = Double.parseDouble(args[6]);
            double z = Double.parseDouble(args[7]);
            line.setOffset(new Vector(x, y, z));
            markHologramContentChanged(hologram);
            plugin.getHologramManager().saveHologram(hologram);
            hologram.refreshViewers();
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("offset-success")
                    .replace("<line_number>", String.valueOf(lineNumber))
                    .replace("<x>", args[5])
                    .replace("<y>", args[6])
                    .replace("<z>", args[7]));
        } catch (NumberFormatException exception) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("invalid-offset-number"));
        }
    }

    private void handleLineHeight(CommandSender sender, String[] args, Hologram hologram, HologramPage page, int pageNumber) {
        if (args.length < 6) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("line-height-usage"));
            return;
        }

        Integer lineNumber = parsePositiveInt(sender, args[4], "invalid-line-number");
        if (lineNumber == null) {
            return;
        }

        HologramLine line = page.getLine(lineNumber - 1);
        if (line == null) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("line-not-found")
                    .replace("<hologram_id>", hologram.getId())
                    .replace("<page_number>", String.valueOf(pageNumber))
                    .replace("<line_number>", String.valueOf(lineNumber)));
            return;
        }

        String rawHeight = args[5];
        if (rawHeight.equalsIgnoreCase("default")) {
            line.clearHeight();
        } else {
            try {
                double height = Double.parseDouble(rawHeight);
                if (height < 0.0D) {
                    MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("invalid-line-height-number"));
                    return;
                }
                line.setHeight(height);
            } catch (NumberFormatException exception) {
                MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("invalid-line-height-number"));
                return;
            }
        }

        markHologramContentChanged(hologram);
        plugin.getHologramManager().saveHologram(hologram);
        hologram.refreshViewers();
        String displayHeight = line.hasHeightOverride() ? String.valueOf(line.getHeight()) : "default";
        MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("line-height-success")
                .replace("<line_number>", String.valueOf(lineNumber))
                .replace("<height>", displayHeight));
    }

    private void handleLineScale(CommandSender sender, String[] args, Hologram hologram, HologramPage page, int pageNumber) {
        if (args.length < 6) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("line-scale-usage"));
            return;
        }

        Integer lineNumber = parsePositiveInt(sender, args[4], "invalid-line-number");
        if (lineNumber == null) {
            return;
        }

        HologramLine line = page.getLine(lineNumber - 1);
        if (line == null) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("line-not-found")
                    .replace("<hologram_id>", hologram.getId())
                    .replace("<page_number>", String.valueOf(pageNumber))
                    .replace("<line_number>", String.valueOf(lineNumber)));
            return;
        }

        String[] scaleArgs = Arrays.copyOfRange(args, 5, args.length);
        if (line instanceof ItemLineImpl itemLine) {
            if (!applyDisplayLineScale(sender, scaleArgs, itemLine::setScale, itemLine::clearScale)) {
                return;
            }
            markHologramContentChanged(hologram);
            plugin.getHologramManager().saveHologram(hologram);
            hologram.refreshViewers();
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("line-scale-success")
                    .replace("<line_number>", String.valueOf(lineNumber))
                    .replace("<x>", formatDecimal(itemLine.getScaleX()))
                    .replace("<y>", formatDecimal(itemLine.getScaleY()))
                    .replace("<z>", formatDecimal(itemLine.getScaleZ())));
            return;
        }

        if (line instanceof BlockLineImpl blockLine) {
            if (!applyDisplayLineScale(sender, scaleArgs, blockLine::setScale, blockLine::clearScale)) {
                return;
            }
            markHologramContentChanged(hologram);
            plugin.getHologramManager().saveHologram(hologram);
            hologram.refreshViewers();
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("line-scale-success")
                    .replace("<line_number>", String.valueOf(lineNumber))
                    .replace("<x>", formatDecimal(blockLine.getScaleX()))
                    .replace("<y>", formatDecimal(blockLine.getScaleY()))
                    .replace("<z>", formatDecimal(blockLine.getScaleZ())));
            return;
        }

        MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("line-scale-unsupported"));
    }

    private void handlePermission(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "axohologram.command.edit", "axohologram.permission.edit", "axohologram.edit")) {
            return;
        }
        if (args.length < 2) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("permission-usage"));
            return;
        }

        Hologram hologram = requireHologram(sender, args[1]);
        if (hologram == null) {
            return;
        }

        String permission = args.length >= 3 ? args[2] : null;
        hologram.setPermission(permission);
        if (permission == null || permission.isBlank()) {
            hologram.setVisibilityMode(VisibilityMode.ALL);
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("permission-removed").replace("<hologram_id>", hologram.getId()));
        } else {
            hologram.setVisibilityMode(VisibilityMode.PERMISSION);
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("permission-set")
                    .replace("<hologram_id>", hologram.getId())
                    .replace("<permission>", hologram.getEffectivePermission()));
        }
    }

    private void handleViewDistance(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "axohologram.command.visibility", "axohologram.hologram.visibility", "axohologram.edit")) {
            return;
        }
        if (args.length < 3) {
            String messageKey = args[0].equalsIgnoreCase("visibilitydistance") ? "visibilitydistance-usage" : "viewdistance-usage";
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString(messageKey, plugin.getConfigManager().getMessages().getString("viewdistance-usage")));
            return;
        }

        Integer distance = parseViewDistance(sender, args[2]);
        if (distance == null) {
            return;
        }

        MediaHologram mediaHologram = findMediaHologram(args[1]);
        if (mediaHologram != null) {
            handleMediaViewDistance(sender, mediaHologram, distance);
            return;
        }

        Hologram hologram = requireHologram(sender, args[1]);
        if (hologram == null) {
            return;
        }

        hologram.setViewDistance(distance);
        String displayDistance = distance < 0 ? "default" : String.valueOf(distance);
        MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("viewdistance-success")
                .replace("<hologram_id>", hologram.getId())
                .replace("<distance>", displayDistance));
    }

    private void handleMediaViewDistance(CommandSender sender, MediaHologram mediaHologram, int distance) {
        if (plugin.getMediaManager() == null) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("media-system-disabled"));
            return;
        }

        int resolvedDistance = distance < 0
                ? MediaSettings.defaults(mediaHologram.getType(), plugin.getConfigManager().getMedia()).renderDistance()
                : distance;
        if (!plugin.getMediaManager().updateRenderDistance(mediaHologram.getId(), resolvedDistance)) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("media-hologram-not-found")
                    .replace("<hologram_id>", mediaHologram.getId()));
            return;
        }

        String displayDistance = distance < 0 ? "default" : String.valueOf(resolvedDistance);
        MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("viewdistance-success")
                .replace("<hologram_id>", mediaHologram.getId())
                .replace("<distance>", displayDistance));
    }

    private void handleVisibility(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "axohologram.command.visibility", "axohologram.hologram.visibility", "axohologram.edit")) {
            return;
        }
        if (args.length < 3) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("visibility-usage"));
            return;
        }

        Hologram hologram = requireHologram(sender, args[1]);
        if (hologram == null) {
            return;
        }

        VisibilityMode visibilityMode = parseVisibilityMode(args[2]);
        if (visibilityMode == null) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("invalid-visibility-mode").replace("<mode>", args[2]));
            return;
        }

        hologram.setVisibilityMode(visibilityMode);
        String messageKey = visibilityMode == VisibilityMode.PERMISSION ? "visibility-success-permission" : "visibility-success";
        MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString(messageKey)
                .replace("<hologram_id>", hologram.getId())
                .replace("<mode>", visibilityMode.name().toLowerCase(Locale.ROOT))
                .replace("<permission>", hologram.getEffectivePermission()));
    }

    private void handleScale(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "axohologram.command.edit", "axohologram.hologram.style", "axohologram.edit")) {
            return;
        }
        if (args.length < 3) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("scale-usage"));
            return;
        }

        MediaHologram mediaHologram = findMediaHologram(args[1]);
        if (mediaHologram != null) {
            handleMediaScale(sender, mediaHologram, args[2]);
            return;
        }

        Hologram hologram = requireHologram(sender, args[1]);
        if (hologram == null) {
            return;
        }

        try {
            float scale = Float.parseFloat(args[2]);
            if (scale <= 0.0F) {
                MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("invalid-scale-number"));
                return;
            }

            hologram.setScale(scale);
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("scale-success")
                    .replace("<hologram_id>", hologram.getId())
                    .replace("<factor>", args[2]));
        } catch (NumberFormatException exception) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("invalid-scale-number"));
        }
    }

    private void handleResize(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "axohologram.command.edit", "axohologram.hologram.style", "axohologram.edit")) {
            return;
        }
        if (args.length < 4) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("media-resize-usage"));
            return;
        }

        MediaHologram mediaHologram = findMediaHologram(args[1]);
        if (mediaHologram == null) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("media-hologram-not-found").replace("<hologram_id>", args[1]));
            return;
        }

        try {
            double width = Double.parseDouble(args[2]);
            double height = Double.parseDouble(args[3]);
            if (!Double.isFinite(width) || !Double.isFinite(height) || width <= 0.0D || height <= 0.0D) {
                MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("invalid-media-size-number"));
                return;
            }

            double scale = mediaHologram.getSettings().scale();
            if (args.length >= 5) {
                scale = Double.parseDouble(args[4]);
                if (!Double.isFinite(scale) || scale <= 0.0D) {
                    MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("invalid-scale-number"));
                    return;
                }
            }

            MediaSettings settings = mediaHologram.getSettings().withDimensions(width, height);
            if (args.length >= 5) {
                settings = settings.withScale(scale);
            }

            applyMediaSettings(sender, mediaHologram, settings,
                    mediaSettingsMessage("media-resize-started", mediaHologram, settings),
                    mediaSettingsMessage("media-resize-success", mediaHologram, settings));
        } catch (NumberFormatException exception) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("invalid-media-size-number"));
        }
    }

    private void handleBillboard(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "axohologram.command.edit", "axohologram.hologram.style", "axohologram.edit")) {
            return;
        }
        if (args.length < 3) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("billboard-usage"));
            return;
        }

        Hologram hologram = requireHologram(sender, args[1]);
        if (hologram == null) {
            return;
        }

        Billboard billboard = parseBillboard(args[2]);
        if (billboard == null) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("invalid-billboard").replace("<billboard>", args[2]));
            return;
        }

        hologram.setBillboard(billboard);
        MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("billboard-success")
                .replace("<hologram_id>", hologram.getId())
                .replace("<billboard>", billboard.name().toLowerCase(Locale.ROOT)));
    }

    private void handleShadow(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "axohologram.command.edit", "axohologram.hologram.style", "axohologram.edit")) {
            return;
        }
        if (args.length < 4) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("shadow-usage"));
            return;
        }

        String shadowAction = args[1].toLowerCase(Locale.ROOT);
        Hologram hologram = requireHologram(sender, args[2]);
        if (hologram == null) {
            return;
        }

        try {
            float value = Float.parseFloat(args[3]);
            if (value < 0.0F) {
                MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("invalid-shadow-number"));
                return;
            }

            switch (shadowAction) {
                case "strength" -> {
                    hologram.setShadowStrength(value);
                    MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("shadow-strength-success")
                            .replace("<hologram_id>", hologram.getId())
                            .replace("<value>", args[3]));
                }
                case "radius" -> {
                    hologram.setShadowRadius(value);
                    MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("shadow-radius-success")
                            .replace("<hologram_id>", hologram.getId())
                            .replace("<value>", args[3]));
                }
                default -> MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("shadow-usage"));
            }
        } catch (NumberFormatException exception) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("invalid-shadow-number"));
        }
    }

    private void handleShadowAlias(CommandSender sender, String[] args, boolean strength) {
        if (args.length < 3) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString(strength ? "shadowstrength-usage" : "shadowradius-usage"));
            return;
        }

        handleShadow(sender, new String[]{"shadow", strength ? "strength" : "radius", args[1], args[2]});
    }

    private void handleBackground(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "axohologram.command.edit", "axohologram.hologram.style", "axohologram.edit")) {
            return;
        }
        if (args.length < 3) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("background-usage"));
            return;
        }

        Hologram hologram = requireHologram(sender, args[1]);
        if (hologram == null) {
            return;
        }

        try {
            Color color = ColorUtil.parseNullableColor(args[2]);
            hologram.setBackgroundColor(color);
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("background-success")
                    .replace("<hologram_id>", hologram.getId())
                    .replace("<color>", ColorUtil.toDisplayName(color)));
        } catch (IllegalArgumentException exception) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("invalid-background-color").replace("<color>", args[2]));
        }
    }

    private void handleTextShadow(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "axohologram.command.edit", "axohologram.hologram.style", "axohologram.edit")) {
            return;
        }
        if (args.length < 3) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("textshadow-usage"));
            return;
        }

        Hologram hologram = requireHologram(sender, args[1]);
        if (hologram == null) {
            return;
        }

        Boolean value = parseBoolean(args[2]);
        if (value == null) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("invalid-boolean-value").replace("<value>", args[2]));
            return;
        }

        hologram.setTextShadow(value);
        MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("textshadow-success")
                .replace("<hologram_id>", hologram.getId())
                .replace("<value>", String.valueOf(value)));
    }

    private void handleSeeThrough(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "axohologram.command.edit", "axohologram.hologram.style", "axohologram.edit")) {
            return;
        }
        if (args.length < 3) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("seethrough-usage"));
            return;
        }

        Hologram hologram = requireHologram(sender, args[1]);
        if (hologram == null) {
            return;
        }

        Boolean value = parseBoolean(args[2]);
        if (value == null) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("invalid-boolean-value").replace("<value>", args[2]));
            return;
        }

        hologram.setSeeThrough(value);
        MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("seethrough-success")
                .replace("<hologram_id>", hologram.getId())
                .replace("<value>", String.valueOf(value)));
    }

    private void handleBrightness(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "axohologram.command.edit", "axohologram.hologram.style", "axohologram.edit")) {
            return;
        }
        if (args.length < 4) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("brightness-usage"));
            return;
        }

        Hologram hologram = requireHologram(sender, args[1]);
        if (hologram == null) {
            return;
        }

        Integer value = parseLightLevel(sender, args[3]);
        if (value == null) {
            return;
        }

        switch (args[2].toLowerCase(Locale.ROOT)) {
            case "block" -> hologram.setBrightnessBlock(value);
            case "sky" -> hologram.setBrightnessSky(value);
            default -> {
                MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("brightness-usage"));
                return;
            }
        }

        MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("brightness-success")
                .replace("<hologram_id>", hologram.getId())
                .replace("<channel>", args[2].toLowerCase(Locale.ROOT))
                .replace("<value>", String.valueOf(value)));
    }

    private void handleAlign(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "axohologram.command.edit", "axohologram.hologram.style", "axohologram.edit")) {
            return;
        }
        if (args.length < 3) {
            String messageKey = args[0].equalsIgnoreCase("textalignment") ? "textalignment-usage" : "align-usage";
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString(messageKey, plugin.getConfigManager().getMessages().getString("align-usage")));
            return;
        }

        Hologram hologram = requireHologram(sender, args[1]);
        if (hologram == null) {
            return;
        }

        TextDisplay.TextAlignment alignment = parseAlignment(args[2]);
        if (alignment == null) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("invalid-alignment").replace("<alignment>", args[2]));
            return;
        }

        hologram.setAlignment(alignment);
        MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("align-success")
                .replace("<hologram_id>", hologram.getId())
                .replace("<alignment>", args[2].toLowerCase(Locale.ROOT)));
    }

    private void handleUpdateTextInterval(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "axohologram.command.edit", "axohologram.hologram.style", "axohologram.edit")) {
            return;
        }
        if (args.length < 3) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("updatetextinterval-usage"));
            return;
        }

        Hologram hologram = requireHologram(sender, args[1]);
        if (hologram == null) {
            return;
        }

        Long interval = parseUpdateInterval(sender, args[2]);
        if (interval == null) {
            return;
        }

        hologram.setUpdateTextInterval(interval);
        MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("updatetextinterval-success")
                .replace("<hologram_id>", hologram.getId())
                .replace("<interval>", interval <= 0L ? "default" : String.valueOf(interval)));
    }

    private void handleAction(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "axohologram.command.action", "axohologram.edit")) {
            return;
        }
        if (args.length < 2) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("action-usage"));
            return;
        }

        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "add" -> handleActionAdd(sender, args);
            case "remove", "delete" -> handleActionRemove(sender, args);
            case "list" -> handleActionList(sender, args);
            default -> MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("action-usage"));
        }
    }

    private void handleActionAdd(CommandSender sender, String[] args) {
        if (args.length < 6) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("action-add-usage"));
            return;
        }

        Hologram hologram = requireHologram(sender, args[2]);
        if (hologram == null) {
            return;
        }

        HologramClickType clickType = HologramClickType.fromString(args[3]);
        if (clickType == null) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("invalid-click-type").replace("<type>", args[3]));
            return;
        }

        HologramActionType actionType = HologramActionType.fromString(args[4]);
        if (actionType == null) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("invalid-action-type").replace("<type>", args[4]));
            return;
        }

        try {
            HologramAction action = HologramActionExecutor.createValidated(actionType, joinArgs(args, 5));
            if (actionType == HologramActionType.PAGE && action.getValue().chars().allMatch(Character::isDigit)) {
                int page = Integer.parseInt(action.getValue());
                if (page > hologram.getPages().size()) {
                    MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("invalid-action-value")
                            .replace("<value>", action.getValue()));
                    return;
                }
            }
            hologram.addAction(clickType, action);
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("action-add-success")
                    .replace("<hologram_id>", hologram.getId())
                    .replace("<click>", clickType.getDisplayName())
                    .replace("<type>", action.getType().getDisplayName())
                    .replace("<value>", action.getValue()));
        } catch (IllegalArgumentException exception) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("invalid-action-value")
                    .replace("<value>", joinArgs(args, 5)));
        }
    }

    private void handleActionRemove(CommandSender sender, String[] args) {
        if (args.length < 5) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("action-remove-usage"));
            return;
        }

        Hologram hologram = requireHologram(sender, args[2]);
        if (hologram == null) {
            return;
        }

        HologramClickType clickType = HologramClickType.fromString(args[3]);
        if (clickType == null) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("invalid-click-type").replace("<type>", args[3]));
            return;
        }

        Integer actionId = parsePositiveInt(sender, args[4], "invalid-action-id");
        if (actionId == null) {
            return;
        }

        HologramAction removed = hologram.removeAction(clickType, actionId - 1);
        if (removed == null) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("action-not-found")
                    .replace("<id>", String.valueOf(actionId))
                    .replace("<click>", clickType.getDisplayName())
                    .replace("<hologram_id>", hologram.getId()));
            return;
        }

        MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("action-remove-success")
                .replace("<hologram_id>", hologram.getId())
                .replace("<click>", clickType.getDisplayName())
                .replace("<id>", String.valueOf(actionId)));
    }

    private void handleActionList(CommandSender sender, String[] args) {
        if (args.length < 3) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("action-list-usage"));
            return;
        }

        Hologram hologram = requireHologram(sender, args[2]);
        if (hologram == null) {
            return;
        }

        MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("action-list-header")
                .replace("<hologram_id>", hologram.getId()));

        boolean sentAny = false;
        for (HologramClickType clickType : HologramClickType.values()) {
            List<HologramAction> actions = hologram.getActions(clickType);
            if (actions.isEmpty()) {
                continue;
            }

            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("action-list-branch")
                    .replace("<click>", clickType.getDisplayName()));
            for (int i = 0; i < actions.size(); i++) {
                HologramAction action = actions.get(i);
                MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("action-list-entry")
                        .replace("<id>", String.valueOf(i + 1))
                        .replace("<type>", action.getType().getDisplayName())
                        .replace("<value>", action.getValue()));
            }
            sentAny = true;
        }

        if (!sentAny) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("action-list-empty")
                    .replace("<hologram_id>", hologram.getId()));
        }
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        if (args.length == 0) {
            return availableRootSubcommands(sender);
        }

        if (args.length == 1) {
            return complete(args[0], availableRootSubcommands(sender));
        }

        String subcommand = args[0].toLowerCase(Locale.ROOT);
        return switch (subcommand) {
            case "create" -> suggestCreateCommand(args);
            case "clone", "copy" -> suggestCloneCommand(args);
            case "page" -> suggestPageCommand(args);
            case "line" -> suggestLineCommand(args);
            case "addline" -> suggestAddLineAlias(args);
            case "setline" -> suggestSetLineAlias(args);
            case "removeline", "deleteline" -> suggestRemoveLineAlias(args);
            case "insertbefore", "insertafter" -> suggestInsertLineAlias(args);
            case "shadow" -> suggestShadowCommand(args);
            case "permission" -> suggestPermissionCommand(args);
            case "npc" -> suggestNpcCommand(args);
            case "import" -> suggestImportCommand(args);
            case "linkwithnpc" -> suggestLinkWithNpcAlias(args);
            case "unlinkwithnpc" -> args.length == 2 ? complete(args[1], hologramIds()) : List.of();
            case "action" -> suggestActionCommand(args);
            case "backup" -> suggestBackupCommand(args);
            case "play", "pause", "stop", "info", "reload" -> args.length == 2 ? complete(args[1], hologramIds()) : List.of();
            case "resize", "size" -> suggestMediaResizeCommand(args);
            case "list" -> args.length == 2 ? complete(args[1], List.of("menu")) : List.of();
            case "moveto" -> suggestMoveTo(source, args);
            case "brightness" -> suggestBrightness(args);
            default -> suggestSingleBranch(source, args, subcommand);
        };
    }

    private Collection<String> suggestCreateCommand(String[] args) {
        if (args.length == 2) {
            return complete(args[1], MEDIA_CREATE_TYPES);
        }
        if (args.length == 3 && (isExactLineType(args[1]) || parseMediaCreateType(args[1]) != null)) {
            return List.of("<id>");
        }
        if (args.length == 4 && parseMediaCreateType(args[1]) != null) {
            return List.of("https://example.com/media.png");
        }
        return List.of();
    }

    private Collection<String> suggestBackupCommand(String[] args) {
        if (args.length == 2) {
            return complete(args[1], BACKUP_ACTIONS);
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("restore")) {
            return List.of("<backup.zip>");
        }
        return List.of();
    }

    private Collection<String> suggestMediaResizeCommand(String[] args) {
        if (args.length == 2) {
            return complete(args[1], mediaHologramIds());
        }
        if (args.length == 3 || args.length == 4) {
            return complete(args[args.length - 1], MEDIA_SIZE_SUGGESTIONS);
        }
        if (args.length == 5) {
            return complete(args[4], HOLOGRAM_SCALE_SUGGESTIONS);
        }
        return List.of();
    }

    private Collection<String> suggestCloneCommand(String[] args) {
        if (args.length == 2) {
            return complete(args[1], hologramIds());
        }
        if (args.length == 3) {
            String sourceId = args[1];
            if (plugin.getHologramManager().getHologram(sourceId) == null) {
                return List.of();
            }
            return complete(args[2], List.of(sourceId + "_copy"));
        }
        return List.of();
    }

    private Collection<String> suggestPageCommand(String[] args) {
        if (args.length == 2) {
            return complete(args[1], PAGE_ACTIONS);
        }
        if (args.length == 3) {
            return complete(args[2], hologramIds());
        }
        if (args.length == 4) {
            Hologram hologram = plugin.getHologramManager().getHologram(args[2]);
            String action = args[1].toLowerCase(Locale.ROOT);
            if (hologram == null || action.equals("add")) {
                return List.of();
            }
            return complete(args[3], pageNumbers(hologram));
        }
        return List.of();
    }

    private Collection<String> suggestLineCommand(String[] args) {
        if (args.length == 2) {
            return complete(args[1], LINE_ACTIONS);
        }

        if (args.length == 3) {
            return complete(args[2], hologramIds());
        }

        Hologram hologram = plugin.getHologramManager().getHologram(args[2]);
        if (hologram == null) {
            return List.of();
        }

        String action = args[1].toLowerCase(Locale.ROOT);

        if (args.length == 4) {
            if (action.equals("add")) {
                HologramPage exactPage = resolvePage(hologram, args[3]);
                if (exactPage != null) {
                    return lineTypeNames();
                }
            }
            return complete(args[3], pageNumbers(hologram));
        }

        HologramPage page = resolvePage(hologram, args[3]);
        if (page == null) {
            return List.of();
        }

        if (args.length == 5) {
            return switch (action) {
                case "add" -> {
                    if (isExactLineType(args[4])) {
                        yield contentSuggestionsForType(args[4], "");
                    }
                    yield complete(args[4], lineTypeNames());
                }
                case "remove", "delete" -> complete(args[4], lineNumbers(page));
                case "set" -> {
                    HologramLine line = resolveLine(page, args[4]);
                    if (line != null) {
                        yield contentSuggestionsForLine(line, "");
                    }
                    yield complete(args[4], lineNumbers(page));
                }
                case "offset" -> {
                    if (resolveLine(page, args[4]) != null) {
                        yield List.of("0");
                    }
                    yield complete(args[4], lineNumbers(page));
                }
                case "height" -> {
                    if (resolveLine(page, args[4]) != null) {
                        yield DEFAULT_LINE_HEIGHT_SUGGESTIONS;
                    }
                    yield complete(args[4], lineNumbers(page));
                }
                case "scale" -> {
                    if (resolveLine(page, args[4]) != null) {
                        yield DEFAULT_LINE_SCALE_SUGGESTIONS;
                    }
                    yield complete(args[4], lineNumbers(page));
                }
                default -> List.of();
            };
        }

        if (args.length == 6) {
            return switch (action) {
                case "add" -> contentSuggestionsForType(args[4], args[5]);
                case "set" -> {
                    HologramLine line = resolveLine(page, args[4]);
                    yield line == null ? List.of() : contentSuggestionsForLine(line, args[5]);
                }
                case "offset" -> List.of("0");
                case "height" -> complete(args[5], DEFAULT_LINE_HEIGHT_SUGGESTIONS);
                case "scale" -> complete(args[5], DEFAULT_LINE_SCALE_SUGGESTIONS);
                default -> List.of();
            };
        }

        if (args.length == 7 && action.equals("offset")) {
            return List.of("0");
        }

        if (args.length == 7 && action.equals("scale")) {
            return List.of("1");
        }

        if (args.length == 8 && action.equals("offset")) {
            return List.of("0");
        }

        if (args.length == 8 && action.equals("scale")) {
            return List.of("1");
        }

        return List.of();
    }

    private Collection<String> suggestAddLineAlias(String[] args) {
        if (args.length == 2) {
            return complete(args[1], hologramIds());
        }

        Hologram hologram = plugin.getHologramManager().getHologram(args[1]);
        if (hologram == null) {
            return List.of();
        }

        if (args.length == 3) {
            return complete(args[2], pageNumbers(hologram));
        }
        if (args.length == 4) {
            return complete(args[3], lineTypeNames());
        }
        if (args.length == 5) {
            return contentSuggestionsForType(args[3], args[4]);
        }
        return List.of();
    }

    private Collection<String> suggestSetLineAlias(String[] args) {
        if (args.length == 2) {
            return complete(args[1], hologramIds());
        }

        Hologram hologram = plugin.getHologramManager().getHologram(args[1]);
        if (hologram == null) {
            return List.of();
        }

        if (args.length == 3) {
            return complete(args[2], pageNumbers(hologram));
        }

        HologramPage page = resolvePage(hologram, args[2]);
        if (page == null) {
            return List.of();
        }

        if (args.length == 4) {
            return complete(args[3], lineNumbers(page));
        }
        if (args.length == 5) {
            HologramLine line = resolveLine(page, args[3]);
            return line == null ? List.of() : contentSuggestionsForLine(line, args[4]);
        }
        return List.of();
    }

    private Collection<String> suggestRemoveLineAlias(String[] args) {
        if (args.length == 2) {
            return complete(args[1], hologramIds());
        }

        Hologram hologram = plugin.getHologramManager().getHologram(args[1]);
        if (hologram == null) {
            return List.of();
        }

        if (args.length == 3) {
            return complete(args[2], pageNumbers(hologram));
        }

        HologramPage page = resolvePage(hologram, args[2]);
        if (page == null) {
            return List.of();
        }

        return args.length == 4 ? complete(args[3], lineNumbers(page)) : List.of();
    }

    private Collection<String> suggestInsertLineAlias(String[] args) {
        if (args.length == 2) {
            return complete(args[1], hologramIds());
        }

        Hologram hologram = plugin.getHologramManager().getHologram(args[1]);
        if (hologram == null) {
            return List.of();
        }

        if (args.length == 3) {
            return complete(args[2], pageNumbers(hologram));
        }

        HologramPage page = resolvePage(hologram, args[2]);
        if (page == null) {
            return List.of();
        }

        if (args.length == 4) {
            return complete(args[3], lineNumbers(page));
        }
        if (args.length == 5) {
            return complete(args[4], lineTypeNames());
        }
        if (args.length == 6) {
            return contentSuggestionsForType(args[4], args[5]);
        }
        return List.of();
    }

    private Collection<String> suggestShadowCommand(String[] args) {
        if (args.length == 2) {
            return complete(args[1], SHADOW_ACTIONS);
        }
        if (args.length == 3) {
            return complete(args[2], hologramIds());
        }
        if (args.length == 4) {
            return SHADOW_VALUE_SUGGESTIONS;
        }
        return List.of();
    }

    private Collection<String> suggestPermissionCommand(String[] args) {
        if (args.length == 2) {
            return complete(args[1], hologramIds());
        }
        if (args.length == 3) {
            Hologram hologram = plugin.getHologramManager().getHologram(args[1]);
            if (hologram == null) {
                return List.of();
            }
            return List.of("axohologram.view." + hologram.getId());
        }
        return List.of();
    }

    private Collection<String> suggestNpcCommand(String[] args) {
        if (args.length == 2) {
            return complete(args[1], NPC_ACTIONS);
        }
        if (args.length == 3) {
            return complete(args[2], hologramIds());
        }
        if (args.length == 4 && "link".equalsIgnoreCase(args[1])) {
            return complete(args[3], availableNpcNames());
        }
        return List.of();
    }

    private Collection<String> suggestImportCommand(String[] args) {
        if (args.length == 2) {
            return complete(args[1], importSources());
        }
        if (args.length == 3 && !args[1].equalsIgnoreCase("auto")) {
            HologramImporter importer = plugin.getImportManager().importer(args[1]);
            if (importer == null) {
                return List.of();
            }
            List<String> suggestions = new ArrayList<>();
            suggestions.add("all");
            suggestions.addAll(importer.availableHolograms());
            return complete(args[2], suggestions);
        }
        return List.of();
    }

    private List<String> importSources() {
        List<String> sources = new ArrayList<>();
        sources.add("auto");
        plugin.getImportManager().importers().forEach(importer -> sources.add(importer.id()));
        return sources;
    }

    private Collection<String> suggestLinkWithNpcAlias(String[] args) {
        if (args.length == 2) {
            return complete(args[1], hologramIds());
        }
        if (args.length == 3) {
            return complete(args[2], availableNpcNames());
        }
        return List.of();
    }

    private Collection<String> suggestActionCommand(String[] args) {
        if (args.length == 2) {
            return complete(args[1], ACTION_ACTIONS);
        }
        if (args.length == 3) {
            return complete(args[2], hologramIds());
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        Hologram hologram = plugin.getHologramManager().getHologram(args[2]);
        if (hologram == null) {
            return List.of();
        }

        if (args.length == 4) {
            return complete(args[3], CLICK_TYPES);
        }
        if (args.length == 5 && action.equals("add")) {
            return complete(args[4], actionTypeNames());
        }
        if (args.length == 5 && (action.equals("remove") || action.equals("delete"))) {
            HologramClickType clickType = HologramClickType.fromString(args[3]);
            return clickType == null ? List.of() : complete(args[4], actionIds(hologram, clickType));
        }
        if (args.length == 6 && action.equals("add")) {
            HologramActionType actionType = HologramActionType.fromString(args[4]);
            if (actionType == null) {
                return List.of();
            }
            return switch (actionType) {
                case PAGE -> complete(args[5], PAGE_ACTION_VALUES);
                case SOUND -> complete(args[5], soundSuggestions(args[5]));
                case COMMAND, CONSOLE_COMMAND, MESSAGE -> List.of("<value>");
            };
        }
        return List.of();
    }

    private Collection<String> suggestMoveTo(CommandSourceStack source, String[] args) {
        if (args.length == 2) {
            return complete(args[1], hologramIds());
        }

        Player player = source.getSender() instanceof Player onlinePlayer ? onlinePlayer : null;
        if (args.length == 3 && player != null) {
            return List.of(formatDecimal(player.getLocation().getX()));
        }
        if (args.length == 4 && player != null) {
            return List.of(formatDecimal(player.getLocation().getY()));
        }
        if (args.length == 5 && player != null) {
            return List.of(formatDecimal(player.getLocation().getZ()));
        }
        if (args.length == 6 && player != null) {
            return List.of(formatDecimal(player.getLocation().getYaw()));
        }
        if (args.length == 7 && player != null) {
            return List.of(formatDecimal(player.getLocation().getPitch()));
        }
        return List.of();
    }

    private Collection<String> suggestBrightness(String[] args) {
        if (args.length == 2) {
            return complete(args[1], hologramIds());
        }
        if (args.length == 3) {
            return complete(args[2], BRIGHTNESS_CHANNELS);
        }
        if (args.length == 4) {
            return List.of("0", "5", "10", "15");
        }
        return List.of();
    }

    private Collection<String> suggestSingleBranch(CommandSourceStack source, String[] args, String subcommand) {
        return switch (subcommand) {
            case "delete", "remove", "move", "movehere", "teleport", "rotate", "rotatepitch", "offset", "translate",
                 "viewdistance", "visibilitydistance", "visibility", "scale", "billboard", "background",
                 "textshadow", "seethrough", "brightness", "align", "textalignment", "position", "center",
                 "shadowstrength", "shadowradius", "updatetextinterval" ->
                    args.length == 2 ? complete(args[1], hologramIds()) : suggestSingleBranchValue(source, args, subcommand);
            default -> List.of();
        };
    }

    private Collection<String> suggestSingleBranchValue(CommandSourceStack source, String[] args, String subcommand) {
        return switch (subcommand) {
            case "rotate" -> args.length == 3 ? List.of("0", "90", "180", "270") : List.of();
            case "rotatepitch" -> args.length == 3 ? List.of("-45", "0", "45") : List.of();
            case "offset", "translate" -> args.length >= 3 && args.length <= 5 ? List.of("0") : List.of();
            case "viewdistance", "visibilitydistance" -> args.length == 3 ? List.of("32", "48", "64", "default") : List.of();
            case "visibility" -> args.length == 3 ? complete(args[2], VISIBILITY_MODES) : List.of();
            case "scale" -> args.length == 3 ? HOLOGRAM_SCALE_SUGGESTIONS : List.of();
            case "billboard" -> args.length == 3 ? complete(args[2], billboardNames()) : List.of();
            case "background" -> args.length == 3 ? complete(args[2], COLOR_SUGGESTIONS) : List.of();
            case "textshadow" -> args.length == 3 ? complete(args[2], BOOLEAN_VALUES) : List.of();
            case "seethrough" -> args.length == 3 ? complete(args[2], BOOLEAN_VALUES) : List.of();
            case "align", "textalignment" -> args.length == 3 ? complete(args[2], ALIGNMENTS) : List.of();
            case "shadowstrength", "shadowradius" -> args.length == 3 ? SHADOW_VALUE_SUGGESTIONS : List.of();
            case "updatetextinterval" -> args.length == 3 ? UPDATE_TEXT_INTERVAL_SUGGESTIONS : List.of();
            case "brightness" -> suggestBrightness(args);
            case "moveto" -> suggestMoveTo(source, args);
            default -> List.of();
        };
    }

    private HologramLine createLine(CommandSender sender, LineType lineType, String content) {
        try {
            return switch (lineType) {
                case TEXT -> new TextLineImpl(content, plugin);
                case ITEM -> new ItemLineImpl(content, plugin);
                case BLOCK -> new BlockLineImpl(content, plugin);
            };
        } catch (IllegalArgumentException exception) {
            sendInvalidContentMessage(sender, lineType, content);
            return null;
        }
    }

    private boolean updateLineContent(CommandSender sender, HologramLine line, String content) {
        try {
            if (line instanceof TextLineImpl textLine) {
                textLine.setContent(content);
                return true;
            }
            if (line instanceof ItemLineImpl itemLine) {
                itemLine.setContent(content);
                return true;
            }
            if (line instanceof BlockLineImpl blockLine) {
                blockLine.setContent(content);
                return true;
            }
        } catch (IllegalArgumentException exception) {
            sendInvalidContentMessage(sender, line.getType(), content);
            return false;
        }

        MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("line-not-found"));
        return false;
    }

    private void applyCreateMessageToHologram(Hologram hologram, LineType createType) {
        if (createType != LineType.TEXT) {
            return;
        }

        HologramPage page = hologram.getPage(0);
        if (page == null) {
            return;
        }

        String defaultText = Objects.requireNonNullElse(
                plugin.getConfigManager().getMessages().getString("create-success"),
                "<green>Created <type> hologram '<hologram_id>' at your location.</green>"
        ).replace("<prefix>", "")
                .replace("<hologram_id>", hologram.getId())
                .replace("<type>", createType.name().toLowerCase(Locale.ROOT))
                .trim();

        HologramLine existingLine = page.getLine(0);
        if (existingLine instanceof TextLineImpl textLine) {
            textLine.setContent(defaultText);
        } else if (existingLine == null) {
            page.addLine(new TextLineImpl(defaultText, plugin));
        } else {
            page.setLine(0, new TextLineImpl(defaultText, plugin));
        }

        markHologramContentChanged(hologram);
        plugin.getHologramManager().saveHologram(hologram);
        hologram.refreshViewers();
        plugin.getHologramManager().restartRefreshTask();
    }

    private void markHologramContentChanged(Hologram hologram) {
        if (hologram instanceof AxoHologramImpl axoHologram) {
            axoHologram.markPeriodicRefreshStateDirty();
        }
    }

    private void sendInvalidContentMessage(CommandSender sender, LineType type, String content) {
        String messageKey = switch (type) {
            case ITEM -> "invalid-item-material";
            case BLOCK -> "invalid-block-content";
            case TEXT -> "line-set-invalid-content";
        };
        String message = Objects.requireNonNullElse(plugin.getConfigManager().getMessages().getString(messageKey), "");
        MessageUtil.sendMessage(sender, message.replace("<content>", content));
    }

    private void sendImportResult(CommandSender sender, ImportResult result) {
        MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("import-summary")
                .replace("<source>", result.source())
                .replace("<attempted>", String.valueOf(result.attempted()))
                .replace("<imported>", String.valueOf(result.imported()))
                .replace("<skipped>", String.valueOf(result.skipped()))
                .replace("<failed>", String.valueOf(result.failed())));

        int limit = Math.min(10, result.messages().size());
        for (int i = 0; i < limit; i++) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("import-message-entry")
                    .replace("<message>", escapeMiniMessage(result.messages().get(i))));
        }
        int remaining = result.messages().size() - limit;
        if (remaining > 0) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("import-message-more")
                    .replace("<count>", String.valueOf(remaining)));
        }
    }

    private void sendMediaInfo(CommandSender sender, MediaHologram hologram) {
        Location location = hologram.getLocation();
        MediaSettings settings = hologram.getSettings();
        String playback = hologram.getType() == MediaType.VIDEO
                ? hologram.getPlaybackState().name() + (hologram.isAutoPaused() ? " (AUTO_PAUSED)" : "")
                : "N/A";
        String frames = hologram.getProcessedMedia() == null
                ? "0"
                : String.valueOf(hologram.getProcessedMedia().frameCount());
        MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("info-media")
                .replace("<hologram_id>", hologram.getId())
                .replace("<type>", hologram.getType().name())
                .replace("<state>", hologram.getState().name())
                .replace("<playback>", playback)
                .replace("<world>", hologram.getWorldName() == null ? "unknown" : hologram.getWorldName())
                .replace("<x>", formatDecimal(location.getX()))
                .replace("<y>", formatDecimal(location.getY()))
                .replace("<z>", formatDecimal(location.getZ()))
                .replace("<url>", escapeMiniMessage(hologram.getUrl().toString()))
                .replace("<viewers>", String.valueOf(hologram.viewerCount()))
                .replace("<frames>", frames)
                .replace("<width>", formatDecimal(settings.width()))
                .replace("<height>", formatDecimal(settings.height()))
                .replace("<scale>", formatDecimal(settings.scale()))
                .replace("<render_distance>", String.valueOf(settings.renderDistance()))
                .replace("<status>", escapeMiniMessage(hologram.getStatusMessage())));
    }

    private String escapeMiniMessage(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("<", "\\<");
    }

    private HologramPage requirePage(CommandSender sender, Hologram hologram, int pageNumber) {
        HologramPage page = hologram.getPage(pageNumber - 1);
        if (page == null) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("page-not-found")
                    .replace("<hologram_id>", hologram.getId())
                    .replace("<page_number>", String.valueOf(pageNumber)));
        }
        return page;
    }

    private LineType parseLineType(CommandSender sender, String rawType) {
        try {
            return LineType.valueOf(rawType.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("invalid-line-type").replace("<type>", rawType));
            return null;
        }
    }

    private Long parseUpdateInterval(CommandSender sender, String raw) {
        if (raw == null || raw.isBlank()) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("invalid-update-text-interval"));
            return null;
        }
        if (raw.trim().equalsIgnoreCase("default")) {
            return -1L;
        }

        Long value = parseTickInterval(raw);
        if (value == null || value <= 0L) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("invalid-update-text-interval"));
            return null;
        }
        return value;
    }

    private Long parseTickInterval(String raw) {
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        try {
            if (normalized.endsWith("milliseconds")) {
                return millisecondsToTicks(normalized.substring(0, normalized.length() - "milliseconds".length()));
            }
            if (normalized.endsWith("millisecond")) {
                return millisecondsToTicks(normalized.substring(0, normalized.length() - "millisecond".length()));
            }
            if (normalized.endsWith("ms")) {
                return millisecondsToTicks(normalized.substring(0, normalized.length() - 2));
            }
            if (normalized.endsWith("seconds")) {
                return secondsToTicks(normalized.substring(0, normalized.length() - "seconds".length()));
            }
            if (normalized.endsWith("second")) {
                return secondsToTicks(normalized.substring(0, normalized.length() - "second".length()));
            }
            if (normalized.endsWith("secs")) {
                return secondsToTicks(normalized.substring(0, normalized.length() - 4));
            }
            if (normalized.endsWith("sec")) {
                return secondsToTicks(normalized.substring(0, normalized.length() - 3));
            }
            if (normalized.endsWith("s")) {
                return secondsToTicks(normalized.substring(0, normalized.length() - 1));
            }
            if (normalized.endsWith("ticks")) {
                return Long.parseLong(normalized.substring(0, normalized.length() - 5).trim());
            }
            if (normalized.endsWith("tick")) {
                return Long.parseLong(normalized.substring(0, normalized.length() - 4).trim());
            }
            if (normalized.endsWith("t")) {
                return Long.parseLong(normalized.substring(0, normalized.length() - 1).trim());
            }
            return Long.parseLong(normalized);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Long secondsToTicks(String rawSeconds) {
        double seconds = Double.parseDouble(rawSeconds.trim());
        if (!Double.isFinite(seconds)) {
            return null;
        }
        return Math.round(seconds * 20.0D);
    }

    private Long millisecondsToTicks(String rawMilliseconds) {
        double milliseconds = Double.parseDouble(rawMilliseconds.trim());
        if (!Double.isFinite(milliseconds)) {
            return null;
        }
        return Math.round(milliseconds / 50.0D);
    }

    private boolean applyDisplayLineScale(CommandSender sender, String[] args, LineScaleSetter setter, Runnable clearAction) {
        if (args.length == 1 && args[0].equalsIgnoreCase("default")) {
            clearAction.run();
            return true;
        }

        try {
            if (args.length == 1) {
                float uniformScale = Float.parseFloat(args[0]);
                if (uniformScale <= 0.0F) {
                    MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("invalid-line-scale-number"));
                    return false;
                }
                setter.set(uniformScale, uniformScale, uniformScale);
                return true;
            }

            if (args.length == 3) {
                float scaleX = Float.parseFloat(args[0]);
                float scaleY = Float.parseFloat(args[1]);
                float scaleZ = Float.parseFloat(args[2]);
                if (scaleX <= 0.0F || scaleY <= 0.0F || scaleZ <= 0.0F) {
                    MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("invalid-line-scale-number"));
                    return false;
                }
                setter.set(scaleX, scaleY, scaleZ);
                return true;
            }
        } catch (NumberFormatException ignored) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("invalid-line-scale-number"));
            return false;
        }

        MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("line-scale-usage"));
        return false;
    }

    private String joinArgs(String[] args, int startIndex) {
        if (startIndex >= args.length) {
            return "";
        }
        return String.join(" ", Arrays.copyOfRange(args, startIndex, args.length));
    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("player-only"));
        return null;
    }

    @FunctionalInterface
    private interface LineScaleSetter {
        void set(float scaleX, float scaleY, float scaleZ);
    }

    private boolean requirePermission(CommandSender sender, String permission, String... legacyPermissions) {
        if (sender.hasPermission("axohologram.admin") || sender.hasPermission(permission)) {
            return true;
        }

        for (String legacyPermission : legacyPermissions) {
            if (sender.hasPermission(legacyPermission)) {
                return true;
            }
        }

        MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("no-permission"));
        return false;
    }

    private boolean canUse(CommandSender sender, String permission, String... legacyPermissions) {
        if (sender.hasPermission("axohologram.admin") || sender.hasPermission(permission)) {
            return true;
        }

        for (String legacyPermission : legacyPermissions) {
            if (sender.hasPermission(legacyPermission)) {
                return true;
            }
        }

        return false;
    }

    private Hologram requireHologram(CommandSender sender, String id) {
        if (!plugin.getHologramManager().isValidHologramId(id)) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("invalid-hologram-id").replace("<hologram_id>", id));
            return null;
        }

        Hologram hologram = plugin.getHologramManager().getHologram(id);
        if (hologram == null) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("hologram-not-found").replace("<hologram_id>", id));
        }
        return hologram;
    }

    private MediaHologram findMediaHologram(String id) {
        return plugin.getMediaManager() == null ? null : plugin.getMediaManager().getHologram(id);
    }

    private void moveMediaTo(CommandSender sender, MediaHologram mediaHologram, String[] args) {
        try {
            double x = Double.parseDouble(args[2]);
            double y = Double.parseDouble(args[3]);
            double z = Double.parseDouble(args[4]);
            Location current = mediaHologram.getLocation();
            Player player = sender instanceof Player onlinePlayer ? onlinePlayer : null;
            if (player == null && current.getWorld() == null) {
                MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("world-unavailable").replace("<world>", mediaHologram.getWorldName()));
                return;
            }

            Location target = new Location(
                    player != null ? player.getWorld() : current.getWorld(),
                    x,
                    y,
                    z,
                    current.getYaw(),
                    current.getPitch()
            );
            if (args.length >= 6) {
                target.setYaw(Float.parseFloat(args[5]));
            }
            if (args.length >= 7) {
                target.setPitch(Float.parseFloat(args[6]));
            }

            moveMediaHologram(mediaHologram, target);
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("moveto-success")
                    .replace("<hologram_id>", mediaHologram.getId())
                    .replace("<x>", formatDecimal(x))
                    .replace("<y>", formatDecimal(y))
                    .replace("<z>", formatDecimal(z)));
        } catch (NumberFormatException exception) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("invalid-location-number"));
        }
    }

    private void sendMediaPosition(CommandSender sender, MediaHologram mediaHologram) {
        Location location = mediaHologram.getLocation();
        MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("position-info")
                .replace("<hologram_id>", mediaHologram.getId())
                .replace("<world>", mediaHologram.getWorldName() == null ? "unknown" : mediaHologram.getWorldName())
                .replace("<x>", formatDecimal(location.getX()))
                .replace("<y>", formatDecimal(location.getY()))
                .replace("<z>", formatDecimal(location.getZ()))
                .replace("<yaw>", formatDecimal(location.getYaw()))
                .replace("<pitch>", formatDecimal(location.getPitch()))
                .replace("<tx>", "0.00")
                .replace("<ty>", "0.00")
                .replace("<tz>", "0.00"));
    }

    private void moveMediaHologram(MediaHologram mediaHologram, Location location) {
        if (plugin.getMediaManager() != null) {
            plugin.getMediaManager().moveHologram(mediaHologram.getId(), location);
        }
    }

    private Location centerMediaLocation(MediaHologram mediaHologram) {
        Location centered = mediaHologram.getLocation();
        MapFrameData frame = firstMediaFrame(mediaHologram);
        double horizontalFraction = frame != null && frame.columns() % 2 == 0 ? 0.0D : 0.5D;
        centered.setX(Math.floor(centered.getX()) + horizontalFraction);
        centered.setZ(Math.floor(centered.getZ()) + horizontalFraction);
        if (frame != null) {
            double verticalFraction = frame.rows() % 2 == 0 ? 0.0D : 0.5D;
            centered.setY(Math.floor(centered.getY()) + verticalFraction);
        }
        return centered;
    }

    private MapFrameData firstMediaFrame(MediaHologram mediaHologram) {
        ProcessedMedia processedMedia = mediaHologram.getProcessedMedia();
        return processedMedia == null ? null : processedMedia.firstMapFrame();
    }

    private void handleMediaScale(CommandSender sender, MediaHologram mediaHologram, String rawScale) {
        try {
            double scale = Double.parseDouble(rawScale);
            if (!Double.isFinite(scale) || scale <= 0.0D) {
                MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("invalid-scale-number"));
                return;
            }

            MediaSettings settings = mediaHologram.getSettings().withScale(scale);
            applyMediaSettings(sender, mediaHologram, settings,
                    mediaSettingsMessage("media-scale-started", mediaHologram, settings),
                    mediaSettingsMessage("media-scale-success", mediaHologram, settings));
        } catch (NumberFormatException exception) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("invalid-scale-number"));
        }
    }

    private void applyMediaSettings(CommandSender sender, MediaHologram mediaHologram, MediaSettings settings, String startedMessage, String successMessage) {
        if (plugin.getMediaManager() == null) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("media-system-disabled"));
            return;
        }

        MessageUtil.sendMessage(sender, startedMessage);
        plugin.getMediaManager().updateSettings(mediaHologram.getId(), settings).whenComplete((updated, throwable) ->
                sendAsyncCommandMessage(sender, throwable == null
                        ? successMessage
                        : plugin.getConfigManager().getMessages().getString("media-update-failed")
                        .replace("<hologram_id>", mediaHologram.getId())
                        .replace("<reason>", rootCauseMessage(throwable))));
    }

    private String mediaSettingsMessage(String key, MediaHologram mediaHologram, MediaSettings settings) {
        return plugin.getConfigManager().getMessages().getString(key)
                .replace("<hologram_id>", mediaHologram.getId())
                .replace("<width>", formatDecimal(settings.width()))
                .replace("<height>", formatDecimal(settings.height()))
                .replace("<scale>", formatDecimal(settings.scale()));
    }

    private boolean requireManualPositioning(CommandSender sender, Hologram hologram) {
        String linkedNpc = hologram.getLinkedNpc();
        if (linkedNpc == null || linkedNpc.isBlank()) {
            return true;
        }

        MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("hologram-linked-npc-move-blocked")
                .replace("<hologram_id>", hologram.getId())
                .replace("<npc_name>", linkedNpc));
        return false;
    }

    private void sendAsyncCommandMessage(CommandSender sender, String message) {
        if (sender instanceof Player player) {
            plugin.getSchedulerUtil().runAtEntity(player, () -> MessageUtil.sendMessage(sender, message));
            return;
        }
        plugin.getSchedulerUtil().runGlobal(() -> MessageUtil.sendMessage(sender, message));
    }

    private String rootCauseMessage(Throwable throwable) {
        if (throwable == null) {
            return "unknown";
        }
        Throwable cursor = throwable;
        while (cursor.getCause() != null) {
            cursor = cursor.getCause();
        }
        return cursor.getMessage() == null || cursor.getMessage().isBlank()
                ? cursor.getClass().getSimpleName()
                : escapeMiniMessage(cursor.getMessage());
    }

    private Integer parsePositiveInt(CommandSender sender, String raw, String messageKey) {
        try {
            int value = Integer.parseInt(raw);
            if (value <= 0) {
                MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString(messageKey));
                return null;
            }
            return value;
        } catch (NumberFormatException exception) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString(messageKey));
            return null;
        }
    }

    private Integer parseViewDistance(CommandSender sender, String raw) {
        if (raw.equalsIgnoreCase("default")) {
            return -1;
        }

        try {
            int value = Integer.parseInt(raw);
            if (value == -1) {
                return -1;
            }
            if (value <= 0) {
                MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("invalid-view-distance"));
                return null;
            }
            return value;
        } catch (NumberFormatException exception) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("invalid-view-distance"));
            return null;
        }
    }

    private Integer parseLightLevel(CommandSender sender, String raw) {
        try {
            int value = Integer.parseInt(raw);
            if (value < 0 || value > 15) {
                MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("invalid-brightness-value"));
                return null;
            }
            return value;
        } catch (NumberFormatException exception) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessages().getString("invalid-brightness-value"));
            return null;
        }
    }

    private VisibilityMode parseVisibilityMode(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "all" -> VisibilityMode.ALL;
            case "manual" -> VisibilityMode.MANUAL;
            case "permission" -> VisibilityMode.PERMISSION;
            default -> null;
        };
    }

    private LineType parseCreateType(String raw) {
        for (LineType value : LineType.values()) {
            if (value.name().equalsIgnoreCase(raw)) {
                return value;
            }
        }
        return null;
    }

    private MediaType parseMediaCreateType(String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "image", "imagen" -> MediaType.IMAGE;
            case "video" -> MediaType.VIDEO;
            default -> null;
        };
    }

    private boolean isCreateTypeName(String raw) {
        return parseCreateType(raw) != null || parseMediaCreateType(raw) != null;
    }

    private Billboard parseBillboard(String raw) {
        for (Billboard value : Billboard.values()) {
            if (value.name().equalsIgnoreCase(raw)) {
                return value;
            }
        }
        return null;
    }

    private TextDisplay.TextAlignment parseAlignment(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "center" -> TextDisplay.TextAlignment.CENTER;
            case "left" -> TextDisplay.TextAlignment.LEFT;
            case "right" -> TextDisplay.TextAlignment.RIGHT;
            default -> null;
        };
    }

    private Boolean parseBoolean(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "true" -> true;
            case "false" -> false;
            default -> null;
        };
    }

    private List<String> complete(String input, List<String> options) {
        if (options.isEmpty()) {
            return List.of();
        }

        String prefix = input == null ? "" : input;
        if (prefix.isEmpty()) {
            return options;
        }

        List<String> matches = new ArrayList<>(Math.min(options.size(), 16));
        for (String option : options) {
            if (option != null && option.regionMatches(true, 0, prefix, 0, prefix.length())) {
                matches.add(option);
            }
        }
        return matches.isEmpty() ? List.of() : matches;
    }

    private HologramPage resolvePage(Hologram hologram, String rawPage) {
        try {
            int pageIndex = Integer.parseInt(rawPage) - 1;
            return hologram.getPage(pageIndex);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private HologramLine resolveLine(HologramPage page, String rawLine) {
        try {
            int lineIndex = Integer.parseInt(rawLine) - 1;
            return page.getLine(lineIndex);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private boolean isExactLineType(String rawType) {
        return rawType != null && EXACT_LINE_TYPE_NAMES.contains(rawType.toLowerCase(Locale.ROOT));
    }

    private List<String> lineTypeNames() {
        return LINE_TYPE_NAMES;
    }

    private List<String> billboardNames() {
        return BILLBOARD_NAMES;
    }

    private List<String> actionTypeNames() {
        return ACTION_TYPE_NAMES;
    }

    private List<String> actionIds(Hologram hologram, HologramClickType clickType) {
        int size = hologram.getActions(clickType).size();
        if (size <= 0) {
            return List.of();
        }

        List<String> ids = new ArrayList<>(size);
        for (int i = 1; i <= size; i++) {
            ids.add(String.valueOf(i));
        }
        return ids;
    }

    private List<String> soundSuggestions(String input) {
        String prefix = input == null ? "" : input.toLowerCase(Locale.ROOT);
        List<String> suggestions = new ArrayList<>(20);
        for (Sound sound : Registry.SOUNDS) {
            String value = sound.getKey().asString();
            if (!value.startsWith(prefix)) {
                continue;
            }
            suggestions.add(value);
            if (suggestions.size() >= 20) {
                break;
            }
        }
        return suggestions.isEmpty() ? List.of() : suggestions;
    }

    private Collection<String> contentSuggestionsForType(String rawType, String input) {
        return switch (rawType.toUpperCase(Locale.ROOT)) {
            case "ITEM" -> complete(input, materialSuggestions(false));
            case "BLOCK" -> complete(input, materialSuggestions(true));
            case "TEXT" -> List.of("<content>");
            default -> List.of();
        };
    }

    private Collection<String> contentSuggestionsForLine(HologramLine line, String input) {
        String currentContent = lineContent(line);
        if (line instanceof ItemLineImpl) {
            List<String> suggestions = new ArrayList<>();
            addIfCompletes(suggestions, currentContent, input);
            suggestions.addAll(complete(input, materialSuggestions(false)));
            return suggestions;
        }
        if (line instanceof BlockLineImpl) {
            List<String> suggestions = new ArrayList<>();
            addIfCompletes(suggestions, currentContent, input);
            suggestions.addAll(complete(input, materialSuggestions(true)));
            return suggestions;
        }
        if (line instanceof TextLineImpl) {
            if (currentContent == null || currentContent.isEmpty()) {
                return List.of("<content>");
            }
            return complete(input, List.of(currentContent));
        }
        return List.of();
    }

    private void addIfCompletes(List<String> suggestions, String value, String input) {
        if (value == null || value.isEmpty()) {
            return;
        }
        String prefix = input == null ? "" : input;
        if ((prefix.isEmpty() || value.regionMatches(true, 0, prefix, 0, prefix.length())) && !suggestions.contains(value)) {
            suggestions.add(value);
        }
    }

    private String lineContent(HologramLine line) {
        if (line instanceof TextLineImpl textLine) {
            return textLine.getContent();
        }
        if (line instanceof ItemLineImpl itemLine) {
            return itemLine.getContent();
        }
        if (line instanceof BlockLineImpl blockLine) {
            return blockLine.getContent();
        }
        return null;
    }

    private List<String> availableRootSubcommands(CommandSender sender) {
        if (sender.hasPermission("axohologram.admin")) {
            return ROOT_ADMIN_SUBCOMMANDS;
        }

        Set<String> commands = new LinkedHashSet<>();
        commands.addAll(List.of("version", "ver"));
        if (canUse(sender, "axohologram.create") || canUse(sender, "axohologram.create.image") || canUse(sender, "axohologram.create.video")) {
            commands.addAll(List.of("create", "clone"));
        }
        if (canUse(sender, "axohologram.remove", "axohologram.delete")) {
            commands.addAll(List.of("delete", "remove"));
        }
        if (canUse(sender, "axohologram.command.edit", "axohologram.hologram.move", "axohologram.edit")) {
            commands.addAll(List.of("movehere", "moveto", "position", "center", "rotate", "rotatepitch", "offset", "translate"));
        }
        if (canUse(sender, "axohologram.teleport")) {
            commands.add("teleport");
        }
        if (canUse(sender, "axohologram.list")) {
            commands.add("list");
        }
        if (canUse(sender, "axohologram.info", "axohologram.list")) {
            commands.add("info");
        }
        if (canUse(sender, "axohologram.import")) {
            commands.add("import");
        }
        if (canUse(sender, "axohologram.reload")) {
            commands.add("reload");
        }
        if (canUse(sender, "axohologram.video.play")) {
            commands.add("play");
        }
        if (canUse(sender, "axohologram.video.pause")) {
            commands.add("pause");
        }
        if (canUse(sender, "axohologram.video.stop")) {
            commands.add("stop");
        }
        if (canUse(sender, "axohologram.admin")) {
            commands.add("backup");
        }
        if (canUse(sender, "axohologram.command.edit", "axohologram.page.edit", "axohologram.edit")) {
            commands.add("page");
        }
        if (canUse(sender, "axohologram.command.line", "axohologram.line.edit", "axohologram.edit")) {
            commands.addAll(List.of("line", "addline", "setline", "removeline", "insertbefore", "insertafter"));
        }
        if (canUse(sender, "axohologram.command.edit", "axohologram.permission.edit", "axohologram.edit")) {
            commands.add("permission");
        }
        if (canUse(sender, "axohologram.command.npc", "axohologram.npc.edit")
                || canUse(sender, "axohologram.npc.info", "axohologram.command.npc", "axohologram.npc.edit")) {
            commands.addAll(List.of("npc", "linkwithnpc", "unlinkwithnpc"));
        }
        if (canUse(sender, "axohologram.command.visibility", "axohologram.hologram.visibility", "axohologram.edit")) {
            commands.addAll(List.of("viewdistance", "visibilitydistance", "visibility"));
        }
        if (canUse(sender, "axohologram.command.edit", "axohologram.hologram.style", "axohologram.edit")) {
            commands.addAll(List.of(
                    "scale",
                    "resize",
                    "size",
                    "billboard",
                    "shadow",
                    "shadowstrength",
                    "shadowradius",
                    "background",
                    "textshadow",
                    "seethrough",
                    "brightness",
                    "align",
                    "textalignment",
                    "updatetextinterval"
            ));
        }
        if (canUse(sender, "axohologram.command.action", "axohologram.edit")) {
            commands.add("action");
        }
        return new ArrayList<>(commands);
    }

    private List<String> hologramIds() {
        Collection<Hologram> holograms = plugin.getHologramManager().getAllHolograms();
        Collection<MediaHologram> mediaHolograms = plugin.getMediaManager() == null
                ? List.of()
                : plugin.getMediaManager().getAllMediaHolograms();
        if (holograms.isEmpty() && mediaHolograms.isEmpty()) {
            return List.of();
        }

        List<String> ids = new ArrayList<>(holograms.size() + mediaHolograms.size());
        for (Hologram hologram : holograms) {
            ids.add(hologram.getId());
        }
        for (MediaHologram hologram : mediaHolograms) {
            ids.add(hologram.getId());
        }
        return ids;
    }

    private List<String> mediaHologramIds() {
        Collection<MediaHologram> mediaHolograms = plugin.getMediaManager() == null
                ? List.of()
                : plugin.getMediaManager().getAllMediaHolograms();
        if (mediaHolograms.isEmpty()) {
            return List.of();
        }

        List<String> ids = new ArrayList<>(mediaHolograms.size());
        for (MediaHologram hologram : mediaHolograms) {
            ids.add(hologram.getId());
        }
        return ids;
    }

    private List<String> pageNumbers(Hologram hologram) {
        int size = hologram.getPages().size();
        if (size <= 0) {
            return List.of();
        }

        List<String> pages = new ArrayList<>(size);
        for (int i = 1; i <= size; i++) {
            pages.add(String.valueOf(i));
        }
        return pages;
    }

    private List<String> lineNumbers(HologramPage page) {
        int size = page.getLines().size();
        if (size <= 0) {
            return List.of();
        }

        List<String> lines = new ArrayList<>(size);
        for (int i = 1; i <= size; i++) {
            lines.add(String.valueOf(i));
        }
        return lines;
    }

    private List<String> materialSuggestions(boolean blocksOnly) {
        return blocksOnly ? BLOCK_MATERIAL_SUGGESTIONS : ITEM_MATERIAL_SUGGESTIONS;
    }

    private List<String> availableNpcNames() {
        if (plugin.getNpcLinkService() == null || !plugin.getNpcLinkService().isAvailable()) {
            return List.of();
        }

        return List.copyOf(plugin.getNpcLinkService().getNpcNames());
    }

    private String formatDecimal(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private static List<String> createLineTypeNames() {
        LineType[] values = LineType.values();
        List<String> names = new ArrayList<>(values.length);
        for (LineType value : values) {
            names.add(value.name().toLowerCase(Locale.ROOT));
        }
        return List.copyOf(names);
    }

    private static List<String> createBillboardNames() {
        Billboard[] values = Billboard.values();
        List<String> names = new ArrayList<>(values.length);
        for (Billboard value : values) {
            names.add(value.name().toLowerCase(Locale.ROOT));
        }
        return List.copyOf(names);
    }

    private static List<String> createActionTypeNames() {
        HologramActionType[] values = HologramActionType.values();
        List<String> names = new ArrayList<>(values.length);
        for (HologramActionType value : values) {
            names.add(value.getDisplayName());
        }
        return List.copyOf(names);
    }

    private static List<String> createMaterialSuggestions(boolean blocksOnly) {
        Material[] materials = Material.values();
        List<String> suggestions = new ArrayList<>(materials.length);
        for (Material material : materials) {
            if (material.isAir()) {
                continue;
            }
            if (blocksOnly) {
                if (material.isBlock()) {
                    suggestions.add(material.name());
                }
                continue;
            }
            if (material.isItem()) {
                suggestions.add(material.name());
            }
        }
        return List.copyOf(suggestions);
    }
}
