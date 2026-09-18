package org.axostudio.axohologram.command

import org.axostudio.axohologram.api.action.HologramAction
import org.axostudio.axohologram.api.action.HologramActionType
import org.axostudio.axohologram.api.action.HologramClickType
import org.axostudio.axohologram.api.hologram.Hologram
import org.axostudio.axohologram.api.hologram.HologramLine
import org.axostudio.axohologram.api.hologram.HologramService
import org.axostudio.axohologram.common.text.ColorUtil
import org.axostudio.axohologram.common.text.MiniMessageUtil
import org.axostudio.axohologram.config.ConfigManager
import org.axostudio.axohologram.core.hologram.line.LineManager
import org.axostudio.axohologram.core.hologram.line.LineType
import org.axostudio.axohologram.core.hologram.model.AxoHologramPage
import org.axostudio.axohologram.core.hologram.visibility.VisibilityMode
import org.axostudio.axohologram.feature.npc.NpcLinkService
import org.axostudio.axohologram.feature.media.MediaManager
import org.axostudio.axohologram.feature.media.MediaType
import org.axostudio.axohologram.menu.MenuManager
import org.axostudio.axohologram.infrastructure.scheduler.TaskScheduler
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.command.CommandSender
import org.bukkit.entity.Display
import org.bukkit.entity.Player
import org.bukkit.entity.TextDisplay
import org.bukkit.util.Vector
import java.util.Locale

