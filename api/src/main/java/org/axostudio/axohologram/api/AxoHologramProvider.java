package org.axostudio.axohologram.api;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

public final class AxoHologramProvider {

    private static AxoHologramAPI instance;

    private AxoHologramProvider() {
    }

    public static AxoHologramAPI get() {
        if (instance != null) {
            return instance;
        }

        RegisteredServiceProvider<AxoHologramAPI> registration = Bukkit.getServicesManager().getRegistration(AxoHologramAPI.class);
        if (registration != null) {
            instance = registration.getProvider();
            return instance;
        }

        throw new IllegalStateException("AxoHologramAPI is not registered yet or AxoHologram is disabled.");
    }

    public static boolean isAvailable() {
        if (instance != null) {
            return true;
        }
        return Bukkit.getServicesManager().getRegistration(AxoHologramAPI.class) != null;
    }

    public static void setInstance(AxoHologramAPI api) {
        instance = api;
    }

    public static void register(AxoHologramAPI api) {
        setInstance(api);
    }

    public static void register(org.axostudio.axohologram.api.hologram.HologramService service) {
        if (service instanceof AxoHologramAPI) {
            setInstance((AxoHologramAPI) service);
        }
    }

    public static void clearInstance() {
        instance = null;
    }

    public static void unregister() {
        clearInstance();
    }
}
