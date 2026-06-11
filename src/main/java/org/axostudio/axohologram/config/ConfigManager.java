package org.axostudio.axohologram.config;

import org.axostudio.axohologram.AxoHologram;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

public class ConfigManager {

    private final AxoHologram plugin;
    private FileConfiguration config;
    private FileConfiguration messages;
    private FileConfiguration media;
    private volatile VisibilityRuntimeConfig visibilityRuntimeConfig = VisibilityRuntimeConfig.from(null);
    private volatile DynamicRefreshRuntimeConfig dynamicRefreshRuntimeConfig = DynamicRefreshRuntimeConfig.from(null);

    private final File configFile;
    private final File messagesFile;
    private final File mediaFile;

    public ConfigManager(AxoHologram plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "config.yml");
        this.messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        this.mediaFile = new File(plugin.getDataFolder(), "media.yml");
    }

    public void loadAllConfigs() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("Could not create plugin data folder: " + plugin.getDataFolder().getAbsolutePath());
        }

        saveDefaultIfMissing("config.yml", configFile);
        saveDefaultIfMissing("messages.yml", messagesFile);
        saveDefaultIfMissing("media.yml", mediaFile);

        reloadConfig();
        reloadMessages();
        reloadMedia();
    }

    private void saveDefaultIfMissing(String resourcePath, File destination) {
        if (!destination.exists()) {
            plugin.saveResource(resourcePath, false);
        }
    }

    private FileConfiguration loadConfiguration(File file, String resourceName) {
        FileConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        try (InputStream stream = plugin.getResource(resourceName)) {
            if (stream != null) {
                configuration.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8)));
                configuration.options().copyDefaults(true);
            }
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not load defaults for " + resourceName, exception);
        }
        return configuration;
    }

    public FileConfiguration getConfig() {
        if (config == null) {
            reloadConfig();
        }
        return config;
    }

    public FileConfiguration getMessages() {
        if (messages == null) {
            reloadMessages();
        }
        return messages;
    }

    public FileConfiguration getMedia() {
        if (media == null) {
            reloadMedia();
        }
        return media;
    }

    public void reloadConfig() {
        config = loadConfiguration(configFile, "config.yml");
        visibilityRuntimeConfig = VisibilityRuntimeConfig.from(config);
        dynamicRefreshRuntimeConfig = DynamicRefreshRuntimeConfig.from(config);
    }

    public void reloadMessages() {
        messages = loadConfiguration(messagesFile, "messages.yml");
    }

    public void reloadMedia() {
        media = loadConfiguration(mediaFile, "media.yml");
    }

    public void saveConfig() {
        saveYaml(config, configFile, "config");
    }

    public void saveMessages() {
        saveYaml(messages, messagesFile, "messages");
    }

    public void saveMedia() {
        saveYaml(media, mediaFile, "media");
    }

    public VisibilityRuntimeConfig getVisibilityRuntimeConfig() {
        return visibilityRuntimeConfig;
    }

    public DynamicRefreshRuntimeConfig getDynamicRefreshRuntimeConfig() {
        return dynamicRefreshRuntimeConfig;
    }

    private void saveYaml(FileConfiguration configuration, File file, String name) {
        if (configuration == null || !file.exists()) {
            return;
        }
        try {
            configuration.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not save " + name + " to " + file, ex);
        }
    }
}
