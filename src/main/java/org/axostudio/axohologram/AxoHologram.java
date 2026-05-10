package org.axostudio.axohologram;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.axostudio.axohologram.animation.AnimationConfigManager;
import org.axostudio.axohologram.animation.AnimationManager;
import org.axostudio.axohologram.api.AxoHologramAPI;
import org.axostudio.axohologram.api.AxoHologramProvider;
import org.axostudio.axohologram.command.HologramCommand;
import org.axostudio.axohologram.config.ConfigManager;
import org.axostudio.axohologram.hologram.HologramManager;
import org.axostudio.axohologram.importer.ImportManager;
import org.axostudio.axohologram.integration.fancynpcs.FancyNpcHook;
import org.axostudio.axohologram.integration.MiniPlaceholdersIntegration;
import org.axostudio.axohologram.integration.npc.NpcLinkService;
import org.axostudio.axohologram.listener.HologramInteractionListener;
import org.axostudio.axohologram.integration.PlaceholderAPIIntegration;
import org.axostudio.axohologram.listener.PlayerListener;
import org.axostudio.axohologram.util.SchedulerUtil;
import org.axostudio.axohologram.util.UpdateChecker;
import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class AxoHologram extends JavaPlugin {

    private static final TextColor BRAND_PURPLE = TextColor.color(157, 0, 255);
    private static AxoHologram instance;
    private ConfigManager configManager;
    private HologramManager hologramManager;
    private NpcLinkService npcLinkService;
    private PlaceholderAPIIntegration placeholderApiIntegration;
    private SchedulerUtil schedulerUtil;
    private UpdateChecker updateChecker;
    private AnimationConfigManager animationConfigManager;
    private AnimationManager animationManager;
    private ImportManager importManager;
    private AxoHologramAPI api;

    @Override
    public void onEnable() {
        instance = this;
        logStartupBanner();

        this.configManager = new ConfigManager(this);
        configManager.loadAllConfigs();

        this.schedulerUtil = new SchedulerUtil(this);
        this.animationConfigManager = new AnimationConfigManager(this);
        animationConfigManager.loadAnimations();
        this.animationManager = new AnimationManager(this, animationConfigManager);
        this.api = new AxoHologramProvider(this);
        getServer().getServicesManager().register(AxoHologramAPI.class, api, this, ServicePriority.Normal);
        this.hologramManager = new HologramManager(this);
        this.importManager = new ImportManager(this);
        hologramManager.loadHolograms();
        animationManager.start();
        this.updateChecker = new UpdateChecker(this);

        HologramCommand hologramCommand = new HologramCommand(this);
        registerCommand(
                "axohologram",
                "Main command for AxoHologram.",
                List.of("aholo", "axoholo"),
                hologramCommand
        );
        registerCommand(
                "holograma",
                "Administrative hologram command.",
                List.of("holo"),
                hologramCommand
        );

        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new HologramInteractionListener(this), this);

        setupIntegrations();
        checkForUpdates();
        getLogger().info("AxoHologram has been enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("AxoHologram is disabling...");

        getServer().getServicesManager().unregisterAll(this);
        if (animationManager != null) {
            animationManager.stop();
        }
        shutdownIntegrations();
        if (hologramManager != null) {
            hologramManager.shutdown();
        }

        instance = null;
        getLogger().info("AxoHologram has been disabled!");
    }

    private void setupIntegrations() {
        setupPlaceholderApiIntegration();
        setupMiniPlaceholdersIntegration();
        setupFancyNpcIntegration();
    }

    private void setupPlaceholderApiIntegration() {
        stopPlaceholderApiIntegration();

        if (!configManager.getConfig().getBoolean("integrations.placeholderapi", true)) {
            getLogger().info("PlaceholderAPI integration disabled in config.");
            return;
        }

        if (!getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            getLogger().info("PlaceholderAPI not found. PlaceholderAPI placeholders will not be parsed.");
            return;
        }

        placeholderApiIntegration = new PlaceholderAPIIntegration(this);
        if (placeholderApiIntegration.register()) {
            getLogger().info("PlaceholderAPI found! Enabling integration.");
            return;
        }

        placeholderApiIntegration = null;
        getLogger().warning("PlaceholderAPI is enabled but its expansion could not be registered.");
    }

    private void setupMiniPlaceholdersIntegration() {
        if (!configManager.getConfig().getBoolean("integrations.miniplaceholders", true)) {
            getLogger().info("MiniPlaceholders integration disabled in config.");
            return;
        }

        if (!getServer().getPluginManager().isPluginEnabled("MiniPlaceholders")) {
            getLogger().info("MiniPlaceholders not found. MiniPlaceholders will not be parsed.");
            return;
        }

        if (MiniPlaceholdersIntegration.register()) {
            getLogger().info("MiniPlaceholders found! Enabling integration.");
            return;
        }

        getLogger().warning("MiniPlaceholders is enabled but its API could not be initialized.");
    }

    private void setupFancyNpcIntegration() {
        stopNpcIntegration();

        if (!configManager.getConfig().getBoolean("integrations.fancynpcs", true)) {
            getLogger().info("FancyNpcs integration disabled in config.");
            return;
        }

        boolean fancyNpcsEnabled = Bukkit.getPluginManager().isPluginEnabled("FancyNpcs");
        if (!fancyNpcsEnabled) {
            getLogger().info("FancyNpcs not found. NPC-linked holograms will stay static.");
            return;
        }

        FancyNpcHook hook = new FancyNpcHook();
        if (!hook.isAvailable()) {
            getLogger().warning("FancyNpcs is enabled but its API is not available. Skipping NPC integration.");
            return;
        }

        npcLinkService = new NpcLinkService(this, hook);
        npcLinkService.start();
        getLogger().info("FancyNpcs found! Enabling NPC link integration.");
    }

    private void stopNpcIntegration() {
        if (npcLinkService != null) {
            npcLinkService.stop();
            npcLinkService = null;
        }
    }

    private void stopPlaceholderApiIntegration() {
        if (placeholderApiIntegration != null) {
            placeholderApiIntegration.unregister();
            placeholderApiIntegration = null;
        }
    }

    private void shutdownIntegrations() {
        stopNpcIntegration();
        stopPlaceholderApiIntegration();
    }

    public void reloadPluginState() {
        shutdownIntegrations();
        configManager.loadAllConfigs();
        animationManager.reloadAnimations();
        hologramManager.reload();
        setupIntegrations();
    }

    public static AxoHologram getInstance() {
        return instance;
    }

    public static AxoHologramAPI getAPI() {
        return instance == null ? null : instance.api;
    }

    public AxoHologramAPI getApi() {
        return api;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public HologramManager getHologramManager() {
        return hologramManager;
    }

    public NpcLinkService getNpcLinkService() {
        return npcLinkService;
    }

    public SchedulerUtil getSchedulerUtil() {
        return schedulerUtil;
    }

    public AnimationConfigManager getAnimationConfigManager() {
        return animationConfigManager;
    }

    public AnimationManager getAnimationManager() {
        return animationManager;
    }

    public ImportManager getImportManager() {
        return importManager;
    }

    private void checkForUpdates() {
        if (!configManager.getConfig().getBoolean("general.check-updates", true)) {
            getLogger().info("Update checker disabled in config.");
            return;
        }

        updateChecker.getVersion(latestVersion -> {
            String currentVersion = getPluginMeta().getVersion();
            if (currentVersion.equalsIgnoreCase(latestVersion)) {
                getLogger().info("You are running the latest version: " + currentVersion);
            } else {
                getLogger().warning("A new AxoHologram version is available: " + latestVersion + " (current: " + currentVersion + ").");
                getLogger().warning("Download: https://www.spigotmc.org/resources/134707");
            }
        });
    }

    private void logStartupBanner() {
        String version = getPluginMeta().getVersion();
        String banner = """
                 _____                   ___ ___        .__
                /  _  \\ ___  _______    /   |   \\  ____ |  |   ____   ________________    _____
               /  /_\\  \\\\  \\/  /  _ \\  /    ~    \\/  _ \\|  |  /  _ \\ / ___\\_  __ \\__  \\  /     \\
              /    |    \\>    <  <_> ) \\    Y    (  <_> )  |_(  <_> ) /_/  >  | \\// __ \\|  Y Y  \\
              \\____|__  /__/\\_ \\____/   \\___|_  / \\____/|____/\\____/\\___  /|__|  (____  /__|_|  / /\\
                      \\/      \\/              \\/                   /_____/            \\/      \\/  )/
              """;

        for (String line : banner.split("\\R")) {
            getServer().getConsoleSender().sendMessage(Component.text(line, BRAND_PURPLE));
        }
        getServer().getConsoleSender().sendMessage(Component.text("AxoHologram v" + version, BRAND_PURPLE));
    }
}
