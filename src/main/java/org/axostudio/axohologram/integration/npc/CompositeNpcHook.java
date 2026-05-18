package org.axostudio.axohologram.integration.npc;

import org.axostudio.axohologram.AxoHologram;
import org.bukkit.Location;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class CompositeNpcHook implements NpcHook {

    private final List<NpcHook> hooks;

    public CompositeNpcHook(Collection<NpcHook> hooks) {
        this.hooks = hooks.stream()
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public String getPluginName() {
        return hooks.stream()
                .map(NpcHook::getPluginName)
                .collect(Collectors.joining("/"));
    }

    @Override
    public boolean isAvailable() {
        return hooks.stream().anyMatch(NpcHook::isAvailable);
    }

    @Override
    public boolean exists(String npcName) {
        return hooks.stream()
                .filter(NpcHook::isAvailable)
                .anyMatch(hook -> hook.exists(npcName));
    }

    @Override
    public Optional<String> getProviderName(String npcName) {
        for (NpcHook hook : hooks) {
            if (!hook.isAvailable()) {
                continue;
            }
            if (hook.exists(npcName)) {
                return Optional.of(hook.getPluginName());
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<Location> getLocation(String npcName) {
        for (NpcHook hook : hooks) {
            if (!hook.isAvailable()) {
                continue;
            }

            Optional<Location> location = hook.getLocation(npcName);
            if (location.isPresent()) {
                return location;
            }
        }
        return Optional.empty();
    }

    @Override
    public Collection<String> getNpcNames() {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (NpcHook hook : hooks) {
            if (hook.isAvailable()) {
                names.addAll(hook.getNpcNames());
            }
        }
        return names.stream()
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
        for (NpcHook hook : hooks) {
            if (hook.isAvailable()) {
                hook.startWatching(plugin, locationUpdateConsumer, loadedConsumer, removeConsumer);
            }
        }
    }

    @Override
    public void stopWatching() {
        hooks.forEach(NpcHook::stopWatching);
    }
}
