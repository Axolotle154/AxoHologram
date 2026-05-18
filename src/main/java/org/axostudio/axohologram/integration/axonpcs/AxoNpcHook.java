package org.axostudio.axohologram.integration.axonpcs;

import org.axostudio.axohologram.AxoHologram;
import org.axostudio.axohologram.integration.npc.NpcHook;
import org.axostudio.axohologram.integration.npc.NpcHook.NpcLocationUpdate;
import org.axostudio.axonpcs.api.AxoNPCsAPI;
import org.axostudio.axonpcs.api.AxoNPCsProvider;
import org.axostudio.axonpcs.api.event.AxoNPCCreateEvent;
import org.axostudio.axonpcs.api.event.AxoNPCDeleteEvent;
import org.axostudio.axonpcs.api.model.AxoNPC;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public final class AxoNpcHook implements NpcHook, Listener {

    public static final String PLUGIN_NAME = "AxoNPCs";

    private AxoHologram plugin;
    private Consumer<NpcLocationUpdate> locationUpdateConsumer;
    private Consumer<Collection<String>> removeConsumer;
    private boolean watchingEvents;

    @Override
    public String getPluginName() {
        return PLUGIN_NAME;
    }

    @Override
    public boolean isAvailable() {
        return getApi() != null;
    }

    @Override
    public boolean exists(String npcName) {
        AxoNPCsAPI api = getApi();
        return api != null && api.exists(npcName);
    }

    @Override
    public Optional<Location> getLocation(String npcName) {
        AxoNPCsAPI api = getApi();
        if (api == null || npcName == null || npcName.isBlank()) {
            return Optional.empty();
        }

        return api.getNPC(npcName)
                .map(AxoNPC::getLocation)
                .map(Location::clone);
    }

    @Override
    public Collection<String> getNpcNames() {
        AxoNPCsAPI api = getApi();
        if (api == null) {
            return List.of();
        }

        return api.getNPCs().stream()
                .map(AxoNPC::getId)
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .sorted(Comparator.comparing(String::toLowerCase))
                .toList();
    }

    @Override
    public void startWatching(
            AxoHologram plugin,
            Consumer<NpcLocationUpdate> locationUpdateConsumer,
            Runnable loadedConsumer,
            Consumer<Collection<String>> removeConsumer
    ) {
        stopWatching();
        this.plugin = plugin;
        this.locationUpdateConsumer = locationUpdateConsumer;
        this.removeConsumer = removeConsumer;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        watchingEvents = true;
    }

    @Override
    public void stopWatching() {
        if (watchingEvents) {
            HandlerList.unregisterAll(this);
        }
        watchingEvents = false;
        plugin = null;
        locationUpdateConsumer = null;
        removeConsumer = null;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onNpcCreated(AxoNPCCreateEvent event) {
        scheduleLocationUpdate(event.getNPC());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onNpcDeleted(AxoNPCDeleteEvent event) {
        Consumer<Collection<String>> consumer = removeConsumer;
        if (consumer == null || event.getNPC() == null || event.getNPC().getId() == null) {
            return;
        }

        consumer.accept(List.of(event.getNPC().getId()));
    }

    private AxoNPCsAPI getApi() {
        if (!Bukkit.getPluginManager().isPluginEnabled(PLUGIN_NAME) || !AxoNPCsProvider.isAvailable()) {
            return null;
        }

        try {
            return AxoNPCsProvider.getAPI();
        } catch (IllegalStateException exception) {
            return null;
        }
    }

    private void scheduleLocationUpdate(AxoNPC npc) {
        AxoHologram currentPlugin = plugin;
        if (currentPlugin == null || locationUpdateConsumer == null) {
            return;
        }

        currentPlugin.getSchedulerUtil().runGlobalDelayed(ignored -> publishLocationUpdate(npc), 1L);
    }

    private void publishLocationUpdate(AxoNPC npc) {
        Consumer<NpcLocationUpdate> consumer = locationUpdateConsumer;
        if (consumer == null || npc == null || npc.getId() == null || npc.getLocation() == null) {
            return;
        }

        consumer.accept(new NpcLocationUpdate(List.of(npc.getId()), npc.getLocation().clone()));
    }
}
