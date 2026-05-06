package org.axostudio.axohologram.animation;

import org.axostudio.axohologram.AxoHologram;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;

public final class AnimationConfigManager {

    private static final String RESOURCE_NAME = "animations.yml";

    private final AxoHologram plugin;
    private final File animationsFile;
    private final Map<String, TextAnimation> textAnimations = new HashMap<>();
    private final Map<String, DisplayAnimation> displayAnimations = new HashMap<>();
    private final Map<String, String> hologramDisplayAnimations = new HashMap<>();
    private final Map<String, AnimationPreset> presets = new HashMap<>();

    private FileConfiguration animationsConfig;
    private AnimationSettings settings = AnimationSettings.defaults();

    public AnimationConfigManager(AxoHologram plugin) {
        this.plugin = plugin;
        this.animationsFile = new File(plugin.getDataFolder(), RESOURCE_NAME);
    }

    public void loadAnimations() {
        ensureAnimationsFile();
        animationsConfig = loadConfiguration();
        textAnimations.clear();
        displayAnimations.clear();
        hologramDisplayAnimations.clear();
        presets.clear();

        ConfigurationSection root = animationsConfig.getConfigurationSection("animations");
        if (root == null) {
            plugin.getLogger().warning("animations.yml has no 'animations' section. Animation system will use defaults.");
            settings = AnimationSettings.defaults();
            return;
        }

        settings = readSettings(root.getConfigurationSection("settings"));
        loadTextAnimations(root.getConfigurationSection("text"));
        loadCustomAnimations(root.getConfigurationSection("custom"));
        loadDisplayAnimations(root.getConfigurationSection("display"));
        loadPresets(root.getConfigurationSection("presets"));
        loadHologramAssignments(animationsConfig.getConfigurationSection("holograms"));
        plugin.getLogger().info("Loaded " + textAnimations.size() + " text animations, "
                + displayAnimations.size() + " display animations and " + presets.size() + " animation presets.");
    }

    public void reloadAnimations() {
        loadAnimations();
    }

    public AnimationSettings getSettings() {
        return settings;
    }

    public Optional<TextAnimation> getTextAnimation(String name) {
        return Optional.ofNullable(textAnimations.get(normalizeName(name)));
    }

    public Optional<DisplayAnimation> getDisplayAnimation(String name) {
        return Optional.ofNullable(displayAnimations.get(normalizeName(name)));
    }

    public boolean hasDisplayAnimation(String name) {
        return displayAnimations.containsKey(normalizeName(name));
    }

    public Optional<String> getDisplayAnimationForHologram(String hologramId) {
        return Optional.ofNullable(hologramDisplayAnimations.get(normalizeName(hologramId)));
    }

    public Map<String, TextAnimation> getTextAnimations() {
        return Collections.unmodifiableMap(textAnimations);
    }

    public Map<String, DisplayAnimation> getDisplayAnimations() {
        return Collections.unmodifiableMap(displayAnimations);
    }

    public Map<String, AnimationPreset> getPresets() {
        return Collections.unmodifiableMap(presets);
    }

