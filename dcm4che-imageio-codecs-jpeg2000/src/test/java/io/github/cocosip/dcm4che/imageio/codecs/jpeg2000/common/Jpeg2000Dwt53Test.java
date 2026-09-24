package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class Jpeg2000Dwt53Test {
    private final Jpeg2000Limits limits = Jpeg2000Limits.defaults();

    @Test
    void transformsEvenOriginSignalsWithSymmetricBoundaryExtension() throws Exception {
        assertArrayEquals(new int[] {5}, Jpeg2000Dwt53.forward1D(new int[] {5}, 0, limits));
        assertArrayEquals(new int[] {1, 3, 5, 0, 0},
                Jpeg2000Dwt53.forward1D(new int[] {1, 2, 3, 4, 5}, 0, limits));
        assertArrayEquals(new int[] {1, 3, 0, 1},
                Jpeg2000Dwt53.forward1D(new int[] {1, 2, 3, 4}, 0, limits));
    }

    @Test
    void transformsOddOriginSignalsWithSymmetricBoundaryExtension() throws Exception {
        assertArrayEquals(new int[] {10}, Jpeg2000Dwt53.forward1D(new int[] {5}, 1, limits));
        assertArrayEquals(new int[] {2, 4, -1, 0},
                Jpeg2000Dwt53.forward1D(new int[] {1, 2, 3, 4}, 1, limits));
        assertArrayEquals(new int[] {2, 4, -1, 0, 1},
                Jpeg2000Dwt53.forward1D(new int[] {1, 2, 3, 4, 5}, 1, limits));
    }

    @Test
    void reconstructsOneDimensionalSignalsExactly() throws Exception {
        int[][] signals = {
                {5},
                {-3},
                {1, 2},
                {-7, 4, 0},
                {1, 2, 3, 4},
                {4, -2, 9, 0, 3}
        };
        for (int origin = 0; origin <= 1; origin++) {
            for (int[] signal : signals) {
                int[] transformed = Jpeg2000Dwt53.forward1D(signal, origin, limits);
                assertArrayEquals(signal, Jpeg2000Dwt53.inverse1D(transformed, origin, limits));
            }
        }
    }

    @Test
    void reconstructsOddSizedImageAcrossMultipleLevels() throws Exception {
        int width = 5;
        int height = 3;
        int[] samples = {
                -10, 3, 8, 12, -4,
                7, 1, -9, 2, 6,
                5, -2, 11, 0, 4
        };

        int[] transformed = Jpeg2000Dwt53.forward(
                samples, width, height, 2, 1, 3, limits);

        assertArrayEquals(samples, Jpeg2000Dwt53.inverse(
                transformed, width, height, 2, 1, 3, limits));
    }

    @Test
    void transformsOddSingletonAxesInTwoDimensions() throws Exception {
        int[] vertical = Jpeg2000Dwt53.forward(
                new int[] {5, 7}, 1, 2, 1, 1, 0, limits);
        int[] singleton = Jpeg2000Dwt53.forward(
                new int[] {5}, 1, 1, 1, 1, 1, limits);

        assertArrayEquals(new int[] {12, 4}, vertical);
        assertArrayEquals(new int[] {5, 7}, Jpeg2000Dwt53.inverse(
                vertical, 1, 2, 1, 1, 0, limits));
        assertArrayEquals(new int[] {20}, singleton);
        assertArrayEquals(new int[] {5}, Jpeg2000Dwt53.inverse(
                singleton, 1, 1, 1, 1, 1, limits));
    }

    @Test
    void advancesSingletonOriginParityAcrossDeclaredLevels() throws Exception {
        int[] transformed = Jpeg2000Dwt53.forward(
                new int[] {5}, 1, 1, 2, 2, 2, limits);

        assertArrayEquals(new int[] {20}, transformed);
        assertArrayEquals(new int[] {5}, Jpeg2000Dwt53.inverse(
                transformed, 1, 1, 2, 2, 2, limits));
    }

    @Test
    void preservesUnsignedSizOriginParityWithoutNarrowing() throws Exception {
        long origin = 0xffffffffL;

        int[] line = Jpeg2000Dwt53.forward1D(new int[] {5}, origin, limits);
        int[] image = Jpeg2000Dwt53.forward(
                new int[] {5}, 1, 1, 1, origin, origin, limits);

        assertArrayEquals(new int[] {10}, line);
        assertArrayEquals(new int[] {5}, Jpeg2000Dwt53.inverse1D(line, origin, limits));
        assertArrayEquals(new int[] {20}, image);
        assertArrayEquals(new int[] {5}, Jpeg2000Dwt53.inverse(
                image, 1, 1, 1, origin, origin, limits));
    }

    @Test
    void validatesDimensionsAndAllocationLimitsBeforeTransform() {
        Jpeg2000Limits small = new Jpeg2000Limits(16, 4, 1, 4, 4, 10, 1);

        assertThrows(Jpeg2000Exception.class, () -> Jpeg2000Dwt53.forward(
                new int[6], 3, 2, 1, 0, 0, small));
        assertThrows(Jpeg2000Exception.class, () -> Jpeg2000Dwt53.forward(
                new int[5], 3, 2, 1, 0, 0, limits));
        assertThrows(Jpeg2000Exception.class, () -> Jpeg2000Dwt53.forward(
                new int[1], 1, 1, 33, 0, 0, limits));
    }
}
