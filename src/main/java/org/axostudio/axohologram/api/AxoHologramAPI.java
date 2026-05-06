package org.axostudio.axohologram.api;

import org.axostudio.axohologram.hologram.Hologram;
import org.bukkit.Location;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Public API for creating and controlling AxoHologram holograms from other plugins.
 */
public interface AxoHologramAPI {

    /**
     * Creates a persistent text hologram and saves it to YAML.
     *
     * @param id unique hologram id
     * @param location hologram location with a loaded world
     * @param lines text lines to display
     * @return created hologram
     */
    Hologram createHologram(String id, Location location, List<String> lines);

    /**
     * Creates a text hologram.
     *
     * @param id unique hologram id
     * @param location hologram location with a loaded world
     * @param lines text lines to display
     * @param saveToYaml true for persistent holograms, false for runtime-only holograms
     * @return created hologram
     */
    Hologram createHologram(String id, Location location, List<String> lines, boolean saveToYaml);

    /**
     * Creates a temporary text hologram with an automatically generated id.
     *
     * @param location hologram location with a loaded world
     * @param lines text lines to display
     * @return created temporary hologram
     */
    Hologram createTemporaryHologram(Location location, List<String> lines);

    /**
     * Creates a temporary text hologram. If id is null or blank, an id is generated.
     *
     * @param id optional unique hologram id
     * @param location hologram location with a loaded world
     * @param lines text lines to display
     * @return created temporary hologram
     */
    Hologram createTemporaryHologram(String id, Location location, List<String> lines);

    /**
     * Creates a temporary text hologram and removes it after the given duration.
     *
     * @param location hologram location with a loaded world
     * @param lines text lines to display
     * @param durationTicks lifetime in server ticks; values <= 0 do not schedule removal
     * @return created temporary hologram
     */
    Hologram createTemporaryHologram(Location location, List<String> lines, long durationTicks);

    /**
     * Creates a temporary text hologram and removes it after the given duration.
     *
     * @param id optional unique hologram id
     * @param location hologram location with a loaded world
     * @param lines text lines to display
     * @param durationTicks lifetime in server ticks; values <= 0 do not schedule removal
     * @return created temporary hologram
     */
    Hologram createTemporaryHologram(String id, Location location, List<String> lines, long durationTicks);

    /**
     * Deletes a persistent or temporary hologram.
     *
     * @param id hologram id
     * @return true if a hologram was removed
     */
    boolean deleteHologram(String id);

    /**
     * Checks whether a hologram exists.
     *
     * @param id hologram id
     * @return true when the hologram is currently registered
     */
    boolean exists(String id);

    /**
     * Gets a hologram by id.
     *
     * @param id hologram id
     * @return optional hologram
     */
    Optional<Hologram> getHologram(String id);

    /**
     * Returns all currently registered persistent and temporary holograms.
     *
     * @return immutable collection view
     */
    Collection<Hologram> getHolograms();

    /**
     * Replaces the first page lines of a hologram.
     *
     * @param id hologram id
     * @param lines replacement text lines
     */
    void updateLines(String id, List<String> lines);

    /**
     * Moves a hologram to another location.
     *
     * @param id hologram id
     * @param location target location with a loaded world
     */
    void teleportHologram(String id, Location location);

    /**
     * Shows a hologram to all online players that can view it.
     *
     * @param id hologram id
     */
    void showHologram(String id);

    /**
     * Hides a hologram from all current viewers.
     *
     * @param id hologram id
     */
    void hideHologram(String id);
}
