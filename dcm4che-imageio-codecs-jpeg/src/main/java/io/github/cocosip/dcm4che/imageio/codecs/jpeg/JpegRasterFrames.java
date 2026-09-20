package io.github.cocosip.dcm4che.imageio.codecs.jpeg;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.awt.image.WritableRaster;

import javax.imageio.IIOException;
import javax.imageio.ImageReadParam;

import org.dcm4che3.imageio.codec.ImageDescriptor;

import io.github.cocosip.dcm4che.imageio.codecs.core.image.DicomImageTypes;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg.internal.JpegFrame;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg.internal.JpegSampling;

final class JpegRasterFrames {
    enum Flavor {
        BASELINE,
        EXTENDED,
        LOSSLESS
    }

    private JpegRasterFrames() {
    }

    static JpegFrame fromImage(ImageDescriptor descriptor, RenderedImage image)
            throws IIOException {
        return fromImage(descriptor, image, Flavor.BASELINE);
    }

    static JpegFrame fromImage(ImageDescriptor descriptor, RenderedImage image, boolean extended)
            throws IIOException {
        return fromImage(descriptor, image, extended ? Flavor.EXTENDED : Flavor.BASELINE);
    }

    static JpegFrame fromImage(ImageDescriptor descriptor, RenderedImage image, Flavor flavor)
            throws IIOException {
        validateDescriptor(descriptor, flavor);
        Raster raster = image.getData();
        validateImage(descriptor, image.getWidth(), image.getHeight(), raster.getNumBands(),
                raster.getDataBuffer().getDataType());
        int[] samples = new int[descriptor.getRows() * descriptor.getColumns()
                * descriptor.getSamples()];
        int index = 0;
        for (int y = raster.getMinY(); y < raster.getMinY() + raster.getHeight(); y++) {
            for (int x = raster.getMinX(); x < raster.getMinX() + raster.getWidth(); x++) {
                if (shouldConvertRgb(descriptor, flavor)) {
                    int[] ybr = rgbToYbr(raster.getSample(x, y, 0), raster.getSample(x, y, 1),
                            raster.getSample(x, y, 2), descriptor.getBitsStored());
                    for (int component = 0; component < 3; component++) {
                        samples[index++] = ybr[component];
                    }
                } else {
                    for (int component = 0; component < descriptor.getSamples(); component++) {
                        int value = raster.getSample(x, y, component);
                        if (descriptor.getSamples() == 1 && isMonochrome1(descriptor)) {
                            value = maxSample(descriptor) - value;
                        }
                        samples[index++] = value;
                    }
                }
            }
        }
        return JpegFrame.of(descriptor.getColumns(), descriptor.getRows(),
                descriptor.getSamples(), samples,
                flavor == Flavor.BASELINE ? 8 : descriptor.getBitsStored());
    }

    static BufferedImage toImage(ImageDescriptor descriptor, JpegFrame frame, ImageReadParam param)
            throws IIOException {
        return toImage(descriptor, frame, param, false);
    }

    static BufferedImage toImage(ImageDescriptor descriptor, JpegFrame frame, ImageReadParam param,
            boolean extended) throws IIOException {
        return toImage(descriptor, frame, param, extended ? Flavor.EXTENDED : Flavor.BASELINE);
    }

    static BufferedImage toImage(ImageDescriptor descriptor, JpegFrame frame, ImageReadParam param,
            Flavor flavor) throws IIOException {
        validateDescriptor(descriptor, flavor);
        int expectedPrecision = flavor == Flavor.BASELINE ? 8 : descriptor.getBitsStored();
        if (frame.precision() != expectedPrecision) {
            throw new IIOException("JPEG precision does not match DICOM BitsStored");
        }
        ReadRegion readRegion = readRegion(descriptor, param);
        int[] sourceBands = sourceBands(descriptor, param);
        int[] destinationBands = destinationBands(sourceBands, param);
        int destinationWidth = readRegion.destinationOffset.x + readRegion.width;
        int destinationHeight = readRegion.destinationOffset.y + readRegion.height;
        BufferedImage image = destinationImage(descriptor, param, destinationWidth, destinationHeight);
        WritableRaster raster = image.getRaster();
        validateDestinationImage(descriptor, image, raster, destinationBands, readRegion);
        int[] samples = frame.samples();
        for (int outputY = 0; outputY < readRegion.height; outputY++) {
            int sourceY = readRegion.source.y + readRegion.sourceYOffset
                    + outputY * readRegion.sourceYSubsampling;
            int destinationY = image.getMinY() + readRegion.destinationOffset.y + outputY;
            for (int outputX = 0; outputX < readRegion.width; outputX++) {
                int sourceX = readRegion.source.x + readRegion.sourceXOffset
                        + outputX * readRegion.sourceXSubsampling;
                int sampleIndex = (sourceY * descriptor.getColumns() + sourceX)
                        * descriptor.getSamples();
                int[] values = decodedPixel(descriptor, flavor, samples, sampleIndex);
                int destinationX = image.getMinX() + readRegion.destinationOffset.x + outputX;
                for (int band = 0; band < sourceBands.length; band++) {
                    raster.setSample(destinationX, destinationY, destinationBands[band],
                            values[sourceBands[band]]);
                }
            }
        }
        return image;
    }

