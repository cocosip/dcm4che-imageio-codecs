package io.github.cocosip.dcm4che.imageio.codecs.rle;

import java.awt.image.RenderedImage;
import java.io.IOException;

import javax.imageio.ImageWriteParam;
import javax.imageio.spi.ImageWriterSpi;
import javax.imageio.stream.ImageOutputStream;

import org.dcm4che3.imageio.codec.ImageDescriptor;

import io.github.cocosip.dcm4che.imageio.codecs.core.spi.AbstractDicomImageWriter;
import io.github.cocosip.dcm4che.imageio.codecs.rle.internal.RleFrameCodec;

public final class RleImageWriter extends AbstractDicomImageWriter {

    public RleImageWriter(ImageWriterSpi provider) {
        super(provider);
    }

    @Override
    protected void writeFrame(ImageDescriptor descriptor, RenderedImage image,
            ImageOutputStream output, ImageWriteParam param) throws IOException {
        byte[] raw = RleRasterFrames.toRawFrame(descriptor, image);
        output.write(RleFrameCodec.encode(descriptor, raw));
    }
}
