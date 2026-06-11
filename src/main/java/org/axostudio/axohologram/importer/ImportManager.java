package org.axostudio.axohologram.importer;

import org.axostudio.axohologram.AxoHologram;
import org.axostudio.axohologram.hologram.Hologram;
import org.axostudio.axohologram.hologram.HologramManager;
import org.axostudio.axohologram.hologram.impl.AxoHologramImpl;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

public final class ImportManager {

    private static final DateTimeFormatter BACKUP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final AxoHologram plugin;
    private final HologramConverter converter;
    private final Map<String, HologramImporter> importers = new LinkedHashMap<>();

    public ImportManager(AxoHologram plugin) {
        this.plugin = plugin;
        this.converter = new HologramConverter(new LineConverter());
        registerImporter(new FancyHologramsImporter(plugin, this, new FancyHologramsParser(plugin), converter));
        registerImporter(new DecentHologramsImporter(plugin, this, new DecentHologramsParser(plugin), converter));
        registerImporter(new GholoImporter(plugin, this, new GholoParser(plugin), converter));
    }

    public Collection<HologramImporter> importers() {
        return importers.values();
    }

    public HologramImporter importer(String id) {
        return id == null ? null : importers.get(id.toLowerCase(Locale.ROOT));
    }

    public ImportResult importAuto() {
        ImportResult result = new ImportResult("auto");
        if (!isEnabled()) {
            result.addMessage("[IMPORT] Importer is disabled in config.");
            return result;
        }
        if (!plugin.getConfigManager().getConfig().getBoolean("importer.auto-detect-installed-plugins", true)) {
            result.addMessage("[IMPORT] Automatic importer detection is disabled in config.");
            return result;
        }

        boolean backedUp = false;
        for (HologramImporter importer : importers.values()) {
            if (!importer.isAvailable()) {
                result.addMessage("[IMPORT] " + importer.displayName() + " not found. Skipping.");
                continue;
            }
            if (!backedUp) {
                backupBeforeImport();
                backedUp = true;
            }
            result.merge(importer.importAllWithoutBackup());
        }
        return result;
    }

    ImportResult importConverted(String source, ConvertedHologram converted) {
        ImportResult result = new ImportResult(source);
        if (!isEnabled()) {
            result.markSkipped("[IMPORT] Importer is disabled in config.");
            return result;
        }
        if (converted == null) {
            result.markFailed("[IMPORT] Invalid converted hologram.");
            return result;
        }

        String targetId = resolveTargetId(converted.id());
        try {
            Hologram hologram = AxoHologramImpl.deserialize(targetId, converted.toConfiguration(), plugin);
            if (hologram == null) {
                result.markFailed("[IMPORT] Failed to convert hologram \"" + converted.sourceName() + "\".");
                return result;
            }

            boolean overwrite = plugin.getConfigManager().getConfig().getBoolean("importer.overwrite-existing", false);
            if (!plugin.getHologramManager().registerImportedHologram(hologram, overwrite)) {
                result.markSkipped("[IMPORT] Skipped \"" + converted.sourceName() + "\" because target id exists: " + targetId);
                return result;
            }

            plugin.getLogger().info("[IMPORT] Imported successfully: " + converted.sourceName() + " -> " + targetId);
            result.markImported("[IMPORT] Imported \"" + converted.sourceName() + "\" as \"" + targetId + "\" (" + converted.lineCount() + " lines).");
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "[IMPORT] Failed to import hologram \"" + converted.sourceName() + "\".", exception);
            result.markFailed("[IMPORT] Failed \"" + converted.sourceName() + "\": " + exception.getMessage());
        }
        return result;
    }

    void backupBeforeImport() {
        if (!plugin.getConfigManager().getConfig().getBoolean("importer.backup-before-import", true)) {
            return;
        }

        File hologramFolder = new File(plugin.getDataFolder(), "holograms");
        File[] files = hologramFolder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null || files.length == 0) {
            return;
        }

        File backupFolder = new File(plugin.getDataFolder(), "backups/import-" + LocalDateTime.now().format(BACKUP_FORMAT));
        if (!backupFolder.exists() && !backupFolder.mkdirs()) {
            plugin.getLogger().warning("[IMPORT] Could not create backup folder: " + backupFolder.getAbsolutePath());
            return;
        }

        for (File file : files) {
            try {
                Files.copy(file.toPath(), new File(backupFolder, file.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException exception) {
                plugin.getLogger().log(Level.WARNING, "[IMPORT] Could not backup hologram file " + file.getName(), exception);
            }
        }
    }

    String sanitizeId(String rawName) {
        String normalized = rawName == null || rawName.isBlank() ? "imported_hologram" : rawName.trim();
        normalized = normalized.replaceAll("[^a-zA-Z0-9_-]", "_");
        normalized = normalized.replaceAll("_+", "_");
        if (normalized.isBlank() || !HologramManager.VALID_HOLOGRAM_ID.matcher(normalized).matches()) {
            normalized = "imported_hologram";
        }
        return normalized;
    }

    private String resolveTargetId(String rawName) {
        String baseId = sanitizeId(rawName);
        boolean overwrite = plugin.getConfigManager().getConfig().getBoolean("importer.overwrite-existing", false);
        if (overwrite || plugin.getHologramManager().getHologram(baseId) == null) {
            return baseId;
        }

        String candidate = baseId + "_imported";
        int suffix = 2;
        while (plugin.getHologramManager().getHologram(candidate) != null) {
            candidate = baseId + "_imported_" + suffix++;
        }
        return candidate;
    }

    boolean isEnabled() {
        return plugin.getConfigManager().getConfig().getBoolean("importer.enabled", true);
    }

    private void registerImporter(HologramImporter importer) {
        importers.put(importer.id(), importer);
    }
}
