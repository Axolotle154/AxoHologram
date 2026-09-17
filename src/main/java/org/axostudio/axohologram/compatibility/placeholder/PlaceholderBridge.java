package org.axostudio.axohologram.compatibility.placeholder;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiFunction;

/**
 * Common Java contract for placeholder resolution and expansion registration.
 */
public interface PlaceholderBridge {

    /**
     * @return true if PlaceholderAPI is present and enabled
     */
    boolean isPlaceholderApiAvailable();

    /**
     * @return true if MiniPlaceholders is present and enabled
     */
    boolean isMiniPlaceholdersAvailable();

    /**
     * Replaces PlaceholderAPI placeholders in text for an online player.
     *
     * @param player the player context or null
     * @param text the text to process
     * @return text with replaced placeholders
     */
    @NotNull
    String setPlaceholders(@Nullable Player player, @NotNull String text);

    /**
     * Replaces PlaceholderAPI placeholders in text for an offline player.
     *
     * @param player the offline player context or null
     * @param text the text to process
     * @return text with replaced placeholders
     */
    @NotNull
    String setPlaceholders(@Nullable OfflinePlayer player, @NotNull String text);

    /**
     * Registers an expansion for this plugin with PlaceholderAPI if present.
     *
     * @param identifier expansion identifier
     * @param handler logic to handle requests
     */
    void registerExpansion(@NotNull String identifier, @NotNull BiFunction<OfflinePlayer, String, String> handler);

    /**
     * Unregisters an expansion if present.
     *
     * @param identifier expansion identifier
     */
    void unregisterExpansion(@NotNull String identifier);
}
