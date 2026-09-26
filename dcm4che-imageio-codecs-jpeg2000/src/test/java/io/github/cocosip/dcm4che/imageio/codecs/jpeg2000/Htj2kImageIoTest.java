package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;

import javax.imageio.IIOImage;
import javax.imageio.IIOException;
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
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000ProgressionOrder;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Limits;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Raster;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.htj2k.Htj2kFrameCodec;

class Htj2kImageIoTest {
    @Test
    void roundTripsLosslessGrayscaleAndPaletteFrames() throws Exception {
        for (String photometric : new String[] {"MONOCHROME2", "PALETTE COLOR"}) {
            ImageDescriptor descriptor = descriptor(35, 37, 12, true, photometric, 1, false);
            BufferedImage source = image(descriptor);
            byte[] encoded = encode(Htj2kFrameCodec.LOSSLESS_UID, descriptor, source, null);
            BufferedImage decoded = decode(Htj2kFrameCodec.LOSSLESS_UID,
                    encoded, descriptor, null, false);
            assertPixels(source, decoded, 0);
        }
    }

    @Test
    void roundTripsPlanarRgbCprlAndAppliesReadRegion() throws Exception {
        ImageDescriptor descriptor = descriptor(35, 37, 8, false, "RGB", 3, true);
        BufferedImage source = image(descriptor);
        Htj2kImageWriteParam options = new Htj2kImageWriteParam(
                Htj2kFrameCodec.LOSSLESS_RPCL_UID);
        options.setProgressionOrder(Jpeg2000ProgressionOrder.CPRL);
        byte[] encoded = encode(Htj2kFrameCodec.LOSSLESS_RPCL_UID,
                descriptor, source, options);
        BufferedImage decoded = decode(Htj2kFrameCodec.LOSSLESS_RPCL_UID,
                encoded, descriptor, null, true);
        assertPixels(source, decoded, 0);
        assertEquals("RGB", String.valueOf(descriptor.getPhotometricInterpretation()));

        Htj2kImageReader reader = reader(Htj2kFrameCodec.LOSSLESS_RPCL_UID);
        reader.setInput(new DescriptorInputStream(encoded, descriptor, false));
        ImageReadParam readParam = reader.getDefaultReadParam();
        readParam.setSourceRegion(new Rectangle(3, 2, 12, 11));
        readParam.setSourceSubsampling(2, 3, 0, 0);
        BufferedImage region = reader.read(0, readParam);
        assertEquals(6, region.getWidth());
        assertEquals(4, region.getHeight());
        for (int y = 0; y < region.getHeight(); y++) {
            for (int x = 0; x < region.getWidth(); x++) {
                for (int c = 0; c < 3; c++) {
                    assertEquals(source.getRaster().getSample(3 + 2 * x, 2 + 3 * y, c),
                            region.getRaster().getSample(x, y, c));
                }
            }
        }
    }

    @Test
    void normalizesFullRangeYbrAtTheImageIoBoundary() throws Exception {
        for (String photometric : new String[] {"YBR_FULL", "YBR_FULL_422"}) {
            ImageDescriptor descriptor = descriptor(2, 4, 8, false,
                    photometric, 3, false);
            BufferedImage source = DicomImageTypes.createImage(descriptor);
            for (int y = 0; y < 2; y++) {
                for (int x = 0; x < 4; x++) {
                    source.getRaster().setSample(x, y, 0, 80 + x * 20 + y * 5);
                    source.getRaster().setSample(x, y, 1, 128);
                    source.getRaster().setSample(x, y, 2, 128);
                }
            }
            byte[] encoded = encode(Htj2kFrameCodec.LOSSLESS_UID,
                    descriptor, source, null);
            BufferedImage decoded = decode(Htj2kFrameCodec.LOSSLESS_UID,
                    encoded, descriptor, null, false);
            for (int y = 0; y < 2; y++) {
                for (int x = 0; x < 4; x++) {
                    for (int c = 0; c < 3; c++) {
                        assertEquals(80 + x * 20 + y * 5,
                                decoded.getRaster().getSample(x, y, c));
                    }
                }
            }
            assertEquals(photometric,
                    String.valueOf(descriptor.getPhotometricInterpretation()));
        }
    }

    @Test
    void readsFoDicomTwelveBitFrameWithAllocatedPrecisionInSiz() throws Exception {
        ImageDescriptor descriptor = descriptor(131, 129, 12, false,
                "MONOCHROME2", 1, false);
        byte[] encoded = Files.readAllBytes(Paths.get(getClass().getResource(
                "/jpeg2000/htj2k_fodicom_gray12_201.j2c").toURI()));
        BufferedImage decoded = decode(Htj2kFrameCodec.LOSSLESS_UID,
                encoded, descriptor, null, false);
        for (int y = 0; y < 131; y++) {
            for (int x = 0; x < 129; x++) {
                assertEquals((x * 17 + y * 31 + x * y * 3) & 4095,
                        decoded.getRaster().getSample(x, y, 0));
            }
        }
    }

