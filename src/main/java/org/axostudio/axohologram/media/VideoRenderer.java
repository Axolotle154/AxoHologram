package org.axostudio.axohologram.media;

public final class VideoRenderer {

    private static final double NANOS_PER_SECOND = 1_000_000_000.0D;

    private final ImageRenderer imageRenderer;

    public VideoRenderer(ImageRenderer imageRenderer) {
        this.imageRenderer = imageRenderer;
    }

    public void tick(MediaHologram hologram, long currentTick, int renderIntervalTicks, int viewerBatchSize) {
        if (hologram == null || hologram.getType() != MediaType.VIDEO) {
            return;
        }
        if (!hologram.hasViewers()
                || hologram.getState() != MediaState.READY
                || hologram.getPlaybackState() != VideoPlaybackState.PLAYING
                || hologram.isAutoPaused()) {
            return;
        }

        ProcessedMedia processedMedia = hologram.getProcessedMedia();
        if (processedMedia == null || processedMedia.frameCount() <= 0) {
            return;
        }

        MediaSettings settings = hologram.getSettings();
        int fps = Math.max(1, settings.fps());
        double nanosPerFrame = NANOS_PER_SECOND / fps;
        long nowNanos = System.nanoTime();
        long lastFrameNanos = hologram.getLastFrameNanos();
        if (lastFrameNanos <= 0L) {
            hologram.setLastFrameNanos(nowNanos);
            hologram.setLastFrameTick(currentTick);
            return;
        }

        long elapsedNanos = nowNanos - lastFrameNanos;
        if (elapsedNanos < nanosPerFrame) {
            return;
        }

        int frameStep = Math.max(1, (int) Math.min(Integer.MAX_VALUE, Math.floor(elapsedNanos / nanosPerFrame)));
        int frameCount = processedMedia.frameCount();
        int previousFrame = hologram.getCurrentFrame();
        int currentFrame = previousFrame + frameStep;

        if (currentFrame >= frameCount) {
            if (settings.loop()) {
                currentFrame %= frameCount;
            } else {
                currentFrame = frameCount - 1;
                hologram.setPlaybackState(VideoPlaybackState.STOPPED);
                hologram.setLastFrameNanos(0L);
            }
        }

        hologram.setCurrentFrame(currentFrame);
        hologram.setLastFrameTick(currentTick);
        if (hologram.getPlaybackState() == VideoPlaybackState.PLAYING) {
            long consumedNanos = Math.max(1L, Math.round(frameStep * nanosPerFrame));
            long nextFrameNanos = lastFrameNanos + consumedNanos;
            hologram.setLastFrameNanos(nextFrameNanos > nowNanos ? nowNanos : nextFrameNanos);
        }
        if (currentFrame == previousFrame) {
            return;
        }
        if (currentFrame == hologram.getLastRenderedFrame()) {
            return;
        }
        int visualInterval = Math.max(1, renderIntervalTicks);
        long lastRenderTick = hologram.getLastRenderTick();
        if (lastRenderTick > 0L && currentTick - lastRenderTick < visualInterval) {
            return;
        }
        MapFrameData frame = processedMedia.mapFrame(currentFrame);
        imageRenderer.renderFrame(hologram, frame, viewerBatchSize);
        hologram.setLastRenderedFrame(currentFrame);
        hologram.setLastRenderTick(currentTick);
    }

    public void renderCurrentFrame(MediaHologram hologram) {
        if (hologram == null || hologram.getProcessedMedia() == null || hologram.getProcessedMedia().frameCount() <= 0) {
            return;
        }

        int index = Math.max(0, Math.min(hologram.getCurrentFrame(), hologram.getProcessedMedia().frameCount() - 1));
        imageRenderer.renderFrame(hologram, hologram.getProcessedMedia().mapFrame(index));
        hologram.setLastRenderedFrame(index);
        hologram.setLastRenderTick(currentServerTick());
    }

    private long currentServerTick() {
        return org.bukkit.Bukkit.getCurrentTick();
    }
}
