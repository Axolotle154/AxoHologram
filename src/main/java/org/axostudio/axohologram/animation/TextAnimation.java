package org.axostudio.axohologram.animation;

public interface TextAnimation {

    String name();

    String type();

    long frameIndex(long tick);

    String render(String text, long tick);
}
