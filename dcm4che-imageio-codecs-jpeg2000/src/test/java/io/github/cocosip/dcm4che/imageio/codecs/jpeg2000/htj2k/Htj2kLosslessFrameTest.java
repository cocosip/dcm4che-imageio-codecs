package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.htj2k;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;

import javax.imageio.IIOException;
import javax.imageio.stream.MemoryCacheImageInputStream;

import org.junit.jupiter.api.Test;

import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Limits;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000ProgressionOrder;
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

    @Test
    void roundTripsColorAndSelectableProgression() throws Exception {
        for (Jpeg2000ProgressionOrder order : Jpeg2000ProgressionOrder.values()) {
            roundTripColor(17, 13, 8, false, order);
            roundTripColor(35, 21, 12, false, order);
        }
        roundTripColor(65, 67, 16, true, Jpeg2000ProgressionOrder.CPRL);
    }

    @Test
    void losslessUidKeepsRpclWhenProgressionIsSpecified() throws Exception {
        Jpeg2000Raster raster = colorRaster(7, 9, 8, false);
        Htj2kFrameCodec codec = Htj2kFrameCodec.forTransferSyntax(
                Htj2kFrameCodec.LOSSLESS_UID);
        byte[] encoded = codec.encode(raster, Jpeg2000ProgressionOrder.CPRL);
        Htj2kCodestream stream = codec.inspect(new MemoryCacheImageInputStream(
                new ByteArrayInputStream(encoded)), encoded.length);
        assertEquals(Jpeg2000ProgressionOrder.RPCL, stream.progression());
    }

    private static void roundTripColor(int width, int height, int precision,
            boolean signed, Jpeg2000ProgressionOrder order) throws Exception {
        Jpeg2000Raster raster = colorRaster(width, height, precision, signed);
        Htj2kFrameCodec codec = Htj2kFrameCodec.forTransferSyntax(
                Htj2kFrameCodec.LOSSLESS_RPCL_UID);
        byte[] encoded = codec.encode(raster, order);
        Htj2kCodestream stream = codec.inspect(new MemoryCacheImageInputStream(
                new ByteArrayInputStream(encoded)), encoded.length);
        assertEquals(order, stream.progression());
        assertEquals(6, stream.tilePartCount());
        Jpeg2000Raster decoded = codec.decode(encoded);
        for (int c = 0; c < 3; c++) {
            assertArrayEquals(raster.component(c), decoded.component(c));
        }
    }

    private static Jpeg2000Raster colorRaster(int width, int height, int precision,
            boolean signed) throws Exception {
        int[][] samples = new int[3][width * height];
        int mask = (1 << precision) - 1;
        for (int c = 0; c < 3; c++) {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int value = (x * (17 + c * 13) + y * (31 + c * 19)
                            + x * y * (3 + c * 5) + c * 97) & mask;
                    samples[c][y * width + x] = signed
                            && (value & (1 << (precision - 1))) != 0
                            ? value - (1 << precision) : value;
                }
            }
        }
        return Jpeg2000Raster.of(width, height, precision <= 8 ? 8 : 16,
                precision, signed, "RGB", samples, Jpeg2000Limits.defaults());
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
