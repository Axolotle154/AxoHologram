package org.axostudio.axohologram.command.admin

import org.axostudio.axohologram.api.hologram.HologramService
import org.axostudio.axohologram.command.SubCommand
import org.axostudio.axohologram.common.text.MiniMessageUtil
import org.axostudio.axohologram.config.ConfigManager
import org.axostudio.axohologram.core.hologram.HologramManager
import org.axostudio.axohologram.core.hologram.HologramReloadReport
import org.axostudio.axohologram.core.hologram.ReloadedHologramInfo
import org.bukkit.command.CommandSender

class ReloadCommand(
    private val configManager: ConfigManager,
    private val hologramService: HologramService
) : SubCommand {

    override val name: String = "reload"
    override val permission: String = "axohologram.reload"
    override val aliases: List<String> = listOf("rl")
    override val usage: String = "/holo reload"

    override fun execute(sender: CommandSender, args: Array<String>) {
        send(sender, "reload-start", "<yellow>Reloading configuration and holograms...</yellow>")

        val report = runCatching {
            val manager = hologramService as? HologramManager
            if (manager != null) {
                manager.reloadWithReport()
            } else {
                hologramService.reload()
                fallbackReport()
            }
        }.getOrElse { error ->
            send(
                sender,
                "reload-failed",
                "<red>Reload failed: <reason></red>",
                "<reason>" to safe(error.message ?: error.javaClass.simpleName)
            )
            return
        }

        send(
            sender,
            "reload-report-summary",
            "<green>Reload complete:</green> <white><loaded></white> loaded, <yellow><warnings></yellow> warnings, <red><failed></red> failed.",
            "<loaded>" to report.loadedCount.toString(),
            "<normal>" to report.holograms.size.toString(),
            "<media>" to report.media.size.toString(),
            "<warnings>" to report.warningCount.toString(),
            "<failed>" to report.failures.size.toString()
        )

        if (report.loadedCount == 0 && report.failures.isEmpty()) {
            send(sender, "reload-report-empty", "<gray>No holograms were found.</gray>")
        }

        report.holograms.forEach { hologram ->
            val key = if (hologram.warning == null) "reload-report-entry" else "reload-report-entry-warning"
            val fallback = if (hologram.warning == null) {
                "<green>✓</green> <white><hologram_id></white> <dark_gray>[<type>]</dark_gray> <gray><pages> pages, <lines> lines, world <world>.</gray>"
            } else {
                "<yellow>⚠</yellow> <white><hologram_id></white> <dark_gray>[<type>]</dark_gray> <yellow><warning></yellow>"
            }
            send(
                sender,
                key,
                fallback,
                "<hologram_id>" to safe(hologram.id),
                "<type>" to safe(hologram.kind),
                "<pages>" to hologram.pageCount.toString(),
                "<lines>" to hologram.lineCount.toString(),
                "<world>" to safe(hologram.world),
                "<warning>" to safe(hologram.warning.orEmpty())
            )
        }

        report.media.forEach { media ->
            send(
                sender,
                "reload-report-media-entry",
                "<green>✓</green> <white><hologram_id></white> <dark_gray>[MEDIA/<type>]</dark_gray> <gray>world <world>.</gray>",
                "<hologram_id>" to safe(media.id),
                "<type>" to safe(media.type),
                "<world>" to safe(media.world)
            )
        }

        report.failures.forEach { failure ->
            send(
                sender,
                "reload-report-failure",
                "<red>✗ <hologram_id></red> <dark_gray>(<source>)</dark_gray><gray>: <reason></gray>",
                "<hologram_id>" to safe(failure.id),
                "<source>" to safe(failure.source),
                "<reason>" to safe(failure.reason)
            )
        }
    }

    private fun fallbackReport(): HologramReloadReport = HologramReloadReport(
        holograms = hologramService.getAll().map { hologram ->
            val types = hologram.pages.flatMap { it.lines }.map { it.type.name }.distinct()
            ReloadedHologramInfo(
                id = hologram.id,
                kind = when (types.size) {
                    0 -> "EMPTY"
                    1 -> types.first()
                    else -> "MIXED"
                },
                world = hologram.worldName,
                pageCount = hologram.pageCount(),
                lineCount = hologram.pages.sumOf { it.lineCount() }
            )
        }.sortedBy { it.id.lowercase() }
    )

    private fun send(
        sender: CommandSender,
        key: String,
        fallback: String,
        vararg replacements: Pair<String, String>
    ) {
        val message = replacements.fold(configManager.getMessage(key, fallback)) { text, replacement ->
            text.replace(replacement.first, replacement.second)
        }
        sender.sendMessage(MiniMessageUtil.parse(message, sender as? org.bukkit.entity.Player))
    }

    private fun safe(value: String): String = value
        .replace("\\", "\\\\")
        .replace("<", "\\<")
}
