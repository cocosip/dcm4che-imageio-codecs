package io.github.cocosip.dcm4che.imageio.codecs.jpegls;

import java.util.Locale;

import io.github.cocosip.dcm4che.imageio.codecs.core.spi.AbstractDicomImageReaderSpi;

public final class JpegLsLosslessImageReaderSpi extends AbstractDicomImageReaderSpi {
    public JpegLsLosslessImageReaderSpi() {
        super("io.github.cocosip", "1.0", new String[] {"jpeg-ls-lossless", "JPEG-LS"},
                JpegLsLosslessImageReader.class,
                new String[] {JpegLsLosslessImageWriterSpi.class.getName()});
    }

    @Override protected JpegLsLosslessImageReader newReader() {
        return new JpegLsLosslessImageReader(this);
    }

    @Override public String getDescription(Locale locale) {
        return "Pure Java DICOM JPEG-LS Lossless image reader";
    }
}
