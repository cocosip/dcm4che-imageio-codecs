package io.github.cocosip.dcm4che.imageio.codecs.jpeg.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void roundTripsProgressiveRestartIntervals() throws Exception {
        int[] samples = new int[19 * 17];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = (i * 29 + 11) & 0xff;
        }

        byte[] encoded = ProgressiveJpegCodec.encodeWithRestart(JpegFrame.of(19, 17, 1, samples),
                JpegSampling.SF444, 0.8f, 2);
        JpegFrame decoded = ProgressiveJpegCodec.decode(encoded);

        assertTrue(hasMarker(encoded, 0xdd));
        assertTrue(hasRestartMarker(encoded));
        assertTrue(maxDifference(samples, decoded.samples()) <= 80);
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
    void writerOutputIsAcceptedByJdkProgressiveDecoderForRgb() throws Exception {
        int width = 17;
        int height = 13;
        BufferedImage source = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                source.setRGB(x, y, ((x * 17) & 0xff) << 16
                        | ((y * 23) & 0xff) << 8 | ((x * 7 + y * 11) & 0xff));
            }
        }

        int[] samples = new int[width * height * 3];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = source.getRGB(x, y);
                int offset = (y * width + x) * 3;
                samples[offset] = (rgb >>> 16) & 0xff;
                samples[offset + 1] = (rgb >>> 8) & 0xff;
                samples[offset + 2] = rgb & 0xff;
            }
        }
        byte[] encoded = ProgressiveJpegCodec.encode(JpegFrame.of(width, height, 3, samples));
        assertNotNull(ImageIO.read(new java.io.ByteArrayInputStream(encoded)));
    }

    @Test
    void roundTripsProgressiveFourTwoZeroSampling() throws Exception {
        int width = 13;
        int height = 11;
        int[] samples = new int[width * height * 3];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int offset = (y * width + x) * 3;
                samples[offset] = x * 5 + y * 3 + 40;
                samples[offset + 1] = 90;
                samples[offset + 2] = 160;
            }
        }

        byte[] encoded = ProgressiveJpegCodec.encode(JpegFrame.of(width, height, 3, samples),
                JpegSampling.SF420);
        JpegFrame decoded = ProgressiveJpegCodec.decode(encoded);

        assertTrue(hasMarker(encoded, 0xc2));
        assertTrue(maxDifference(samples, decoded.samples()) <= 75,
                () -> "max difference=" + maxDifference(samples, decoded.samples()));
    }

    @Test
    void roundTripsAcRefinementScanScript() throws Exception {
        int[] samples = new int[8 * 8];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = (i * 37 + 13) & 0xff;
        }
        JpegFrame source = JpegFrame.of(8, 8, 1, samples);
        JpegScanScript acFirstAtOne = new JpegScanScript(new int[] {0}, 1, 63, 0, 1);
        JpegScanScript acRefinement = JpegScanScript.acRefinement(0, 1, 63, 0);
        byte[] encoded = ProgressiveJpegCodec.encode(source, JpegSampling.SF444, 0.75f, 0,
                JpegScanScript.dcFirst(0), acFirstAtOne, acRefinement);

        JpegFrame decoded = ProgressiveJpegCodec.decode(encoded);
        assertTrue(maxDifference(samples, decoded.samples()) <= 80,
                () -> "max difference=" + maxDifference(samples, decoded.samples()));
    }

    @Test
    void roundTripsDcRefinementScanScript() throws Exception {
        int[] samples = new int[8 * 8];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = (i * 41 + 29) & 0xff;
        }
        JpegFrame source = JpegFrame.of(8, 8, 1, samples);
        JpegScanScript dcFirstAtOne = new JpegScanScript(new int[] {0}, 0, 0, 0, 1);
        JpegScanScript dcRefinement = JpegScanScript.dcRefinement(0, 0);
        byte[] encoded = ProgressiveJpegCodec.encode(source, JpegSampling.SF444, 0.75f, 0,
                dcFirstAtOne, dcRefinement,
                JpegScanScript.acFirst(0, 1, 63));

        JpegFrame decoded = ProgressiveJpegCodec.decode(encoded);
        assertTrue(maxDifference(samples, decoded.samples()) <= 80,
                () -> "max difference=" + maxDifference(samples, decoded.samples()));
    }

    @Test
    void rejectsIncompleteOrRepeatedProgressiveScanBands() {
        JpegFrame source = JpegFrame.of(8, 8, 1, new int[64]);
        JpegScanScript dc = JpegScanScript.dcFirst(0);
        assertThrows(JpegException.class, () -> ProgressiveJpegCodec.encode(source,
                JpegSampling.SF444, 0.75f, 0, dc, dc));
    }

    @Test
    void roundTripsAcRefinementWithLongZeroRun() throws Exception {
        QuantizationTable quant = QuantizationTable.of(JpegTables.standardLuminanceQuantization());
        double[] coefficients = new double[64];
        coefficients[JpegZigZag.ORDER[1]] = 2 * quant.get(1);
        coefficients[JpegZigZag.ORDER[63]] = quant.get(63);
        double[] pixels = JpegDct.inverse(coefficients);
        int[] samples = new int[64];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = Math.max(0, Math.min(255, (int) Math.round(pixels[i] + 128)));
        }
        JpegFrame source = JpegFrame.of(8, 8, 1, samples);
        byte[] encoded = ProgressiveJpegCodec.encode(source, JpegSampling.SF444, 0.75f, 0,
                JpegScanScript.dcFirst(0),
                new JpegScanScript(new int[] {0}, 1, 63, 0, 1),
                JpegScanScript.acRefinement(0, 1, 63, 0));

        JpegFrame decoded = ProgressiveJpegCodec.decode(encoded);
        assertTrue(maxDifference(samples, decoded.samples()) <= 80);
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

    private static boolean hasRestartMarker(byte[] data) {
        for (int i = 0; i + 1 < data.length; i++) {
            int value = data[i] & 0xff;
            int next = data[i + 1] & 0xff;
            if (value == 0xff && next >= 0xd0 && next <= 0xd7) {
                return true;
            }
        }
        return false;
    }
}
