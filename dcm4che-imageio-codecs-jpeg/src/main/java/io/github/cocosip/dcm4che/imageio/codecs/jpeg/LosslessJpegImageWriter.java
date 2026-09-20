package io.github.cocosip.dcm4che.imageio.codecs.jpeg;

import java.awt.image.RenderedImage;
import java.io.IOException;

import javax.imageio.IIOException;
import javax.imageio.ImageWriteParam;
import javax.imageio.spi.ImageWriterSpi;
import javax.imageio.stream.ImageOutputStream;

import org.dcm4che3.imageio.codec.ImageDescriptor;

import io.github.cocosip.dcm4che.imageio.codecs.core.spi.AbstractDicomImageWriter;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg.internal.JpegFrame;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg.internal.LosslessJpegCodec;

public class LosslessJpegImageWriter extends AbstractDicomImageWriter {
    public LosslessJpegImageWriter(ImageWriterSpi provider) {
        super(provider);
    }

    @Override
    public ImageWriteParam getDefaultWriteParam() {
        return new JpegImageWriteParam(getLocale());
    }

    @Override
    protected void writeFrame(ImageDescriptor descriptor, RenderedImage image,
            ImageOutputStream output, ImageWriteParam param) throws IOException {
        if (param != null && !(param instanceof JpegImageWriteParam)
                && param.canWriteCompressed()
                && param.getCompressionMode() == ImageWriteParam.MODE_EXPLICIT) {
            throw new IIOException("JPEG Lossless compression controls are not implemented");
        }
        int restartInterval = param instanceof JpegImageWriteParam
                ? ((JpegImageWriteParam) param).getRestartInterval() : 0;
        int pointTransform = param instanceof JpegImageWriteParam
                ? ((JpegImageWriteParam) param).getPointTransform() : 0;
        JpegFrame frame = JpegRasterFrames.fromImage(descriptor, image,
                JpegRasterFrames.Flavor.LOSSLESS);
        output.write(LosslessJpegCodec.encode(frame, 1, restartInterval, pointTransform));
    }
}
