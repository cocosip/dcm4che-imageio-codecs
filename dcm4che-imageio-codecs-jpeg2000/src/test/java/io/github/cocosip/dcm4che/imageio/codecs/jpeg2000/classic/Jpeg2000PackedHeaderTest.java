package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.classic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Exception;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Limits;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000ProgressionOrder;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Raster;

class Jpeg2000PackedHeaderTest {
    @Test
    void decodesPptAndPpmPackedPacketHeaders() throws Exception {
        int[] samples = new int[67 * 65];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = i * 31 & 255;
        }
        Jpeg2000Raster source = Jpeg2000Raster.of(67, 65, 8, 8, false,
                "MONOCHROME2", new int[][] {samples}, Jpeg2000Limits.defaults());
        for (int mode : new int[] {1, 2}) {
            byte[] encoded = Jpeg2000LosslessCodec.encode(source, false,
                    new double[] {64, 0}, Jpeg2000ProgressionOrder.LRCP,
                    true, true, mode);
            assertArrayEquals(samples, Jpeg2000LosslessCodec.decode(encoded).component(0));
            byte[] invalid = encoded.clone();
            int marker = marker(invalid, mode == 1 ? 0x61 : 0x60);
            invalid[marker + 4] = 1;
            assertThrows(Jpeg2000Exception.class,
                    () -> Jpeg2000LosslessCodec.decode(invalid));
        }
    }

    private static int marker(byte[] bytes, int code) {
        for (int i = 0; i < bytes.length - 1; i++) {
            if ((bytes[i] & 0xff) == 0xff && (bytes[i + 1] & 0xff) == code) {
                return i;
            }
        }
        throw new AssertionError("marker not found");
    }
}
