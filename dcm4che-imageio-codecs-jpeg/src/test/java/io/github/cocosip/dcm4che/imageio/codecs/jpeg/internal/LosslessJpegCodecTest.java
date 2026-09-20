package io.github.cocosip.dcm4che.imageio.codecs.jpeg.internal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LosslessJpegCodecTest {
    @Test
    void roundTripsEightBitMonochromeExactly() throws Exception {
        int[] samples = new int[8 * 5];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = (i * 37 + 11) & 0xff;
        }

        JpegFrame source = JpegFrame.of(8, 5, 1, samples, 8);
        byte[] encoded = LosslessJpegCodec.encode(source, 1);
        JpegFrame decoded = LosslessJpegCodec.decode(encoded);

        assertTrue(hasMarker(encoded, 0xc3));
        assertEquals(8, decoded.precision());
        assertArrayEquals(samples, decoded.samples());
    }

    @Test
    void roundTripsTwelveBitRgbWithAHighOrderPredictor() throws Exception {
        int[] samples = new int[7 * 4 * 3];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = (i * 149 + (i / 3) * 17) & 0xfff;
        }

        JpegFrame source = JpegFrame.of(7, 4, 3, samples, 12);
        JpegFrame decoded = LosslessJpegCodec.decode(LosslessJpegCodec.encode(source, 7));

        assertEquals(12, decoded.precision());
        assertArrayEquals(samples, decoded.samples());
    }

    @Test
    void roundTripsSixteenBitMonochromeExactly() throws Exception {
        int[] samples = new int[] {0, 1, 32768, 65535, 12345, 54321, 65534, 256};

        JpegFrame source = JpegFrame.of(4, 2, 1, samples, 16);
        JpegFrame decoded = LosslessJpegCodec.decode(LosslessJpegCodec.encode(source, 1));

        assertEquals(16, decoded.precision());
        assertArrayEquals(samples, decoded.samples());
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
