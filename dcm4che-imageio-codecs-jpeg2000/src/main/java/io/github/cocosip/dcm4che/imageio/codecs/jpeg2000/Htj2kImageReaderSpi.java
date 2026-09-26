package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000;

import java.io.IOException;
import java.util.Locale;

import javax.imageio.stream.ImageInputStream;

import io.github.cocosip.dcm4che.imageio.codecs.core.spi.AbstractDicomImageReaderSpi;

abstract class Htj2kImageReaderSpi extends AbstractDicomImageReaderSpi {
    Htj2kImageReaderSpi(String name, Class<? extends Htj2kImageReader> reader,
            Class<? extends Htj2kImageWriterSpi> writer) {
        super("io.github.cocosip", "1.0", new String[] {name}, reader,
                new String[] {writer.getName()});
    }

    @Override
    public boolean canDecodeInput(Object source) throws IOException {
        return super.canDecodeInput(source)
                && Jpeg2000LosslessImageReaderSpi.hasHtCodestreamSignature(
                        (ImageInputStream) source);
    }

    @Override
    public String getDescription(Locale locale) {
        return "Pure Java DICOM HTJ2K reader";
    }
}
