package org.axostudio.axohologram.importer;

public final class LineConverter {

    public ConvertedHologram.ConvertedLine convert(SourceLine sourceLine) {
        return new ConvertedHologram.ConvertedLine(
                sourceLine.type(),
                sourceLine.content(),
                sourceLine.itemStack(),
                sourceLine.blockData(),
                sourceLine.offset(),
                sourceLine.height(),
                sourceLine.billboard(),
                sourceLine.permission()
        );
    }
}
