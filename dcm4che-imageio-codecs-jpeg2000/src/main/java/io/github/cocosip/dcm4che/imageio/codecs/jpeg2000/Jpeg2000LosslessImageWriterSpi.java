package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000;

import java.util.Locale;

import io.github.cocosip.dcm4che.imageio.codecs.core.spi.AbstractDicomImageWriterSpi;

public final class Jpeg2000LosslessImageWriterSpi extends AbstractDicomImageWriterSpi {
    private static final String[] NAMES = {"jpeg2000-lossless"};

    public Jpeg2000LosslessImageWriterSpi() {
        super("io.github.cocosip", "1.0", NAMES, Jpeg2000LosslessImageWriter.class,
                new String[] {Jpeg2000LosslessImageReaderSpi.class.getName()});
    }

    @Override
    protected Jpeg2000LosslessImageWriter newWriter() {
        return new Jpeg2000LosslessImageWriter(this);
    }

    @Override
    public String getDescription(Locale locale) {
        return "Pure Java DICOM JPEG 2000 Lossless writer";
    }
}
