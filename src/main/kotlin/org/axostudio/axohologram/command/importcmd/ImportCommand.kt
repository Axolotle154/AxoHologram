package org.axostudio.axohologram.command.importcmd

import org.axostudio.axohologram.command.SubCommand
import org.axostudio.axohologram.common.text.MiniMessageUtil
import org.axostudio.axohologram.config.ConfigManager
import org.axostudio.axohologram.feature.importer.ImportManager
import org.axostudio.axohologram.feature.importer.ImportResult
import org.axostudio.axohologram.feature.importer.web.WebImporter
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class ImportCommand(
    private val importManager: ImportManager,
    private val configManager: ConfigManager
) : SubCommand {

    override val name: String = "import"
    override val permission: String = "axohologram.import"
    override val aliases: List<String> = emptyList()
    override val usage: String = "/holo import <source> [all|<name>] | /holo import web <code_or_url> [id]"

    override fun execute(sender: CommandSender, args: Array<String>) {
        if (args.isEmpty()) {
            sender.sendMessage(MiniMessageUtil.parse(configManager.getMessage("import-usage", "<red>Usage: $usage</red>")))
            val available = importManager.getAvailableImporters().map { it.id() }
            sender.sendMessage(MiniMessageUtil.parse("<gray>Available importers: ${available.joinToString(", ")}</gray>"))
            return
        }

        val source = args[0]
        val importer = importManager.getImporter(source)
        if (importer == null || !importer.isAvailable()) {
            sender.sendMessage(MiniMessageUtil.parse("<red>Importer '$source' is not available.</red>"))
            return
        }

        if (importer is WebImporter) {
            val codeOrUrl = args.getOrNull(1)
            if (codeOrUrl.isNullOrBlank() || codeOrUrl.equals("all", ignoreCase = true)) {
                sender.sendMessage(MiniMessageUtil.parse("<red>Usage: /holo import web <code_or_url> [custom_id]</red>"))
                return
            }
            val location = (sender as? Player)?.location?.clone()
            sender.sendMessage(MiniMessageUtil.parse("<gray>Fetching hologram from AxoStudio web...</gray>"))
            importer.importFromWebAsync(codeOrUrl, args.getOrNull(2), location) { result -> sendResult(sender, result) }
            return
        }

        val target = if (args.size > 1) args[1] else "all"
        val result = if (target.equals("all", ignoreCase = true)) {
            importer.importAll()
        } else {
            importer.importHologram(target)
        }

        sendResult(sender, result)
    }

    private fun sendResult(sender: CommandSender, result: ImportResult) {
        val colour = if (result.failed == 0) "green" else "red"
        sender.sendMessage(MiniMessageUtil.parse("<$colour>Import finished: ${result.imported} imported, ${result.skipped} skipped, ${result.failed} failed.</$colour>"))
        for (msg in result.messages) {
            sender.sendMessage(MiniMessageUtil.parse("<gray>• $msg</gray>"))
        }
    }

    override fun suggest(sender: CommandSender, args: Array<String>): List<String> {
        if (args.size == 1) {
            val input = args[0].lowercase()
            return importManager.getAvailableImporters().map { it.id() }.filter { it.startsWith(input) }
        }
        if (args.size == 2) {
            val importer = importManager.getImporter(args[0])
            val input = args[1].lowercase()
            val list = mutableListOf("all")
            if (importer != null) {
                list.addAll(importer.availableHolograms())
            }
            return list.filter { it.lowercase().startsWith(input) }
        }
        return emptyList()
    }
}
