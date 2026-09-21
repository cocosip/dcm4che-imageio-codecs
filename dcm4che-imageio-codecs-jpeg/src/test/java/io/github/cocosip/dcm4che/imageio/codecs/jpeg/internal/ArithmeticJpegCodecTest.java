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
        assertTrue(maxDifference(samples, decoded.samples()) <= 120,
                () -> "max difference=" + maxDifference(samples, decoded.samples()));
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
        JpegFrame progressiveDecoded = ArithmeticJpegCodec.decode(progressive);
        assertTrue(maxDifference(samples, progressiveDecoded.samples()) <= 70,
                () -> "max difference=" + maxDifference(samples, progressiveDecoded.samples()));
        assertEquals(0, maxDifference(samples, ArithmeticJpegCodec.decode(lossless).samples()));
    }

    @Test
    void arithmeticLosslessUsesPredictorAndRestartSegments() throws Exception {
        int width = 11;
        int height = 6;
        int[] samples = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                samples[y * width + x] = (x * 17 + y * 29 + (x * y)) & 0xff;
            }
        }
        JpegFrame source = JpegFrame.of(width, height, 1, samples);

        byte[] encoded = ArithmeticJpegCodec.encodeLossless(source, 7, 4);

        assertTrue(hasMarker(encoded, 0xcb));
        assertTrue(hasMarker(encoded, 0xdd));
        assertTrue(hasMarker(encoded, 0xd0));
        assertEquals(0, maxDifference(samples, ArithmeticJpegCodec.decode(encoded).samples()));
    }

    @Test
    void arithmeticLosslessUsesCustomDcConditioning() throws Exception {
        int[] samples = new int[9 * 7];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = (i * 31 + 7) & 0xff;
        }
        JpegFrame source = JpegFrame.of(9, 7, 1, samples);

        byte[] encoded = ArithmeticJpegCodec.encodeLossless(source, 1, 0, 2, 6);

        assertTrue(hasDacDcConditioning(encoded, 2, 6));
        assertEquals(0, maxDifference(samples, ArithmeticJpegCodec.decode(encoded).samples()));
    }

    @Test
    void arithmeticProgressiveEmitsSeparateDcAndAcScans() throws Exception {
        byte[] encoded = ArithmeticJpegCodec.encodeProgressive(JpegFrame.of(9, 7, 1,
                new int[9 * 7]));

        assertEquals(2, countMarker(encoded, 0xda));
        assertTrue(hasScanRange(encoded, 0, 0));
        assertTrue(hasScanRange(encoded, 1, 63));
    }

    @Test
    void arithmeticProgressiveUsesCustomDacConditioning() throws Exception {
        int[] samples = new int[9 * 7];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = (i * 31 + 7) & 0xff;
        }
        JpegFrame source = JpegFrame.of(9, 7, 1, samples);

        byte[] encoded = ArithmeticJpegCodec.encodeProgressive(source, 1, 3, 7);

        assertTrue(hasDacConditioning(encoded, 1, 3, 7));
        int[] decoded = ArithmeticJpegCodec.decode(encoded).samples();
        assertTrue(maxDifference(samples, decoded) <= 70,
                () -> "max difference=" + maxDifference(samples, decoded));
    }

    @Test
    void arithmeticSequentialSupportsRestartIntervals() throws Exception {
        int[] samples = new int[17 * 9];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = (i * 13 + 5) & 0xff;
        }
        byte[] encoded = ArithmeticJpegCodec.encodeSequential(JpegFrame.of(17, 9, 1, samples), 2);

        assertTrue(hasMarker(encoded, 0xdd));
        assertTrue(hasMarker(encoded, 0xd0));
        int[] decoded = ArithmeticJpegCodec.decode(encoded).samples();
        assertTrue(maxDifference(samples, decoded) <= 100,
                () -> "max difference=" + maxDifference(samples, decoded));
    }

    @Test
    void arithmeticConditioningParametersDriveDctContexts() throws Exception {
        int[] samples = new int[8 * 8];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = (i * 37 + 9) & 0xff;
        }
        JpegFrame source = JpegFrame.of(8, 8, 1, samples);

        int[][] conditioning = {{0, 1, 7}, {0, 3, 5}, {1, 3, 5}, {0, 3, 7}};
        for (int[] parameters : conditioning) {
            byte[] encoded = ArithmeticJpegCodec.encodeSequential(source, 0,
                    parameters[0], parameters[1], parameters[2]);
            assertTrue(hasDacConditioning(encoded, parameters[0], parameters[1], parameters[2]));
            assertTrue(maxDifference(samples, ArithmeticJpegCodec.decode(encoded).samples()) <= 70,
                    () -> "conditioning=" + java.util.Arrays.toString(parameters));
        }
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
        JpegFrame differentialDecoded = ArithmeticJpegCodec.decode(
                ArithmeticJpegCodec.encodeDifferentialSequential(source));
        assertTrue(maxDifference(samples, differentialDecoded.samples()) <= 70,
                () -> "max difference=" + maxDifference(samples, differentialDecoded.samples()));
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

    private static boolean hasDacConditioning(byte[] data, int dcL, int dcU, int acK) {
        int[] expected = {0xff, 0xcc, 0, 6, 0, (dcU << 4) | dcL, 0x10, acK};
        for (int i = 0; i + expected.length <= data.length; i++) {
            boolean match = true;
            for (int j = 0; j < expected.length; j++) {
                if ((data[i + j] & 0xff) != expected[j]) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasDacDcConditioning(byte[] data, int dcL, int dcU) {
        int[] expected = {0xff, 0xcc, 0, 4, 0, (dcU << 4) | dcL};
        for (int i = 0; i + expected.length <= data.length; i++) {
            boolean match = true;
            for (int j = 0; j < expected.length; j++) {
                if ((data[i + j] & 0xff) != expected[j]) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return true;
            }
        }
        return false;
    }

    private static int countMarker(byte[] data, int marker) {
        int count = 0;
        for (int i = 0; i + 1 < data.length; i++) {
            if ((data[i] & 0xff) == 0xff && (data[i + 1] & 0xff) == marker) {
                count++;
            }
        }
        return count;
    }

    private static boolean hasScanRange(byte[] data, int ss, int se) {
        for (int i = 0; i + 4 < data.length; i++) {
            if ((data[i] & 0xff) == 0xff && (data[i + 1] & 0xff) == 0xda) {
                int length = ((data[i + 2] & 0xff) << 8) | (data[i + 3] & 0xff);
                int payload = i + 4;
                if (payload + length - 2 <= data.length && (data[payload + length - 5] & 0xff) == ss
                        && (data[payload + length - 4] & 0xff) == se) {
                    return true;
                }
            }
        }
        return false;
    }
}
