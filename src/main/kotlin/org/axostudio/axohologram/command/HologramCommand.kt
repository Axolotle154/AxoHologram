package org.axostudio.axohologram.command

import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import org.axostudio.axohologram.api.hologram.HologramService
import org.axostudio.axohologram.command.admin.BackupCommand
import org.axostudio.axohologram.command.admin.ReloadCommand
import org.axostudio.axohologram.command.hologram.*
import org.axostudio.axohologram.command.importcmd.ImportCommand
import org.axostudio.axohologram.common.text.MiniMessageUtil
import org.axostudio.axohologram.config.ConfigManager
import org.axostudio.axohologram.feature.backup.BackupManager
import org.axostudio.axohologram.feature.importer.ImportManager
import org.axostudio.axohologram.feature.npc.NpcLinkService
import org.axostudio.axohologram.feature.media.MediaManager
import org.axostudio.axohologram.feature.wand.HologramWandService
import org.axostudio.axohologram.menu.MenuManager
import org.axostudio.axohologram.infrastructure.scheduler.TaskScheduler
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class HologramCommand(
    private val hologramService: HologramService,
    private val configManager: ConfigManager,
    private val importManager: ImportManager,
    private val backupManager: BackupManager,
    private val menuManager: MenuManager? = null,
    private val npcLinkService: NpcLinkService? = null,
    private val mediaManager: MediaManager? = null,
    private val wandService: HologramWandService? = null,
    private val scheduler: TaskScheduler
) : BasicCommand, CommandExecutor, TabCompleter {

    private val subcommands = ConcurrentHashMap<String, SubCommand>()

    init {
        register(CreateCommand(hologramService, configManager, mediaManager, scheduler))
        register(DeleteCommand(hologramService, configManager))
        register(EditCommand(hologramService, configManager, menuManager))
        register(MoveCommand(hologramService, configManager))
        register(CloneCommand(hologramService, configManager))
        register(ListCommand(hologramService, configManager, menuManager))
        register(InfoCommand(hologramService, configManager))
        register(EnableCommand(hologramService, configManager))
        register(DisableCommand(hologramService, configManager))
        wandService?.let { register(WandCommand(hologramService, it)) }
        register(ImportCommand(importManager, configManager))
        register(ReloadCommand(configManager, hologramService))
        register(BackupCommand(backupManager, configManager, hologramService))
        AdvancedCommands(hologramService, configManager, npcLinkService, mediaManager, menuManager, scheduler).registerInto(this)
    }

    fun register(cmd: SubCommand) {
        subcommands[cmd.name.lowercase(Locale.ROOT)] = cmd
        for (alias in cmd.aliases) {
            subcommands[alias.lowercase(Locale.ROOT)] = cmd
        }
    }

    override fun execute(commandSourceStack: CommandSourceStack, args: Array<String>) {
        dispatch(commandSourceStack.sender, args)
    }

    override fun suggest(commandSourceStack: CommandSourceStack, args: Array<String>): Collection<String> {
        return getSuggestions(commandSourceStack.sender, args)
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        dispatch(sender, args)
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<String>): List<String> {
        return getSuggestions(sender, args)
    }

    private fun dispatch(sender: CommandSender, args: Array<String>) {
        if (args.isEmpty()) {
            sendHelp(sender)
            return
        }

        val subName = args[0].lowercase(Locale.ROOT)
        val cmd = subcommands[subName]
        if (cmd == null) {
            sender.sendMessage(MiniMessageUtil.parse(configManager.getMessage("unknown-command", "<red>Unknown subcommand: $subName</red>")))
            sendHelp(sender)
            return
        }

        val perm = cmd.permission
        if (perm != null && !sender.hasPermission(perm) && !sender.hasPermission("axohologram.admin")) {
            sender.sendMessage(MiniMessageUtil.parse(configManager.getMessage("no-permission", "<red>You don't have permission to do this.</red>")))
            return
        }

        cmd.execute(sender, args.drop(1).toTypedArray())
    }

    private fun getSuggestions(sender: CommandSender, args: Array<String>): List<String> {
        if (args.isEmpty()) {
            return subcommands.keys.toList()
        }

        if (args.size == 1) {
            val input = args[0].lowercase(Locale.ROOT)
            return subcommands.entries
                .filter { (name, cmd) ->
                    val perm = cmd.permission
                    (perm == null || sender.hasPermission(perm) || sender.hasPermission("axohologram.admin")) &&
                            name.startsWith(input)
                }
                .map { it.key }
        }

        val subName = args[0].lowercase(Locale.ROOT)
        val cmd = subcommands[subName] ?: return emptyList()
        return cmd.suggest(sender, args.drop(1).toTypedArray())
    }

    private fun sendHelp(sender: CommandSender) {
        sender.sendMessage(MiniMessageUtil.parse("<gold>=== AxoHologram Commands ===</gold>"))
        for (cmd in subcommands.values.toSet()) {
            val perm = cmd.permission
            if (perm == null || sender.hasPermission(perm) || sender.hasPermission("axohologram.admin")) {
                val usage = if (cmd.usage.isNotBlank()) cmd.usage else "/holo ${cmd.name}"
                sender.sendMessage(MiniMessageUtil.parse("<yellow>• <gold>$usage</gold></yellow>"))
            }
        }
    }
}
