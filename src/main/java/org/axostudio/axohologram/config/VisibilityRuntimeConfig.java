package org.axostudio.axohologram.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.Locale;

public record VisibilityRuntimeConfig(
        MovementCheckMode movementCheckMode,
        double visibilityMoveDistanceSquared,
        long movementVisibilityCooldownTicks,
        long visibilityRefreshIntervalTicks,
        long visibilityCheckIntervalTicks,
        boolean periodicVisibilityTaskEnabled,
        int defaultViewDistance
) {

    public static VisibilityRuntimeConfig from(FileConfiguration configuration) {
        FileConfiguration config = configuration;
        double movementDistance = config == null
                ? 1.5D
                : config.getDouble("performance.visibility-move-distance-blocks", 1.5D);
        double normalizedMovementDistance = Math.max(0.0D, movementDistance);

        return new VisibilityRuntimeConfig(
                MovementCheckMode.fromConfig(config == null
                        ? "DISTANCE"
                        : config.getString("performance.movement-check-mode", "DISTANCE")),
                normalizedMovementDistance * normalizedMovementDistance,
                Math.max(0L, config == null
                        ? 5L
                        : config.getLong(
                                "performance.movement-visibility-cooldown-ticks",
                                config.getLong("performance.movement-visibility-cooldown", 5L)
                        )),
                config == null
                        ? 100L
                        : config.getLong(
                                "performance.visibility-refresh-interval-ticks",
                                config.getLong("performance.visibility-refresh-interval", 100L)
                        ),
                config == null
                        ? 100L
                        : config.getLong(
                                "visibility.refresh-interval",
                                config.getLong(
                                        "performance.visibility-check-interval-ticks",
                                        config.getLong(
                                                "performance.visibility-refresh-interval-ticks",
                                                config.getLong(
                                                        "performance.visibility-refresh-interval",
                                                        config.getLong("general.visibility-check-interval", 100L)
                                                )
                                        )
                                )
                        ),
                resolvePeriodicVisibilityTaskEnabled(config),
                config == null ? 48 : config.getInt("general.view-distance", 48)
        );
    }

    public boolean hasMovedRequiredDistance(double x, double y, double z, double currentX, double currentY, double currentZ) {
        if (visibilityMoveDistanceSquared <= 0.0D) {
            return true;
        }
        double deltaX = currentX - x;
        double deltaY = currentY - y;
        double deltaZ = currentZ - z;
        return deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ >= visibilityMoveDistanceSquared;
    }

    private static boolean resolvePeriodicVisibilityTaskEnabled(FileConfiguration config) {
        if (config == null) {
            return false;
        }
        if (config.contains("visibility.periodic-task-enabled")) {
            return config.getBoolean("visibility.periodic-task-enabled");
        }
        if (config.contains("performance.visibility-periodic-task-enabled")) {
            return config.getBoolean("performance.visibility-periodic-task-enabled");
        }

        String mode = config.getString("visibility.mode", "EVENT_DRIVEN");
        return mode != null
                && !mode.equalsIgnoreCase("EVENT_DRIVEN")
                && !mode.equalsIgnoreCase("EVENT");
    }

    public enum MovementCheckMode {
        BLOCK,
        CHUNK,
        DISTANCE;

        private static MovementCheckMode fromConfig(String rawMode) {
            if (rawMode == null || rawMode.isBlank()) {
                return DISTANCE;
            }
            try {
                return MovementCheckMode.valueOf(rawMode.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return DISTANCE;
            }
        }
    }
}