    @Test
    void signExtendsFoDicomTwelveBitSamplesAtTheImageIoBoundary() throws Exception {
        ImageDescriptor descriptor = descriptor(131, 129, 12, true,
                "MONOCHROME2", 1, false);
        byte[] encoded = Files.readAllBytes(Paths.get(getClass().getResource(
                "/jpeg2000/htj2k_fodicom_signed_gray12_201.j2c").toURI()));
        BufferedImage decoded = decode(Htj2kFrameCodec.LOSSLESS_UID,
                encoded, descriptor, null, false);
        for (int y = 0; y < 131; y++) {
            for (int x = 0; x < 129; x++) {
                int code = (x * 17 + y * 31 + x * y * 3) & 4095;
                int expected = (code & 2048) == 0 ? code : code - 4096;
                assertEquals(expected, decoded.getRaster().getSample(x, y, 0));
            }
        }
    }

    @Test
    void rejectsAllocatedPrecisionSamplesOutsideBitsStored() throws Exception {
        ImageDescriptor descriptor = descriptor(2, 2, 12, false,
                "MONOCHROME2", 1, false);
        Jpeg2000Raster foreign = Jpeg2000Raster.of(2, 2, 16, 16, false,
                "MONOCHROME2", new int[][] {{0, 1, 4096, 2}},
                Jpeg2000Limits.defaults());
        byte[] encoded = Htj2kFrameCodec.forTransferSyntax(
                Htj2kFrameCodec.LOSSLESS_UID).encode(foreign);
        IIOException failure = assertThrows(IIOException.class,
                () -> decode(Htj2kFrameCodec.LOSSLESS_UID,
                        encoded, descriptor, null, false));
        assertTrue(failure.getMessage().contains("BitsStored"));
    }

    @Test
    void readsFoDicomTwelveBitLossyFrame() throws Exception {
        ImageDescriptor descriptor = descriptor(131, 129, 12, false,
                "MONOCHROME2", 1, false);
        byte[] encoded = Files.readAllBytes(Paths.get(getClass().getResource(
                "/jpeg2000/htj2k_fodicom_gray12_203.j2c").toURI()));
        BufferedImage decoded = decode(Htj2kFrameCodec.LOSSY_UID,
                encoded, descriptor, null, false);
        for (int y = 0; y < 131; y++) {
            for (int x = 0; x < 129; x++) {
                int expected = (x * 17 + y * 31 + x * y * 3) & 4095;
                assertTrue(Math.abs(expected - decoded.getRaster().getSample(x, y, 0)) <= 3);
            }
        }
    }

    @Test
    void roundTripsLossyRgbAndRejectsCrossSyntaxParameters() throws Exception {
        ImageDescriptor descriptor = descriptor(35, 37, 8, false, "RGB", 3, false);
        BufferedImage source = image(descriptor);
        byte[] encoded = encode(Htj2kFrameCodec.LOSSY_UID, descriptor, source, null);
        BufferedImage decoded = decode(Htj2kFrameCodec.LOSSY_UID,
                encoded, descriptor, null, false);
        assertPixels(source, decoded, 12);

        Htj2kImageWriter writer = writer(Htj2kFrameCodec.LOSSLESS_UID);
        writer.setOutput(new DescriptorOutputStream(new ByteArrayOutputStream(), descriptor));
        Htj2kImageWriteParam wrong = new Htj2kImageWriteParam(Htj2kFrameCodec.LOSSY_UID);
        assertThrows(IIOException.class,
                () -> writer.write(null, new IIOImage(source, null, null), wrong));
        assertThrows(IllegalArgumentException.class, () -> wrong.setNumLayers(2));
        assertThrows(IllegalArgumentException.class, () -> wrong.setTargetRatio(1));
        wrong.setTargetRatio(2);
        Htj2kImageWriter lossy = writer(Htj2kFrameCodec.LOSSY_UID);
        ByteArrayOutputStream reduced = new ByteArrayOutputStream();
        DescriptorOutputStream output = new DescriptorOutputStream(reduced, descriptor);
        lossy.setOutput(output);
        lossy.write(null, new IIOImage(source, null, null), wrong);
        output.flush();
        assertPixels(source, decode(Htj2kFrameCodec.LOSSY_UID,
                reduced.toByteArray(), descriptor, null, false), 12);
    }

    @Test
    void rejectsTrailingBytesAndAdditionalFrames() throws Exception {
        ImageDescriptor descriptor = descriptor(11, 13, 8, false, "MONOCHROME2", 1, false);
        BufferedImage source = image(descriptor);
        byte[] encoded = encode(Htj2kFrameCodec.LOSSLESS_UID, descriptor, source, null);
        Htj2kImageReader reader = reader(Htj2kFrameCodec.LOSSLESS_UID);
        reader.setInput(new DescriptorInputStream(encoded, descriptor, false));
        assertEquals(1, reader.getNumImages(true));
        reader.read(0);
        assertThrows(IndexOutOfBoundsException.class, () -> reader.read(1));
        reader.setInput(new DescriptorInputStream(Arrays.copyOf(encoded, encoded.length + 2),
                descriptor, false));
        assertThrows(IIOException.class, () -> reader.read(0));
    }

