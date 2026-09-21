package io.github.cocosip.dcm4che.imageio.codecs.jpeg.internal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

class JpegArithmeticDctTest {
    @Test
    void roundTripsSequentialDctCoefficients() throws Exception {
        int[][][] expected = new int[1][1][64];
        expected[0][0][0] = 12;
        expected[0][0][JpegZigZag.ORDER[1]] = -3;
        expected[0][0][JpegZigZag.ORDER[7]] = 2;

        byte[] entropy = JpegArithmeticDct.encode(expected, 1, 1, 1);
        int[][][] actual = JpegArithmeticDct.decode(entropy, 1, 1, 1);
        assertArrayEquals(expected[0][0], actual[0][0], Arrays.toString(actual[0][0]));
    }

    @Test
    void roundTripsMagnitudeCategoriesAndRuns() throws Exception {
        int[][][] expected = new int[1][1][64];
        expected[0][0][0] = -31;
        int[] values = {1, -2, 3, -4, 7, -8, 11, -12, 15, -16, 31, -32,
                63, -64, 127, -128, 255, -255, 1023, -2047, 4095};
        for (int i = 0; i < values.length; i++) {
            expected[0][0][JpegZigZag.ORDER[i + 1]] = values[i];
        }
        int[][][] actual = JpegArithmeticDct.decode(
                JpegArithmeticDct.encode(expected, 1, 1, 1), 1, 1, 1);
        assertArrayEquals(expected[0][0], actual[0][0], Arrays.toString(actual[0][0]));
    }

    @Test
    void roundTripsMultipleBlocksWithDcPredictors() throws Exception {
        int[][][] expected = new int[1][4][64];
        for (int block = 0; block < expected[0].length; block++) {
            expected[0][block][0] = (block - 1) * 37;
            expected[0][block][JpegZigZag.ORDER[1]] = block * 9 - 12;
            expected[0][block][JpegZigZag.ORDER[5]] = 63 - block * 17;
            expected[0][block][JpegZigZag.ORDER[12]] = block % 2 == 0 ? -31 : 22;
        }
        int[][][] actual = JpegArithmeticDct.decode(
                JpegArithmeticDct.encode(expected, 2, 2, 1), 2, 2, 1);
        for (int block = 0; block < expected[0].length; block++) {
            assertArrayEquals(expected[0][block], actual[0][block]);
        }
    }

    @Test
    void preservesDcConditioningBoundaryForMagnitudeTwo() throws Exception {
        int[][][] expected = new int[1][3][64];
        expected[0][1][0] = 2;
        expected[0][2][0] = 4;

        int[][][] actual = JpegArithmeticDct.decode(
                JpegArithmeticDct.encode(expected, 3, 1, 1), 3, 1, 1);

        for (int block = 0; block < expected[0].length; block++) {
            assertArrayEquals(expected[0][block], actual[0][block]);
        }
    }

    @Test
    void usesActualAcCoefficientIndexAfterZeroRun() throws Exception {
        int[][][] expected = new int[1][1][64];
        expected[0][0][JpegZigZag.ORDER[1]] = -426;
        expected[0][0][JpegZigZag.ORDER[6]] = -31;

        int[][][] actual = JpegArithmeticDct.decode(
                JpegArithmeticDct.encode(expected, 1, 1, 1), 1, 1, 1);

        assertArrayEquals(expected[0][0], actual[0][0]);
    }

    @Test
    void roundTripsAcConditioningBoundary() throws Exception {
        int[][][] expected = new int[1][1][64];
        expected[0][0][JpegZigZag.ORDER[6]] = -426;
        expected[0][0][JpegZigZag.ORDER[8]] = 31;

        int[][][] actual = JpegArithmeticDct.decode(
                JpegArithmeticDct.encode(expected, 1, 1, 1, 0, 1, 7), 1, 1, 1,
                0, 1, 7);
        assertArrayEquals(expected[0][0], actual[0][0]);
    }
}
