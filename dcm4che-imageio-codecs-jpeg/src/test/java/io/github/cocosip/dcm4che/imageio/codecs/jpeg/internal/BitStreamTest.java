package io.github.cocosip.dcm4che.imageio.codecs.jpeg.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;

import org.junit.jupiter.api.Test;

class BitStreamTest {
    @Test
    void writesAndReadsMostSignificantBitFirst() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        BitWriter writer = new BitWriter(new MemoryCacheImageOutputStream(bytes));
        writer.writeBits(0b101, 3);
        writer.writeBits(0b11001100, 8);
        writer.flush();

        byte[] encoded = bytes.toByteArray();
        assertArrayEquals(new byte[] {(byte) 0xb9, (byte) 0x9f}, encoded);

        BitReader reader = new BitReader(new MemoryCacheImageInputStream(
                new ByteArrayInputStream(encoded)));
        assertEquals(0b101, reader.readBits(3));
        assertEquals(0b11001100, reader.readBits(8));
    }

    @Test
    void stuffsZeroAfterEntropyByteFF() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        BitWriter writer = new BitWriter(new MemoryCacheImageOutputStream(bytes));
        writer.writeBits(0xff, 8);
        writer.flush();

        assertArrayEquals(new byte[] {(byte) 0xff, 0}, bytes.toByteArray());
    }

    @Test
    void rejectsTruncatedBitInput() throws Exception {
        BitReader reader = new BitReader(new MemoryCacheImageInputStream(
                new ByteArrayInputStream(new byte[] {1})));
        reader.readBits(8);
        org.junit.jupiter.api.Assertions.assertThrows(
                JpegException.class, () -> reader.readBits(1));
    }
}
