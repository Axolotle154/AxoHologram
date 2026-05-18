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
import org.axostudio.axohologram.integration.axonpcs.AxoNpcHook;
import org.axostudio.axohologram.integration.fancynpcs.FancyNpcHook;
import org.axostudio.axohologram.integration.MiniPlaceholdersIntegration;
import org.axostudio.axohologram.integration.npc.CompositeNpcHook;
import org.axostudio.axohologram.integration.npc.NpcHook;
import org.axostudio.axohologram.integration.npc.NpcLinkService;
import org.axostudio.axohologram.listener.HologramInteractionListener;
import org.axostudio.axohologram.integration.PlaceholderAPIIntegration;
import org.axostudio.axohologram.listener.PlayerListener;
import org.axostudio.axohologram.util.MiniMessageUtil;
import org.axostudio.axohologram.util.SchedulerUtil;
import org.axostudio.axohologram.util.UpdateChecker;
import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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

    private static final long[] POST_STARTUP_PLACEHOLDER_REFRESH_DELAYS = {1L, 20L, 100L};

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
        setupPlaceholderIntegrations();
        hologramManager.loadHolograms();
        setupNpcIntegration();
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

        schedulePlaceholderRuntimeRefreshes(POST_STARTUP_PLACEHOLDER_REFRESH_DELAYS);
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

    private void setupPlaceholderIntegrations() {
        setupPlaceholderApiIntegration();
        setupMiniPlaceholdersIntegration();
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

    private void setupNpcIntegration() {
        stopNpcIntegration();

        List<NpcHook> hooks = new ArrayList<>();
        addAxoNpcHook(hooks);
        addFancyNpcHook(hooks);

        if (hooks.isEmpty()) {
            getLogger().info("No supported NPC plugin found. NPC-linked holograms will stay static.");
            return;
        }

        NpcHook hook = hooks.size() == 1 ? hooks.getFirst() : new CompositeNpcHook(hooks);
        if (!hook.isAvailable()) {
            getLogger().warning("Supported NPC plugin found, but its API is not available. Skipping NPC integration.");
            return;
        }

        npcLinkService = new NpcLinkService(this, hook);
        npcLinkService.start();
        getLogger().info("NPC link integration enabled through " + hook.getPluginName() + ".");
    }

    private void addAxoNpcHook(List<NpcHook> hooks) {
        if (!configManager.getConfig().getBoolean("integrations.axonpcs", true)) {
            getLogger().info("AxoNPCs integration disabled in config.");
            return;
        }

        if (!Bukkit.getPluginManager().isPluginEnabled("AxoNPCs")) {
            getLogger().info("AxoNPCs not found.");
            return;
        }

        AxoNpcHook hook = new AxoNpcHook();
        if (!hook.isAvailable()) {
            getLogger().warning("AxoNPCs is enabled but its API is not available.");
            return;
        }

        hooks.add(hook);
    }

    private void addFancyNpcHook(List<NpcHook> hooks) {
        if (!configManager.getConfig().getBoolean("integrations.fancynpcs", true)) {
            getLogger().info("FancyNpcs integration disabled in config.");
            return;
        }

        if (!Bukkit.getPluginManager().isPluginEnabled("FancyNpcs")) {
            getLogger().info("FancyNpcs not found.");
            return;
        }

        FancyNpcHook hook = new FancyNpcHook();
        if (!hook.isAvailable()) {
            getLogger().warning("FancyNpcs is enabled but its API is not available.");
            return;
        }

        hooks.add(hook);
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
        MiniMessageUtil.clearPlaceholderApiCache();
        animationManager.reloadAnimations();
        setupPlaceholderIntegrations();
        hologramManager.reload();
        setupNpcIntegration();
        schedulePlaceholderRuntimeRefreshes(1L, 20L);
        checkForUpdates();
    }

    public void handleOptionalPluginEnabled(String pluginName) {
        if (pluginName == null || pluginName.isBlank()) {
            return;
        }

        boolean placeholderIntegrationChanged = false;
        switch (pluginName.trim().toLowerCase(Locale.ROOT)) {
            case "placeholderapi" -> {
                setupPlaceholderApiIntegration();
                placeholderIntegrationChanged = true;
            }
            case "miniplaceholders" -> {
                setupMiniPlaceholdersIntegration();
                placeholderIntegrationChanged = true;
            }
            case "axonpcs", "fancynpcs" -> {
                setupNpcIntegration();
                if (hologramManager != null) {
                    hologramManager.refreshRuntimeStateAndOnlineViewers();
                }
            }
            default -> {
                return;
            }
        }

        if (placeholderIntegrationChanged) {
            schedulePlaceholderRuntimeRefreshes(1L, 20L, 100L);
        }
    }

    private void schedulePlaceholderRuntimeRefreshes(long... delays) {
        if (schedulerUtil == null || hologramManager == null || delays == null) {
            return;
        }

        for (long delay : delays) {
            long normalizedDelay = Math.max(1L, delay);
            schedulerUtil.runGlobalDelayed(task -> refreshPlaceholderRuntimeState(), normalizedDelay);
        }
    }

    private void refreshPlaceholderRuntimeState() {
        if (!isEnabled() || hologramManager == null) {
            return;
        }

        MiniMessageUtil.clearPlaceholderApiCache();
        hologramManager.refreshRuntimeStateAndOnlineViewers();
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

    public UpdateChecker getUpdateChecker() {
        return updateChecker;
    }

    private void checkForUpdates() {
        if (!configManager.getConfig().getBoolean("general.check-updates", true)) {
            if (updateChecker != null) {
                updateChecker.reset();
            }
            getLogger().info("Update checker disabled in config.");
            return;
        }

        if (updateChecker != null) {
            updateChecker.check();
        }
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
