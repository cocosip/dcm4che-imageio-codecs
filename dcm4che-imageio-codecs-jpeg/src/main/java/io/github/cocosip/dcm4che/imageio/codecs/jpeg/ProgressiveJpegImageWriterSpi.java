package io.github.cocosip.dcm4che.imageio.codecs.jpeg;

import java.util.Locale;

import io.github.cocosip.dcm4che.imageio.codecs.core.spi.AbstractDicomImageWriterSpi;

public final class ProgressiveJpegImageWriterSpi extends AbstractDicomImageWriterSpi {
    private static final String[] NAMES = {"jpeg-progressive", "JPEG-PROGRESSIVE"};

    public ProgressiveJpegImageWriterSpi() {
        super("io.github.cocosip", "1.0", NAMES, ProgressiveJpegImageWriter.class,
                new String[] {ProgressiveJpegImageReaderSpi.class.getName()});
    }

    @Override
    public boolean isFormatLossless() {
        return false;
    }

    @Override
    protected ProgressiveJpegImageWriter newWriter() {
        return new ProgressiveJpegImageWriter(this);
    }

    @Override
    public String getDescription(Locale locale) {
        return "Pure Java JPEG Progressive DCT Huffman image writer";
    }
}
