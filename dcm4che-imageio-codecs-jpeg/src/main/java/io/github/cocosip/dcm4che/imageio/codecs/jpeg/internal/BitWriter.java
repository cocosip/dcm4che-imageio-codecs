package io.github.cocosip.dcm4che.imageio.codecs.jpeg.internal;

import java.io.IOException;

import javax.imageio.stream.ImageOutputStream;

final class BitWriter {
    private final ImageOutputStream output;
    private int bits;
    private int count;

    BitWriter(ImageOutputStream output) {
        this.output = output;
    }

    void writeBits(int value, int length) throws IOException {
        if (length < 0 || length > 24) {
            throw new IllegalArgumentException("bit length must be between 0 and 24");
        }
        if (length == 0) {
            return;
        }
        if ((value & ~((1 << length) - 1)) != 0) {
            throw new IllegalArgumentException("value does not fit in bit length");
        }
        bits = (bits << length) | value;
        count += length;
        while (count >= 8) {
            int shift = count - 8;
            writeByte((bits >>> shift) & 0xff);
            count = shift;
            bits = count == 0 ? 0 : bits & ((1 << count) - 1);
        }
    }

    void flush() throws IOException {
        if (count != 0) {
            writeByte((bits << (8 - count)) | ((1 << (8 - count)) - 1));
            bits = 0;
            count = 0;
        }
        output.flush();
    }

    private void writeByte(int value) throws IOException {
        output.write(value);
        if (value == 0xff) {
            output.write(0);
        }
    }
}
