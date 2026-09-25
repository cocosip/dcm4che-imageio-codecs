package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.htj2k;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.imageio.IIOException;

import org.junit.jupiter.api.Test;

class Htj2kUvlcTest {
    @Test
    void encodesFixedSingleAndPairedPrefixes() throws Exception {
        Htj2kReverseBitStream.Writer writer = new Htj2kReverseBitStream.Writer(8, "UVLC");
        Htj2kUvlc.encodePair(false, 3, 2, writer);
        byte[] encoded = writer.finish();
        assertArrayEquals(new byte[] {0x14}, encoded);
        Htj2kReverseBitStream.Reader reader =
                new Htj2kReverseBitStream.Reader(encoded, 0, encoded.length, "UVLC");
        assertArrayEquals(new int[] {3, 2},
                Htj2kUvlc.decodePair(false, true, true, false, reader));
    }

    @Test
    void roundTripsInitialAndSubsequentRowModes() throws Exception {
        int checked = 0;
        for (boolean firstRow : new boolean[] {true, false}) {
            for (int u0 = 0; u0 <= 32; u0++) {
                for (int u1 = 0; u1 <= 32; u1++) {
                    Htj2kReverseBitStream.Writer writer =
                            new Htj2kReverseBitStream.Writer(16, "UVLC");
                    Htj2kUvlc.encodePair(firstRow, u0, u1, writer);
                    byte[] encoded = writer.finish();
                    Htj2kReverseBitStream.Reader reader =
                            new Htj2kReverseBitStream.Reader(encoded, 0, encoded.length, "UVLC");
                    assertArrayEquals(new int[] {u0, u1}, Htj2kUvlc.decodePair(firstRow,
                            u0 > 0, u1 > 0, Htj2kUvlc.initialMelEvent(u0, u1), reader));
                    checked++;
                }
            }
        }
        assertEquals(2 * 33 * 33, checked);
        assertFalse(Htj2kUvlc.initialMelEvent(3, 2));
        assertTrue(Htj2kUvlc.initialMelEvent(3, 3));
    }

    @Test
    void rejectsUnsupportedExtensionAndTruncation() throws Exception {
        Htj2kReverseBitStream.Writer writer = new Htj2kReverseBitStream.Writer(1, "UVLC");
        assertThrows(IIOException.class, () -> Htj2kUvlc.encodePair(false, 33, 0, writer));
        Htj2kReverseBitStream.Reader empty =
                new Htj2kReverseBitStream.Reader(new byte[0], 0, 0, "UVLC");
        assertTrue(assertThrows(IIOException.class,
                () -> Htj2kUvlc.decodePair(false, true, false, false, empty))
                .getMessage().contains("reverse bit position 0"));
    }
}
