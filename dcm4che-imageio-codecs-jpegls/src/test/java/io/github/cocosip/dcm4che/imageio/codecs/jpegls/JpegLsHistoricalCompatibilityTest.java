package io.github.cocosip.dcm4che.imageio.codecs.jpegls;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import io.github.cocosip.dcm4che.imageio.codecs.jpegls.internal.JpegLsFrameCodec;

class JpegLsHistoricalCompatibilityTest {
    @Test
    void expandsEvenWidthYbrFull422AndReturnsRgbMetadata() {
        byte[] ybr = {10, 20, (byte) 128, (byte) 128};

        JpegLsHistoricalCompatibility.NormalizedFrame normalized =
                JpegLsHistoricalCompatibility.normalizeYbrFull422ForEncode(
                        ybr, 2, 1, false);

        assertArrayEquals(new byte[] {10, 10, 10, 20, 20, 20}, normalized.pixelData());
        assertEquals("RGB", normalized.photometricInterpretation());
        assertEquals(0, normalized.planarConfiguration());
    }

    @Test
    void expandsOddWidthAndDropsPaddedSecondLuma() {
        byte[] ybr = {10, 20, (byte) 128, (byte) 128, 30, 0, (byte) 128, (byte) 128};

        JpegLsHistoricalCompatibility.NormalizedFrame normalized =
                JpegLsHistoricalCompatibility.normalizeYbrFull422ForEncode(
                        ybr, 3, 1, false);

        assertArrayEquals(new byte[] {10, 10, 10, 20, 20, 20, 30, 30, 30},
                normalized.pixelData());
    }

    @Test
    void rejectsPlanarYbrFull422AndWrongFrameLength() {
        assertThrows(IllegalArgumentException.class, () ->
                JpegLsHistoricalCompatibility.normalizeYbrFull422ForEncode(
                        new byte[4], 2, 1, true));
        assertThrows(IllegalArgumentException.class, () ->
                JpegLsHistoricalCompatibility.normalizeYbrFull422ForEncode(
                        new byte[3], 2, 1, false));
    }

    @Test
    void decodesNormalizedJpegLsFrameWithUpdatedMetadata() throws IOException {
        byte[] encoded = JpegLsFrameCodec.encode(3, 1, 8, 3, 0,
                new int[] {10, 10, 10, 20, 20, 20, 30, 30, 30});

        JpegLsHistoricalCompatibility.NormalizedFrame normalized =
                JpegLsHistoricalCompatibility.decodeYbrFull422(encoded, 3, 1);

        assertArrayEquals(new byte[] {10, 10, 10, 20, 20, 20, 30, 30, 30},
                normalized.pixelData());
        assertEquals("RGB", normalized.photometricInterpretation());
        assertEquals(0, normalized.planarConfiguration());
    }
}
