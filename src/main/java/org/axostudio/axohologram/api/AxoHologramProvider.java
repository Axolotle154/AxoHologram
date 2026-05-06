package org.axostudio.axohologram.api;

import org.axostudio.axohologram.AxoHologram;
import org.axostudio.axohologram.hologram.Hologram;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class AxoHologramProvider implements AxoHologramAPI {

    private static final String TEMPORARY_ID_PREFIX = "temporary_";

    private final AxoHologram plugin;

    public AxoHologramProvider(AxoHologram plugin) {
        this.plugin = plugin;
    }

    @Override
    public Hologram createHologram(String id, Location location, List<String> lines) {
        return createHologram(id, location, lines, true);
    }

    @Override
    public Hologram createHologram(String id, Location location, List<String> lines, boolean saveToYaml) {
        String normalizedId = validateId(id);
        validateCreateInput(normalizedId, location, lines);
        Hologram hologram = plugin.getHologramManager().createHologram(normalizedId, location, List.copyOf(lines), saveToYaml);
        if (hologram == null) {
            throw new IllegalStateException("Could not create hologram '" + normalizedId + "'.");
        }
        return hologram;
    }

    @Override
    public Hologram createTemporaryHologram(Location location, List<String> lines) {
        return createTemporaryHologram(generateTemporaryId(), location, lines);
    }

    @Override
    public Hologram createTemporaryHologram(String id, Location location, List<String> lines) {
        return createTemporaryHologram(id, location, lines, -1L);
    }

    @Override
    public Hologram createTemporaryHologram(Location location, List<String> lines, long durationTicks) {
        return createTemporaryHologram(generateTemporaryId(), location, lines, durationTicks);
    }

    @Override
    public Hologram createTemporaryHologram(String id, Location location, List<String> lines, long durationTicks) {
        String normalizedId = id == null || id.isBlank() ? generateTemporaryId() : validateId(id);
        validateCreateInput(normalizedId, location, lines);
        Hologram hologram = plugin.getHologramManager().createHologram(normalizedId, location, List.copyOf(lines), false);
        if (hologram == null) {
            throw new IllegalStateException("Could not create temporary hologram '" + normalizedId + "'.");
        }
        plugin.getHologramManager().scheduleTemporaryRemoval(normalizedId, durationTicks);
        return hologram;
    }

    @Override
    public boolean deleteHologram(String id) {
        return plugin.getHologramManager().deleteHologram(validateId(id));
    }

    @Override
    public boolean exists(String id) {
        return plugin.getHologramManager().getHologram(validateId(id)) != null;
    }

    @Override
    public Optional<Hologram> getHologram(String id) {
        return Optional.ofNullable(plugin.getHologramManager().getHologram(validateId(id)));
    }

    @Override
    public Collection<Hologram> getHolograms() {
        return plugin.getHologramManager().getAllHolograms();
    }

    @Override
    public void updateLines(String id, List<String> lines) {
        validateLines(lines);
        plugin.getHologramManager().setTextLines(requireHologram(id), List.copyOf(lines));
    }

    @Override
    public void teleportHologram(String id, Location location) {
        validateLocation(location);
        requireHologram(id).setLocation(location);
    }

    @Override
    public void showHologram(String id) {
        Hologram hologram = requireHologram(id);
        for (Player player : Bukkit.getOnlinePlayers()) {
            hologram.show(player);
        }
    }

    @Override
    public void hideHologram(String id) {
        Hologram hologram = requireHologram(id);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (hologram.isViewing(player)) {
                hologram.hide(player);
            }
        }
    }

    private Hologram requireHologram(String id) {
        String normalizedId = validateId(id);
        Hologram hologram = plugin.getHologramManager().getHologram(normalizedId);
        if (hologram == null) {
            throw new IllegalArgumentException("Hologram '" + normalizedId + "' does not exist.");
        }
        return hologram;
    }

    private void validateCreateInput(String id, Location location, List<String> lines) {
        validateLocation(location);
        validateLines(lines);
        if (plugin.getHologramManager().getHologram(id) != null) {
            throw new IllegalArgumentException("Hologram id '" + id + "' is already in use.");
        }
    }

    private String validateId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Hologram id cannot be null or blank.");
        }
        String normalizedId = id.trim();
        if (!plugin.getHologramManager().isValidHologramId(normalizedId)) {
            throw new IllegalArgumentException("Invalid hologram id '" + id + "'. Only letters, numbers, _ and - are allowed.");
        }
        return normalizedId;
    }

    private void validateLocation(Location location) {
        Objects.requireNonNull(location, "location");
        if (location.getWorld() == null) {
            throw new IllegalArgumentException("Hologram location must have a loaded world.");
        }
    }

    private void validateLines(List<String> lines) {
        Objects.requireNonNull(lines, "lines");
        for (String line : lines) {
            if (line == null) {
                throw new IllegalArgumentException("Hologram lines cannot contain null values.");
            }
        }
    }

    private String generateTemporaryId() {
        String id;
        do {
            id = TEMPORARY_ID_PREFIX + UUID.randomUUID();
        } while (plugin.getHologramManager().getHologram(id) != null);
        return id;
    }
}
