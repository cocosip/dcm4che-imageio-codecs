package io.github.cocosip.dcm4che.imageio.codecs.jpeg.internal;

import java.util.Arrays;

public final class JpegFrame {
    private final int width;
    private final int height;
    private final int components;
    private final int precision;
    private final int[] samples;

    private JpegFrame(int width, int height, int components, int precision, int[] samples) {
        this.width = width;
        this.height = height;
        this.components = components;
        this.precision = precision;
        this.samples = samples;
    }

    public static JpegFrame of(int width, int height, int components, int[] samples) {
        return of(width, height, components, samples, 8);
    }

    public static JpegFrame of(int width, int height, int components, int[] samples,
            int precision) {
        if (width <= 0 || height <= 0 || (components != 1 && components != 3 && components != 4)) {
            throw new IllegalArgumentException("invalid JPEG frame dimensions/components");
        }
        if (precision < 8 || precision > 16) {
            throw new IllegalArgumentException("JPEG precision must be between 8 and 16 bits");
        }
        long expected = (long) width * height * components;
        if (expected > Integer.MAX_VALUE || samples == null || samples.length != expected) {
            throw new IllegalArgumentException("invalid JPEG frame sample count");
        }
        int[] copy = Arrays.copyOf(samples, samples.length);
        int maximum = (1 << precision) - 1;
        for (int sample : copy) {
            if (sample < 0 || sample > maximum) {
                throw new IllegalArgumentException("JPEG sample is outside precision range");
            }
        }
        return new JpegFrame(width, height, components, precision, copy);
    }

    public int width() { return width; }
    public int height() { return height; }
    public int components() { return components; }
    public int precision() { return precision; }
    public int[] samples() { return Arrays.copyOf(samples, samples.length); }
    public int sample(int x, int y, int component) { return samples[(y * width + x) * components + component]; }
}
