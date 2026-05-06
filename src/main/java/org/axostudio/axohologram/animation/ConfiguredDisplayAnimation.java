package org.axostudio.axohologram.animation;

import java.util.Locale;

public final class ConfiguredDisplayAnimation implements DisplayAnimation {

    private final String name;
    private final String type;
    private final double height;
    private final double radius;
    private final double speed;
    private final double rotationSpeed;
    private final float scaleMin;
    private final float scaleMax;
    private final String axis;

    public ConfiguredDisplayAnimation(
            String name,
            String type,
            double height,
            double radius,
            double speed,
            double rotationSpeed,
            float scaleMin,
            float scaleMax,
            String axis
    ) {
        this.name = name;
        this.type = type;
        this.height = Math.max(0.0D, height);
        this.radius = Math.max(0.0D, radius);
        this.speed = Math.max(0.01D, speed);
        this.rotationSpeed = rotationSpeed;
        this.scaleMin = Math.max(0.01F, scaleMin);
        this.scaleMax = Math.max(this.scaleMin, scaleMax);
        this.axis = axis == null || axis.isBlank() ? "y" : axis.toLowerCase(Locale.ROOT);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String type() {
        return type;
    }

    @Override
    public DisplayAnimationFrame frame(long tick) {
        return switch (type) {
            case "float" -> floatingFrame(tick);
            case "spin" -> spinFrame(tick);
            case "cinematic-idle" -> cinematicIdleFrame(tick);
            case "orbit" -> orbitFrame(tick);
            default -> DisplayAnimationFrame.IDENTITY;
        };
    }

    private DisplayAnimationFrame floatingFrame(long tick) {
        double phase = tick * speed * 0.1D;
        return new DisplayAnimationFrame(0.0D, Math.sin(phase) * height, 0.0D, 0.0F, 0.0F, 0.0F, 1.0F);
    }

    private DisplayAnimationFrame spinFrame(long tick) {
        float degrees = normalizeDegrees((float) (tick * speed * 3.0D));
        return switch (axis) {
            case "x" -> new DisplayAnimationFrame(0.0D, 0.0D, 0.0D, 0.0F, degrees, 0.0F, 1.0F);
            case "z" -> new DisplayAnimationFrame(0.0D, 0.0D, 0.0D, 0.0F, 0.0F, degrees, 1.0F);
            default -> new DisplayAnimationFrame(0.0D, 0.0D, 0.0D, degrees, 0.0F, 0.0F, 1.0F);
        };
    }

    private DisplayAnimationFrame cinematicIdleFrame(long tick) {
        double phase = tick * speed * 0.08D;
        double floatOffset = Math.sin(phase) * height;
        float yawOffset = normalizeDegrees((float) (tick * rotationSpeed));
        float scaleRange = scaleMax - scaleMin;
        float scale = scaleMin + (float) ((Math.sin(phase) + 1.0D) * 0.5D * scaleRange);
        return new DisplayAnimationFrame(0.0D, floatOffset, 0.0D, yawOffset, 0.0F, 0.0F, scale);
    }

    private DisplayAnimationFrame orbitFrame(long tick) {
        double phase = tick * speed * 0.08D;
        return new DisplayAnimationFrame(Math.cos(phase) * radius, 0.0D, Math.sin(phase) * radius, 0.0F, 0.0F, 0.0F, 1.0F);
    }

    private static float normalizeDegrees(float degrees) {
        float normalized = degrees % 360.0F;
        return normalized < 0.0F ? normalized + 360.0F : normalized;
    }
}
