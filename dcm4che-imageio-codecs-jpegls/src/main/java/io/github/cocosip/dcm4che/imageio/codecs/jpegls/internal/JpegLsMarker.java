package io.github.cocosip.dcm4che.imageio.codecs.jpegls.internal;

import java.util.Arrays;

final class JpegLsMarker {
    static final int SOF55 = 0xf7;
    static final int LSE = 0xf8;
    static final int SOI = 0xd8;
    static final int EOI = 0xd9;
    static final int SOS = 0xda;
    static final int DNL = 0xdc;
    static final int DRI = 0xdd;
    static final int COM = 0xfe;

    private final int code;
    private final byte[] payload;

    JpegLsMarker(int code, byte[] payload) {
        this.code = code;
        this.payload = Arrays.copyOf(payload, payload.length);
    }

    int code() {
        return code;
    }

    byte[] payload() {
        return Arrays.copyOf(payload, payload.length);
    }

    boolean isApplicationSegment() {
        return code >= 0xe0 && code <= 0xef;
    }

    boolean isRestartMarker() {
        return code >= 0xd0 && code <= 0xd7;
    }
}
