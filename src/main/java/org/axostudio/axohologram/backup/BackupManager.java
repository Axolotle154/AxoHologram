package org.axostudio.axohologram.backup;

import org.axostudio.axohologram.AxoHologram;
import org.axostudio.axohologram.media.MediaCacheManager;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class BackupManager {

    private final AxoHologram plugin;
    private final MediaCacheManager cacheManager;

    public BackupManager(AxoHologram plugin, MediaCacheManager cacheManager) {
        this.plugin = plugin;
        this.cacheManager = cacheManager;
    }

    public File createBackup() throws IOException {
        cacheManager.ensureFolders();
        String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date());
        File backupFile = new File(cacheManager.backupsFolder(), "backup-" + timestamp + ".zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(backupFile.toPath()))) {
            addIfExists(output, new File(plugin.getDataFolder(), "config.yml"), "config.yml");
            addIfExists(output, new File(plugin.getDataFolder(), "messages.yml"), "messages.yml");
            addIfExists(output, new File(plugin.getDataFolder(), "animations.yml"), "animations.yml");
            addIfExists(output, new File(plugin.getDataFolder(), "media.yml"), "media.yml");
            addDirectory(output, new File(plugin.getDataFolder(), "holograms"), "holograms/");
        }
        return backupFile;
    }

    public boolean restoreBackup(String fileName) {
        // Restore is intentionally staged behind this API to avoid overwriting live data without a safer migration flow.
        return fileName != null && !fileName.isBlank() && new File(cacheManager.backupsFolder(), fileName).isFile();
    }

    private void addDirectory(ZipOutputStream output, File directory, String entryPrefix) throws IOException {
        if (!directory.isDirectory()) {
            return;
        }

        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            String entryName = entryPrefix + file.getName();
            if (file.isDirectory()) {
                addDirectory(output, file, entryName + "/");
            } else {
                addIfExists(output, file, entryName);
            }
        }
    }

    private void addIfExists(ZipOutputStream output, File file, String entryName) throws IOException {
        if (!file.isFile()) {
            return;
        }

        output.putNextEntry(new ZipEntry(entryName));
        Files.copy(file.toPath(), output);
        output.closeEntry();
    }
}
