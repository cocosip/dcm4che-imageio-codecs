package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.classic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;

import javax.imageio.stream.MemoryCacheImageInputStream;

import org.junit.jupiter.api.Test;

import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Exception;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000CodestreamReader;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Limits;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000QuantizationStyle;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Raster;

class Jpeg2000LosslessCodecTest {
    @Test
    void decodesIndependentOpenJpegLosslessFixtureExactly() throws Exception {
        InputStream input = getClass().getResourceAsStream(
                "/jpeg2000/fo_dicom_codecs_synthetic8_lossless.j2k");
        assertNotNull(input);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int length;
        while ((length = input.read(buffer)) >= 0) {
            bytes.write(buffer, 0, length);
        }
        Jpeg2000Raster decoded = Jpeg2000LosslessCodec.decode(bytes.toByteArray());
        assertEquals(67, decoded.width());
        assertEquals(65, decoded.height());
        assertEquals(8, decoded.precision());
        assertEquals(false, decoded.signed());
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(decoded.toFrame(false));
        assertEquals("EBEFE59C1BFA9785D48ED1A1973978C6BE8CD6FC2B866BCF40218FFBC68C08D7",
                toHex(hash));
    }

    @Test
    void emitsDeterministicFiveLevelSingleLosslessLayer() throws Exception {
        int width = 71;
        int height = 69;
        int[] samples = new int[width * height];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = i * 29 & 255;
        }
        Jpeg2000Raster raster = Jpeg2000Raster.of(width, height, 8, 8, false,
                "MONOCHROME2", new int[][] {samples}, Jpeg2000Limits.defaults());
        byte[] encoded = Jpeg2000LosslessCodec.encode(raster);
        assertArrayEquals(encoded, Jpeg2000LosslessCodec.encode(raster));
        Jpeg2000ClassicCodestream codestream = new Jpeg2000ClassicCodestreamParser(
                new Jpeg2000CodestreamReader(new MemoryCacheImageInputStream(
                        new ByteArrayInputStream(encoded)), encoded.length,
                        Jpeg2000Limits.defaults()), Jpeg2000Limits.defaults()).parse();
        assertEquals(5, codestream.codingStyle().decompositionLevels());
        assertEquals(64, codestream.codingStyle().codeBlockWidth());
        assertEquals(64, codestream.codingStyle().codeBlockHeight());
        assertEquals(1, codestream.codingStyle().qualityLayers());
        assertEquals(Jpeg2000QuantizationStyle.NO_QUANTIZATION,
                codestream.quantization().style());
        assertEquals(14 + codestream.tileData().length,
                codestream.startOfTile().tilePartLength());
        assertEquals(encoded.length, codestream.logicalLength());
    }

    @Test
    void preservesSinglePixelAtPrecisionExtremes() throws Exception {
        for (int sample : new int[] {-32768, -1, 0, 32767}) {
            Jpeg2000Raster source = Jpeg2000Raster.of(1, 1, 16, 16, true,
                    "MONOCHROME2", new int[][] {{sample}}, Jpeg2000Limits.defaults());
            assertArrayEquals(new int[] {sample},
                    Jpeg2000LosslessCodec.decode(Jpeg2000LosslessCodec.encode(source))
                            .component(0));
        }
    }

    @Test
    void roundTripsLosslessPrecisionColorAndOddBlockBoundaries() throws Exception {
        for (int precision : new int[] {8, 12, 16}) {
            for (boolean signed : new boolean[] {false, true}) {
                for (int components : new int[] {1, 3}) {
                    int width = 67;
                    int height = 65;
                    int[][] samples = new int[components][width * height];
                    int mask = precision == 16 ? 0xffff : (1 << precision) - 1;
                    for (int c = 0; c < components; c++) {
                        for (int i = 0; i < samples[c].length; i++) {
                            int value = (i * 31 + (i / width) * 73 + c * 97) & mask;
                            samples[c][i] = signed && value >= (1 << (precision - 1))
                                    ? value - (1 << precision) : value;
                        }
                    }
                    Jpeg2000Raster raster = Jpeg2000Raster.of(width, height,
                            precision <= 8 ? 8 : 16, precision, signed,
                            components == 1 ? "MONOCHROME2" : "RGB", samples,
                            Jpeg2000Limits.defaults());
                    byte[] encoded = Jpeg2000LosslessCodec.encode(raster);
                    Jpeg2000ClassicCodestream codestream = new Jpeg2000ClassicCodestreamParser(
                            new Jpeg2000CodestreamReader(new MemoryCacheImageInputStream(
                                    new ByteArrayInputStream(encoded)), encoded.length,
                                    Jpeg2000Limits.defaults()), Jpeg2000Limits.defaults()).parse();
                    assertEquals(components == 3,
                            codestream.codingStyle().multipleComponentTransform());
                    Jpeg2000Raster decoded = Jpeg2000LosslessCodec.decode(encoded);
                    assertEquals(width, decoded.width());
                    assertEquals(height, decoded.height());
                    assertEquals(precision, decoded.precision());
                    assertEquals(signed, decoded.signed());
                    for (int c = 0; c < components; c++) {
                        assertArrayEquals(samples[c], decoded.component(c),
                                "precision=" + precision + " signed=" + signed + " component=" + c);
                    }
                }
            }
        }
    }

    @Test
    void acceptsOnlyOneDicomPaddingByteAndRejectsTruncation() throws Exception {
        int[] zeros = new int[9 * 7];
        Jpeg2000Raster raster = Jpeg2000Raster.of(9, 7, 8, 8, false,
                "MONOCHROME2", new int[][] {zeros}, Jpeg2000Limits.defaults());
        byte[] encoded = Jpeg2000LosslessCodec.encode(raster);
        assertArrayEquals(zeros, Jpeg2000LosslessCodec.decode(encoded).component(0));
        byte[] padded = Arrays.copyOf(encoded, encoded.length + 1);
        assertArrayEquals(zeros, Jpeg2000LosslessCodec.decode(padded).component(0));
        assertThrows(Jpeg2000Exception.class,
                () -> Jpeg2000LosslessCodec.decode(Arrays.copyOf(encoded, encoded.length - 2)));
        assertThrows(Jpeg2000Exception.class,
                () -> Jpeg2000LosslessCodec.decode(Arrays.copyOf(encoded, encoded.length + 2)));
    }

    private static String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte value : bytes) {
            result.append(String.format("%02X", value & 0xff));
        }
        return result.toString();
    }
}
