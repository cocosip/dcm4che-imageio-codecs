package io.github.cocosip.dcm4che.imageio.codecs.rle.internal;

import javax.imageio.IIOException;

import org.dcm4che3.imageio.codec.ImageDescriptor;

final class RlePixelLayout {
    private final int width;
    private final int height;
    private final int samplesPerPixel;
    private final int bytesAllocated;
    private final boolean planar;
    private final int pixelCount;
    private final int frameLength;

    private RlePixelLayout(int width, int height, int samplesPerPixel,
            int bytesAllocated, boolean planar, int pixelCount, int frameLength) {
        this.width = width;
        this.height = height;
        this.samplesPerPixel = samplesPerPixel;
        this.bytesAllocated = bytesAllocated;
        this.planar = planar;
        this.pixelCount = pixelCount;
        this.frameLength = frameLength;
    }

    static RlePixelLayout from(ImageDescriptor descriptor) throws IIOException {
        if (descriptor == null) {
            throw new NullPointerException("descriptor");
        }

        int width = descriptor.getColumns();
        int height = descriptor.getRows();
        int samples = descriptor.getSamples();
        int bitsAllocated = descriptor.getBitsAllocated();
        if (width <= 0 || height <= 0) {
            throw error("image dimensions must be positive");
        }
        if (samples != 1 && samples != 3) {
            throw error("SamplesPerPixel " + samples + " is not supported");
        }
        if (bitsAllocated != 8 && bitsAllocated != 16) {
            throw error("BitsAllocated " + bitsAllocated + " is not supported");
        }
        int planarConfiguration = descriptor.getPlanarConfiguration();
        if (samples > 1 && planarConfiguration != 0 && planarConfiguration != 1) {
            throw error("PlanarConfiguration " + planarConfiguration + " is not supported");
        }

        int bytesAllocated = bitsAllocated / 8;
        int pixelCount = checkedProduct(width, height, "pixel count");
        int frameLength = checkedProduct(
                checkedProduct(pixelCount, samples, "sample count"),
                bytesAllocated, "frame length");
        int segmentCount = samples * bytesAllocated;
        if (segmentCount > RleHeader.MAX_SEGMENT_COUNT) {
            throw error("segment count " + segmentCount + " exceeds 15");
        }
        return new RlePixelLayout(width, height, samples, bytesAllocated,
                samples > 1 && planarConfiguration == 1, pixelCount, frameLength);
    }

    int width() {
        return width;
    }

    int height() {
        return height;
    }

    int samplesPerPixel() {
        return samplesPerPixel;
    }

    int bytesAllocated() {
        return bytesAllocated;
    }

    int pixelCount() {
        return pixelCount;
    }

    int segmentCount() {
        return samplesPerPixel * bytesAllocated;
    }

    int frameLength() {
        return frameLength;
    }

    int rawOffset(int pixelIndex, int sampleIndex, int byteIndex) {
        if (planar) {
            return sampleIndex * pixelCount * bytesAllocated
                    + pixelIndex * bytesAllocated + byteIndex;
        }
        return pixelIndex * samplesPerPixel * bytesAllocated
                + sampleIndex * bytesAllocated + byteIndex;
    }

    private static int checkedProduct(int left, int right, String name) throws IIOException {
        long value = (long) left * right;
        if (value > Integer.MAX_VALUE) {
            throw error(name + " exceeds Java array limits: " + value);
        }
        return (int) value;
    }

    private static IIOException error(String message) {
        return new IIOException("RLE Lossless " + message);
    }
}
