package org.axostudio.axohologram.command

import org.bukkit.command.CommandSender

interface SubCommand {
    val name: String
    val permission: String?
        get() = null
    val aliases: List<String>
        get() = emptyList()
    val description: String
        get() = ""
    val usage: String
        get() = ""

    fun execute(sender: CommandSender, args: Array<String>)
    fun suggest(sender: CommandSender, args: Array<String>): List<String> = emptyList()
}
