package org.axostudio.axohologram.api.hologram;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public interface HologramService {

    Optional<Hologram> get(String id);

    Hologram require(String id);

    boolean exists(String id);

    Collection<Hologram> getAll();

    Hologram create(String id, Location location, List<String> lines);

    Hologram create(String id, Location location, List<String> lines, boolean persistent);

    Hologram createItem(String id, Location location, String itemContent, boolean persistent);

    Hologram createBlock(String id, Location location, String blockContent, boolean persistent);

    Hologram createTemporary(Location location, List<String> lines, long durationTicks);

    default Hologram createTemporary(String id, Location location, List<String> lines, long durationTicks) {
        return create(id, location, lines, false);
    }

    boolean delete(String id);

    boolean delete(Hologram hologram);

    void show(Hologram hologram);

    void hide(Hologram hologram);

    default void enable(Hologram hologram) {
        show(hologram);
    }

    default void disable(Hologram hologram) {
        hide(hologram);
    }

    default boolean enable(String id) {
        Hologram hologram = getHologram(id);
        if (hologram != null) {
            enable(hologram);
            return true;
        }
        return false;
    }

    default boolean disable(String id) {
        Hologram hologram = getHologram(id);
        if (hologram != null) {
            disable(hologram);
            return true;
        }
        return false;
    }

    void reload();

    default Hologram getHologram(String id) {
        return get(id).orElse(null);
    }

    default Collection<Hologram> getAllHolograms() {
        return getAll();
    }

    default Set<String> getAllHologramIds() {
        return getAll().stream().map(Hologram::getId).collect(Collectors.toSet());
    }

    default Hologram createHologram(String id, Location location) {
        return create(id, location, Collections.emptyList());
    }

    default Hologram createHologram(String id, Location location, List<String> lines) {
        return create(id, location, lines);
    }

    default void registerHologram(Hologram hologram) {
    }

    default void deleteHologram(String id) {
        delete(id);
    }

    default void deleteHologram(Hologram hologram) {
        delete(hologram);
    }

    default void deleteAll() {
        for (Hologram h : new ArrayList<>(getAll())) {
            delete(h);
        }
    }

    default void saveAll() {
    }

    /** Notify the active implementation that a hologram's contents or style changed. */
    default void update(Hologram hologram) {
        if (hologram != null) hologram.refreshViewers();
    }
}
