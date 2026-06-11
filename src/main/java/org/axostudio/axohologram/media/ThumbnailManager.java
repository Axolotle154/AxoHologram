package org.axostudio.axohologram.media;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public final class ThumbnailManager {

    public File createThumbnail(BufferedImage image, File destination) throws IOException {
        if (image == null || destination == null) {
            return null;
        }
        File parent = destination.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create thumbnail folder: " + parent.getAbsolutePath());
        }

        BufferedImage thumbnail = scale(image, 128);
        ImageIO.write(thumbnail, "png", destination);
        return destination;
    }

    private BufferedImage scale(BufferedImage source, int maxSize) {
        int width = source.getWidth();
        int height = source.getHeight();
        if (width <= maxSize && height <= maxSize) {
            return source;
        }

        double factor = Math.min((double) maxSize / width, (double) maxSize / height);
        int targetWidth = Math.max(1, (int) Math.round(width * factor));
        int targetHeight = Math.max(1, (int) Math.round(height * factor));
        BufferedImage output = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = output.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }
        return output;
    }
}
