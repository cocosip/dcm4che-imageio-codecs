package io.github.cocosip.dcm4che.imageio.codecs.jpegls.internal;

final class JpegLsRegularContextState {
    private int a;
    private int b;
    private int c;
    private int n = 1;

    JpegLsRegularContextState(int initialA) {
        this.a = initialA;
    }

    int a() { return a; }
    int b() { return b; }
    int c() { return c; }
    int n() { return n; }

    int golombParameter() {
        int parameter = 0;
        while (parameter < 16 && (n << parameter) < a) parameter++;
        return parameter;
    }

    int errorCorrection(int parameter, int nearLossless) {
        return parameter != 0 || nearLossless != 0 ? 0 : (2 * b + n - 1 < 0 ? -1 : 0);
    }

    void update(int errorValue, int nearLossless, int resetThreshold) {
        a += Math.abs(errorValue);
        b += errorValue * (2 * nearLossless + 1);
        if (n == resetThreshold) {
            a >>= 1;
            b >>= 1;
            n >>= 1;
        }
        n++;
        if (b + n <= 0) {
            b += n;
            if (b <= -n) b = -n + 1;
            if (c > -128) c--;
        } else if (b > 0) {
            b -= n;
            if (b > 0) b = 0;
            if (c < 127) c++;
        }
    }
}
