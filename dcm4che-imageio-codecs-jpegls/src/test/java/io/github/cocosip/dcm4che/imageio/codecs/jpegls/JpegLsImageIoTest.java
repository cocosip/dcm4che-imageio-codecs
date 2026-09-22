package io.github.cocosip.dcm4che.imageio.codecs.jpegls;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferShort;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

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
import org.dcm4che3.imageio.codec.ImageReaderFactory;
import org.dcm4che3.imageio.codec.ImageWriterFactory;

import io.github.cocosip.dcm4che.imageio.codecs.core.image.DicomImageTypes;

class JpegLsImageIoTest {
    @Test
    void losslessWriterAndReaderRoundTripMonochrome8() throws Exception {
        ImageDescriptor descriptor = descriptor(2, 3, 8, false);
        BufferedImage source = DicomImageTypes.createImage(descriptor);
        int[] samples = {1, 2, 3, 4, 5, 6};
        fill(source, samples);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        JpegLsLosslessImageWriter writer = new JpegLsLosslessImageWriter(null);
        DescriptorOutputStream output = new DescriptorOutputStream(bytes, descriptor);
        writer.setOutput(output);
        writer.write(null, new IIOImage(source, null, null), writer.getDefaultWriteParam());
        output.flush();

        JpegLsLosslessImageReader reader = new JpegLsLosslessImageReader(null);
        reader.setInput(new DescriptorInputStream(bytes.toByteArray(), descriptor));
        BufferedImage decoded = reader.read(0);

        assertSamples(decoded, samples);
    }

    @Test
    void nearLosslessParameterIsUsedAndDestinationIsReused() throws Exception {
        ImageDescriptor descriptor = descriptor(1, 4, 8, false);
        BufferedImage source = DicomImageTypes.createImage(descriptor);
        fill(source, new int[] {10, 20, 30, 40});

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        JpegLsNearLosslessImageWriter writer = new JpegLsNearLosslessImageWriter(null);
        DescriptorOutputStream output = new DescriptorOutputStream(bytes, descriptor);
        writer.setOutput(output);
        JpegLsImageWriteParam writeParam = (JpegLsImageWriteParam) writer.getDefaultWriteParam();
        writeParam.setAllowedError(2);
        writer.write(null, new IIOImage(source, null, null), writeParam);
        output.flush();

        JpegLsNearLosslessImageReader reader = new JpegLsNearLosslessImageReader(null);
        reader.setInput(new DescriptorInputStream(bytes.toByteArray(), descriptor));
        BufferedImage destination = DicomImageTypes.createImage(descriptor);
        ImageReadParam readParam = reader.getDefaultReadParam();
        readParam.setDestination(destination);
        assertSame(destination, reader.read(0, readParam));
        for (int x = 0; x < 4; x++) {
            assertEquals(true, Math.abs(source.getRaster().getSample(x, 0, 0)
                    - destination.getRaster().getSample(x, 0, 0)) <= 2);
        }
    }

    @Test
    void registersBothTransferSyntaxesWithDcm4cheFactories() throws Exception {
        JpegLsCodec.register();
        assertInstanceOf(JpegLsLosslessImageReader.class,
                ImageReaderFactory.getImageReader(ImageReaderFactory.getImageReaderParam(
                        JpegLsCodec.LOSSLESS_TRANSFER_SYNTAX_UID)));
        assertInstanceOf(JpegLsNearLosslessImageWriter.class,
                ImageWriterFactory.getImageWriter(ImageWriterFactory.getImageWriterParam(
                        JpegLsCodec.NEAR_LOSSLESS_TRANSFER_SYNTAX_UID)));
    }

    @Test
    void preservesSignedSixteenBitContainerBits() throws Exception {
        ImageDescriptor descriptor = descriptor(1, 3, 16, true);
        BufferedImage source = DicomImageTypes.createImage(descriptor);
        short[] sourceData = ((DataBufferShort) source.getRaster().getDataBuffer()).getData();
        sourceData[0] = (short) 0x8001;
        sourceData[1] = (short) 0xffff;
        sourceData[2] = (short) 0x1234;

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        JpegLsLosslessImageWriter writer = new JpegLsLosslessImageWriter(null);
        DescriptorOutputStream output = new DescriptorOutputStream(bytes, descriptor);
        writer.setOutput(output);
        writer.write(null, new IIOImage(source, null, null), writer.getDefaultWriteParam());
        output.flush();

        JpegLsLosslessImageReader reader = new JpegLsLosslessImageReader(null);
        reader.setInput(new DescriptorInputStream(bytes.toByteArray(), descriptor));
        short[] decoded = ((DataBufferShort) reader.read(0).getRaster().getDataBuffer()).getData();
        assertEquals(sourceData[0], decoded[0]);
        assertEquals(sourceData[1], decoded[1]);
        assertEquals(sourceData[2], decoded[2]);
    }

