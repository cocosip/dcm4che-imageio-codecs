package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

import javax.imageio.IIOImage;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.imageio.codec.BytesWithImageImageDescriptor;
import org.dcm4che3.imageio.codec.ImageDescriptor;
import org.junit.jupiter.api.Test;

import io.github.cocosip.dcm4che.imageio.codecs.core.image.DicomImageTypes;

class Jpeg2000LosslessImageIoTest {
    @Test
    void roundTripsPlanarRgbAndPaletteCodesThroughOneImageIoFrame() throws Exception {
        for (String photometric : new String[] {"RGB", "PALETTE COLOR"}) {
            int count = photometric.equals("RGB") ? 3 : 1;
            ImageDescriptor descriptor = descriptor(37, 35, 8, false, photometric, count == 3);
            BufferedImage source = DicomImageTypes.createImage(descriptor);
            for (int y = 0; y < source.getHeight(); y++) {
                for (int x = 0; x < source.getWidth(); x++) {
                    for (int c = 0; c < count; c++) {
                        source.getRaster().setSample(x, y, c, (x * 7 + y * 11 + c * 79) & 255);
                    }
                }
            }
            byte[] encoded = encode(descriptor, source);
            assertEquals(0xff, encoded[0] & 0xff);
            assertEquals(0x4f, encoded[1] & 0xff);
            Jpeg2000LosslessImageReader reader = new Jpeg2000LosslessImageReader(
                    new Jpeg2000LosslessImageReaderSpi());
            reader.setInput(new DescriptorInputStream(encoded, descriptor));
            BufferedImage decoded = reader.read(0);
            assertEquals(1, reader.getNumImages(true));
            for (int y = 0; y < source.getHeight(); y++) {
                for (int x = 0; x < source.getWidth(); x++) {
                    for (int c = 0; c < count; c++) {
                        assertEquals(source.getRaster().getSample(x, y, c),
                                decoded.getRaster().getSample(x, y, c));
                    }
                }
            }
            assertThrows(IndexOutOfBoundsException.class, () -> reader.read(1));
        }
    }

    @Test
    void appliesSourceRegionAfterDecodingAndRejectsTrailingBytes() throws Exception {
        ImageDescriptor descriptor = descriptor(19, 21, 12, true, "MONOCHROME2", false);
        BufferedImage source = DicomImageTypes.createImage(descriptor);
        for (int y = 0; y < 19; y++) {
            for (int x = 0; x < 21; x++) {
                source.getRaster().setSample(x, y, 0, (x * 31 + y * 61) % 4096 - 2048);
            }
        }
        byte[] encoded = encode(descriptor, source);
        Jpeg2000LosslessImageReader reader = new Jpeg2000LosslessImageReader(null);
        reader.setInput(new DescriptorInputStream(encoded, descriptor));
        ImageReadParam param = reader.getDefaultReadParam();
        param.setSourceRegion(new Rectangle(3, 2, 12, 11));
        param.setSourceSubsampling(2, 3, 0, 0);
        BufferedImage decoded = reader.read(0, param);
        assertEquals(6, decoded.getWidth());
        assertEquals(4, decoded.getHeight());
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 6; x++) {
                assertEquals(source.getRaster().getSample(3 + x * 2, 2 + y * 3, 0),
                        decoded.getRaster().getSample(x, y, 0));
            }
        }
        reader.setInput(new DescriptorInputStream(Arrays.copyOf(encoded, encoded.length + 2), descriptor));
        assertThrows(javax.imageio.IIOException.class, () -> reader.read(0));
    }

    @Test
    void keepsSyntaxSpecificProvidersUnregisteredUntilIntegrationGate() {
        assertEquals("jpeg2000-lossless", new Jpeg2000LosslessImageReaderSpi().getFormatNames()[0]);
        assertEquals("jpeg2000-lossless", new Jpeg2000LosslessImageWriterSpi().getFormatNames()[0]);
        assertFalse(ImageIO.getImageReadersByFormatName("jpeg2000-lossless").hasNext());
        assertFalse(ImageIO.getImageWritersByFormatName("jpeg2000-lossless").hasNext());
    }

    private static byte[] encode(ImageDescriptor descriptor, BufferedImage source) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DescriptorOutputStream output = new DescriptorOutputStream(bytes, descriptor);
        Jpeg2000LosslessImageWriter writer = new Jpeg2000LosslessImageWriter(
                new Jpeg2000LosslessImageWriterSpi());
        writer.setOutput(output);
        writer.write(null, new IIOImage(source, null, null), null);
        output.flush();
        return bytes.toByteArray();
    }

    private static ImageDescriptor descriptor(int rows, int columns, int precision,
            boolean signed, String photometric, boolean planar) {
        Attributes attributes = new Attributes();
        attributes.setInt(Tag.Rows, VR.US, rows);
        attributes.setInt(Tag.Columns, VR.US, columns);
        attributes.setInt(Tag.SamplesPerPixel, VR.US, planar ? 3 : 1);
        attributes.setInt(Tag.BitsAllocated, VR.US, precision <= 8 ? 8 : 16);
        attributes.setInt(Tag.BitsStored, VR.US, precision);
        attributes.setInt(Tag.PixelRepresentation, VR.US, signed ? 1 : 0);
        if (planar) {
            attributes.setInt(Tag.PlanarConfiguration, VR.US, 1);
        }
        attributes.setString(Tag.PhotometricInterpretation, VR.CS, photometric);
        return new ImageDescriptor(attributes);
    }

    private static final class DescriptorInputStream extends MemoryCacheImageInputStream
            implements BytesWithImageImageDescriptor {
        private final ImageDescriptor descriptor;
        private final byte[] bytes;

        DescriptorInputStream(byte[] bytes, ImageDescriptor descriptor) {
            super(new ByteArrayInputStream(bytes));
            this.descriptor = descriptor;
            this.bytes = bytes;
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
