package org.axostudio.axohologram.listener;

import org.axostudio.axohologram.AxoHologram;
import org.axostudio.axohologram.config.VisibilityRuntimeConfig;
import org.axostudio.axohologram.hologram.Hologram;
import org.axostudio.axohologram.packet.HologramPacketManager;
import org.axostudio.axohologram.util.MiniMessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.server.PluginDisableEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerListener implements Listener {

    private static final long[] JOIN_VISIBILITY_DELAYS = {5L, 20L, 60L};
    private static final long[] JOIN_PLACEHOLDER_REFRESH_DELAYS = {40L, 100L};
    private static final long[] TRANSITION_VISIBILITY_DELAYS = {1L, 10L, 40L};

    private final AxoHologram plugin;
    private final Map<UUID, Long> scheduledVisibilityRefreshes = new ConcurrentHashMap<>();
    private final Map<UUID, Long> mediaLookRefreshes = new ConcurrentHashMap<>();
    private final Map<UUID, MovementState> movementStates = new ConcurrentHashMap<>();

    public PlayerListener(AxoHologram plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        rememberMovementPosition(player.getUniqueId(), player.getLocation());
        HologramPacketManager.hideAllTrackedEntitiesForPlayer(player);
        scheduleVisibilityRefreshes(player, true, JOIN_VISIBILITY_DELAYS);
        scheduleMediaVisibilityRefreshes(player, JOIN_VISIBILITY_DELAYS);
        schedulePlaceholderRefreshes(player, JOIN_PLACEHOLDER_REFRESH_DELAYS);
        plugin.getSchedulerUtil().runAtEntityDelayed(player, () -> {
            if (plugin.getUpdateChecker() != null) {
                plugin.getUpdateChecker().notifyPlayer(player);
            }
        }, 40L);
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        plugin.handleOptionalPluginEnabled(event.getPlugin().getName());
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        plugin.handleOptionalPluginDisabled(event.getPlugin().getName());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        for (Hologram hologram : plugin.getHologramManager().getAllHolograms()) {
            hologram.hide(player);
        }
        if (plugin.getMediaManager() != null) {
            plugin.getMediaManager().removeViewer(player);
        }
        MiniMessageUtil.clearPlaceholderApiCache(player.getUniqueId());
        plugin.getHologramManager().clearVisibilityState(player.getUniqueId());
        scheduledVisibilityRefreshes.remove(player.getUniqueId());
        mediaLookRefreshes.remove(player.getUniqueId());
        movementStates.remove(player.getUniqueId());
        // Clean up any remaining packet data for the player
        HologramPacketManager.destroyAllHologramLinesForPlayer(player);
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        rememberMovementPosition(player.getUniqueId(), player.getLocation());
        plugin.getHologramManager().resetPlayerRenderState(player);
        if (plugin.getMediaManager() != null) {
            plugin.getMediaManager().removeViewer(player);
        }
        plugin.getHologramManager().clearVisibilityState(player.getUniqueId());
        mediaLookRefreshes.remove(player.getUniqueId());
        MiniMessageUtil.clearPlaceholderApiCache(player.getUniqueId());
        scheduleVisibilityRefreshes(player, true, TRANSITION_VISIBILITY_DELAYS);
        scheduleMediaVisibilityRefreshes(player, TRANSITION_VISIBILITY_DELAYS);
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        movementStates.remove(player.getUniqueId());
        plugin.getHologramManager().resetPlayerRenderState(player);
        if (plugin.getMediaManager() != null) {
            plugin.getMediaManager().removeViewer(player);
        }
        plugin.getHologramManager().clearVisibilityState(player.getUniqueId());
        mediaLookRefreshes.remove(player.getUniqueId());
        MiniMessageUtil.clearPlaceholderApiCache(player.getUniqueId());
        scheduleVisibilityRefreshes(player, true, JOIN_VISIBILITY_DELAYS);
        scheduleMediaVisibilityRefreshes(player, JOIN_VISIBILITY_DELAYS);
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (event.getTo() == null) {
            return;
        }
        if (event.getFrom().getWorld() != event.getTo().getWorld()) {
            return;
        }

        scheduleVisibilityRefreshes(event.getPlayer(), TRANSITION_VISIBILITY_DELAYS);
        scheduleMediaVisibilityRefreshes(event.getPlayer(), TRANSITION_VISIBILITY_DELAYS);
        rememberMovementPosition(event.getPlayer().getUniqueId(), event.getTo());
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getTo() == null) {
            return;
        }
        UUID playerId = event.getPlayer().getUniqueId();
        boolean shouldHandleMovement = shouldHandleMovement(playerId, event.getFrom(), event.getTo());
        boolean shouldHandleMediaLook = shouldHandleMediaLookChange(event.getPlayer(), event.getFrom(), event.getTo());
        if (!shouldHandleMovement && !shouldHandleMediaLook) {
            return;
        }

        if (shouldHandleMovement) {
            rememberMovementPosition(playerId, event.getTo());
            plugin.getHologramManager().handlePlayerMovement(event.getPlayer(), event.getTo());
        }
        if (plugin.getMediaManager() != null) {
            plugin.getMediaManager().refreshPlayerVisibility(event.getPlayer());
        }
    }

    private void scheduleMediaVisibilityRefreshes(Player player, long... delays) {
        if (plugin.getMediaManager() == null || !plugin.getMediaManager().hasMediaHolograms()) {
            return;
        }

        for (long delay : delays) {
            plugin.getSchedulerUtil().runAtEntityDelayed(player, () -> {
                if (player.isOnline() && plugin.getMediaManager() != null) {
                    plugin.getMediaManager().refreshPlayerVisibility(player);
                }
            }, Math.max(1L, delay));
        }
    }

    private void scheduleVisibilityRefreshes(Player player, long... delays) {
        scheduleVisibilityRefreshes(player, false, delays);
    }

    private void scheduleVisibilityRefreshes(Player player, boolean force, long... delays) {
        if (plugin.getHologramManager().getAllHolograms().isEmpty()) {
            return;
        }

        UUID playerId = player.getUniqueId();
        long currentTick = Bukkit.getCurrentTick();
        long minimumInterval = resolveVisibilityRefreshInterval();
        for (long delay : resolveVisibilityRefreshDelays(delays, force)) {
            long targetTick = currentTick + Math.max(1L, delay);
            Long scheduledTick = scheduledVisibilityRefreshes.get(playerId);
            if (!force && scheduledTick != null && scheduledTick >= currentTick && Math.abs(scheduledTick - targetTick) < minimumInterval) {
                continue;
            }
            scheduledVisibilityRefreshes.put(playerId, targetTick);
            plugin.getSchedulerUtil().runAtEntityDelayed(player, () -> {
                if (!player.isOnline() || plugin.getHologramManager().getAllHolograms().isEmpty()) {
                    scheduledVisibilityRefreshes.remove(playerId);
                    return;
                }

                Long expectedTick = scheduledVisibilityRefreshes.get(playerId);
                if (expectedTick != null && expectedTick <= Bukkit.getCurrentTick()) {
                    scheduledVisibilityRefreshes.remove(playerId, expectedTick);
                }
                plugin.getHologramManager().updateVisibilityForPlayer(player, force);
            }, delay);
        }
    }

    private void schedulePlaceholderRefreshes(Player player, long... delays) {
        if (plugin.getHologramManager().getAllHolograms().isEmpty()) {
            return;
        }

        UUID playerId = player.getUniqueId();
        for (long delay : delays) {
            plugin.getSchedulerUtil().runAtEntityDelayed(player, () -> {
                if (!player.isOnline() || plugin.getHologramManager().getAllHolograms().isEmpty()) {
                    return;
                }

                MiniMessageUtil.clearPlaceholderApiCache(playerId);
                plugin.getHologramManager().updateVisibilityForPlayer(player, true);
            }, Math.max(1L, delay));
        }
    }

    private boolean shouldHandleMovement(UUID playerId, Location from, Location to) {
        if (from.getWorld() != to.getWorld()) {
            return true;
        }
        if (Double.compare(from.getX(), to.getX()) == 0
                && Double.compare(from.getY(), to.getY()) == 0
                && Double.compare(from.getZ(), to.getZ()) == 0) {
            return false;
        }

        MovementState previous = movementStates.computeIfAbsent(playerId, ignored -> MovementState.from(from));
        if (!previous.sameWorld(to)) {
            return true;
        }

        VisibilityRuntimeConfig config = plugin.getConfigManager().getVisibilityRuntimeConfig();
        return switch (config.movementCheckMode()) {
            case CHUNK -> previous.chunkX() != to.getBlockX() >> 4
                    || previous.chunkZ() != to.getBlockZ() >> 4;
            case DISTANCE -> config.hasMovedRequiredDistance(previous.x(), previous.y(), previous.z(), to.getX(), to.getY(), to.getZ());
            case BLOCK -> previous.blockX() != to.getBlockX()
                    || previous.blockY() != to.getBlockY()
                    || previous.blockZ() != to.getBlockZ();
        };
    }

    private boolean shouldHandleMediaLookChange(Player player, Location from, Location to) {
        if (player == null
                || plugin.getMediaManager() == null
                || !plugin.getMediaManager().hasMediaHolograms()
                || !plugin.getMediaManager().usesLineOfSightVisibility()) {
            return false;
        }
        if (Float.compare(from.getYaw(), to.getYaw()) == 0 && Float.compare(from.getPitch(), to.getPitch()) == 0) {
            return false;
        }

        UUID playerId = player.getUniqueId();
        long currentTick = Bukkit.getCurrentTick();
        long interval = Math.max(1L, plugin.getMediaManager().visibilityRefreshIntervalTicks());
        Long previousTick = mediaLookRefreshes.get(playerId);
        if (previousTick != null && currentTick - previousTick < interval) {
            return false;
        }
        mediaLookRefreshes.put(playerId, currentTick);
        return true;
    }

    private long[] resolveVisibilityRefreshDelays(long[] delays, boolean force) {
        if (delays.length == 0) {
            return delays;
        }
        if (force) {
            long[] resolved = new long[delays.length];
            for (int i = 0; i < delays.length; i++) {
                resolved[i] = Math.max(1L, delays[i]);
            }
            return resolved;
        }
        if (isEventDrivenVisibilityMode()) {
            return new long[]{Math.max(1L, delays[0])};
        }

        long minimumInterval = resolveVisibilityRefreshInterval();
        if (minimumInterval <= 0L || delays.length <= 1) {
            return delays;
        }

        long[] resolved = new long[delays.length];
        long previous = Long.MIN_VALUE;
        for (int i = 0; i < delays.length; i++) {
            long delay = Math.max(1L, delays[i]);
            if (previous != Long.MIN_VALUE && delay - previous < minimumInterval) {
                delay = previous + minimumInterval;
            }
            resolved[i] = delay;
            previous = delay;
        }
        return resolved;
    }

    private long resolveVisibilityRefreshInterval() {
        return plugin.getConfigManager().getVisibilityRuntimeConfig().visibilityRefreshIntervalTicks();
    }

    private boolean isEventDrivenVisibilityMode() {
        return !plugin.getConfigManager().getVisibilityRuntimeConfig().periodicVisibilityTaskEnabled();
    }

    private void rememberMovementPosition(UUID playerId, Location location) {
        if (playerId != null && location != null) {
            movementStates.put(playerId, MovementState.from(location));
        }
    }

    private record MovementState(
            org.bukkit.World world,
            double x,
            double y,
            double z,
            int blockX,
            int blockY,
            int blockZ,
            int chunkX,
            int chunkZ
    ) {
        private static MovementState from(Location location) {
            return new MovementState(
                    location.getWorld(),
                    location.getX(),
                    location.getY(),
                    location.getZ(),
                    location.getBlockX(),
                    location.getBlockY(),
                    location.getBlockZ(),
                    location.getBlockX() >> 4,
                    location.getBlockZ() >> 4
            );
        }

        private boolean sameWorld(Location location) {
            return location != null && world == location.getWorld();
        }
    }
}
