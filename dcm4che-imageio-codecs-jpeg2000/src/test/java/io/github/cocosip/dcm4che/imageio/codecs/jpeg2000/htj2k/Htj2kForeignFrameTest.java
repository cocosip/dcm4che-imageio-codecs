package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.htj2k;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
