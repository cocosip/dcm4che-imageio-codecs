package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.classic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Exception;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Limits;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000ProgressionOrder;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Raster;

class Jpeg2000ProgressionChangeTest {
    @Test
    void pocOverridesCodPacketOrderAndRejectsIncompleteCoverage() throws Exception {
        int[] samples = new int[67 * 65];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = i * 29 & 255;
        }
        Jpeg2000Raster source = Jpeg2000Raster.of(67, 65, 8, 8, false,
                "MONOCHROME2", new int[][] {samples}, Jpeg2000Limits.defaults());
        byte[] encoded = Jpeg2000LosslessCodec.encode(source, false,
                new double[] {128, 16, 0}, Jpeg2000ProgressionOrder.RLCP);
        encoded[marker(encoded, 0x52) + 5] = 0;
        int sot = marker(encoded, 0x90);
        byte[] withPoc = new byte[encoded.length + 11];
        System.arraycopy(encoded, 0, withPoc, 0, sot);
        byte[] poc = {(byte) 0xff, 0x5f, 0, 9, 0, 0, 0, 3, 6, 1, 1};
        System.arraycopy(poc, 0, withPoc, sot, poc.length);
        System.arraycopy(encoded, sot, withPoc, sot + poc.length, encoded.length - sot);
        assertArrayEquals(samples, Jpeg2000LosslessCodec.decode(withPoc).component(0));
        byte[] incomplete = withPoc.clone();
        incomplete[sot + 7] = 2;
        assertThrows(Jpeg2000Exception.class,
                () -> Jpeg2000LosslessCodec.decode(incomplete));
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
