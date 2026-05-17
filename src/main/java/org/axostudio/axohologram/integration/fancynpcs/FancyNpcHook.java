package org.axostudio.axohologram.integration.fancynpcs;

import de.oliver.fancynpcs.api.FancyNpcsPlugin;
import de.oliver.fancynpcs.api.Npc;
import de.oliver.fancynpcs.api.NpcData;
import de.oliver.fancynpcs.api.NpcManager;
import de.oliver.fancynpcs.api.events.NpcCreateEvent;
import de.oliver.fancynpcs.api.events.NpcModifyEvent;
import de.oliver.fancynpcs.api.events.NpcRemoveEvent;
import de.oliver.fancynpcs.api.events.NpcsLoadedEvent;
import org.axostudio.axohologram.AxoHologram;
import org.axostudio.axohologram.integration.npc.NpcHook;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

public final class FancyNpcHook implements NpcHook, Listener {

    private static final String PLUGIN_NAME = "FancyNpcs";
    private AxoHologram plugin;
    private Consumer<NpcLocationUpdate> locationUpdateConsumer;
    private Consumer<Collection<String>> removeConsumer;
    private Runnable loadedConsumer;
    private boolean watchingEvents;

    @Override
    public String getPluginName() {
        return PLUGIN_NAME;
    }

    @Override
    public boolean isAvailable() {
        return Bukkit.getPluginManager().isPluginEnabled(PLUGIN_NAME) && getNpcManager() != null;
    }

    @Override
    public boolean exists(String npcName) {
        return findNpc(npcName) != null;
    }

    @Override
    public Optional<Location> getLocation(String npcName) {
        Npc npc = findNpc(npcName);
        if (npc == null) {
            return Optional.empty();
        }

        NpcData data = npc.getData();
        if (data == null || data.getLocation() == null) {
            return Optional.empty();
        }

        return Optional.of(data.getLocation().clone());
    }

    @Override
    public Collection<String> getNpcNames() {
        NpcManager npcManager = getNpcManager();
        if (npcManager == null) {
            return List.of();
        }

        return npcManager.getAllNpcs().stream()
                .map(this::resolveNpcName)
                .filter(Objects::nonNull)
                .filter(name -> !name.isBlank())
                .distinct()
                .sorted(Comparator.comparing(String::toLowerCase))
                .toList();
    }

    public void startWatching(
            AxoHologram plugin,
            Consumer<NpcLocationUpdate> locationUpdateConsumer,
            Runnable loadedConsumer,
            Consumer<Collection<String>> removeConsumer
    ) {
        stopWatching();
        this.plugin = plugin;
        this.locationUpdateConsumer = locationUpdateConsumer;
        this.loadedConsumer = loadedConsumer;
        this.removeConsumer = removeConsumer;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        watchingEvents = true;
    }

    public void stopWatching() {
        if (watchingEvents) {
            HandlerList.unregisterAll(this);
        }
        watchingEvents = false;
        plugin = null;
        locationUpdateConsumer = null;
        loadedConsumer = null;
        removeConsumer = null;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onNpcModified(NpcModifyEvent event) {
        if (event.getModification() != NpcModifyEvent.NpcModification.LOCATION
                && event.getModification() != NpcModifyEvent.NpcModification.ROTATION) {
            return;
        }
        scheduleLocationUpdate(event.getNpc());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onNpcCreated(NpcCreateEvent event) {
        scheduleLocationUpdate(event.getNpc());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onNpcRemoved(NpcRemoveEvent event) {
        Consumer<Collection<String>> consumer = removeConsumer;
        if (consumer == null) {
            return;
        }
        Collection<String> identifiers = resolveNpcIdentifiers(event.getNpc());
        if (!identifiers.isEmpty()) {
            consumer.accept(identifiers);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onNpcsLoaded(NpcsLoadedEvent event) {
        Runnable consumer = loadedConsumer;
        if (consumer != null) {
            consumer.run();
        }
    }

    private Npc findNpc(String npcName) {
        if (npcName == null || npcName.isBlank()) {
            return null;
        }

        NpcManager npcManager = getNpcManager();
        if (npcManager == null) {
            return null;
        }

        return npcManager.getNpc(npcName);
    }

    private NpcManager getNpcManager() {
        if (!Bukkit.getPluginManager().isPluginEnabled(PLUGIN_NAME)) {
            return null;
        }

        FancyNpcsPlugin plugin = FancyNpcsPlugin.get();
        return plugin == null ? null : plugin.getNpcManager();
    }

    private String resolveNpcName(Npc npc) {
        NpcData data = npc.getData();
        if (data == null) {
            return null;
        }

        if (data.getName() != null && !data.getName().isBlank()) {
            return data.getName();
        }
        return data.getId();
    }

    private void scheduleLocationUpdate(Npc npc) {
        AxoHologram currentPlugin = plugin;
        if (currentPlugin == null || locationUpdateConsumer == null) {
            return;
        }

        currentPlugin.getSchedulerUtil().runGlobalDelayed(ignored -> publishLocationUpdate(npc), 1L);
    }

    private void publishLocationUpdate(Npc npc) {
        Consumer<NpcLocationUpdate> consumer = locationUpdateConsumer;
        if (consumer == null) {
            return;
        }

        NpcLocationUpdate update = createLocationUpdate(npc);
        if (update != null) {
            consumer.accept(update);
        }
    }

    private NpcLocationUpdate createLocationUpdate(Npc npc) {
        if (npc == null || npc.getData() == null || npc.getData().getLocation() == null) {
            return null;
        }

        Collection<String> identifiers = resolveNpcIdentifiers(npc);
        if (identifiers.isEmpty()) {
            return null;
        }

        return new NpcLocationUpdate(identifiers, npc.getData().getLocation().clone());
    }

    private Collection<String> resolveNpcIdentifiers(Npc npc) {
        if (npc == null || npc.getData() == null) {
            return List.of();
        }

        NpcData data = npc.getData();
        LinkedHashSet<String> identifiers = new LinkedHashSet<>();
        if (data.getName() != null && !data.getName().isBlank()) {
            identifiers.add(data.getName());
        }
        if (data.getId() != null && !data.getId().isBlank()) {
            identifiers.add(data.getId());
        }
        return List.copyOf(identifiers);
    }

    public record NpcLocationUpdate(Collection<String> identifiers, Location location) {
    }
}
