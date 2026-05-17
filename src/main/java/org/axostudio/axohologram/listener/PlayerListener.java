package org.axostudio.axohologram.listener;

import org.axostudio.axohologram.AxoHologram;
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

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerListener implements Listener {

    private static final long[] JOIN_VISIBILITY_DELAYS = {5L, 20L, 60L};
    private static final long[] TRANSITION_VISIBILITY_DELAYS = {1L, 10L, 40L};

    private final AxoHologram plugin;
    private final Map<UUID, Long> scheduledVisibilityRefreshes = new ConcurrentHashMap<>();

    public PlayerListener(AxoHologram plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        HologramPacketManager.hideAllTrackedEntitiesForPlayer(player);
        scheduleVisibilityRefreshes(player, JOIN_VISIBILITY_DELAYS);
        plugin.getSchedulerUtil().runAtEntityDelayed(player, () -> {
            if (plugin.getUpdateChecker() != null) {
                plugin.getUpdateChecker().notifyPlayer(player);
            }
        }, 40L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        for (Hologram hologram : plugin.getHologramManager().getAllHolograms()) {
            hologram.hide(player);
        }
        MiniMessageUtil.clearPlaceholderApiCache(player.getUniqueId());
        plugin.getHologramManager().clearVisibilityState(player.getUniqueId());
        scheduledVisibilityRefreshes.remove(player.getUniqueId());
        // Clean up any remaining packet data for the player
        HologramPacketManager.destroyAllHologramLinesForPlayer(player);
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        HologramPacketManager.hideAllTrackedEntitiesForPlayer(player);
        scheduleVisibilityRefreshes(player, TRANSITION_VISIBILITY_DELAYS);
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        HologramPacketManager.hideAllTrackedEntitiesForPlayer(player);
        scheduleVisibilityRefreshes(player, JOIN_VISIBILITY_DELAYS);
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (event.getTo() == null) {
            return;
        }

        scheduleVisibilityRefreshes(event.getPlayer(), TRANSITION_VISIBILITY_DELAYS);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getTo() == null) {
            return;
        }
        if (!shouldHandleMovement(event.getFrom(), event.getTo())) {
            return;
        }

        plugin.getHologramManager().handlePlayerMovement(event.getPlayer(), event.getTo());
    }

    private void scheduleVisibilityRefreshes(Player player, long... delays) {
        if (plugin.getHologramManager().getAllHolograms().isEmpty()) {
            return;
        }

        UUID playerId = player.getUniqueId();
        long currentTick = Bukkit.getCurrentTick();
        long minimumInterval = resolveVisibilityRefreshInterval();
        for (long delay : resolveVisibilityRefreshDelays(delays)) {
            long targetTick = currentTick + Math.max(1L, delay);
            Long scheduledTick = scheduledVisibilityRefreshes.get(playerId);
            if (scheduledTick != null && scheduledTick >= currentTick && Math.abs(scheduledTick - targetTick) < minimumInterval) {
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
                plugin.getHologramManager().updateVisibilityForPlayer(player, false);
            }, delay);
        }
    }

    private boolean shouldHandleMovement(Location from, Location to) {
        if (from.getWorld() != to.getWorld()) {
            return true;
        }
        if (Double.compare(from.getX(), to.getX()) == 0
                && Double.compare(from.getY(), to.getY()) == 0
                && Double.compare(from.getZ(), to.getZ()) == 0) {
            return false;
        }

        String mode = plugin.getConfigManager().getConfig().getString("performance.movement-check-mode", "BLOCK");
        return switch (mode == null ? "BLOCK" : mode.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "CHUNK" -> from.getBlockX() >> 4 != to.getBlockX() >> 4
                    || from.getBlockZ() >> 4 != to.getBlockZ() >> 4;
            case "DISTANCE" -> true;
            default -> from.getBlockX() != to.getBlockX()
                    || from.getBlockY() != to.getBlockY()
                    || from.getBlockZ() != to.getBlockZ();
        };
    }

    private long[] resolveVisibilityRefreshDelays(long[] delays) {
        if (delays.length == 0) {
            return delays;
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
        return plugin.getConfigManager().getConfig().getLong(
                "performance.visibility-refresh-interval-ticks",
                plugin.getConfigManager().getConfig().getLong("performance.visibility-refresh-interval", 100L)
        );
    }

    private boolean isEventDrivenVisibilityMode() {
        if (plugin.getConfigManager().getConfig().contains("visibility.periodic-task-enabled")) {
            return !plugin.getConfigManager().getConfig().getBoolean("visibility.periodic-task-enabled");
        }
        if (plugin.getConfigManager().getConfig().contains("performance.visibility-periodic-task-enabled")) {
            return !plugin.getConfigManager().getConfig().getBoolean("performance.visibility-periodic-task-enabled");
        }

        String mode = plugin.getConfigManager().getConfig().getString("visibility.mode", "EVENT_DRIVEN");
        return mode == null
                || mode.equalsIgnoreCase("EVENT_DRIVEN")
                || mode.equalsIgnoreCase("EVENT");
    }
}
