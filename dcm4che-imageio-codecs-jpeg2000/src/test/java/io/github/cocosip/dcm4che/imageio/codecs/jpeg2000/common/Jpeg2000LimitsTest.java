package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class Jpeg2000LimitsTest {
    private final Jpeg2000Limits limits = new Jpeg2000Limits(
            64,
            16,
            4,
            8,
            12,
            10,
            3);

    @Test
    void computesSampleBufferSizeWithCheckedArithmetic() throws Exception {
        assertEquals(48, limits.checkedSampleBufferBytes(3, 2, 2, 4));
    }

    @Test
    void rejectsSampleCountsAndByteLengthsBeyondConfiguredLimits() {
        Jpeg2000Exception sampleError = assertThrows(
                Jpeg2000Exception.class,
                () -> limits.checkedSampleBufferBytes(3, 3, 2, 1));
        Jpeg2000Exception byteError = assertThrows(
                Jpeg2000Exception.class,
                () -> limits.checkedSampleBufferBytes(4, 4, 1, 8));

        assertTrue(sampleError.getMessage().contains("sample count"));
        assertTrue(byteError.getMessage().contains("byte length"));
    }

    @Test
    void rejectsOverflowBeforeNarrowingToJavaArrayLength() {
        Jpeg2000Exception error = assertThrows(
                Jpeg2000Exception.class,
                () -> limits.checkedSampleBufferBytes(Long.MAX_VALUE, 2, 1, 1));

        assertTrue(error.getMessage().contains("overflow"));
    }

    @Test
    void enforcesIndependentCodestreamStructureLimits() throws Exception {
        assertEquals(64, limits.requireFrameLength(64));
        assertEquals(10, limits.requireMarkerPayloadLength(10));
        assertEquals(4, limits.requireTileCount(4));
        assertEquals(8, limits.requireCodeBlockCount(8));
        assertEquals(12, limits.requirePacketCount(12));
        assertEquals(3, limits.requireQualityLayerCount(3));

        assertThrows(Jpeg2000Exception.class, () -> limits.requireFrameLength(65));
        assertThrows(Jpeg2000Exception.class, () -> limits.requireMarkerPayloadLength(11));
        assertThrows(Jpeg2000Exception.class, () -> limits.requireTileCount(5));
        assertThrows(Jpeg2000Exception.class, () -> limits.requireCodeBlockCount(9));
        assertThrows(Jpeg2000Exception.class, () -> limits.requirePacketCount(13));
        assertThrows(Jpeg2000Exception.class, () -> limits.requireQualityLayerCount(4));
    }

    @Test
    void rejectsZeroOrNegativeDeclaredCounts() {
        assertThrows(Jpeg2000Exception.class, () -> limits.requireFrameLength(0));
        assertThrows(Jpeg2000Exception.class, () -> limits.requireTileCount(0));
        assertThrows(Jpeg2000Exception.class, () -> limits.requireQualityLayerCount(0));
    }
}
