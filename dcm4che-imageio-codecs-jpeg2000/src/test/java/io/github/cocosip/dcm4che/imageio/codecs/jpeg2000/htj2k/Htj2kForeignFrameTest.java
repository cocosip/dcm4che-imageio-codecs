package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.htj2k;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Raster;

class Htj2kForeignFrameTest {
    @Test
    void decodesOpenJphGrayscalePixelsExactly() throws Exception {
        byte[] codestream = Files.readAllBytes(Paths.get(getClass().getResource(
                "/jpeg2000/htj2k_openjph_gray128.j2c").toURI()));
        Jpeg2000Raster decoded = Htj2kFrameCodec.forTransferSyntax(
                Htj2kFrameCodec.LOSSLESS_UID).decode(codestream);
        assertEquals(128, decoded.width());
        assertEquals(128, decoded.height());
        int[] pixels = decoded.component(0);
        for (int y = 0; y < 128; y++) {
            for (int x = 0; x < 128; x++) {
                assertEquals((x * 17 + y * 31 + x * y * 3) & 255,
                        pixels[y * 128 + x], "pixel " + x + "," + y);
            }
        }
    }

    @Test
    void decodesOpenJphSixteenBitGrayscaleExactly() throws Exception {
        byte[] codestream = Files.readAllBytes(Paths.get(getClass().getResource(
                "/jpeg2000/htj2k_openjph_gray16.j2c").toURI()));
        Jpeg2000Raster decoded = Htj2kFrameCodec.forTransferSyntax(
                Htj2kFrameCodec.LOSSLESS_UID).decode(codestream);
        assertEquals(16, decoded.precision());
        int[] pixels = decoded.component(0);
        for (int y = 0; y < 128; y++) {
            for (int x = 0; x < 128; x++) {
                assertEquals(x * 2 + y * 3, pixels[y * 128 + x]);
            }
        }
    }

    @Test
    void decodesOpenJphColorCprlPixelsExactly() throws Exception {
        byte[] codestream = Files.readAllBytes(Paths.get(getClass().getResource(
                "/jpeg2000/htj2k_openjph_rgb128_cprl.j2c").toURI()));
        Jpeg2000Raster decoded = Htj2kFrameCodec.forTransferSyntax(
                Htj2kFrameCodec.LOSSLESS_RPCL_UID).decode(codestream);
        assertEquals(128, decoded.width());
        assertEquals(128, decoded.height());
        for (int c = 0; c < 3; c++) {
            int[] pixels = decoded.component(c);
            for (int y = 0; y < 128; y++) {
                for (int x = 0; x < 128; x++) {
                    assertEquals((x * 2 + y * 3 + c * 19) & 255,
                            pixels[y * 128 + x], "component " + c + " pixel " + x + "," + y);
                }
            }
        }
    }

    @Test
    void decodesFourOpenJphTilesExactly() throws Exception {
        byte[] codestream = Files.readAllBytes(Paths.get(getClass().getResource(
                "/jpeg2000/htj2k_openjph_rgb128_four_tiles.j2c").toURI()));
        Jpeg2000Raster decoded = Htj2kFrameCodec.forTransferSyntax(
                Htj2kFrameCodec.LOSSLESS_UID).decode(codestream);
        for (int c = 0; c < 3; c++) {
            int[] pixels = decoded.component(c);
            for (int y = 0; y < 128; y++) {
                for (int x = 0; x < 128; x++) {
                    assertEquals((x * 2 + y * 3 + c * 19) & 255,
                            pixels[y * 128 + x], "tile pixel " + x + "," + y);
                }
            }
        }
    }

