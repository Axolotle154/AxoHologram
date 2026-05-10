package org.axostudio.axohologram.api;

import org.axostudio.axohologram.AxoHologram;
import org.axostudio.axohologram.hologram.Hologram;
import org.axostudio.axohologram.hologram.line.HologramLine;
import org.axostudio.axohologram.hologram.line.impl.ItemLineImpl;
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
    public Hologram createItemHologram(String id, Location location, String itemContent) {
        return createItemHologram(id, location, itemContent, true);
    }

    @Override
    public Hologram createItemHologram(String id, Location location, String itemContent, boolean saveToYaml) {
        String normalizedId = validateId(id);
        validateCreateItemInput(normalizedId, location, itemContent);
        Hologram hologram = plugin.getHologramManager().createItemHologram(normalizedId, location, itemContent, saveToYaml);
        if (hologram == null) {
            throw new IllegalStateException("Could not create item hologram '" + normalizedId + "'.");
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
    public Hologram createTemporaryItemHologram(Location location, String itemContent) {
        return createTemporaryItemHologram(generateTemporaryId(), location, itemContent);
    }

    @Override
    public Hologram createTemporaryItemHologram(String id, Location location, String itemContent) {
        return createTemporaryItemHologram(id, location, itemContent, -1L);
    }

    @Override
    public Hologram createTemporaryItemHologram(Location location, String itemContent, long durationTicks) {
        return createTemporaryItemHologram(generateTemporaryId(), location, itemContent, durationTicks);
    }

    @Override
    public Hologram createTemporaryItemHologram(String id, Location location, String itemContent, long durationTicks) {
        String normalizedId = id == null || id.isBlank() ? generateTemporaryId() : validateId(id);
        validateCreateItemInput(normalizedId, location, itemContent);
        Hologram hologram = plugin.getHologramManager().createItemHologram(normalizedId, location, itemContent, false);
        if (hologram == null) {
            throw new IllegalStateException("Could not create temporary item hologram '" + normalizedId + "'.");
        }
        plugin.getHologramManager().scheduleTemporaryRemoval(normalizedId, durationTicks);
        return hologram;
    }

    @Override
    public boolean deleteHologram(String id) {
        return plugin.getHologramManager().deleteHologram(validateId(id));
    }

    @Override
    public boolean deleteHologram(Hologram hologram) {
        return plugin.getHologramManager().deleteHologram(requireHologram(hologram).getId());
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
    public void updateLines(Hologram hologram, List<String> lines) {
        validateLines(lines);
        plugin.getHologramManager().setTextLines(requireHologram(hologram), List.copyOf(lines));
    }

    @Override
    public void updateItemLine(String id, String itemContent) {
        validateItemContent(itemContent);
        plugin.getHologramManager().setItemLine(requireHologram(id), itemContent);
    }

    @Override
    public void updateItemLine(Hologram hologram, String itemContent) {
        validateItemContent(itemContent);
        plugin.getHologramManager().setItemLine(requireHologram(hologram), itemContent);
    }

    @Override
    public void addLine(String id, String line) {
        addTextLine(id, line);
    }

    @Override
    public void addLine(Hologram hologram, String line) {
        addTextLine(hologram, line);
    }

    @Override
    public void addLines(String id, List<String> lines) {
        addTextLines(id, lines);
    }

    @Override
    public void addLines(Hologram hologram, List<String> lines) {
        addTextLines(hologram, lines);
    }

    @Override
    public void addTextLine(String id, String line) {
        validateLine(line);
        plugin.getHologramManager().addTextLine(requireHologram(id), line);
    }

    @Override
    public void addTextLine(Hologram hologram, String line) {
        validateLine(line);
        plugin.getHologramManager().addTextLine(requireHologram(hologram), line);
    }

    @Override
    public void addTextLines(String id, List<String> lines) {
        validateLines(lines);
        plugin.getHologramManager().addTextLines(requireHologram(id), List.copyOf(lines));
    }

    @Override
    public void addTextLines(Hologram hologram, List<String> lines) {
        validateLines(lines);
        plugin.getHologramManager().addTextLines(requireHologram(hologram), List.copyOf(lines));
    }

    @Override
    public void addItemLine(String id, String itemContent) {
        validateItemContent(itemContent);
        plugin.getHologramManager().addItemLine(requireHologram(id), itemContent);
    }

    @Override
    public void addItemLine(Hologram hologram, String itemContent) {
        validateItemContent(itemContent);
        plugin.getHologramManager().addItemLine(requireHologram(hologram), itemContent);
    }

    @Override
    public void addLine(String id, HologramLine line) {
        validateHologramLine(line);
        plugin.getHologramManager().addLine(requireHologram(id), line);
    }

    @Override
    public void addLine(Hologram hologram, HologramLine line) {
        validateHologramLine(line);
        plugin.getHologramManager().addLine(requireHologram(hologram), line);
    }

    @Override
    public void addLines(String id, Collection<? extends HologramLine> lines) {
        validateHologramLines(lines);
        plugin.getHologramManager().addLines(requireHologram(id), List.copyOf(lines));
    }

    @Override
    public void addLines(Hologram hologram, Collection<? extends HologramLine> lines) {
        validateHologramLines(lines);
        plugin.getHologramManager().addLines(requireHologram(hologram), List.copyOf(lines));
    }

    @Override
    public HologramLine createItemLine(String itemContent) {
        validateItemContent(itemContent);
        return new ItemLineImpl(itemContent, plugin);
    }

    @Override
    public double getHeight(String id) {
        return plugin.getHologramManager().getHeight(requireHologram(id));
    }

    @Override
    public double getHeight(String id, int pageIndex) {
        return plugin.getHologramManager().getHeight(requireHologram(id), pageIndex);
    }

    @Override
    public double getHeight(Hologram hologram) {
        return plugin.getHologramManager().getHeight(requireHologram(hologram));
    }

    @Override
    public double getHeight(Hologram hologram, int pageIndex) {
        return plugin.getHologramManager().getHeight(requireHologram(hologram), pageIndex);
    }

    @Override
    public double getLineHeight(Hologram hologram, HologramLine line) {
        validateHologramLine(line);
        return plugin.getHologramManager().resolveLineHeight(requireHologram(hologram), line);
    }

    @Override
    public void teleportHologram(String id, Location location) {
        validateLocation(location);
        requireHologram(id).setLocation(location);
    }

    @Override
    public void teleportHologram(Hologram hologram, Location location) {
        validateLocation(location);
        requireHologram(hologram).setLocation(location);
    }

    @Override
    public void showHologram(String id) {
        showHologram(requireHologram(id));
    }

    @Override
    public void showHologram(Hologram hologram) {
        Hologram registeredHologram = requireHologram(hologram);
        for (Player player : Bukkit.getOnlinePlayers()) {
            registeredHologram.show(player);
        }
    }

    @Override
    public void hideHologram(String id) {
        hideHologram(requireHologram(id));
    }

    @Override
    public void hideHologram(Hologram hologram) {
        Hologram registeredHologram = requireHologram(hologram);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (registeredHologram.isViewing(player)) {
                registeredHologram.hide(player);
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

    private Hologram requireHologram(Hologram hologram) {
        Objects.requireNonNull(hologram, "hologram");
        String normalizedId = validateId(hologram.getId());
        Hologram registeredHologram = plugin.getHologramManager().getHologram(normalizedId);
        if (registeredHologram == null) {
            throw new IllegalArgumentException("Hologram '" + normalizedId + "' does not exist.");
        }
        return registeredHologram;
    }

    private void validateCreateInput(String id, Location location, List<String> lines) {
        validateLocation(location);
        validateLines(lines);
        if (plugin.getHologramManager().getHologram(id) != null) {
            throw new IllegalArgumentException("Hologram id '" + id + "' is already in use.");
        }
    }

    private void validateCreateItemInput(String id, Location location, String itemContent) {
        validateLocation(location);
        validateItemContent(itemContent);
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

    private void validateLines(Collection<String> lines) {
        Objects.requireNonNull(lines, "lines");
        for (String line : lines) {
            validateLine(line);
        }
    }

    private void validateLine(String line) {
        if (line == null) {
            throw new IllegalArgumentException("Hologram lines cannot contain null values.");
        }
    }

    private void validateItemContent(String itemContent) {
        if (itemContent == null || itemContent.isBlank()) {
            throw new IllegalArgumentException("Item line content cannot be null or blank.");
        }
        new ItemLineImpl(itemContent, plugin);
    }

    private void validateHologramLine(HologramLine line) {
        Objects.requireNonNull(line, "line");
    }

    private void validateHologramLines(Collection<? extends HologramLine> lines) {
        Objects.requireNonNull(lines, "lines");
        for (HologramLine line : lines) {
            validateHologramLine(line);
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
