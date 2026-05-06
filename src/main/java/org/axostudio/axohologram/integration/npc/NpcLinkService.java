package org.axostudio.axohologram.integration.npc;

import org.axostudio.axohologram.AxoHologram;
import org.axostudio.axohologram.hologram.Hologram;
import org.axostudio.axohologram.util.SchedulerUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public final class NpcLinkService {

    private static final double POSITION_EPSILON = 0.0001D;
    private static final float ROTATION_EPSILON = 0.01F;

    private final AxoHologram plugin;
    private final NpcHook npcHook;
    private SchedulerUtil.TaskHandle syncTask;

    public NpcLinkService(AxoHologram plugin, NpcHook npcHook) {
        this.plugin = plugin;
        this.npcHook = npcHook;
    }

    public void start() {
        stop();
        if (!npcHook.isAvailable()) {
            return;
        }

        long interval = plugin.getConfigManager().getConfig().getLong("integrations.fancynpcs-sync-interval", 10L);
        if (interval <= 0L) {
            return;
        }

        syncTask = plugin.getSchedulerUtil().runGlobalAtFixedRate(task -> syncLinkedHolograms(), interval, interval);
        syncLinkedHolograms();
    }

    public void stop() {
        if (syncTask != null) {
            syncTask.cancel();
            syncTask = null;
        }
    }

    public boolean isAvailable() {
        return npcHook.isAvailable();
    }

    public boolean hasNpc(String npcName) {
        return npcHook.isAvailable() && npcHook.exists(npcName);
    }

    public Collection<String> getNpcNames() {
        if (!npcHook.isAvailable()) {
            return List.of();
        }

        return npcHook.getNpcNames();
    }

    public boolean link(Hologram hologram, String npcName) {
        Optional<Location> targetLocation = resolveLinkedLocation(npcName);
        if (targetLocation.isEmpty()) {
            return false;
        }

        Location location = targetLocation.get();
        if (!locationsMatch(hologram.getLocation(), location)) {
            hologram.setLocation(location, false);
        }
        hologram.setLinkedNpc(npcName);
        return true;
    }

    public void unlink(Hologram hologram) {
        hologram.setLinkedNpc(null);
    }

    public boolean isLinkedNpcAvailable(Hologram hologram) {
        String linkedNpc = hologram.getLinkedNpc();
        return linkedNpc != null && hasNpc(linkedNpc);
    }

    public void syncLinkedHolograms() {
        if (!npcHook.isAvailable() || Bukkit.getOnlinePlayers().isEmpty()) {
            return;
        }

        for (Hologram hologram : plugin.getHologramManager().getAllHolograms()) {
            if (!isLinked(hologram)) {
                continue;
            }
            syncHologram(hologram, false);
        }
    }

    public void syncHologram(Hologram hologram, boolean persist) {
        Optional<Location> targetLocation = resolveLinkedLocation(hologram.getLinkedNpc());
        if (targetLocation.isEmpty()) {
            return;
        }

        Location location = targetLocation.get();
        if (locationsMatch(hologram.getLocation(), location)) {
            return;
        }

        hologram.setLocation(location, persist);
    }

    private double resolveYOffset() {
        return plugin.getConfigManager().getConfig().getDouble("integrations.fancynpcs-y-offset", 2.2D);
    }

    private Optional<Location> resolveLinkedLocation(String npcName) {
        if (npcName == null || npcName.isBlank()) {
            return Optional.empty();
        }

        return npcHook.getLocation(npcName)
                .map(location -> location.clone().add(0.0D, resolveYOffset(), 0.0D));
    }

    private boolean isLinked(Hologram hologram) {
        String linkedNpc = hologram.getLinkedNpc();
        return linkedNpc != null && !linkedNpc.isBlank();
    }

    private boolean locationsMatch(Location first, Location second) {
        World firstWorld = first.getWorld();
        World secondWorld = second.getWorld();
        if (firstWorld != null || secondWorld != null) {
            if (firstWorld == null || secondWorld == null || !firstWorld.getUID().equals(secondWorld.getUID())) {
                return false;
            }
        }

        return Math.abs(first.getX() - second.getX()) < POSITION_EPSILON
                && Math.abs(first.getY() - second.getY()) < POSITION_EPSILON
                && Math.abs(first.getZ() - second.getZ()) < POSITION_EPSILON
                && Math.abs(first.getYaw() - second.getYaw()) < ROTATION_EPSILON
                && Math.abs(first.getPitch() - second.getPitch()) < ROTATION_EPSILON;
    }
}
