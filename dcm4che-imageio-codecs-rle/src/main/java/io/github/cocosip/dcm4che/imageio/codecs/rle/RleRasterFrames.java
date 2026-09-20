package io.github.cocosip.dcm4che.imageio.codecs.rle;

import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.awt.image.WritableRaster;

import javax.imageio.IIOException;

import org.dcm4che3.imageio.codec.ImageDescriptor;

final class RleRasterFrames {

    private RleRasterFrames() {
    }

    static byte[] toRawFrame(ImageDescriptor descriptor, RenderedImage image)
            throws IIOException {
        Raster raster = image.getData();
        validate(descriptor, image.getWidth(), image.getHeight(), raster.getNumBands(),
                raster.getDataBuffer().getDataType());
        int bytesAllocated = descriptor.getBitsAllocated() / 8;
        int pixelCount = checkedPixelCount(descriptor);
        byte[] raw = new byte[pixelCount * descriptor.getSamples() * bytesAllocated];
        int pixelIndex = 0;
        for (int y = raster.getMinY(); y < raster.getMinY() + raster.getHeight(); y++) {
            for (int x = raster.getMinX(); x < raster.getMinX() + raster.getWidth(); x++) {
                for (int sample = 0; sample < descriptor.getSamples(); sample++) {
                    int value = raster.getSample(x, y, sample);
                    int offset = rawOffset(
                            descriptor, pixelCount, pixelIndex, sample, bytesAllocated);
                    raw[offset] = (byte) value;
                    if (bytesAllocated == 2) {
                        raw[offset + 1] = (byte) (value >>> 8);
                    }
                }
                pixelIndex++;
            }
        }
        return raw;
    }

    static void copyToImage(ImageDescriptor descriptor, byte[] raw, BufferedImage image)
            throws IIOException {
        WritableRaster raster = image.getRaster();
        validate(descriptor, image.getWidth(), image.getHeight(), raster.getNumBands(),
                raster.getDataBuffer().getDataType());
        int bytesAllocated = descriptor.getBitsAllocated() / 8;
        int pixelCount = checkedPixelCount(descriptor);
        int expectedLength = pixelCount * descriptor.getSamples() * bytesAllocated;
        if (raw.length != expectedLength) {
            throw new IIOException("RLE Lossless raw frame length " + raw.length
                    + " does not match expected length " + expectedLength);
        }

        int pixelIndex = 0;
        for (int y = raster.getMinY(); y < raster.getMinY() + raster.getHeight(); y++) {
            for (int x = raster.getMinX(); x < raster.getMinX() + raster.getWidth(); x++) {
                for (int sample = 0; sample < descriptor.getSamples(); sample++) {
                    int offset = rawOffset(
                            descriptor, pixelCount, pixelIndex, sample, bytesAllocated);
                    int value = raw[offset] & 0xff;
                    if (bytesAllocated == 2) {
                        value |= (raw[offset + 1] & 0xff) << 8;
                    }
                    raster.setSample(x, y, sample, value);
                }
                pixelIndex++;
            }
        }
    }

    private static void validate(ImageDescriptor descriptor, int width, int height,
            int bands, int dataType) throws IIOException {
        if (width != descriptor.getColumns() || height != descriptor.getRows()) {
            throw new IIOException("RLE Lossless image dimensions " + width + "x" + height
                    + " do not match descriptor " + descriptor.getColumns()
                    + "x" + descriptor.getRows());
        }
        if (bands != descriptor.getSamples()) {
            throw new IIOException("RLE Lossless image band count " + bands
                    + " does not match SamplesPerPixel " + descriptor.getSamples());
        }
        int expectedType = descriptor.getBitsAllocated() == 8
                ? DataBuffer.TYPE_BYTE
                : descriptor.isSigned() ? DataBuffer.TYPE_SHORT : DataBuffer.TYPE_USHORT;
        if (dataType != expectedType) {
            throw new IIOException("RLE Lossless image data type " + dataType
                    + " does not match descriptor data type " + expectedType);
        }
    }

    private static int checkedPixelCount(ImageDescriptor descriptor) throws IIOException {
        long count = (long) descriptor.getRows() * descriptor.getColumns();
        if (count <= 0 || count > Integer.MAX_VALUE) {
            throw new IIOException("RLE Lossless pixel count is outside Java array limits: "
                    + count);
        }
        return (int) count;
    }

    private static int rawOffset(ImageDescriptor descriptor, int pixelCount,
            int pixelIndex, int sample, int bytesAllocated) {
        if (descriptor.isBanded() && descriptor.getSamples() > 1) {
            return sample * pixelCount * bytesAllocated + pixelIndex * bytesAllocated;
        }
        return pixelIndex * descriptor.getSamples() * bytesAllocated
                + sample * bytesAllocated;
    }
}
