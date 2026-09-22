package io.github.cocosip.dcm4che.imageio.codecs.jpegls.internal;

final class JpegLsTraits {
    private final int maximumSampleValue;
    private final int nearLossless;
    private final int range;
    private final int quantizedBitsPerPixel;
    private final int limit;
    private final int resetThreshold;
    private final int threshold1;
    private final int threshold2;
    private final int threshold3;

    private JpegLsTraits(int maximumSampleValue, int nearLossless, int resetThreshold,
            int threshold1, int threshold2, int threshold3) throws JpegLsException {
        if (maximumSampleValue <= 0) {
            throw new JpegLsException("JPEG-LS maximum sample value must be positive");
        }
        if (nearLossless < 0 || nearLossless > maximumSampleValue / 2) {
            throw new JpegLsException("JPEG-LS near-lossless value is outside the sample range");
        }
        if (resetThreshold < 3 || resetThreshold > Math.max(255, maximumSampleValue)) {
            throw new JpegLsException("JPEG-LS reset threshold is outside the valid range");
        }
        if (threshold1 < nearLossless + 1 || threshold1 > threshold2
                || threshold2 > threshold3 || threshold3 > maximumSampleValue) {
            throw new JpegLsException("JPEG-LS thresholds are invalid");
        }
        this.maximumSampleValue = maximumSampleValue;
        this.nearLossless = nearLossless;
        this.range = nearLossless == 0
                ? maximumSampleValue + 1
                : (maximumSampleValue + 2 * nearLossless) / (2 * nearLossless + 1) + 1;
        this.quantizedBitsPerPixel = bitsLength(range);
        int bitsPerPixel = bitsLength(maximumSampleValue);
        this.limit = 2 * (bitsPerPixel + Math.max(8, bitsPerPixel));
        this.resetThreshold = resetThreshold;
        this.threshold1 = threshold1;
        this.threshold2 = threshold2;
        this.threshold3 = threshold3;
    }

    static JpegLsTraits create(int maximumSampleValue, int nearLossless, int resetThreshold,
            int threshold1, int threshold2, int threshold3) throws JpegLsException {
        return new JpegLsTraits(maximumSampleValue, nearLossless, resetThreshold,
                threshold1, threshold2, threshold3);
    }

    int range() {
        return range;
    }

    int quantizedBitsPerPixel() {
        return quantizedBitsPerPixel;
    }

    int limit() {
        return limit;
    }

    int initialContextValue() {
        return Math.max(2, (range + 32) / 64);
    }

    int nearLossless() { return nearLossless; }
    int resetThreshold() { return resetThreshold; }
    int maximumSampleValue() { return maximumSampleValue; }
    int threshold1() { return threshold1; }
    int threshold2() { return threshold2; }
    int threshold3() { return threshold3; }

    int quantizeGradient(int gradient) {
        if (gradient <= -threshold3) return -4;
        if (gradient <= -threshold2) return -3;
        if (gradient <= -threshold1) return -2;
        if (gradient < -nearLossless) return -1;
        if (gradient <= nearLossless) return 0;
        if (gradient < threshold1) return 1;
        if (gradient < threshold2) return 2;
        return gradient < threshold3 ? 3 : 4;
    }

    int computeErrorValue(int error) {
        return moduloRange(quantize(error));
    }

    int reconstruct(int prediction, int error) {
        int value = prediction + error * (2 * nearLossless + 1);
        if (nearLossless == 0 && ((maximumSampleValue + 1) & maximumSampleValue) == 0) {
            return value & maximumSampleValue;
        }
        if (value < -nearLossless) value += range * (2 * nearLossless + 1);
        else if (value > maximumSampleValue + nearLossless) value -= range * (2 * nearLossless + 1);
        return clamp(value, 0, maximumSampleValue);
    }

    boolean isNear(int left, int right) {
        return Math.abs(left - right) <= nearLossless;
    }

    int mapError(int error) {
        return error >= 0 ? error << 1 : (-error << 1) - 1;
    }

    int unmapError(int value) {
        return (value & 1) == 0 ? value >> 1 : -((value + 1) >> 1);
    }

    private int moduloRange(int value) {
        if (value < 0) value += range;
        if (value >= (range + 1) / 2) value -= range;
        return value;
    }

    private int quantize(int error) {
        if (nearLossless == 0) return error;
        return error > 0 ? (error + nearLossless) / (2 * nearLossless + 1)
                : -((nearLossless - error) / (2 * nearLossless + 1));
    }

    private static int bitsLength(int value) {
        int length = 0;
        for (int n = Math.max(1, value - 1); n > 0; n >>= 1) length++;
        return Math.max(1, length);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }
}
