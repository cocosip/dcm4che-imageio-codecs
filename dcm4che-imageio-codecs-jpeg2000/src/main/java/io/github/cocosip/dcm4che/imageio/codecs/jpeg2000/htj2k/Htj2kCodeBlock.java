package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.htj2k;

import java.util.Arrays;

import javax.imageio.IIOException;

/** A bounded HT code-block before packet contribution state is assigned. */
final class Htj2kCodeBlock {
    private final int width;
    private final int height;
    private final int kmax;
    private final int[] coefficients;

    Htj2kCodeBlock(int width, int height, int kmax, int[] coefficients)
            throws IIOException {
        if (width <= 0 || height <= 0 || width > 64 || height > 64
                || (long) width * height > 4096 || kmax < 2 || kmax > 30
                || coefficients == null || coefficients.length != width * height) {
            throw new IIOException("Invalid HTJ2K code-block geometry, precision, or sample count");
        }
        long maximum = (1L << kmax) - 1;
        for (int coefficient : coefficients) {
            if (Math.abs((long) coefficient) > maximum) {
                throw new IIOException("HTJ2K code-block coefficient exceeds Kmax=" + kmax);
            }
        }
        this.width = width;
        this.height = height;
        this.kmax = kmax;
        this.coefficients = Arrays.copyOf(coefficients, coefficients.length);
    }

    int width() {
        return width;
    }

    int height() {
        return height;
    }

    int kmax() {
        return kmax;
    }

    int coefficient(int x, int y) {
        return coefficients[y * width + x];
    }
}
