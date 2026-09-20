package io.github.cocosip.dcm4che.imageio.codecs.core.spi;

import java.awt.image.DataBuffer;
import java.io.IOException;

import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriter;
import javax.imageio.spi.ImageWriterSpi;
import javax.imageio.stream.ImageOutputStream;

/** ImageWriter SPI boilerplate shared by DICOM frame codecs. */
public abstract class AbstractDicomImageWriterSpi extends ImageWriterSpi {

    protected AbstractDicomImageWriterSpi(
            String vendorName, String version, String[] names,
            Class<? extends ImageWriter> writerClass, String[] readerSpiNames) {
        super(vendorName, version, names, null, null, writerClass.getName(),
                new Class<?>[] { ImageOutputStream.class }, readerSpiNames,
                false, null, null, null, null,
                false, null, null, null, null);
    }

    @Override
    public boolean canEncodeImage(ImageTypeSpecifier type) {
        if (type == null) {
            throw new IllegalArgumentException("type is null");
        }
        int bands = type.getNumBands();
        int dataType = type.getSampleModel().getDataType();
        return (bands == 1 || bands == 3)
                && (dataType == DataBuffer.TYPE_BYTE
                || dataType == DataBuffer.TYPE_SHORT
                || dataType == DataBuffer.TYPE_USHORT);
    }

    @Override
    public final ImageWriter createWriterInstance(Object extension) throws IOException {
        return newWriter();
    }

    protected abstract ImageWriter newWriter();
}
