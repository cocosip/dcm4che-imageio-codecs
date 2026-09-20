package io.github.cocosip.dcm4che.imageio.codecs.jpeg;

import java.util.Locale;

import io.github.cocosip.dcm4che.imageio.codecs.core.spi.AbstractDicomImageReaderSpi;

public final class LosslessJpegImageReaderSpi extends AbstractDicomImageReaderSpi {
    private static final String[] NAMES = {"jpeg-lossless", "JPEG-LOSSLESS"};

    public LosslessJpegImageReaderSpi() {
        super("io.github.cocosip", "1.0", NAMES, LosslessJpegImageReader.class,
                new String[] {LosslessJpegImageWriterSpi.class.getName()});
    }

    @Override
    protected LosslessJpegImageReader newReader() {
        return new LosslessJpegImageReader(this);
    }

    @Override
    public String getDescription(Locale locale) {
        return "Pure Java JPEG Lossless Process 14 image reader";
    }
}
