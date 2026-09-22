package io.github.cocosip.dcm4che.imageio.codecs.jpegls.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class JpegLsPresetCodingParametersTest {
    @Test
    void resolvesEightBitLosslessDefaults() throws Exception {
        JpegLsPresetCodingParameters parameters = JpegLsPresetCodingParameters.defaults(8, 0);

        assertEquals(255, parameters.maximumSampleValue());
        assertEquals(3, parameters.threshold1());
        assertEquals(7, parameters.threshold2());
        assertEquals(21, parameters.threshold3());
        assertEquals(64, parameters.resetValue());
    }

    @Test
    void resolvesLowMaximumAndNearLosslessDefaults() throws Exception {
        JpegLsPresetCodingParameters raw = JpegLsPresetCodingParameters.parse(payload(
                31, 0, 0, 0, 0));

        JpegLsPresetCodingParameters parameters = raw.resolve(8, 2);

        assertEquals(31, parameters.maximumSampleValue());
        assertEquals(6, parameters.threshold1());
        assertEquals(10, parameters.threshold2());
        assertEquals(16, parameters.threshold3());
        assertEquals(64, parameters.resetValue());
    }

    @Test
    void preservesExplicitValidParameters() throws Exception {
        JpegLsPresetCodingParameters raw = JpegLsPresetCodingParameters.parse(payload(
                4095, 10, 20, 30, 128));

        JpegLsPresetCodingParameters parameters = raw.resolve(12, 1);

        assertEquals(4095, parameters.maximumSampleValue());
        assertEquals(10, parameters.threshold1());
        assertEquals(20, parameters.threshold2());
        assertEquals(30, parameters.threshold3());
        assertEquals(128, parameters.resetValue());
    }

    @Test
    void rejectsInvalidThresholdOrderingAndNearLosslessRange() throws Exception {
        JpegLsPresetCodingParameters invalid = JpegLsPresetCodingParameters.parse(payload(
                255, 8, 7, 21, 64));

        assertThrows(JpegLsException.class, () -> invalid.resolve(8, 0));
        assertThrows(JpegLsException.class, () -> JpegLsPresetCodingParameters.defaults(2, 2));
    }

    @Test
    void rejectsInvalidPresetSegmentLength() {
        assertThrows(JpegLsException.class,
                () -> JpegLsPresetCodingParameters.parse(new byte[] {1, 0, 1}));
    }

    private static byte[] payload(int maxValue, int t1, int t2, int t3, int reset) {
        return new byte[] {
                1,
                (byte) (maxValue >>> 8), (byte) maxValue,
                (byte) (t1 >>> 8), (byte) t1,
                (byte) (t2 >>> 8), (byte) t2,
                (byte) (t3 >>> 8), (byte) t3,
                (byte) (reset >>> 8), (byte) reset
        };
    }
}
