package org.axostudio.axohologram.api;

import org.bukkit.Location;

import java.util.List;

/**
 * Example usage for plugin developers.
 *
 * <pre>{@code
 * AxoHologramAPI api = AxoHologram.getAPI();
 *
 * api.createHologram(
 *     "spawn_info",
 *     location,
 *     List.of("&aBienvenido", "&7Usa /warp")
 * );
 *
 * api.createHologram(
 *     "runtime_damage",
 *     location,
 *     List.of("&c-10 HP"),
 *     false
 * );
 *
 * api.createTemporaryHologram(
 *     location,
 *     List.of("&e+50 XP"),
 *     60L
 * );
 *
 * api.createItemHologram(
 *     "player_head",
 *     location,
 *     "#ITEM:PLAYER_HEAD(%player_name%)"
 * );
 * }</pre>
 */
public final class AxoHologramAPIExample {

    private AxoHologramAPIExample() {
    }

    public static void example(AxoHologramAPI api, Location location) {
        api.createHologram(
                "spawn_info",
                location,
                List.of("&aBienvenido", "&7Usa /warp")
        );

        api.createHologram(
                "runtime_damage",
                location,
                List.of("&c-10 HP"),
                false
        );

        api.createTemporaryHologram(
                location,
                List.of("&e+50 XP"),
                60L
        );

        api.createItemHologram(
                "player_head",
                location,
                "#ITEM:PLAYER_HEAD(%player_name%)"
        );
    }
}
