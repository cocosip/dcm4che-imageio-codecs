import java.nio.file.Files;
import java.nio.file.Path;

import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Limits;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Raster;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.htj2k.Htj2kFrameCodec;

public final class Htj2kExchange {
    public static void main(String[] args) throws Exception {
        String syntax = args[0];
        Path raw = Path.of(args[1]);
        Path codestream = Path.of(args[2]);
        int width = 128;
        int height = 128;
        int components = syntax.equals("201") ? 1 : 3;
        String uid = "1.2.840.10008.1.2.4." + syntax;
        if (args[3].equals("encode")) {
            int[][] samples = new int[components][width * height];
            byte[] pixels = new byte[width * height * components];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    for (int c = 0; c < components; c++) {
                        int value = (x * 2 + y * 3 + c * 19) & 255;
                        samples[c][y * width + x] = value;
                        pixels[(y * width + x) * components + c] = (byte) value;
                    }
                }
            }
            Jpeg2000Raster raster = Jpeg2000Raster.of(width, height, 8, 8,
                    false, components == 1 ? "MONOCHROME2" : "RGB",
                    samples, Jpeg2000Limits.defaults());
            Files.write(raw, pixels);
            Files.write(codestream,
                    Htj2kFrameCodec.forTransferSyntax(uid).encode(raster));
        } else {
            byte[] expected = Files.readAllBytes(raw);
            byte[] decoded = Htj2kFrameCodec.forTransferSyntax(uid)
                    .decode(Files.readAllBytes(codestream)).toFrame(false);
            if (expected.length != decoded.length) {
                throw new AssertionError("C# encoded frame length differs from Java decode");
            }
            int maximumError = 0;
            for (int i = 0; i < expected.length; i++) {
                maximumError = Math.max(maximumError,
                        Math.abs((expected[i] & 0xff) - (decoded[i] & 0xff)));
            }
            if (maximumError > (syntax.equals("203") ? 12 : 0)) {
                throw new AssertionError("C# encoded pixels differ from Java decode: "
                        + maximumError);
            }
            System.out.println("Java decoded C# codestream, max error " + maximumError);
        }
    }
}
