package io.github.cocosip.dcm4che.imageio.codecs.jpegls.internal;

final class JpegLsPredictor {
    private JpegLsPredictor() {
    }

    static int predict(int left, int above, int aboveLeft) {
        if (aboveLeft >= Math.max(left, above)) {
            return Math.min(left, above);
        }
        if (aboveLeft <= Math.min(left, above)) {
            return Math.max(left, above);
        }
        return left + above - aboveLeft;
    }
}
