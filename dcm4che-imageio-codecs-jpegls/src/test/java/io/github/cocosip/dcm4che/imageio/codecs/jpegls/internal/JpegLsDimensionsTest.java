package io.github.cocosip.dcm4che.imageio.codecs.jpegls.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class JpegLsDimensionsTest {
    @Test
    void parsesOversizeDimensions() throws Exception {
        JpegLsDimensions dimensions = JpegLsDimensions.fromFrame(0, 0);

        dimensions.applyOversize(new byte[] {
                4, 3,
                0x01, 0x11, 0x70,
                0x01, 0x38, (byte) 0x80
        });

        assertEquals(70000, dimensions.height());
        assertEquals(80000, dimensions.width());
    }

    @Test
    void parsesTwoThreeAndFourByteDnlValues() throws Exception {
        assertEquals(300, JpegLsDimensions.parseDnl(new byte[] {0x01, 0x2c}));
        assertEquals(70000, JpegLsDimensions.parseDnl(new byte[] {0x01, 0x11, 0x70}));
        assertEquals(100000, JpegLsDimensions.parseDnl(new byte[] {0, 0x01, (byte) 0x86, (byte) 0xa0}));
    }

    @Test
    void rejectsOversizeReplacementOfNonZeroFrameDimensions() throws Exception {
        JpegLsDimensions dimensions = JpegLsDimensions.fromFrame(10, 20);

        assertThrows(JpegLsException.class, () -> dimensions.applyOversize(new byte[] {
                4, 2, 0, 10, 0, 20
        }));
    }

    @Test
    void rejectsInvalidOversizeAndDnlPayloads() throws Exception {
        JpegLsDimensions dimensions = JpegLsDimensions.fromFrame(0, 0);

        assertThrows(JpegLsException.class,
                () -> dimensions.applyOversize(new byte[] {4, 1, 1, 1}));
        assertThrows(JpegLsException.class,
                () -> dimensions.applyOversize(new byte[] {4, 2, 0, 1, 0, 0}));
        assertThrows(JpegLsException.class,
                () -> JpegLsDimensions.parseDnl(new byte[] {0}));
        assertThrows(JpegLsException.class,
                () -> JpegLsDimensions.parseDnl(new byte[] {0, 0}));
    }
}
