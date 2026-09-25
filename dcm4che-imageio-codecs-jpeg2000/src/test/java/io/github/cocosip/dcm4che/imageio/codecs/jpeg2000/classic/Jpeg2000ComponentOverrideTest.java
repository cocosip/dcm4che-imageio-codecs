package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.classic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Exception;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Limits;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Raster;

class Jpeg2000ComponentOverrideTest {
    @Test
    void cocOverridesDefaultCodeBlockGeometry() throws Exception {
        Jpeg2000Raster source = source();
        byte[] baseline = Jpeg2000LosslessCodec.encode(source);
        int cod = marker(baseline, 0x52);
        baseline[cod + 10] = 3;
        baseline[cod + 11] = 3;
        byte[] coc = {(byte) 0xff, 0x53, 0, 9, 0, 0, 5, 4, 4, 0, 1};
        byte[] overridden = insertBeforeSot(baseline, coc);
        assertArrayEquals(source.component(0),
                Jpeg2000LosslessCodec.decode(overridden).component(0));
        byte[] withoutCoc = baseline;
        assertThrows(Jpeg2000Exception.class,
                () -> Jpeg2000LosslessCodec.decode(withoutCoc));
    }

    @Test
    void qccOverridesDefaultScalarQuantizationSteps() throws Exception {
        Jpeg2000Raster source = source();
        byte[] baseline = Jpeg2000LosslessCodec.encode(source, true);
        int qcd = marker(baseline, 0x5c);
        int length = ((baseline[qcd + 2] & 0xff) << 8) | (baseline[qcd + 3] & 0xff);
        byte[] originalPayload = Arrays.copyOfRange(baseline, qcd + 4,
                qcd + 2 + length);
        for (int offset = qcd + 5; offset < qcd + 2 + length; offset += 2) {
            baseline[offset] -= 8;
        }
        byte[] qcc = new byte[5 + originalPayload.length];
        qcc[0] = (byte) 0xff;
        qcc[1] = 0x5d;
        int qccLength = 3 + originalPayload.length;
        qcc[2] = (byte) (qccLength >>> 8);
        qcc[3] = (byte) qccLength;
        System.arraycopy(originalPayload, 0, qcc, 5, originalPayload.length);
        byte[] overridden = insertBeforeSot(baseline, qcc);
        assertArrayEquals(Jpeg2000LosslessCodec.decode(
                Jpeg2000LosslessCodec.encode(source, true)).component(0),
                Jpeg2000LosslessCodec.decode(overridden).component(0));
    }

    @Test
    void rejectsCocLevelCountWithoutMatchingQuantizationSteps() throws Exception {
        byte[] baseline = Jpeg2000LosslessCodec.encode(source());
        byte[] coc = {(byte) 0xff, 0x53, 0, 9, 0, 0, 6, 4, 4, 0, 1};
        assertThrows(Jpeg2000Exception.class,
                () -> Jpeg2000LosslessCodec.decode(insertBeforeSot(baseline, coc)));
    }

    private static Jpeg2000Raster source() throws Exception {
        int[] samples = new int[67 * 65];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = i * 17 & 255;
        }
        return Jpeg2000Raster.of(67, 65, 8, 8, false,
                "MONOCHROME2", new int[][] {samples}, Jpeg2000Limits.defaults());
    }

    private static byte[] insertBeforeSot(byte[] encoded, byte[] segment) {
        int sot = marker(encoded, 0x90);
        byte[] result = new byte[encoded.length + segment.length];
        System.arraycopy(encoded, 0, result, 0, sot);
        System.arraycopy(segment, 0, result, sot, segment.length);
        System.arraycopy(encoded, sot, result, sot + segment.length,
                encoded.length - sot);
        return result;
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
