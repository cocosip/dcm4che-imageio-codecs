package io.github.cocosip.dcm4che.imageio.codecs.jpeg;

import java.util.Locale;

import io.github.cocosip.dcm4che.imageio.codecs.core.spi.AbstractDicomImageReaderSpi;

public final class ExtendedJpegImageReaderSpi extends AbstractDicomImageReaderSpi {
    private static final String[] NAMES = {"jpeg-ext", "JPEG-EXT"};

    public ExtendedJpegImageReaderSpi() {
        super("io.github.cocosip", "1.0", NAMES, ExtendedJpegImageReader.class,
                new String[] {ExtendedJpegImageWriterSpi.class.getName()});
    }

    @Override
    protected ExtendedJpegImageReader newReader() {
        return new ExtendedJpegImageReader(this);
    }

    @Override
    public String getDescription(Locale locale) {
        return "Pure Java JPEG Extended Process 2/4 image reader";
    }
}
