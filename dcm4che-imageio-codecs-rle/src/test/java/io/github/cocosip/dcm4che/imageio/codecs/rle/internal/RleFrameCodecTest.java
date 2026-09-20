package io.github.cocosip.dcm4che.imageio.codecs.rle.internal;

import java.util.Arrays;

import javax.imageio.IIOException;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.imageio.codec.ImageDescriptor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RleFrameCodecTest {

    @Test
    void encodesMonochrome16AsMostSignificantByteSegmentFirst() throws Exception {
        RlePixelLayout unsigned = RlePixelLayout.from(descriptor(1, 2, 1, 16, false, false));
        RlePixelLayout signed = RlePixelLayout.from(descriptor(1, 2, 1, 16, true, false));
        byte[] raw = bytes(0x34, 0x12, 0xcd, 0xab);

        byte[] encoded = RleFrameCodec.encode(unsigned, raw);

        byte[] expected = new byte[72];
        System.arraycopy(RleHeader.of(new int[] { 64, 68 }).toBytes(), 0, expected, 0, 64);
        System.arraycopy(bytes(1, 0x12, 0xab, 0, 1, 0x34, 0xcd, 0),
                0, expected, 64, 8);
        assertArrayEquals(expected, encoded);
        assertArrayEquals(encoded, RleFrameCodec.encode(signed, raw));
        assertArrayEquals(raw, RleFrameCodec.decode(unsigned, encoded));
        assertArrayEquals(raw, RleFrameCodec.decode(signed, encoded));
    }

    @Test
    void producesSameLogicalRgbSegmentsFromInterleavedAndPlanarFrames() throws Exception {
        RlePixelLayout interleaved = RlePixelLayout.from(
                descriptor(1, 2, 3, 8, false, false));
        RlePixelLayout planar = RlePixelLayout.from(
                descriptor(1, 2, 3, 8, false, true));
        byte[] interleavedRaw = bytes(1, 2, 3, 4, 5, 6);
        byte[] planarRaw = bytes(1, 4, 2, 5, 3, 6);

        byte[] interleavedEncoded = RleFrameCodec.encode(interleaved, interleavedRaw);
        byte[] planarEncoded = RleFrameCodec.encode(planar, planarRaw);

        assertArrayEquals(interleavedEncoded, planarEncoded);
        assertArrayEquals(new int[] { 64, 68, 72 },
                RleHeader.parse(interleavedEncoded).segmentOffsets());
        assertArrayEquals(interleavedRaw,
                RleFrameCodec.decode(interleaved, interleavedEncoded));
        assertArrayEquals(planarRaw, RleFrameCodec.decode(planar, planarEncoded));
    }

    @Test
    void roundTripsOddAndEvenLengthMonochrome8Frames() throws Exception {
        assertRoundTrip(descriptor(1, 3, 1, 8, false, false), bytes(1, 2, 3));
        assertRoundTrip(descriptor(1, 4, 1, 8, false, false), bytes(1, 2, 3, 4));
    }

    @Test
    void preservesLowEntropySigned16RawBitsAcrossWidths() throws Exception {
        for (int width = 1; width <= 257; width++) {
            byte[] raw = new byte[width * 2];
            for (int index = 0; index < raw.length; index++) {
                raw[index] = (byte) ((index * 17 + width) & 1);
            }
            assertRoundTrip(descriptor(1, width, 1, 16, true, false), raw);
        }
    }

    @Test
    void acceptsExactlyOneZeroAlignmentBytePerSegment() throws Exception {
        RlePixelLayout layout = RlePixelLayout.from(
                descriptor(1, 2, 1, 8, false, false));
        byte[] encoded = RleFrameCodec.encode(layout, bytes(1, 2));

        assertEquals(68, encoded.length);
        assertEquals(0, encoded[encoded.length - 1]);
        assertArrayEquals(bytes(1, 2), RleFrameCodec.decode(layout, encoded));

        byte[] extraPadding = Arrays.copyOf(encoded, encoded.length + 1);
        assertThrows(IIOException.class, () -> RleFrameCodec.decode(layout, extraPadding));
    }

    @Test
    void rejectsLayoutAndFrameMismatches() throws Exception {
        RlePixelLayout mono8 = RlePixelLayout.from(
                descriptor(1, 2, 1, 8, false, false));
        RlePixelLayout mono16 = RlePixelLayout.from(
                descriptor(1, 2, 1, 16, false, false));

        assertThrows(IIOException.class, () -> RleFrameCodec.encode(mono8, bytes(1)));
        byte[] encodedMono8 = RleFrameCodec.encode(mono8, bytes(1, 2));
        assertThrows(IIOException.class, () -> RleFrameCodec.decode(mono16, encodedMono8));
    }

    @Test
    void rejectsUnsupportedOrOverflowingLayouts() {
        assertThrows(IIOException.class,
                () -> RlePixelLayout.from(descriptor(1, 1, 1, 12, false, false)));
        assertThrows(IIOException.class,
                () -> RlePixelLayout.from(descriptor(1, 1, 2, 8, false, false)));
        assertThrows(IIOException.class,
                () -> RlePixelLayout.from(descriptor(65535, 65535, 3, 16, false, false)));
    }

    private static void assertRoundTrip(ImageDescriptor descriptor, byte[] raw) throws Exception {
        RlePixelLayout layout = RlePixelLayout.from(descriptor);
        assertArrayEquals(raw, RleFrameCodec.decode(layout, RleFrameCodec.encode(layout, raw)));
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

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int index = 0; index < values.length; index++) {
            result[index] = (byte) values[index];
        }
        return result;
    }
}
