package org.axostudio.axohologram.integration.npc;

import org.axostudio.axohologram.AxoHologram;
import org.axostudio.axohologram.hologram.Hologram;
import org.axostudio.axohologram.integration.fancynpcs.FancyNpcHook;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class NpcLinkService {

    private static final double POSITION_EPSILON = 0.0001D;
    private static final float ROTATION_EPSILON = 0.01F;

    private final AxoHologram plugin;
    private final NpcHook npcHook;
    private final Map<String, Location> lastNpcLocations = new ConcurrentHashMap<>();

    public NpcLinkService(AxoHologram plugin, NpcHook npcHook) {
        this.plugin = plugin;
        this.npcHook = npcHook;
    }

    public void start() {
        stop();
        if (!npcHook.isAvailable()) {
            return;
        }

        if (npcHook instanceof FancyNpcHook fancyNpcHook) {
            fancyNpcHook.startWatching(
                    plugin,
                    this::syncLinkedNpcLocation,
                    this::syncLinkedHolograms,
                    this::forgetNpcLocations
            );
        }
        syncLinkedHolograms();
    }

    public void stop() {
        if (npcHook instanceof FancyNpcHook fancyNpcHook) {
            fancyNpcHook.stopWatching();
        }
        lastNpcLocations.clear();
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
        Optional<Location> npcLocation = resolveNpcLocation(npcName);
        if (npcLocation.isEmpty()) {
            return false;
        }

        syncSingleHologram(hologram, npcLocation.get(), false);
        hologram.setLinkedNpc(npcName);
        rememberNpcLocation(List.of(npcName), npcLocation.get());
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
        if (!npcHook.isAvailable()) {
            return;
        }

        Map<String, String> linkedNpcNames = new LinkedHashMap<>();
        for (Hologram hologram : plugin.getHologramManager().getNpcLinkedHolograms()) {
            String linkedNpc = hologram.getLinkedNpc();
            if (linkedNpc == null || linkedNpc.isBlank()) {
                continue;
            }
            linkedNpcNames.putIfAbsent(normalizeNpcKey(linkedNpc), linkedNpc);
        }

        for (String npcName : linkedNpcNames.values()) {
            syncLinkedNpcIfMoved(npcName, false);
        }
    }

    public void syncHologram(Hologram hologram, boolean persist) {
        String linkedNpc = hologram.getLinkedNpc();
        Optional<Location> npcLocation = resolveNpcLocation(linkedNpc);
        if (npcLocation.isEmpty()) {
            return;
        }

        syncSingleHologram(hologram, npcLocation.get(), persist);
        rememberNpcLocation(List.of(linkedNpc), npcLocation.get());
    }

    private void syncLinkedNpcLocation(FancyNpcHook.NpcLocationUpdate update) {
        if (update == null || update.location() == null || update.identifiers().isEmpty()) {
            return;
        }

        syncLinkedNpcLocation(update.identifiers(), update.location(), false);
    }

    private void syncLinkedNpcIfMoved(String npcName, boolean persist) {
        Optional<Location> npcLocation = resolveNpcLocation(npcName);
        npcLocation.ifPresent(location -> syncLinkedNpcLocation(List.of(npcName), location, persist));
    }

    private void syncLinkedNpcLocation(Collection<String> npcNames, Location npcLocation, boolean persist) {
        String cacheKey = primaryCacheKey(npcNames);
        if (cacheKey == null) {
            return;
        }

        Location previousLocation = lastNpcLocations.get(cacheKey);
        if (previousLocation != null && locationsMatch(previousLocation, npcLocation)) {
            return;
        }

        boolean hasLinkedHolograms = false;
        for (Hologram hologram : plugin.getHologramManager().getNpcLinkedHolograms()) {
            if (matchesLinkedNpc(hologram, npcNames)) {
                hasLinkedHolograms = true;
                break;
            }
        }
        if (!hasLinkedHolograms) {
            rememberNpcLocation(npcNames, npcLocation);
            return;
        }

        double yOffset = resolveYOffset();
        Location targetLocation = npcLocation.clone().add(0.0D, yOffset, 0.0D);
        rememberNpcLocation(npcNames, npcLocation);

        for (Hologram hologram : plugin.getHologramManager().getNpcLinkedHolograms()) {
            if (!matchesLinkedNpc(hologram, npcNames)) {
                continue;
            }
            if (!locationsMatch(hologram.getLocation(), targetLocation)) {
                hologram.setLocation(targetLocation, persist);
            }
        }
    }

    private double resolveYOffset() {
        return plugin.getConfigManager().getConfig().getDouble("integrations.fancynpcs-y-offset", 2.2D);
    }

    private Optional<Location> resolveNpcLocation(String npcName) {
        if (npcName == null || npcName.isBlank()) {
            return Optional.empty();
        }

        return npcHook.getLocation(npcName)
                .map(Location::clone);
    }

    private void syncSingleHologram(Hologram hologram, Location npcLocation, boolean persist) {
        double yOffset = resolveYOffset();
        Location targetLocation = npcLocation.clone().add(0.0D, yOffset, 0.0D);
        if (!locationsMatch(hologram.getLocation(), targetLocation)) {
            hologram.setLocation(targetLocation, persist);
        }
    }

    private void rememberNpcLocation(Collection<String> npcNames, Location location) {
        if (location == null) {
            return;
        }

        for (String npcName : npcNames) {
            String key = normalizeNpcKey(npcName);
            if (!key.isBlank()) {
                lastNpcLocations.put(key, location.clone());
            }
        }
    }

    private void forgetNpcLocations(Collection<String> npcNames) {
        for (String npcName : npcNames) {
            lastNpcLocations.remove(normalizeNpcKey(npcName));
        }
    }

    private boolean matchesLinkedNpc(Hologram hologram, Collection<String> npcNames) {
        String linkedNpc = normalizeNpcKey(hologram.getLinkedNpc());
        if (linkedNpc.isBlank()) {
            return false;
        }

        for (String npcName : npcNames) {
            if (linkedNpc.equals(normalizeNpcKey(npcName))) {
                return true;
            }
        }
        return false;
    }

    private String primaryCacheKey(Collection<String> npcNames) {
        for (String npcName : npcNames) {
            String key = normalizeNpcKey(npcName);
            if (!key.isBlank()) {
                return key;
            }
        }
        return null;
    }

    private String normalizeNpcKey(String npcName) {
        return npcName == null ? "" : npcName.trim().toLowerCase(Locale.ROOT);
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
