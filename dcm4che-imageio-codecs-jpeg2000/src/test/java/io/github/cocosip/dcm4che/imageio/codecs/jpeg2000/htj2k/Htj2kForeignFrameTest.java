package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.htj2k;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
