package io.github.cocosip.dcm4che.imageio.codecs.jpeg.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ExtendedJpegCodecTest {
    @Test
    void roundTripsTwelveBitMonochromeUsingSof1() throws Exception {
        int[] samples = new int[64];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = (i * 257) & 0xfff;
        }

        byte[] encoded = ExtendedJpegCodec.encode(JpegFrame.of(8, 8, 1, samples, 12));
        JpegFrame decoded = ExtendedJpegCodec.decode(encoded);

        assertTrue(hasMarker(encoded, 0xc1));
        assertEquals(12, decoded.precision());
        for (int i = 0; i < samples.length; i++) {
            assertTrue(Math.abs(samples[i] - decoded.samples()[i]) <= 80,
                    "sample " + i + " differs too much");
        }
    }

    @Test
    void acceptsEightBitExtendedFrames() throws Exception {
        int[] samples = new int[64];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = (i * 17) & 0xff;
        }

        byte[] encoded = ExtendedJpegCodec.encode(JpegFrame.of(8, 8, 1, samples, 8));
        JpegFrame decoded = ExtendedJpegCodec.decode(encoded);

        assertEquals(8, decoded.precision());
        assertTrue(decoded.samples()[63] >= 0 && decoded.samples()[63] <= 255);
    }

    @Test
    void roundTripsTwelveBitRgbUsingSof1() throws Exception {
        int[] samples = new int[8 * 8 * 3];
        for (int i = 0; i < samples.length; i += 3) {
            samples[i] = (i * 13) & 0xfff;
            samples[i + 1] = (i * 17) & 0xfff;
            samples[i + 2] = (i * 19) & 0xfff;
        }

        JpegFrame decoded = ExtendedJpegCodec.decode(ExtendedJpegCodec.encode(
                JpegFrame.of(8, 8, 3, samples, 12)));

        assertEquals(12, decoded.precision());
        for (int i = 0; i < samples.length; i++) {
            assertTrue(Math.abs(samples[i] - decoded.samples()[i]) <= 100,
                    "sample " + i + " differs too much");
        }
    }

    @Test
    void roundTripsExtendedFrameWithRestartMarkers() throws Exception {
        int[] samples = new int[17 * 11];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = (i * 257 + 11) & 0xfff;
        }

        JpegFrame decoded = ExtendedJpegCodec.decode(ExtendedJpegCodec.encode(
                JpegFrame.of(17, 11, 1, samples, 12), 2));

        assertEquals(12, decoded.precision());
        assertTrue(maxDifference(samples, decoded.samples()) <= 100);
    }

    @Test
    void roundTripsTwelveBitFourTwoTwoSampling() throws Exception {
        int width = 13;
        int height = 9;
        int[] samples = new int[width * height * 3];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int offset = (y * width + x) * 3;
                samples[offset] = x * 37 + y * 29 + 400;
                samples[offset + 1] = 1200;
                samples[offset + 2] = 2400;
            }
        }

        byte[] encoded = ExtendedJpegCodec.encode(JpegFrame.of(width, height, 3, samples, 12),
                JpegSampling.SF422);
        JpegFrame decoded = ExtendedJpegCodec.decode(encoded);

        assertTrue(hasMarker(encoded, 0xc1));
        assertFrameSampling(encoded, 0x21, 0x11, 0x11);
        assertTrue(maxDifference(samples, decoded.samples()) <= 90,
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

    private static void assertFrameSampling(byte[] data, int... expected) {
        for (int i = 0; i + 1 < data.length; i++) {
            if ((data[i] & 0xff) == 0xff && (data[i + 1] & 0xff) == 0xc1) {
                for (int component = 0; component < expected.length; component++) {
                    assertEquals(expected[component], data[i + 11 + component * 3] & 0xff);
                }
                return;
            }
        }
        throw new AssertionError("SOF1 marker not found");
    }
}