    private static void validateDescriptor(ImageDescriptor descriptor, Flavor flavor)
            throws IIOException {
        int bitsStored = descriptor.getBitsStored();
        boolean baseline = flavor == Flavor.BASELINE;
        boolean validPrecision = baseline
                ? bitsStored <= 8
                : bitsStored >= 8 && bitsStored <= (flavor == Flavor.LOSSLESS ? 16 : 12);
        boolean validAllocation = !baseline
                ? descriptor.getBitsAllocated() == (bitsStored <= 8 ? 8 : 16)
                : descriptor.getBitsAllocated() == 8;
        if (!validAllocation || !validPrecision
                || (descriptor.getSamples() != 1 && descriptor.getSamples() != 3)
                || (!baseline && descriptor.isSigned())) {
            throw new IIOException(!baseline
                    ? flavor == Flavor.LOSSLESS
                    ? "JPEG Lossless requires unsigned 8-16 bit monochrome or RGB pixels"
                    : "JPEG Extended requires unsigned 8-12 bit monochrome or RGB pixels"
                    : "JPEG Baseline requires 8-bit monochrome or RGB pixels");
        }
    }

    private static void validateImage(ImageDescriptor descriptor, int width, int height,
            int bands, int dataType) throws IIOException {
        if (width != descriptor.getColumns() || height != descriptor.getRows()) {
            throw new IIOException("JPEG image dimensions do not match descriptor");
        }
        int expectedType = descriptor.getBitsAllocated() == 8
                ? DataBuffer.TYPE_BYTE
                : DataBuffer.TYPE_USHORT;
        if (bands != descriptor.getSamples() || dataType != expectedType) {
            throw new IIOException("JPEG image sample model does not match descriptor");
        }
    }

    private static ReadRegion readRegion(ImageDescriptor descriptor, ImageReadParam param)
            throws IIOException {
        Rectangle source = new Rectangle(0, 0, descriptor.getColumns(), descriptor.getRows());
        int sourceXSubsampling = param == null ? 1 : param.getSourceXSubsampling();
        int sourceYSubsampling = param == null ? 1 : param.getSourceYSubsampling();
        // ImageReadParam exposes the subsampling factors but not the offsets on Java 8.
        int sourceXOffset = 0;
        int sourceYOffset = 0;
        if (param != null && param.getSourceRegion() != null) {
            source = source.intersection(param.getSourceRegion());
        }
        if (source.isEmpty()) {
            throw new IIOException("JPEG source region is empty");
        }
        if (sourceXOffset >= source.width || sourceYOffset >= source.height) {
            throw new IIOException("JPEG source subsampling excludes the source region");
        }
        int width = (source.width - sourceXOffset + sourceXSubsampling - 1)
                / sourceXSubsampling;
        int height = (source.height - sourceYOffset + sourceYSubsampling - 1)
                / sourceYSubsampling;
        Point destinationOffset = param == null || param.getDestinationOffset() == null
                ? new Point() : param.getDestinationOffset();
        if (destinationOffset.x < 0 || destinationOffset.y < 0) {
            throw new IIOException("JPEG destination offset must be non-negative");
        }
        return new ReadRegion(source, sourceXSubsampling, sourceYSubsampling,
                sourceXOffset, sourceYOffset, width, height, destinationOffset);
    }

    private static BufferedImage destinationImage(ImageDescriptor descriptor, ImageReadParam param,
            int width, int height) throws IIOException {
        if (param != null && param.getDestination() != null) {
            return param.getDestination();
        }
        try {
            if (param != null && param.getDestinationType() != null) {
                return param.getDestinationType().createBufferedImage(width, height);
            }
            return DicomImageTypes.createType(descriptor).createBufferedImage(width, height);
        } catch (RuntimeException e) {
            throw new IIOException("Unable to create JPEG destination image", e);
        }
    }

    private static int[] sourceBands(ImageDescriptor descriptor, ImageReadParam param)
            throws IIOException {
        int[] requestedSource = param == null ? null : param.getSourceBands();
        int[] requestedDestination = param == null ? null : param.getDestinationBands();
        int count = requestedSource != null ? requestedSource.length
                : requestedDestination != null ? requestedDestination.length : descriptor.getSamples();
        int[] result = requestedSource == null ? sequence(count) : requestedSource.clone();
        for (int band : result) {
            if (band < 0 || band >= descriptor.getSamples()) {
                throw new IIOException("JPEG source band is outside the source image");
            }
        }
        if (requestedDestination != null && requestedDestination.length != result.length) {
            throw new IIOException("JPEG source and destination band counts differ");
        }
        return result;
    }

