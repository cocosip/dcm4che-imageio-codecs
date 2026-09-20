package io.github.cocosip.dcm4che.imageio.codecs.rle.internal;

import java.io.InputStream;
import java.util.Properties;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.imageio.codec.ImageDescriptor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RleInteropGoldenTest {

    @Test
    void matchesFoDicomCodecsMono16GoldenFrame() throws Exception {
        Properties golden = new Properties();
        try (InputStream input = getClass().getResourceAsStream(
                "/io/github/cocosip/dcm4che/imageio/codecs/rle/interop/"
                        + "fo-dicom-codecs-mono16.properties")) {
            assertNotNull(input);
            golden.load(input);
        }

        byte[] raw = hex(golden.getProperty("raw"));
        byte[] encoded = hex(golden.getProperty("rle"));
        RlePixelLayout layout = RlePixelLayout.from(descriptor(golden));

        assertArrayEquals(encoded, RleFrameCodec.encode(layout, raw));
        assertArrayEquals(raw, RleFrameCodec.decode(layout, encoded));
    }

    private static ImageDescriptor descriptor(Properties golden) {
        Attributes attributes = new Attributes();
        attributes.setInt(Tag.Rows, VR.US, integer(golden, "rows"));
        attributes.setInt(Tag.Columns, VR.US, integer(golden, "columns"));
        attributes.setInt(Tag.SamplesPerPixel, VR.US,
                integer(golden, "samplesPerPixel"));
        attributes.setInt(Tag.BitsAllocated, VR.US,
                integer(golden, "bitsAllocated"));
        attributes.setInt(Tag.BitsStored, VR.US,
                integer(golden, "bitsAllocated"));
        attributes.setInt(Tag.PixelRepresentation, VR.US,
                integer(golden, "pixelRepresentation"));
        attributes.setString(Tag.PhotometricInterpretation, VR.CS, "MONOCHROME2");
        return new ImageDescriptor(attributes);
    }

    private static int integer(Properties properties, String name) {
        return Integer.parseInt(properties.getProperty(name));
    }

    private static byte[] hex(String value) {
        if ((value.length() & 1) != 0) {
            throw new IllegalArgumentException("hex value has odd length");
        }
        byte[] bytes = new byte[value.length() / 2];
        for (int index = 0; index < bytes.length; index++) {
            int offset = index * 2;
            bytes[index] = (byte) Integer.parseInt(value.substring(offset, offset + 2), 16);
        }
        return bytes;
    }
}
