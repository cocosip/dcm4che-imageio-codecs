package io.github.cocosip.dcm4che.imageio.codecs.jpeg.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import org.junit.jupiter.api.Test;

class ProgressiveJpegCodecTest {
    @Test
    void roundTripsProgressiveGrayscaleWithDcAndAcScans() throws Exception {
        int[] samples = new int[13 * 9];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = (i * 23 + 17) & 0xff;
        }

        JpegFrame decoded = ProgressiveJpegCodec.decode(
                ProgressiveJpegCodec.encode(JpegFrame.of(13, 9, 1, samples)));

        assertEquals(8, decoded.precision());
        assertTrue(maxDifference(samples, decoded.samples()) <= 70,
                () -> "max difference=" + maxDifference(samples, decoded.samples()));
    }

    @Test
    void roundTripsProgressiveRgbWithDcAndAcScans() throws Exception {
        int[] samples = new int[9 * 10 * 3];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = (i * 19 + i / 3 * 7) & 0xff;
        }

        byte[] encoded = ProgressiveJpegCodec.encode(JpegFrame.of(9, 10, 3, samples));
        JpegFrame decoded = ProgressiveJpegCodec.decode(encoded);

        assertTrue(hasMarker(encoded, 0xc2));
        assertTrue(maxDifference(samples, decoded.samples()) <= 70);
    }

    @Test
    void decodesJdkProgressiveJpeg() throws Exception {
        BufferedImage source = new BufferedImage(17, 13, BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                source.getRaster().setSample(x, y, 0, (x * 19 + y * 23) & 0xff);
            }
        }
        ImageWriter writer = ImageIO.getImageWritersByFormatName("JPEG").next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream output = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(output);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setProgressiveMode(ImageWriteParam.MODE_DEFAULT);
            writer.write(null, new IIOImage(source, null, null), param);
        } finally {
            writer.dispose();
        }

        JpegFrame decoded = ProgressiveJpegCodec.decode(bytes.toByteArray());
        assertEquals(17, decoded.width());
        assertEquals(13, decoded.height());
    }

    private static int maxDifference(int[] expected, int[] actual) {
        int max = 0;
        for (int i = 0; i < expected.length; i++) {
            max = Math.max(max, Math.abs(expected[i] - actual[i]));
        }
        return max;
    }

    private static boolean hasMarker(byte[] data, int marker) {
        for (int i = 0; i + 1 < data.length; i++) {
            if ((data[i] & 0xff) == 0xff && (data[i + 1] & 0xff) == marker) {
                return true;
            }
        }
        return false;
    }
}
