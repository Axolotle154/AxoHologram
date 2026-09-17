package org.axostudio.axohologram.command.admin

import org.axostudio.axohologram.command.SubCommand
import org.axostudio.axohologram.common.text.MiniMessageUtil
import org.axostudio.axohologram.config.ConfigManager
import org.axostudio.axohologram.feature.backup.BackupManager
import org.axostudio.axohologram.api.hologram.HologramService
import org.bukkit.command.CommandSender

class BackupCommand(
    private val backupManager: BackupManager,
    private val configManager: ConfigManager,
    private val hologramService: HologramService
) : SubCommand {

    override val name: String = "backup"
    override val permission: String = "axohologram.admin"
    override val aliases: List<String> = emptyList()
    override val usage: String = "/holo backup <create|restore> [backup.zip]"

    override fun execute(sender: CommandSender, args: Array<String>) {
        when (args.firstOrNull()?.lowercase()) {
            null, "create" -> {
                sender.sendMessage(MiniMessageUtil.parse("<yellow>Starting backup...</yellow>"))
                backupManager.performBackupAsync().thenAccept { file ->
                    sender.sendMessage(MiniMessageUtil.parse("<green>Backup successfully created: <yellow>${file.name}</yellow></green>"))
                }.exceptionally { error ->
                    sender.sendMessage(MiniMessageUtil.parse("<red>Backup failed: ${error.message}</red>"))
                    null
                }
            }
            "restore" -> {
                val name = args.getOrNull(1)
                if (name.isNullOrBlank()) {
                    sender.sendMessage(MiniMessageUtil.parse("<red>Usage: /holo backup restore <backup.zip></red>"))
                    return
                }
                sender.sendMessage(MiniMessageUtil.parse("<yellow>Restoring backup...</yellow>"))
                backupManager.restoreBackupAndRun(name) { hologramService.reload() }.thenAccept { restored ->
                    if (restored) {
                        sender.sendMessage(MiniMessageUtil.parse("<green>Backup restored and holograms reloaded.</green>"))
                    } else sender.sendMessage(MiniMessageUtil.parse("<red>Backup not found or invalid.</red>"))
                }.exceptionally { error ->
                    sender.sendMessage(MiniMessageUtil.parse("<red>Restore failed: ${error.message}</red>"))
                    null
                }
            }
            else -> sender.sendMessage(MiniMessageUtil.parse("<red>Usage: $usage</red>"))
        }
    }
}
