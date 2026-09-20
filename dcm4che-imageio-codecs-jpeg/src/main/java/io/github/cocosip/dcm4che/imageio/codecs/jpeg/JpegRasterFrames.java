package io.github.cocosip.dcm4che.imageio.codecs.jpeg;

import java.awt.Point;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;

import javax.imageio.IIOException;
import javax.imageio.ImageReadParam;

import org.dcm4che3.imageio.codec.ImageDescriptor;

import io.github.cocosip.dcm4che.imageio.codecs.core.image.DicomImageTypes;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg.internal.JpegFrame;

final class JpegRasterFrames {
    private JpegRasterFrames() {
    }

    static JpegFrame fromImage(ImageDescriptor descriptor, RenderedImage image)
            throws IIOException {
        validateDescriptor(descriptor);
        Raster raster = image.getData();
        validateImage(descriptor, image.getWidth(), image.getHeight(), raster.getNumBands(),
                raster.getDataBuffer().getDataType());
        int[] samples = new int[descriptor.getRows() * descriptor.getColumns()
                * descriptor.getSamples()];
        int index = 0;
        for (int y = raster.getMinY(); y < raster.getMinY() + raster.getHeight(); y++) {
            for (int x = raster.getMinX(); x < raster.getMinX() + raster.getWidth(); x++) {
                for (int component = 0; component < descriptor.getSamples(); component++) {
                    int value = raster.getSample(x, y, component);
                    if (descriptor.getSamples() == 1 && isMonochrome1(descriptor)) {
                        value = 255 - value;
                    }
                    samples[index++] = value;
                }
            }
        }
        return JpegFrame.of(descriptor.getColumns(), descriptor.getRows(),
                descriptor.getSamples(), samples);
    }

    static BufferedImage toImage(ImageDescriptor descriptor, JpegFrame frame, ImageReadParam param)
            throws IIOException {
        validateDescriptor(descriptor);
        validateReadParam(param);
        BufferedImage image = param != null && param.getDestination() != null
                ? param.getDestination()
                : param != null && param.getDestinationType() != null
                ? param.getDestinationType().createBufferedImage(
                        descriptor.getColumns(), descriptor.getRows())
                : DicomImageTypes.createImage(descriptor);
        Raster raster = image.getRaster();
        validateImage(descriptor, image.getWidth(), image.getHeight(), raster.getNumBands(),
                raster.getDataBuffer().getDataType());
        int[] samples = frame.samples();
        int index = 0;
        for (int y = raster.getMinY(); y < raster.getMinY() + raster.getHeight(); y++) {
            for (int x = raster.getMinX(); x < raster.getMinX() + raster.getWidth(); x++) {
                for (int component = 0; component < descriptor.getSamples(); component++) {
                    int value = samples[index++];
                    if (descriptor.getSamples() == 1 && isMonochrome1(descriptor)) {
                        value = 255 - value;
                    }
                    image.getRaster().setSample(x, y, component, value);
                }
            }
        }
        return image;
    }

    private static void validateDescriptor(ImageDescriptor descriptor) throws IIOException {
        if (descriptor.getBitsAllocated() != 8 || descriptor.getBitsStored() > 8
                || (descriptor.getSamples() != 1 && descriptor.getSamples() != 3)) {
            throw new IIOException("JPEG Baseline requires 8-bit monochrome or RGB pixels");
        }
        String photometric = String.valueOf(descriptor.getPhotometricInterpretation());
        if ("YBR_FULL_422".equals(photometric)) {
            throw new IIOException("JPEG Baseline does not support YBR_FULL_422");
        }
    }

    private static void validateImage(ImageDescriptor descriptor, int width, int height,
            int bands, int dataType) throws IIOException {
        if (width != descriptor.getColumns() || height != descriptor.getRows()) {
            throw new IIOException("JPEG image dimensions do not match descriptor");
        }
        if (bands != descriptor.getSamples() || dataType != DataBuffer.TYPE_BYTE) {
            throw new IIOException("JPEG image sample model does not match descriptor");
        }
    }

    private static void validateReadParam(ImageReadParam param) throws IIOException {
        if (param == null) {
            return;
        }
        Point offset = param.getDestinationOffset();
        if (param.getSourceRegion() != null || param.getSourceXSubsampling() != 1
                || param.getSourceYSubsampling() != 1 || offset.x != 0 || offset.y != 0
                || param.getSourceBands() != null || param.getDestinationBands() != null) {
            throw new IIOException("JPEG Baseline does not support regions, subsampling, offsets, or band selection");
        }
    }

    private static boolean isMonochrome1(ImageDescriptor descriptor) {
        return "MONOCHROME1".equals(String.valueOf(descriptor.getPhotometricInterpretation()));
    }
}
