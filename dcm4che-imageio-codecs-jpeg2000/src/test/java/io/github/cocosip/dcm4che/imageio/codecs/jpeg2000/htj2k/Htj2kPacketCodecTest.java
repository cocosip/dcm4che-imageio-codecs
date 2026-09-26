package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.htj2k;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.imageio.IIOException;

import org.junit.jupiter.api.Test;

class Htj2kPacketCodecTest {
    @Test
    void encodesOneCleanupContribution() throws Exception {
        byte[] cleanup = hex("FE006300");
        Htj2kPacketCodec.Band band = band(1, 1, block(7, cleanup));
        byte[] packet = Htj2kPacketCodec.encode(Collections.singletonList(band));
        assertArrayEquals(hex("C048FE006300"), packet);
        List<Htj2kPacketCodec.Band> decoded = Htj2kPacketCodec.decode(packet,
                Collections.singletonList(new int[] {1, 1}));
        assertEquals(7, decoded.get(0).blocks.get(0).missingMsbs);
        assertArrayEquals(cleanup, decoded.get(0).blocks.get(0).data);
    }

    @Test
    void emptyAndMixedBandsKeepPacketBoundaries() throws Exception {
        assertArrayEquals(hex("00"), Htj2kPacketCodec.encode(Collections.emptyList()));
        assertEquals(0, Htj2kPacketCodec.decode(hex("00"), Collections.emptyList()).size());
        Htj2kPacketCodec.Band empty = band(1, 1, block(0, new byte[0]));
        assertArrayEquals(hex("00"), Htj2kPacketCodec.encode(
                Collections.singletonList(empty)));
        Htj2kPacketCodec.Band mixed = band(2, 2,
                block(0, new byte[0]), block(7, hex("006300")),
                block(0, new byte[0]), block(6, hex("FE006300")));
        byte[] packet = Htj2kPacketCodec.encode(Arrays.asList(empty, mixed));
        List<Htj2kPacketCodec.Band> decoded = Htj2kPacketCodec.decode(packet,
                Arrays.asList(new int[] {1, 1}, new int[] {2, 2}));
        assertEquals(0, decoded.get(0).blocks.get(0).data.length);
        for (int i = 0; i < 4; i++) {
            assertArrayEquals(mixed.blocks.get(i).data, decoded.get(1).blocks.get(i).data);
        }
    }

    @Test
    void rejectsTruncatedPacketAndUnsupportedPasses() throws Exception {
        assertThrows(IIOException.class, () -> Htj2kPacketCodec.decode(hex("C0"),
                Collections.singletonList(new int[] {1, 1})));
        assertThrows(IIOException.class, () -> Htj2kPacketCodec.decode(hex("F0"),
                Collections.singletonList(new int[] {1, 1})));
    }

    @Test
    void readsOnePacketFromAConcatenatedTilePart() throws Exception {
        byte[] first = Htj2kPacketCodec.encode(Collections.singletonList(
                band(1, 1, block(7, hex("FE006300")))));
        byte[] second = Htj2kPacketCodec.encode(Collections.singletonList(
                band(1, 1, block(0, new byte[0]))));
        byte[] joined = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, joined, first.length, second.length);
        List<int[]> shape = Collections.singletonList(new int[] {1, 1});
        Htj2kPacketCodec.Decoded decoded = Htj2kPacketCodec.decodeNext(
                joined, 0, joined.length, shape);
        assertEquals(first.length, decoded.bytesConsumed);
        assertArrayEquals(hex("FE006300"), decoded.bands.get(0).blocks.get(0).data);
        assertEquals(second.length, Htj2kPacketCodec.decodeNext(
                joined, first.length, joined.length, shape).bytesConsumed);
    }

    @Test
    void preservesCleanupAndRefinementSegmentLengths() throws Exception {
        byte[] data = hex("FE0063000101");
        Htj2kPacketCodec.Contribution contribution =
                new Htj2kPacketCodec.Contribution(5, data, 4, 3);
        byte[] packet = Htj2kPacketCodec.encode(Collections.singletonList(
                band(1, 1, contribution)));
        Htj2kPacketCodec.Contribution parsed = Htj2kPacketCodec.decode(packet,
                Collections.singletonList(new int[] {1, 1})).get(0).blocks.get(0);
        assertEquals(5, parsed.missingMsbs);
        assertEquals(3, parsed.passes);
        assertEquals(4, parsed.cleanupLength);
        assertArrayEquals(data, parsed.data);
        assertThrows(IIOException.class, () -> Htj2kPacketCodec.decode(
                Arrays.copyOf(packet, packet.length - 1),
                Collections.singletonList(new int[] {1, 1})));
    }

    private static Htj2kPacketCodec.Band band(int width, int height,
            Htj2kPacketCodec.Contribution... blocks) {
        return new Htj2kPacketCodec.Band(width, height, Arrays.asList(blocks));
    }

    private static Htj2kPacketCodec.Contribution block(int missingMsbs, byte[] data) {
        return new Htj2kPacketCodec.Contribution(missingMsbs, data);
    }

    private static byte[] hex(String value) {
        byte[] bytes = new byte[value.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(value.substring(2 * i, 2 * i + 2), 16);
        }
        return bytes;
    }
}