    @Test
    void roundTripsRgbInterleavedImage() throws Exception {
        ImageDescriptor descriptor = rgbDescriptor(1, 2);
        BufferedImage source = DicomImageTypes.createImage(descriptor);
        fill(source, new int[] {1, 10, 100, 2, 20, 110});
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        JpegLsLosslessImageWriter writer = new JpegLsLosslessImageWriter(null);
        DescriptorOutputStream output = new DescriptorOutputStream(bytes, descriptor);
        writer.setOutput(output);
        writer.write(null, new IIOImage(source, null, null), writer.getDefaultWriteParam());
        output.flush();
        JpegLsLosslessImageReader reader = new JpegLsLosslessImageReader(null);
        reader.setInput(new DescriptorInputStream(bytes.toByteArray(), descriptor));
        assertSamples(reader.read(0), new int[] {1, 10, 100, 2, 20, 110});
    }

    private static ImageDescriptor descriptor(int rows, int columns, int bits, boolean signed) {
        Attributes attributes = new Attributes();
        attributes.setInt(Tag.Rows, VR.US, rows);
        attributes.setInt(Tag.Columns, VR.US, columns);
        attributes.setInt(Tag.SamplesPerPixel, VR.US, 1);
        attributes.setInt(Tag.BitsAllocated, VR.US, bits);
        attributes.setInt(Tag.BitsStored, VR.US, bits);
        attributes.setInt(Tag.PixelRepresentation, VR.US, signed ? 1 : 0);
        attributes.setString(Tag.PhotometricInterpretation, VR.CS, "MONOCHROME2");
        return new ImageDescriptor(attributes);
    }

    private static ImageDescriptor rgbDescriptor(int rows, int columns) {
        Attributes attributes = new Attributes();
        attributes.setInt(Tag.Rows, VR.US, rows);
        attributes.setInt(Tag.Columns, VR.US, columns);
        attributes.setInt(Tag.SamplesPerPixel, VR.US, 3);
        attributes.setInt(Tag.BitsAllocated, VR.US, 8);
        attributes.setInt(Tag.BitsStored, VR.US, 8);
        attributes.setInt(Tag.PixelRepresentation, VR.US, 0);
        attributes.setInt(Tag.PlanarConfiguration, VR.US, 0);
        attributes.setString(Tag.PhotometricInterpretation, VR.CS, "RGB");
        return new ImageDescriptor(attributes);
    }

    private static void fill(BufferedImage image, int[] values) {
        int i = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                for (int band = 0; band < image.getRaster().getNumBands(); band++) {
                    image.getRaster().setSample(x, y, band, values[i++]);
                }
            }
        }
    }

    private static void assertSamples(BufferedImage image, int[] expected) {
        int i = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                for (int band = 0; band < image.getRaster().getNumBands(); band++) {
                    assertEquals(expected[i++], image.getRaster().getSample(x, y, band));
                }
            }
        }
    }

    private static final class DescriptorInputStream extends MemoryCacheImageInputStream
            implements BytesWithImageImageDescriptor {
        private final ImageDescriptor descriptor;
        private final byte[] bytes;

        DescriptorInputStream(byte[] bytes, ImageDescriptor descriptor) {
            super(new ByteArrayInputStream(bytes));
            this.bytes = bytes;
            this.descriptor = descriptor;
        }

        @Override public ByteBuffer getBytes() { return ByteBuffer.wrap(bytes).asReadOnlyBuffer(); }
        @Override public ImageDescriptor getImageDescriptor() { return descriptor; }
    }

    private static final class DescriptorOutputStream extends MemoryCacheImageOutputStream
            implements BytesWithImageImageDescriptor {
        private final ImageDescriptor descriptor;

        DescriptorOutputStream(ByteArrayOutputStream output, ImageDescriptor descriptor) {
            super(output);
            this.descriptor = descriptor;
        }

        @Override public ByteBuffer getBytes() { return ByteBuffer.allocate(0); }
        @Override public ImageDescriptor getImageDescriptor() { return descriptor; }
    }
}
