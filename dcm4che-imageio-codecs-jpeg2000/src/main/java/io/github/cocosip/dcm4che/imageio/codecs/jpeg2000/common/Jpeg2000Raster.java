package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common;

public final class Jpeg2000Raster {
    private final int width;
    private final int height;
    private final int bitsAllocated;
    private final int precision;
    private final boolean signed;
    private final String photometricInterpretation;
    private final int[][] components;

    Jpeg2000Raster(
            int width,
            int height,
            int bitsAllocated,
            int precision,
            boolean signed,
            String photometricInterpretation,
            int[][] components) {
        this.width = width;
        this.height = height;
        this.bitsAllocated = bitsAllocated;
        this.precision = precision;
        this.signed = signed;
        this.photometricInterpretation = photometricInterpretation;
        this.components = copy(components);
    }

    public static Jpeg2000Raster of(
            int width, int height, int bitsAllocated, int precision, boolean signed,
            String photometricInterpretation, int[][] components,
            Jpeg2000Limits limits) throws Jpeg2000Exception {
        if (components == null || (components.length != 1 && components.length != 3)) {
            throw new Jpeg2000Exception("JPEG 2000 raster requires one or three components");
        }
        if (bitsAllocated != 8 && bitsAllocated != 16 || precision < 1
                || precision > bitsAllocated) {
            throw new Jpeg2000Exception("JPEG 2000 raster precision is invalid");
        }
        int sampleBytes = limits.checkedSampleBufferBytes(
                width, height, components.length, Integer.BYTES);
        int pixels = sampleBytes / Integer.BYTES / components.length;
        int minimum = signed ? -(1 << (precision - 1)) : 0;
        int maximum = signed ? (1 << (precision - 1)) - 1
                : precision == 16 ? 0xffff : (1 << precision) - 1;
        for (int[] component : components) {
            if (component == null || component.length != pixels) {
                throw new Jpeg2000Exception("JPEG 2000 component dimensions do not match raster");
            }
            for (int sample : component) {
                if (sample < minimum || sample > maximum) {
                    throw new Jpeg2000Exception("JPEG 2000 raster sample exceeds declared precision");
                }
            }
        }
        return new Jpeg2000Raster(width, height, bitsAllocated, precision, signed,
                photometricInterpretation, components);
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int precision() {
        return precision;
    }

    public int bitsAllocated() {
        return bitsAllocated;
    }

    public boolean signed() {
        return signed;
    }

    public String photometricInterpretation() {
        return photometricInterpretation;
    }

    public int componentCount() {
        return components.length;
    }

    public int[] component(int index) {
        return components[index].clone();
    }

    public int[] levelShiftedComponent(int index) {
        int[] result = component(index);
        if (!signed) {
            int offset = 1 << (precision - 1);
            for (int sample = 0; sample < result.length; sample++) {
                result[sample] -= offset;
            }
        }
        return result;
    }

    public byte[] toFrame(boolean planar) throws Jpeg2000Exception {
        if (planar && components.length != 3) {
            throw new Jpeg2000Exception("JPEG 2000 planar output requires three components");
        }
        int bytesPerSample = bitsAllocated / 8;
        long length = (long) width * height * components.length * bytesPerSample;
        if (length > Integer.MAX_VALUE) {
            throw new Jpeg2000Exception("JPEG 2000 raster frame exceeds Java array limits");
        }
        byte[] frame = new byte[(int) length];
        int pixelCount = width * height;
        for (int pixel = 0; pixel < pixelCount; pixel++) {
            for (int component = 0; component < components.length; component++) {
                int sampleIndex = planar
                        ? component * pixelCount + pixel
                        : pixel * components.length + component;
                writeSample(frame, sampleIndex * bytesPerSample, components[component][pixel]);
            }
        }
        return frame;
    }

    private void writeSample(byte[] frame, int offset, int sample) throws Jpeg2000Exception {
        int mask = precision == 16 ? 0xffff : (1 << precision) - 1;
        int minimum = signed ? -(1 << (precision - 1)) : 0;
        int maximum = signed ? (1 << (precision - 1)) - 1 : mask;
        if (sample < minimum || sample > maximum) {
            throw new Jpeg2000Exception("JPEG 2000 raster sample is outside its declared precision");
        }
        int value = sample & mask;
        frame[offset] = (byte) value;
        if (bitsAllocated == 16) {
            frame[offset + 1] = (byte) (value >>> 8);
        }
    }

    private static int[][] copy(int[][] values) {
        int[][] result = new int[values.length][];
        for (int index = 0; index < values.length; index++) {
            result[index] = values[index].clone();
        }
        return result;
    }
}
