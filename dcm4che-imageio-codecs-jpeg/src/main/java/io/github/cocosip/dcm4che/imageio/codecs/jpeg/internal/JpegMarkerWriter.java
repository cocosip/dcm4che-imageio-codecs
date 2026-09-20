package io.github.cocosip.dcm4che.imageio.codecs.jpeg.internal;

import java.io.IOException;

import javax.imageio.stream.ImageOutputStream;

final class JpegMarkerWriter {
    private JpegMarkerWriter() {
    }

    static void write(ImageOutputStream output, int code, byte[] payload) throws IOException {
        if ((code & 0xff00) != 0 || code < 0xc0 || code > 0xfe || code == 0xff) {
            throw new IllegalArgumentException("invalid JPEG marker code: " + code);
        }
        if (payload.length > 0xfffd) {
            throw new IllegalArgumentException("JPEG marker payload is too large");
        }
        output.write(0xff);
        output.write(code);
        if (payload.length != 0 || (code != 0xd8 && code != 0xd9)) {
            int length = payload.length + 2;
            output.write(length >>> 8);
            output.write(length);
            output.write(payload);
        }
    }
}
