package io.github.cocosip.dcm4che.imageio.codecs.jpeg.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BaselineJpegCodecTest {
    @Test
    void roundTripsAnEightByEightMonochromeBlock() throws Exception {
        int[] samples = new int[64];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = (i * 17) & 0xff;
        }
        JpegFrame source = JpegFrame.of(8, 8, 1, samples);

        byte[] encoded = BaselineJpegCodec.encode(source);
        JpegFrame decoded = BaselineJpegCodec.decode(encoded);

        assertTrue(encoded.length > 100);
        for (int i = 0; i < samples.length; i++) {
            assertTrue(Math.abs(samples[i] - decoded.samples()[i]) <= 50,
                    "sample " + i + " differs too much: " + samples[i] + " vs " + decoded.samples()[i]);
        }
    }

    @Test
    void roundTripsSequentialDctWithRestartMarkers() throws Exception {
        int[] samples = new int[17 * 11];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = (i * 17 + 9) & 0xff;
        }
        JpegFrame source = JpegFrame.of(17, 11, 1, samples);

        byte[] encoded = BaselineJpegCodec.encode(source, 2);
        JpegFrame decoded = BaselineJpegCodec.decode(encoded);

        assertTrue(hasMarker(encoded, 0xdd));
        assertTrue(hasRestartMarker(encoded));
        assertTrue(maxDifference(samples, decoded.samples()) <= 50);
    }

    @Test
    void rejectsOutOfOrderSequentialDctRestartMarker() throws Exception {
        byte[] encoded = BaselineJpegCodec.encode(JpegFrame.of(17, 11, 1, new int[187]), 2);
        replaceFirstRestartMarker(encoded, 0xd1);

        assertThrows(JpegException.class, () -> BaselineJpegCodec.decode(encoded));
    }

    @Test
    void roundTripsFourTwoTwoSamplingWithMcuEdgeReplication() throws Exception {
        int width = 13;
        int height = 9;
        int[] samples = new int[width * height * 3];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int offset = (y * width + x) * 3;
                samples[offset] = x * 5 + y * 3 + 40;
                samples[offset + 1] = 90;
                samples[offset + 2] = 160;
            }
        }
        JpegFrame source = JpegFrame.of(width, height, 3, samples);

        byte[] encoded = BaselineJpegCodec.encode(source, JpegSampling.SF422);
        JpegFrame decoded = BaselineJpegCodec.decode(encoded);

        assertFrameSampling(encoded, 0x21, 0x11, 0x11);
        assertTrue(maxDifference(samples, decoded.samples()) <= 65,
                () -> "max difference=" + maxDifference(samples, decoded.samples()));
    }

    @Test
    void roundTripsFourTwoZeroSamplingWithMcuEdgeReplication() throws Exception {
        int width = 11;
        int height = 13;
        int[] samples = new int[width * height * 3];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int offset = (y * width + x) * 3;
                samples[offset] = x * 4 + y * 3 + 40;
                samples[offset + 1] = 90;
                samples[offset + 2] = 160;
            }
        }
        JpegFrame source = JpegFrame.of(width, height, 3, samples);

        byte[] encoded = BaselineJpegCodec.encode(source, JpegSampling.SF420);
        JpegFrame decoded = BaselineJpegCodec.decode(encoded);

        assertFrameSampling(encoded, 0x22, 0x11, 0x11);
        assertTrue(maxDifference(samples, decoded.samples()) <= 75,
                () -> "max difference=" + maxDifference(samples, decoded.samples()));
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

    private static boolean hasRestartMarker(byte[] data) {
        for (int i = 0; i + 1 < data.length; i++) {
            if ((data[i] & 0xff) == 0xff && (data[i + 1] & 0xff) >= 0xd0
                    && (data[i + 1] & 0xff) <= 0xd7) {
                return true;
            }
        }
        return false;
    }

    private static void assertFrameSampling(byte[] data, int... expected) {
        for (int i = 0; i + 1 < data.length; i++) {
            if ((data[i] & 0xff) == 0xff && (data[i + 1] & 0xff) == 0xc0) {
                int length = ((data[i + 2] & 0xff) << 8) | (data[i + 3] & 0xff);
                int components = data[i + 9] & 0xff;
                assertTrue(length >= 8 + components * 3);
                for (int component = 0; component < expected.length; component++) {
                    assertEquals(expected[component], data[i + 11 + component * 3] & 0xff);
                }
                return;
            }
        }
        throw new AssertionError("SOF0 marker not found");
    }

    private static void replaceFirstRestartMarker(byte[] data, int marker) {
        for (int i = 0; i + 1 < data.length; i++) {
            if ((data[i] & 0xff) == 0xff && (data[i + 1] & 0xff) >= 0xd0
                    && (data[i + 1] & 0xff) <= 0xd7) {
                data[i + 1] = (byte) marker;
                return;
            }
        }
        throw new AssertionError("restart marker not found");
    }

}
