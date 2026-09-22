package io.github.cocosip.dcm4che.imageio.codecs.jpegls.internal;

import java.io.IOException;

import javax.imageio.stream.ImageInputStream;

final class JpegLsMarkerReader {
    private final ImageInputStream input;

    JpegLsMarkerReader(ImageInputStream input) {
        this.input = input;
    }

    JpegLsMarker next() throws IOException {
        int prefix = input.read();
        if (prefix != 0xff) {
            throw new JpegLsException("expected JPEG-LS marker prefix");
        }

        int code;
        do {
            code = input.read();
            if (code < 0) {
                throw new JpegLsException("truncated JPEG-LS marker");
            }
        } while (code == 0xff);

        if (code == 0) {
            throw new JpegLsException("unexpected stuffed byte outside JPEG-LS entropy data");
        }
        if (isStandalone(code)) {
            return new JpegLsMarker(code, new byte[0]);
        }

        int high = input.read();
        int low = input.read();
        if (high < 0 || low < 0) {
            throw new JpegLsException("truncated JPEG-LS marker length");
        }
        int length = (high << 8) | low;
        if (length < 2) {
            throw new JpegLsException("invalid JPEG-LS marker length: " + length);
        }

        byte[] payload = new byte[length - 2];
        input.readFully(payload);
        return new JpegLsMarker(code, payload);
    }

    private static boolean isStandalone(int code) {
        return code == JpegLsMarker.SOI
                || code == JpegLsMarker.EOI
                || code >= 0xd0 && code <= 0xd7;
    }
}
