package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.classic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Limits;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000ProgressionOrder;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Raster;

class Jpeg2000InteropGenerationTest {
    @Test
    void emitsSyntheticCodestreamsForSeparateForeignDecoder() throws Exception {
        int width = 67;
        int height = 65;
        int[] samples = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                samples[y * width + x] = (x * 31 + y * 73 + (x ^ y) * 7) & 255;
            }
        }
        Jpeg2000Raster source = Jpeg2000Raster.of(width, height, 8, 8, false,
                "MONOCHROME2", new int[][] {samples}, Jpeg2000Limits.defaults());
        byte[] lossless = Jpeg2000LosslessCodec.encode(source, false,
                new double[] {128, 16, 0}, Jpeg2000ProgressionOrder.LRCP);
        byte[] lossy = Jpeg2000LosslessCodec.encode(source, true,
                new double[] {0}, Jpeg2000ProgressionOrder.LRCP);
        assertArrayEquals(samples, Jpeg2000LosslessCodec.decode(lossless).component(0));
        Files.write(Paths.get("target/java-synthetic-lossless.j2k"), lossless);
        Files.write(Paths.get("target/java-synthetic-lossy.j2k"), lossy);
        Files.write(Paths.get("target/java-synthetic-sop-eph.j2k"),
                Jpeg2000LosslessCodec.encode(source, false, new double[] {0},
                        Jpeg2000ProgressionOrder.LRCP, true, true));
        Files.write(Paths.get("target/java-synthetic-ppt.j2k"),
                Jpeg2000LosslessCodec.encode(source, false, new double[] {0},
                        Jpeg2000ProgressionOrder.LRCP, false, false, 1));
        Files.write(Paths.get("target/java-synthetic-ppm.j2k"),
                Jpeg2000LosslessCodec.encode(source, false, new double[] {0},
                        Jpeg2000ProgressionOrder.LRCP, false, false, 2));
        Files.write(Paths.get("target/java-synthetic-rgn.j2k"),
                Jpeg2000LosslessCodec.encode(source, false, new double[] {0},
                        Jpeg2000ProgressionOrder.LRCP, false, false, 0, 2));
        byte[] poc = Jpeg2000LosslessCodec.encode(source, false,
                new double[] {128, 16, 0}, Jpeg2000ProgressionOrder.RLCP);
        poc[marker(poc, 0x52) + 5] = 0;
        int sot = marker(poc, 0x90);
        byte[] pocSegment = {(byte) 0xff, 0x5f, 0, 9, 0, 0, 0, 3, 6, 1, 1};
        byte[] withPoc = new byte[poc.length + pocSegment.length];
        System.arraycopy(poc, 0, withPoc, 0, sot);
        System.arraycopy(pocSegment, 0, withPoc, sot, pocSegment.length);
        System.arraycopy(poc, sot, withPoc, sot + pocSegment.length, poc.length - sot);
        Files.write(Paths.get("target/java-synthetic-poc.j2k"), withPoc);
    }

    private static int marker(byte[] bytes, int code) {
        for (int i = 0; i < bytes.length - 1; i++) {
            if ((bytes[i] & 255) == 255 && (bytes[i + 1] & 255) == code) return i;
        }
        throw new AssertionError("marker missing");
    }
}
