package io.github.cocosip.dcm4che.imageio.codecs.core.spi;

import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import javax.imageio.IIOImage;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageWriteParam;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.spi.ImageWriterSpi;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.imageio.codec.BytesWithImageImageDescriptor;
import org.dcm4che3.imageio.codec.ImageDescriptor;
import org.junit.jupiter.api.Test;

import io.github.cocosip.dcm4che.imageio.codecs.core.image.DicomImageTypes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbstractDicomImageIoTest {

    @Test
    void readerUsesOneImagePerFrameAndDelegatesRead() throws IOException {
        ImageDescriptor descriptor = descriptor();
        TestReader reader = new TestReader(null);
        DescriptorInputStream input = new DescriptorInputStream(descriptor);
        reader.setInput(input);

        assertEquals(1, reader.getNumImages(true));
        assertEquals(3, reader.getWidth(0));
        assertEquals(2, reader.getHeight(0));
        assertEquals(1, reader.getImageTypes(0).next().getNumBands());
        assertNull(reader.getStreamMetadata());
        assertNull(reader.getImageMetadata(0));
        assertThrows(IndexOutOfBoundsException.class, () -> reader.getWidth(1));
        assertThrows(IndexOutOfBoundsException.class, () -> reader.read(1));

        ImageReadParam param = reader.getDefaultReadParam();
        BufferedImage image = reader.read(0, param);
        assertSame(param, reader.lastParam);
        assertEquals(3, image.getWidth());

        reader.reset();
        assertThrows(IllegalStateException.class, () -> reader.getWidth(0));
    }

    @Test
    void writerRequiresDescriptorOutputAndDelegatesWrite() throws IOException {
        ImageDescriptor descriptor = descriptor();
        TestWriter writer = new TestWriter(null);
        DescriptorOutputStream output = new DescriptorOutputStream(descriptor);
        BufferedImage image = DicomImageTypes.createImage(descriptor);
        writer.setOutput(output);

        writer.write(null, new IIOImage(image, null, null), writer.getDefaultWriteParam());

        assertSame(image, writer.lastImage);
        assertNull(writer.getDefaultStreamMetadata(writer.getDefaultWriteParam()));
        assertNull(writer.getDefaultImageMetadata(
                DicomImageTypes.createType(descriptor), writer.getDefaultWriteParam()));
        assertThrows(IllegalArgumentException.class,
                () -> writer.setOutput(new Object()));

        writer.reset();
        assertThrows(IllegalStateException.class,
                () -> writer.write(null, new IIOImage(image, null, null), null));
    }

    @Test
    void spiBaseClassesCreateConfiguredInstances() throws IOException {
        TestReaderSpi readerSpi = new TestReaderSpi();
        TestWriterSpi writerSpi = new TestWriterSpi();

        assertInstanceOf(TestReader.class, readerSpi.createReaderInstance());
        assertInstanceOf(TestWriter.class, writerSpi.createWriterInstance());
        assertEquals(false, readerSpi.canDecodeInput(new Object()));
        assertEquals(false, readerSpi.canDecodeInput(new DescriptorOnly(descriptor())));
        assertTrue(readerSpi.canDecodeInput(new DescriptorInputStream(descriptor())));
        assertEquals(true, writerSpi.canEncodeImage(DicomImageTypes.createType(descriptor())));
    }

    private static ImageDescriptor descriptor() {
        Attributes attributes = new Attributes();
        attributes.setInt(Tag.Rows, VR.US, 2);
        attributes.setInt(Tag.Columns, VR.US, 3);
        attributes.setInt(Tag.SamplesPerPixel, VR.US, 1);
        attributes.setInt(Tag.BitsAllocated, VR.US, 8);
        attributes.setInt(Tag.BitsStored, VR.US, 8);
        attributes.setString(Tag.PhotometricInterpretation, VR.CS, "MONOCHROME2");
        return new ImageDescriptor(attributes);
    }

    private static final class TestReader extends AbstractDicomImageReader {
        private ImageReadParam lastParam;

        private TestReader(ImageReaderSpi provider) {
            super(provider);
        }

        @Override
        protected BufferedImage readFrame(ImageDescriptor descriptor,
                ImageInputStream input, ImageReadParam param) {
            lastParam = param;
            return DicomImageTypes.createImage(descriptor);
        }
    }

    private static final class TestWriter extends AbstractDicomImageWriter {
        private RenderedImage lastImage;

        private TestWriter(ImageWriterSpi provider) {
            super(provider);
        }

        @Override
        protected void writeFrame(ImageDescriptor descriptor, RenderedImage image,
                ImageOutputStream output, ImageWriteParam param) {
            lastImage = image;
        }
    }

    private static final class TestReaderSpi extends AbstractDicomImageReaderSpi {
        private TestReaderSpi() {
            super("test", "1", new String[] { "test-dicom" }, TestReader.class, null);
        }

        @Override
        protected TestReader newReader() {
            return new TestReader(this);
        }

        @Override
        public String getDescription(java.util.Locale locale) {
            return "test reader";
        }
    }

    private static final class TestWriterSpi extends AbstractDicomImageWriterSpi {
        private TestWriterSpi() {
            super("test", "1", new String[] { "test-dicom" }, TestWriter.class, null);
        }

        @Override
        protected TestWriter newWriter() {
            return new TestWriter(this);
        }

        @Override
        public String getDescription(java.util.Locale locale) {
            return "test writer";
        }
    }

    private static final class DescriptorInputStream extends MemoryCacheImageInputStream
            implements BytesWithImageImageDescriptor {
        private final ImageDescriptor descriptor;

        private DescriptorInputStream(ImageDescriptor descriptor) {
            super(new ByteArrayInputStream(new byte[0]));
            this.descriptor = descriptor;
        }

        @Override
        public ByteBuffer getBytes() {
            return ByteBuffer.allocate(0);
        }

        @Override
        public ImageDescriptor getImageDescriptor() {
            return descriptor;
        }
    }

    private static final class DescriptorOnly implements BytesWithImageImageDescriptor {
        private final ImageDescriptor descriptor;

        private DescriptorOnly(ImageDescriptor descriptor) {
            this.descriptor = descriptor;
        }

        @Override
        public ByteBuffer getBytes() {
            return ByteBuffer.allocate(0);
        }

        @Override
        public ImageDescriptor getImageDescriptor() {
            return descriptor;
        }
    }

    private static final class DescriptorOutputStream extends MemoryCacheImageOutputStream
            implements BytesWithImageImageDescriptor {
        private final ImageDescriptor descriptor;

        private DescriptorOutputStream(ImageDescriptor descriptor) {
            super(new ByteArrayOutputStream());
            this.descriptor = descriptor;
        }

        @Override
        public ByteBuffer getBytes() {
            return ByteBuffer.allocate(0);
        }

        @Override
        public ImageDescriptor getImageDescriptor() {
            return descriptor;
        }
    }
}
