package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.awt.Rectangle;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

import javax.imageio.IIOImage;
import javax.imageio.IIOException;
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
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.classic.Jpeg2000LosslessCodec;

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
    void discoversSyntaxSpecificProviders() {
        assertEquals("jpeg2000-lossless", new Jpeg2000LosslessImageReaderSpi().getFormatNames()[0]);
        assertEquals("jpeg2000-lossless", new Jpeg2000LosslessImageWriterSpi().getFormatNames()[0]);
        assertTrue(ImageIO.getImageReadersByFormatName("jpeg2000-lossless").hasNext());
        assertTrue(ImageIO.getImageWritersByFormatName("jpeg2000-lossless").hasNext());
    }

    @Test
    void rejectsWriteParametersForTheOtherTransferSyntax() throws Exception {
        ImageDescriptor descriptor = descriptor(8, 8, 8, false, "MONOCHROME2", false);
        BufferedImage image = DicomImageTypes.createImage(descriptor);
        IIOImage frame = new IIOImage(image, null, null);

        Jpeg2000LosslessImageWriter lossless = new Jpeg2000LosslessImageWriter(
                new Jpeg2000LosslessImageWriterSpi());
        lossless.setOutput(new DescriptorOutputStream(new ByteArrayOutputStream(), descriptor));
        assertThrows(IIOException.class,
                () -> lossless.write(null, frame, new Jpeg2000ImageWriteParam(false)));

        Jpeg2000LossyImageWriter lossy = new Jpeg2000LossyImageWriter(
                new Jpeg2000LossyImageWriterSpi());
        lossy.setOutput(new DescriptorOutputStream(new ByteArrayOutputStream(), descriptor));
        assertThrows(IIOException.class,
                () -> lossy.write(null, frame, new Jpeg2000ImageWriteParam(true)));
    }

    @Test
    void losslessReaderRejectsIrreversibleCodestream() throws Exception {
        ImageDescriptor descriptor = descriptor(16, 16, 8, false, "MONOCHROME2", false);
        BufferedImage image = DicomImageTypes.createImage(descriptor);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                image.getRaster().setSample(x, y, 0, x * 11 + y * 3);
            }
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DescriptorOutputStream output = new DescriptorOutputStream(bytes, descriptor);
        Jpeg2000LossyImageWriter writer = new Jpeg2000LossyImageWriter(
                new Jpeg2000LossyImageWriterSpi());
        writer.setOutput(output);
        writer.write(null, new IIOImage(image, null, null), null);
        output.flush();

        Jpeg2000LosslessImageReader lossless = new Jpeg2000LosslessImageReader(
                new Jpeg2000LosslessImageReaderSpi());
        lossless.setInput(new DescriptorInputStream(bytes.toByteArray(), descriptor));
        assertThrows(IIOException.class, () -> lossless.read(0));

        Jpeg2000LossyImageReader lossy = new Jpeg2000LossyImageReader(
                new Jpeg2000LossyImageReaderSpi());
        lossy.setInput(new DescriptorInputStream(bytes.toByteArray(), descriptor));
        assertEquals(16, lossy.read(0).getWidth());
    }

    @Test
    void readerSpisCheckSocWithoutConsumingInput() throws Exception {
        ImageDescriptor descriptor = descriptor(2, 2, 8, false, "MONOCHROME2", false);
        DescriptorInputStream valid = new DescriptorInputStream(
                new byte[] {(byte) 0xff, 0x4f, 0}, descriptor);
        DescriptorInputStream invalid = new DescriptorInputStream(
                new byte[] {(byte) 0xff, (byte) 0xd8, 0}, descriptor);
        Jpeg2000LosslessImageReaderSpi lossless = new Jpeg2000LosslessImageReaderSpi();
        Jpeg2000LossyImageReaderSpi lossy = new Jpeg2000LossyImageReaderSpi();

        assertTrue(lossless.canDecodeInput(valid));
        assertTrue(lossy.canDecodeInput(valid));
        assertEquals(0, valid.getStreamPosition());
        assertFalse(lossless.canDecodeInput(invalid));
        assertFalse(lossy.canDecodeInput(invalid));
        assertEquals(0, invalid.getStreamPosition());
    }

    @Test
    void normalizesYbrFull422ImageIoInput() throws Exception {
        Attributes attributes = new Attributes();
        attributes.setInt(Tag.Rows, VR.US, 2);
        attributes.setInt(Tag.Columns, VR.US, 2);
        attributes.setInt(Tag.SamplesPerPixel, VR.US, 3);
        attributes.setInt(Tag.PlanarConfiguration, VR.US, 0);
        attributes.setInt(Tag.BitsAllocated, VR.US, 8);
        attributes.setInt(Tag.BitsStored, VR.US, 8);
        attributes.setInt(Tag.PixelRepresentation, VR.US, 0);
        attributes.setString(Tag.PhotometricInterpretation, VR.CS, "YBR_FULL_422");
        ImageDescriptor descriptor = new ImageDescriptor(attributes);
        BufferedImage source = DicomImageTypes.createImage(descriptor);
        int[] luminance = {100, 110, 120, 130};
        for (int i = 0; i < luminance.length; i++) {
            int x = i % 2;
            int y = i / 2;
            source.getRaster().setSample(x, y, 0, luminance[i]);
            source.getRaster().setSample(x, y, 1, 128);
            source.getRaster().setSample(x, y, 2, 128);
        }
        Jpeg2000LosslessImageReader reader = new Jpeg2000LosslessImageReader(
                new Jpeg2000LosslessImageReaderSpi());
        reader.setInput(new DescriptorInputStream(encode(descriptor, source), descriptor));
        BufferedImage decoded = reader.read(0);
        for (int i = 0; i < luminance.length; i++) {
            for (int c = 0; c < 3; c++) {
                assertEquals(luminance[i], decoded.getRaster().getSample(i % 2, i / 2, c));
            }
        }
    }

    @Test
    void reversibleLossySyntaxIsExactForMonochromeAndRejectsRgb() throws Exception {
        ImageDescriptor monochrome = descriptor(35, 37, 8, false, "MONOCHROME2", false);
        BufferedImage source = DicomImageTypes.createImage(monochrome);
        for (int y = 0; y < 35; y++) {
            for (int x = 0; x < 37; x++) {
                source.getRaster().setSample(x, y, 0, (x * 17 + y * 11) & 255);
            }
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DescriptorOutputStream output = new DescriptorOutputStream(bytes, monochrome);
        Jpeg2000LossyImageWriter writer = new Jpeg2000LossyImageWriter(
                new Jpeg2000LossyImageWriterSpi());
        Jpeg2000ImageWriteParam options = (Jpeg2000ImageWriteParam) writer.getDefaultWriteParam();
        options.setIrreversible(false);
        options.setRate(0);
        writer.setOutput(output);
        writer.write(null, new IIOImage(source, null, null), options);
        output.flush();
        Jpeg2000LossyImageReader reader = new Jpeg2000LossyImageReader(
                new Jpeg2000LossyImageReaderSpi());
        reader.setInput(new DescriptorInputStream(bytes.toByteArray(), monochrome));
        BufferedImage decoded = reader.read(0);
        for (int y = 0; y < 35; y++) {
            for (int x = 0; x < 37; x++) {
                assertEquals(source.getRaster().getSample(x, y, 0),
                        decoded.getRaster().getSample(x, y, 0));
            }
        }

        ImageDescriptor rgb = descriptor(2, 2, 8, false, "RGB", true);
        writer.setOutput(new DescriptorOutputStream(new ByteArrayOutputStream(), rgb));
        BufferedImage rgbImage = DicomImageTypes.createImage(rgb);
        assertThrows(javax.imageio.IIOException.class,
                () -> writer.write(null, new IIOImage(rgbImage, null, null), options));
    }

    @Test
    void rejectsOversizedDestinationAndRecoversOnNextInput() throws Exception {
        ImageDescriptor descriptor = descriptor(35, 37, 8, false, "MONOCHROME2", false);
        BufferedImage source = DicomImageTypes.createImage(descriptor);
        byte[] encoded = encode(descriptor, source);
        Jpeg2000LosslessImageReader reader = new Jpeg2000LosslessImageReader(
                new Jpeg2000LosslessImageReaderSpi());
        reader.setInput(new DescriptorInputStream(encoded, descriptor));
        ImageReadParam param = reader.getDefaultReadParam();
        param.setDestinationOffset(new Point(Integer.MAX_VALUE, 0));
        assertThrows(javax.imageio.IIOException.class, () -> reader.read(0, param));
        reader.setInput(new DescriptorInputStream(encoded, descriptor));
        assertEquals(37, reader.read(0).getWidth());
    }

    @Test
    void signedAsUnsignedOptionPreservesSixteenBitCodes() throws Exception {
        ImageDescriptor descriptor = descriptor(35, 37, 16, true, "MONOCHROME2", false);
        BufferedImage source = DicomImageTypes.createImage(descriptor);
        for (int y = 0; y < 35; y++) {
            for (int x = 0; x < 37; x++) {
                source.getRaster().setSample(x, y, 0, (x * 253 + y * 317) - 32768);
            }
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DescriptorOutputStream output = new DescriptorOutputStream(bytes, descriptor);
        Jpeg2000LosslessImageWriter writer = new Jpeg2000LosslessImageWriter(
                new Jpeg2000LosslessImageWriterSpi());
        Jpeg2000ImageWriteParam options = (Jpeg2000ImageWriteParam) writer.getDefaultWriteParam();
        options.setEncodeSignedAsUnsigned(true);
        writer.setOutput(output);
        writer.write(null, new IIOImage(source, null, null), options);
        output.flush();
        byte[] encoded = bytes.toByteArray();
        assertFalse(Jpeg2000LosslessCodec.decode(encoded).signed());
        Jpeg2000LosslessImageReader reader = new Jpeg2000LosslessImageReader(
                new Jpeg2000LosslessImageReaderSpi());
        reader.setInput(new DescriptorInputStream(encoded, descriptor));
        BufferedImage decoded = reader.read(0);
        for (int y = 0; y < 35; y++) {
            for (int x = 0; x < 37; x++) {
                assertEquals(source.getRaster().getSample(x, y, 0),
                        decoded.getRaster().getSample(x, y, 0));
            }
        }
    }

    @Test
    void readsFrameAcrossShortInputChunks() throws Exception {
        ImageDescriptor descriptor = descriptor(35, 37, 8, false, "MONOCHROME2", false);
        BufferedImage source = DicomImageTypes.createImage(descriptor);
        for (int y = 0; y < 35; y++) {
            for (int x = 0; x < 37; x++) {
                source.getRaster().setSample(x, y, 0, x + y);
            }
        }
        byte[] encoded = encode(descriptor, source);
        InputStream chunks = new ByteArrayInputStream(encoded) {
            @Override
            public synchronized int read(byte[] bytes, int offset, int length) {
                return super.read(bytes, offset, Math.min(length, 3));
            }
        };
        Jpeg2000LosslessImageReader reader = new Jpeg2000LosslessImageReader(
                new Jpeg2000LosslessImageReaderSpi());
        reader.setInput(new DescriptorInputStream(chunks, encoded, descriptor));
        BufferedImage decoded = reader.read(0);
        for (int y = 0; y < 35; y++) {
            for (int x = 0; x < 37; x++) {
                assertEquals(x + y, decoded.getRaster().getSample(x, y, 0));
            }
        }
    }

    @Test
    void decodesPartial422DescriptorToRgbRaster() throws Exception {
        ImageDescriptor rgb = descriptor(35, 37, 8, false, "RGB", true);
        BufferedImage source = DicomImageTypes.createImage(rgb);
        for (int y = 0; y < 35; y++) {
            for (int x = 0; x < 37; x++) {
                for (int c = 0; c < 3; c++) {
                    source.getRaster().setSample(x, y, c, x + y + c * 20);
                }
            }
        }
        Attributes metadata = new Attributes();
        metadata.setInt(Tag.Rows, VR.US, 35);
        metadata.setInt(Tag.Columns, VR.US, 37);
        metadata.setInt(Tag.SamplesPerPixel, VR.US, 3);
        metadata.setInt(Tag.PlanarConfiguration, VR.US, 0);
        metadata.setInt(Tag.BitsAllocated, VR.US, 8);
        metadata.setInt(Tag.BitsStored, VR.US, 8);
        metadata.setInt(Tag.PixelRepresentation, VR.US, 0);
        metadata.setString(Tag.PhotometricInterpretation, VR.CS, "YBR_PARTIAL_422");
        ImageDescriptor partial = new ImageDescriptor(metadata);
        Jpeg2000LosslessImageReader reader = new Jpeg2000LosslessImageReader(
                new Jpeg2000LosslessImageReaderSpi());
        reader.setInput(new DescriptorInputStream(encode(rgb, source), partial));
        BufferedImage decoded = reader.read(0);
        for (int y = 0; y < 35; y++) {
            for (int x = 0; x < 37; x++) {
                for (int c = 0; c < 3; c++) {
                    assertEquals(x + y + c * 20,
                            decoded.getRaster().getSample(x, y, c));
                }
            }
        }
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
            this(new ByteArrayInputStream(bytes), bytes, descriptor);
        }

        DescriptorInputStream(InputStream input, byte[] bytes, ImageDescriptor descriptor) {
            super(input);
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
