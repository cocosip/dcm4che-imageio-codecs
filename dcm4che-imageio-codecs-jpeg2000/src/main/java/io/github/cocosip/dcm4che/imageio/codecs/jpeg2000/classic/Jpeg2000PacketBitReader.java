package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.classic;

import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Exception;

/** Bounded bit reader for inline classic JPEG 2000 packet headers. */
final class Jpeg2000PacketBitReader {
    private final byte[] data;
    private final int start;
    private final int end;
    private int position;
    private int buffer;
    private int bitCount;

    Jpeg2000PacketBitReader(byte[] data, int offset, int length) {
        if (data == null) {
            throw new NullPointerException("data");
        }
        if (offset < 0 || length < 0 || offset > data.length || length > data.length - offset) {
            throw new IllegalArgumentException("JPEG 2000 packet-header bounds are invalid");
        }
        this.data = data;
        this.start = offset;
        this.position = offset;
        this.end = offset + length;
    }

    int readBit() throws Jpeg2000Exception {
        if (bitCount == 0) {
            byteIn();
        }
        bitCount--;
        return (buffer >>> bitCount) & 1;
    }

    int readBits(int count) throws Jpeg2000Exception {
        if (count <= 0 || count > 32) {
            throw new IllegalArgumentException("JPEG 2000 packet-header bit count must be 1..32");
        }
        int value = 0;
        for (int bit = 0; bit < count; bit++) {
            value = (value << 1) | readBit();
        }
        return value;
    }

    void align() throws Jpeg2000Exception {
        if ((buffer & 0xff) == 0xff) {
            byteIn();
        }
        bitCount = 0;
    }

    int bytesRead() {
        return position - start;
    }

    private void byteIn() throws Jpeg2000Exception {
        if (position >= end) {
            throw new Jpeg2000Exception(
                    "Truncated JPEG 2000 packet header at byte " + bytesRead());
        }
        buffer = (buffer << 8) & 0xffff;
        bitCount = buffer == 0xff00 ? 7 : 8;
        buffer |= data[position++] & 0xff;
    }
}
