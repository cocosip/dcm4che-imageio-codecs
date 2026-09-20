package io.github.cocosip.dcm4che.imageio.codecs.jpeg;

import java.util.Locale;

import io.github.cocosip.dcm4che.imageio.codecs.core.spi.AbstractDicomImageReaderSpi;

public final class LosslessJpegSv1ImageReaderSpi extends AbstractDicomImageReaderSpi {
    private static final String[] NAMES = {"jpeg-lossless-sv1", "JPEG-LOSSLESS-SV1"};

    public LosslessJpegSv1ImageReaderSpi() {
        super("io.github.cocosip", "1.0", NAMES, LosslessJpegSv1ImageReader.class,
                new String[] {LosslessJpegSv1ImageWriterSpi.class.getName()});
    }

    @Override
    protected LosslessJpegSv1ImageReader newReader() {
        return new LosslessJpegSv1ImageReader(this);
    }

    @Override
    public String getDescription(Locale locale) {
        return "Pure Java JPEG Lossless Process 14 SV1 image reader";
    }
}
