package io.github.cocosip.dcm4che.imageio.codecs.jpegls;

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

final class JpegLsRasterFrames {
    private JpegLsRasterFrames() {
    }

    static int[] toSamples(ImageDescriptor descriptor, RenderedImage image) throws IIOException {
        return toSamples(descriptor, image, false);
    }

    static int[] toSamples(ImageDescriptor descriptor, RenderedImage image,
            boolean signedDomain) throws IIOException {
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
                    samples[index++] = encodeSample(descriptor, raster.getSample(x, y, band), mask,
                            signedDomain);
                }
            }
        }
        return samples;
    }

    static void copyToImage(ImageDescriptor descriptor, int[] samples, BufferedImage image)
            throws IIOException {
        copyToImage(descriptor, samples, image, false);
    }

    static void copyToImage(ImageDescriptor descriptor, int[] samples, BufferedImage image,
            boolean signedDomain) throws IIOException {
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
                    raster.setSample(x, y, band,
                            decodeSample(descriptor, samples[index++], signedDomain));
                }
            }
        }
    }

    static BufferedImage toImage(ImageDescriptor descriptor, int[] samples, ImageReadParam param)
            throws IIOException {
        return toImage(descriptor, samples, param, false);
    }

    static BufferedImage toImage(ImageDescriptor descriptor, int[] samples, ImageReadParam param,
            boolean signedDomain) throws IIOException {
        validateDescriptor(descriptor);
        int count = checkedCount(descriptor) * descriptor.getSamples();
        if (samples.length != count) {
            throw new IIOException("JPEG-LS sample count does not match descriptor");
        }
        ReadRegion region = readRegion(descriptor, param);
        int[] sourceBands = sourceBands(descriptor, param);
        int[] destinationBands = destinationBands(sourceBands, param);
        int destinationWidth = region.destinationOffset.x + region.width;
        int destinationHeight = region.destinationOffset.y + region.height;
        BufferedImage image = destinationImage(descriptor, param,
                destinationWidth, destinationHeight);
        WritableRaster raster = image.getRaster();
        validateDestinationImage(descriptor, image, raster, destinationBands, region);
        for (int outputY = 0; outputY < region.height; outputY++) {
            int sourceY = region.source.y + region.sourceYOffset
                    + outputY * region.sourceYSubsampling;
            int destinationY = image.getMinY() + region.destinationOffset.y + outputY;
            for (int outputX = 0; outputX < region.width; outputX++) {
                int sourceX = region.source.x + region.sourceXOffset
                        + outputX * region.sourceXSubsampling;
                int sourceIndex = (sourceY * descriptor.getColumns() + sourceX)
                        * descriptor.getSamples();
                int destinationX = image.getMinX() + region.destinationOffset.x + outputX;
                for (int band = 0; band < sourceBands.length; band++) {
                    raster.setSample(destinationX, destinationY, destinationBands[band],
                            decodeSample(descriptor, samples[sourceIndex + sourceBands[band]],
                                    signedDomain));
                }
            }
        }
        return image;
    }

    private static void validateDescriptor(ImageDescriptor descriptor) throws IIOException {
        if (descriptor.getSamples() != 1 && descriptor.getSamples() != 3) {
            throw new IIOException("JPEG-LS ImageIO supports one or three components");
        }
        if (descriptor.getBitsAllocated() != 8 && descriptor.getBitsAllocated() != 16) {
            throw new IIOException("JPEG-LS requires 8 or 16 BitsAllocated");
        }
        if (descriptor.getBitsStored() < 2
                || descriptor.getBitsStored() > descriptor.getBitsAllocated()) {
            throw new IIOException("JPEG-LS BitsStored must be between 2 and BitsAllocated");
        }
        String photometric = String.valueOf(descriptor.getPhotometricInterpretation());
        if (descriptor.getSamples() == 1) {
            if (!"MONOCHROME1".equals(photometric)
                    && !"MONOCHROME2".equals(photometric)
                    && !"PALETTE COLOR".equals(photometric)) {
                throw new IIOException("JPEG-LS requires a supported monochrome or palette photometric interpretation");
            }
        } else if (!"RGB".equals(photometric) && !"YBR_FULL".equals(photometric)) {
            throw new IIOException("JPEG-LS standard profile rejects subsampled or partial color photometric interpretations");
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

    private static ReadRegion readRegion(ImageDescriptor descriptor, ImageReadParam param)
            throws IIOException {
        Rectangle source = new Rectangle(0, 0, descriptor.getColumns(), descriptor.getRows());
        int sourceXSubsampling = param == null ? 1 : param.getSourceXSubsampling();
        int sourceYSubsampling = param == null ? 1 : param.getSourceYSubsampling();
        int sourceXOffset = param == null ? 0 : param.getSubsamplingXOffset();
        int sourceYOffset = param == null ? 0 : param.getSubsamplingYOffset();
        if (param != null && param.getSourceRegion() != null) {
            source = source.intersection(param.getSourceRegion());
        }
        if (source.isEmpty()) {
            throw new IIOException("JPEG-LS source region is empty");
        }
        if (sourceXOffset >= source.width || sourceYOffset >= source.height) {
            throw new IIOException("JPEG-LS source subsampling excludes the source region");
        }
        int width = (source.width - sourceXOffset + sourceXSubsampling - 1)
                / sourceXSubsampling;
        int height = (source.height - sourceYOffset + sourceYSubsampling - 1)
                / sourceYSubsampling;
        Point destinationOffset = param == null || param.getDestinationOffset() == null
                ? new Point() : param.getDestinationOffset();
        if (destinationOffset.x < 0 || destinationOffset.y < 0) {
            throw new IIOException("JPEG-LS destination offset must be non-negative");
        }
        return new ReadRegion(source, sourceXSubsampling, sourceYSubsampling,
                sourceXOffset, sourceYOffset, width, height, destinationOffset);
    }

    private static BufferedImage destinationImage(ImageDescriptor descriptor, ImageReadParam param,
            int width, int height) throws IIOException {
        if (param != null && param.getDestination() != null) return param.getDestination();
        try {
            if (param != null && param.getDestinationType() != null) {
                return param.getDestinationType().createBufferedImage(width, height);
            }
            return DicomImageTypes.createType(descriptor).createBufferedImage(width, height);
        } catch (RuntimeException e) {
            throw new IIOException("Unable to create JPEG-LS destination image", e);
        }
    }

    private static int[] sourceBands(ImageDescriptor descriptor, ImageReadParam param)
            throws IIOException {
        int[] requestedSource = param == null ? null : param.getSourceBands();
        int[] requestedDestination = param == null ? null : param.getDestinationBands();
        int[] result = requestedSource == null
                ? sequence(descriptor.getSamples()) : requestedSource.clone();
        for (int band : result) {
            if (band < 0 || band >= descriptor.getSamples()) {
                throw new IIOException("JPEG-LS source band is outside the source image");
            }
        }
        if (requestedDestination != null && requestedDestination.length != result.length) {
            throw new IIOException("JPEG-LS source and destination band counts differ");
        }
        return result;
    }

    private static int[] destinationBands(int[] sourceBands, ImageReadParam param)
            throws IIOException {
        int[] requested = param == null ? null : param.getDestinationBands();
        int[] result = requested == null ? sequence(sourceBands.length) : requested.clone();
        for (int band : result) {
            if (band < 0) {
                throw new IIOException("JPEG-LS destination band must be non-negative");
            }
        }
        return result;
    }

    private static int[] sequence(int length) {
        int[] result = new int[length];
        for (int i = 0; i < length; i++) result[i] = i;
        return result;
    }

    private static int encodeSample(ImageDescriptor descriptor, int sample, int mask,
            boolean signedDomain) {
        int value = sample & mask;
        // Near-Lossless signed data is offset into an unsigned domain so the
        // requested error is measured across the signed zero boundary. Lossless
        // paths keep the original DICOM two's-complement bit pattern.
        return descriptor.isSigned() && signedDomain ? value ^ signBit(descriptor) : value;
    }

    private static int decodeSample(ImageDescriptor descriptor, int sample,
            boolean signedDomain) {
        int mask = descriptor.getBitsStored() == 16
                ? 0xffff : (1 << descriptor.getBitsStored()) - 1;
        int value = sample & mask;
        // Keep unsigned 16-bit values in 0..65535. Signed output reverses the
        // near-lossless offset, when present, then sign-extends from BitsStored.
        return descriptor.isSigned()
                ? signExtend(signedDomain ? value ^ signBit(descriptor) : value,
                        descriptor.getBitsStored()) : value;
    }

    private static int signBit(ImageDescriptor descriptor) {
        return 1 << (descriptor.getBitsStored() - 1);
    }

    private static int signExtend(int value, int bitsStored) {
        int signBit = 1 << (bitsStored - 1);
        return (value & signBit) == 0 ? value : value - (signBit << 1);
    }

    private static void validateDestinationImage(ImageDescriptor descriptor, BufferedImage image,
            Raster raster, int[] destinationBands, ReadRegion region) throws IIOException {
        int expectedType = descriptor.getBitsAllocated() == 8
                ? DataBuffer.TYPE_BYTE
                : descriptor.isSigned() ? DataBuffer.TYPE_SHORT : DataBuffer.TYPE_USHORT;
        if (raster.getDataBuffer().getDataType() != expectedType) {
            throw new IIOException("JPEG-LS destination image sample type does not match descriptor");
        }
        for (int band : destinationBands) {
            if (band >= raster.getNumBands()) {
                throw new IIOException("JPEG-LS destination band is outside the destination image");
            }
        }
        Rectangle target = new Rectangle(image.getMinX() + region.destinationOffset.x,
                image.getMinY() + region.destinationOffset.y, region.width, region.height);
        if (!raster.getBounds().contains(target)) {
            throw new IIOException("JPEG-LS destination image is too small for the read region");
        }
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
}
