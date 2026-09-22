package io.github.cocosip.dcm4che.imageio.codecs.jpeg.internal;

import java.util.Arrays;

final class QuantizationTable {
    private final int[] values;

    private QuantizationTable(int[] values) {
        this.values = values;
    }

    static QuantizationTable of(int[] values) {
        if (values == null || values.length != 64) {
            throw new IllegalArgumentException("JPEG quantization table must contain 64 values");
        }
        int[] copy = Arrays.copyOf(values, values.length);
        for (int value : copy) {
            if (value < 1 || value > 0xffff) {
                throw new IllegalArgumentException("JPEG quantization value out of range: " + value);
            }
        }
        return new QuantizationTable(copy);
    }

    int get(int zigZagIndex) {
        return values[zigZagIndex];
    }

    int[] values() {
        return Arrays.copyOf(values, values.length);
    }
}
