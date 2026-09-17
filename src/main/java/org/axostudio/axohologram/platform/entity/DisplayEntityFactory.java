package org.axostudio.axohologram.platform.entity;

import org.bukkit.Location;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;

public final class DisplayEntityFactory {

    private DisplayEntityFactory() {
    }

    public static void configureBaseDisplay(Display display, Display.Billboard billboard, float viewRange, float shadowRadius, float shadowStrength) {
        display.setVisibleByDefault(false);
        display.setPersistent(false);
        display.setInvulnerable(true);
        display.setGravity(false);
        display.setBillboard(billboard != null ? billboard : Display.Billboard.CENTER);
        if (viewRange > 0) {
            display.setViewRange(viewRange);
        }
        display.setShadowRadius(shadowRadius);
        display.setShadowStrength(shadowStrength);
        display.setInterpolationDelay(0);
    }

    public static Interaction spawnInteraction(Location location, float width, float height) {
        if (location == null || location.getWorld() == null) return null;
        return location.getWorld().spawn(location, Interaction.class, interaction -> {
            interaction.setVisibleByDefault(false);
            interaction.setPersistent(false);
            interaction.setInvulnerable(true);
            interaction.setGravity(false);
            interaction.setInteractionWidth(Math.max(0.1f, width));
            interaction.setInteractionHeight(Math.max(0.1f, height));
            interaction.setResponsive(true);
        });
    }
}
