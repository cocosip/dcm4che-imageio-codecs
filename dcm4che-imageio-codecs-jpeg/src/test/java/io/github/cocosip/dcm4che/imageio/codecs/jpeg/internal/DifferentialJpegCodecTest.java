package io.github.cocosip.dcm4che.imageio.codecs.jpeg.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DifferentialJpegCodecTest {
    @Test
    void differentialLosslessUsesExplicitReferenceFrame() throws Exception {
        int[] referenceSamples = new int[7 * 5];
        int[] currentSamples = new int[referenceSamples.length];
        for (int i = 0; i < referenceSamples.length; i++) {
            referenceSamples[i] = (i * 257 + 100) & 0xfff;
            currentSamples[i] = (referenceSamples[i] + (i % 9) - 4) & 0xfff;
        }
        JpegFrame reference = JpegFrame.of(7, 5, 1, referenceSamples, 12);
        JpegFrame current = JpegFrame.of(7, 5, 1, currentSamples, 12);

        byte[] encoded = DifferentialJpegCodec.encode(current, reference,
                DifferentialProcess.LOSSLESS);
        JpegFrame decoded = DifferentialJpegCodec.decode(encoded, reference);

        assertEquals(0, maxDifference(current.samples(), decoded.samples()));
    }

    @Test
    void differentialRejectsMissingOrIncompatibleReference() {
        JpegFrame current = JpegFrame.of(4, 4, 1, new int[16]);
        assertThrows(IllegalArgumentException.class, () -> DifferentialJpegCodec.encode(current,
                null, DifferentialProcess.LOSSLESS));
        assertThrows(IllegalArgumentException.class, () -> DifferentialJpegCodec.encode(current,
                JpegFrame.of(3, 4, 1, new int[12]), DifferentialProcess.LOSSLESS));
    }

    @Test
    void roundTripsDifferentialSequentialDct() throws Exception {
        int[] samples = new int[13 * 9];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = (i * 7 + 21) & 0xff;
        }

        byte[] encoded = DifferentialJpegCodec.encodeSequential(JpegFrame.of(13, 9, 1, samples));
        JpegFrame decoded = DifferentialJpegCodec.decodeSequential(encoded);

        assertTrue(hasMarker(encoded, 0xc5));
        assertEquals(8, decoded.precision());
        assertTrue(maxDifference(samples, decoded.samples()) <= 80);
    }

    @Test
    void roundTripsDifferentialProgressiveDct() throws Exception {
        int[] samples = new int[11 * 10];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = (i * 13 + 3) & 0xff;
        }

        byte[] encoded = DifferentialJpegCodec.encodeProgressive(JpegFrame.of(11, 10, 1, samples));
        JpegFrame decoded = DifferentialJpegCodec.decodeProgressive(encoded);

        assertTrue(hasMarker(encoded, 0xc6));
        assertTrue(maxDifference(samples, decoded.samples()) <= 75);
    }

    @Test
    void roundTripsDifferentialLossless() throws Exception {
        int[] samples = new int[7 * 5];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = (i * 257 + 13) & 0xfff;
        }

        byte[] encoded = DifferentialJpegCodec.encodeLossless(JpegFrame.of(7, 5, 1, samples, 12));
        JpegFrame decoded = DifferentialJpegCodec.decodeLossless(encoded);

        assertTrue(hasMarker(encoded, 0xc7));
        assertEquals(0, maxDifference(samples, decoded.samples()));
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
