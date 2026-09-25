package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.classic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Exception;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Limits;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000ProgressionOrder;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Raster;

class Jpeg2000PacketMarkerTest {
    @Test
    void decodesSopAndEphAndRejectsWrongSequenceOrMissingEph() throws Exception {
        int[] samples = new int[39 * 37];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = i * 17 & 255;
        }
        Jpeg2000Raster source = Jpeg2000Raster.of(39, 37, 8, 8, false,
                "MONOCHROME2", new int[][] {samples}, Jpeg2000Limits.defaults());
        byte[] encoded = Jpeg2000LosslessCodec.encode(source, false,
                new double[] {0}, Jpeg2000ProgressionOrder.LRCP, true, true);
        assertArrayEquals(samples, Jpeg2000LosslessCodec.decode(encoded).component(0));
        int sop = marker(encoded, 0x91);
        byte[] wrongSequence = encoded.clone();
        wrongSequence[sop + 5] = 1;
        assertThrows(Jpeg2000Exception.class,
                () -> Jpeg2000LosslessCodec.decode(wrongSequence));
        int eph = marker(encoded, 0x92);
        byte[] missingEph = encoded.clone();
        missingEph[eph + 1] = 0;
        assertThrows(Jpeg2000Exception.class,
                () -> Jpeg2000LosslessCodec.decode(missingEph));
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
