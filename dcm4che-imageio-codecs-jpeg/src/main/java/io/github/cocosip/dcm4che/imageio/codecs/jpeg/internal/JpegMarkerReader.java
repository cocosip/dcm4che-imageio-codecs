package io.github.cocosip.dcm4che.imageio.codecs.jpeg.internal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.stream.ImageInputStream;

final class JpegMarkerReader {
    private final ImageInputStream input;

    JpegMarkerReader(ImageInputStream input) {
        this.input = input;
    }

    JpegMarker next() throws IOException {
        int prefix = input.read();
        if (prefix != 0xff) {
            throw new JpegException("expected JPEG marker prefix");
        }
        int code;
        do {
            code = input.read();
            if (code < 0) {
                throw new JpegException("truncated JPEG marker");
            }
        } while (code == 0xff);
        if (code == 0 || (code >= 0xd0 && code <= 0xd9)) {
            return new JpegMarker(code, new byte[0]);
        }
        int high = input.read();
        int low = input.read();
        if (high < 0 || low < 0) {
            throw new JpegException("truncated JPEG marker length");
        }
        int length = (high << 8) | low;
        if (length < 2) {
            throw new JpegException("invalid JPEG marker length: " + length);
        }
        byte[] payload = new byte[length - 2];
        input.readFully(payload);
        return new JpegMarker(code, payload);
    }

    static byte[] readEntropyBytes(ImageInputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        while (true) {
            int value = input.read();
            if (value < 0) {
                throw new JpegException("truncated JPEG scan");
            }
            if (value != 0xff) {
                output.write(value);
                continue;
            }
            int next = input.read();
            if (next < 0) {
                throw new JpegException("truncated JPEG scan marker");
            }
            if (next == 0) {
                output.write(0xff);
                output.write(0);
                continue;
            }
            input.seek(input.getStreamPosition() - 2);
            return output.toByteArray();
        }
    }
}
