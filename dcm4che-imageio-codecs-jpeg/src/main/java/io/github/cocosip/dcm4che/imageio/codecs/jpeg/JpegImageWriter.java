package io.github.cocosip.dcm4che.imageio.codecs.jpeg;

import java.awt.image.RenderedImage;
import java.io.IOException;

import javax.imageio.ImageWriteParam;
import javax.imageio.IIOException;
import javax.imageio.spi.ImageWriterSpi;
import javax.imageio.stream.ImageOutputStream;

import org.dcm4che3.imageio.codec.ImageDescriptor;

import io.github.cocosip.dcm4che.imageio.codecs.core.spi.AbstractDicomImageWriter;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg.internal.BaselineJpegCodec;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg.internal.JpegFrame;

public final class JpegImageWriter extends AbstractDicomImageWriter {
    public JpegImageWriter(ImageWriterSpi provider) {
        super(provider);
    }

    @Override
    protected void writeFrame(ImageDescriptor descriptor, RenderedImage image,
            ImageOutputStream output, ImageWriteParam param) throws IOException {
        if (param != null && param.canWriteCompressed() && param.getCompressionMode()
                == ImageWriteParam.MODE_EXPLICIT) {
            throw new IIOException("JPEG Baseline quality controls are not implemented");
        }
        JpegFrame frame = JpegRasterFrames.fromImage(descriptor, image);
        output.write(BaselineJpegCodec.encode(frame));
    }
}
