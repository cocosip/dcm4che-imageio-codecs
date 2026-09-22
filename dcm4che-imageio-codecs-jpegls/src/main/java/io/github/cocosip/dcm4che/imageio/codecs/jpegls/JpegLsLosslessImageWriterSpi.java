package io.github.cocosip.dcm4che.imageio.codecs.jpegls;

import java.util.Locale;

import io.github.cocosip.dcm4che.imageio.codecs.core.spi.AbstractDicomImageWriterSpi;

public final class JpegLsLosslessImageWriterSpi extends AbstractDicomImageWriterSpi {
    public JpegLsLosslessImageWriterSpi() {
        super("io.github.cocosip", "1.0", new String[] {"jpeg-ls-lossless", "JPEG-LS"},
                JpegLsLosslessImageWriter.class,
                new String[] {JpegLsLosslessImageReaderSpi.class.getName()});
    }

    @Override public boolean isFormatLossless() { return true; }

    @Override protected JpegLsLosslessImageWriter newWriter() {
        return new JpegLsLosslessImageWriter(this);
    }

    @Override public String getDescription(Locale locale) {
        return "Pure Java DICOM JPEG-LS Lossless image writer";
    }
}
