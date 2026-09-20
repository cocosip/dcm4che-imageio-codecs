package io.github.cocosip.dcm4che.imageio.codecs.jpeg;

import java.util.Locale;

import io.github.cocosip.dcm4che.imageio.codecs.core.spi.AbstractDicomImageWriterSpi;

public final class ExtendedJpegImageWriterSpi extends AbstractDicomImageWriterSpi {
    private static final String[] NAMES = {"jpeg-ext", "JPEG-EXT"};

    public ExtendedJpegImageWriterSpi() {
        super("io.github.cocosip", "1.0", NAMES, ExtendedJpegImageWriter.class,
                new String[] {ExtendedJpegImageReaderSpi.class.getName()});
    }

    @Override
    public boolean isFormatLossless() {
        return false;
    }

    @Override
    protected ExtendedJpegImageWriter newWriter() {
        return new ExtendedJpegImageWriter(this);
    }

    @Override
    public String getDescription(Locale locale) {
        return "Pure Java JPEG Extended Process 2/4 image writer";
    }
}
