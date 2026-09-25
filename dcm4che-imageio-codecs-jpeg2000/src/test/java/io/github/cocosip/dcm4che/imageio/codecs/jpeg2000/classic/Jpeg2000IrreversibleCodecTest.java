package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.classic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

import javax.imageio.stream.MemoryCacheImageInputStream;

import org.junit.jupiter.api.Test;

import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000CodestreamReader;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Limits;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000ProgressionOrder;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000QuantizationStyle;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Raster;

class Jpeg2000IrreversibleCodecTest {
    @Test
    void decodesReproducibleForeignLossyCodestream() throws Exception {
        byte[] codestream = readAll(getClass().getResourceAsStream(
                "/jpeg2000/fo_dicom_codecs_synthetic8_lossy.j2k"));
        byte[] reference = readAll(getClass().getResourceAsStream(
                "/jpeg2000/fo_dicom_codecs_synthetic8_lossy_reference.raw"));
        Jpeg2000Raster decoded = Jpeg2000LosslessCodec.decode(codestream);
        assertEquals(67, decoded.width());
        assertEquals(65, decoded.height());
        assertEquals(8, decoded.precision());
        byte[] actual = decoded.toFrame(false);
        assertEquals(reference.length, actual.length);
        int maximumError = 0;
        for (int i = 0; i < actual.length; i++) {
            maximumError = Math.max(maximumError,
                    Math.abs((actual[i] & 255) - (reference[i] & 255)));
        }
        assertTrue(maximumError <= 6, "maximum error=" + maximumError);
    }

    @Test
    void decodesIndependentOpenJpegLossyCodestream() throws Exception {
        InputStream input = getClass().getResourceAsStream(
                "/jpeg2000/fo_dicom_codecs_unit8_lossy.j2k");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            output.write(buffer, 0, count);
        }
        Jpeg2000Raster decoded = Jpeg2000LosslessCodec.decode(output.toByteArray());
        assertEquals(512, decoded.width());
        assertEquals(512, decoded.height());
        assertEquals(3, decoded.componentCount());
        byte[] reference = readAll(new GZIPInputStream(getClass().getResourceAsStream(
                "/jpeg2000/fo_dicom_codecs_unit8_lossy_native.raw.gz")));
        byte[] actual = decoded.toFrame(false);
        assertEquals(reference.length, actual.length);
        int maximumError = 0;
        long absoluteError = 0;
        for (int i = 0; i < actual.length; i++) {
            int error = Math.abs((actual[i] & 0xff) - (reference[i] & 0xff));
            maximumError = Math.max(maximumError, error);
            absoluteError += error;
        }
        assertTrue(maximumError <= 6, "maximum error=" + maximumError);
        assertTrue((double) absoluteError / actual.length < 1.0);
    }

    @Test
    void writesMultipleRateLayersWithCompleteFinalReversibleLayer() throws Exception {
        int[] samples = new int[67 * 65];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = (i * 23 + i / 67 * 41) & 255;
        }
        Jpeg2000Raster source = Jpeg2000Raster.of(67, 65, 8, 8, false,
                "MONOCHROME2", new int[][] {samples}, Jpeg2000Limits.defaults());
        byte[] encoded = Jpeg2000LosslessCodec.encode(source, false,
                new double[] {128, 16, 0}, Jpeg2000ProgressionOrder.RLCP);
        Jpeg2000ClassicCodestream stream = new Jpeg2000ClassicCodestreamParser(
                new Jpeg2000CodestreamReader(new MemoryCacheImageInputStream(
                        new ByteArrayInputStream(encoded)), encoded.length,
                        Jpeg2000Limits.defaults()), Jpeg2000Limits.defaults()).parse();
        assertEquals(3, stream.codingStyle().qualityLayers());
        assertEquals(Jpeg2000ProgressionOrder.RLCP, stream.codingStyle().progressionOrder());
        assertArrayEquals(samples, Jpeg2000LosslessCodec.decode(encoded).component(0));
    }

    @Test
    void encodesDeterministicNineSevenGrayscaleAndRgb() throws Exception {
        for (int components : new int[] {1, 3}) {
            int width = 67;
            int height = 65;
            int[][] samples = new int[components][width * height];
            for (int c = 0; c < components; c++) {
                for (int i = 0; i < samples[c].length; i++) {
                    samples[c][i] = (i * 17 + i / width * 29 + c * 61) & 255;
                }
            }
            Jpeg2000Raster source = Jpeg2000Raster.of(width, height, 8, 8, false,
                    components == 1 ? "MONOCHROME2" : "RGB", samples,
                    Jpeg2000Limits.defaults());
            byte[] encoded = Jpeg2000LosslessCodec.encode(source, true);
            assertArrayEquals(encoded, Jpeg2000LosslessCodec.encode(source, true));
            Jpeg2000ClassicCodestream stream = new Jpeg2000ClassicCodestreamParser(
                    new Jpeg2000CodestreamReader(new MemoryCacheImageInputStream(
                            new ByteArrayInputStream(encoded)), encoded.length,
                            Jpeg2000Limits.defaults()), Jpeg2000Limits.defaults()).parse();
            assertEquals(0, stream.codingStyle().transformation());
            assertEquals(components == 3, stream.codingStyle().multipleComponentTransform());
            assertEquals(Jpeg2000QuantizationStyle.SCALAR_EXPOUNDED,
                    stream.quantization().style());
            Jpeg2000Raster decoded = Jpeg2000LosslessCodec.decode(encoded);
            for (int c = 0; c < components; c++) {
                int maximumError = 0;
                int[] actual = decoded.component(c);
                for (int i = 0; i < actual.length; i++) {
                    maximumError = Math.max(maximumError, Math.abs(actual[i] - samples[c][i]));
                }
                assertTrue(maximumError <= 20, "component=" + c + " error=" + maximumError);
            }
        }
    }

    private static byte[] readAll(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }
}
