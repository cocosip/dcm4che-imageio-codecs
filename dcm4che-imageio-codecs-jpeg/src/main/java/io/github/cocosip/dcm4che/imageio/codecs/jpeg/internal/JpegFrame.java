package io.github.cocosip.dcm4che.imageio.codecs.jpeg.internal;

import java.util.Arrays;

public final class JpegFrame {
    private final int width;
    private final int height;
    private final int components;
    private final int[] samples;

    private JpegFrame(int width, int height, int components, int[] samples) {
        this.width = width;
        this.height = height;
        this.components = components;
        this.samples = samples;
    }

    public static JpegFrame of(int width, int height, int components, int[] samples) {
        if (width <= 0 || height <= 0 || (components != 1 && components != 3)) {
            throw new IllegalArgumentException("invalid JPEG frame dimensions/components");
        }
        long expected = (long) width * height * components;
        if (expected > Integer.MAX_VALUE || samples == null || samples.length != expected) {
            throw new IllegalArgumentException("invalid JPEG frame sample count");
        }
        int[] copy = Arrays.copyOf(samples, samples.length);
        for (int sample : copy) {
            if (sample < 0 || sample > 255) {
                throw new IllegalArgumentException("JPEG baseline samples must be 8-bit");
            }
        }
        return new JpegFrame(width, height, components, copy);
    }

    public int width() { return width; }
    public int height() { return height; }
    public int components() { return components; }
    public int[] samples() { return Arrays.copyOf(samples, samples.length); }
    public int sample(int x, int y, int component) { return samples[(y * width + x) * components + component]; }
}
