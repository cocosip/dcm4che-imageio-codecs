package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.htj2k;

import javax.imageio.IIOException;

/** Strict MEL event reader; callers supply the event count from quad context. */
final class Htj2kMelDecoder {
    private static final int[] EXPONENT = {0, 0, 0, 1, 1, 1, 2, 2, 2, 3, 3, 4, 5};

    private final Htj2kForwardBitStream.Reader bits;
    private int state;
    private int pendingZeros;
    private boolean pendingOne;

    Htj2kMelDecoder(byte[] data, int offset, int length) {
        bits = new Htj2kForwardBitStream.Reader(data, offset, length, true, "MEL");
    }

    boolean nextEvent() throws IIOException {
        if (pendingZeros == 0 && !pendingOne) {
            if (bits.readBit() == 1) {
                pendingZeros = 1 << EXPONENT[state];
                state = Math.min(12, state + 1);
            } else {
                pendingZeros = bits.readBits(EXPONENT[state]);
                pendingOne = true;
                state = Math.max(0, state - 1);
            }
        }
        if (pendingZeros > 0) {
            pendingZeros--;
            return false;
        }
        pendingOne = false;
        return true;
    }

    int bitPosition() {
        return bits.bitPosition();
    }
}
