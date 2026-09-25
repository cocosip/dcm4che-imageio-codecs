package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.htj2k;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;

import javax.imageio.IIOException;

import org.junit.jupiter.api.Test;

class Htj2kCleanupPassTest {
    @Test
    void matchesOpenJphCleanupBlocks() throws Exception {
        assertCleanup("FE006300", 1, 1, 8, 1);
        assertCleanup("006300", 1, 1, 8, -1);
        assertCleanup("60297300", 2, 2, 8, 1, -2, 3, 0);
        assertCleanup("C00300", 4, 2, 8, 0, 0, 0, 0, 0, 0, 0, 0);
        assertCleanup("8088B40316B400", 4, 2, 8, 1, 2, 3, 4, 5, 6, 7, 8);
        assertCleanup("E02FFB7400", 1, 3, 8, 1, 2, 3);
        assertCleanup("20C1EA22F6B400", 2, 4, 8, 1, 2, 3, 4, 5, 6, 7, 8);
        assertCleanup("C4A6FE4119D7FD7600", 3, 3, 8, 0, 1, -2, 3, 0, 4, -5, 6, 0);
        assertCleanup("8088B4014B25360AD65F16B600", 4, 4, 8,
                1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16);
        assertCleanup("FC01077400", 1, 1, 8, 127);
        assertCleanup("FD01077400", 1, 1, 8, -127);
        assertCleanup("0833E21D582EAFF6A028E602BDCEFF5DCBA6FCED7B00",
                5, 5, 12, 0, 1, -2, 3, -4, 5, 0, -6, 7, 8, -9, 10, 0,
                -11, 12, 13, -14, 15, 0, -16, 17, 18, -19, 20, 0);
        assertCleanup("FCFF7F0C077400", 1, 1, 30, 536870911);
    }

    @Test
    void rejectsInvalidCleanupBounds() {
        assertThrows(IIOException.class, () -> Htj2kCleanupPassDecoder.decode(
                hex("006300"), Integer.MAX_VALUE, 1, 8));
        assertThrows(IIOException.class, () -> Htj2kCleanupPassDecoder.decode(
                hex("000000"), 1, 1, 8));
        assertThrows(IIOException.class, () -> Htj2kCleanupPassDecoder.decode(
                hex("006300"), 1, 1, 31));
    }

    private static void assertCleanup(String expected, int width, int height,
            int kmax, int... coefficients) throws Exception {
        byte[] bytes = Htj2kCleanupPassEncoder.encode(
                new Htj2kCodeBlock(width, height, kmax, coefficients));
        assertArrayEquals(hex(expected), bytes, Arrays.toString(bytes));
        Htj2kCodeBlock decoded = Htj2kCleanupPassDecoder.decode(
                hex(expected), width, height, kmax);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                org.junit.jupiter.api.Assertions.assertEquals(coefficients[y * width + x],
                        decoded.coefficient(x, y), "sample " + x + "," + y);
            }
        }
    }

    private static byte[] hex(String value) {
        byte[] bytes = new byte[value.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(value.substring(2 * i, 2 * i + 2), 16);
        }
        return bytes;
    }
}
