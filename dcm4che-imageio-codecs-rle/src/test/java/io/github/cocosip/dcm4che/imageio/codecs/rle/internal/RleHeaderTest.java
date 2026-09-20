package io.github.cocosip.dcm4che.imageio.codecs.rle.internal;

import javax.imageio.IIOException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RleHeaderTest {

    @Test
    void parsesSegmentCountAndLittleEndianOffsets() throws Exception {
        byte[] frame = frame(80, 2, 64, 70);

        RleHeader header = RleHeader.parse(frame);

        assertEquals(2, header.segmentCount());
        assertEquals(64, header.segmentOffset(0));
        assertEquals(70, header.segmentOffset(1));
        assertArrayEquals(new int[] { 64, 70 }, header.segmentOffsets());
    }

    @Test
    void writesExactFixedLengthHeaderAndDefensivelyCopiesOffsets() throws Exception {
        int[] offsets = { 64, 0x01020304 };
        RleHeader header = RleHeader.of(offsets);
        offsets[0] = 65;

        byte[] bytes = header.toBytes();

        assertEquals(64, bytes.length);
        assertArrayEquals(new byte[] { 2, 0, 0, 0 }, slice(bytes, 0, 4));
        assertArrayEquals(new byte[] { 64, 0, 0, 0 }, slice(bytes, 4, 8));
        assertArrayEquals(new byte[] { 4, 3, 2, 1 }, slice(bytes, 8, 12));
        assertArrayEquals(new byte[52], slice(bytes, 12, 64));

        int[] returned = header.segmentOffsets();
        returned[0] = 66;
        assertEquals(64, header.segmentOffset(0));
    }

    @Test
    void rejectsFrameShorterThanHeader() {
        assertThrows(IIOException.class, () -> RleHeader.parse(new byte[63]));
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, 16 })
    void rejectsInvalidSegmentCount(int segmentCount) {
        assertThrows(IIOException.class, () -> RleHeader.parse(frame(65, segmentCount, 64)));
    }

    @Test
    void rejectsOffsetsOutsideFrame() {
        assertThrows(IIOException.class, () -> RleHeader.parse(frame(65, 1, 63)));
        assertThrows(IIOException.class, () -> RleHeader.parse(frame(65, 1, 65)));
    }

    @Test
    void rejectsFirstSegmentThatDoesNotStartAfterHeader() {
        assertThrows(IIOException.class, () -> RleHeader.parse(frame(70, 1, 65)));
        assertThrows(IIOException.class, () -> RleHeader.of(new int[] { 65 }));
    }

    @Test
    void rejectsEqualOrDescendingOffsets() {
        assertThrows(IIOException.class, () -> RleHeader.parse(frame(80, 2, 64, 64)));
        assertThrows(IIOException.class, () -> RleHeader.parse(frame(80, 2, 70, 64)));
    }

    private static byte[] frame(int length, int segmentCount, int... offsets) {
        byte[] bytes = new byte[length];
        writeIntLittleEndian(bytes, 0, segmentCount);
        for (int index = 0; index < offsets.length; index++) {
            writeIntLittleEndian(bytes, 4 + index * 4, offsets[index]);
        }
        return bytes;
    }

    private static void writeIntLittleEndian(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) value;
        bytes[offset + 1] = (byte) (value >>> 8);
        bytes[offset + 2] = (byte) (value >>> 16);
        bytes[offset + 3] = (byte) (value >>> 24);
    }

    private static byte[] slice(byte[] source, int from, int to) {
        byte[] result = new byte[to - from];
        System.arraycopy(source, from, result, 0, result.length);
        return result;
    }
}