    private void ensureAnimationsFile() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("Could not create plugin data folder: " + plugin.getDataFolder().getAbsolutePath());
        }

        if (!animationsFile.exists()) {
            plugin.saveResource(RESOURCE_NAME, false);
        }
    }

    private FileConfiguration loadConfiguration() {
        FileConfiguration configuration = YamlConfiguration.loadConfiguration(animationsFile);
        try (InputStream stream = plugin.getResource(RESOURCE_NAME)) {
            if (stream != null) {
                configuration.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8)));
                configuration.options().copyDefaults(true);
                configuration.save(animationsFile);
            }
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not load animation defaults.", exception);
        }
        return configuration;
    }

    private AnimationSettings readSettings(ConfigurationSection section) {
        if (section == null) {
            return AnimationSettings.defaults();
        }

        return new AnimationSettings(
                section.getBoolean("enabled", true),
                section.getLong("tick-rate", 2L),
                section.getBoolean("cache-frames", true),
                section.getBoolean("async-text-processing", true),
                section.getBoolean("allow-placeholders-inside-animations", false),
                section.getBoolean("reduce-quality-on-low-tps", true),
                section.getBoolean("display-interpolation", true)
        );
    }

    private void loadTextAnimations(ConfigurationSection section) {
        if (section == null) {
            return;
        }

        for (String name : section.getKeys(false)) {
            ConfigurationSection animationSection = section.getConfigurationSection(name);
            if (animationSection == null) {
                warnInvalid("text", name, "missing configuration section");
                continue;
            }

            try {
                TextAnimation animation = parseTextAnimation(name, animationSection);
                textAnimations.put(normalizeName(name), animation);
            } catch (IllegalArgumentException exception) {
                warnInvalid("text", name, exception.getMessage());
            }
        }
    }

    private void loadCustomAnimations(ConfigurationSection section) {
        if (section == null) {
            return;
        }

        for (String name : section.getKeys(false)) {
            ConfigurationSection animationSection = section.getConfigurationSection(name);
            if (animationSection == null) {
                warnInvalid("custom", name, "missing configuration section");
                continue;
            }

            try {
                String type = normalizeType(animationSection.getString("type", ""));
                if (!type.equals("frame-animation")) {
                    throw new IllegalArgumentException("unsupported custom animation type '" + type + "'");
                }

                List<String> frames = animationSection.getStringList("frames");
                if (frames.isEmpty()) {
                    throw new IllegalArgumentException("frame-animation requires at least one frame");
                }

                textAnimations.put(normalizeName(name), new FrameTextAnimation(
                        name,
                        frames,
                        animationSection.getInt("frame-duration", 1),
                        animationSection.getBoolean("loop", true)
                ));
            } catch (IllegalArgumentException exception) {
                warnInvalid("custom", name, exception.getMessage());
            }
        }
    }

    private TextAnimation parseTextAnimation(String name, ConfigurationSection section) {
        String type = normalizeType(section.getString("type", ""));
        if (!List.of("rainbow", "pulse", "matrix", "wave").contains(type)) {
            throw new IllegalArgumentException("unsupported text animation type '" + type + "'");
        }

        return new ConfiguredTextAnimation(
                name,
                type,
                section.getInt("speed", 1),
                section.getStringList("colors"),
                section.getString("color", "&f"),
                section.getString("color1", "&f"),
                section.getString("color2", "&b")
        );
    }

    private void loadDisplayAnimations(ConfigurationSection section) {
        if (section == null) {
            return;
        }

        for (String name : section.getKeys(false)) {
            ConfigurationSection animationSection = section.getConfigurationSection(name);
            if (animationSection == null) {
                warnInvalid("display", name, "missing configuration section");
                continue;
            }

            try {
                DisplayAnimation animation = parseDisplayAnimation(name, animationSection);
                displayAnimations.put(normalizeName(name), animation);
            } catch (IllegalArgumentException exception) {
                warnInvalid("display", name, exception.getMessage());
            }
        }
    }

    private void loadHologramAssignments(ConfigurationSection section) {
        if (section == null) {
            return;
        }

        for (String hologramId : section.getKeys(false)) {
            ConfigurationSection hologramSection = section.getConfigurationSection(hologramId);
            if (hologramSection == null) {
                warnInvalid("hologram", hologramId, "missing configuration section");
                continue;
            }

            String displayAnimation = hologramSection.getString("display-animation");
            if (displayAnimation == null || displayAnimation.isBlank()) {
                continue;
            }

            String normalizedAnimation = normalizeName(displayAnimation);
            if (!displayAnimations.containsKey(normalizedAnimation)) {
                warnInvalid("hologram", hologramId, "display animation '" + displayAnimation + "' is not registered");
                continue;
            }

            hologramDisplayAnimations.put(normalizeName(hologramId), normalizedAnimation);
        }
    }

    private void loadPresets(ConfigurationSection section) {
        if (section == null) {
            return;
        }

        for (String name : section.getKeys(false)) {
            ConfigurationSection presetSection = section.getConfigurationSection(name);
            if (presetSection == null) {
                warnInvalid("preset", name, "missing configuration section");
                continue;
            }

            String textAnimation = presetSection.getString("text-animation");
            String displayAnimation = presetSection.getString("display-animation");
            if ((textAnimation == null || textAnimation.isBlank()) && (displayAnimation == null || displayAnimation.isBlank())) {
                warnInvalid("preset", name, "preset must define text-animation or display-animation");
                continue;
            }

            if (textAnimation != null && !textAnimation.isBlank() && !textAnimations.containsKey(normalizeName(textAnimation))) {
                warnInvalid("preset", name, "text animation '" + textAnimation + "' is not registered");
                continue;
            }
            if (displayAnimation != null && !displayAnimation.isBlank() && !displayAnimations.containsKey(normalizeName(displayAnimation))) {
                warnInvalid("preset", name, "display animation '" + displayAnimation + "' is not registered");
                continue;
            }

            presets.put(normalizeName(name), new AnimationPreset(name, normalizeName(textAnimation), normalizeName(displayAnimation)));
        }
    }

    private DisplayAnimation parseDisplayAnimation(String name, ConfigurationSection section) {
        String type = normalizeType(section.getString("type", ""));
        if (!List.of("float", "spin", "cinematic-idle", "orbit").contains(type)) {
            throw new IllegalArgumentException("unsupported display animation type '" + type + "'");
        }

        return new ConfiguredDisplayAnimation(
                name,
                type,
                readDouble(section, "height", "float-height", 0.0D),
                section.getDouble("radius", 0.0D),
                section.getDouble("speed", 1.0D),
                section.getDouble("rotation-speed", section.getDouble("speed", 1.0D)),
                (float) section.getDouble("scale-min", 1.0D),
                (float) section.getDouble("scale-max", 1.0D),
                section.getString("axis", "y")
        );
    }

    private double readDouble(ConfigurationSection section, String primary, String fallback, double defaultValue) {
        if (section.contains(primary)) {
            return section.getDouble(primary, defaultValue);
        }
        return section.getDouble(fallback, defaultValue);
    }

    private void warnInvalid(String category, String name, String reason) {
        plugin.getLogger().warning("Ignoring invalid " + category + " animation '" + name + "' in animations.yml: " + reason + ".");
    }

    private String normalizeType(String type) {
        return type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeName(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }
}
