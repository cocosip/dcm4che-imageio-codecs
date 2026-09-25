package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.awt.image.WritableRaster;

import javax.imageio.IIOException;
import javax.imageio.ImageReadParam;

import org.dcm4che3.imageio.codec.ImageDescriptor;

import io.github.cocosip.dcm4che.imageio.codecs.core.image.DicomImageTypes;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Limits;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Raster;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000RasterNormalizer;

final class Jpeg2000RasterFrames {
    private Jpeg2000RasterFrames() {
    }

    static Jpeg2000Raster fromImage(ImageDescriptor descriptor, RenderedImage image)
            throws IIOException {
        int width = descriptor.getColumns();
        int height = descriptor.getRows();
        int components = descriptor.getSamples();
        if (image.getWidth() != width || image.getHeight() != height) {
            throw new IIOException("JPEG 2000 source image dimensions do not match descriptor");
        }
        if ("YBR_FULL_422".equals(String.valueOf(descriptor.getPhotometricInterpretation()))) {
            throw new IIOException("JPEG 2000 packed YBR_FULL_422 ImageIO input is unsupported");
        }
        Raster raster = image.getData();
        if (raster.getNumBands() != components) {
            throw new IIOException("JPEG 2000 source raster band count does not match descriptor");
        }
        Jpeg2000Limits limits = Jpeg2000Limits.defaults();
        int sampleBytes = descriptor.getBitsAllocated() / 8;
        int length = limits.checkedSampleBufferBytes(width, height, components, sampleBytes);
        byte[] frame = new byte[length];
        int pixels = width * height;
        boolean planar = components == 3 && descriptor.getPlanarConfiguration() == 1;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = y * width + x;
                for (int c = 0; c < components; c++) {
                    int sample = raster.getSample(raster.getMinX() + x, raster.getMinY() + y, c);
                    int index = (planar ? c * pixels + pixel : pixel * components + c) * sampleBytes;
                    frame[index] = (byte) sample;
                    if (sampleBytes == 2) {
                        frame[index + 1] = (byte) (sample >>> 8);
                    }
                }
            }
        }
        return Jpeg2000RasterNormalizer.normalize(descriptor, frame, limits);
    }

    static BufferedImage toImage(ImageDescriptor descriptor, Jpeg2000Raster raster,
            ImageReadParam param) throws IIOException {
        if (raster.width() != descriptor.getColumns() || raster.height() != descriptor.getRows()
                || raster.precision() != descriptor.getBitsStored()
                || raster.signed() != descriptor.isSigned()
                || raster.componentCount() != descriptor.getSamples()) {
            throw new IIOException("JPEG 2000 codestream SIZ does not match DICOM descriptor");
        }
        Rectangle source = new Rectangle(0, 0, raster.width(), raster.height());
        if (param != null && param.getSourceRegion() != null) {
            source = source.intersection(param.getSourceRegion());
        }
        int stepX = param == null ? 1 : param.getSourceXSubsampling();
        int stepY = param == null ? 1 : param.getSourceYSubsampling();
        int offsetX = param == null ? 0 : param.getSubsamplingXOffset();
        int offsetY = param == null ? 0 : param.getSubsamplingYOffset();
        if (source.isEmpty() || offsetX >= source.width || offsetY >= source.height) {
            throw new IIOException("JPEG 2000 source region is empty");
        }
        if (param != null && (param.getSourceBands() != null || param.getDestinationBands() != null
                || param.getDestination() != null)) {
            throw new IIOException("JPEG 2000 band selection or destination image is unsupported");
        }
        Point destination = param == null ? new Point() : param.getDestinationOffset();
        int outWidth = (source.width - offsetX + stepX - 1) / stepX;
        int outHeight = (source.height - offsetY + stepY - 1) / stepY;
        if (destination.x < 0 || destination.y < 0) {
            throw new IIOException("JPEG 2000 destination offset cannot be negative");
        }
        BufferedImage image = DicomImageTypes.createType(descriptor).createBufferedImage(
                Math.addExact(destination.x, outWidth), Math.addExact(destination.y, outHeight));
        WritableRaster output = image.getRaster();
        int[][] samples = new int[raster.componentCount()][];
        for (int c = 0; c < samples.length; c++) {
            samples[c] = raster.component(c);
        }
        for (int y = 0; y < outHeight; y++) {
            int inputY = source.y + offsetY + y * stepY;
            for (int x = 0; x < outWidth; x++) {
                int input = inputY * raster.width() + source.x + offsetX + x * stepX;
                for (int c = 0; c < samples.length; c++) {
                    output.setSample(destination.x + x, destination.y + y, c, samples[c][input]);
                }
            }
        }
        return image;
    }
}
