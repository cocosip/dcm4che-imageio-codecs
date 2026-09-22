package io.github.cocosip.dcm4che.imageio.codecs.jpegls;

import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.awt.image.WritableRaster;

import javax.imageio.IIOException;

import org.dcm4che3.imageio.codec.ImageDescriptor;

final class JpegLsRasterFrames {
    private JpegLsRasterFrames() {
    }

    static int[] toSamples(ImageDescriptor descriptor, RenderedImage image) throws IIOException {
        validateDescriptor(descriptor);
        Raster raster = image.getData();
        validateImage(descriptor, image.getWidth(), image.getHeight(), raster.getNumBands(),
                raster.getDataBuffer().getDataType());
        int count = checkedCount(descriptor) * descriptor.getSamples();
        int mask = descriptor.getBitsStored() == 16
                ? 0xffff : (1 << descriptor.getBitsStored()) - 1;
        int[] samples = new int[count];
        int index = 0;
        for (int y = raster.getMinY(); y < raster.getMinY() + raster.getHeight(); y++) {
            for (int x = raster.getMinX(); x < raster.getMinX() + raster.getWidth(); x++) {
                for (int band = 0; band < descriptor.getSamples(); band++) {
                    samples[index++] = raster.getSample(x, y, band) & mask;
                }
            }
        }
        return samples;
    }

    static void copyToImage(ImageDescriptor descriptor, int[] samples, BufferedImage image)
            throws IIOException {
        validateDescriptor(descriptor);
        WritableRaster raster = image.getRaster();
        validateImage(descriptor, image.getWidth(), image.getHeight(), raster.getNumBands(),
                raster.getDataBuffer().getDataType());
        int count = checkedCount(descriptor) * descriptor.getSamples();
        if (samples.length != count) {
            throw new IIOException("JPEG-LS sample count does not match descriptor");
        }
        int index = 0;
        for (int y = raster.getMinY(); y < raster.getMinY() + raster.getHeight(); y++) {
            for (int x = raster.getMinX(); x < raster.getMinX() + raster.getWidth(); x++) {
                for (int band = 0; band < descriptor.getSamples(); band++) {
                    raster.setSample(x, y, band, samples[index++]);
                }
            }
        }
    }

    private static void validateDescriptor(ImageDescriptor descriptor) throws IIOException {
        if (descriptor.getSamples() != 1 && descriptor.getSamples() != 3) {
            throw new IIOException("JPEG-LS ImageIO supports one or three components");
        }
        if (descriptor.getBitsAllocated() != 8 && descriptor.getBitsAllocated() != 16) {
            throw new IIOException("JPEG-LS requires 8 or 16 BitsAllocated");
        }
    }

    private static void validateImage(ImageDescriptor descriptor, int width, int height,
            int bands, int dataType) throws IIOException {
        if (width != descriptor.getColumns() || height != descriptor.getRows()) {
            throw new IIOException("JPEG-LS image dimensions do not match descriptor");
        }
        if (bands != descriptor.getSamples()) {
            throw new IIOException("JPEG-LS image band count does not match descriptor");
        }
        int expected = descriptor.getBitsAllocated() == 8
                ? DataBuffer.TYPE_BYTE
                : descriptor.isSigned() ? DataBuffer.TYPE_SHORT : DataBuffer.TYPE_USHORT;
        if (dataType != expected) {
            throw new IIOException("JPEG-LS image data type does not match descriptor");
        }
    }

    private static int checkedCount(ImageDescriptor descriptor) throws IIOException {
        long count = (long) descriptor.getRows() * descriptor.getColumns();
        if (count <= 0 || count > Integer.MAX_VALUE) {
            throw new IIOException("JPEG-LS pixel count exceeds Java array limits");
        }
        return (int) count;
    }
}
