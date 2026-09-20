package io.github.cocosip.dcm4che.imageio.codecs.rle.internal;

import java.util.Arrays;

import javax.imageio.IIOException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RleSegmentCodecTest {

    @Test
    void decodesLiteralRepeatMixedAndNoOpPackets() throws Exception {
        assertArrayEquals(bytes(10, 11, 12),
                RleSegmentCodec.decode(bytes(2, 10, 11, 12), 3));
        assertArrayEquals(bytes(7, 7, 7),
                RleSegmentCodec.decode(bytes(254, 7), 3));
        assertArrayEquals(bytes(1, 2, 9, 9, 9, 9, 3),
                RleSegmentCodec.decode(bytes(1, 1, 2, 253, 9, 0, 3), 7));
        assertArrayEquals(bytes(5),
                RleSegmentCodec.decode(bytes(128, 0, 5, 128), 1));
    }

    @Test
    void encodesFoDicomCompatibleLiteralRepeatAndMixedPackets() {
        assertArrayEquals(bytes(2, 10, 11, 12),
                RleSegmentCodec.encode(bytes(10, 11, 12)));
        assertArrayEquals(bytes(254, 7),
                RleSegmentCodec.encode(bytes(7, 7, 7)));
        assertArrayEquals(bytes(1, 1, 2, 253, 9, 0, 3),
                RleSegmentCodec.encode(bytes(1, 2, 9, 9, 9, 9, 3)));
    }

    @Test
    void matchesFoDicomPacketizationAtTwoAndThreeByteRuns() {
        assertArrayEquals(bytes(255, 7), RleSegmentCodec.encode(bytes(7, 7)));
        assertArrayEquals(bytes(2, 7, 7, 8), RleSegmentCodec.encode(bytes(7, 7, 8)));
        assertArrayEquals(bytes(254, 7), RleSegmentCodec.encode(bytes(7, 7, 7)));
    }

    @Test
    void splitsLiteralPacketsAt128Bytes() {
        byte[] source128 = increasing(128);
        byte[] expected128 = new byte[129];
        expected128[0] = 127;
        System.arraycopy(source128, 0, expected128, 1, source128.length);
        assertArrayEquals(expected128, RleSegmentCodec.encode(source128));

        byte[] source129 = increasing(129);
        byte[] expected129 = Arrays.copyOf(expected128, 131);
        expected129[129] = 0;
        expected129[130] = (byte) 128;
        assertArrayEquals(expected129, RleSegmentCodec.encode(source129));
    }

    @Test
    void splitsRepeatPacketsAt128Bytes() {
        assertArrayEquals(bytes(130, 7), RleSegmentCodec.encode(repeated(127, 7)));
        assertArrayEquals(bytes(129, 7), RleSegmentCodec.encode(repeated(128, 7)));
        assertArrayEquals(bytes(129, 7, 0, 7), RleSegmentCodec.encode(repeated(129, 7)));
    }

    @Test
    void rejectsTruncatedPacketsAndOutputOverflow() {
        assertThrows(IIOException.class,
                () -> RleSegmentCodec.decode(bytes(2, 10, 11), 3));
        assertThrows(IIOException.class,
                () -> RleSegmentCodec.decode(bytes(254), 3));
        assertThrows(IIOException.class,
                () -> RleSegmentCodec.decode(bytes(2, 10, 11, 12), 2));
        assertThrows(IIOException.class,
                () -> RleSegmentCodec.decode(bytes(254, 7), 2));
    }

    @Test
    void rejectsIncompleteDecodedOutput() {
        assertThrows(IIOException.class,
                () -> RleSegmentCodec.decode(bytes(0, 10), 2));
    }

    private static byte[] increasing(int length) {
        byte[] bytes = new byte[length];
        for (int index = 0; index < length; index++) {
            bytes[index] = (byte) index;
        }
        return bytes;
    }

    private static byte[] repeated(int length, int value) {
        byte[] bytes = new byte[length];
        Arrays.fill(bytes, (byte) value);
        return bytes;
    }

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int index = 0; index < values.length; index++) {
            result[index] = (byte) values[index];
        }
        return result;
    }
}
