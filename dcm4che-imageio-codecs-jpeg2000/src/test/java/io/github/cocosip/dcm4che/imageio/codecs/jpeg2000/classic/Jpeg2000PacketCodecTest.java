package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.classic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Exception;

class Jpeg2000PacketCodecTest {
    @Test
    void roundTripsMultiLayerContributionsAndEmptyPacketState() throws Exception {
        List<Jpeg2000PacketEncoder.CodeBlock> blocks = Arrays.asList(
                new Jpeg2000PacketEncoder.CodeBlock(
                        0, 0, 2, hex("1020304050"), new int[] {2, 3, 5}),
                new Jpeg2000PacketEncoder.CodeBlock(
                        1, 0, 1, hex("A0B0C0D0"), new int[] {1, 4}));
        Jpeg2000PacketEncoder encoder = new Jpeg2000PacketEncoder(2, 1, blocks);
        Jpeg2000PacketDecoder decoder = new Jpeg2000PacketDecoder(2, 1);

        Jpeg2000PacketEncoder.EncodedPacket first = encoder.encodeLayer(0, new int[] {1, 0});
        List<Jpeg2000PacketDecoder.DecodedContribution> firstDecoded =
                decoder.decodeLayer(0, first.header(), first.body());
        assertEquals(1, firstDecoded.size());
        assertContribution(firstDecoded.get(0), 0, 2, 0, 1, hex("1020"));

        Jpeg2000PacketEncoder.EncodedPacket second = encoder.encodeLayer(1, new int[] {3, 2});
        List<Jpeg2000PacketDecoder.DecodedContribution> secondDecoded =
                decoder.decodeLayer(1, second.header(), second.body());
        assertEquals(2, secondDecoded.size());
        assertContribution(secondDecoded.get(0), 0, 2, 1, 2, hex("304050"));
        assertContribution(secondDecoded.get(1), 1, 1, 0, 2, hex("A0B0C0D0"));

        Jpeg2000PacketEncoder.EncodedPacket empty = encoder.encodeLayer(2, new int[] {3, 2});
        assertArrayEquals(new byte[] {0}, empty.header());
        assertEquals(0, empty.body().length);
        assertTrue(decoder.decodeLayer(2, empty.header(), empty.body()).isEmpty());
    }

    @Test
    void equivalentPrecinctEncodersDoNotShareLayerState() throws Exception {
        List<Jpeg2000PacketEncoder.CodeBlock> blocks = Arrays.asList(
                new Jpeg2000PacketEncoder.CodeBlock(0, 0, 0, hex("1122"), new int[] {2}));
        Jpeg2000PacketEncoder first = new Jpeg2000PacketEncoder(1, 1, blocks);
        Jpeg2000PacketEncoder second = new Jpeg2000PacketEncoder(1, 1, blocks);

        Jpeg2000PacketEncoder.EncodedPacket firstPacket = first.encodeLayer(0, new int[] {1});
        Jpeg2000PacketEncoder.EncodedPacket secondPacket = second.encodeLayer(0, new int[] {1});
        assertArrayEquals(firstPacket.header(), secondPacket.header());
        assertArrayEquals(firstPacket.body(), secondPacket.body());
    }

