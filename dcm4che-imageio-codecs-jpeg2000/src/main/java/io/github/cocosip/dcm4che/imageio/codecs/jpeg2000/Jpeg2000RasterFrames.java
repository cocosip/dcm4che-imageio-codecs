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
        boolean packed422 = "YBR_FULL_422".equals(
                String.valueOf(descriptor.getPhotometricInterpretation()));
        Raster raster = image.getData();
        if (raster.getNumBands() != components) {
            throw new IIOException("JPEG 2000 source raster band count does not match descriptor");
        }
        Jpeg2000Limits limits = Jpeg2000Limits.defaults();
        limits.checkedSampleBufferBytes(width, height, components, Integer.BYTES);
        int sampleBytes = descriptor.getBitsAllocated() / 8;
        int length = packed422
                ? limits.requireFrameLength(((width + 1L) / 2) * height * 4)
                : limits.checkedSampleBufferBytes(width, height, components, sampleBytes);
        byte[] frame = new byte[length];
        if (packed422) {
            if (components != 3 || sampleBytes != 1 || descriptor.getPlanarConfiguration() != 0) {
                throw new IIOException("JPEG 2000 YBR_FULL_422 requires interleaved 8-bit color");
            }
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x += 2) {
                    int index = (y * ((width + 1) / 2) + x / 2) * 4;
                    int firstX = raster.getMinX() + x;
                    int secondX = raster.getMinX() + Math.min(x + 1, width - 1);
                    int row = raster.getMinY() + y;
                    int cb = raster.getSample(firstX, row, 1);
                    int cr = raster.getSample(firstX, row, 2);
                    if (x + 1 < width && (cb != raster.getSample(secondX, row, 1)
                            || cr != raster.getSample(secondX, row, 2))) {
                        throw new IIOException("JPEG 2000 YBR_FULL_422 pair has different chroma samples");
                    }
                    frame[index] = (byte) raster.getSample(firstX, row, 0);
                    frame[index + 1] = (byte) raster.getSample(secondX, row, 0);
                    frame[index + 2] = (byte) cb;
                    frame[index + 3] = (byte) cr;
                }
            }
            return Jpeg2000RasterNormalizer.normalize(descriptor, frame, limits);
        }
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

    static Jpeg2000Raster asUnsignedCodes(Jpeg2000Raster raster) throws IIOException {
        if (!raster.signed()) {
            return raster;
        }
        int mask = raster.precision() == 16 ? 0xffff : (1 << raster.precision()) - 1;
        int[][] codes = new int[raster.componentCount()][];
        for (int component = 0; component < codes.length; component++) {
            codes[component] = raster.component(component);
            for (int i = 0; i < codes[component].length; i++) {
                codes[component][i] &= mask;
            }
        }
        return Jpeg2000Raster.of(raster.width(), raster.height(), raster.bitsAllocated(),
                raster.precision(), false, raster.photometricInterpretation(), codes,
                Jpeg2000Limits.defaults());
    }

    static BufferedImage toImage(ImageDescriptor descriptor, Jpeg2000Raster raster,
            ImageReadParam param) throws IIOException {
        if (raster.width() != descriptor.getColumns() || raster.height() != descriptor.getRows()
                || raster.precision() != descriptor.getBitsStored()
                || (raster.signed() != descriptor.isSigned()
                        && !(descriptor.isSigned() && !raster.signed()))
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
        Point destination = param == null ? new Point() : param.getDestinationOffset();
        int outWidth = (int) (((long) source.width - offsetX + stepX - 1) / stepX);
        int outHeight = (int) (((long) source.height - offsetY + stepY - 1) / stepY);
        if (destination.x < 0 || destination.y < 0) {
            throw new IIOException("JPEG 2000 destination offset cannot be negative");
        }
        BufferedImage image = param == null ? null : param.getDestination();
        if (image == null) {
            long requiredWidth = (long) destination.x + outWidth;
            long requiredHeight = (long) destination.y + outHeight;
            Jpeg2000Limits.defaults().checkedSampleBufferBytes(requiredWidth, requiredHeight,
                    raster.componentCount(), descriptor.getBitsAllocated() / 8);
            image = DicomImageTypes.createType(descriptor).createBufferedImage(
                    (int) requiredWidth, (int) requiredHeight);
        } else if ((long) destination.x + outWidth > image.getWidth()
                || (long) destination.y + outHeight > image.getHeight()) {
            throw new IIOException("JPEG 2000 destination image is too small");
        }
        WritableRaster output = image.getRaster();
        int[] sourceBands = param == null ? null : param.getSourceBands();
        int[] destinationBands = param == null ? null : param.getDestinationBands();
        int selected = sourceBands == null ? raster.componentCount() : sourceBands.length;
        if (selected == 0 || (destinationBands != null && destinationBands.length != selected)) {
            throw new IIOException("JPEG 2000 source and destination band counts differ");
        }
        for (int band = 0; band < selected; band++) {
            int sourceBand = sourceBands == null ? band : sourceBands[band];
            int destinationBand = destinationBands == null ? band : destinationBands[band];
            if (sourceBand < 0 || sourceBand >= raster.componentCount()
                    || destinationBand < 0 || destinationBand >= output.getNumBands()) {
                throw new IIOException("JPEG 2000 source or destination band is out of range");
            }
        }
        int[][] samples = new int[raster.componentCount()][];
        for (int c = 0; c < samples.length; c++) {
            samples[c] = raster.component(c);
        }
        for (int y = 0; y < outHeight; y++) {
            int inputY = source.y + offsetY + y * stepY;
            for (int x = 0; x < outWidth; x++) {
                int input = inputY * raster.width() + source.x + offsetX + x * stepX;
                for (int band = 0; band < selected; band++) {
                    int sourceBand = sourceBands == null ? band : sourceBands[band];
                    int destinationBand = destinationBands == null ? band : destinationBands[band];
                    output.setSample(destination.x + x, destination.y + y,
                            destinationBand, samples[sourceBand][input]);
                }
            }
        }
        return image;
    }
}
