package org.axostudio.axohologram.platform.entity;

import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class ItemDisplayAdapter {

    private ItemDisplayAdapter() {
    }

    public static void applyItem(ItemDisplay display, ItemStack itemStack, ItemDisplay.ItemDisplayTransform transform) {
        if (display == null) return;
        if (itemStack != null) {
            display.setItemStack(itemStack);
        }
        if (transform != null) {
            display.setItemDisplayTransform(transform);
        }
    }

    public static void applyTransformation(
            ItemDisplay display,
            Vector3f translation,
            Quaternionf leftRotation,
            Vector3f scale,
            Quaternionf rightRotation,
            int interpolationDuration
    ) {
        if (display == null) return;
        display.setInterpolationDuration(interpolationDuration);
        display.setTransformation(new Transformation(
                translation != null ? translation : new Vector3f(0f, 0f, 0f),
                leftRotation != null ? leftRotation : new Quaternionf(),
                scale != null ? scale : new Vector3f(1f, 1f, 1f),
                rightRotation != null ? rightRotation : new Quaternionf()
        ));
    }
}
