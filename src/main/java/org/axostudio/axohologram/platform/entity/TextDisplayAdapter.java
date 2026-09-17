package org.axostudio.axohologram.platform.entity;

import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

public final class TextDisplayAdapter {

    private TextDisplayAdapter() {
    }

    public static void applyTextStyle(
            TextDisplay display,
            Component text,
            Color backgroundColor,
            boolean textShadow,
            boolean seeThrough,
            TextDisplay.TextAlignment alignment,
            int lineWidth
    ) {
        if (display == null) return;
        if (text != null) {
            display.text(text);
        }
        // Never inherit Minecraft's default opaque text background. The
        // hologram's configured colour (including transparent) is authoritative.
        display.setDefaultBackground(false);
        display.setTextOpacity((byte) 0xFF);
        if (backgroundColor != null) {
            display.setBackgroundColor(backgroundColor);
        }
        display.setShadowed(textShadow);
        display.setSeeThrough(seeThrough);
        if (alignment != null) {
            display.setAlignment(alignment);
        }
        if (lineWidth > 0) {
            display.setLineWidth(lineWidth);
        }
    }

    public static void applyTransformation(TextDisplay display, float scaleX, float scaleY, float scaleZ, int interpolationDuration) {
        if (display == null) return;
        display.setInterpolationDuration(interpolationDuration);
        display.setTransformation(new Transformation(
                new Vector3f(0f, 0f, 0f),
                new AxisAngle4f(0f, 0f, 1f, 0f),
                new Vector3f(scaleX, scaleY, scaleZ),
                new AxisAngle4f(0f, 0f, 1f, 0f)
        ));
    }
}
