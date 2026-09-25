package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000;

import java.util.Locale;

import io.github.cocosip.dcm4che.imageio.codecs.core.spi.AbstractDicomImageReaderSpi;

public final class Jpeg2000LosslessImageReaderSpi extends AbstractDicomImageReaderSpi {
    private static final String[] NAMES = {"jpeg2000-lossless"};

    public Jpeg2000LosslessImageReaderSpi() {
        super("io.github.cocosip", "1.0", NAMES, Jpeg2000LosslessImageReader.class,
                new String[] {Jpeg2000LosslessImageWriterSpi.class.getName()});
    }

    @Override
    protected Jpeg2000LosslessImageReader newReader() {
        return new Jpeg2000LosslessImageReader(this);
    }

    @Override
    public String getDescription(Locale locale) {
        return "Pure Java DICOM JPEG 2000 Lossless reader";
    }
}
