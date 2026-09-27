package io.github.cocosip.dcm4che.imageio.codecs.jpeg.internal;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class JpegFrameTest {
    @Test
    void rejectsDimensionsOutsideJpegSofRange() {
        assertThrows(IllegalArgumentException.class,
                () -> JpegFrame.of(0x10000, 1, 1, new int[0x10000]));
        assertThrows(IllegalArgumentException.class,
                () -> JpegFrame.of(1, 0x10000, 1, new int[0x10000]));
    }

    @Test
    void rejectsUnsupportedFourComponentFrames() {
        assertThrows(IllegalArgumentException.class,
                () -> JpegFrame.of(1, 1, 4, new int[4]));
    }
}