/** Restores the command surface that existed in the previous monolithic command. */
class AdvancedCommands(
    private val service: HologramService,
    private val config: ConfigManager,
    private val npcLinks: NpcLinkService?,
    private val media: MediaManager?,
    private val menus: MenuManager?,
    private val scheduler: TaskScheduler
) {

    fun registerInto(root: HologramCommand) {
        add(root, "version", null, listOf("ver"), "/holo version") { sender, _ ->
            val plugin = Bukkit.getPluginManager().getPlugin("AxoHologram")
            sender.sendMessage(MiniMessageUtil.parse("<green>AxoHologram <yellow>${plugin?.pluginMeta?.version ?: "unknown"}</yellow></green>"))
        }
        add(root, "teleport", "axohologram.teleport", emptyList(), "/holo teleport <id>", ::teleport)
        add(root, "movehere", "axohologram.hologram.move", emptyList(), "/holo movehere <id>") { s, a -> moveHere(s, a) }
        add(root, "moveto", "axohologram.hologram.move", emptyList(), "/holo moveto <id> <x> <y> <z> [yaw] [pitch]", ::moveTo)
        add(root, "position", "axohologram.hologram.move", emptyList(), "/holo position <id> <x> <y> <z>", ::moveTo)
        add(root, "center", "axohologram.hologram.move", emptyList(), "/holo center <id>", ::moveHere)
        add(root, "rotate", "axohologram.hologram.move", emptyList(), "/holo rotate <id> <yaw>", ::rotate)
        add(root, "rotatepitch", "axohologram.hologram.move", emptyList(), "/holo rotatepitch <id> <pitch>", ::rotatePitch)
        add(root, "offset", "axohologram.hologram.move", listOf("translate"), "/holo offset <id> <x> <y> <z>", ::offset)

        add(root, "page", "axohologram.page.edit", emptyList(), "/holo page <add|delete|default> <id> [page]", ::page)
        add(root, "line", "axohologram.line.edit", emptyList(), "/holo line <add|set|delete|offset|height|scale> ...", ::line)
        add(root, "addline", "axohologram.line.edit", emptyList(), "/holo addline <id> <page> <type> <content>") { s, a -> lineAlias(s, a, "add") }
        add(root, "setline", "axohologram.line.edit", emptyList(), "/holo setline <id> <page> <line> <content>") { s, a -> lineAlias(s, a, "set") }
        add(root, "removeline", "axohologram.line.edit", listOf("deleteline"), "/holo removeline <id> <page> <line>") { s, a -> lineAlias(s, a, "delete") }
        add(root, "insertbefore", "axohologram.line.edit", emptyList(), "/holo insertbefore <id> <page> <line> <type> <content>") { s, a -> insertLine(s, a, false) }
        add(root, "insertafter", "axohologram.line.edit", emptyList(), "/holo insertafter <id> <page> <line> <type> <content>") { s, a -> insertLine(s, a, true) }

        add(root, "permission", "axohologram.permission.edit", emptyList(), "/holo permission <id> [permission]", ::permission)
        add(root, "visibility", "axohologram.hologram.visibility", emptyList(), "/holo visibility <id> <all|manual|permission>", ::visibility)
        add(root, "viewdistance", "axohologram.hologram.visibility", listOf("visibilitydistance"), "/holo viewdistance <id> <distance>", ::viewDistance)
        add(root, "scale", "axohologram.hologram.style", emptyList(), "/holo scale <id> <factor>", ::scale)
        add(root, "resize", "axohologram.hologram.style", listOf("size"), "/holo resize <id> <x> <y> <z>", ::resize)
        add(root, "billboard", "axohologram.hologram.style", emptyList(), "/holo billboard <id> <center|fixed|vertical|horizontal>", ::billboard)
        add(root, "shadow", "axohologram.hologram.style", emptyList(), "/holo shadow <strength|radius> <id> <value>", ::shadow)
        add(root, "shadowstrength", "axohologram.hologram.style", emptyList(), "/holo shadowstrength <id> <value>") { s, a -> shadowAlias(s, a, true) }
        add(root, "shadowradius", "axohologram.hologram.style", emptyList(), "/holo shadowradius <id> <value>") { s, a -> shadowAlias(s, a, false) }
        add(root, "background", "axohologram.hologram.style", emptyList(), "/holo background <id> <color>", ::background)
        add(root, "textshadow", "axohologram.hologram.style", emptyList(), "/holo textshadow <id> <true|false>") { s, a -> booleanStyle(s, a, true) }
        add(root, "seethrough", "axohologram.hologram.style", emptyList(), "/holo seethrough <id> <true|false>") { s, a -> booleanStyle(s, a, false) }
        add(root, "brightness", "axohologram.hologram.style", emptyList(), "/holo brightness <id> <block|sky> <0-15>", ::brightness)
        add(root, "align", "axohologram.hologram.style", listOf("textalignment"), "/holo align <id> <center|left|right>", ::align)
        add(root, "updatetextinterval", "axohologram.hologram.style", emptyList(), "/holo updatetextinterval <id> <ticks>", ::updateInterval)

        add(root, "action", "axohologram.command.action", emptyList(), "/holo action <add|remove|list> ...", ::action)
        add(root, "npc", "axohologram.command.npc", emptyList(), "/holo npc <hologram> <npc> | <link|unlink|info> ...", ::npc)
        add(root, "linkwithnpc", "axohologram.command.npc", emptyList(), "/holo linkwithnpc <id> <npc>") { s, a -> npcAlias(s, a, true) }
        add(root, "unlinkwithnpc", "axohologram.command.npc", emptyList(), "/holo unlinkwithnpc <id>") { s, a -> npcAlias(s, a, false) }
        add(root, "group", "axohologram.admin", emptyList(), "/holo group <list|info|set> ...", ::group)
        add(root, "media", "axohologram.admin", emptyList(), "/holo media <create|list|info|play|pause|stop|remove> ...", ::mediaCommand)
        add(root, "menu", "axohologram.admin", emptyList(), "/holo menu <media>", ::openMediaMenu)
        add(root, "play", "axohologram.video.play", emptyList(), "/holo play <media>") { s, a -> mediaControl(s, a, "play") }
        add(root, "pause", "axohologram.video.pause", emptyList(), "/holo pause <media>") { s, a -> mediaControl(s, a, "pause") }
        add(root, "stop", "axohologram.video.stop", emptyList(), "/holo stop <media>") { s, a -> mediaControl(s, a, "stop") }
    }

    private fun add(root: HologramCommand, commandName: String, commandPermission: String?, commandAliases: List<String>, commandUsage: String, handler: (CommandSender, Array<String>) -> Unit) {
        root.register(object : SubCommand {
            override val name = commandName
            override val permission: String? = commandPermission
            override val aliases = commandAliases
            override val usage: String = commandUsage
            override fun execute(sender: CommandSender, args: Array<String>) = handler(sender, args)
            override fun suggest(sender: CommandSender, args: Array<String>): List<String> = suggestions(commandName, args)
        })
    }

    private fun suggestions(name: String, args: Array<String>): List<String> = when {
        name == "linkwithnpc" && args.size == 2 -> npcIds(args[1])
        name == "npc" -> npcSuggestions(args)
        args.size == 1 && name !in setOf("version", "group", "page", "line", "action", "npc") -> ids(args[0])
        name == "page" && args.size == 2 -> ids(args[1])
        name == "line" && args.size == 2 -> ids(args[1])
        else -> emptyList()
    }

    private fun npcSuggestions(args: Array<String>): List<String> = when {
        // Keep the explicit subcommands, while also supporting the short form:
        // /holo npc <hologram> <npc>
        args.size == 1 -> {
            val input = args[0]
            (listOf("link", "unlink", "info").filter { it.startsWith(input, ignoreCase = true) } + ids(input))
                .distinct()
        }
        args.size == 2 && args[0].equals("link", ignoreCase = true) -> ids(args[1])
        args.size == 3 && args[0].equals("link", ignoreCase = true) -> npcIds(args[2])
        args.size == 2 && (args[0].equals("unlink", ignoreCase = true) || args[0].equals("info", ignoreCase = true)) -> ids(args[1])
        args.size == 2 -> npcIds(args[1])
        else -> emptyList()
    }

    private fun id(args: Array<String>, index: Int = 0): Hologram? {
        if (args.size <= index) return null
        return service.getHologram(args[index])
    }

    private fun ids(input: String): List<String> = service.allHologramIds.filter { it.startsWith(input, true) }

    private fun npcIds(input: String): List<String> = npcLinks?.getNpcIdentifiers(input) ?: emptyList()

    private fun save(hologram: Hologram) {
        service.saveAll()
        service.update(hologram)
    }

    private fun usage(sender: CommandSender, text: String) = sender.sendMessage(MiniMessageUtil.parse(config.withPrefix("<red>Usage: $text</red>")))
    private fun fail(sender: CommandSender, text: String) = sender.sendMessage(MiniMessageUtil.parse(config.withPrefix("<red>$text</red>")))
    private fun ok(sender: CommandSender, text: String) = sender.sendMessage(MiniMessageUtil.parse(config.withPrefix("<green>$text</green>")))

    private fun teleport(sender: CommandSender, args: Array<String>) {
        val player = sender as? Player ?: run { fail(sender, "Only players can use teleport."); return }
        val holo = id(args) ?: run { usage(sender, "/holo teleport <id>"); return }
        val location = holo.location ?: run { fail(sender, "Hologram world is not loaded."); return }
        player.teleportAsync(location)
    }

    private fun moveHere(sender: CommandSender, args: Array<String>) {
        val player = sender as? Player ?: run { fail(sender, "Only players can move a hologram here."); return }
        val holo = id(args) ?: run { usage(sender, "/holo movehere <id>"); return }
        holo.setLocation(player.location, true); save(holo); ok(sender, "Hologram ${holo.id} moved here.")
    }

    private fun moveTo(sender: CommandSender, args: Array<String>) {
        if (args.size < 4) { usage(sender, "/holo moveto <id> <x> <y> <z> [yaw] [pitch]"); return }
        val holo = id(args) ?: run { fail(sender, "Hologram not found."); return }
        val x = args[1].toDoubleOrNull(); val y = args[2].toDoubleOrNull(); val z = args[3].toDoubleOrNull()
        val world = (sender as? Player)?.world ?: holo.location?.world
        if (x == null || y == null || z == null || world == null) { fail(sender, "Invalid coordinates or world."); return }
        val yaw = args.getOrNull(4)?.toFloatOrNull() ?: holo.location?.yaw ?: 0f
        val pitch = args.getOrNull(5)?.toFloatOrNull() ?: holo.location?.pitch ?: 0f
        holo.setLocation(Location(world, x, y, z, yaw, pitch), true); save(holo); ok(sender, "Hologram ${holo.id} moved.")
    }

    private fun rotate(sender: CommandSender, args: Array<String>) = rotatePart(sender, args, true)
    private fun rotatePitch(sender: CommandSender, args: Array<String>) = rotatePart(sender, args, false)
    private fun rotatePart(sender: CommandSender, args: Array<String>, yaw: Boolean) {
        if (args.size < 2) { usage(sender, "/holo ${if (yaw) "rotate" else "rotatepitch"} <id> <degrees>"); return }
        val holo = id(args) ?: run { fail(sender, "Hologram not found."); return }
        val value = args[1].toFloatOrNull() ?: run { fail(sender, "Invalid rotation."); return }
        val loc = holo.location ?: run { fail(sender, "Hologram world is not loaded."); return }
        if (yaw) loc.yaw = value else loc.pitch = value
        holo.setLocation(loc, true); save(holo); ok(sender, "Rotation updated.")
    }

    private fun offset(sender: CommandSender, args: Array<String>) {
        if (args.size < 4) { usage(sender, "/holo offset <id> <x> <y> <z>"); return }
        val holo = id(args) ?: run { fail(sender, "Hologram not found."); return }
        val values = args.drop(1).take(3).map { it.toDoubleOrNull() }
        if (values.any { it == null }) { fail(sender, "Invalid offset."); return }
        holo.offset = Vector(values[0]!!, values[1]!!, values[2]!!); save(holo); ok(sender, "Offset updated.")
    }

    private fun page(sender: CommandSender, args: Array<String>) {
        if (args.size < 2) { usage(sender, "/holo page <add|delete|default> <id> [page]"); return }
        val action = args[0].lowercase(Locale.ROOT); val holo = id(args, 1) ?: run { fail(sender, "Hologram not found."); return }
        when (action) {
            "add" -> holo.addPage(AxoHologramPage(holo.pageCount()))
            "delete", "remove" -> { val page = args.getOrNull(2)?.toIntOrNull()?.minus(1) ?: -1; if (page !in 0 until holo.pageCount()) { fail(sender, "Invalid page."); return }; holo.removePage(page) }
            "default", "set" -> { val page = args.getOrNull(2)?.toIntOrNull()?.minus(1) ?: -1; if (page !in 0 until holo.pageCount()) { fail(sender, "Invalid page."); return }; holo.setDefaultPageIndex(page) }
            else -> { usage(sender, "/holo page <add|delete|default> <id> [page]"); return }
        }
        save(holo); ok(sender, "Page updated.")
    }

    private fun line(sender: CommandSender, args: Array<String>) {
        if (args.isEmpty()) { usage(sender, "/holo line <add|set|delete|offset|height|scale> ..."); return }
        when (args[0].lowercase(Locale.ROOT)) {
            "add" -> lineAdd(sender, args.drop(1).toTypedArray())
            "set" -> lineSet(sender, args.drop(1).toTypedArray())
            "delete", "remove" -> lineDelete(sender, args.drop(1).toTypedArray())
            "offset" -> lineOffset(sender, args.drop(1).toTypedArray())
            "height" -> lineHeight(sender, args.drop(1).toTypedArray())
            "scale" -> lineScale(sender, args.drop(1).toTypedArray())
            else -> usage(sender, "/holo line <add|set|delete|offset|height|scale> ...")
        }
    }

    private fun lineAlias(sender: CommandSender, args: Array<String>, action: String) = when (action) {
        "add" -> lineAdd(sender, args)
        "set" -> lineSet(sender, args)
        else -> lineDelete(sender, args)
    }

    private fun pageAndLine(sender: CommandSender, args: Array<String>, pageIndex: Int = 1): Pair<Hologram, org.axostudio.axohologram.api.hologram.HologramPage>? {
        val holo = id(args) ?: run { fail(sender, "Hologram not found."); return null }
        val page = args.getOrNull(pageIndex)?.toIntOrNull()?.minus(1)?.let(holo::getPage)
        if (page == null) { fail(sender, "Invalid page."); return null }
        return holo to page
    }

    private fun parseLine(type: String, content: String): HologramLine? {
        val t = LineType.fromString(type)
        if (t == LineType.UNKNOWN) return null
        return when (t) {
            LineType.ITEM -> LineManager.parseLine("item:$content")
            LineType.BLOCK -> LineManager.parseLine("block:$content")
            else -> LineManager.parseLine(content)
        }
    }

    private fun lineAdd(sender: CommandSender, args: Array<String>) {
        if (args.size < 4) { usage(sender, "/holo line add <id> <page> <type> <content>"); return }
        val pair = pageAndLine(sender, args) ?: return; val content = args.drop(3).joinToString(" "); val newLine = parseLine(args[2], content)
        if (newLine == null) { fail(sender, "Invalid line type."); return }; pair.second.addLine(newLine); save(pair.first); ok(sender, "Line added.")
    }

    private fun lineSet(sender: CommandSender, args: Array<String>) {
        if (args.size < 4) { usage(sender, "/holo line set <id> <page> <line> <content>"); return }
        val pair = pageAndLine(sender, args) ?: return; val index = args[2].toIntOrNull()?.minus(1) ?: -1; val old = pair.second.getLine(index)
        if (old == null) { fail(sender, "Invalid line."); return }; old.content = args.drop(3).joinToString(" "); save(pair.first); ok(sender, "Line updated.")
    }

    private fun lineDelete(sender: CommandSender, args: Array<String>) {
        if (args.size < 3) { usage(sender, "/holo line delete <id> <page> <line>"); return }
        val pair = pageAndLine(sender, args) ?: return; val index = args[2].toIntOrNull()?.minus(1) ?: -1
        if (pair.second.getLine(index) == null) { fail(sender, "Invalid line."); return }; pair.second.removeLine(index); save(pair.first); ok(sender, "Line removed.")
    }

    private fun lineOffset(sender: CommandSender, args: Array<String>) = lineProperty(sender, args, "offset") { line, values -> line.offset = Vector(values[0], values[1], values[2]) }
    private fun lineHeight(sender: CommandSender, args: Array<String>) = lineProperty(sender, args, "height") { line, values -> line.height = values[0] }
    private fun lineScale(sender: CommandSender, args: Array<String>) = lineProperty(sender, args, "scale") { line, values -> line.setScale(values[0].toFloat(), values.getOrElse(1) { values[0] }.toFloat(), values.getOrElse(2) { values[0] }.toFloat()) }

    private fun lineProperty(sender: CommandSender, args: Array<String>, property: String, setter: (HologramLine, List<Double>) -> Unit) {
        val needed = if (property == "offset") 6 else 4; if (args.size < needed) { usage(sender, "/holo line $property <id> <page> <line> ..."); return }
        val pair = pageAndLine(sender, args) ?: return; val index = args[2].toIntOrNull()?.minus(1) ?: -1; val line = pair.second.getLine(index)
        if (line == null) { fail(sender, "Invalid line."); return }; val values = args.drop(3).mapNotNull(String::toDoubleOrNull)
        if (values.size < if (property == "offset") 3 else 1) { fail(sender, "Invalid $property."); return }; setter(line, values); save(pair.first); ok(sender, "Line $property updated.")
    }

    private fun insertLine(sender: CommandSender, args: Array<String>, after: Boolean) {
        if (args.size < 5) { usage(sender, "/holo insert <id> <page> <line> <type> <content>"); return }
        val pair = pageAndLine(sender, args) ?: return; val index = args[2].toIntOrNull()?.minus(1) ?: -1; if (pair.second.getLine(index) == null) { fail(sender, "Invalid line."); return }
        val parsed = parseLine(args[3], args.drop(4).joinToString(" ")) ?: run { fail(sender, "Invalid line type."); return }
        pair.second.insertLine(if (after) index + 1 else index, parsed); save(pair.first); ok(sender, "Line inserted.")
    }

    private fun permission(sender: CommandSender, args: Array<String>) {
        val holo = id(args) ?: run { usage(sender, "/holo permission <id> [permission]"); return }; holo.permission = args.getOrNull(1)?.takeIf { it.isNotBlank() }; if (holo.permission == null) holo.visibilityMode = VisibilityMode.ALL; else holo.visibilityMode = VisibilityMode.PERMISSION; save(holo); ok(sender, "Permission updated.")
    }

    private fun visibility(sender: CommandSender, args: Array<String>) {
        if (args.size < 2) { usage(sender, "/holo visibility <id> <all|manual|permission>"); return }; val holo = id(args) ?: run { fail(sender, "Hologram not found."); return }; val mode = runCatching { VisibilityMode.valueOf(args[1].uppercase(Locale.ROOT)) }.getOrNull() ?: run { fail(sender, "Invalid visibility mode."); return }; holo.visibilityMode = mode; save(holo); ok(sender, "Visibility updated.")
    }

    private fun viewDistance(sender: CommandSender, args: Array<String>) {
        if (args.size < 2) { usage(sender, "/holo viewdistance <id> <distance>"); return }; val holo = id(args) ?: run { fail(sender, "Hologram not found."); return }; val value = if (args[1].equals("default", true)) config.pluginConfig.defaultViewDistance else args[1].toIntOrNull(); if (value == null || value <= 0) { fail(sender, "Invalid view distance."); return }; holo.viewDistance = value; save(holo); ok(sender, "View distance updated.")
    }

    private fun scale(sender: CommandSender, args: Array<String>) { if (args.size < 2) { usage(sender, "/holo scale <id> <factor>"); return }; val h = id(args) ?: run { fail(sender, "Hologram not found."); return }; val v = args[1].toFloatOrNull(); if (v == null || v <= 0) { fail(sender, "Invalid scale."); return }; h.setScale(v); save(h); ok(sender, "Scale updated.") }
    private fun resize(sender: CommandSender, args: Array<String>) { if (args.size < 4) { usage(sender, "/holo resize <id> <x> <y> <z>"); return }; val h = id(args) ?: run { fail(sender, "Hologram not found."); return }; val v = args.drop(1).take(3).map { it.toFloatOrNull() }; if (v.any { it == null || it <= 0 }) { fail(sender, "Invalid scale."); return }; h.setScale(v[0]!!, v[1]!!, v[2]!!); save(h); ok(sender, "Scale updated.") }
    private fun billboard(sender: CommandSender, args: Array<String>) { if (args.size < 2) { usage(sender, "/holo billboard <id> <value>"); return }; val h = id(args) ?: run { fail(sender, "Hologram not found."); return }; val value = runCatching { Display.Billboard.valueOf(args[1].uppercase(Locale.ROOT)) }.getOrNull() ?: run { fail(sender, "Invalid billboard."); return }; h.billboard = value; save(h); ok(sender, "Billboard updated.") }
    private fun shadow(sender: CommandSender, args: Array<String>) { if (args.size < 3) { usage(sender, "/holo shadow <strength|radius> <id> <value>"); return }; val h = id(args, 1) ?: run { fail(sender, "Hologram not found."); return }; val v = args[2].toFloatOrNull(); if (v == null || v < 0) { fail(sender, "Invalid shadow value."); return }; if (args[0].equals("strength", true)) h.shadowStrength = v else if (args[0].equals("radius", true)) h.shadowRadius = v else { fail(sender, "Use strength or radius."); return }; save(h); ok(sender, "Shadow updated.") }
    private fun shadowAlias(sender: CommandSender, args: Array<String>, strength: Boolean) = shadow(sender, arrayOf(if (strength) "strength" else "radius") + args)
    private fun background(sender: CommandSender, args: Array<String>) { if (args.size < 2) { usage(sender, "/holo background <id> <color>"); return }; val h = id(args) ?: run { fail(sender, "Hologram not found."); return }; val c = runCatching { ColorUtil.parseColor(args[1]) }.getOrNull() ?: run { fail(sender, "Invalid color."); return }; h.backgroundColor = c; save(h); ok(sender, "Background updated.") }
    private fun booleanStyle(sender: CommandSender, args: Array<String>, textShadow: Boolean) { if (args.size < 2) { usage(sender, "/holo ${if (textShadow) "textshadow" else "seethrough"} <id> <true|false>"); return }; val h = id(args) ?: run { fail(sender, "Hologram not found."); return }; val v = args[1].toBooleanStrictOrNull() ?: run { fail(sender, "Use true or false."); return }; if (textShadow) h.setTextShadow(v) else h.isSeeThrough = v; save(h); ok(sender, "Style updated.") }
    private fun brightness(sender: CommandSender, args: Array<String>) { if (args.size < 3) { usage(sender, "/holo brightness <id> <block|sky> <0-15>"); return }; val h = id(args) ?: run { fail(sender, "Hologram not found."); return }; val v = args[2].toIntOrNull(); if (v == null || v !in 0..15) { fail(sender, "Brightness must be between 0 and 15."); return }; if (args[1].equals("block", true)) h.brightnessBlock = v else if (args[1].equals("sky", true)) h.brightnessSky = v else { fail(sender, "Use block or sky."); return }; save(h); ok(sender, "Brightness updated.") }
    private fun align(sender: CommandSender, args: Array<String>) { if (args.size < 2) { usage(sender, "/holo align <id> <center|left|right>"); return }; val h = id(args) ?: run { fail(sender, "Hologram not found."); return }; val a = runCatching { TextDisplay.TextAlignment.valueOf(args[1].uppercase(Locale.ROOT)) }.getOrNull() ?: run { fail(sender, "Invalid alignment."); return }; h.alignment = a; save(h); ok(sender, "Alignment updated.") }
    private fun updateInterval(sender: CommandSender, args: Array<String>) { if (args.size < 2) { usage(sender, "/holo updatetextinterval <id> <ticks>"); return }; val h = id(args) ?: run { fail(sender, "Hologram not found."); return }; val v = args[1].toLongOrNull(); if (v == null || v < 0) { fail(sender, "Invalid interval."); return }; h.updateTextInterval = v; save(h); ok(sender, "Text update interval updated.") }

    private fun action(sender: CommandSender, args: Array<String>) {
        if (args.isEmpty()) { usage(sender, "/holo action <add|remove|list> ..."); return }
        when (args[0].lowercase(Locale.ROOT)) {
            "add" -> { if (args.size < 4) { usage(sender, "/holo action add <id> <left|right|any_click> <type> <value>"); return }; val h = id(args, 1) ?: run { fail(sender, "Hologram not found."); return }; val click = HologramClickType.fromString(args[2]) ?: run { fail(sender, "Invalid click type."); return }; val type = HologramActionType.fromString(args[3]) ?: run { fail(sender, "Invalid action type."); return }; val value = args.drop(4).joinToString(" "); if (value.isBlank()) { fail(sender, "Action value is required."); return }; h.addAction(click, HologramAction(type, value)); save(h); ok(sender, "Action added.") }
            "remove", "delete" -> { if (args.size < 4) { usage(sender, "/holo action remove <id> <click> <number>"); return }; val h = id(args, 1) ?: run { fail(sender, "Hologram not found."); return }; val click = HologramClickType.fromString(args[2]) ?: run { fail(sender, "Invalid click type."); return }; val index = args[3].toIntOrNull()?.minus(1) ?: -1; if (h.removeAction(click, index) == null) { fail(sender, "Action not found."); return }; save(h); ok(sender, "Action removed.") }
            "list" -> { val h = id(args, 1) ?: run { fail(sender, "Hologram not found."); return }; for (click in HologramClickType.values()) for ((i, a) in h.getActions(click).withIndex()) sender.sendMessage(MiniMessageUtil.parse("<gray>${click.name.lowercase()} #${i + 1}: ${a.type.name.lowercase()} ${a.value}</gray>")) }
            else -> usage(sender, "/holo action <add|remove|list> ...")
        }
    }

    private fun npc(sender: CommandSender, args: Array<String>) {
        if (args.isEmpty()) { usage(sender, "/holo npc <hologram> <npc> | <link|unlink|info> ..."); return }
        when (args[0].lowercase(Locale.ROOT)) {
            "info" -> {
                val h = id(args, 1) ?: run { fail(sender, "Hologram not found."); return }
                ok(sender, "${h.id}: ${h.linkedNpc ?: "not linked"}")
            }
            "link" -> npcAlias(sender, args.drop(1).toTypedArray(), true)
            "unlink", "remove" -> npcAlias(sender, args.drop(1).toTypedArray(), false)
            // Short form kept for the command syntax used by older servers:
            // /holo npc <hologram> <npc>
            else -> npcAlias(sender, args, true)
        }
    }
    private fun npcAlias(sender: CommandSender, args: Array<String>, link: Boolean) { if (npcLinks == null) { fail(sender, "NPC integration is unavailable."); return }; val h = id(args) ?: run { fail(sender, "Hologram not found."); return }; if (link) { val npc = args.getOrNull(1) ?: run { usage(sender, "/holo npc link <id> <npc>"); return }; npcLinks.link(h.id, npc); save(h); ok(sender, "Hologram linked to $npc.") } else { npcLinks.unlink(h.id); save(h); ok(sender, "NPC link removed.") } }
    private fun group(sender: CommandSender, args: Array<String>) { when (args.firstOrNull()?.lowercase()) { "list" -> service.allHolograms.map { it.group }.filter { it.isNotBlank() }.distinct().forEach { group -> ok(sender, "$group (${service.allHolograms.count { it.group == group }} holograms)") }; "info" -> { val group = args.getOrNull(1) ?: run { usage(sender, "/holo group info <group>"); return }; service.allHolograms.filter { it.group.equals(group, true) }.forEach { ok(sender, it.id) } }; "create" -> { if (args.size < 2) { usage(sender, "/holo group create <group>"); return }; ok(sender, "Group ${args[1]} is ready; assign holograms with /holo group set <id> ${args[1]}.") }; "delete" -> { if (args.size < 2) { usage(sender, "/holo group delete <group>"); return }; service.allHolograms.filter { it.group.equals(args[1], true) }.forEach { it.group = ""; save(it) }; ok(sender, "Group cleared.") }; "set", "move" -> { val h = id(args, 1) ?: run { fail(sender, "Hologram not found."); return }; h.group = args.getOrNull(2) ?: ""; save(h); ok(sender, "Group updated.") }; else -> usage(sender, "/holo group <create|delete|list|info|set> ...") } }

    private fun mediaCommand(sender: CommandSender, args: Array<String>) {
        val manager = media ?: run { fail(sender, "Media system is unavailable."); return }
        when (args.firstOrNull()?.lowercase(Locale.ROOT)) {
            "create" -> {
                if (args.size < 4) { usage(sender, "/holo media create <image|gif|video> <id> <file-or-url>"); return }
                val player = sender as? Player ?: run { fail(sender, "Only players can place media holograms."); return }
                val type = MediaType.fromInput(args[1])
                    ?: run { fail(sender, "Media type must be image, gif, video, png, jpg, webp, mp4, or webm."); return }
                val source = args.drop(3).joinToString(" ")
                runCatching { manager.create(args[2], type, source, player.location) }
                    .onFailure { fail(sender, it.message ?: "Could not create media.") }
                    .getOrNull()
                    ?.whenComplete { item, error ->
                        scheduler.runAtEntity(player, Runnable {
                            if (error == null) ok(sender, "Media ${item.id} created and rendered.")
                            else fail(sender, error.cause?.message ?: error.message ?: "Could not prepare media.")
                        })
                    }
            }
            "list" -> {
                if (manager.getAll().isEmpty()) { ok(sender, "No media holograms registered."); return }
                manager.getAll().forEach { ok(sender, "${it.id} (${it.type.name.lowercase()}, ${if (it.isPlaying) "playing" else "paused"})") }
            }
            "info" -> {
                val item = args.getOrNull(1)?.let(manager::get) ?: run { usage(sender, "/holo media info <id>"); return }
                ok(sender, "${item.id}: ${item.type.name.lowercase()} ${item.source} frame=${item.currentFrameIndex} ${if (item.isPlaying) "playing" else "paused"}")
            }
            "play", "pause", "stop", "remove", "delete" -> mediaControl(sender, args.drop(1).toTypedArray(), args[0].lowercase(Locale.ROOT))
            else -> usage(sender, "/holo media <create|list|info|play|pause|stop|remove> ...")
        }
    }

    private fun openMediaMenu(sender: CommandSender, args: Array<String>) {
        val player = sender as? Player ?: run { fail(sender, "Only players can open media menus."); return }
        val id = args.firstOrNull() ?: run { usage(sender, "/holo menu <media>"); return }
        if (menus?.openMedia(player, id) != true) fail(sender, "Media not found: $id")
    }

    private fun mediaControl(sender: CommandSender, args: Array<String>, operation: String) {
        val manager = media ?: run { fail(sender, "Media system is unavailable."); return }
        val id = args.firstOrNull() ?: run { usage(sender, "/holo $operation <media>"); return }
        val changed = when (operation) {
            "play" -> manager.play(id)
            "pause" -> manager.pause(id)
            "stop" -> manager.stop(id)
            "remove", "delete" -> manager.remove(id) != null
            else -> false
        }
        if (changed) ok(sender, "Media $id: $operation.") else fail(sender, "Media not found: $id")
    }
}
