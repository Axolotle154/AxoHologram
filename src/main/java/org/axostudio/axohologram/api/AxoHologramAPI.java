package org.axostudio.axohologram.api;

import org.axostudio.axohologram.hologram.Hologram;
import org.axostudio.axohologram.hologram.line.HologramLine;
import org.bukkit.Location;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Public API for creating and controlling AxoHologram holograms from other plugins.
 */
@SuppressWarnings({"unused", "UnusedReturnValue"})
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
     * Creates a persistent item hologram and saves it to YAML.
     * <p>
     * The item content accepts material names and {@code PLAYER_HEAD(identifier)}.
     * The optional {@code #ITEM:} prefix is also accepted. Player heads support
     * player name, UUID, base64/value, texture URL and texture hash identifiers.
     *
     * @param id unique hologram id
     * @param location hologram location with a loaded world
     * @param itemContent item content to display
     * @return created hologram
     */
    Hologram createItemHologram(String id, Location location, String itemContent);

    /**
     * Creates an item hologram.
     *
     * @param id unique hologram id
     * @param location hologram location with a loaded world
     * @param itemContent item content to display
     * @param saveToYaml true for persistent holograms, false for runtime-only holograms
     * @return created hologram
     */
    Hologram createItemHologram(String id, Location location, String itemContent, boolean saveToYaml);

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
     * Creates a temporary item hologram with an automatically generated id.
     *
     * @param location hologram location with a loaded world
     * @param itemContent item content to display
     * @return created temporary hologram
     */
    Hologram createTemporaryItemHologram(Location location, String itemContent);

    /**
     * Creates a temporary item hologram. If id is null or blank, an id is generated.
     *
     * @param id optional unique hologram id
     * @param location hologram location with a loaded world
     * @param itemContent item content to display
     * @return created temporary hologram
     */
    Hologram createTemporaryItemHologram(String id, Location location, String itemContent);

    /**
     * Creates a temporary item hologram and removes it after the given duration.
     *
     * @param location hologram location with a loaded world
     * @param itemContent item content to display
     * @param durationTicks lifetime in server ticks; values <= 0 do not schedule removal
     * @return created temporary hologram
     */
    Hologram createTemporaryItemHologram(Location location, String itemContent, long durationTicks);

    /**
     * Creates a temporary item hologram and removes it after the given duration.
     *
     * @param id optional unique hologram id
     * @param location hologram location with a loaded world
     * @param itemContent item content to display
     * @param durationTicks lifetime in server ticks; values <= 0 do not schedule removal
     * @return created temporary hologram
     */
    Hologram createTemporaryItemHologram(String id, Location location, String itemContent, long durationTicks);

    /**
     * Deletes a persistent or temporary hologram.
     *
     * @param id hologram id
     * @return true if a hologram was removed
     */
    boolean deleteHologram(String id);

    /**
     * Deletes a persistent or temporary hologram.
     *
     * @param hologram hologram instance
     * @return true if a hologram was removed
     */
    boolean deleteHologram(Hologram hologram);

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
     * Replaces the first page lines of a hologram.
     *
     * @param hologram hologram instance
     * @param lines replacement text lines
     */
    void updateLines(Hologram hologram, List<String> lines);

    /**
     * Replaces the first page with a single item line.
     *
     * @param id hologram id
     * @param itemContent item content to display
     */
    void updateItemLine(String id, String itemContent);

    /**
     * Replaces the first page with a single item line.
     *
     * @param hologram hologram instance
     * @param itemContent item content to display
     */
    void updateItemLine(Hologram hologram, String itemContent);

    /**
     * Appends a text line to the first page of a hologram.
     *
     * @param id hologram id
     * @param line text line to append
     */
    void addLine(String id, String line);

    /**
     * Appends a text line to the first page of a hologram.
     *
     * @param hologram hologram instance
     * @param line text line to append
     */
    void addLine(Hologram hologram, String line);

    /**
     * Appends text lines to the first page of a hologram.
     *
     * @param id hologram id
     * @param lines text lines to append
     */
    void addLines(String id, List<String> lines);

    /**
     * Appends text lines to the first page of a hologram.
     *
     * @param hologram hologram instance
     * @param lines text lines to append
     */
    void addLines(Hologram hologram, List<String> lines);

    /**
     * Appends a text line to the first page of a hologram.
     *
     * @param id hologram id
     * @param line text line to append
     */
    void addTextLine(String id, String line);

    /**
     * Appends a text line to the first page of a hologram.
     *
     * @param hologram hologram instance
     * @param line text line to append
     */
    void addTextLine(Hologram hologram, String line);

    /**
     * Appends text lines to the first page of a hologram.
     *
     * @param id hologram id
     * @param lines text lines to append
     */
    void addTextLines(String id, List<String> lines);

    /**
     * Appends text lines to the first page of a hologram.
     *
     * @param hologram hologram instance
     * @param lines text lines to append
     */
    void addTextLines(Hologram hologram, List<String> lines);

    /**
     * Appends an item line to the first page of a hologram.
     *
     * @param id hologram id
     * @param itemContent item content to append
     */
    void addItemLine(String id, String itemContent);

    /**
     * Appends an item line to the first page of a hologram.
     *
     * @param hologram hologram instance
     * @param itemContent item content to append
     */
    void addItemLine(Hologram hologram, String itemContent);

    /**
     * Appends a raw line to the first page of a hologram.
     *
     * @param id hologram id
     * @param line line to append
     */
    void addLine(String id, HologramLine line);

    /**
     * Appends a raw line to the first page of a hologram.
     *
     * @param hologram hologram instance
     * @param line line to append
     */
    void addLine(Hologram hologram, HologramLine line);

    /**
     * Appends raw lines to the first page of a hologram.
     *
     * @param id hologram id
     * @param lines lines to append
     */
    void addLines(String id, Collection<? extends HologramLine> lines);

    /**
     * Appends raw lines to the first page of a hologram.
     *
     * @param hologram hologram instance
     * @param lines lines to append
     */
    void addLines(Hologram hologram, Collection<? extends HologramLine> lines);

    /**
     * Creates an item line that can be passed to raw line APIs.
     *
     * @param itemContent item content to display
     * @return created item line
     */
    HologramLine createItemLine(String itemContent);

    /**
     * Returns the internally resolved height of the hologram default page.
     *
     * @param id hologram id
     * @return resolved layout height
     */
    double getHeight(String id);

    /**
     * Returns the internally resolved height of a hologram page.
     *
     * @param id hologram id
     * @param pageIndex zero-based page index
     * @return resolved layout height
     */
    double getHeight(String id, int pageIndex);

    /**
     * Returns the internally resolved height of the hologram default page.
     *
     * @param hologram hologram instance
     * @return resolved layout height
     */
    double getHeight(Hologram hologram);

    /**
     * Returns the internally resolved height of a hologram page.
     *
     * @param hologram hologram instance
     * @param pageIndex zero-based page index
     * @return resolved layout height
     */
    double getHeight(Hologram hologram, int pageIndex);

    /**
     * Returns the internally resolved height for a line in the context of a hologram.
     *
     * @param hologram hologram instance
     * @param line line to measure
     * @return resolved line height
     */
    double getLineHeight(Hologram hologram, HologramLine line);

    /**
     * Moves a hologram to another location.
     *
     * @param id hologram id
     * @param location target location with a loaded world
     */
    void teleportHologram(String id, Location location);

    /**
     * Moves a hologram to another location.
     *
     * @param hologram hologram instance
     * @param location target location with a loaded world
     */
    void teleportHologram(Hologram hologram, Location location);

    /**
     * Shows a hologram to all online players that can view it.
     *
     * @param id hologram id
     */
    void showHologram(String id);

    /**
     * Shows a hologram to all online players that can view it.
     *
     * @param hologram hologram instance
     */
    void showHologram(Hologram hologram);

    /**
     * Hides a hologram from all current viewers.
     *
     * @param id hologram id
     */
    void hideHologram(String id);

    /**
     * Hides a hologram from all current viewers.
     *
     * @param hologram hologram instance
     */
    void hideHologram(Hologram hologram);
}
