package io.github.cocosip.dcm4che.imageio.codecs.jpeg;

import java.util.Locale;

import io.github.cocosip.dcm4che.imageio.codecs.core.spi.AbstractDicomImageWriterSpi;

public final class JpegImageWriterSpi extends AbstractDicomImageWriterSpi {
    private static final String[] NAMES = {"jpeg-ext", "JPEG-EXT"};

    public JpegImageWriterSpi() {
        super("io.github.cocosip", "1.0", NAMES, JpegImageWriter.class,
                new String[] {JpegImageReaderSpi.class.getName()});
    }

    @Override
    public boolean isFormatLossless() {
        return false;
    }

    @Override
    protected JpegImageWriter newWriter() {
        return new JpegImageWriter(this);
    }

    @Override
    public String getDescription(Locale locale) {
        return "Pure Java JPEG Baseline Process 1 image writer";
    }
}
