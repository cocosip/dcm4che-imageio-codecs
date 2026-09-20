package io.github.cocosip.dcm4che.imageio.codecs.jpeg.internal;

import java.util.Arrays;

final class JpegMarker {
    private final int code;
    private final byte[] payload;

    JpegMarker(int code, byte[] payload) {
        this.code = code;
        this.payload = payload;
    }

    int code() {
        return code;
    }

    byte[] payload() {
        return Arrays.copyOf(payload, payload.length);
    }
}
