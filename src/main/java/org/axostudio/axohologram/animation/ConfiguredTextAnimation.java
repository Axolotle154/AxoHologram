package org.axostudio.axohologram.animation;

import java.util.List;

public final class ConfiguredTextAnimation implements TextAnimation {

    private static final List<String> RAINBOW_COLORS = List.of("&c", "&6", "&e", "&a", "&b", "&d");

    private final String name;
    private final String type;
    private final int speed;
    private final List<String> colors;
    private final String color;
    private final String color1;
    private final String color2;

    public ConfiguredTextAnimation(String name, String type, int speed, List<String> colors, String color, String color1, String color2) {
        this.name = name;
        this.type = type;
        this.speed = Math.max(1, speed);
        this.colors = colors == null || colors.isEmpty() ? List.of("&f") : List.copyOf(colors);
        this.color = color == null || color.isBlank() ? "&f" : color;
        this.color1 = color1 == null || color1.isBlank() ? "&f" : color1;
        this.color2 = color2 == null || color2.isBlank() ? "&b" : color2;
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
    public long frameIndex(long tick) {
        return Math.max(0L, tick / speed);
    }

    @Override
    public String render(String text, long tick) {
        long frame = frameIndex(tick);
        return switch (type) {
            case "rainbow" -> RAINBOW_COLORS.get((int) (frame % RAINBOW_COLORS.size())) + text;
            case "pulse" -> colors.get((int) (frame % colors.size())) + text;
            case "matrix" -> color + text;
            case "wave" -> renderWave(text, frame);
            default -> text;
        };
    }

    private String renderWave(String text, long frame) {
        StringBuilder output = new StringBuilder(text.length() * 4);
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (Character.isWhitespace(character)) {
                output.append(character);
                continue;
            }

            output.append(((index + frame) & 1L) == 0L ? color1 : color2).append(character);
        }
        return output.toString();
    }
}