    private static BufferedImage image(ImageDescriptor descriptor) {
        BufferedImage source = DicomImageTypes.createImage(descriptor);
        int mask = (1 << descriptor.getBitsStored()) - 1;
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                for (int c = 0; c < descriptor.getSamples(); c++) {
                    int value = (x * 17 + y * 11 + c * 59) & mask;
                    if (descriptor.isSigned() && (value & (1 << (descriptor.getBitsStored() - 1))) != 0) {
                        value -= 1 << descriptor.getBitsStored();
                    }
                    source.getRaster().setSample(x, y, c, value);
                }
            }
        }
        return source;
    }

    private static void assertPixels(BufferedImage expected, BufferedImage actual,
            int tolerance) {
        assertEquals(expected.getWidth(), actual.getWidth());
        assertEquals(expected.getHeight(), actual.getHeight());
        for (int y = 0; y < expected.getHeight(); y++) {
            for (int x = 0; x < expected.getWidth(); x++) {
                for (int c = 0; c < expected.getRaster().getNumBands(); c++) {
                    int first = expected.getRaster().getSample(x, y, c);
                    int second = actual.getRaster().getSample(x, y, c);
                    assertTrue(Math.abs(first - second) <= tolerance,
                            "pixel " + x + "," + y + " component " + c);
                }
            }
        }
    }

    private static byte[] encode(String uid, ImageDescriptor descriptor,
            BufferedImage source, Htj2kImageWriteParam options) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DescriptorOutputStream output = new DescriptorOutputStream(bytes, descriptor);
        Htj2kImageWriter writer = writer(uid);
        writer.setOutput(output);
        writer.write(null, new IIOImage(source, null, null), options);
        output.flush();
        return bytes.toByteArray();
    }

    private static BufferedImage decode(String uid, byte[] encoded,
            ImageDescriptor descriptor, ImageReadParam param, boolean fragmented)
            throws Exception {
        Htj2kImageReader reader = reader(uid);
        reader.setInput(new DescriptorInputStream(encoded, descriptor, fragmented));
        return reader.read(0, param);
    }

    private static Htj2kImageWriter writer(String uid) {
        if (Htj2kFrameCodec.LOSSLESS_UID.equals(uid)) {
            return new Htj2kLosslessImageWriter(new Htj2kLosslessImageWriterSpi());
        }
        if (Htj2kFrameCodec.LOSSLESS_RPCL_UID.equals(uid)) {
            return new Htj2kLosslessRpclImageWriter(new Htj2kLosslessRpclImageWriterSpi());
        }
        return new Htj2kLossyImageWriter(new Htj2kLossyImageWriterSpi());
    }

    private static Htj2kImageReader reader(String uid) {
        if (Htj2kFrameCodec.LOSSLESS_UID.equals(uid)) {
            return new Htj2kLosslessImageReader(new Htj2kLosslessImageReaderSpi());
        }
        if (Htj2kFrameCodec.LOSSLESS_RPCL_UID.equals(uid)) {
            return new Htj2kLosslessRpclImageReader(new Htj2kLosslessRpclImageReaderSpi());
        }
        return new Htj2kLossyImageReader(new Htj2kLossyImageReaderSpi());
    }

    private static ImageDescriptor descriptor(int rows, int columns, int precision,
            boolean signed, String photometric, int components, boolean planar) {
        Attributes attributes = new Attributes();
        attributes.setInt(Tag.Rows, VR.US, rows);
        attributes.setInt(Tag.Columns, VR.US, columns);
        attributes.setInt(Tag.SamplesPerPixel, VR.US, components);
        attributes.setInt(Tag.BitsAllocated, VR.US, precision <= 8 ? 8 : 16);
        attributes.setInt(Tag.BitsStored, VR.US, precision);
        attributes.setInt(Tag.PixelRepresentation, VR.US, signed ? 1 : 0);
        if (components == 3) {
            attributes.setInt(Tag.PlanarConfiguration, VR.US, planar ? 1 : 0);
        }
        attributes.setString(Tag.PhotometricInterpretation, VR.CS, photometric);
        return new ImageDescriptor(attributes);
    }

    private static final class DescriptorInputStream extends MemoryCacheImageInputStream
            implements BytesWithImageImageDescriptor {
        private final ImageDescriptor descriptor;
        private final byte[] bytes;

        DescriptorInputStream(byte[] bytes, ImageDescriptor descriptor, boolean fragmented) {
            super(fragmented ? new ByteArrayInputStream(bytes) {
                @Override
                public synchronized int read(byte[] buffer, int offset, int length) {
                    return super.read(buffer, offset, Math.min(length, 3));
                }
            } : new ByteArrayInputStream(bytes));
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
