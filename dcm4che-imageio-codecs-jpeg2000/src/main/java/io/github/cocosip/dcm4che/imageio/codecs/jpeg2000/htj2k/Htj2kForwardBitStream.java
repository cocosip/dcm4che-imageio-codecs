package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.htj2k;

import java.io.ByteArrayOutputStream;

import javax.imageio.IIOException;

/** Bounded forward HT bit streams, with the bit after 0xFF stuffed to zero. */
final class Htj2kForwardBitStream {
    private Htj2kForwardBitStream() {
    }

    static final class Reader {
        private final byte[] data;
        private final int end;
        private final boolean msbFirst;
        private final String context;
        private final boolean terminalFill;
        private int nextByte;
        private int current;
        private int capacity;
        private int used;
        private int bitPosition;
        private boolean previousWasFF;

        Reader(byte[] data, int offset, int length, boolean msbFirst, String context) {
            this(data, offset, length, msbFirst, context, false);
        }

        Reader(byte[] data, int offset, int length, boolean msbFirst,
                String context, boolean terminalFill) {
            if (data == null || context == null) {
                throw new NullPointerException(data == null ? "data" : "context");
            }
            if (offset < 0 || length < 0 || offset > data.length - length) {
                throw new IllegalArgumentException("HT bit view is outside its byte array");
            }
            this.data = data;
            this.nextByte = offset;
            this.end = offset + length;
            this.msbFirst = msbFirst;
            this.context = context;
            this.terminalFill = terminalFill;
        }

        int readBit() throws IIOException {
            if (used == capacity) {
                if (nextByte == end) {
                    if (!terminalFill) {
                        throw new IIOException("Truncated HTJ2K " + context
                                + " at bit position " + bitPosition);
                    }
                    current = previousWasFF ? 0x7f : 0xff;
                } else {
                    current = data[nextByte++] & 0xff;
                }
                if (previousWasFF && (current & 0x80) != 0) {
                    throw new IIOException("Invalid HTJ2K " + context
                            + " stuffed bit at bit position " + bitPosition);
                }
                capacity = previousWasFF ? 7 : 8;
                previousWasFF = current == 0xff;
                used = 0;
            }
            int bit = (current >>> (msbFirst ? capacity - 1 - used : used)) & 1;
            used++;
            bitPosition++;
            return bit;
        }

        int readBits(int count) throws IIOException {
            if (count < 0 || count > 32) {
                throw new IllegalArgumentException("HT read width must be 0..32");
            }
            int value = 0;
            for (int i = 0; i < count; i++) {
                int bit = readBit();
                value = msbFirst ? (value << 1) | bit : value | (bit << i);
            }
            return value;
        }

        int bitPosition() {
            return bitPosition;
        }

    }

    static final class Writer {
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private final int maxBytes;
        private final boolean msbFirst;
        private final String context;
        private int current;
        private int capacity = 8;
        private int used;
        private int bitPosition;

        Writer(int maxBytes, boolean msbFirst, String context) {
            if (maxBytes < 0 || context == null) {
                throw new IllegalArgumentException("HT bit writer limit or context is invalid");
            }
            this.maxBytes = maxBytes;
            this.msbFirst = msbFirst;
            this.context = context;
        }

        void writeBit(int bit) throws IIOException {
            if (bit != 0 && bit != 1) {
                throw new IllegalArgumentException("HT bit must be zero or one");
            }
            current |= bit << (msbFirst ? capacity - 1 - used : used);
            used++;
            bitPosition++;
            if (used == capacity) {
                emit();
            }
        }

        void writeBits(int value, int count) throws IIOException {
            if (count < 0 || count > 32) {
                throw new IllegalArgumentException("HT write width must be 0..32");
            }
            for (int i = 0; i < count; i++) {
                int shift = msbFirst ? count - 1 - i : i;
                writeBit((value >>> shift) & 1);
            }
        }

        byte[] finish(boolean padWithOnes) throws IIOException {
            if (used > 0) {
                while (used < capacity) {
                    current |= (padWithOnes ? 1 : 0)
                            << (msbFirst ? capacity - 1 - used : used);
                    used++;
                }
                emit();
            }
            return output.toByteArray();
        }

        int bitPosition() {
            return bitPosition;
        }

        byte[] emittedBytes() {
            return output.toByteArray();
        }

        int pendingByteAligned() {
            return msbFirst ? current : current << (capacity - used);
        }

        int remainingBits() {
            return capacity - used;
        }

        private void emit() throws IIOException {
            if (output.size() == maxBytes) {
                throw new IIOException("HTJ2K " + context + " exceeds " + maxBytes
                        + " bytes at bit position " + bitPosition);
            }
            output.write(current);
            capacity = current == 0xff ? 7 : 8;
            current = 0;
            used = 0;
        }
    }
}
