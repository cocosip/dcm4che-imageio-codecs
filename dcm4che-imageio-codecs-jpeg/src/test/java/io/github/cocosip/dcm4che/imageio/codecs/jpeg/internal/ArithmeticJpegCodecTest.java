package io.github.cocosip.dcm4che.imageio.codecs.jpeg.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ArithmeticJpegCodecTest {
    @Test
    void roundTripsEightBitArithmeticSequentialDctWithDac() throws Exception {
        int width = 13;
        int height = 9;
        int[] samples = new int[width * height];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = (i * 17 + 19) & 0xff;
        }

        byte[] encoded = ArithmeticJpegCodec.encodeSequential(JpegFrame.of(width, height, 1,
                samples));
        JpegFrame decoded = ArithmeticJpegCodec.decode(encoded);

        assertTrue(hasMarker(encoded, 0xc9));
        assertTrue(hasMarker(encoded, 0xcc));
        assertEquals(8, decoded.precision());
        assertTrue(maxDifference(samples, decoded.samples()) <= 70,
                () -> "max difference=" + maxDifference(samples, decoded.samples()));
    }

    @Test
    void roundTripsTwelveBitArithmeticSequentialDct() throws Exception {
        int[] samples = new int[8 * 8];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = (i * 257 + 31) & 0xfff;
        }

        JpegFrame decoded = ArithmeticJpegCodec.decode(ArithmeticJpegCodec.encodeSequential(
                JpegFrame.of(8, 8, 1, samples, 12)));

        assertEquals(12, decoded.precision());
        assertTrue(maxDifference(samples, decoded.samples()) <= 120);
    }

    @Test
    void roundTripsArithmeticProgressiveAndLosslessFamilies() throws Exception {
        int[] samples = new int[9 * 7];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = (i * 31 + 7) & 0xff;
        }
        JpegFrame source = JpegFrame.of(9, 7, 1, samples);

        byte[] progressive = ArithmeticJpegCodec.encodeProgressive(source);
        byte[] lossless = ArithmeticJpegCodec.encodeLossless(source);

        assertTrue(hasMarker(progressive, 0xca));
        assertTrue(hasMarker(lossless, 0xcb));
        assertTrue(maxDifference(samples, ArithmeticJpegCodec.decode(progressive).samples()) <= 70);
        assertEquals(0, maxDifference(samples, ArithmeticJpegCodec.decode(lossless).samples()));
    }

    @Test
    void roundTripsDifferentialArithmeticFamilies() throws Exception {
        int[] samples = new int[8 * 8];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = (i * 9 + 11) & 0xff;
        }
        JpegFrame source = JpegFrame.of(8, 8, 1, samples);

        assertTrue(hasMarker(ArithmeticJpegCodec.encodeDifferentialSequential(source), 0xcd));
        assertTrue(hasMarker(ArithmeticJpegCodec.encodeDifferentialProgressive(source), 0xce));
        assertTrue(hasMarker(ArithmeticJpegCodec.encodeDifferentialLossless(source), 0xcf));
        assertTrue(maxDifference(samples, ArithmeticJpegCodec.decode(
                ArithmeticJpegCodec.encodeDifferentialSequential(source)).samples()) <= 70);
        assertEquals(0, maxDifference(samples, ArithmeticJpegCodec.decode(
                ArithmeticJpegCodec.encodeDifferentialLossless(source)).samples()));
    }

    private static int maxDifference(int[] expected, int[] actual) {
        int max = 0;
        for (int i = 0; i < expected.length; i++) {
            max = Math.max(max, Math.abs(expected[i] - actual[i]));
        }
        return max;
    }

    private static boolean hasMarker(byte[] data, int marker) {
        for (int i = 0; i + 1 < data.length; i++) {
            if ((data[i] & 0xff) == 0xff && (data[i + 1] & 0xff) == marker) {
                return true;
            }
        }
        return false;
    }
}
