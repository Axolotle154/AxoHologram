package org.axostudio.axohologram.integration.npc;

import org.axostudio.axohologram.AxoHologram;
import org.bukkit.Location;

import java.util.Collection;
import java.util.Optional;
import java.util.function.Consumer;

public interface NpcHook {

    String getPluginName();

    boolean isAvailable();

    boolean exists(String npcName);

    default Optional<String> getProviderName(String npcName) {
        return exists(npcName) ? Optional.of(getPluginName()) : Optional.empty();
    }

    Optional<Location> getLocation(String npcName);

    Collection<String> getNpcNames();

    default void startWatching(
            AxoHologram plugin,
            Consumer<NpcLocationUpdate> locationUpdateConsumer,
            Runnable loadedConsumer,
            Consumer<Collection<String>> removeConsumer
    ) {
    }

    default void stopWatching() {
    }

    record NpcLocationUpdate(Collection<String> identifiers, Location location) {
    }
}
