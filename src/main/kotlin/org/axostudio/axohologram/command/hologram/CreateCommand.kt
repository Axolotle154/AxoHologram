package org.axostudio.axohologram.command.hologram

import org.axostudio.axohologram.api.hologram.HologramService
import org.axostudio.axohologram.command.SubCommand
import org.axostudio.axohologram.common.text.MiniMessageUtil
import org.axostudio.axohologram.common.validation.HologramId
import org.axostudio.axohologram.config.ConfigManager
import org.axostudio.axohologram.core.hologram.line.BlockLine
import org.axostudio.axohologram.core.hologram.line.ItemLine
import org.axostudio.axohologram.feature.media.MediaManager
import org.axostudio.axohologram.feature.media.MediaType
import org.axostudio.axohologram.infrastructure.scheduler.TaskScheduler
import org.bukkit.Material
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/** Keeps the 3.x creation grammar so existing staff workflows do not change. */
class CreateCommand(
    private val hologramService: HologramService,
    private val configManager: ConfigManager,
    private val mediaManager: MediaManager?,
    private val scheduler: TaskScheduler
) : SubCommand {

    override val name = "create"
    override val permission = "axohologram.create"
    override val aliases = listOf("new")
    override val usage = "/holo create <id> | <text|item|block> <id> [content] | <image|gif|video> <id> <file-or-url>"

    override fun execute(sender: CommandSender, args: Array<String>) {
        val player = sender as? Player ?: run { send(sender, "only-players"); return }
        if (args.isEmpty()) { send(sender, "create-usage", "<red>Usage: $usage</red>"); return }

        val type = args[0].lowercase()
        if (type == "group") {
            val groupId = args.getOrNull(1)?.takeIf { it.isNotBlank() } ?: run { send(player, "<red>Usage: /holo create group <group_id></red>"); return }
            if (!player.hasPermission("axohologram.admin")) { send(player, "no-permission"); return }
            send(player, "<green>Group '$groupId' is ready; assign holograms with /holo group set <id> $groupId.</green>")
            return
        }
        if (type in setOf("image", "imagen", "gif", "video", "png", "jpg", "jpeg", "webp", "mp4", "webm")) {
            createMedia(player, args, MediaType.fromInput(type) ?: return)
            return
        }

        val legacyType = if (type in setOf("text", "item", "block")) type else "text"
        val idIndex = if (legacyType == type) 1 else 0
        if (args.size <= idIndex) { send(sender, "create-usage", "<red>Usage: $usage</red>"); return }
        val id = args[idIndex]
        if (!HologramId.isValid(id)) { send(sender, "invalid-hologram-id", "<red>Invalid hologram id '$id'.</red>", "<hologram_id>" to id); return }
        if (hologramService.exists(id) || mediaManager?.get(id) != null) {
            send(sender, "create-fail-exists", "<red>A hologram with the ID '$id' already exists.</red>", "<hologram_id>" to id)
            return
        }

        val content = args.drop(idIndex + 1).joinToString(" ").trim()
        val hologram = when (legacyType) {
            "item" -> createItem(player, id, content) ?: return
            "block" -> createBlock(player, id, content) ?: return
            else -> hologramService.createHologram(id, player.location.clone(), listOf(
                content.ifBlank { message("create-success", "<green>Created text hologram '$id'.</green>", "<hologram_id>" to id, "<type>" to "text") }
            ))
        }
        hologramService.saveAll()
        hologramService.update(hologram)
        send(sender, "create-success", "<green>Created $legacyType hologram '$id'.</green>", "<hologram_id>" to id, "<type>" to legacyType)
        send(sender, "create-next-step-$legacyType", "", "<hologram_id>" to id)
    }

    private fun createItem(player: Player, id: String, raw: String): org.axostudio.axohologram.api.hologram.Hologram? {
        val item = if (raw.equals("hand", true) || raw.equals("mainhand", true) || raw.equals("iteminhand", true)) {
            player.inventory.itemInMainHand.takeUnless { it.type.isAir }?.clone()
        } else null
        if (raw.isNotBlank() && item == null && Material.matchMaterial(raw) == null) { send(player, "<red>Invalid item material.</red>"); return null }
        val hologram = hologramService.createItem(id, player.location.clone(), item?.type?.name ?: raw.ifBlank { "PAPER" }, true)
        item?.let { stack -> (hologram.getPage(0).getLine(0) as? ItemLine)?.setItemStack(stack) }
        return hologram
    }

    private fun createBlock(player: Player, id: String, raw: String): org.axostudio.axohologram.api.hologram.Hologram? {
        val blockData = if (raw.equals("looking", true) || raw.equals("target", true) || raw.equals("lookingblock", true)) {
            player.getTargetBlockExact(6)?.blockData
        } else null
        val material = blockData?.material ?: Material.matchMaterial(raw.ifBlank { "STONE" })
        if (material == null || !material.isBlock) { send(player, "<red>Invalid block material.</red>"); return null }
        val hologram = hologramService.createBlock(id, player.location.clone(), blockData?.asString ?: material.name, true)
        blockData?.let { data -> (hologram.getPage(0).getLine(0) as? BlockLine)?.setBlockData(data) }
        return hologram
    }

    private fun createMedia(player: Player, args: Array<String>, type: MediaType) {
        if (args.size < 3) {
            send(player, if (type == MediaType.VIDEO) "media-create-video-usage" else "media-create-image-usage", "<red>Usage: $usage</red>")
            return
        }
        val mediaPermission = if (type == MediaType.VIDEO) "axohologram.create.video" else "axohologram.create.image"
        if (!player.hasPermission(mediaPermission) && !player.hasPermission("axohologram.create") && !player.hasPermission("axohologram.admin")) { send(player, "no-permission"); return }
        val manager = mediaManager ?: run { send(player, "<red>Media system is unavailable.</red>"); return }
        val id = args[1]
        val source = args.drop(2).joinToString(" ")
        if (!HologramId.isValid(id)) { send(player, "invalid-hologram-id", "<red>Invalid hologram id '$id'.</red>", "<hologram_id>" to id); return }
        if (hologramService.exists(id) || manager.get(id) != null) { send(player, "create-fail-exists", "<red>A hologram with the ID '$id' already exists.</red>", "<hologram_id>" to id); return }
        val location = player.location.clone().also { it.pitch = 0f }
        val future = runCatching { manager.create(id, type, source, location) }.getOrElse {
            send(player, "media-create-failed", "<red>Media '$id' failed: ${it.message}</red>", "<hologram_id>" to id, "<reason>" to (it.message ?: "unknown error")); return
        }
        send(player, "media-create-started", "<yellow>Created $type media hologram '$id'.</yellow>", "<hologram_id>" to id, "<type>" to type.name.lowercase())
        future.whenComplete { _, error -> scheduler.runAtEntity(player, Runnable {
            if (error == null) send(player, "media-create-success", "<green>Media '$id' is ready.</green>", "<hologram_id>" to id)
            else send(player, "media-create-failed", "<red>Media '$id' failed: ${error.message}</red>", "<hologram_id>" to id, "<reason>" to (error.cause?.message ?: error.message ?: "unknown error"))
        }) }
    }

    private fun send(sender: CommandSender, key: String, fallback: String = key, vararg replacements: Pair<String, String>) {
        sender.sendMessage(MiniMessageUtil.parse(message(key, fallback, *replacements), sender as? Player))
    }

    private fun message(key: String, fallback: String, vararg replacements: Pair<String, String>): String =
        replacements.fold(configManager.getMessage(key, fallback).replace("<prefix>", "")) { text, replacement -> text.replace(replacement.first, replacement.second) }.trim()
}
