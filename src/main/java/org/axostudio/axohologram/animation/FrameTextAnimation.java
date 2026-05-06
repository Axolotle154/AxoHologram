package org.axostudio.axohologram.animation;

import java.util.List;

public final class FrameTextAnimation implements TextAnimation {

    private final String name;
    private final List<String> frames;
    private final int frameDuration;
    private final boolean loop;

    public FrameTextAnimation(String name, List<String> frames, int frameDuration, boolean loop) {
        this.name = name;
        this.frames = List.copyOf(frames);
        this.frameDuration = Math.max(1, frameDuration);
        this.loop = loop;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String type() {
        return "frame-animation";
    }

    @Override
    public long frameIndex(long tick) {
        if (frames.isEmpty()) {
            return 0L;
        }

        long frame = Math.max(0L, tick / frameDuration);
        return loop ? frame % frames.size() : Math.min(frame, frames.size() - 1L);
    }

    @Override
    public String render(String text, long tick) {
        if (frames.isEmpty()) {
            return text;
        }

        return frames.get((int) frameIndex(tick)).replace("{text}", text);
    }
}
