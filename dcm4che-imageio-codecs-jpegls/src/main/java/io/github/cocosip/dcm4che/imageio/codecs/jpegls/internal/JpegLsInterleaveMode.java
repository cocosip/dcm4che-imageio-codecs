package io.github.cocosip.dcm4che.imageio.codecs.jpegls.internal;

enum JpegLsInterleaveMode {
    NONE(0),
    LINE(1),
    SAMPLE(2);

    private final int value;

    JpegLsInterleaveMode(int value) {
        this.value = value;
    }

    int value() {
        return value;
    }

    static JpegLsInterleaveMode fromValue(int value) throws JpegLsException {
        for (JpegLsInterleaveMode mode : values()) {
            if (mode.value == value) {
                return mode;
            }
        }
        throw new JpegLsException("invalid JPEG-LS interleave mode: " + value);
    }
}
