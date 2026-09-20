package io.github.cocosip.dcm4che.imageio.codecs.jpeg;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.IIOException;
import javax.imageio.ImageReadParam;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.ImageInputStream;

import org.dcm4che3.imageio.codec.ImageDescriptor;

import io.github.cocosip.dcm4che.imageio.codecs.core.spi.AbstractDicomImageReader;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg.internal.BaselineJpegCodec;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg.internal.JpegFrame;

public final class JpegImageReader extends AbstractDicomImageReader {
    public JpegImageReader(ImageReaderSpi provider) {
        super(provider);
    }

    @Override
    protected java.awt.image.BufferedImage readFrame(ImageDescriptor descriptor,
            ImageInputStream input, ImageReadParam param) throws IOException {
        byte[] encoded = readRemaining(input);
        try {
            JpegFrame frame = BaselineJpegCodec.decode(encoded);
            return JpegRasterFrames.toImage(descriptor, frame, param);
        } catch (IIOException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IIOException("Invalid JPEG Baseline frame", e);
        }
    }

    private static byte[] readRemaining(ImageInputStream input) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) >= 0) {
            if (read == 0) {
                int value = input.read();
                if (value < 0) {
                    break;
                }
                bytes.write(value);
            } else {
                bytes.write(buffer, 0, read);
            }
        }
        return bytes.toByteArray();
    }
}
