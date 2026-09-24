package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common;

import org.dcm4che3.imageio.codec.ImageDescriptor;

public final class Jpeg2000RasterNormalizer {
    private Jpeg2000RasterNormalizer() {
    }

    public static Jpeg2000Raster normalize(
            ImageDescriptor descriptor,
            byte[] frame,
            Jpeg2000Limits limits) throws Jpeg2000Exception {
        if (descriptor == null) {
            throw new NullPointerException("descriptor");
        }
        if (frame == null) {
            throw new NullPointerException("frame");
        }
        if (limits == null) {
            throw new NullPointerException("limits");
        }

        int width = descriptor.getColumns();
        int height = descriptor.getRows();
        int componentCount = descriptor.getSamples();
        int bitsAllocated = descriptor.getBitsAllocated();
        int precision = descriptor.getBitsStored();
        boolean signed = descriptor.isSigned();
        int planarConfiguration = descriptor.getPlanarConfiguration();
        String photometric = String.valueOf(descriptor.getPhotometricInterpretation());
        validateDescriptor(
                width, height, componentCount, bitsAllocated, precision,
                signed, planarConfiguration, photometric, descriptor.getEmbeddedOverlays());

        int normalizedBytes = limits.checkedSampleBufferBytes(
                width, height, componentCount, Integer.BYTES);
        int pixelCount = normalizedBytes / Integer.BYTES / componentCount;
        boolean packed422 = "YBR_FULL_422".equals(photometric);
        int expectedLength = packed422
                ? packed422Length(width, height, limits)
                : ordinaryFrameLength(pixelCount, componentCount, bitsAllocated, limits);
        if (frame.length != expectedLength) {
            throw new Jpeg2000Exception(
                    "JPEG 2000 uncompressed frame length " + frame.length
                            + " does not match expected length " + expectedLength);
        }

        int[][] components = new int[componentCount][pixelCount];
        if (packed422) {
            unpackYbrFull422(frame, width, height, components);
            photometric = "RGB";
        } else {
            unpackOrdinary(
                    frame, components, pixelCount, bitsAllocated, precision,
                    signed, planarConfiguration == 1);
            if ("YBR_FULL".equals(photometric)) {
                convertYbrFull(components);
                photometric = "RGB";
            }
        }
        return new Jpeg2000Raster(
                width, height, bitsAllocated, precision, signed, photometric, components);
    }

    private static void validateDescriptor(
            int width,
            int height,
            int components,
            int bitsAllocated,
            int precision,
            boolean signed,
            int planarConfiguration,
            String photometric,
            int[] embeddedOverlays) throws Jpeg2000Exception {
        if (width <= 0 || height <= 0) {
            throw new Jpeg2000Exception("JPEG 2000 raster dimensions must be positive");
        }
        if (components != 1 && components != 3) {
            throw new Jpeg2000Exception("JPEG 2000 raster requires one or three components");
        }
        if (bitsAllocated != 8 && bitsAllocated != 16) {
            throw new Jpeg2000Exception("JPEG 2000 raster requires 8 or 16 BitsAllocated");
        }
        if (precision < 1 || precision > bitsAllocated) {
            throw new Jpeg2000Exception("JPEG 2000 BitsStored must fit BitsAllocated");
        }
        if (components == 3 && planarConfiguration != 0 && planarConfiguration != 1) {
            throw new Jpeg2000Exception("JPEG 2000 PlanarConfiguration must be 0 or 1");
        }
        if (embeddedOverlays != null && embeddedOverlays.length != 0) {
            throw new Jpeg2000Exception("JPEG 2000 does not implicitly mask embedded overlays");
        }

        boolean monochrome = "MONOCHROME1".equals(photometric)
                || "MONOCHROME2".equals(photometric)
                || "PALETTE COLOR".equals(photometric);
        boolean rgb = "RGB".equals(photometric);
        boolean ybr = "YBR_FULL".equals(photometric) || "YBR_FULL_422".equals(photometric);
        if ((components == 1 && !monochrome) || (components == 3 && !rgb && !ybr)) {
            throw new Jpeg2000Exception(
                    "JPEG 2000 unsupported photometric interpretation " + photometric);
        }
        if (ybr && signed) {
            throw new Jpeg2000Exception("JPEG 2000 YBR raster samples must be unsigned");
        }
        if (ybr && (bitsAllocated != 8 || precision != 8)) {
            throw new Jpeg2000Exception("JPEG 2000 YBR encode normalization requires 8-bit samples");
        }
        if ("YBR_FULL_422".equals(photometric) && planarConfiguration != 0) {
            throw new Jpeg2000Exception("JPEG 2000 planar YBR_FULL_422 input is unsupported");
        }
    }

