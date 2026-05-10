package org.axostudio.axohologram.importer;

import java.util.ArrayList;
import java.util.List;

public final class HologramConverter {

    private final LineConverter lineConverter;

    public HologramConverter(LineConverter lineConverter) {
        this.lineConverter = lineConverter;
    }

    public ConvertedHologram convert(SourceHologram source) {
        List<List<ConvertedHologram.ConvertedLine>> pages = new ArrayList<>();
        for (List<SourceLine> sourcePage : source.pages()) {
            List<ConvertedHologram.ConvertedLine> convertedLines = new ArrayList<>();
            for (SourceLine sourceLine : sourcePage) {
                convertedLines.add(lineConverter.convert(sourceLine));
            }
            pages.add(List.copyOf(convertedLines));
        }

        return new ConvertedHologram(
                source.name(),
                source.name(),
                source.worldName(),
                source.location(),
                source.enabled(),
                source.visibilityDistance(),
                source.visibilityMode(),
                source.billboard(),
                source.scaleX(),
                source.scaleY(),
                source.scaleZ(),
                source.translation(),
                source.shadowRadius(),
                source.shadowStrength(),
                source.backgroundColor(),
                source.textShadow(),
                source.seeThrough(),
                source.alignment(),
                source.updateTextInterval(),
                source.displayAnimation(),
                pages
        );
    }
}
