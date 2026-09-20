package io.github.cocosip.dcm4che.imageio.codecs.jpeg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
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

class JpegImageIoTest {
    @Test
    void roundTripsMonochromeThroughDescriptorBackedImageIo() throws Exception {
        ImageDescriptor descriptor = descriptor(9, 10, 1, "MONOCHROME2");
        BufferedImage source = DicomImageTypes.createImage(descriptor);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                source.getRaster().setSample(x, y, 0, (x * 19 + y * 7) & 0xff);
            }
        }

        byte[] encoded = write(descriptor, source);
        BufferedImage decoded = read(descriptor, encoded);

        assertEquals(source.getWidth(), decoded.getWidth());
        assertEquals(source.getHeight(), decoded.getHeight());
        assertJpegTolerance(source, decoded, 80);
        assertInstanceOf(BufferedImage.class, ImageIO.read(new ByteArrayInputStream(encoded)));
    }

    @Test
    void invertsMonochrome1AtTheImageBoundary() throws Exception {
        ImageDescriptor descriptor = descriptor(1, 1, 1, "MONOCHROME1");
        BufferedImage source = DicomImageTypes.createImage(descriptor);
        source.getRaster().setSample(0, 0, 0, 32);

        BufferedImage decoded = read(descriptor, write(descriptor, source));

        assertEquals(32, decoded.getRaster().getSample(0, 0, 0), 1);
    }

    @Test
    void roundTripsRgbWithOneByOneSampling() throws Exception {
        ImageDescriptor descriptor = descriptor(9, 10, 3, "RGB");
        BufferedImage source = DicomImageTypes.createImage(descriptor);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                source.getRaster().setSample(x, y, 0, x * 7 + y * 3);
                source.getRaster().setSample(x, y, 1, x * 3 + y * 7);
                source.getRaster().setSample(x, y, 2, x * 5 + y * 5);
            }
        }

        byte[] encoded = write(descriptor, source);
        BufferedImage decoded = read(descriptor, encoded);

        assertJpegTolerance(source, decoded, 80);
        assertInstanceOf(BufferedImage.class, ImageIO.read(new ByteArrayInputStream(encoded)));
    }

    @Test
    void decodesJdkGeneratedBaselineGray() throws Exception {
        BufferedImage source = new BufferedImage(8, 8, BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                source.getRaster().setSample(x, y, 0, (x * 21 + y * 9) & 0xff);
            }
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(source, "JPEG", bytes);

        BufferedImage decoded = read(descriptor(8, 8, 1, "MONOCHROME2"), bytes.toByteArray());

        assertJpegTolerance(source, decoded, 50);
    }

    @Test
    void exposesPairedImageIoSpis() throws Exception {
        assertInstanceOf(JpegImageReader.class, new JpegImageReaderSpi().createReaderInstance());
        assertInstanceOf(JpegImageWriter.class, new JpegImageWriterSpi().createWriterInstance());
        assertInstanceOf(ExtendedJpegImageReader.class,
                new ExtendedJpegImageReaderSpi().createReaderInstance());
        assertInstanceOf(ExtendedJpegImageWriter.class,
                new ExtendedJpegImageWriterSpi().createWriterInstance());
        assertInstanceOf(LosslessJpegImageReader.class,
                new LosslessJpegImageReaderSpi().createReaderInstance());
        assertInstanceOf(LosslessJpegImageWriter.class,
                new LosslessJpegImageWriterSpi().createWriterInstance());
        assertInstanceOf(LosslessJpegSv1ImageReader.class,
                new LosslessJpegSv1ImageReaderSpi().createReaderInstance());
        assertInstanceOf(LosslessJpegSv1ImageWriter.class,
                new LosslessJpegSv1ImageWriterSpi().createWriterInstance());
    }

    @Test
    void roundTripsTwelveBitExtendedThroughDescriptorBackedImageIo() throws Exception {
        ImageDescriptor descriptor = descriptor(9, 10, 1, 16, 12, "MONOCHROME2");
        BufferedImage source = DicomImageTypes.createImage(descriptor);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                source.getRaster().setSample(x, y, 0, (x * 257 + y * 113) & 0xfff);
            }
        }

        byte[] encoded = writeExtended(descriptor, source);
        BufferedImage decoded = readExtended(descriptor, encoded);

        assertEquals(12, decoded.getColorModel().getComponentSize(0));
        assertJpegTolerance(source, decoded, 90);

        ImageDescriptor eightBit = descriptor(8, 8, 1, 8, 8, "MONOCHROME2");
        BufferedImage eightBitSource = DicomImageTypes.createImage(eightBit);
        byte[] eightBitEncoded = writeExtended(eightBit, eightBitSource);
        assertInstanceOf(BufferedImage.class,
                ImageIO.read(new ByteArrayInputStream(eightBitEncoded)));
    }

    @Test
    void rejectsUnsupportedReadParameters() throws Exception {
        ImageDescriptor descriptor = descriptor(1, 1, 1, "MONOCHROME2");
        JpegImageReader reader = new JpegImageReader(null);
        reader.setInput(new DescriptorInputStream(new byte[] {0}, descriptor));
        ImageReadParam param = reader.getDefaultReadParam();
        param.setSourceSubsampling(2, 1, 0, 0);
        assertThrows(Exception.class, () -> reader.read(0, param));
    }

    @Test
    void roundTripsSixteenBitLosslessThroughDescriptorBackedImageIo() throws Exception {
        ImageDescriptor descriptor = descriptor(4, 5, 1, 16, 16, "MONOCHROME2");
        BufferedImage source = DicomImageTypes.createImage(descriptor);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                source.getRaster().setSample(x, y, 0, (x * 12345 + y * 23456) & 0xffff);
            }
        }

        byte[] encoded = writeLossless(descriptor, source);
        BufferedImage decoded = readLossless(descriptor, encoded);

        assertEquals(16, decoded.getColorModel().getComponentSize(0));
        assertExactSamples(source, decoded);
    }

    @Test
    void roundTripsTwelveBitRgbLosslessThroughDescriptorBackedImageIo() throws Exception {
        ImageDescriptor descriptor = descriptor(4, 5, 3, 16, 12, "RGB");
        BufferedImage source = DicomImageTypes.createImage(descriptor);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                source.getRaster().setSample(x, y, 0, (x * 257 + y * 113) & 0xfff);
                source.getRaster().setSample(x, y, 1, (x * 97 + y * 211) & 0xfff);
                source.getRaster().setSample(x, y, 2, (x * 31 + y * 307) & 0xfff);
            }
        }

        BufferedImage decoded = readLossless(descriptor, writeLossless(descriptor, source));

        assertEquals(12, decoded.getColorModel().getComponentSize(0));
        assertExactSamples(source, decoded);
    }

    @Test
    void roundTripsSv1LosslessThroughDescriptorBackedImageIo() throws Exception {
        ImageDescriptor descriptor = descriptor(4, 5, 1, 8, 8, "MONOCHROME2");
        BufferedImage source = DicomImageTypes.createImage(descriptor);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                source.getRaster().setSample(x, y, 0, (x * 31 + y * 17) & 0xff);
            }
        }

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DescriptorOutputStream output = new DescriptorOutputStream(bytes, descriptor);
        LosslessJpegSv1ImageWriter writer = new LosslessJpegSv1ImageWriter(null);
        writer.setOutput(output);
        writer.write(null, new IIOImage(source, null, null), writer.getDefaultWriteParam());
        output.flush();

        LosslessJpegSv1ImageReader reader = new LosslessJpegSv1ImageReader(null);
        reader.setInput(new DescriptorInputStream(bytes.toByteArray(), descriptor));
        assertExactSamples(source, reader.read(0));
    }

    private static byte[] write(ImageDescriptor descriptor, BufferedImage image) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DescriptorOutputStream output = new DescriptorOutputStream(bytes, descriptor);
        JpegImageWriter writer = new JpegImageWriter(null);
        writer.setOutput(output);
        writer.write(null, new IIOImage(image, null, null), writer.getDefaultWriteParam());
        output.flush();
        return bytes.toByteArray();
    }

    private static BufferedImage read(ImageDescriptor descriptor, byte[] encoded) throws Exception {
        JpegImageReader reader = new JpegImageReader(null);
        reader.setInput(new DescriptorInputStream(encoded, descriptor));
        return reader.read(0);
    }

    private static byte[] writeExtended(ImageDescriptor descriptor, BufferedImage image)
            throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DescriptorOutputStream output = new DescriptorOutputStream(bytes, descriptor);
        ExtendedJpegImageWriter writer = new ExtendedJpegImageWriter(null);
        writer.setOutput(output);
        writer.write(null, new IIOImage(image, null, null), writer.getDefaultWriteParam());
        output.flush();
        return bytes.toByteArray();
    }

    private static BufferedImage readExtended(ImageDescriptor descriptor, byte[] encoded)
            throws Exception {
        ExtendedJpegImageReader reader = new ExtendedJpegImageReader(null);
        reader.setInput(new DescriptorInputStream(encoded, descriptor));
        return reader.read(0);
    }

    private static byte[] writeLossless(ImageDescriptor descriptor, BufferedImage image)
            throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DescriptorOutputStream output = new DescriptorOutputStream(bytes, descriptor);
        LosslessJpegImageWriter writer = new LosslessJpegImageWriter(null);
        writer.setOutput(output);
        writer.write(null, new IIOImage(image, null, null), writer.getDefaultWriteParam());
        output.flush();
        return bytes.toByteArray();
    }

    private static BufferedImage readLossless(ImageDescriptor descriptor, byte[] encoded)
            throws Exception {
        LosslessJpegImageReader reader = new LosslessJpegImageReader(null);
        reader.setInput(new DescriptorInputStream(encoded, descriptor));
        return reader.read(0);
    }

    private static void assertJpegTolerance(BufferedImage expected, BufferedImage actual, int tolerance) {
        for (int y = 0; y < expected.getHeight(); y++) {
            for (int x = 0; x < expected.getWidth(); x++) {
                for (int band = 0; band < expected.getRaster().getNumBands(); band++) {
                    int difference = Math.abs(expected.getRaster().getSample(x, y, band)
                            - actual.getRaster().getSample(x, y, band));
                    assertEquals(true, difference <= tolerance,
                            "sample differs at " + x + "," + y + ": " + difference);
                }
            }
        }
    }

    private static ImageDescriptor descriptor(int rows, int columns, int samples, String photometric) {
        return descriptor(rows, columns, samples, 8, 8, photometric);
    }

    private static void assertExactSamples(BufferedImage expected, BufferedImage actual) {
        for (int y = 0; y < expected.getHeight(); y++) {
            for (int x = 0; x < expected.getWidth(); x++) {
                for (int band = 0; band < expected.getRaster().getNumBands(); band++) {
                    assertEquals(expected.getRaster().getSample(x, y, band),
                            actual.getRaster().getSample(x, y, band),
                            "sample differs at " + x + "," + y + "," + band);
                }
            }
        }
    }

    private static ImageDescriptor descriptor(int rows, int columns, int samples,
            int bitsAllocated, int bitsStored, String photometric) {
        Attributes attributes = new Attributes();
        attributes.setInt(Tag.Rows, VR.US, rows);
        attributes.setInt(Tag.Columns, VR.US, columns);
        attributes.setInt(Tag.SamplesPerPixel, VR.US, samples);
        attributes.setInt(Tag.BitsAllocated, VR.US, bitsAllocated);
        attributes.setInt(Tag.BitsStored, VR.US, bitsStored);
        attributes.setInt(Tag.PixelRepresentation, VR.US, 0);
        if (samples > 1) {
            attributes.setInt(Tag.PlanarConfiguration, VR.US, 0);
        }
        attributes.setString(Tag.PhotometricInterpretation, VR.CS, photometric);
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

        private DescriptorOutputStream(ByteArrayOutputStream bytes, ImageDescriptor descriptor) {
            super(bytes);
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
