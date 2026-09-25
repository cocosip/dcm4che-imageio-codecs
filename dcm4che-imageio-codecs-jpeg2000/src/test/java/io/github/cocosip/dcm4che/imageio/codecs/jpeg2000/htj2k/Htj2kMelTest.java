package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.htj2k;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.imageio.IIOException;

import org.junit.jupiter.api.Test;

class Htj2kMelTest {
    @Test
    void encodesFixedMelStateVector() throws Exception {
        Htj2kMelEncoder encoder = new Htj2kMelEncoder(16);
        for (boolean event : new boolean[] {false, true, false, false, true}) {
            encoder.encode(event);
        }
        assertEquals(5, encoder.bitPosition());
        assertArrayEquals(new byte[] {(byte) 0xb0}, encoder.finish());

        Htj2kMelDecoder decoder = new Htj2kMelDecoder(new byte[] {(byte) 0xb0}, 0, 1);
        assertFalse(decoder.nextEvent());
        assertTrue(decoder.nextEvent());
        assertFalse(decoder.nextEvent());
        assertFalse(decoder.nextEvent());
        assertTrue(decoder.nextEvent());
        assertEquals(5, decoder.bitPosition());
    }

    @Test
    void decodesLongRunsAndPartialTerminalRun() throws Exception {
        boolean[] events = new boolean[103];
        events[100] = true;
        Htj2kMelEncoder encoder = new Htj2kMelEncoder(64);
        for (boolean event : events) {
            encoder.encode(event);
        }
        byte[] encoded = encoder.finish();
        Htj2kMelDecoder decoder = new Htj2kMelDecoder(encoded, 0, encoded.length);
        for (boolean expected : events) {
            assertEquals(expected, decoder.nextEvent());
        }
    }

    @Test
    void reportsTruncationAndOutputLimitAtFirstBit() throws Exception {
        Htj2kMelDecoder empty = new Htj2kMelDecoder(new byte[0], 0, 0);
        IIOException truncated = assertThrows(IIOException.class, empty::nextEvent);
        assertTrue(truncated.getMessage().contains("bit position 0"));

        Htj2kMelEncoder limited = new Htj2kMelEncoder(0);
        limited.encode(false);
        IIOException overflow = assertThrows(IIOException.class, limited::finish);
        assertTrue(overflow.getMessage().contains("bit position 1"));
    }

    @Test
    void forwardViewsStuffAfterFfInBothOrders() throws Exception {
        Htj2kForwardBitStream.Writer msb =
                new Htj2kForwardBitStream.Writer(2, true, "MEL");
        msb.writeBits(0xff, 8);
        msb.writeBits(0x55, 7);
        assertArrayEquals(new byte[] {(byte) 0xff, 0x55}, msb.finish(false));

        Htj2kForwardBitStream.Reader reader = new Htj2kForwardBitStream.Reader(
                new byte[] {(byte) 0xff, 0x55}, 0, 2, true, "MEL");
        assertEquals(0xff, reader.readBits(8));
        assertEquals(0x55, reader.readBits(7));
        assertEquals(15, reader.bitPosition());
        assertThrows(IIOException.class, reader::readBit);

        Htj2kForwardBitStream.Reader malformed = new Htj2kForwardBitStream.Reader(
                new byte[] {(byte) 0xff, (byte) 0x80}, 0, 2, true, "MEL");
        malformed.readBits(8);
        assertTrue(assertThrows(IIOException.class, malformed::readBit)
                .getMessage().contains("stuffed bit at bit position 8"));
    }
}
