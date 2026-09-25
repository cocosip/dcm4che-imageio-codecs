package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.classic;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Exception;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Limits;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Raster;

class Jpeg2000MalformedFrameTest {
    @Test
    void rejectsTruncationThroughPacketBodyAndEoc() throws Exception {
        byte[] valid = Jpeg2000LosslessCodec.encode(source());
        for (int cut = 1; cut <= Math.min(128, valid.length - 2); cut++) {
            byte[] truncated = Arrays.copyOf(valid, valid.length - cut);
            assertThrows(Jpeg2000Exception.class, () -> Jpeg2000LosslessCodec.decode(truncated));
        }
    }

    @Test
    void rejectsOversizedSizBeforeRasterAllocation() throws Exception {
        byte[] valid = Jpeg2000LosslessCodec.encode(source());
        byte[] oversized = valid.clone();
        int siz = marker(oversized, 0x51);
        oversized[siz + 6] = 0x7f;
        oversized[siz + 7] = (byte) 0xff;
        oversized[siz + 8] = (byte) 0xff;
        oversized[siz + 9] = (byte) 0xff;
        assertThrows(Jpeg2000Exception.class, () -> Jpeg2000LosslessCodec.decode(oversized));
    }

    private static Jpeg2000Raster source() throws Exception {
        int[] samples = new int[35 * 37];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = i * 17 & 255;
        }
        return Jpeg2000Raster.of(37, 35, 8, 8, false, "MONOCHROME2",
                new int[][] {samples}, Jpeg2000Limits.defaults());
    }

    private static int marker(byte[] bytes, int code) {
        for (int i = 0; i < bytes.length - 1; i++) {
            if ((bytes[i] & 255) == 255 && (bytes[i + 1] & 255) == code) return i;
        }
        throw new AssertionError("marker missing");
    }
}
