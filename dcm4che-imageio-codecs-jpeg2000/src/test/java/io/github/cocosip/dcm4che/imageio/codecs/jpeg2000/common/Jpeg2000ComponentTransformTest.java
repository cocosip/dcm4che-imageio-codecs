package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class Jpeg2000ComponentTransformTest {
    private final Jpeg2000Limits limits = Jpeg2000Limits.defaults();

    @Test
    void appliesReversibleColorTransformWithFloorDivision() throws Exception {
        int[][] transformed = Jpeg2000ComponentTransform.forwardReversible(
                new int[] {255, 0, -32768},
                new int[] {0, 255, 0},
                new int[] {0, 0, 32767},
                limits);

        assertArrayEquals(new int[] {63, 127, -1}, transformed[0]);
        assertArrayEquals(new int[] {0, -255, 32767}, transformed[1]);
        assertArrayEquals(new int[] {255, -255, -32768}, transformed[2]);
    }

    @Test
    void reconstructsReversibleColorComponentsExactly() throws Exception {
        int[] red = {0, 1, 255, -2048, 2047};
        int[] green = {0, 2, 17, 100, -100};
        int[] blue = {0, 3, 99, 2047, -2048};

        int[][] transformed = Jpeg2000ComponentTransform.forwardReversible(
                red, green, blue, limits);
        int[][] reconstructed = Jpeg2000ComponentTransform.inverseReversible(
                transformed[0], transformed[1], transformed[2], limits);

        assertArrayEquals(red, reconstructed[0]);
        assertArrayEquals(green, reconstructed[1]);
        assertArrayEquals(blue, reconstructed[2]);
    }

    @Test
    void validatesComponentLengthsAndLimitsBeforeTransform() {
        Jpeg2000Limits small = new Jpeg2000Limits(16, 3, 1, 1, 1, 10, 1);

        assertThrows(Jpeg2000Exception.class, () ->
                Jpeg2000ComponentTransform.forwardReversible(
                        new int[2], new int[1], new int[2], limits));
        assertThrows(Jpeg2000Exception.class, () ->
                Jpeg2000ComponentTransform.forwardReversible(
                        new int[4], new int[4], new int[4], small));
    }
}
