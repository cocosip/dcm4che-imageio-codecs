package io.github.cocosip.dcm4che.imageio.codecs.core.spi;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;

import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.ImageInputStream;

import org.dcm4che3.imageio.codec.ImageDescriptor;

import io.github.cocosip.dcm4che.imageio.codecs.core.image.DicomImageTypes;
import io.github.cocosip.dcm4che.imageio.codecs.core.stream.DicomImageStreams;

/** Common ImageIO lifecycle for codecs that read one compressed DICOM frame. */
public abstract class AbstractDicomImageReader extends ImageReader {
    private ImageInputStream imageInput;
    private ImageDescriptor descriptor;

    protected AbstractDicomImageReader(ImageReaderSpi provider) {
        super(provider);
    }

    @Override
    public void setInput(Object input, boolean seekForwardOnly, boolean ignoreMetadata) {
        if (input == null) {
            super.setInput(null, seekForwardOnly, ignoreMetadata);
            imageInput = null;
            descriptor = null;
            return;
        }
        if (!(input instanceof ImageInputStream)) {
            throw new IllegalArgumentException("input is not an ImageInputStream");
        }

        ImageDescriptor nextDescriptor = DicomImageStreams.requireDescriptor(input);
        super.setInput(input, seekForwardOnly, ignoreMetadata);
        imageInput = (ImageInputStream) input;
        descriptor = nextDescriptor;
    }

    @Override
    public int getNumImages(boolean allowSearch) {
        requireInput();
        return 1;
    }

    @Override
    public int getWidth(int imageIndex) {
        return descriptor(imageIndex).getColumns();
    }

    @Override
    public int getHeight(int imageIndex) {
        return descriptor(imageIndex).getRows();
    }

    @Override
    public ImageTypeSpecifier getRawImageType(int imageIndex) {
        return DicomImageTypes.createType(descriptor(imageIndex));
    }

    @Override
    public Iterator<ImageTypeSpecifier> getImageTypes(int imageIndex) {
        return Collections.singleton(getRawImageType(imageIndex)).iterator();
    }

    @Override
    public IIOMetadata getStreamMetadata() {
        return null;
    }

    @Override
    public IIOMetadata getImageMetadata(int imageIndex) {
        descriptor(imageIndex);
        return null;
    }

    @Override
    public BufferedImage read(int imageIndex, ImageReadParam param) throws IOException {
        ImageDescriptor currentDescriptor = descriptor(imageIndex);
        return readFrame(currentDescriptor, imageInput, param);
    }

    protected abstract BufferedImage readFrame(
            ImageDescriptor descriptor, ImageInputStream input, ImageReadParam param)
            throws IOException;

    private ImageDescriptor descriptor(int imageIndex) {
        requireInput();
        if (imageIndex != 0) {
            throw new IndexOutOfBoundsException("imageIndex must be 0: " + imageIndex);
        }
        return descriptor;
    }

    private void requireInput() {
        if (imageInput == null || descriptor == null) {
            throw new IllegalStateException("input is not set");
        }
    }
}
