package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageReadParam;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.ImageInputStream;

import org.dcm4che3.imageio.codec.ImageDescriptor;

import io.github.cocosip.dcm4che.imageio.codecs.core.spi.AbstractDicomImageReader;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.classic.Jpeg2000LosslessCodec;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Limits;

public class Jpeg2000LosslessImageReader extends AbstractDicomImageReader {
    public Jpeg2000LosslessImageReader(ImageReaderSpi provider) {
        super(provider);
    }

    @Override
    protected BufferedImage readFrame(ImageDescriptor descriptor,
            ImageInputStream input, ImageReadParam param) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Jpeg2000Limits limits = Jpeg2000Limits.defaults();
        byte[] buffer = new byte[8192];
        int length;
        while ((length = input.read(buffer)) >= 0) {
            if (length == 0) {
                int one = input.read();
                if (one < 0) {
                    break;
                }
                limits.requireFrameLength((long) bytes.size() + 1);
                bytes.write(one);
            } else {
                limits.requireFrameLength((long) bytes.size() + length);
                bytes.write(buffer, 0, length);
            }
        }
        return Jpeg2000RasterFrames.toImage(descriptor,
                Jpeg2000LosslessCodec.decode(bytes.toByteArray(), requireReversibleTransform()), param);
    }

    protected boolean requireReversibleTransform() {
        return true;
    }
}
