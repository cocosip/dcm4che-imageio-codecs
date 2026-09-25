package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.classic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.List;

import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;

import org.junit.jupiter.api.Test;

import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000CodestreamReader;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000CodestreamWriter;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Exception;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Limits;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Marker;

class Jpeg2000ClassicCodestreamTest {
    private static final Jpeg2000Limits LIMITS = Jpeg2000Limits.defaults();

    @Test
    void parsesBaselineCodestreamByPsotAndStopsAtEoc() throws Exception {
        byte[] tileData = new byte[] {0x11, (byte) 0xff, (byte) Jpeg2000Marker.EOC, 0x22};
        byte[] codestream = baseline(tileData, true);
        MemoryCacheImageInputStream stream = new MemoryCacheImageInputStream(
                new ByteArrayInputStream(codestream));
        Jpeg2000CodestreamReader markerReader = new Jpeg2000CodestreamReader(
                stream,
                codestream.length,
                LIMITS);

        Jpeg2000ClassicCodestream parsed = new Jpeg2000ClassicCodestreamParser(markerReader, LIMITS).parse();

        assertEquals(8, parsed.size().referenceGridWidth());
        assertEquals(8, parsed.size().referenceGridHeight());
        assertEquals(5, parsed.codingStyle().decompositionLevels());
        assertEquals(16, parsed.quantization().stepSizes().length);
        assertEquals("Java P1", parsed.comments().get(0).text());
        assertArrayEquals(tileData, parsed.tileData());
        assertEquals(codestream.length - 1, parsed.logicalLength());
        assertEquals(1, markerReader.remaining());
        assertEquals(0, stream.readUnsignedByte());
    }

    @Test
    void rejectsJp2BytesBeforeSocAndMissingRequiredMarkers() throws Exception {
        byte[] jp2 = new byte[] {
                0, 0, 0, 12, 0x6a, 0x50, 0x20, 0x20, 0x0d, 0x0a, (byte) 0x87, 0x0a
        };
        Jpeg2000Exception jp2Error = assertThrows(Jpeg2000Exception.class, () -> parse(jp2));
        Jpeg2000Exception prefixError = assertThrows(
                Jpeg2000Exception.class,
                () -> parse(new byte[] {0, (byte) 0xff, Jpeg2000Marker.SOC}));
        Jpeg2000Exception missingError = assertThrows(
                Jpeg2000Exception.class,
                () -> parse(concat(marker(Jpeg2000Marker.SOC), marker(Jpeg2000Marker.EOC))));

        assertTrue(jp2Error.getMessage().contains("JP2"));
        assertTrue(prefixError.getMessage().contains("SOC"));
        assertTrue(missingError.getMessage().contains("SIZ"));
    }

    @Test
    void rejectsDuplicateOutOfOrderAndUnknownSemanticMarkers() throws Exception {
        byte[] duplicateSize = concat(
                marker(Jpeg2000Marker.SOC),
                segment(Jpeg2000Marker.SIZ, sizePayload()),
                segment(Jpeg2000Marker.SIZ, sizePayload()),
                marker(Jpeg2000Marker.EOC));
        byte[] codingBeforeSize = concat(
                marker(Jpeg2000Marker.SOC),
                segment(Jpeg2000Marker.COD, codingPayload()),
                marker(Jpeg2000Marker.EOC));
        byte[] unknown = concat(
                marker(Jpeg2000Marker.SOC),
                segment(Jpeg2000Marker.SIZ, sizePayload()),
                segment(0x65, new byte[0]),
                marker(Jpeg2000Marker.EOC));

        assertThrows(Jpeg2000Exception.class, () -> parse(duplicateSize));
        assertThrows(Jpeg2000Exception.class, () -> parse(codingBeforeSize));
        Jpeg2000Exception unknownError = assertThrows(Jpeg2000Exception.class, () -> parse(unknown));
        assertTrue(unknownError.getMessage().contains("0x65"));
    }

    @Test
    void rejectsTruncatedTilePartMissingEocAndZeroPsot() throws Exception {
        byte[] baseline = baseline(new byte[] {1, 2, 3}, false);
        byte[] truncated = java.util.Arrays.copyOf(baseline, baseline.length - 3);
        byte[] missingEoc = java.util.Arrays.copyOf(baseline, baseline.length - 2);
        byte[] zeroPsot = baseline.clone();
        int sot = indexOf(zeroPsot, new byte[] {(byte) 0xff, (byte) Jpeg2000Marker.SOT});
        zeroPsot[sot + 6] = 0;
        zeroPsot[sot + 7] = 0;
        zeroPsot[sot + 8] = 0;
        zeroPsot[sot + 9] = 0;

        assertThrows(Jpeg2000Exception.class, () -> parse(truncated));
        assertThrows(Jpeg2000Exception.class, () -> parse(missingEoc));
        Jpeg2000Exception zeroError = assertThrows(Jpeg2000Exception.class, () -> parse(zeroPsot));
        assertTrue(zeroError.getMessage().contains("Psot=0"));
    }

