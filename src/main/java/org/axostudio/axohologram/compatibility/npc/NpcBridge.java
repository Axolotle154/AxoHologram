package org.axostudio.axohologram.compatibility.npc;

import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Common Java bridge interface for hooking into NPC systems (Citizens, FancyNpcs, AxoNpcs).
 */
public interface NpcBridge {

    @NotNull
    String getProviderName();

    boolean isAvailable();

    boolean exists(@NotNull String npcIdentifier);

    @NotNull
    Optional<Location> getLocation(@NotNull String npcIdentifier);

    @NotNull
    Collection<String> getNpcIdentifiers();

    default void startWatching(
            @NotNull Consumer<NpcLocationUpdate> locationUpdateConsumer,
            @NotNull Runnable loadedConsumer,
            @NotNull Consumer<Collection<String>> removeConsumer
    ) {
    }

    default void stopWatching() {
    }

    record NpcLocationUpdate(
            @NotNull Collection<String> identifiers,
            @NotNull Location location
    ) {
    }
}
