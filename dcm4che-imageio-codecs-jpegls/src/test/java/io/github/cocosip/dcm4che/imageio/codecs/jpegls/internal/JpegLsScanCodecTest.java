package io.github.cocosip.dcm4che.imageio.codecs.jpegls.internal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class JpegLsScanCodecTest {
    @Test
    void roundTripsMonoLosslessSamples() throws Exception {
        int[] samples = {0, 10, 20, 30, 30, 30, 40, 50, 50, 50, 49, 48};
        JpegLsTraits traits = JpegLsTraits.create(255, 0, 64, 3, 7, 21);
        JpegLsScanCodec codec = new JpegLsScanCodec(4, 3, traits);

        byte[] encoded = codec.encode(samples);
        int[] decoded = codec.decode(encoded);

        assertArrayEquals(samples, decoded);
    }

    @Test
    void roundTripsMonoNearLosslessSamplesWithinAllowedError() throws Exception {
        int[] samples = {0, 10, 20, 30, 30, 30, 40, 50, 50, 50, 49, 48};
        JpegLsTraits traits = JpegLsTraits.create(255, 2, 64, 9, 17, 37);
        JpegLsScanCodec codec = new JpegLsScanCodec(4, 3, traits);

        int[] decoded = codec.decode(codec.encode(samples));

        for (int i = 0; i < samples.length; i++) {
            if (Math.abs(samples[i] - decoded[i]) > 2) {
                throw new AssertionError("sample " + i + " exceeds NEAR tolerance");
            }
        }
    }

    @Test
    void rejectsMismatchedSampleCountAndTruncatedScan() throws Exception {
        JpegLsTraits traits = JpegLsTraits.create(255, 0, 64, 3, 7, 21);
        JpegLsScanCodec codec = new JpegLsScanCodec(2, 2, traits);

        assertThrows(JpegLsException.class, () -> codec.encode(new int[] {1, 2, 3}));
        assertThrows(JpegLsException.class, () -> codec.decode(new byte[] {0}));
    }

    @Test
    void roundTripsRgbSampleAndLineInterleave() throws Exception {
        int[] samples = {
            1, 10, 100, 2, 20, 110,
            3, 30, 120, 4, 40, 130
        };
        JpegLsTraits traits = JpegLsTraits.create(255, 0, 64, 3, 7, 21);
        for (JpegLsInterleaveMode mode : new JpegLsInterleaveMode[] {
                JpegLsInterleaveMode.SAMPLE, JpegLsInterleaveMode.LINE}) {
            JpegLsScanCodec codec = new JpegLsScanCodec(2, 2, 3, traits, mode);
            assertArrayEquals(samples, codec.decode(codec.encode(samples)));
        }
    }
}
