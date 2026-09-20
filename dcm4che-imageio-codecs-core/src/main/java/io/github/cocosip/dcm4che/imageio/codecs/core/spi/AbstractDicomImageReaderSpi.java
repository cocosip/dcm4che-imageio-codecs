package io.github.cocosip.dcm4che.imageio.codecs.core.spi;

import java.io.IOException;

import javax.imageio.ImageReader;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.ImageInputStream;

import io.github.cocosip.dcm4che.imageio.codecs.core.stream.DicomImageStreams;

/** ImageReader SPI boilerplate shared by DICOM frame codecs. */
public abstract class AbstractDicomImageReaderSpi extends ImageReaderSpi {

    protected AbstractDicomImageReaderSpi(
            String vendorName, String version, String[] names,
            Class<? extends ImageReader> readerClass, String[] writerSpiNames) {
        super(vendorName, version, names, null, null, readerClass.getName(),
                new Class<?>[] { ImageInputStream.class }, writerSpiNames,
                false, null, null, null, null,
                false, null, null, null, null);
    }

    @Override
    public boolean canDecodeInput(Object source) throws IOException {
        if (!(source instanceof ImageInputStream)) {
            return false;
        }
        try {
            DicomImageStreams.requireDescriptor(source);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public final ImageReader createReaderInstance(Object extension) throws IOException {
        return newReader();
    }

    protected abstract ImageReader newReader();
}
