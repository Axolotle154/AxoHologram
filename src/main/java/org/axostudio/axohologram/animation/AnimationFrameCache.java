package org.axostudio.axohologram.animation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class AnimationFrameCache {

    private final Map<String, String> textFrames = new ConcurrentHashMap<>();

    public String getTextFrame(String key, Supplier<String> renderer) {
        return textFrames.computeIfAbsent(key, ignored -> renderer.get());
    }

    public void clear() {
        textFrames.clear();
    }
}
