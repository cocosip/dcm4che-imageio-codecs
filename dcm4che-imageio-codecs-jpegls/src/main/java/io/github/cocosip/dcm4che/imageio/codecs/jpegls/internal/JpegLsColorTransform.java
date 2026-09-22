package io.github.cocosip.dcm4che.imageio.codecs.jpegls.internal;

final class JpegLsColorTransform {
    private JpegLsColorTransform() {
    }

    static void validate(int transform, int componentCount, int precision)
            throws JpegLsException {
        if (transform < 0 || transform > 3) {
            throw new JpegLsException("unsupported JPEG-LS mrfx transform: " + transform);
        }
        if (transform == 0) return;
        if (componentCount != 3) {
            throw new JpegLsException("JPEG-LS HP transform requires three components");
        }
        if (precision != 8 && precision != 16) {
            throw new JpegLsException("JPEG-LS HP transform requires 8 or 16-bit precision");
        }
    }

    static void applyInverse(int[] samples, int transform, int precision)
            throws JpegLsException {
        validate(transform, samples.length % 3 == 0 ? 3 : 0, precision);
        if (transform == 0) return;
        int range = precision == 8 ? 256 : 65536;
        int mask = range - 1;
        int half = range / 2;
        for (int index = 0; index < samples.length; index += 3) {
            int first = samples[index];
            int second = samples[index + 1];
            int third = samples[index + 2];
            if (transform == 1) {
                samples[index] = first + second - half & mask;
                samples[index + 1] = second;
                samples[index + 2] = third + second - half & mask;
            } else if (transform == 2) {
                int red = first + second - half & mask;
                samples[index] = red;
                samples[index + 1] = second;
                samples[index + 2] = third + ((red + second) >> 1) - half & mask;
            } else {
                int green = first - ((third + second) >> 2) + range / 4 & mask;
                samples[index] = third + green - half & mask;
                samples[index + 1] = green;
                samples[index + 2] = second + green - half & mask;
            }
        }
    }
}
