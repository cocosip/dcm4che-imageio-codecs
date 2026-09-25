package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000;

import java.io.IOException;
import java.util.Locale;

import javax.imageio.stream.ImageInputStream;

import io.github.cocosip.dcm4che.imageio.codecs.core.spi.AbstractDicomImageReaderSpi;

public final class Jpeg2000LossyImageReaderSpi extends AbstractDicomImageReaderSpi {
    private static final String[] NAMES = {"jpeg2000-lossy"};

    public Jpeg2000LossyImageReaderSpi() {
        super("io.github.cocosip", "1.0", NAMES, Jpeg2000LossyImageReader.class,
                new String[] {Jpeg2000LossyImageWriterSpi.class.getName()});
    }

    @Override
    public boolean canDecodeInput(Object source) throws IOException {
        return super.canDecodeInput(source)
                && Jpeg2000LosslessImageReaderSpi.hasCodestreamSignature(
                        (ImageInputStream) source);
    }

    @Override
    protected Jpeg2000LossyImageReader newReader() {
        return new Jpeg2000LossyImageReader(this);
    }

    @Override
    public String getDescription(Locale locale) {
        return "Pure Java DICOM JPEG 2000 Lossy reader";
    }
}
