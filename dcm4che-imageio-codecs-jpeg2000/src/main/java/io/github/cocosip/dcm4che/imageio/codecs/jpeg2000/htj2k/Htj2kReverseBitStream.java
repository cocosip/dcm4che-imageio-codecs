package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.htj2k;

import java.io.ByteArrayOutputStream;

import javax.imageio.IIOException;

/** Bounded, LSB-first reverse HT bit views used by VLC and refinement. */
final class Htj2kReverseBitStream {
    private Htj2kReverseBitStream() {
    }

    static final class Reader {
        private final byte[] data;
        private final int start;
        private final String context;
        private int nextByte;
        private int current;
        private int capacity;
        private int used;
        private int bitPosition;
        private boolean rightByteGreaterThan8f;

        Reader(byte[] data, int offset, int length, String context) {
            this(data, offset, length, context, false);
        }

        Reader(byte[] data, int offset, int length, String context,
                boolean cleanupLocator) {
            if (data == null || context == null) {
                throw new NullPointerException(data == null ? "data" : "context");
            }
            if (offset < 0 || length < 0 || offset > data.length - length) {
                throw new IllegalArgumentException("HT reverse bit view is outside its byte array");
            }
            this.data = data;
            this.start = offset;
            this.nextByte = offset + length - (cleanupLocator ? 3 : 1);
            this.context = context;
            if (cleanupLocator) {
                if (length < 2) {
                    throw new IllegalArgumentException("HT cleanup locator needs two bytes");
                }
                int locator = data[offset + length - 2] & 0xff;
                current = locator >>> 4;
                capacity = 4 - ((current & 7) == 7 ? 1 : 0);
                rightByteGreaterThan8f = (locator | 0xf) > 0x8f;
            }
        }

        int peekBits(int count) throws IIOException {
            int savedNext = nextByte;
            int savedCurrent = current;
            int savedCapacity = capacity;
            int savedUsed = used;
            int savedPosition = bitPosition;
            boolean savedStuffing = rightByteGreaterThan8f;
            int result = 0;
            for (int i = 0; i < count; i++) {
                try {
                    result |= readBit() << i;
                } catch (IIOException truncated) {
                    if (!truncated.getMessage().startsWith("Truncated")) {
                        throw truncated;
                    }
                    result |= ((1 << (count - i)) - 1) << i;
                    break;
                }
            }
            nextByte = savedNext;
            current = savedCurrent;
            capacity = savedCapacity;
            used = savedUsed;
            bitPosition = savedPosition;
            rightByteGreaterThan8f = savedStuffing;
            return result;
        }

        int readBit() throws IIOException {
            if (used == capacity) {
                if (nextByte < start) {
                    throw new IIOException("Truncated HTJ2K " + context
                            + " at reverse bit position " + bitPosition);
                }
                current = data[nextByte--] & 0xff;
                boolean stuffed = rightByteGreaterThan8f && (current & 0x7f) == 0x7f;
                if (stuffed && (current & 0x80) != 0) {
                    throw new IIOException("Invalid HTJ2K " + context
                            + " reverse stuffed bit at bit position " + bitPosition);
                }
                capacity = stuffed ? 7 : 8;
                rightByteGreaterThan8f = current > 0x8f;
                used = 0;
            }
            int bit = (current >>> used) & 1;
            used++;
            bitPosition++;
            return bit;
        }

        int readBits(int count) throws IIOException {
            if (count < 0 || count > 32) {
                throw new IllegalArgumentException("HT reverse read width must be 0..32");
            }
            int value = 0;
            for (int i = 0; i < count; i++) {
                value |= readBit() << i;
            }
            return value;
        }

        int bitPosition() {
            return bitPosition;
        }

        void initializeRefinement() {
            rightByteGreaterThan8f = true;
        }
    }

    static final class Writer {
        private final ByteArrayOutputStream reverseBytes = new ByteArrayOutputStream();
        private final int maxBytes;
        private final String context;
        private int current;
        private int used;
        private int bitPosition;
        private boolean rightByteGreaterThan8f;

        Writer(int maxBytes, String context) {
            this(maxBytes, context, false);
        }

        Writer(int maxBytes, String context, boolean cleanupLocator) {
            if (maxBytes < 0 || context == null) {
                throw new IllegalArgumentException("HT reverse bit writer limit or context is invalid");
            }
            this.maxBytes = maxBytes;
            this.context = context;
            if (cleanupLocator) {
                reverseBytes.write(0xff);
                current = 0x0f;
                used = 4;
                rightByteGreaterThan8f = true;
            }
        }

        void writeBits(int value, int count) throws IIOException {
            if (count < 0 || count > 32) {
                throw new IllegalArgumentException("HT reverse write width must be 0..32");
            }
            for (int i = 0; i < count; i++) {
                int capacity = rightByteGreaterThan8f ? 7 : 8;
                current |= ((value >>> i) & 1) << used;
                used++;
                bitPosition++;
                if (used == capacity) {
                    if (rightByteGreaterThan8f && current != 0x7f) {
                        rightByteGreaterThan8f = false;
                    } else {
                        emit();
                    }
                }
            }
        }

        byte[] finish() throws IIOException {
            if (used > 0) {
                emit();
            }
            return emittedBytes();
        }

        byte[] emittedBytes() {
            byte[] result = reverseBytes.toByteArray();
            for (int left = 0, right = result.length - 1; left < right; left++, right--) {
                byte swap = result[left];
                result[left] = result[right];
                result[right] = swap;
            }
            return result;
        }

        int emittedCount() {
            return reverseBytes.size();
        }

        int pendingByte() {
            return current;
        }

        int usedBits() {
            return used;
        }

        int bitPosition() {
            return bitPosition;
        }

        private void emit() throws IIOException {
            if (reverseBytes.size() == maxBytes) {
                throw new IIOException("HTJ2K " + context + " exceeds " + maxBytes
                        + " bytes at reverse bit position " + bitPosition);
            }
            reverseBytes.write(current);
            rightByteGreaterThan8f = current > 0x8f;
            current = 0;
            used = 0;
        }
    }
}
