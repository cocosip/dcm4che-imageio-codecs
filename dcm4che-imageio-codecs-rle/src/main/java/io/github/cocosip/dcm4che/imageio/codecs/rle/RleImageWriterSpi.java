package io.github.cocosip.dcm4che.imageio.codecs.rle;

import java.util.Locale;

import io.github.cocosip.dcm4che.imageio.codecs.core.spi.AbstractDicomImageWriterSpi;

public final class RleImageWriterSpi extends AbstractDicomImageWriterSpi {
    private static final String[] NAMES = { "rle-ext", "RLE-EXT" };

    public RleImageWriterSpi() {
        super("io.github.cocosip", "1.0", NAMES, RleImageWriter.class,
                new String[] { RleImageReaderSpi.class.getName() });
    }

    @Override
    public boolean isFormatLossless() {
        return true;
    }

    @Override
    protected RleImageWriter newWriter() {
        return new RleImageWriter(this);
    }

    @Override
    public String getDescription(Locale locale) {
        return "Pure Java DICOM RLE Lossless image writer";
    }
}