    @Test
    void writesDeterministicBaselineCodestreamWithLogicalPsot() throws Exception {
        byte[] tileData = new byte[] {1, 2, 3};
        Jpeg2000ClassicCodestream model = parse(baseline(tileData, false));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        MemoryCacheImageOutputStream stream = new MemoryCacheImageOutputStream(bytes);
        Jpeg2000ClassicCodestreamWriter writer = new Jpeg2000ClassicCodestreamWriter(
                new Jpeg2000CodestreamWriter(stream, LIMITS));

        writer.write(model);
        stream.flush();

        assertArrayEquals(baseline(tileData, false), bytes.toByteArray());
        int sot = indexOf(bytes.toByteArray(), new byte[] {(byte) 0xff, (byte) Jpeg2000Marker.SOT});
        assertEquals(17, readInt(bytes.toByteArray(), sot + 6));
    }

    @Test
    void parsesMultipleTilesAndInterleavedOrderedTileParts() throws Exception {
        byte[] codestream = concat(
                mainHeader(true),
                tilePart(0, 0, 2, new byte[] {0x10}),
                tilePart(1, 0, 2, new byte[] {0x20, 0x21}),
                tilePart(0, 1, 2, new byte[] {0x11, 0x12}),
                tilePart(1, 1, 2, new byte[] {0x22}),
                marker(Jpeg2000Marker.EOC));

        Jpeg2000ClassicCodestream parsed = parse(codestream);
        List<Jpeg2000ClassicTilePart> parts = parsed.tileParts();

        assertEquals(4, parts.size());
        assertTilePart(parts.get(0), 0, 0, 2, new byte[] {0x10});
        assertTilePart(parts.get(1), 1, 0, 2, new byte[] {0x20, 0x21});
        assertTilePart(parts.get(2), 0, 1, 2, new byte[] {0x11, 0x12});
        assertTilePart(parts.get(3), 1, 1, 2, new byte[] {0x22});
        assertEquals(codestream.length, parsed.logicalLength());

        ByteArrayOutputStream rewritten = new ByteArrayOutputStream();
        MemoryCacheImageOutputStream output = new MemoryCacheImageOutputStream(rewritten);
        new Jpeg2000ClassicCodestreamWriter(
                new Jpeg2000CodestreamWriter(output, LIMITS)).write(parsed);
        output.flush();
        assertArrayEquals(codestream, rewritten.toByteArray());
    }

    @Test
    void rejectsOutOfOrderAndInconsistentTilePartDeclarations() throws Exception {
        byte[] startsAtOne = concat(
                mainHeader(false),
                tilePart(0, 1, 2, new byte[0]),
                marker(Jpeg2000Marker.EOC));
        byte[] skipsPart = concat(
                mainHeader(false),
                tilePart(0, 0, 3, new byte[0]),
                tilePart(0, 2, 3, new byte[0]),
                marker(Jpeg2000Marker.EOC));
        byte[] inconsistentCount = concat(
                mainHeader(false),
                tilePart(0, 0, 2, new byte[0]),
                tilePart(0, 1, 3, new byte[0]),
                marker(Jpeg2000Marker.EOC));

        assertThrows(Jpeg2000Exception.class, () -> parse(startsAtOne));
        assertThrows(Jpeg2000Exception.class, () -> parse(skipsPart));
        assertThrows(Jpeg2000Exception.class, () -> parse(inconsistentCount));
    }

    @Test
    void rejectsMissingTilesAndDeclaredTileParts() throws Exception {
        byte[] missingTile = concat(
                mainHeader(true),
                tilePart(0, 0, 1, new byte[0]),
                marker(Jpeg2000Marker.EOC));
        byte[] missingPart = concat(
                mainHeader(false),
                tilePart(0, 0, 2, new byte[0]),
                marker(Jpeg2000Marker.EOC));

        assertThrows(Jpeg2000Exception.class, () -> parse(missingTile));
        assertThrows(Jpeg2000Exception.class, () -> parse(missingPart));
    }

    @Test
    void rejectsTilePartLengthThatDoesNotReachItsBoundary() throws Exception {
        byte[] tooLong = concat(
                mainHeader(false),
                tilePartWithLength(0, 0, 1, 18, new byte[] {1}),
                marker(Jpeg2000Marker.EOC));

        Jpeg2000Exception error = assertThrows(Jpeg2000Exception.class, () -> parse(tooLong));
        assertTrue(error.getMessage().contains("tile-part"));
    }

    @Test
    void validatesTlmAgainstSotAndRejectsUnsupportedPacketLengthMarkers() throws Exception {
        byte[] tlm = segment(Jpeg2000Marker.TLM,
                new byte[] {0, 0x60, 0, 0, 0, 0, 0, 17});
        byte[] valid = concat(mainHeader(false), tlm,
                tilePart(0, 0, 1, new byte[] {1, 2, 3}), marker(Jpeg2000Marker.EOC));
        assertEquals(1, parse(valid).tileParts().size());

        byte[] wrongLength = tlm.clone();
        wrongLength[wrongLength.length - 1] = 18;
        assertThrows(Jpeg2000Exception.class, () -> parse(concat(mainHeader(false), wrongLength,
                tilePart(0, 0, 1, new byte[] {1, 2, 3}), marker(Jpeg2000Marker.EOC))));

        for (int marker : new int[] {Jpeg2000Marker.PLM, Jpeg2000Marker.PLT}) {
            byte[] stream = concat(mainHeader(false), segment(marker, new byte[] {0}),
                    tilePart(0, 0, 1, new byte[] {1}), marker(Jpeg2000Marker.EOC));
            assertThrows(Jpeg2000Exception.class, () -> parse(stream));
        }
    }

