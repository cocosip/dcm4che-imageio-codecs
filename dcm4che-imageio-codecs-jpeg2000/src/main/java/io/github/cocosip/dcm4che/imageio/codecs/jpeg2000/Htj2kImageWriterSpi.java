package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000;

import java.util.Locale;

import io.github.cocosip.dcm4che.imageio.codecs.core.spi.AbstractDicomImageWriterSpi;

abstract class Htj2kImageWriterSpi extends AbstractDicomImageWriterSpi {
    Htj2kImageWriterSpi(String name, Class<? extends Htj2kImageWriter> writer,
            Class<? extends Htj2kImageReaderSpi> reader) {
        super("io.github.cocosip", "1.0", new String[] {name}, writer,
                new String[] {reader.getName()});
    }

    @Override
    public String getDescription(Locale locale) {
        return "Pure Java DICOM HTJ2K writer";
    }
}
