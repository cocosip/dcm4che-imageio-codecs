package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.classic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Exception;

class Jpeg2000PacketBitIoTest {
    @Test
    void stuffsTheBitAfterFfAndReadsTheSameBoundedHeader() throws Exception {
        Jpeg2000PacketBitWriter writer = new Jpeg2000PacketBitWriter();
        writer.writeBits(0xff, 8);
        writer.writeBits(0x55, 7);
        writer.align();

        assertArrayEquals(new byte[] {(byte) 0xff, 0x55}, writer.toByteArray());

        Jpeg2000PacketBitReader reader = new Jpeg2000PacketBitReader(
                writer.toByteArray(), 0, writer.toByteArray().length);
        assertEquals(0xff, reader.readBits(8));
        assertEquals(0x55, reader.readBits(7));
        reader.align();
        assertEquals(2, reader.bytesRead());
    }

    @Test
    void rejectsInvalidCountsBoundsAndTruncatedHeaders() throws Exception {
        Jpeg2000PacketBitWriter writer = new Jpeg2000PacketBitWriter();
        assertThrows(IllegalArgumentException.class, () -> writer.writeBit(2));
        assertThrows(IllegalArgumentException.class, () -> writer.writeBits(0, 0));

        assertThrows(IllegalArgumentException.class,
                () -> new Jpeg2000PacketBitReader(new byte[2], 1, 2));

        Jpeg2000PacketBitReader reader = new Jpeg2000PacketBitReader(new byte[] {0}, 0, 1);
        assertEquals(0, reader.readBits(8));
        assertThrows(Jpeg2000Exception.class, reader::readBit);
    }
}
