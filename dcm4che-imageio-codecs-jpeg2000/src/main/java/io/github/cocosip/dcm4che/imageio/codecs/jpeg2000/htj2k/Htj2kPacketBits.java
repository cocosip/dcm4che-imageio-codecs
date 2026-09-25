package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.htj2k;

import java.io.ByteArrayOutputStream;

import javax.imageio.IIOException;

/** Packet-header bits with JPEG 2000 byte stuffing. */
final class Htj2kPacketBits {
    private Htj2kPacketBits() {
    }

    static final class Writer {
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private int current;
        private int capacity = 8;
        private int used;

        void bit(int value) {
            if ((value & ~1) != 0) {
                throw new IllegalArgumentException("HT packet bit must be zero or one");
            }
            current |= value << (capacity - 1 - used++);
            if (used == capacity) {
                flush();
            }
        }

        void bits(int value, int count) {
            if (count < 0 || count > 32) {
                throw new IllegalArgumentException("HT packet bit count must be 0..32");
            }
            for (int i = count - 1; i >= 0; i--) {
                bit((value >>> i) & 1);
            }
        }

        byte[] finish() {
            if (used > 0 || capacity == 7) {
                flush();
            }
            return output.toByteArray();
        }

        private void flush() {
            output.write(current);
            capacity = current == 0xff ? 7 : 8;
            current = 0;
            used = 0;
        }
    }

    static final class Reader {
        private final byte[] data;
        private final int end;
        private final int start;
        private int position;
        private int current;
        private int capacity;
        private int used;
        private boolean previousFF;

        Reader(byte[] data, int offset, int length) {
            if (data == null || offset < 0 || length < 0
                    || offset > data.length - length) {
                throw new IllegalArgumentException("HT packet bounds are invalid");
            }
            this.data = data;
            this.start = offset;
            this.position = offset;
            this.end = offset + length;
        }

        int bit() throws IIOException {
            if (used == capacity) {
                if (position == end) {
                    throw new IIOException("Truncated HT packet header at byte "
                            + bytesRead());
                }
                current = data[position++] & 0xff;
                if (previousFF && (current & 0x80) != 0) {
                    throw new IIOException("Invalid HT packet stuffed bit at byte "
                            + (bytesRead() - 1));
                }
                capacity = previousFF ? 7 : 8;
                previousFF = current == 0xff;
                used = 0;
            }
            return (current >>> (capacity - 1 - used++)) & 1;
        }

        int bits(int count) throws IIOException {
            if (count < 0 || count > 32) {
                throw new IllegalArgumentException("HT packet bit count must be 0..32");
            }
            int value = 0;
            for (int i = 0; i < count; i++) {
                value = (value << 1) | bit();
            }
            return value;
        }

        void align() throws IIOException {
            if (previousFF && used == capacity) {
                bit();
            }
            used = capacity;
        }

        int bytesRead() {
            return position - start;
        }
    }
}
