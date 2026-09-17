package org.axostudio.axohologram.feature.media

import org.axostudio.axohologram.config.ConfigManager
import org.axostudio.axohologram.common.validation.HologramId
import org.axostudio.axohologram.feature.media.map.MapFrameRenderer
import org.axostudio.axohologram.infrastructure.scheduler.TaskScheduler
import org.bukkit.Location
import org.bukkit.Bukkit
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Display
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.MapMeta
import org.bukkit.Material
import org.bukkit.map.MapView
import org.bukkit.plugin.Plugin
import org.jcodec.api.awt.AWTFrameGrab
import org.jcodec.common.io.NIOUtils
import org.jcodec.common.io.SeekableByteChannel
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CompletableFuture
import javax.imageio.ImageIO
import javax.imageio.ImageReader
import javax.imageio.stream.ImageInputStream

class MediaManager(
    private val plugin: Plugin,
    private val configManager: ConfigManager,
    private val scheduler: TaskScheduler,
    val mediaFolder: File
) {
    private val mediaHolograms = ConcurrentHashMap<String, MediaHologram>()
    private var playbackTask: org.axostudio.axohologram.platform.scheduler.AxoScheduler.TaskHandle? = null
    @Volatile private var runtimeSettings = MediaRuntimeSettings()

    fun init() {
        if (!mediaFolder.exists()) {
            mediaFolder.mkdirs()
        }
        reload()
    }

    fun reload() {
        clearRuntime()
        mediaHolograms.clear()
        runtimeSettings = MediaRuntimeSettings.from(configManager.mediaConfig)
        if (!runtimeSettings.enabled) return
        val file = File(mediaFolder, "media.yml")
        if (file.exists()) {
            val yaml = YamlConfiguration.loadConfiguration(file)
            for (id in yaml.getConfigurationSection("media")?.getKeys(false).orEmpty()) {
                val section = yaml.getConfigurationSection("media.$id") ?: continue
                loadMedia(id, section, legacy = false)
            }
        }
        loadLegacyMedia()
        startPlayback()
    }

    fun get(id: String): MediaHologram? = mediaHolograms[id.lowercase()]

    fun getAll(): Collection<MediaHologram> = mediaHolograms.values

    fun register(media: MediaHologram) {
        HologramId.requireValid(media.id, "Media id")
        require(media.location.world != null) { "Media location must have a world." }
        mediaHolograms[media.id.lowercase(Locale.ROOT)] = media
        save()
    }

    fun create(id: String, type: MediaType, source: String, location: Location, settings: MediaSettings = defaultSettings(type)): CompletableFuture<MediaHologram> {
        require(runtimeSettings.enabled) { "The media system is disabled in media.yml." }
        require(type != MediaType.IMAGE || runtimeSettings.imagesEnabled) { "Image holograms are disabled in media.yml." }
        require(type !in setOf(MediaType.VIDEO, MediaType.GIF) || runtimeSettings.videosEnabled) { "Video holograms are disabled in media.yml." }
        HologramId.requireValid(id, "Media id")
        require(source.isNotBlank()) { "Media source cannot be blank." }
        require(location.world != null) { "Media location must have a world." }
        val media = MediaHologram(id, type, source, location.clone(), settings, settings.autoplay)
        if (mediaHolograms.putIfAbsent(id.lowercase(Locale.ROOT), media) != null) {
            return CompletableFuture.failedFuture(IllegalStateException("Media id already exists."))
        }
        save()
        return prepare(media).whenComplete { _, error ->
            if (error != null) {
                mediaHolograms.remove(id.lowercase(Locale.ROOT), media)
                save()
            }
        }
    }

    fun remove(id: String): MediaHologram? = mediaHolograms.remove(id.lowercase(Locale.ROOT)).also {
        if (it != null) {
            scheduler.runAtLocation(it.location, Runnable { destroyDisplays(it) })
            save()
        }
    }

    fun play(id: String): Boolean = get(id)?.let { media ->
        if (media.frames.isEmpty()) return false
        media.isPlaying = true
        scheduler.runAtLocation(media.location, Runnable { ensureDisplays(media); render(media); refreshViewers(media) })
        startPlayback(); save(); true
    } ?: false
    fun pause(id: String): Boolean = get(id)?.let { it.isPlaying = false; save(); true } ?: false
    fun stop(id: String): Boolean = get(id)?.let { media ->
        media.isPlaying = false; media.currentFrameIndex = 0
        scheduler.runAtLocation(media.location, Runnable { render(media) })
        save(); true
    } ?: false

    fun clear() {
        clearRuntime()
        mediaHolograms.clear()
    }

    fun shutdown() {
        clearRuntime()
        mediaHolograms.clear()
    }

    private fun prepare(media: MediaHologram): CompletableFuture<MediaHologram> = CompletableFuture.supplyAsync {
        val source = resolveSource(media.id, media.source, media.type)
        val rawFrames = when (media.type) {
            MediaType.IMAGE -> if (source.extension.equals("gif", true)) readGifFrames(source) else listOfNotNull(ImageIO.read(source))
            MediaType.GIF -> readGifFrames(source)
            MediaType.VIDEO -> readVideoFrames(source)
        }
        require(rawFrames.isNotEmpty()) { "No decodable frames were found in '${media.source}'." }
        media.frames = rawFrames.map { MapFrameData.fromImage(resize(it, media.settings.maxResolution)) }
        media.currentFrameIndex = media.currentFrameIndex.coerceIn(0, media.frames.lastIndex)
        media
    }.thenApply { prepared ->
        scheduler.runAtLocation(prepared.location, Runnable {
            if (mediaHolograms[prepared.id.lowercase(Locale.ROOT)] !== prepared) return@Runnable
            ensureDisplays(prepared)
            render(prepared)
            refreshViewers(prepared)
            if (prepared.settings.autoplay) prepared.isPlaying = true
            save()
            startPlayback()
        })
        prepared
    }

    private fun resolveSource(id: String, raw: String, type: MediaType): File {
        val uri = runCatching { URI(raw) }.getOrNull()
        if (uri?.scheme.equals("http", true) || uri?.scheme.equals("https", true)) {
            require(!uri!!.host.isNullOrBlank()) { "Remote media URL must include a host." }
            if (!runtimeSettings.allowPrivateAddresses) {
                val addresses = InetAddress.getAllByName(uri.host)
                require(addresses.isNotEmpty() && addresses.none {
                    it.isAnyLocalAddress || it.isLoopbackAddress || it.isLinkLocalAddress || it.isSiteLocalAddress || it.isMulticastAddress
                }) { "Remote media URL must not target a local network address." }
            }
            val cache = File(mediaFolder, "cache").apply { mkdirs() }
            val extension = uri!!.path.substringAfterLast('.', "bin").take(10).ifBlank { "bin" }
            val target = File(cache, "$id.$extension")
            val connection = uri.toURL().openConnection() as HttpURLConnection
            connection.connectTimeout = runtimeSettings.urlTimeoutSeconds * 1_000
            connection.readTimeout = runtimeSettings.urlTimeoutSeconds * 1_000
            connection.instanceFollowRedirects = false
            connection.requestMethod = "GET"
            connection.connect()
            require(connection.responseCode in 200..299) { "Remote server returned HTTP ${connection.responseCode}." }
            val limit = if (type == MediaType.VIDEO) runtimeSettings.videoMaxFileSizeBytes else runtimeSettings.imageMaxFileSizeBytes
            require(connection.contentLengthLong < 0 || connection.contentLengthLong <= limit) { "Remote media exceeds the configured file-size limit." }
            connection.inputStream.use { input -> copyBounded(input, target, limit) }
            return target
        }
        require(uri?.scheme == null || uri.scheme.equals("file", true)) { "Only local files and HTTP(S) URLs are supported." }
        val candidate = if (uri?.scheme.equals("file", true)) File(uri) else File(raw)
        val root = mediaFolder.canonicalFile
        val file = if (candidate.isAbsolute) candidate.canonicalFile else File(root, raw).canonicalFile
        require(file.path.startsWith(root.path + File.separator)) { "Local media must be inside the media folder." }
        require(file.isFile) { "Media file does not exist." }
        val limit = if (type == MediaType.VIDEO) runtimeSettings.videoMaxFileSizeBytes else runtimeSettings.imageMaxFileSizeBytes
        require(file.length() <= limit) { "Local media exceeds the configured file-size limit." }
        return file
    }

    private fun readGifFrames(file: File): List<BufferedImage> {
        val frames = mutableListOf<BufferedImage>()
        ImageIO.createImageInputStream(file).use { input ->
            val readers = ImageIO.getImageReaders(input)
            if (!readers.hasNext()) return emptyList()
            val reader = readers.next()
            try {
                reader.input = input
                val count = runCatching { reader.getNumImages(true) }.getOrDefault(1).coerceAtMost(runtimeSettings.maxFrames)
                for (index in 0 until count) runCatching { reader.read(index) }.getOrNull()?.let(frames::add)
            } finally {
                reader.dispose()
            }
        }
        return frames
    }

    private fun readVideoFrames(file: File): List<BufferedImage> {
        val frames = mutableListOf<BufferedImage>()
        var channel: SeekableByteChannel? = null
        try {
            channel = NIOUtils.readableChannel(file)
            val grab = AWTFrameGrab.createAWTFrameGrab(channel)
            repeat(runtimeSettings.maxFrames) {
                val frame = grab.frame ?: return@repeat
                frames += frame
            }
        } finally {
            NIOUtils.closeQuietly(channel)
        }
        return frames
    }

    private fun resize(source: BufferedImage, maxResolution: Int): BufferedImage {
        val limit = maxResolution.coerceIn(16, 2048)
        val largest = maxOf(source.width, source.height)
        if (largest <= limit) return source
        val scale = limit.toDouble() / largest
        val width = (source.width * scale).toInt().coerceAtLeast(1)
        val height = (source.height * scale).toInt().coerceAtLeast(1)
        return BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB).also { target ->
            val graphics: Graphics2D = target.createGraphics()
            try {
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                graphics.drawImage(source, 0, 0, width, height, null)
            } finally { graphics.dispose() }
        }
    }

    private fun copyBounded(input: InputStream, target: File, limit: Long) {
        var written = 0L
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        target.outputStream().buffered().use { output ->
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                written += read
                require(written <= limit) { "Remote media exceeds the configured file-size limit." }
                output.write(buffer, 0, read)
            }
        }
    }

    private fun ensureDisplays(media: MediaHologram) {
        if (media.frames.isEmpty() || media.location.world == null) return
        if (media.displayIds.isNotEmpty() && media.displayIds.all { Bukkit.getEntity(it)?.isValid == true }) return
        destroyDisplays(media)
        val frame = media.frames.first()
        for (row in 0 until frame.rows) for (column in 0 until frame.columns) {
            val map = Bukkit.createMap(media.location.world!!)
            media.mapViews.add(map)
            val stack = ItemStack(Material.FILLED_MAP)
            stack.itemMeta = (stack.itemMeta as MapMeta).apply { mapView = map }
            val offsetX = (column - (frame.columns - 1) / 2.0) * media.settings.scale
            val offsetY = -row * media.settings.scale
            val location = media.location.clone().add(offsetX, offsetY, 0.0)
            val display = media.location.world!!.spawn(location, ItemDisplay::class.java)
            display.setItemStack(stack)
            display.setVisibleByDefault(false)
            display.setPersistent(false)
            display.setInvulnerable(true)
            display.setGravity(false)
            display.billboard = if (media.settings.lookAtPlayer) Display.Billboard.CENTER else Display.Billboard.FIXED
            display.viewRange = media.settings.renderDistance.toFloat().coerceAtLeast(1f)
            display.addScoreboardTag("axohologram-media")
            display.addScoreboardTag("axohologram-media-${media.id}")
            media.displayIds += display.uniqueId
        }
    }

    /** Applies the same per-player visibility model used by normal holograms. */
    private fun refreshViewers(media: MediaHologram) {
        if (media.displayIds.isEmpty()) return
        val displayIds = media.displayIds.toList()
        media.viewerIds.removeIf { Bukkit.getPlayer(it)?.isOnline != true }
        Bukkit.getOnlinePlayers().forEach { player ->
            scheduler.runAtEntity(player, Runnable {
                val visible = canView(player, media)
                if (visible && media.viewerIds.add(player.uniqueId)) {
                    displayIds.mapNotNull { Bukkit.getEntity(it) }.forEach { player.showEntity(plugin, it) }
                } else if (!visible && media.viewerIds.remove(player.uniqueId)) {
                    displayIds.mapNotNull { Bukkit.getEntity(it) }.forEach { player.hideEntity(plugin, it) }
                }
                if (visible) media.frames.getOrNull(media.currentFrameIndex)?.let { sendFrameTo(player, media, it) }
            })
        }
    }

    private fun canView(player: Player, media: MediaHologram): Boolean {
        val world = media.location.world ?: return false
        if (player.world.uid != world.uid) return false
        val distance = minOf(media.settings.renderDistance, runtimeSettings.renderDistance).toDouble()
        return player.location.distanceSquared(media.location) <= distance * distance
    }

    private fun sendFrameTo(player: Player, media: MediaHologram, frame: MapFrameData) {
        frame.tiles.forEachIndexed { index, bytes ->
            media.mapViews.getOrNull(index)?.let { MapFrameRenderer.sendMapData(player, it.id, bytes) }
        }
    }

    private fun render(media: MediaHologram) {
        val frame = media.frames.getOrNull(media.currentFrameIndex) ?: return
        if (media.mapViews.size != frame.tileCount) { ensureDisplays(media); if (media.mapViews.size != frame.tileCount) return }
        frame.tiles.forEachIndexed { index, bytes -> MapFrameRenderer.updateMapData(media.mapViews[index].id, bytes) }
        media.viewerIds.toList().forEach { viewerId ->
            val player = Bukkit.getPlayer(viewerId) ?: return@forEach
            scheduler.runAtEntity(player, Runnable { sendFrameTo(player, media, frame) })
        }
    }

    private fun startPlayback() {
        if (playbackTask?.isCancelled == false) return
        playbackTask = scheduler.runGlobalTimer(1, 1, Runnable { tickPlayback() })
    }

    private fun tickPlayback() {
        val tick = Bukkit.getCurrentTick().toLong()
        for (media in mediaHolograms.values) {
            scheduler.runAtLocation(media.location, Runnable {
                refreshViewers(media)
                if (!media.isPlaying || media.frames.size < 2 || tick < media.nextFrameTick) return@Runnable
                media.nextFrameTick = tick + (20.0 / media.settings.fps.coerceIn(1, runtimeSettings.maxVideoFps)).toLong().coerceAtLeast(1)
                if (media.currentFrameIndex >= media.frames.lastIndex) {
                    if (!media.settings.loop) { media.isPlaying = false; save(); return@Runnable }
                    media.currentFrameIndex = 0
                } else media.currentFrameIndex++
                render(media)
            })
        }
    }

    private fun destroyDisplays(media: MediaHologram) {
        media.displayIds.forEach { Bukkit.getEntity(it)?.remove() }
        media.displayIds.clear()
        media.mapViews.clear()
        media.viewerIds.clear()
    }

    private fun clearRuntime() {
        playbackTask?.cancel()
        playbackTask = null
        mediaHolograms.values.forEach { media -> scheduler.runAtLocation(media.location, Runnable { destroyDisplays(media) }) }
    }

    private fun defaultSettings(type: MediaType): MediaSettings {
        val image = type == MediaType.IMAGE
        return MediaSettings(
            renderDistance = runtimeSettings.renderDistance,
            maxResolution = if (image) runtimeSettings.imageMaxResolution else runtimeSettings.videoMaxResolution,
            fps = if (image) 1 else runtimeSettings.defaultVideoFps,
            loop = !image,
            autoplay = if (image) true else runtimeSettings.videoAutoplay,
            maxFrames = runtimeSettings.maxFrames,
            maxFileSizeBytes = if (image) runtimeSettings.imageMaxFileSizeBytes else runtimeSettings.videoMaxFileSizeBytes
        )
    }

    /** Imports 3.x media records from the holograms directory without deleting original files. */
    private fun loadLegacyMedia() {
        val folder = File(mediaFolder.parentFile, "holograms")
        folder.walkTopDown().filter { it.isFile && it.extension.equals("yml", true) }.forEach { file ->
            val id = file.nameWithoutExtension
            if (mediaHolograms.containsKey(id.lowercase(Locale.ROOT))) return@forEach
            val section = YamlConfiguration.loadConfiguration(file)
            if (section.getString("type")?.uppercase(Locale.ROOT) !in setOf("IMAGE", "VIDEO") || section.getString("url").isNullOrBlank()) return@forEach
            loadMedia(id, section, legacy = true)
        }
    }

    private fun loadMedia(id: String, section: org.bukkit.configuration.ConfigurationSection, legacy: Boolean) {
        if (!HologramId.isValid(id)) return
        val rawType = section.getString("type", "IMAGE") ?: "IMAGE"
        val type = runCatching { MediaType.valueOf(rawType.uppercase(Locale.ROOT)) }.getOrNull() ?: return
        val world = Bukkit.getWorld(section.getString("location.world", "world") ?: "world") ?: return
        val location = Location(world, section.getDouble("location.x"), section.getDouble("location.y"), section.getDouble("location.z"), section.getDouble("location.yaw").toFloat(), section.getDouble("location.pitch").toFloat())
        val settingsPath = if (legacy) "settings" else "settings"
        val settings = MediaSettings(
            width = section.getDouble("$settingsPath.width", if (type == MediaType.IMAGE) 4.0 else 6.0),
            height = section.getDouble("$settingsPath.height", if (type == MediaType.IMAGE) 3.0 else 4.0),
            scale = section.getDouble("$settingsPath.scale", 1.0),
            renderDistance = section.getInt("$settingsPath.render-distance", section.getInt("visibility.distance", 32)),
            maxResolution = section.getInt("$settingsPath.max-resolution", if (type == MediaType.IMAGE) runtimeSettings.imageMaxResolution else runtimeSettings.videoMaxResolution)
                .coerceIn(16, 2048),
            lookAtPlayer = section.getBoolean("$settingsPath.look-at-player", false),
            rotation = section.getDouble("$settingsPath.rotation", 0.0).toFloat(),
            fps = section.getInt("$settingsPath.fps", if (type == MediaType.VIDEO) runtimeSettings.defaultVideoFps else 1)
                .coerceIn(1, runtimeSettings.maxVideoFps),
            loop = section.getBoolean("$settingsPath.loop", type != MediaType.IMAGE),
            autoplay = section.getBoolean("$settingsPath.autoplay", runtimeSettings.videoAutoplay),
            maxFrames = section.getInt("$settingsPath.max-frames", runtimeSettings.maxFrames).coerceIn(1, runtimeSettings.maxFrames),
            maxFileSizeBytes = section.getLong("$settingsPath.max-file-size-bytes", if (type == MediaType.VIDEO) runtimeSettings.videoMaxFileSizeBytes else runtimeSettings.imageMaxFileSizeBytes)
        )
        val source = section.getString("source") ?: section.getString("url") ?: return
        val media = MediaHologram(id, type, source, location, settings, section.getBoolean("playing", settings.autoplay), section.getInt("frame", 0))
        mediaHolograms.putIfAbsent(id.lowercase(Locale.ROOT), media)?.let { return }
        prepare(media).exceptionally { null }
    }

    private fun save() {
        val yaml = YamlConfiguration()
        for (media in mediaHolograms.values) {
            val key = "media.${media.id}"
            yaml.set("$key.type", media.type.name)
            yaml.set("$key.source", media.source)
            yaml.set("$key.location.world", media.location.world?.name ?: "world")
            yaml.set("$key.location.x", media.location.x)
            yaml.set("$key.location.y", media.location.y)
            yaml.set("$key.location.z", media.location.z)
            yaml.set("$key.location.yaw", media.location.yaw)
            yaml.set("$key.location.pitch", media.location.pitch)
            yaml.set("$key.playing", media.isPlaying)
            yaml.set("$key.frame", media.currentFrameIndex)
            yaml.set("$key.settings.width", media.settings.width)
            yaml.set("$key.settings.height", media.settings.height)
            yaml.set("$key.settings.scale", media.settings.scale)
            yaml.set("$key.settings.render-distance", media.settings.renderDistance)
            yaml.set("$key.settings.max-resolution", media.settings.maxResolution)
            yaml.set("$key.settings.look-at-player", media.settings.lookAtPlayer)
            yaml.set("$key.settings.rotation", media.settings.rotation)
            yaml.set("$key.settings.fps", media.settings.fps)
            yaml.set("$key.settings.loop", media.settings.loop)
            yaml.set("$key.settings.autoplay", media.settings.autoplay)
            yaml.set("$key.settings.max-frames", media.settings.maxFrames)
            yaml.set("$key.settings.max-file-size-bytes", media.settings.maxFileSizeBytes)
        }
        runCatching {
            if (!mediaFolder.exists()) mediaFolder.mkdirs()
            yaml.save(File(mediaFolder, "media.yml"))
        }
    }
}
