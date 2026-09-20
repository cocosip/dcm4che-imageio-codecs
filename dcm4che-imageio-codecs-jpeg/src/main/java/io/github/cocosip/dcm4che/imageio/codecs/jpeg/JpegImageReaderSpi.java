package io.github.cocosip.dcm4che.imageio.codecs.jpeg;

import java.util.Locale;

import io.github.cocosip.dcm4che.imageio.codecs.core.spi.AbstractDicomImageReaderSpi;

public final class JpegImageReaderSpi extends AbstractDicomImageReaderSpi {
    private static final String[] NAMES = {"jpeg-ext", "JPEG-EXT"};

    public JpegImageReaderSpi() {
        super("io.github.cocosip", "1.0", NAMES, JpegImageReader.class,
                new String[] {JpegImageWriterSpi.class.getName()});
    }

    @Override
    protected JpegImageReader newReader() {
        return new JpegImageReader(this);
    }

    @Override
    public String getDescription(Locale locale) {
        return "Pure Java JPEG Baseline Process 1 image reader";
    }
}
