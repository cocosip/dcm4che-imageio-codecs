package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000;

import java.io.IOException;
import java.util.Locale;

import javax.imageio.stream.ImageInputStream;

import io.github.cocosip.dcm4che.imageio.codecs.core.spi.AbstractDicomImageReaderSpi;

public final class Jpeg2000LosslessImageReaderSpi extends AbstractDicomImageReaderSpi {
    private static final String[] NAMES = {"jpeg2000-lossless"};

    public Jpeg2000LosslessImageReaderSpi() {
        super("io.github.cocosip", "1.0", NAMES, Jpeg2000LosslessImageReader.class,
                new String[] {Jpeg2000LosslessImageWriterSpi.class.getName()});
    }

    @Override
    public boolean canDecodeInput(Object source) throws IOException {
        return super.canDecodeInput(source)
                && hasCodestreamSignature((ImageInputStream) source);
    }

    static boolean hasCodestreamSignature(ImageInputStream input) throws IOException {
        long position = input.getStreamPosition();
        try {
            return input.read() == 0xff && input.read() == 0x4f;
        } finally {
            input.seek(position);
        }
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
