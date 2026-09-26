package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.htj2k;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Raster;

class Htj2kFoDicomFixtureTest {
    @Test
    void decodesFoDicomTwelveBitGrayscaleLosslessly() throws Exception {
        assertFixture("htj2k_fodicom_gray12_201", Htj2kFrameCodec.LOSSLESS_UID,
                129, 131, 12, 16, 1, false, 0);
    }

    @Test
    void decodesFoDicomSignedTwelveBitGrayscaleLosslessly() throws Exception {
        assertFixture("htj2k_fodicom_signed_gray12_201", Htj2kFrameCodec.LOSSLESS_UID,
                129, 131, 12, 16, 1, true, 0);
    }

    @Test
    void decodesFoDicomSignedSixteenBitGrayscaleLosslessly() throws Exception {
        assertFixture("htj2k_fodicom_signed_gray16_201", Htj2kFrameCodec.LOSSLESS_UID,
                129, 131, 16, 16, 1, true, 0);
    }

    @Test
    void decodesFoDicomRgbLosslessly() throws Exception {
        assertFixture("htj2k_fodicom_rgb8_202", Htj2kFrameCodec.LOSSLESS_RPCL_UID,
                64, 64, 8, 8, 3, false, 0);
    }

    @Test
    void decodesFoDicomRgbWithLosslessSyntax() throws Exception {
        assertFixture("htj2k_fodicom_rgb8_202", Htj2kFrameCodec.LOSSLESS_UID,
                64, 64, 8, 8, 3, false, 0);
    }

    @Test
    void decodesFoDicomLossyRgbWithinTolerance() throws Exception {
        assertFixture("htj2k_fodicom_rgb8_203", Htj2kFrameCodec.LOSSY_UID,
                64, 64, 8, 8, 3, false, 4);
    }

    @Test
    void decodesFoDicomTwelveBitLossyGrayscaleWithinTolerance() throws Exception {
        assertFixture("htj2k_fodicom_gray12_203", Htj2kFrameCodec.LOSSY_UID,
                129, 131, 12, 16, 1, false, 3);
    }

    private static void assertFixture(String name, String uid, int width, int height,
            int storedPrecision, int codestreamPrecision, int components, boolean signed,
            int tolerance) throws Exception {
        byte[] codestream = resource(name + ".j2c");
        byte[] expected = resource(name + ".raw");
        Jpeg2000Raster decoded = Htj2kFrameCodec.forTransferSyntax(uid).decode(codestream);
        assertEquals(width, decoded.width());
        assertEquals(height, decoded.height());
        assertEquals(codestreamPrecision, decoded.precision());
        assertEquals(signed, decoded.signed());
        assertEquals(components, decoded.componentCount());
        int bytesPerSample = storedPrecision <= 8 ? 1 : 2;
        assertEquals(width * height * components * bytesPerSample, expected.length);
        int mask = (1 << storedPrecision) - 1;
        int[][] decodedSamples = new int[components][];
        for (int component = 0; component < components; component++) {
            decodedSamples[component] = decoded.component(component);
        }
        for (int pixel = 0; pixel < width * height; pixel++) {
            for (int component = 0; component < components; component++) {
                int offset = (pixel * components + component) * bytesPerSample;
                int sample = expected[offset] & 0xff;
                if (bytesPerSample == 2) {
                    sample |= (expected[offset + 1] & 0xff) << 8;
                }
                sample &= mask;
                if (signed && codestreamPrecision == storedPrecision
                        && (sample & (1 << (storedPrecision - 1))) != 0) {
                    sample -= 1 << storedPrecision;
                }
                int actual = decodedSamples[component][pixel];
                if (signed && codestreamPrecision > storedPrecision) {
                    actual &= mask;
                }
                assertTrue(Math.abs(sample - actual) <= tolerance,
                        name + " pixel " + pixel + " component " + component);
            }
        }
    }

    private static byte[] resource(String name) throws Exception {
        return Files.readAllBytes(Paths.get(Htj2kFoDicomFixtureTest.class.getResource(
                "/jpeg2000/" + name).toURI()));
    }
}
