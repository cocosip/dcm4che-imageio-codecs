package io.github.cocosip.dcm4che.imageio.codecs.core.spi;

import java.awt.image.RenderedImage;
import java.io.IOException;

import javax.imageio.IIOImage;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.spi.ImageWriterSpi;
import javax.imageio.stream.ImageOutputStream;

import org.dcm4che3.imageio.codec.ImageDescriptor;

import io.github.cocosip.dcm4che.imageio.codecs.core.stream.DicomImageStreams;

/** Common ImageIO lifecycle for codecs that write one compressed DICOM frame. */
public abstract class AbstractDicomImageWriter extends ImageWriter {
    private ImageOutputStream imageOutput;
    private ImageDescriptor descriptor;

    protected AbstractDicomImageWriter(ImageWriterSpi provider) {
        super(provider);
    }

    @Override
    public void setOutput(Object output) {
        if (output == null) {
            super.setOutput(null);
            imageOutput = null;
            descriptor = null;
            return;
        }
        if (!(output instanceof ImageOutputStream)) {
            throw new IllegalArgumentException("output is not an ImageOutputStream");
        }

        ImageDescriptor nextDescriptor = DicomImageStreams.requireDescriptor(output);
        super.setOutput(output);
        imageOutput = (ImageOutputStream) output;
        descriptor = nextDescriptor;
    }

    @Override
    public IIOMetadata getDefaultStreamMetadata(ImageWriteParam param) {
        return null;
    }

    @Override
    public IIOMetadata getDefaultImageMetadata(
            ImageTypeSpecifier imageType, ImageWriteParam param) {
        return null;
    }

    @Override
    public IIOMetadata convertStreamMetadata(IIOMetadata metadata, ImageWriteParam param) {
        return null;
    }

    @Override
    public IIOMetadata convertImageMetadata(
            IIOMetadata metadata, ImageTypeSpecifier imageType, ImageWriteParam param) {
        return null;
    }

    @Override
    public void write(IIOMetadata streamMetadata, IIOImage image, ImageWriteParam param)
            throws IOException {
        requireOutput();
        if (image == null) {
            throw new IllegalArgumentException("image is null");
        }
        RenderedImage renderedImage = image.getRenderedImage();
        if (renderedImage == null) {
            throw new IllegalArgumentException("raster-only images are not supported");
        }
        writeFrame(descriptor, renderedImage, imageOutput, param);
    }

    protected abstract void writeFrame(
            ImageDescriptor descriptor, RenderedImage image,
            ImageOutputStream output, ImageWriteParam param) throws IOException;

    private void requireOutput() {
        if (imageOutput == null || descriptor == null) {
            throw new IllegalStateException("output is not set");
        }
    }
}
