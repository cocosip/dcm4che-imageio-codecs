package io.github.cocosip.dcm4che.imageio.codecs.jpeg;

import java.util.Locale;

import io.github.cocosip.dcm4che.imageio.codecs.core.spi.AbstractDicomImageWriterSpi;

public final class LosslessJpegImageWriterSpi extends AbstractDicomImageWriterSpi {
    private static final String[] NAMES = {"jpeg-lossless", "JPEG-LOSSLESS"};

    public LosslessJpegImageWriterSpi() {
        super("io.github.cocosip", "1.0", NAMES, LosslessJpegImageWriter.class,
                new String[] {LosslessJpegImageReaderSpi.class.getName()});
    }

    @Override
    public boolean isFormatLossless() {
        return true;
    }

    @Override
    protected LosslessJpegImageWriter newWriter() {
        return new LosslessJpegImageWriter(this);
    }

    @Override
    public String getDescription(Locale locale) {
        return "Pure Java JPEG Lossless Process 14 image writer";
    }
}
