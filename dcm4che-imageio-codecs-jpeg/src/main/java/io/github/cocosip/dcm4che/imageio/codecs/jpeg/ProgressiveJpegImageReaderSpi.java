package io.github.cocosip.dcm4che.imageio.codecs.jpeg;

import java.util.Locale;

import io.github.cocosip.dcm4che.imageio.codecs.core.spi.AbstractDicomImageReaderSpi;

public final class ProgressiveJpegImageReaderSpi extends AbstractDicomImageReaderSpi {
    private static final String[] NAMES = {"jpeg-progressive", "JPEG-PROGRESSIVE"};

    public ProgressiveJpegImageReaderSpi() {
        super("io.github.cocosip", "1.0", NAMES, ProgressiveJpegImageReader.class,
                new String[] {ProgressiveJpegImageWriterSpi.class.getName()});
    }

    @Override
    protected ProgressiveJpegImageReader newReader() {
        return new ProgressiveJpegImageReader(this);
    }

    @Override
    public String getDescription(Locale locale) {
        return "Pure Java JPEG Progressive DCT Huffman image reader";
    }
}
