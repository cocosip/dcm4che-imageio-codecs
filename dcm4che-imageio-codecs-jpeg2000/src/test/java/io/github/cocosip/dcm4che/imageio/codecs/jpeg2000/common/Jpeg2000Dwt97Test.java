package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class Jpeg2000Dwt97Test {
    private final Jpeg2000Limits limits = Jpeg2000Limits.defaults();

    @Test
    void roundTripsShortLinesAtBothOrigins() throws Exception {
        for (int length = 1; length <= 17; length++) {
            double[] samples = new double[length];
            for (int i = 0; i < length; i++) {
                samples[i] = (i * 17 % 29) - 13;
            }
            for (int origin = 0; origin < 2; origin++) {
                assertArrayEquals(samples, Jpeg2000Dwt97.inverse1D(
                        Jpeg2000Dwt97.forward1D(samples, origin, limits), origin, limits),
                        1e-9);
            }
        }
    }

    @Test
    void roundTripsOddTwoDimensionalMultiLevelImage() throws Exception {
        int width = 67;
        int height = 65;
        double[] samples = new double[width * height];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = (i * 31 % 251) - 125;
        }
        assertArrayEquals(samples, Jpeg2000Dwt97.inverse(
                Jpeg2000Dwt97.forward(samples, width, height, 5, 0, 0, limits),
                width, height, 5, 0, 0, limits), 1e-8);
    }

    @Test
    void rejectsInvalidGeometryAndNonFiniteSamples() {
        assertThrows(Jpeg2000Exception.class,
                () -> Jpeg2000Dwt97.forward(new double[] {1}, 2, 1, 0, 0, 0, limits));
        assertThrows(Jpeg2000Exception.class,
                () -> Jpeg2000Dwt97.forward1D(new double[] {Double.NaN}, 0, limits));
    }
}
