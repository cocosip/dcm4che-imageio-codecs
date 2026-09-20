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

    private static boolean hasMarker(byte[] data, int marker) {
        for (int i = 0; i + 1 < data.length; i++) {
            if ((data[i] & 0xff) == 0xff && (data[i + 1] & 0xff) == marker) {
                return true;
            }
        }
        return false;
    }
}
