package org.axostudio.axohologram.importer;

import org.axostudio.axohologram.AxoHologram;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.logging.Level;

public final class GholoImporter implements HologramImporter {

    private final AxoHologram plugin;
    private final ImportManager importManager;
    private final GholoParser parser;
    private final HologramConverter converter;

    public GholoImporter(AxoHologram plugin, ImportManager importManager, GholoParser parser, HologramConverter converter) {
        this.plugin = plugin;
        this.importManager = importManager;
        this.parser = parser;
        this.converter = converter;
    }

    @Override
    public String id() {
        return "gholo";
    }

    @Override
    public String displayName() {
        return "GHolo";
    }

    @Override
    public boolean isAvailable() {
        return parser.isAvailable();
    }

    @Override
    public Collection<String> availableHolograms() {
        return new ArrayList<>(parser.parseAll().keySet());
    }

    @Override
    public ImportResult importHologram(String name) {
        ImportResult result = new ImportResult(displayName());
        if (!importManager.isEnabled()) {
            result.addMessage("[IMPORT] Importer is disabled in config.");
            return result;
        }
        if (!isAvailable()) {
            result.addMessage("[IMPORT] GHolo SQLite data was not found or no SQLite driver is available.");
            return result;
        }

        SourceHologram hologram = parser.parse(name);
        if (hologram == null) {
            result.markSkipped("[IMPORT] GHolo hologram \"" + name + "\" was not found.");
            return result;
        }

        importManager.backupBeforeImport();
        result.merge(importOne(hologram));
        return result;
    }

    @Override
    public ImportResult importAll() {
        if (!importManager.isEnabled()) {
            return importAllWithoutBackup();
        }
        importManager.backupBeforeImport();
        return importAllWithoutBackup();
    }

    @Override
    public ImportResult importAllWithoutBackup() {
        ImportResult result = new ImportResult(displayName());
        if (!importManager.isEnabled()) {
            result.addMessage("[IMPORT] Importer is disabled in config.");
            return result;
        }
        if (!isAvailable()) {
            result.addMessage("[IMPORT] GHolo SQLite data was not found or no SQLite driver is available.");
            return result;
        }

        Map<String, SourceHologram> holograms = parser.parseAll();
        if (holograms.isEmpty()) {
            result.addMessage("[IMPORT] GHolo has no readable holograms.");
            return result;
        }

        for (SourceHologram hologram : holograms.values()) {
            result.merge(importOne(hologram));
        }
        return result;
    }

    private ImportResult importOne(SourceHologram source) {
        ImportResult result = new ImportResult(displayName());
        try {
            plugin.getLogger().info("[IMPORT] Importing hologram \"" + source.name() + "\" from GHolo.");
            ConvertedHologram converted = converter.convert(source);
            plugin.getLogger().info("[IMPORT] Converted " + converted.lineCount() + " lines.");
            result.merge(importManager.importConverted(displayName(), converted));
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "[IMPORT] Failed to import GHolo hologram \"" + source.name() + "\".", exception);
            result.markFailed("[IMPORT] Failed \"" + source.name() + "\": " + exception.getMessage());
        }
        return result;
    }
}