    private static int ordinaryFrameLength(
            int pixelCount,
            int components,
            int bitsAllocated,
            Jpeg2000Limits limits) throws Jpeg2000Exception {
        long length = (long) pixelCount * components * (bitsAllocated / 8);
        return limits.requireFrameLength(length);
    }

    private static int packed422Length(
            int width,
            int height,
            Jpeg2000Limits limits) throws Jpeg2000Exception {
        long groupsPerRow = ((long) width + 1L) / 2L;
        return limits.requireFrameLength(groupsPerRow * height * 4L);
    }

    private static void unpackOrdinary(
            byte[] frame,
            int[][] components,
            int pixelCount,
            int bitsAllocated,
            int precision,
            boolean signed,
            boolean planar) throws Jpeg2000Exception {
        int bytesPerSample = bitsAllocated / 8;
        for (int pixel = 0; pixel < pixelCount; pixel++) {
            for (int component = 0; component < components.length; component++) {
                int sampleIndex = planar
                        ? component * pixelCount + pixel
                        : pixel * components.length + component;
                int raw = readUnsigned(frame, sampleIndex * bytesPerSample, bytesPerSample);
                components[component][pixel] = normalizeSample(raw, bitsAllocated, precision, signed);
            }
        }
    }

    private static int normalizeSample(
            int raw,
            int bitsAllocated,
            int precision,
            boolean signed) throws Jpeg2000Exception {
        int allocationMask = bitsAllocated == 16 ? 0xffff : 0xff;
        int valueMask = precision == 16 ? 0xffff : (1 << precision) - 1;
        int unusedMask = allocationMask ^ valueMask;
        int unused = raw & unusedMask;
        int value = raw & valueMask;
        int signBit = 1 << (precision - 1);
        boolean legalSignExtension = signed
                && (value & signBit) != 0
                && unused == unusedMask;
        if (unused != 0 && !legalSignExtension) {
            throw new Jpeg2000Exception(
                    "JPEG 2000 sample contains data outside BitsStored; implicit overlay masking is disabled");
        }
        return signed && (value & signBit) != 0 ? value - (signBit << 1) : value;
    }

    private static int readUnsigned(byte[] frame, int offset, int bytesPerSample) {
        int value = frame[offset] & 0xff;
        return bytesPerSample == 1 ? value : value | (frame[offset + 1] & 0xff) << 8;
    }

    private static void unpackYbrFull422(
            byte[] frame,
            int width,
            int height,
            int[][] components) {
        int groupsPerRow = (width + 1) / 2;
        int pixel = 0;
        for (int y = 0; y < height; y++) {
            int rowOffset = y * groupsPerRow * 4;
            for (int group = 0; group < groupsPerRow; group++) {
                int input = rowOffset + group * 4;
                int cb = frame[input + 2] & 0xff;
                int cr = frame[input + 3] & 0xff;
                writeRgb(frame[input] & 0xff, cb, cr, components, pixel++);
                if (group * 2 + 1 < width) {
                    writeRgb(frame[input + 1] & 0xff, cb, cr, components, pixel++);
                }
            }
        }
    }

    private static void convertYbrFull(int[][] components) {
        for (int pixel = 0; pixel < components[0].length; pixel++) {
            writeRgb(
                    components[0][pixel], components[1][pixel], components[2][pixel],
                    components, pixel);
        }
    }

    private static void writeRgb(int y, int cb, int cr, int[][] components, int pixel) {
        double blueDifference = cb - 128.0;
        double redDifference = cr - 128.0;
        components[0][pixel] = clamp(roundAwayFromZero(y + 1.402 * redDifference));
        components[1][pixel] = clamp(roundAwayFromZero(
                y - 0.344136 * blueDifference - 0.714136 * redDifference));
        components[2][pixel] = clamp(roundAwayFromZero(y + 1.772 * blueDifference));
    }

    private static int roundAwayFromZero(double value) {
        return value < 0 ? (int) Math.ceil(value - 0.5) : (int) Math.floor(value + 0.5);
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
