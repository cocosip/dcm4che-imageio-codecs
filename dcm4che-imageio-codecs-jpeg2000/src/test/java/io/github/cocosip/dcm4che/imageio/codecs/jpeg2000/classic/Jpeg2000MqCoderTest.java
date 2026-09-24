package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.classic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Exception;

class Jpeg2000MqCoderTest {
    @Test
    void exposesTheStandardFortySevenStateTable() {
        assertEquals(47, Jpeg2000MqStateTable.size());
        assertEquals(0x5601, Jpeg2000MqStateTable.qe(0));
        assertEquals(38, Jpeg2000MqStateTable.nmps(5));
        assertEquals(33, Jpeg2000MqStateTable.nlps(5));
        assertTrue(Jpeg2000MqStateTable.switchMps(0));
        assertEquals(0x0001, Jpeg2000MqStateTable.qe(45));
        assertEquals(0x5601, Jpeg2000MqStateTable.qe(46));
    }

    @Test
    void encodesAndDecodesContextSymbolsAcrossRefillAndStateTransitions() throws Exception {
        int[] expected = new int[8192];
        for (int index = 0; index < expected.length; index++) {
            expected[index] = ((index * 31 + (index >>> 3)) ^ (index >>> 1)) & 1;
        }

        Jpeg2000MqCoder encoder = new Jpeg2000MqCoder(4);
        for (int index = 0; index < expected.length; index++) {
            encoder.encode(expected[index], index & 3);
        }
        byte[] encoded = encoder.flush();
        assertTrue(encoded.length > 0);

        Jpeg2000MqDecoder decoder = new Jpeg2000MqDecoder(encoded, 4);
        for (int index = 0; index < expected.length; index++) {
            assertEquals(expected[index], decoder.decode(index & 3));
        }
    }

    @Test
    void flushIsIdempotentAndResetRestartsTheCoder() throws Exception {
        Jpeg2000MqCoder encoder = new Jpeg2000MqCoder(1);
        for (int index = 0; index < 64; index++) {
            encoder.encode(index & 1, 0);
        }
        byte[] first = encoder.flush();
        assertArrayEquals(first, encoder.flush());

        encoder.reset();
        for (int index = 0; index < 64; index++) {
            encoder.encode((index + 1) & 1, 0);
        }
        byte[] second = encoder.flush();
        assertTrue(second.length > 0);
        assertTrue(!Arrays.equals(first, second));

        Jpeg2000MqDecoder decoder = new Jpeg2000MqDecoder(second, 1);
        for (int index = 0; index < 64; index++) {
            assertEquals((index + 1) & 1, decoder.decode(0));
        }
    }

    @Test
    void keepsEveryByteAfterFfWithinTheSevenBitMqRange() {
        Jpeg2000MqCoder encoder = new Jpeg2000MqCoder(2);
        for (int index = 0; index < 8192; index++) {
            encoder.encode((index % 17 == 0) ? 1 : 0, index & 1);
        }

        byte[] encoded = encoder.flush();
        for (int index = 1; index < encoded.length; index++) {
            if ((encoded[index - 1] & 0xff) == 0xff) {
                assertTrue((encoded[index] & 0xff) <= 0x8f,
                        "MQ byte after 0xff must use the reduced seven-bit range");
            }
        }
    }

    @Test
    void rejectsInvalidContextsAndShortEntropyInput() throws Exception {
        Jpeg2000MqCoder encoder = new Jpeg2000MqCoder(1);
        assertThrows(IllegalArgumentException.class, () -> encoder.encode(0, 1));
        assertThrows(IllegalArgumentException.class, () -> encoder.encode(0, -1));
        assertThrows(Jpeg2000Exception.class, () -> new Jpeg2000MqDecoder(new byte[] {0}, 1));

        Jpeg2000MqDecoder decoder = new Jpeg2000MqDecoder(new byte[] {0, 0}, 1);
        assertThrows(IllegalArgumentException.class, () -> decoder.decode(-1));
        assertThrows(Jpeg2000Exception.class,
                () -> new Jpeg2000MqDecoder(new byte[] {(byte) 0xff, (byte) 0x90}, 1));
    }

    @Test
    void matchesIndependentFoDicomMqVectorInBothDirections() throws Exception {
        int[] symbols = {
                1, 0, 1, 1, 0, 0, 1, 0, 1, 0,
                0, 1, 1, 1, 0, 1, 0, 0, 1, 1,
                1, 1, 0, 0, 1, 0, 1, 1, 0, 1,
                0, 0, 1, 0, 0, 1, 1, 0, 1, 0
        };
        byte[] foreign = hex("B98D284C028E");

        Jpeg2000MqCoder encoder = new Jpeg2000MqCoder(2);
        for (int index = 0; index < symbols.length; index++) {
            if (index == 20) {
                encoder.resetContexts();
            }
            encoder.encode(symbols[index], index & 1);
        }
        assertArrayEquals(foreign, encoder.flush());

        Jpeg2000MqDecoder decoder = new Jpeg2000MqDecoder(foreign, 2);
        for (int index = 0; index < symbols.length; index++) {
            if (index == 20) {
                decoder.resetContexts();
            }
            assertEquals(symbols[index], decoder.decode(index & 1));
        }
    }

    private static byte[] hex(String value) {
        byte[] result = new byte[value.length() / 2];
        for (int index = 0; index < result.length; index++) {
            result[index] = (byte) Integer.parseInt(value.substring(index * 2, index * 2 + 2), 16);
        }
        return result;
    }
}
