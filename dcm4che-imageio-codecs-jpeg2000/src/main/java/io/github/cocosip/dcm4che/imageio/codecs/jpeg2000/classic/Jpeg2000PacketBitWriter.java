package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.classic;

import java.io.ByteArrayOutputStream;

/** Bit writer for inline classic JPEG 2000 packet headers. */
final class Jpeg2000PacketBitWriter {
    private final ByteArrayOutputStream output = new ByteArrayOutputStream();
    private int buffer;
    private int bitCount;

    void writeBit(int bit) {
        if ((bit & ~1) != 0) {
            throw new IllegalArgumentException("JPEG 2000 packet-header bit must be 0 or 1");
        }
        if (bit != 0) {
            buffer |= 1 << (7 - bitCount);
        }
        bitCount++;
        if (bitCount == 8) {
            flushByte();
        }
    }

    void writeBits(int value, int count) {
        if (count <= 0 || count > 32) {
            throw new IllegalArgumentException("JPEG 2000 packet-header bit count must be 1..32");
        }
        for (int bit = count - 1; bit >= 0; bit--) {
            writeBit((value >>> bit) & 1);
        }
    }

    void align() {
        if (bitCount > 0) {
            flushByte();
        }
    }

    byte[] toByteArray() {
        return output.toByteArray();
    }

    private void flushByte() {
        int value = buffer & 0xff;
        output.write(value);
        buffer = 0;
        bitCount = value == 0xff ? 1 : 0;
    }
}
