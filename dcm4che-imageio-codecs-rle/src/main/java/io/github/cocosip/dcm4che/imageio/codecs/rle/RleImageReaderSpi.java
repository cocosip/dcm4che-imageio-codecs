package io.github.cocosip.dcm4che.imageio.codecs.rle;

import java.util.Locale;

import io.github.cocosip.dcm4che.imageio.codecs.core.spi.AbstractDicomImageReaderSpi;

public final class RleImageReaderSpi extends AbstractDicomImageReaderSpi {
    private static final String[] NAMES = { "rle-ext", "RLE-EXT" };

    public RleImageReaderSpi() {
        super("io.github.cocosip", "1.0", NAMES, RleImageReader.class,
                new String[] { RleImageWriterSpi.class.getName() });
    }

    @Override
    protected RleImageReader newReader() {
        return new RleImageReader(this);
    }

    @Override
    public String getDescription(Locale locale) {
        return "Pure Java DICOM RLE Lossless image reader";
    }
}
