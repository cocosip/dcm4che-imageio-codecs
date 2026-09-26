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
        int width = args.length > 4 ? Integer.parseInt(args[4]) : 128;
        int height = args.length > 5 ? Integer.parseInt(args[5]) : 128;
        int precision = args.length > 6 ? Integer.parseInt(args[6]) : 8;
        boolean signed = args.length > 7 && Boolean.parseBoolean(args[7]);
        int bytesPerSample = precision <= 8 ? 1 : 2;
        int components = args.length > 8 ? Integer.parseInt(args[8])
                : syntax.equals("201") ? 1 : 3;
        String uid = "1.2.840.10008.1.2.4." + syntax;
        if (args[3].equals("encode")) {
            int[][] samples = new int[components][width * height];
            byte[] pixels = new byte[width * height * components * bytesPerSample];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    for (int c = 0; c < components; c++) {
                        int value = (x * 17 + y * 31 + c * 79 + x * y * 3)
                                & ((1 << precision) - 1);
                        samples[c][y * width + x] = signed
                                && (value & (1 << (precision - 1))) != 0
                                ? value - (1 << precision) : value;
                        int index = ((y * width + x) * components + c) * bytesPerSample;
                        pixels[index] = (byte) value;
                        if (bytesPerSample == 2) {
                            pixels[index + 1] = (byte) (value >>> 8);
                        }
                    }
                }
            }
            Jpeg2000Raster raster = Jpeg2000Raster.of(width, height,
                    bytesPerSample * 8, precision, signed,
                    components == 1 ? "MONOCHROME2" : "RGB",
                    samples, Jpeg2000Limits.defaults());
            Files.write(raw, pixels);
            Files.write(codestream,
                    Htj2kFrameCodec.forTransferSyntax(uid).encode(raster));
        } else {
            byte[] decoded = Htj2kFrameCodec.forTransferSyntax(uid)
                    .decode(Files.readAllBytes(codestream)).toFrame(false);
            if (args[3].equals("dump")) {
                Files.write(raw, decoded);
                return;
            }
            byte[] expected = Files.readAllBytes(raw);
            if (expected.length != decoded.length) {
                throw new AssertionError("C# encoded frame length differs from Java decode");
            }
            int maximumError = 0;
            int firstDifference = -1;
            int firstExpected = 0;
            int firstActual = 0;
            int mask = (1 << precision) - 1;
            for (int i = 0; i < expected.length; i += bytesPerSample) {
                int first = expected[i] & 0xff;
                int second = decoded[i] & 0xff;
                if (bytesPerSample == 2) {
                    first |= (expected[i + 1] & 0xff) << 8;
                    second |= (decoded[i + 1] & 0xff) << 8;
                }
                first &= mask;
                second &= mask;
                if (signed) {
                    int signBit = 1 << (precision - 1);
                    first = (first ^ signBit) - signBit;
                    second = (second ^ signBit) - signBit;
                }
                int difference = Math.abs(first - second);
                if (difference != 0 && firstDifference < 0) {
                    firstDifference = i / bytesPerSample;
                    firstExpected = first;
                    firstActual = second;
                }
                maximumError = Math.max(maximumError, difference);
            }
            if (maximumError > (syntax.equals("203") ? 12 : 0)) {
                throw new AssertionError("C# encoded pixels differ from Java decode: "
                        + maximumError + " first sample " + firstDifference
                        + " expected " + firstExpected + " actual " + firstActual);
            }
            System.out.println("Java decoded C# codestream, max error " + maximumError);
        }
    }
}
