package io.github.cocosip.dcm4che.imageio.codecs.jpeg.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class JpegDctTest {
    @Test
    void forwardAndInversePreserveAnUnquantizedBlock() {
        double[] block = new double[64];
        for (int i = 0; i < block.length; i++) {
            block[i] = (i * 13) % 256 - 128;
        }

        double[] restored = JpegDct.inverse(JpegDct.forward(block));

        for (int i = 0; i < block.length; i++) {
            assertEquals(block[i], restored[i], 1e-8, "sample " + i);
        }
    }

    @Test
    void exposesTheStandardZigZagOrder() {
        assertEquals(0, JpegZigZag.ORDER[0]);
        assertEquals(1, JpegZigZag.ORDER[1]);
        assertEquals(8, JpegZigZag.ORDER[2]);
        assertEquals(63, JpegZigZag.ORDER[63]);
    }
}
