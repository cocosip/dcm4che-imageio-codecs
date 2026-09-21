package io.github.cocosip.dcm4che.imageio.codecs.jpeg.internal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

class JpegQmCoderTest {
    @Test
    void exposesTheCompleteJpegProbabilityEstimationTable() {
        assertEquals(114, JpegArithmeticContexts.size());
        assertEquals(0x5a1d, JpegArithmeticContexts.qe(0));
        assertEquals(14, JpegArithmeticContexts.nlps(1));
        assertEquals(2, JpegArithmeticContexts.nmps(1));
        assertEquals(1, JpegArithmeticContexts.switchMps(0));
        assertEquals(0x5a1d, JpegArithmeticContexts.qe(113));
    }

    @Test
    void appliesSwitchMpsWhenAnInitialLpsIsEncoded() {
        int[] contexts = new int[1];
        JpegQmCoder.Encoder encoder = new JpegQmCoder.Encoder();

        encoder.encode(contexts, 0, 1);

        assertEquals(0x81, contexts[0]);
    }

    @Test
    void roundTripsContextCodedBitsAndStuffedBytes() throws Exception {
        int[] contexts = new int[4];
        int[] expected = new int[4096];
        for (int i = 0; i < expected.length; i++) {
            expected[i] = ((i * 31 + (i >>> 3)) ^ (i >>> 1)) & 1;
        }

        JpegQmCoder.Encoder encoder = new JpegQmCoder.Encoder();
        for (int i = 0; i < expected.length; i++) {
            encoder.encode(contexts, i & 3, expected[i]);
        }
        byte[] encoded = encoder.finish();
        assertTrue(encoded.length > 0);
        assertStuffedBytes(encoded);

        int[] decodedContexts = new int[4];
        JpegQmCoder.Decoder decoder = new JpegQmCoder.Decoder(encoded);
        int[] actual = new int[expected.length];
        for (int i = 0; i < actual.length; i++) {
            actual[i] = decoder.decode(decodedContexts, i & 3);
        }
        assertArrayEquals(expected, actual);
    }

    @Test
    void resetRestartsTheIntervalAndContextState() throws Exception {
        JpegQmCoder.Encoder encoder = new JpegQmCoder.Encoder();
        int[] contexts = new int[1];
        for (int i = 0; i < 40; i++) {
            encoder.encode(contexts, 0, i & 1);
        }
        byte[] first = encoder.finish();
        encoder.restart();
        contexts[0] = 0;
        for (int i = 0; i < 40; i++) {
            encoder.encode(contexts, 0, (i + 1) & 1);
        }
        byte[] encoded = encoder.finish();
        byte[] second = Arrays.copyOfRange(encoded, first.length, encoded.length);
        assertTrue(second.length > 0);

        JpegQmCoder.Decoder decoder = new JpegQmCoder.Decoder(first);
        int[] decodedContexts = new int[1];
        for (int i = 0; i < 40; i++) {
            assertEquals(i & 1, decoder.decode(decodedContexts, 0));
        }
        decoder = new JpegQmCoder.Decoder(second);
        decodedContexts[0] = 0;
        for (int i = 0; i < 40; i++) {
            assertEquals((i + 1) & 1, decoder.decode(decodedContexts, 0));
        }
    }

    @Test
    void roundTripsTheAnnexDcMagnitudeDecisionSequence() throws Exception {
        int[] contexts = new int[64];
        int[] acContexts = new int[256];
        JpegQmCoder.Encoder encoder = new JpegQmCoder.Encoder();
        int[][] symbols = {{0, 1}, {1, 0}, {2, 1}, {20, 1}, {21, 1}, {22, 1},
                {23, 0}, {37, 0}, {38, 1}, {39, 1}, {0, 1}};
        for (int i = 0; i < symbols.length; i++) {
            int[] symbol = symbols[i];
            encoder.encode(i == symbols.length - 1 ? acContexts : contexts, symbol[0], symbol[1]);
        }
        JpegQmCoder.Decoder decoder = new JpegQmCoder.Decoder(encoder.finish());
        int[] decodedContexts = new int[64];
        int[] decodedAcContexts = new int[256];
        for (int i = 0; i < symbols.length; i++) {
            int[] symbol = symbols[i];
            assertEquals(symbol[1], decoder.decode(i == symbols.length - 1 ? decodedAcContexts
                    : decodedContexts, symbol[0]));
        }
    }

    private static void assertStuffedBytes(byte[] bytes) {
        for (int i = 1; i < bytes.length; i++) {
            if ((bytes[i - 1] & 0xff) == 0xff && bytes[i] == 0) {
                i++;
            } else if ((bytes[i - 1] & 0xff) == 0xff) {
                throw new AssertionError("arithmetic entropy contains an unstuffed 0xff byte");
            }
        }
    }
}
