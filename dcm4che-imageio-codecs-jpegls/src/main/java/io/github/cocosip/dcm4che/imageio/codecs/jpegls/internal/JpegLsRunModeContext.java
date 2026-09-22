package io.github.cocosip.dcm4che.imageio.codecs.jpegls.internal;

final class JpegLsRunModeContext {
    private final int interruptionType;
    private int a;
    private int n = 1;
    private int negativeErrorCount;

    JpegLsRunModeContext(int interruptionType, int range) {
        this.interruptionType = interruptionType;
        this.a = Math.max(2, (range + 32) / 64);
    }

    int golombParameter() {
        int test = n;
        int target = a + (n >> 1) * interruptionType;
        int parameter = 0;
        while (test < target) {
            test <<= 1;
            parameter++;
        }
        return parameter;
    }

    boolean computeMap(int errorValue, int parameter) {
        if (parameter == 0 && errorValue > 0 && 2 * negativeErrorCount < n) return true;
        if (errorValue < 0 && 2 * negativeErrorCount >= n) return true;
        return errorValue < 0 && parameter != 0;
    }

    int computeErrorValue(int mappedValue, int parameter) {
        int mapBit = mappedValue & 1;
        int absoluteValue = (mappedValue + mapBit) / 2;
        boolean mapCondition = parameter != 0 || 2 * negativeErrorCount >= n;
        return mapCondition == (mapBit != 0) ? -absoluteValue : absoluteValue;
    }

    void update(int errorValue, int mappedErrorValue, int resetThreshold) {
        if (errorValue < 0) negativeErrorCount++;
        a += (mappedErrorValue + 1 - interruptionType) >> 1;
        if (n == resetThreshold) {
            a >>= 1;
            n >>= 1;
            negativeErrorCount >>= 1;
        }
        n++;
    }
}
