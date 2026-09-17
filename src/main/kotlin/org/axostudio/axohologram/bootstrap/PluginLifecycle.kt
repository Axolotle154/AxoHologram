package org.axostudio.axohologram.bootstrap

import org.axostudio.axohologram.api.hologram.HologramService
import org.axostudio.axohologram.api.hologram.Hologram
import org.axostudio.axohologram.api.hologram.HologramLine
import org.axostudio.axohologram.api.AxoHologramAPI
import org.axostudio.axohologram.api.AxoHologramApiAdapter
import org.axostudio.axohologram.command.HologramCommand
import org.axostudio.axohologram.common.text.MiniMessageUtil
import org.axostudio.axohologram.common.update.UpdateChecker
import org.axostudio.axohologram.config.ConfigManager
import org.axostudio.axohologram.core.hologram.HologramFactory
import org.axostudio.axohologram.core.hologram.HologramManager
import org.axostudio.axohologram.core.hologram.action.ActionExecutor
import org.axostudio.axohologram.core.hologram.action.ActionManager
import org.axostudio.axohologram.core.hologram.line.LineType
import org.axostudio.axohologram.core.hologram.line.CompositeLine
import org.axostudio.axohologram.core.hologram.visibility.VisibilityService
import org.axostudio.axohologram.core.render.HologramRenderer
import org.axostudio.axohologram.feature.animation.AnimationManager
import org.axostudio.axohologram.feature.backup.BackupManager
import org.axostudio.axohologram.feature.importer.ImportManager
import org.axostudio.axohologram.feature.media.MediaManager
import org.axostudio.axohologram.feature.npc.NpcLinkService
import org.axostudio.axohologram.feature.wand.HologramWandService
import org.axostudio.axohologram.infrastructure.scheduler.TaskScheduler
import org.axostudio.axohologram.integration.npc.CitizensHook
import org.axostudio.axohologram.integration.npc.AxoNpcsHook
import org.axostudio.axohologram.integration.npc.FancyNpcsHook
import org.axostudio.axohologram.integration.npc.ZNpcsPlusHook
import org.axostudio.axohologram.integration.placeholder.PlaceholderAPIHook
import org.axostudio.axohologram.listener.HologramInteractionListener
import org.axostudio.axohologram.listener.PlayerConnectionListener
import org.axostudio.axohologram.listener.PlayerWorldListener
import org.axostudio.axohologram.menu.MenuManager
import org.axostudio.axohologram.persistence.StorageManager
import org.axostudio.axohologram.platform.packet.HologramPacketManager
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.plugin.ServicePriority
import java.io.File

