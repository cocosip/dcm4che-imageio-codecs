package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.htj2k;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.imageio.IIOException;

import org.junit.jupiter.api.Test;

class Htj2kMagSgnTest {
    @Test
    void writesLsbFirstCodewordsAndTerminalOnes() throws Exception {
        Htj2kMagSgnEncoder encoder = new Htj2kMagSgnEncoder(2);
        encoder.encode(0b101, 3);
        encoder.encode(0b10, 2);
        assertEquals(5, encoder.bitPosition());
        assertArrayEquals(new byte[] {(byte) 0xf5}, encoder.finish());

        Htj2kMagSgnDecoder decoder = new Htj2kMagSgnDecoder(
                new byte[] {(byte) 0xf5}, 0, 1);
        assertEquals(0b101, decoder.decode(3));
        assertEquals(0b10, decoder.decode(2));
        assertEquals(5, decoder.bitPosition());
    }

    @Test
    void omitsTerminalFfAndRejectsTruncatedCodeword() throws Exception {
        Htj2kMagSgnEncoder encoder = new Htj2kMagSgnEncoder(2);
        encoder.encode(0xff, 8);
        assertArrayEquals(new byte[0], encoder.finish());

        Htj2kMagSgnDecoder decoder = new Htj2kMagSgnDecoder(new byte[] {0x05}, 0, 1);
        decoder.decode(8);
        IIOException error = assertThrows(IIOException.class, () -> decoder.decode(1));
        assertTrue(error.getMessage().contains("bit position 8"));
    }

    @Test
    void rejectsIllegalStuffedBit() throws Exception {
        Htj2kMagSgnDecoder decoder = new Htj2kMagSgnDecoder(
                new byte[] {(byte) 0xff, (byte) 0x80}, 0, 2);
        decoder.decode(8);
        assertTrue(assertThrows(IIOException.class, () -> decoder.decode(1))
                .getMessage().contains("stuffed bit at bit position 8"));
    }
}
