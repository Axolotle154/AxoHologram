package org.axostudio.axohologram.api;

import org.axostudio.axohologram.api.hologram.Hologram;
import org.axostudio.axohologram.api.hologram.HologramLine;
import org.axostudio.axohologram.api.hologram.HologramService;
import org.bukkit.Location;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@SuppressWarnings({"unused", "UnusedReturnValue"})
public interface AxoHologramAPI {

    /**
     * Access the modern HologramService.
     */
    HologramService holograms();

    // ==========================================
    // Backwards-compatible facade methods
    // ==========================================

    Hologram createHologram(String id, Location location, List<String> lines);
    Hologram createHologram(String id, Location location, List<String> lines, boolean saveToYaml);
    Hologram createHologram(String id, Location location, Collection<? extends HologramLine> lines);
    Hologram createHologram(String id, Location location, Collection<? extends HologramLine> lines, boolean saveToYaml);

    Hologram createItemHologram(String id, Location location, String itemContent);
    Hologram createItemHologram(String id, Location location, String itemContent, boolean saveToYaml);

    Hologram createBlockHologram(String id, Location location, String blockContent);
    Hologram createBlockHologram(String id, Location location, String blockContent, boolean saveToYaml);

    Hologram createTemporaryHologram(Location location, List<String> lines);
    Hologram createTemporaryHologram(String id, Location location, List<String> lines);
    Hologram createTemporaryHologram(Location location, List<String> lines, long durationTicks);
    Hologram createTemporaryHologram(String id, Location location, List<String> lines, long durationTicks);

    Hologram createTemporaryHologram(Location location, Collection<? extends HologramLine> lines);
    Hologram createTemporaryHologram(String id, Location location, Collection<? extends HologramLine> lines);
    Hologram createTemporaryHologram(Location location, Collection<? extends HologramLine> lines, long durationTicks);
    Hologram createTemporaryHologram(String id, Location location, Collection<? extends HologramLine> lines, long durationTicks);

    Hologram createTemporaryItemHologram(Location location, String itemContent);
    Hologram createTemporaryItemHologram(String id, Location location, String itemContent);
    Hologram createTemporaryItemHologram(Location location, String itemContent, long durationTicks);
    Hologram createTemporaryItemHologram(String id, Location location, String itemContent, long durationTicks);

    Hologram createTemporaryBlockHologram(Location location, String blockContent);
    Hologram createTemporaryBlockHologram(String id, Location location, String blockContent);
    Hologram createTemporaryBlockHologram(Location location, String blockContent, long durationTicks);
    Hologram createTemporaryBlockHologram(String id, Location location, String blockContent, long durationTicks);

    boolean deleteHologram(String id);
    boolean deleteHologram(Hologram hologram);

    default boolean enableHologram(String id) {
        return holograms().enable(id);
    }

    default void enableHologram(Hologram hologram) {
        if (hologram != null) holograms().enable(hologram);
    }

    default boolean disableHologram(String id) {
        return holograms().disable(id);
    }

    default void disableHologram(Hologram hologram) {
        if (hologram != null) holograms().disable(hologram);
    }

    boolean exists(String id);
    Optional<Hologram> getHologram(String id);
    Collection<Hologram> getHolograms();

    void updateLines(String id, List<String> lines);
    void updateLines(Hologram hologram, List<String> lines);
    void updateLines(String id, Collection<? extends HologramLine> lines);
    void updateLines(Hologram hologram, Collection<? extends HologramLine> lines);

    void updateItemLine(String id, String itemContent);
    void updateItemLine(Hologram hologram, String itemContent);
    void updateBlockLine(String id, String blockContent);
    void updateBlockLine(Hologram hologram, String blockContent);

    void addLine(String id, String line);
    void addLine(Hologram hologram, String line);
    void addLines(String id, List<String> lines);
    default void addLines(String id, String... lines) {
        addTextLines(id, Arrays.asList(lines));
    }
    void addLines(Hologram hologram, List<String> lines);
    default void addLines(Hologram hologram, String... lines) {
        addTextLines(hologram, Arrays.asList(lines));
    }

    void addTextLine(String id, String line);
    void addTextLine(Hologram hologram, String line);
    void addTextLines(String id, List<String> lines);
    default void addTextLines(String id, String... lines) {
        addTextLines(id, Arrays.asList(lines));
    }
    void addTextLines(Hologram hologram, List<String> lines);
    default void addTextLines(Hologram hologram, String... lines) {
        addTextLines(hologram, Arrays.asList(lines));
    }

    void addItemLine(String id, String itemContent);
    void addItemLine(Hologram hologram, String itemContent);
    void addBlockLine(String id, String blockContent);
    void addBlockLine(Hologram hologram, String blockContent);

    void addLine(String id, HologramLine line);
    void addLine(Hologram hologram, HologramLine line);
    void addLines(String id, Collection<? extends HologramLine> lines);
    default void addLines(String id, HologramLine... lines) {
        addLines(id, Arrays.asList(lines));
    }
    void addLines(Hologram hologram, Collection<? extends HologramLine> lines);
    default void addLines(Hologram hologram, HologramLine... lines) {
        addLines(hologram, Arrays.asList(lines));
    }

    HologramLine createItemLine(String itemContent);
    HologramLine createTextLine(String textContent);
    HologramLine createBlockLine(String blockContent);

    double getHeight(String id);
    double getHeight(String id, int pageIndex);
    double getHeight(Hologram hologram);
    double getHeight(Hologram hologram, int pageIndex);
    double getLineHeight(Hologram hologram, HologramLine line);

    void teleportHologram(String id, Location location);
    void teleportHologram(Hologram hologram, Location location);

    void showHologram(String id);
    void showHologram(Hologram hologram);
    void hideHologram(String id);
    void hideHologram(Hologram hologram);
}
