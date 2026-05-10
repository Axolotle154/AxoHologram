package org.axostudio.axohologram.importer;

import java.util.Collection;

public interface HologramImporter {

    String id();

    String displayName();

    boolean isAvailable();

    Collection<String> availableHolograms();

    ImportResult importHologram(String name);

    ImportResult importAll();

    default ImportResult importAllWithoutBackup() {
        return importAll();
    }
}
