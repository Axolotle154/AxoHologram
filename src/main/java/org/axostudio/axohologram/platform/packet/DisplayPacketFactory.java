package org.axostudio.axohologram.platform.packet;

import net.kyori.adventure.text.Component;
import org.axostudio.axohologram.platform.entity.BlockDisplayAdapter;
import org.axostudio.axohologram.platform.entity.DisplayEntityFactory;
import org.axostudio.axohologram.platform.entity.ItemDisplayAdapter;
import org.axostudio.axohologram.platform.entity.TextDisplayAdapter;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class DisplayPacketFactory {

    private DisplayPacketFactory() {
    }

    public static TextDisplay spawnTextDisplay(
            Location location,
            Display.Billboard billboard,
            float viewRange,
            float shadowRadius,
            float shadowStrength,
            Component text,
            Color backgroundColor,
            boolean textShadow,
            boolean seeThrough,
            TextDisplay.TextAlignment alignment,
            int lineWidth,
            float scaleX,
            float scaleY,
            float scaleZ,
            int interpolationDuration
    ) {
        if (location == null || location.getWorld() == null) return null;
        return location.getWorld().spawn(location, TextDisplay.class, display -> {
            DisplayEntityFactory.configureBaseDisplay(display, billboard, viewRange, shadowRadius, shadowStrength);
            TextDisplayAdapter.applyTextStyle(display, text, backgroundColor, textShadow, seeThrough, alignment, lineWidth);
            TextDisplayAdapter.applyTransformation(display, scaleX, scaleY, scaleZ, interpolationDuration);
        });
    }

    public static ItemDisplay spawnItemDisplay(
            Location location,
            Display.Billboard billboard,
            float viewRange,
            float shadowRadius,
            float shadowStrength,
            ItemStack itemStack,
            ItemDisplay.ItemDisplayTransform transform,
            Vector3f translation,
            Quaternionf leftRotation,
            Vector3f scale,
            Quaternionf rightRotation,
            int interpolationDuration
    ) {
        if (location == null || location.getWorld() == null) return null;
        return location.getWorld().spawn(location, ItemDisplay.class, display -> {
            DisplayEntityFactory.configureBaseDisplay(display, billboard, viewRange, shadowRadius, shadowStrength);
            ItemDisplayAdapter.applyItem(display, itemStack, transform);
            ItemDisplayAdapter.applyTransformation(display, translation, leftRotation, scale, rightRotation, interpolationDuration);
        });
    }

    public static BlockDisplay spawnBlockDisplay(
            Location location,
            Display.Billboard billboard,
            float viewRange,
            float shadowRadius,
            float shadowStrength,
            BlockData blockData,
            Vector3f translation,
            Quaternionf leftRotation,
            Vector3f scale,
            Quaternionf rightRotation,
            int interpolationDuration
    ) {
        if (location == null || location.getWorld() == null) return null;
        return location.getWorld().spawn(location, BlockDisplay.class, display -> {
            DisplayEntityFactory.configureBaseDisplay(display, billboard, viewRange, shadowRadius, shadowStrength);
            BlockDisplayAdapter.applyBlockData(display, blockData);
            BlockDisplayAdapter.applyTransformation(display, translation, leftRotation, scale, rightRotation, interpolationDuration);
        });
    }

    public static Interaction spawnInteraction(Location location, float width, float height) {
        return DisplayEntityFactory.spawnInteraction(location, width, height);
    }
}
