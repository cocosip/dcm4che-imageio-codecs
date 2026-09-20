package io.github.cocosip.dcm4che.imageio.codecs.core.image;

import java.awt.image.BandedSampleModel;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;

import javax.imageio.ImageTypeSpecifier;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.imageio.codec.ImageDescriptor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DicomImageTypesTest {

    @Test
    void createsEightBitMonochromeImage() {
        ImageTypeSpecifier type = DicomImageTypes.createType(descriptor(2, 3, 1, 8, 8, false, false));
        BufferedImage image = type.createBufferedImage(3, 2);

        assertEquals(3, image.getWidth());
        assertEquals(2, image.getHeight());
        assertEquals(1, image.getRaster().getNumBands());
        assertEquals(DataBuffer.TYPE_BYTE, image.getRaster().getDataBuffer().getDataType());
    }

    @Test
    void createsSignedAndUnsignedSixteenBitImages() {
        BufferedImage signed = DicomImageTypes.createImage(
                descriptor(2, 3, 1, 16, 12, true, false));
        BufferedImage unsigned = DicomImageTypes.createImage(
                descriptor(2, 3, 1, 16, 12, false, false));

        assertEquals(DataBuffer.TYPE_SHORT, signed.getRaster().getDataBuffer().getDataType());
        assertEquals(DataBuffer.TYPE_USHORT, unsigned.getRaster().getDataBuffer().getDataType());
    }

    @Test
    void createsBandedRgbImage() {
        BufferedImage image = DicomImageTypes.createImage(
                descriptor(2, 3, 3, 8, 8, false, true));

        assertEquals(3, image.getRaster().getNumBands());
        assertInstanceOf(BandedSampleModel.class, image.getSampleModel());
    }

    @Test
    void rejectsUnsupportedSampleCount() {
        assertThrows(IllegalArgumentException.class,
                () -> DicomImageTypes.createType(descriptor(1, 1, 2, 8, 8, false, false)));
    }

    @Test
    void rejectsUnsupportedAllocatedPrecision() {
        assertThrows(IllegalArgumentException.class,
                () -> DicomImageTypes.createType(descriptor(1, 1, 1, 32, 32, false, false)));
    }

    private static ImageDescriptor descriptor(int rows, int columns, int samples,
            int bitsAllocated, int bitsStored, boolean signed, boolean banded) {
        Attributes attributes = new Attributes();
        attributes.setInt(Tag.Rows, VR.US, rows);
        attributes.setInt(Tag.Columns, VR.US, columns);
        attributes.setInt(Tag.SamplesPerPixel, VR.US, samples);
        attributes.setInt(Tag.BitsAllocated, VR.US, bitsAllocated);
        attributes.setInt(Tag.BitsStored, VR.US, bitsStored);
        attributes.setInt(Tag.PixelRepresentation, VR.US, signed ? 1 : 0);
        attributes.setInt(Tag.PlanarConfiguration, VR.US, banded ? 1 : 0);
        attributes.setString(Tag.PhotometricInterpretation, VR.CS,
                samples == 1 ? "MONOCHROME2" : "RGB");
        return new ImageDescriptor(attributes);
    }
}
