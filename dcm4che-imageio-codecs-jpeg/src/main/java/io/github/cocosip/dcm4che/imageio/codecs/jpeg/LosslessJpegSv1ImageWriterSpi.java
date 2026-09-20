package io.github.cocosip.dcm4che.imageio.codecs.jpeg;

import java.util.Locale;

import io.github.cocosip.dcm4che.imageio.codecs.core.spi.AbstractDicomImageWriterSpi;

public final class LosslessJpegSv1ImageWriterSpi extends AbstractDicomImageWriterSpi {
    private static final String[] NAMES = {"jpeg-lossless-sv1", "JPEG-LOSSLESS-SV1"};

    public LosslessJpegSv1ImageWriterSpi() {
        super("io.github.cocosip", "1.0", NAMES, LosslessJpegSv1ImageWriter.class,
                new String[] {LosslessJpegSv1ImageReaderSpi.class.getName()});
    }

    @Override
    public boolean isFormatLossless() {
        return true;
    }

    @Override
    protected LosslessJpegSv1ImageWriter newWriter() {
        return new LosslessJpegSv1ImageWriter(this);
    }

    @Override
    public String getDescription(Locale locale) {
        return "Pure Java JPEG Lossless Process 14 SV1 image writer";
    }
}
