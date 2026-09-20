package io.github.cocosip.dcm4che.imageio.codecs.jpeg;

import java.io.IOException;

import javax.imageio.IIOException;
import javax.imageio.ImageReadParam;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.ImageInputStream;

import org.dcm4che3.imageio.codec.ImageDescriptor;

import io.github.cocosip.dcm4che.imageio.codecs.core.spi.AbstractDicomImageReader;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg.internal.JpegFrame;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg.internal.LosslessJpegCodec;

public final class LosslessJpegImageReader extends AbstractDicomImageReader {
    public LosslessJpegImageReader(ImageReaderSpi provider) {
        super(provider);
    }

    @Override
    protected java.awt.image.BufferedImage readFrame(ImageDescriptor descriptor,
            ImageInputStream input, ImageReadParam param) throws IOException {
        byte[] encoded = JpegImageReader.readRemaining(input);
        try {
            JpegFrame frame = LosslessJpegCodec.decode(encoded);
            return JpegRasterFrames.toImage(descriptor, frame, param,
                    JpegRasterFrames.Flavor.LOSSLESS);
        } catch (IIOException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IIOException("Invalid JPEG Lossless frame", e);
        }
    }
}
