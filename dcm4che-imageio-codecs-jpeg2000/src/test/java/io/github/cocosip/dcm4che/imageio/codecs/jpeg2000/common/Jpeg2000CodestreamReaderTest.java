package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class Jpeg2000CodestreamReaderTest {
    private static final Jpeg2000Limits LIMITS = Jpeg2000Limits.defaults();

    @Test
    void parsesAndValidatesSizeGeometryAndComponents() throws Exception {
        Jpeg2000SizeSegment size = Jpeg2000SizeSegment.parse(
                segment(Jpeg2000Marker.SIZ, sizePayload(32, 16, 16, 8,
                        new int[][] {{8, 0, 1, 1}, {16, 1, 1, 1}})),
                LIMITS);

        assertEquals(32, size.referenceGridWidth());
        assertEquals(16, size.referenceGridHeight());
        assertEquals(2, size.components().size());
        assertEquals(8, size.components().get(0).precision());
        assertFalse(size.components().get(0).signed());
        assertEquals(16, size.components().get(1).precision());
        assertTrue(size.components().get(1).signed());
        assertEquals(4, size.tileCount());
        assertArrayEquals(sizePayload(32, 16, 16, 8,
                new int[][] {{8, 0, 1, 1}, {16, 1, 1, 1}}), size.payload());
    }

    @Test
    void sizeRejectsInvalidBoundsSubsamplingPrecisionAndTileCount() {
        assertThrows(Jpeg2000Exception.class, () -> Jpeg2000SizeSegment.parse(
                segment(Jpeg2000Marker.SIZ, sizePayload(0, 16, 16, 8,
                        new int[][] {{8, 0, 1, 1}})), LIMITS));
        assertThrows(Jpeg2000Exception.class, () -> Jpeg2000SizeSegment.parse(
                segment(Jpeg2000Marker.SIZ, sizePayload(32, 16, 16, 8,
                        new int[][] {{8, 0, 2, 1}})), LIMITS));
        assertThrows(Jpeg2000Exception.class, () -> Jpeg2000SizeSegment.parse(
                segment(Jpeg2000Marker.SIZ, sizePayload(32, 16, 16, 8,
                        new int[][] {{39, 0, 1, 1}})), LIMITS));

        Jpeg2000Limits oneTile = new Jpeg2000Limits(1024, 1024, 1, 8, 8, 65533, 3);
        assertThrows(Jpeg2000Exception.class, () -> Jpeg2000SizeSegment.parse(
                segment(Jpeg2000Marker.SIZ, sizePayload(32, 16, 16, 8,
                        new int[][] {{8, 0, 1, 1}})), oneTile));
    }

    @Test
    void parsesAndValidatesCodingStyle() throws Exception {
        Jpeg2000CodingStyleSegment coding = Jpeg2000CodingStyleSegment.parse(
                segment(Jpeg2000Marker.COD,
                        new byte[] {0x07, 0x02, 0x00, 0x03, 0x01, 0x01, 0x04, 0x04, 0x0a, 0x01, 0x44, 0x55}),
                LIMITS);

        assertEquals(Jpeg2000ProgressionOrder.RPCL, coding.progressionOrder());
        assertEquals(3, coding.qualityLayers());
        assertTrue(coding.multipleComponentTransform());
        assertEquals(1, coding.decompositionLevels());
        assertEquals(64, coding.codeBlockWidth());
        assertEquals(64, coding.codeBlockHeight());
        assertEquals(0x0a, coding.codeBlockStyle());
        assertEquals(1, coding.transformation());
        assertTrue(coding.hasStartOfPacketMarkers());
        assertTrue(coding.hasEndOfPacketHeaderMarkers());
        assertArrayEquals(new byte[] {0x44, 0x55}, coding.precinctSizes());
    }

    @Test
    void codingStyleRejectsInvalidProgressionLayersDimensionsAndPrecinctCount() {
        assertCodingRejected(new byte[] {0, 5, 0, 1, 0, 0, 4, 4, 0, 1});
        assertCodingRejected(new byte[] {0, 0, 0, 0, 0, 0, 4, 4, 0, 1});
        assertCodingRejected(new byte[] {0, 0, 0, 1, 0, 0, 9, 4, 0, 1});
        assertCodingRejected(new byte[] {1, 0, 0, 1, 0, 1, 4, 4, 0, 1, 0x44});
    }

    @Test
    void parsesQuantizationAndComponentOverrides() throws Exception {
        Jpeg2000SizeSegment size = Jpeg2000SizeSegment.parse(
                segment(Jpeg2000Marker.SIZ, sizePayload(8, 8, 8, 8,
                        new int[][] {{8, 0, 1, 1}, {8, 0, 1, 1}})), LIMITS);
        Jpeg2000CodingStyleSegment coding = Jpeg2000CodingStyleSegment.parse(
                segment(Jpeg2000Marker.COD, new byte[] {0, 0, 0, 1, 0, 1, 4, 4, 0, 1}), LIMITS);
        Jpeg2000QuantizationSegment quantization = Jpeg2000QuantizationSegment.parse(
                segment(Jpeg2000Marker.QCD, new byte[] {0x40, 0x40, 0x48, 0x48, 0x50}), coding);
        Jpeg2000ComponentCodingStyleSegment componentCoding = Jpeg2000ComponentCodingStyleSegment.parse(
                segment(Jpeg2000Marker.COC, new byte[] {1, 0, 1, 4, 4, 0, 1}), size);
        Jpeg2000ComponentQuantizationSegment componentQuantization =
                Jpeg2000ComponentQuantizationSegment.parse(
                        segment(Jpeg2000Marker.QCC, new byte[] {1, 0x40, 0x40, 0x48, 0x48, 0x50}),
                        size,
                        coding);

        assertEquals(Jpeg2000QuantizationStyle.NO_QUANTIZATION, quantization.style());
        assertEquals(2, quantization.guardBits());
        assertEquals(4, quantization.stepSizes().length);
        assertEquals(1, componentCoding.componentIndex());
        assertEquals(1, componentQuantization.componentIndex());
        assertArrayEquals(quantization.stepSizes(), componentQuantization.quantization().stepSizes());
    }

    @Test
    void quantizationAndOverridesRejectInconsistentPayloads() throws Exception {
        Jpeg2000SizeSegment size = Jpeg2000SizeSegment.parse(
                segment(Jpeg2000Marker.SIZ, sizePayload(8, 8, 8, 8,
                        new int[][] {{8, 0, 1, 1}})), LIMITS);
        Jpeg2000CodingStyleSegment coding = Jpeg2000CodingStyleSegment.parse(
                segment(Jpeg2000Marker.COD, new byte[] {0, 0, 0, 1, 0, 1, 4, 4, 0, 1}), LIMITS);

        assertThrows(Jpeg2000Exception.class, () -> Jpeg2000QuantizationSegment.parse(
                segment(Jpeg2000Marker.QCD, new byte[] {0x40, 0x40}), coding));
        assertThrows(Jpeg2000Exception.class, () -> Jpeg2000ComponentCodingStyleSegment.parse(
                segment(Jpeg2000Marker.COC, new byte[] {1, 0, 0, 4, 4, 0, 1}), size));
        assertThrows(Jpeg2000Exception.class, () -> Jpeg2000ComponentQuantizationSegment.parse(
                segment(Jpeg2000Marker.QCC, new byte[] {1, 0x40, 0x40, 0x48, 0x48, 0x50}), size, coding));
    }

    @Test
    void parsesStartOfTileAndComment() throws Exception {
        Jpeg2000SizeSegment size = Jpeg2000SizeSegment.parse(
                segment(Jpeg2000Marker.SIZ, sizePayload(8, 8, 8, 8,
                        new int[][] {{8, 0, 1, 1}})), LIMITS);
        Jpeg2000StartOfTileSegment tile = Jpeg2000StartOfTileSegment.parse(
                segment(Jpeg2000Marker.SOT, new byte[] {0, 0, 0, 0, 0, 18, 0, 1}), size);
        Jpeg2000CommentSegment comment = Jpeg2000CommentSegment.parse(
                segment(Jpeg2000Marker.COM, new byte[] {0, 1, 'J', 'a', 'v', 'a'}));

        assertEquals(0, tile.tileIndex());
        assertEquals(18, tile.tilePartLength());
        assertEquals(0, tile.tilePartIndex());
        assertEquals(1, tile.tilePartCount());
        assertEquals(1, comment.registration());
        assertEquals("Java", comment.text());
    }

    @Test
    void startOfTileRejectsInvalidLengthTileAndPartIndexes() throws Exception {
        Jpeg2000SizeSegment size = Jpeg2000SizeSegment.parse(
                segment(Jpeg2000Marker.SIZ, sizePayload(8, 8, 8, 8,
                        new int[][] {{8, 0, 1, 1}})), LIMITS);

        assertThrows(Jpeg2000Exception.class, () -> Jpeg2000StartOfTileSegment.parse(
                segment(Jpeg2000Marker.SOT, new byte[] {0, 0, 0, 0, 0, 13, 0, 1}), size));
        assertThrows(Jpeg2000Exception.class, () -> Jpeg2000StartOfTileSegment.parse(
                segment(Jpeg2000Marker.SOT, new byte[] {0, 1, 0, 0, 0, 18, 0, 1}), size));
        assertThrows(Jpeg2000Exception.class, () -> Jpeg2000StartOfTileSegment.parse(
                segment(Jpeg2000Marker.SOT, new byte[] {0, 0, 0, 0, 0, 18, 1, 1}), size));
    }

    private static void assertCodingRejected(byte[] payload) {
        assertThrows(Jpeg2000Exception.class, () -> Jpeg2000CodingStyleSegment.parse(
                segment(Jpeg2000Marker.COD, payload), LIMITS));
    }

    private static Jpeg2000MarkerSegment segment(int marker, byte[] payload) {
        return new Jpeg2000MarkerSegment(marker, 0, payload);
    }

    private static byte[] sizePayload(
            int width,
            int height,
            int tileWidth,
            int tileHeight,
            int[][] components) {
        byte[] payload = new byte[36 + components.length * 3];
        writeInt(payload, 2, width);
        writeInt(payload, 6, height);
        writeInt(payload, 18, tileWidth);
        writeInt(payload, 22, tileHeight);
        writeShort(payload, 34, components.length);
        int offset = 36;
        for (int[] component : components) {
            payload[offset++] = (byte) ((component[0] - 1) | (component[1] == 0 ? 0 : 0x80));
            payload[offset++] = (byte) component[2];
            payload[offset++] = (byte) component[3];
        }
        return payload;
    }

    private static void writeShort(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) (value >>> 8);
        bytes[offset + 1] = (byte) value;
    }

    private static void writeInt(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) (value >>> 24);
        bytes[offset + 1] = (byte) (value >>> 16);
        bytes[offset + 2] = (byte) (value >>> 8);
        bytes[offset + 3] = (byte) value;
    }
}
