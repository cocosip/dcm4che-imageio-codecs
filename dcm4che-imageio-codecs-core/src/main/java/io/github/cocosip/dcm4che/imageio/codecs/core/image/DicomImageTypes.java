package io.github.cocosip.dcm4che.imageio.codecs.core.image;

import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.image.BandedSampleModel;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.PixelInterleavedSampleModel;
import java.awt.image.SampleModel;
import java.util.Arrays;

import javax.imageio.ImageTypeSpecifier;

import org.dcm4che3.imageio.codec.ImageDescriptor;

/** Creates ordinary Java image types from codec-neutral DICOM pixel metadata. */
public final class DicomImageTypes {

    private DicomImageTypes() {
    }

    public static ImageTypeSpecifier createType(ImageDescriptor descriptor) {
        if (descriptor == null) {
            throw new NullPointerException("descriptor");
        }

        int rows = descriptor.getRows();
        int columns = descriptor.getColumns();
        int samples = descriptor.getSamples();
        int bitsAllocated = descriptor.getBitsAllocated();
        int bitsStored = descriptor.getBitsStored();
        validate(rows, columns, samples, bitsAllocated, bitsStored);

        int dataType = bitsAllocated == 8
                ? DataBuffer.TYPE_BYTE
                : descriptor.isSigned() ? DataBuffer.TYPE_SHORT : DataBuffer.TYPE_USHORT;
        int[] componentBits = new int[samples];
        Arrays.fill(componentBits, bitsStored);
        ColorSpace colorSpace = ColorSpace.getInstance(
                samples == 1 ? ColorSpace.CS_GRAY : ColorSpace.CS_sRGB);
        ColorModel colorModel = new ComponentColorModel(
                colorSpace,
                componentBits,
                false,
                false,
                Transparency.OPAQUE,
                dataType);

        SampleModel sampleModel;
        if (samples > 1 && descriptor.isBanded()) {
            sampleModel = new BandedSampleModel(dataType, columns, rows, samples);
        } else {
            int[] bandOffsets = new int[samples];
            for (int i = 0; i < samples; i++) {
                bandOffsets[i] = i;
            }
            int scanlineStride = checkedInt((long) columns * samples, "scanline stride");
            sampleModel = new PixelInterleavedSampleModel(
                    dataType, columns, rows, samples, scanlineStride, bandOffsets);
        }
        return new ImageTypeSpecifier(colorModel, sampleModel);
    }

    public static BufferedImage createImage(ImageDescriptor descriptor) {
        return createType(descriptor).createBufferedImage(
                descriptor.getColumns(), descriptor.getRows());
    }

    private static void validate(int rows, int columns, int samples,
            int bitsAllocated, int bitsStored) {
        if (rows <= 0 || columns <= 0) {
            throw new IllegalArgumentException("Image dimensions must be positive");
        }
        if (samples != 1 && samples != 3) {
            throw new IllegalArgumentException("Unsupported SamplesPerPixel: " + samples);
        }
        if (bitsAllocated != 8 && bitsAllocated != 16) {
            throw new IllegalArgumentException("Unsupported BitsAllocated: " + bitsAllocated);
        }
        if (bitsStored <= 0 || bitsStored > bitsAllocated) {
            throw new IllegalArgumentException("Invalid BitsStored: " + bitsStored);
        }
        checkedInt((long) rows * columns * samples, "image sample count");
    }

    private static int checkedInt(long value, String name) {
        if (value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(name + " exceeds Java image limits: " + value);
        }
        return (int) value;
    }
}
