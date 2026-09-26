import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.classic.Jpeg2000LosslessCodec;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Limits;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Raster;

/** Manual JPEG 2000 pixel exchange using this repository's Java codec. */
public final class Jpeg2000Interop {
    private Jpeg2000Interop() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 9 || args.length > 10) {
            throw new IllegalArgumentException("Usage: Jpeg2000Interop <90|91> "
                    + "<source.raw> <codestream.j2k> <encode|decode|verify> "
                    + "<width> <height> <precision> <signed> <components> [tolerance]");
        }
        boolean lossy = "91".equals(args[0]);
        if (!lossy && !"90".equals(args[0])) {
            throw new IllegalArgumentException("Transfer syntax must be 90 or 91");
        }
        Path raw = Paths.get(args[1]);
        Path codestream = Paths.get(args[2]);
        int width = Integer.parseInt(args[4]);
        int height = Integer.parseInt(args[5]);
        int precision = Integer.parseInt(args[6]);
        boolean signed = Boolean.parseBoolean(args[7]);
        int components = Integer.parseInt(args[8]);
        int bytesPerSample = precision <= 8 ? 1 : 2;
        int expectedLength = Jpeg2000Limits.defaults().checkedSampleBufferBytes(
                width, height, components, bytesPerSample);

        if ("encode".equals(args[3])) {
            byte[] source = Files.readAllBytes(raw);
            if (source.length != expectedLength) {
                throw new IllegalArgumentException("Source frame length differs from geometry");
            }
            int[][] samples = new int[components][width * height];
            int mask = (1 << precision) - 1;
            for (int pixel = 0; pixel < width * height; pixel++) {
                for (int component = 0; component < components; component++) {
                    int offset = (pixel * components + component) * bytesPerSample;
                    int value = source[offset] & 255;
                    if (bytesPerSample == 2) {
                        value |= (source[offset + 1] & 255) << 8;
                    }
                    value &= mask;
                    if (signed && (value & (1 << (precision - 1))) != 0) {
                        value -= 1 << precision;
                    }
                    samples[component][pixel] = value;
                }
            }
            Jpeg2000Raster raster = Jpeg2000Raster.of(width, height,
                    bytesPerSample * 8, precision, signed,
                    components == 1 ? "MONOCHROME2" : "RGB",
                    samples, Jpeg2000Limits.defaults());
            Files.write(codestream, Jpeg2000LosslessCodec.encode(raster, lossy));
            System.out.println("Java encoded " + Files.size(codestream) + " bytes");
            return;
        }

        Jpeg2000Raster decoded = Jpeg2000LosslessCodec.decode(
                Files.readAllBytes(codestream), !lossy);
        if (decoded.width() != width || decoded.height() != height
                || decoded.precision() != precision
                || decoded.componentCount() != components || decoded.signed() != signed) {
            throw new IllegalArgumentException("Codestream geometry or sample type differs");
        }
        byte[] pixels = decoded.toFrame(false);
        if ("decode".equals(args[3])) {
            Files.write(raw, pixels);
            System.out.println("Java decoded " + pixels.length + " bytes");
            return;
        }
        if (!"verify".equals(args[3])) {
            throw new IllegalArgumentException("Action must be encode, decode, or verify");
        }
        byte[] expected = Files.readAllBytes(raw);
        if (expected.length != pixels.length || pixels.length != expectedLength) {
            throw new IllegalArgumentException("Decoded frame length differs from source");
        }
        int tolerance = args.length == 10 ? Integer.parseInt(args[9]) : lossy ? 12 : 0;
        int mask = (1 << precision) - 1;
        int maximumError = 0;
        for (int offset = 0; offset < pixels.length; offset += bytesPerSample) {
            int first = expected[offset] & 255;
            int second = pixels[offset] & 255;
            if (bytesPerSample == 2) {
                first |= (expected[offset + 1] & 255) << 8;
                second |= (pixels[offset + 1] & 255) << 8;
            }
            first &= mask;
            second &= mask;
            if (signed) {
                int signBit = 1 << (precision - 1);
                first = (first ^ signBit) - signBit;
                second = (second ^ signBit) - signBit;
            }
            maximumError = Math.max(maximumError, Math.abs(first - second));
        }
        if (maximumError > tolerance) {
            throw new AssertionError("Maximum sample error " + maximumError
                    + " exceeds tolerance " + tolerance);
        }
        System.out.println("Java verified " + pixels.length
                + " bytes, maximum sample error " + maximumError);
    }
}
