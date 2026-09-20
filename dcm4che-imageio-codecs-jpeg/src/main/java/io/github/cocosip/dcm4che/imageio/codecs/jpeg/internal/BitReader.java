package io.github.cocosip.dcm4che.imageio.codecs.jpeg.internal;

import java.io.IOException;

import javax.imageio.stream.ImageInputStream;

final class BitReader {
    private final ImageInputStream input;
    private int bits;
    private int count;

    BitReader(ImageInputStream input) {
        this.input = input;
    }

    int readBits(int length) throws IOException {
        if (length < 0 || length > 24) {
            throw new IllegalArgumentException("bit length must be between 0 and 24");
        }
        int value = 0;
        for (int i = 0; i < length; i++) {
            if (count == 0) {
                int next = input.read();
                if (next < 0) {
                    throw new JpegException("truncated JPEG entropy data");
                }
                if (next == 0xff) {
                    int stuffed = input.read();
                    if (stuffed != 0) {
                        throw new JpegException("unexpected marker in JPEG entropy data");
                    }
                }
                bits = next;
                count = 8;
            }
            value = (value << 1) | ((bits >>> (count - 1)) & 1);
            count--;
        }
        return value;
    }
}
