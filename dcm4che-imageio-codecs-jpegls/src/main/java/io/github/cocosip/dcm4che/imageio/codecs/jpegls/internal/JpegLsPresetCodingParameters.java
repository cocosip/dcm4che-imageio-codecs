package io.github.cocosip.dcm4che.imageio.codecs.jpegls.internal;

final class JpegLsPresetCodingParameters {
    private static final int DEFAULT_RESET = 64;

    private final int maximumSampleValue;
    private final int threshold1;
    private final int threshold2;
    private final int threshold3;
    private final int resetValue;

    private JpegLsPresetCodingParameters(int maximumSampleValue, int threshold1,
            int threshold2, int threshold3, int resetValue) {
        this.maximumSampleValue = maximumSampleValue;
        this.threshold1 = threshold1;
        this.threshold2 = threshold2;
        this.threshold3 = threshold3;
        this.resetValue = resetValue;
    }

    static JpegLsPresetCodingParameters parse(byte[] payload) throws JpegLsException {
        if (payload.length != 11 || unsigned(payload[0]) != 1) {
            throw new JpegLsException("invalid JPEG-LS preset coding parameter segment");
        }
        return new JpegLsPresetCodingParameters(
                unsignedShort(payload, 1),
                unsignedShort(payload, 3),
                unsignedShort(payload, 5),
                unsignedShort(payload, 7),
                unsignedShort(payload, 9));
    }

    static JpegLsPresetCodingParameters defaults(int precision, int nearLossless)
            throws JpegLsException {
        return new JpegLsPresetCodingParameters(0, 0, 0, 0, 0).resolve(precision, nearLossless);
    }

    JpegLsPresetCodingParameters resolve(int precision, int nearLossless) throws JpegLsException {
        if (precision < 2 || precision > 16) {
            throw new JpegLsException("invalid JPEG-LS sample precision: " + precision);
        }
        int precisionMaximum = (1 << precision) - 1;
        int maximum = maximumSampleValue == 0 ? precisionMaximum : maximumSampleValue;
        if (maximum < 1 || maximum > precisionMaximum) {
            throw new JpegLsException("invalid JPEG-LS maximum sample value: " + maximum);
        }
        if (nearLossless < 0 || nearLossless > Math.min(255, maximum / 2)) {
            throw new JpegLsException("invalid JPEG-LS near-lossless value: " + nearLossless);
        }

        int[] defaults = defaultThresholds(maximum, nearLossless);
        int first = threshold1 == 0 ? defaults[0] : threshold1;
        int second = threshold2 == 0 ? defaults[1] : threshold2;
        int third = threshold3 == 0 ? defaults[2] : threshold3;
        int reset = resetValue == 0 ? DEFAULT_RESET : resetValue;
        if (first < nearLossless + 1 || first > second || second > third || third > maximum) {
            throw new JpegLsException("invalid JPEG-LS preset threshold ordering");
        }
        if (reset < 3 || reset > Math.max(255, maximum)) {
            throw new JpegLsException("invalid JPEG-LS reset value: " + reset);
        }
        return new JpegLsPresetCodingParameters(maximum, first, second, third, reset);
    }

    int maximumSampleValue() {
        return maximumSampleValue;
    }

    int threshold1() {
        return threshold1;
    }

    int threshold2() {
        return threshold2;
    }

    int threshold3() {
        return threshold3;
    }

    int resetValue() {
        return resetValue;
    }

    private static int[] defaultThresholds(int maximum, int nearLossless) {
        int first;
        int second;
        int third;
        if (maximum >= 128) {
            int factor = (Math.min(maximum, 4095) + 128) / 256;
            first = factor + 2 + 3 * nearLossless;
            second = 4 * factor + 3 + 5 * nearLossless;
            third = 17 * factor + 4 + 7 * nearLossless;
        } else {
            int factor = 256 / (maximum + 1);
            first = Math.max(2, 3 / factor + 3 * nearLossless);
            second = Math.max(3, 7 / factor + 5 * nearLossless);
            third = Math.max(4, 21 / factor + 7 * nearLossless);
        }
        first = clamp(first, nearLossless + 1, maximum);
        second = clamp(second, first, maximum);
        third = clamp(third, second, maximum);
        return new int[] {first, second, third};
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }

    private static int unsigned(byte value) {
        return value & 0xff;
    }

    private static int unsignedShort(byte[] values, int offset) {
        return unsigned(values[offset]) << 8 | unsigned(values[offset + 1]);
    }
}