    private static Jpeg2000ClassicCodestream parse(byte[] bytes) throws Exception {
        MemoryCacheImageInputStream stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes));
        return new Jpeg2000ClassicCodestreamParser(
                new Jpeg2000CodestreamReader(stream, bytes.length, LIMITS),
                LIMITS).parse();
    }

    private static byte[] baseline(byte[] tileData, boolean trailingPadding) throws Exception {
        byte[] result = concat(
                mainHeader(false),
                tilePart(0, 0, 1, tileData),
                marker(Jpeg2000Marker.EOC));
        return trailingPadding ? concat(result, new byte[] {0}) : result;
    }

    private static byte[] mainHeader(boolean twoTiles) throws Exception {
        return concat(
                marker(Jpeg2000Marker.SOC),
                segment(Jpeg2000Marker.SIZ, sizePayload(twoTiles)),
                segment(Jpeg2000Marker.COD, codingPayload()),
                segment(Jpeg2000Marker.QCD, quantizationPayload()),
                segment(Jpeg2000Marker.COM, new byte[] {0, 1, 'J', 'a', 'v', 'a', ' ', 'P', '1'}));
    }

    private static byte[] tilePart(
            int tile, int part, int count, byte[] data) throws Exception {
        return tilePartWithLength(tile, part, count, data.length + 14, data);
    }

    private static byte[] tilePartWithLength(
            int tile, int part, int count, int length, byte[] data) throws Exception {
        return concat(
                segment(Jpeg2000Marker.SOT, new byte[] {
                        (byte) (tile >>> 8), (byte) tile,
                        (byte) (length >>> 24), (byte) (length >>> 16),
                        (byte) (length >>> 8), (byte) length,
                        (byte) part, (byte) count
                }),
                marker(Jpeg2000Marker.SOD),
                data);
    }

    private static byte[] sizePayload() throws Exception {
        return sizePayload(false);
    }

    private static byte[] sizePayload(boolean twoTiles) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeShort(0);
        output.writeInt(twoTiles ? 16 : 8);
        output.writeInt(8);
        output.writeInt(0);
        output.writeInt(0);
        output.writeInt(8);
        output.writeInt(8);
        output.writeInt(0);
        output.writeInt(0);
        output.writeShort(1);
        output.writeByte(7);
        output.writeByte(1);
        output.writeByte(1);
        return bytes.toByteArray();
    }

    private static void assertTilePart(
            Jpeg2000ClassicTilePart part,
            int tile,
            int index,
            int count,
            byte[] data) {
        assertEquals(tile, part.startOfTile().tileIndex());
        assertEquals(index, part.startOfTile().tilePartIndex());
        assertEquals(count, part.startOfTile().tilePartCount());
        assertArrayEquals(data, part.data());
    }

    private static byte[] codingPayload() {
        return new byte[] {0, 0, 0, 1, 0, 5, 4, 4, 0, 1};
    }

    private static byte[] quantizationPayload() {
        return new byte[] {
                0x40,
                0x40,
                0x48, 0x48, 0x50,
                0x48, 0x48, 0x50,
                0x48, 0x48, 0x50,
                0x48, 0x48, 0x50,
                0x48, 0x48, 0x50
        };
    }

    private static byte[] marker(int marker) {
        return new byte[] {(byte) 0xff, (byte) marker};
    }

    private static byte[] segment(int marker, byte[] payload) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeByte(0xff);
        output.writeByte(marker);
        output.writeShort(payload.length + 2);
        output.write(payload);
        return bytes.toByteArray();
    }

    private static byte[] concat(byte[]... parts) {
        int length = 0;
        for (byte[] part : parts) {
            length += part.length;
        }
        byte[] result = new byte[length];
        int offset = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, result, offset, part.length);
            offset += part.length;
        }
        return result;
    }

    private static int indexOf(byte[] bytes, byte[] target) {
        for (int index = 0; index <= bytes.length - target.length; index++) {
            boolean equal = true;
            for (int targetIndex = 0; targetIndex < target.length; targetIndex++) {
                if (bytes[index + targetIndex] != target[targetIndex]) {
                    equal = false;
                    break;
                }
            }
            if (equal) {
                return index;
            }
        }
        return -1;
    }

    private static int readInt(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xff) << 24)
                | ((bytes[offset + 1] & 0xff) << 16)
                | ((bytes[offset + 2] & 0xff) << 8)
                | (bytes[offset + 3] & 0xff);
    }
}
