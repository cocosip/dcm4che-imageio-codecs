package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000;

import java.awt.image.RenderedImage;
import java.io.IOException;

import javax.imageio.IIOException;
import javax.imageio.ImageWriteParam;
import javax.imageio.spi.ImageWriterSpi;
import javax.imageio.stream.ImageOutputStream;

import org.dcm4che3.imageio.codec.ImageDescriptor;

import io.github.cocosip.dcm4che.imageio.codecs.core.spi.AbstractDicomImageWriter;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.classic.Jpeg2000LosslessCodec;

public final class Jpeg2000LosslessImageWriter extends AbstractDicomImageWriter {
    public Jpeg2000LosslessImageWriter(ImageWriterSpi provider) {
        super(provider);
    }

    @Override
    protected void writeFrame(ImageDescriptor descriptor, RenderedImage image,
            ImageOutputStream output, ImageWriteParam param) throws IOException {
        if (param != null && param.canWriteCompressed()
                && param.getCompressionMode() == ImageWriteParam.MODE_DISABLED) {
            throw new IIOException("JPEG 2000 compression cannot be disabled");
        }
        output.write(Jpeg2000LosslessCodec.encode(Jpeg2000RasterFrames.fromImage(descriptor, image)));
    }
}
