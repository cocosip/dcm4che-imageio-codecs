package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.htj2k;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;

import javax.imageio.IIOException;
import javax.imageio.stream.MemoryCacheImageInputStream;

import org.junit.jupiter.api.Test;

import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Limits;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Raster;

class Htj2kLosslessFrameTest {
    @Test
    void roundTripsGrayscaleAcrossCodeBlockAndPrecisionBoundaries() throws Exception {
        roundTrip(1, 1, 8, false);
        roundTrip(17, 13, 8, false);
        roundTrip(65, 67, 8, false);
        roundTrip(35, 21, 12, false);
        roundTrip(19, 11, 16, true);
    }

    @Test
    void rejectsLossyCallUntilItsFramePathExists() throws Exception {
        Htj2kFrameCodec lossy = Htj2kFrameCodec.forTransferSyntax(
                Htj2kFrameCodec.LOSSY_UID);
        assertThrows(IIOException.class, () -> lossy.encode(raster(1, 1, 8, false)));
        assertThrows(IIOException.class, () -> lossy.decode(new byte[0]));
    }

    private static void roundTrip(int width, int height, int precision,
            boolean signed) throws Exception {
        Jpeg2000Raster raster = raster(width, height, precision, signed);
        Htj2kFrameCodec codec = Htj2kFrameCodec.forTransferSyntax(
                Htj2kFrameCodec.LOSSLESS_UID);
        byte[] bytes = codec.encode(raster);
        Htj2kCodestream stream = codec.inspect(new MemoryCacheImageInputStream(
                new ByteArrayInputStream(bytes)), bytes.length);
        assertEquals(6, stream.tilePartCount());
        assertEquals(bytes.length, stream.logicalLength());
        assertEquals(width, stream.size().referenceGridWidth());
        assertEquals(height, stream.size().referenceGridHeight());
        assertArrayEquals(raster.component(0), codec.decode(bytes).component(0));
    }

    private static Jpeg2000Raster raster(int width, int height, int precision,
            boolean signed) throws Exception {
        int[] samples = new int[width * height];
        int mask = (1 << precision) - 1;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int value = (x * 17 + y * 31 + x * y * 3) & mask;
                samples[y * width + x] = signed && (value & (1 << (precision - 1))) != 0
                        ? value - (1 << precision) : value;
            }
        }
        return Jpeg2000Raster.of(width, height, precision <= 8 ? 8 : 16,
                precision, signed, "MONOCHROME2", new int[][] {samples},
                Jpeg2000Limits.defaults());
    }
}
