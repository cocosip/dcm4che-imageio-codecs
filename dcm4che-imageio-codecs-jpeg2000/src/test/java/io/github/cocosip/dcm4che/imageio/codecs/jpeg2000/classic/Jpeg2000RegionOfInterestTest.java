package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.classic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;

import org.junit.jupiter.api.Test;

import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Exception;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000ProgressionOrder;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Raster;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Limits;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000CodestreamReader;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000CodestreamWriter;

class Jpeg2000RegionOfInterestTest {
    @Test
    void decodesMaxshiftCoefficients() throws Exception {
        int[] samples = new int[37 * 35];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = (i * 37 + (i >>> 3)) & 255;
        }
        Jpeg2000Raster raster = Jpeg2000Raster.of(37, 35, 8, 8, false,
                "MONOCHROME2", new int[][] {samples}, Jpeg2000Limits.defaults());
        byte[] encoded = Jpeg2000LosslessCodec.encode(raster, false, new double[] {0},
                Jpeg2000ProgressionOrder.LRCP, false, false, 0, 2);
        assertArrayEquals(samples, Jpeg2000LosslessCodec.decode(encoded).component(0));
        Jpeg2000ClassicCodestream parsed = new Jpeg2000ClassicCodestreamParser(
                new Jpeg2000CodestreamReader(new MemoryCacheImageInputStream(
                        new ByteArrayInputStream(encoded)), encoded.length, Jpeg2000Limits.defaults()),
                Jpeg2000Limits.defaults()).parse();
        ByteArrayOutputStream rewritten = new ByteArrayOutputStream();
        MemoryCacheImageOutputStream output = new MemoryCacheImageOutputStream(rewritten);
        new Jpeg2000ClassicCodestreamWriter(
                new Jpeg2000CodestreamWriter(output, Jpeg2000Limits.defaults())).write(parsed);
        output.flush();
        assertArrayEquals(samples,
                Jpeg2000LosslessCodec.decode(rewritten.toByteArray()).component(0));
    }

    @Test
    void rejectsUnsupportedRegionStyleAndTruncation() throws Exception {
        int[] samples = new int[35 * 35];
        Jpeg2000Raster raster = Jpeg2000Raster.of(35, 35, 8, 8, false,
                "MONOCHROME2", new int[][] {samples}, Jpeg2000Limits.defaults());
        byte[] encoded = Jpeg2000LosslessCodec.encode(raster, false, new double[] {0},
                Jpeg2000ProgressionOrder.LRCP, false, false, 0, 2);
        int rgn = markerOffset(encoded, 0x5e);
        byte[] invalidStyle = encoded.clone();
        invalidStyle[rgn + 5] = 1;
        assertThrows(Jpeg2000Exception.class, () -> Jpeg2000LosslessCodec.decode(invalidStyle));
        byte[] truncated = Arrays.copyOf(encoded, encoded.length - 2);
        assertThrows(Jpeg2000Exception.class, () -> Jpeg2000LosslessCodec.decode(truncated));
    }

    private static int markerOffset(byte[] bytes, int marker) {
        for (int i = 0; i < bytes.length - 1; i++) {
            if ((bytes[i] & 255) == 255 && (bytes[i + 1] & 255) == marker) return i;
        }
        throw new AssertionError("marker missing");
    }
}