    @Test
    void decodesOpenJphLossyColorWithinTolerance() throws Exception {
        byte[] codestream = Files.readAllBytes(Paths.get(getClass().getResource(
                "/jpeg2000/htj2k_openjph_lossy_rgb128.j2c").toURI()));
        Jpeg2000Raster decoded = Htj2kFrameCodec.forTransferSyntax(
                Htj2kFrameCodec.LOSSY_UID).decode(codestream);
        assertEquals(128, decoded.width());
        assertEquals(128, decoded.height());
        for (int c = 0; c < 3; c++) {
            int[] pixels = decoded.component(c);
            for (int y = 0; y < 128; y++) {
                for (int x = 0; x < 128; x++) {
                    int expected = (x * 2 + y * 3 + c * 19) & 255;
                    assertTrue(Math.abs(expected - pixels[y * 128 + x]) <= 12,
                            "component " + c + " pixel " + x + "," + y);
                }
            }
        }
    }

    @Test
    void decodesThreePassRefinementLikeFoDicomCodecs() throws Exception {
        for (String variant : new String[] {"3pass", "spp", "dense"}) {
            byte[] codestream = Files.readAllBytes(Paths.get(getClass().getResource(
                    "/jpeg2000/htj2k_refinement_" + variant + ".j2c").toURI()));
            byte[] csharpPixels = Files.readAllBytes(Paths.get(getClass().getResource(
                    "/jpeg2000/htj2k_refinement_" + variant + ".raw").toURI()));
            Jpeg2000Raster decoded = Htj2kFrameCodec.forTransferSyntax(
                    Htj2kFrameCodec.LOSSLESS_UID).decode(codestream);
            assertEquals(64, decoded.width());
            assertEquals(64, decoded.height());
            assertArrayEquals(csharpPixels, decoded.toFrame(false), variant);
        }
    }

    @Test
    void cSharpDecodesJavaEncodedColorFixtures() throws Exception {
        assertJavaEncodedFixture("htj2k_java_rgb8_201",
                Htj2kFrameCodec.LOSSLESS_UID, 64, 64, 8, 0);
        assertJavaEncodedFixture("htj2k_java_rgb12_202",
                Htj2kFrameCodec.LOSSLESS_RPCL_UID, 129, 131, 12, 0);
        assertJavaEncodedFixture("htj2k_java_rgb12_203",
                Htj2kFrameCodec.LOSSY_UID, 129, 131, 12, 1);
    }

    private static void assertJavaEncodedFixture(String name, String uid,
            int width, int height, int precision, int tolerance) throws Exception {
        byte[] codestream = Files.readAllBytes(Paths.get(Htj2kForeignFrameTest.class
                .getResource("/jpeg2000/" + name + ".j2c").toURI()));
        byte[] cSharpPixels = Files.readAllBytes(Paths.get(Htj2kForeignFrameTest.class
                .getResource("/jpeg2000/" + name + ".raw").toURI()));
        Jpeg2000Raster decoded = Htj2kFrameCodec.forTransferSyntax(uid)
                .decode(codestream);
        assertEquals(width, decoded.width());
        assertEquals(height, decoded.height());
        assertEquals(width * height * 3 * (precision <= 8 ? 1 : 2), cSharpPixels.length);
        int[][] javaSamples = {
                decoded.component(0), decoded.component(1), decoded.component(2)
        };
        int sampleBytes = precision <= 8 ? 1 : 2;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = y * width + x;
                for (int component = 0; component < 3; component++) {
                    int offset = (pixel * 3 + component) * sampleBytes;
                    int foreign = cSharpPixels[offset] & 0xff;
                    if (sampleBytes == 2) {
                        foreign |= (cSharpPixels[offset + 1] & 0xff) << 8;
                    }
                    int expected = (x * 2 + y * 3 + component * 19)
                            & ((1 << precision) - 1);
                    assertTrue(Math.abs(expected - foreign) <= tolerance,
                            name + " C# sample " + pixel + "," + component);
                    assertTrue(Math.abs(javaSamples[component][pixel] - foreign) <= tolerance,
                            name + " Java/C# sample " + pixel + "," + component);
                }
            }
        }
    }
}
