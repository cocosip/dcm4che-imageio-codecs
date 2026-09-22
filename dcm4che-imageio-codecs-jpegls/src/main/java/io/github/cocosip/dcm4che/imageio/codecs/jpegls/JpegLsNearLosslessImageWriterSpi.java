package io.github.cocosip.dcm4che.imageio.codecs.jpegls;

import java.util.Locale;

import io.github.cocosip.dcm4che.imageio.codecs.core.spi.AbstractDicomImageWriterSpi;

public final class JpegLsNearLosslessImageWriterSpi extends AbstractDicomImageWriterSpi {
    public JpegLsNearLosslessImageWriterSpi() {
        super("io.github.cocosip", "1.0",
                new String[] {"jpeg-ls-near-lossless", "JPEG-LS-Near-Lossless"},
                JpegLsNearLosslessImageWriter.class,
                new String[] {JpegLsNearLosslessImageReaderSpi.class.getName()});
    }

    @Override public boolean isFormatLossless() { return false; }

    @Override protected JpegLsNearLosslessImageWriter newWriter() {
        return new JpegLsNearLosslessImageWriter(this);
    }

    @Override public String getDescription(Locale locale) {
        return "Pure Java DICOM JPEG-LS Near-Lossless image writer";
    }
}