    @Test
    void rejectsTruncatedHeadersBodiesAndOutOfOrderLayers() throws Exception {
        List<Jpeg2000PacketEncoder.CodeBlock> blocks = Arrays.asList(
                new Jpeg2000PacketEncoder.CodeBlock(0, 0, 0, hex("112233"), new int[] {3}));
        Jpeg2000PacketEncoder encoder = new Jpeg2000PacketEncoder(1, 1, blocks);
        assertThrows(Jpeg2000Exception.class, () -> encoder.encodeLayer(1, new int[] {1}));
        assertThrows(Jpeg2000Exception.class, () -> encoder.encodeLayer(0, new int[] {2}));

        Jpeg2000PacketEncoder.EncodedPacket packet = encoder.encodeLayer(0, new int[] {1});
        Jpeg2000PacketDecoder truncatedHeader = new Jpeg2000PacketDecoder(1, 1);
        assertThrows(Jpeg2000Exception.class,
                () -> truncatedHeader.decodeLayer(0, new byte[0], packet.body()));

        Jpeg2000PacketDecoder truncatedBody = new Jpeg2000PacketDecoder(1, 1);
        assertThrows(Jpeg2000Exception.class,
                () -> truncatedBody.decodeLayer(0, packet.header(), new byte[2]));

        Jpeg2000PacketDecoder wrongLayer = new Jpeg2000PacketDecoder(1, 1);
        assertThrows(Jpeg2000Exception.class,
                () -> wrongLayer.decodeLayer(1, packet.header(), packet.body()));
    }

    @Test
    void passCountCodingCoversEveryStandardPrefixRange() throws Exception {
        for (int passCount : new int[] {1, 2, 3, 5, 6, 36, 37, 164}) {
            Jpeg2000PacketBitWriter writer = new Jpeg2000PacketBitWriter();
            Jpeg2000PacketEncoder.encodePassCount(writer, passCount);
            writer.align();
            Jpeg2000PacketBitReader reader = new Jpeg2000PacketBitReader(
                    writer.toByteArray(), 0, writer.toByteArray().length);
            assertEquals(passCount, Jpeg2000PacketDecoder.decodePassCount(reader));
        }
        assertThrows(IllegalArgumentException.class, () -> Jpeg2000PacketEncoder.encodePassCount(
                new Jpeg2000PacketBitWriter(), 165));
    }

    @Test
    void rejectsAmbiguousCodeBlockLayoutAndPayloadMetadata() {
        List<Jpeg2000PacketEncoder.CodeBlock> reversed = Arrays.asList(
                new Jpeg2000PacketEncoder.CodeBlock(1, 0, 0, new byte[0], new int[0]),
                new Jpeg2000PacketEncoder.CodeBlock(0, 0, 0, new byte[0], new int[0]));
        assertThrows(IllegalArgumentException.class,
                () -> new Jpeg2000PacketEncoder(2, 1, reversed));
        assertThrows(IllegalArgumentException.class,
                () -> new Jpeg2000PacketEncoder.CodeBlock(
                        0, 0, 0, new byte[] {1}, new int[0]));
    }

    @Test
    void matchesIndependentFoDicomInlinePacketVector() throws Exception {
        List<Jpeg2000PacketEncoder.CodeBlock> blocks = Arrays.asList(
                new Jpeg2000PacketEncoder.CodeBlock(0, 0, 2, hex("1020"), new int[] {2}),
                new Jpeg2000PacketEncoder.CodeBlock(1, 0, 1, new byte[0], new int[0]));

        Jpeg2000PacketEncoder.EncodedPacket packet =
                new Jpeg2000PacketEncoder(2, 1, blocks).encodeLayer(0, new int[] {1, 0});

        assertArrayEquals(hex("EA20"), packet.header());
        assertArrayEquals(hex("1020"), packet.body());
    }

    private static void assertContribution(
            Jpeg2000PacketDecoder.DecodedContribution contribution,
            int block,
            int zeroBitPlanes,
            int firstPass,
            int passCount,
            byte[] data) {
        assertEquals(block, contribution.blockIndex());
        assertEquals(zeroBitPlanes, contribution.zeroBitPlanes());
        assertEquals(firstPass, contribution.firstPass());
        assertEquals(passCount, contribution.passCount());
        assertArrayEquals(data, contribution.data());
    }

    private static byte[] hex(String value) {
        byte[] result = new byte[value.length() / 2];
        for (int index = 0; index < result.length; index++) {
            result[index] = (byte) Integer.parseInt(value.substring(index * 2, index * 2 + 2), 16);
        }
        return result;
    }
}
