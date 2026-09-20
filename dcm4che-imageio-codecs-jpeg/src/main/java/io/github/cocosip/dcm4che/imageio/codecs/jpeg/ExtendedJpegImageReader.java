package io.github.cocosip.dcm4che.imageio.codecs.jpeg;

import java.io.IOException;

import javax.imageio.IIOException;
import javax.imageio.ImageReadParam;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.ImageInputStream;

import org.dcm4che3.imageio.codec.ImageDescriptor;

import io.github.cocosip.dcm4che.imageio.codecs.core.spi.AbstractDicomImageReader;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg.internal.ExtendedJpegCodec;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg.internal.JpegFrame;

public final class ExtendedJpegImageReader extends AbstractDicomImageReader {
    public ExtendedJpegImageReader(ImageReaderSpi provider) {
        super(provider);
    }

    @Override
    protected java.awt.image.BufferedImage readFrame(ImageDescriptor descriptor,
            ImageInputStream input, ImageReadParam param) throws IOException {
        byte[] encoded = JpegImageReader.readRemaining(input);
        try {
            JpegFrame frame = ExtendedJpegCodec.decode(encoded);
            return JpegRasterFrames.toImage(descriptor, frame, param, true);
        } catch (IIOException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IIOException("Invalid JPEG Extended frame", e);
        }
    }
}
