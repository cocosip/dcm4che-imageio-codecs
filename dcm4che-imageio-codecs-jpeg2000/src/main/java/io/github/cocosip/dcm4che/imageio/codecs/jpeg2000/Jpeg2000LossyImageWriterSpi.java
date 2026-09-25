package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000;

import java.util.Locale;

import io.github.cocosip.dcm4che.imageio.codecs.core.spi.AbstractDicomImageWriterSpi;

public final class Jpeg2000LossyImageWriterSpi extends AbstractDicomImageWriterSpi {
    private static final String[] NAMES = {"jpeg2000-lossy"};

    public Jpeg2000LossyImageWriterSpi() {
        super("io.github.cocosip", "1.0", NAMES, Jpeg2000LossyImageWriter.class,
                new String[] {Jpeg2000LossyImageReaderSpi.class.getName()});
    }

    @Override
    protected Jpeg2000LossyImageWriter newWriter() {
        return new Jpeg2000LossyImageWriter(this);
    }

    @Override
    public String getDescription(Locale locale) {
        return "Pure Java DICOM JPEG 2000 Lossy writer";
    }
}
