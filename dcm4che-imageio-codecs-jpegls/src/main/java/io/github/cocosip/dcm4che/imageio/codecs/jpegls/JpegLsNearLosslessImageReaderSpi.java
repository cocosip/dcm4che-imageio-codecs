package io.github.cocosip.dcm4che.imageio.codecs.jpegls;

import java.util.Locale;

import io.github.cocosip.dcm4che.imageio.codecs.core.spi.AbstractDicomImageReaderSpi;

public final class JpegLsNearLosslessImageReaderSpi extends AbstractDicomImageReaderSpi {
    public JpegLsNearLosslessImageReaderSpi() {
        super("io.github.cocosip", "1.0",
                new String[] {"jpeg-ls-near-lossless", "JPEG-LS-Near-Lossless"},
                JpegLsNearLosslessImageReader.class,
                new String[] {JpegLsNearLosslessImageWriterSpi.class.getName()});
    }

    @Override protected JpegLsNearLosslessImageReader newReader() {
        return new JpegLsNearLosslessImageReader(this);
    }

    @Override public String getDescription(Locale locale) {
        return "Pure Java DICOM JPEG-LS Near-Lossless image reader";
    }
}