class PluginLifecycle(
    private val plugin: JavaPlugin,
    val registry: ServiceRegistry = ServiceRegistry()
) {
    fun enable() {
        plugin.logger.info("Initializing AxoHologram architecture...")

        // 1. Scheduler & Common
        val scheduler = TaskScheduler(plugin)
        registry.register(TaskScheduler::class.java, scheduler)
        HologramPacketManager.init(plugin, scheduler.run { null })
        MiniMessageUtil.initHooks()

        val updateChecker = UpdateChecker(plugin)
        registry.register(UpdateChecker::class.java, updateChecker)
        if (plugin.config.getBoolean("general.check-updates", true)) {
            updateChecker.check()
        }

        // 2. Config & Storage
        val configManager = ConfigManager(plugin)
        configManager.loadAll()
        registry.register(ConfigManager::class.java, configManager)

        val storageManager = StorageManager(plugin.dataFolder, configManager.pluginConfig.defaultStorageType)
        registry.register(StorageManager::class.java, storageManager)

        // 3. Core
        val visibilityService = VisibilityService(config = configManager.visibilityConfig)
        registry.register(VisibilityService::class.java, visibilityService)

        val factory = HologramFactory { configManager.pluginConfig }
        val hologramManager = HologramManager(
            factory = factory,
            visibilityService = visibilityService
        )
        registry.register(HologramService::class.java, hologramManager)
        registry.register(HologramManager::class.java, hologramManager)

        val wandService = HologramWandService(plugin, hologramManager)
        registry.register(HologramWandService::class.java, wandService)

        val api = AxoHologramApiAdapter(hologramManager)
        org.axostudio.axohologram.api.AxoHologramProvider.register(api)
        Bukkit.getServicesManager().register(AxoHologramAPI::class.java, api, plugin, ServicePriority.Normal)

        val renderer = HologramRenderer(
            pageController = hologramManager.pageController,
            lineHeight = { hologram: Hologram, line: HologramLine ->
                fun defaultDisplayHeight(
                    configured: Double,
                    candidate: HologramLine,
                    textSpacing: Double
                ): Double {
                    val hologramScale = hologram.scaleY.toDouble().coerceAtLeast(0.01)
                    val lineScale = candidate.scaleY.toDouble().coerceAtLeast(0.01)
                    return maxOf(textSpacing, configured.coerceAtLeast(0.0) * hologramScale * lineScale)
                }

                fun resolveHeight(candidate: HologramLine): Double {
                    val textSpacing = configManager.config
                        .getDouble("general.defaults.line-spacing", 0.25)
                        .coerceAtLeast(0.0)
                    if (candidate.hasHeightOverride()) return candidate.height.coerceAtLeast(0.0)

                    return when (candidate.type) {
                        LineType.ITEM -> defaultDisplayHeight(
                            configManager.config.getDouble("general.defaults.item-line-height", 0.65),
                            candidate,
                            textSpacing
                        )
                        LineType.BLOCK -> defaultDisplayHeight(
                            configManager.config.getDouble("general.defaults.block-line-height", 1.0),
                            candidate,
                            textSpacing
                        )
                        LineType.COMPOSITE -> (candidate as? CompositeLine)
                            ?.getSubLines()
                            ?.maxOfOrNull(::resolveHeight)
                            ?: textSpacing
                        else -> textSpacing
                    }
                }
                resolveHeight(line)
            }
        )
        registry.register(HologramRenderer::class.java, renderer)

        val actionExecutor = ActionExecutor(plugin, scheduler)
        val actionManager = ActionManager(actionExecutor)
        registry.register(ActionManager::class.java, actionManager)
        hologramManager.onHologramSpawn = { player, hologram -> renderer.render(player, hologram) }
        hologramManager.onHologramDespawn = { player, hologram -> renderer.despawn(player, hologram) }
        hologramManager.onHologramActionsRequested = { player, hologram, clickType ->
            actionManager.executeActions(player, hologram, clickType)
        }

        // 4. Features
        val animationManager = AnimationManager(configManager, scheduler)
        animationManager.init()
        registry.register(AnimationManager::class.java, animationManager)
        renderer.configureAnimations(animationManager.engine) { animationManager.tickEngine.currentTick }
        animationManager.tickEngine.onTick = {
            for (hologram in hologramManager.allHolograms) {
                val hasTextAnimation = hologram.pages.any { page -> page.lines.any { it.content.contains("<anim:") } }
                val hasDisplayAnimation = hologram.isDisplayAnimationEnabled || hologram.pages.any { page -> page.lines.any { it.hasDisplayAnimationOverride() } }
                if (hasTextAnimation || hasDisplayAnimation) hologramManager.update(hologram)
            }
        }

        val mediaManager = MediaManager(plugin, configManager, scheduler, File(plugin.dataFolder, "media"))
        mediaManager.init()
        registry.register(MediaManager::class.java, mediaManager)

        val importManager = ImportManager(hologramManager, configManager, scheduler, plugin.dataFolder.parentFile ?: File("plugins"))
        registry.register(ImportManager::class.java, importManager)

        val backupManager = BackupManager(plugin.dataFolder, scheduler)
        registry.register(BackupManager::class.java, backupManager)

        val npcBridges = listOf(AxoNpcsHook(), CitizensHook(), FancyNpcsHook(), ZNpcsPlusHook())
        val npcLinkService = NpcLinkService(
            hologramService = hologramManager,
            bridges = npcBridges,
            scheduler = scheduler,
            defaultYOffset = {
                configManager.config.getDouble(
                "integrations.npc-y-offset",
                configManager.config.getDouble("integrations.fancynpcs-y-offset", 2.2)
                )
            },
            syncInterval = {
                configManager.config.getLong(
                    "integrations.npc-sync-interval",
                    configManager.config.getLong("integrations.fancynpcs-sync-interval", 10L)
                )
            }
        )
        npcLinkService.start()
        registry.register(NpcLinkService::class.java, npcLinkService)

        // 5. Menus
        val menuManager = MenuManager(
            hologramService = hologramManager,
            animationRegistry = animationManager.registry,
            mediaManager = mediaManager
        )
        registry.register(MenuManager::class.java, menuManager)

        // 6. Persistence connection
        hologramManager.onSaveRequested = {
            storageManager.saveAll(hologramManager.allHolograms)
        }
        hologramManager.onHologramCreated = { hologram ->
            if (hologram.isPersistent) storageManager.save(hologram)
            for (player in Bukkit.getOnlinePlayers()) {
                visibilityService.updatePlayerVisibility(
                    player,
                    listOf(hologram),
                    onShow = { p, h -> renderer.render(p, h) },
                    onHide = { p, h -> renderer.despawn(p, h) }
                )
            }
        }
        hologramManager.onHologramDeleted = { hologram ->
            renderer.despawnAll(hologram)
            storageManager.delete(hologram.id)
        }
        hologramManager.onHologramUpdated = { hologram ->
            for (player in Bukkit.getOnlinePlayers()) {
                visibilityService.updatePlayerVisibility(
                    player,
                    listOf(hologram),
                    onShow = { p, h -> renderer.render(p, h) },
                    onHide = { p, h -> renderer.despawn(p, h) }
                )
                if (visibilityService.tracker.isViewing(hologram.id, player.uniqueId)) {
                    renderer.render(player, hologram, isUpdate = true)
                }
            }
        }
        hologramManager.onReloadRequested = {
            reload()
        }

        // Load saved holograms
        val loaded = storageManager.loadAll()
        for (h in loaded) {
            animationManager.registry.getAssignedDisplayAnimation(h.id)?.let {
                h.displayAnimation = it
                h.isDisplayAnimationEnabled = true
            }
            hologramManager.registerHologram(h)
            h.linkedNpc?.let { npcLinkService.link(h.id, it) }
        }
        plugin.logger.info("Loaded ${loaded.size} holograms into service.")

        // 7. Placeholders
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            val papiHook = PlaceholderAPIHook(plugin, hologramManager)
            papiHook.register()
            registry.register(PlaceholderAPIHook::class.java, papiHook)
        }

        // 8. Listeners
        val pm = Bukkit.getPluginManager()
        pm.registerEvents(
            PlayerConnectionListener(
                hologramManager,
                visibilityService,
                renderer,
                scheduler,
                updateChecker,
                menuManager.registry
            ),
            plugin
        )
        pm.registerEvents(
            PlayerWorldListener(
                hologramManager,
                visibilityService,
                renderer,
                scheduler
            ),
            plugin
        )
        pm.registerEvents(
            HologramInteractionListener(
                hologramManager,
                actionManager,
                renderer
            ),
            plugin
        )
        pm.registerEvents(wandService, plugin)

        // 9. Commands
        val command = HologramCommand(
            hologramService = hologramManager,
            configManager = configManager,
            importManager = importManager,
            backupManager = backupManager,
            menuManager = menuManager,
            npcLinkService = npcLinkService,
            mediaManager = mediaManager,
            wandService = wandService,
            scheduler = scheduler
        )
        plugin.registerCommand(
            "axohologram",
            "Main command for AxoHologram.",
            listOf("aholo", "axoholo"),
            command
        )
        plugin.registerCommand(
            "holograma",
            "Administrative hologram command.",
            listOf("holo"),
            command
        )

        plugin.logger.info("AxoHologram enabled successfully!")
    }

    fun reload() {
        val configManager = registry.get<ConfigManager>()
        val hologramManager = registry.get<HologramManager>()
        val storageManager = registry.get<StorageManager>()
        val animationManager = registry.get<AnimationManager>()
        val mediaManager = registry.get<MediaManager>()
        val renderer = registry.get<HologramRenderer>()
        val npcLinkService = registry.get<NpcLinkService>()

        configManager?.reload()
        if (configManager != null && hologramManager != null) {
            hologramManager.visibilityService.config = configManager.visibilityConfig
        }
        animationManager?.reload()
        mediaManager?.reload()

        if (hologramManager != null && storageManager != null) {
            // Despawn all current
            for (h in hologramManager.allHolograms) {
                renderer?.despawnAll(h)
            }
            // The renderer removed existing entities, so old visibility entries
            // must not suppress their new spawn packets after reload.
            hologramManager.visibilityService.tracker.clear()
            hologramManager.repository.clear()

            // Reload from storage
            val loaded = storageManager.loadAll()
            for (h in loaded) {
                animationManager?.registry?.getAssignedDisplayAnimation(h.id)?.let {
                    h.displayAnimation = it
                    h.isDisplayAnimationEnabled = true
                }
                hologramManager.registerHologram(h)
                h.linkedNpc?.let { npcLinkService?.link(h.id, it) }
            }

            // Render for online players
            for (player in Bukkit.getOnlinePlayers()) {
                hologramManager.visibilityService.updatePlayerVisibility(
                    player,
                    hologramManager.allHolograms,
                    onShow = { p, h -> renderer?.render(p, h) },
                    onHide = { p, h -> renderer?.despawn(p, h) }
                )
            }
        }
    }

    fun disable() {
        plugin.logger.info("Disabling AxoHologram...")
        val storageManager = registry.get<StorageManager>()
        val hologramManager = registry.get<HologramManager>()
        val renderer = registry.get<HologramRenderer>()
        val animationManager = registry.get<AnimationManager>()
        val mediaManager = registry.get<MediaManager>()
        val npcLinkService = registry.get<NpcLinkService>()

        if (hologramManager != null && storageManager != null) {
            storageManager.saveAll(hologramManager.allHolograms)
            for (h in hologramManager.allHolograms) {
                renderer?.despawnAll(h)
            }
        }

        animationManager?.stop()
        mediaManager?.shutdown()
        npcLinkService?.stop()
        Bukkit.getServicesManager().unregister(AxoHologramAPI::class.java)
        HologramPacketManager.cleanupAll()
        registry.clear()
        plugin.logger.info("AxoHologram disabled cleanly.")
    }
}
