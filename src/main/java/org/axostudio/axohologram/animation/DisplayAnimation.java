package org.axostudio.axohologram.animation;

public interface DisplayAnimation {

    String name();

    String type();

    DisplayAnimationFrame frame(long tick);
}
