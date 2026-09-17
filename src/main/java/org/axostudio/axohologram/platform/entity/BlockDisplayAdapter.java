package org.axostudio.axohologram.platform.entity;

import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class BlockDisplayAdapter {

    private BlockDisplayAdapter() {
    }

    public static void applyBlockData(BlockDisplay display, BlockData blockData) {
        if (display == null || blockData == null) return;
        display.setBlock(blockData);
    }

    public static void applyTransformation(
            BlockDisplay display,
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
