package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.htj2k;

import javax.imageio.IIOException;

/** UVLC coding for a pair of HT quads, in reverse-stream bit order. */
final class Htj2kUvlc {
    private Htj2kUvlc() {
    }

    static boolean initialMelEvent(int u0, int u1) {
        return u0 > 0 && u1 > 0 && Math.min(u0, u1) > 2;
    }

    static void encodePair(boolean firstRow, int u0, int u1,
            Htj2kReverseBitStream.Writer output) throws IIOException {
        requireValue(u0);
        requireValue(u1);
        if (firstRow && u0 > 2 && u1 > 2) {
            writePair(prefix(u0 - 2), prefix(u1 - 2), output);
        } else if (firstRow && u0 > 2 && u1 > 0) {
            Prefix first = prefix(u0);
            output.writeBits(first.bits, first.length);
            output.writeBits(u1 - 1, 1);
            output.writeBits(first.suffix, first.suffixLength);
        } else {
            writePair(prefix(u0), prefix(u1), output);
        }
    }

    static int[] decodePair(boolean firstRow, boolean u0Present, boolean u1Present,
            boolean melEvent, Htj2kReverseBitStream.Reader input) throws IIOException {
        if (firstRow && u0Present && u1Present && melEvent) {
            Prefix first = readPrefix(input);
            Prefix second = readPrefix(input);
            return new int[] {readSuffix(first, input) + 2,
                    readSuffix(second, input) + 2};
        }
        if (firstRow && u0Present && u1Present) {
            Prefix first = readPrefix(input);
            if (first.length == 3) {
                int second = input.readBits(1) + 1;
                return new int[] {readSuffix(first, input), second};
            }
            Prefix second = readPrefix(input);
            return new int[] {readSuffix(first, input), readSuffix(second, input)};
        }
        Prefix first = u0Present ? readPrefix(input) : Prefix.ZERO;
        Prefix second = u1Present ? readPrefix(input) : Prefix.ZERO;
        return new int[] {readSuffix(first, input), readSuffix(second, input)};
    }

    private static void writePair(Prefix first, Prefix second,
            Htj2kReverseBitStream.Writer output) throws IIOException {
        output.writeBits(first.bits, first.length);
        output.writeBits(second.bits, second.length);
        output.writeBits(first.suffix, first.suffixLength);
        output.writeBits(second.suffix, second.suffixLength);
    }

    private static Prefix prefix(int value) {
        if (value == 0) {
            return Prefix.ZERO;
        }
        if (value == 1) {
            return new Prefix(1, 1, 1, 0, 0);
        }
        if (value == 2) {
            return new Prefix(2, 2, 2, 0, 0);
        }
        if (value <= 4) {
            return new Prefix(4, 3, 3, value - 3, 1);
        }
        return new Prefix(0, 3, 5, value - 5, 5);
    }

    private static Prefix readPrefix(Htj2kReverseBitStream.Reader input) throws IIOException {
        if (input.readBit() == 1) {
            return new Prefix(1, 1, 1, 0, 0);
        }
        if (input.readBit() == 1) {
            return new Prefix(2, 2, 2, 0, 0);
        }
        if (input.readBit() == 1) {
            return new Prefix(4, 3, 3, 0, 1);
        }
        return new Prefix(0, 3, 5, 0, 5);
    }

    private static int readSuffix(Prefix prefix, Htj2kReverseBitStream.Reader input)
            throws IIOException {
        return prefix.base + input.readBits(prefix.suffixLength);
    }

    private static void requireValue(int value) throws IIOException {
        if (value < 0 || value > 32) {
            throw new IIOException("HTJ2K UVLC value " + value
                    + " is outside the initial 0..32 profile");
        }
    }

    private static final class Prefix {
        private static final Prefix ZERO = new Prefix(0, 0, 0, 0, 0);

        private final int bits;
        private final int length;
        private final int base;
        private final int suffix;
        private final int suffixLength;

        private Prefix(int bits, int length, int base, int suffix, int suffixLength) {
            this.bits = bits;
            this.length = length;
            this.base = base;
            this.suffix = suffix;
            this.suffixLength = suffixLength;
        }
    }
}
