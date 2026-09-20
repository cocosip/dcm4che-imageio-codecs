package io.github.cocosip.dcm4che.imageio.codecs.rle;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferShort;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import javax.imageio.IIOException;
import javax.imageio.IIOImage;
import javax.imageio.ImageReadParam;
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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RleImageIoTest {

    @Test
    void roundTripsMonochrome8AndReusesReadDestination() throws Exception {
        ImageDescriptor descriptor = descriptor(2, 3, 1, 8, false, false);
        BufferedImage source = DicomImageTypes.createImage(descriptor);
        fillSamples(source, new int[] { 1, 2, 3, 4, 5, 6 });
        byte[] encoded = write(descriptor, source);

        RleImageReader reader = reader(descriptor, encoded);
        BufferedImage destination = DicomImageTypes.createImage(descriptor);
        ImageReadParam param = reader.getDefaultReadParam();
        param.setDestination(destination);

        assertSame(destination, reader.read(0, param));
        assertSamples(destination, new int[] { 1, 2, 3, 4, 5, 6 });
    }

    @Test
    void preservesSigned16RawBits() throws Exception {
        ImageDescriptor descriptor = descriptor(1, 3, 1, 16, true, false);
        BufferedImage source = DicomImageTypes.createImage(descriptor);
        short[] sourceData = ((DataBufferShort) source.getRaster().getDataBuffer()).getData();
        sourceData[0] = (short) 0x8001;
        sourceData[1] = (short) 0xffff;
        sourceData[2] = (short) 0x1234;

        BufferedImage decoded = reader(descriptor, write(descriptor, source)).read(0);
        short[] decodedData = ((DataBufferShort) decoded.getRaster().getDataBuffer()).getData();

        assertEquals(sourceData[0], decodedData[0]);
        assertEquals(sourceData[1], decodedData[1]);
        assertEquals(sourceData[2], decodedData[2]);
    }

    @Test
    void roundTripsRgbInterleavedAndPlanarImages() throws Exception {
        int[] samples = { 1, 2, 3, 4, 5, 6 };
        ImageDescriptor interleaved = descriptor(1, 2, 3, 8, false, false);
        ImageDescriptor planar = descriptor(1, 2, 3, 8, false, true);

        assertImageRoundTrip(interleaved, samples);
        assertImageRoundTrip(planar, samples);
    }

    @Test
    void rejectsDescriptorAndImageMismatches() throws Exception {
        ImageDescriptor descriptor = descriptor(2, 2, 1, 8, false, false);
        BufferedImage wrongSize = DicomImageTypes.createImage(
                descriptor(1, 2, 1, 8, false, false));
        assertThrows(IIOException.class, () -> write(descriptor, wrongSize));

        BufferedImage source = DicomImageTypes.createImage(descriptor);
        RleImageReader reader = reader(descriptor, write(descriptor, source));
        ImageReadParam param = reader.getDefaultReadParam();
        param.setDestination(DicomImageTypes.createImage(
                descriptor(2, 2, 1, 16, false, false)));
        assertThrows(IIOException.class, () -> reader.read(0, param));
    }

    private static void assertImageRoundTrip(ImageDescriptor descriptor, int[] samples)
            throws Exception {
        BufferedImage source = DicomImageTypes.createImage(descriptor);
        fillSamples(source, samples);
        BufferedImage decoded = reader(descriptor, write(descriptor, source)).read(0);
        assertSamples(decoded, samples);
    }

    private static byte[] write(ImageDescriptor descriptor, BufferedImage source)
            throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DescriptorOutputStream output = new DescriptorOutputStream(bytes, descriptor);
        RleImageWriter writer = new RleImageWriter(null);
        writer.setOutput(output);
        writer.write(null, new IIOImage(source, null, null), writer.getDefaultWriteParam());
        output.flush();
        return bytes.toByteArray();
    }

    private static RleImageReader reader(ImageDescriptor descriptor, byte[] encoded) {
        RleImageReader reader = new RleImageReader(null);
        reader.setInput(new DescriptorInputStream(encoded, descriptor));
        return reader;
    }

    private static void fillSamples(BufferedImage image, int[] samples) {
        int index = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                for (int band = 0; band < image.getRaster().getNumBands(); band++) {
                    image.getRaster().setSample(x, y, band, samples[index++]);
                }
            }
        }
    }

    private static void assertSamples(BufferedImage image, int[] expected) {
        int index = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                for (int band = 0; band < image.getRaster().getNumBands(); band++) {
                    assertEquals(expected[index++], image.getRaster().getSample(x, y, band));
                }
            }
        }
    }

    private static ImageDescriptor descriptor(int rows, int columns, int samples,
            int bitsAllocated, boolean signed, boolean planar) {
        Attributes attributes = new Attributes();
        attributes.setInt(Tag.Rows, VR.US, rows);
        attributes.setInt(Tag.Columns, VR.US, columns);
        attributes.setInt(Tag.SamplesPerPixel, VR.US, samples);
        attributes.setInt(Tag.BitsAllocated, VR.US, bitsAllocated);
        attributes.setInt(Tag.BitsStored, VR.US, bitsAllocated);
        attributes.setInt(Tag.PixelRepresentation, VR.US, signed ? 1 : 0);
        attributes.setInt(Tag.PlanarConfiguration, VR.US, planar ? 1 : 0);
        attributes.setString(Tag.PhotometricInterpretation, VR.CS,
                samples == 1 ? "MONOCHROME2" : "RGB");
        return new ImageDescriptor(attributes);
    }

    private static final class DescriptorInputStream extends MemoryCacheImageInputStream
            implements BytesWithImageImageDescriptor {
        private final byte[] bytes;
        private final ImageDescriptor descriptor;

        private DescriptorInputStream(byte[] bytes, ImageDescriptor descriptor) {
            super(new ByteArrayInputStream(bytes));
            this.bytes = bytes;
            this.descriptor = descriptor;
        }

        @Override
        public ByteBuffer getBytes() {
            return ByteBuffer.wrap(bytes).asReadOnlyBuffer();
        }

        @Override
        public ImageDescriptor getImageDescriptor() {
            return descriptor;
        }
    }

    private static final class DescriptorOutputStream extends MemoryCacheImageOutputStream
            implements BytesWithImageImageDescriptor {
        private final ImageDescriptor descriptor;

        private DescriptorOutputStream(ByteArrayOutputStream output, ImageDescriptor descriptor) {
            super(output);
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