    private static int[] destinationBands(int[] sourceBands, ImageReadParam param)
            throws IIOException {
        int[] requested = param == null ? null : param.getDestinationBands();
        int[] result = requested == null ? sequence(sourceBands.length) : requested.clone();
        for (int band : result) {
            if (band < 0) {
                throw new IIOException("JPEG destination band must be non-negative");
            }
        }
        return result;
    }

    private static int[] sequence(int length) {
        int[] result = new int[length];
        for (int i = 0; i < length; i++) {
            result[i] = i;
        }
        return result;
    }

    private static void validateDestinationImage(ImageDescriptor descriptor, BufferedImage image,
            Raster raster, int[] destinationBands, ReadRegion region) throws IIOException {
        int expectedType = descriptor.getBitsAllocated() == 8
                ? DataBuffer.TYPE_BYTE : DataBuffer.TYPE_USHORT;
        if (raster.getDataBuffer().getDataType() != expectedType) {
            throw new IIOException("JPEG destination image sample type does not match descriptor");
        }
        for (int band : destinationBands) {
            if (band >= raster.getNumBands()) {
                throw new IIOException("JPEG destination band is outside the destination image");
            }
        }
        Rectangle target = new Rectangle(image.getMinX() + region.destinationOffset.x,
                image.getMinY() + region.destinationOffset.y, region.width, region.height);
        if (!raster.getBounds().contains(target)) {
            throw new IIOException("JPEG destination image is too small for the read region");
        }
    }

    private static int[] decodedPixel(ImageDescriptor descriptor, Flavor flavor, int[] samples,
            int index) {
        int[] values = new int[descriptor.getSamples()];
        if (shouldConvertRgb(descriptor, flavor)) {
            int[] rgb = ybrToRgb(samples[index], samples[index + 1], samples[index + 2],
                    descriptor.getBitsStored());
            System.arraycopy(rgb, 0, values, 0, values.length);
            return values;
        }
        for (int component = 0; component < values.length; component++) {
            values[component] = samples[index + component];
        }
        if (descriptor.getSamples() == 1 && isMonochrome1(descriptor)) {
            values[0] = maxSample(descriptor) - values[0];
        }
        return values;
    }

    private static final class ReadRegion {
        private final Rectangle source;
        private final int sourceXSubsampling;
        private final int sourceYSubsampling;
        private final int sourceXOffset;
        private final int sourceYOffset;
        private final int width;
        private final int height;
        private final Point destinationOffset;

        private ReadRegion(Rectangle source, int sourceXSubsampling, int sourceYSubsampling,
                int sourceXOffset, int sourceYOffset, int width, int height,
                Point destinationOffset) {
            this.source = source;
            this.sourceXSubsampling = sourceXSubsampling;
            this.sourceYSubsampling = sourceYSubsampling;
            this.sourceXOffset = sourceXOffset;
            this.sourceYOffset = sourceYOffset;
            this.width = width;
            this.height = height;
            this.destinationOffset = destinationOffset;
        }
    }

    private static boolean isMonochrome1(ImageDescriptor descriptor) {
        return "MONOCHROME1".equals(String.valueOf(descriptor.getPhotometricInterpretation()));
    }

    private static boolean isRgb(ImageDescriptor descriptor) {
        return "RGB".equals(String.valueOf(descriptor.getPhotometricInterpretation()));
    }

    private static boolean shouldConvertRgb(ImageDescriptor descriptor, Flavor flavor) {
        return flavor != Flavor.LOSSLESS && descriptor.getSamples() == 3 && isRgb(descriptor);
    }

    static JpegSampling sampling(ImageDescriptor descriptor) {
        return "YBR_FULL_422".equals(String.valueOf(descriptor.getPhotometricInterpretation()))
                ? JpegSampling.SF422 : JpegSampling.SF444;
    }

    private static int[] rgbToYbr(int red, int green, int blue, int precision) {
        int center = 1 << (precision - 1);
        int maximum = (1 << precision) - 1;
        int y = clamp((int) Math.round(0.299 * red + 0.587 * green + 0.114 * blue), maximum);
        int cb = clamp((int) Math.round(center - 0.168736 * red - 0.331264 * green
                + 0.5 * blue), maximum);
        int cr = clamp((int) Math.round(center + 0.5 * red - 0.418688 * green
                - 0.081312 * blue), maximum);
        return new int[] {y, cb, cr};
    }

    private static int[] ybrToRgb(int y, int cb, int cr, int precision) {
        int center = 1 << (precision - 1);
        int maximum = (1 << precision) - 1;
        int red = clamp((int) Math.round(y + 1.402 * (cr - center)), maximum);
        int green = clamp((int) Math.round(y - 0.344136 * (cb - center)
                - 0.714136 * (cr - center)), maximum);
        int blue = clamp((int) Math.round(y + 1.772 * (cb - center)), maximum);
        return new int[] {red, green, blue};
    }

    private static int clamp(int value, int maximum) {
        return Math.max(0, Math.min(maximum, value));
    }

    private static int maxSample(ImageDescriptor descriptor) {
        return (1 << descriptor.getBitsStored()) - 1;
    }
}
