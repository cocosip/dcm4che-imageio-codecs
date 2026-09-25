package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.htj2k;

import javax.imageio.IIOException;

/** HT cleanup MEL event encoder. The cleanup assembler owns MEL/VLC termination. */
final class Htj2kMelEncoder {
    private static final int[] EXPONENT = {0, 0, 0, 1, 1, 1, 2, 2, 2, 3, 3, 4, 5};

    private final Htj2kForwardBitStream.Writer bits;
    private int run;
    private int state;

    Htj2kMelEncoder(int maxBytes) {
        bits = new Htj2kForwardBitStream.Writer(maxBytes, true, "MEL");
    }

    void encode(boolean oneEvent) throws IIOException {
        if (!oneEvent) {
            run++;
            if (run == (1 << EXPONENT[state])) {
                bits.writeBit(1);
                run = 0;
                state = Math.min(12, state + 1);
            }
            return;
        }
        bits.writeBit(0);
        bits.writeBits(run, EXPONENT[state]);
        run = 0;
        state = Math.max(0, state - 1);
    }

    byte[] finish() throws IIOException {
        terminateRun();
        return bits.finish(false);
    }

    void terminateRun() throws IIOException {
        if (run > 0) {
            bits.writeBit(1);
            run = 0;
        }
    }

    byte[] emittedBytes() {
        return bits.emittedBytes();
    }

    int pendingByteAligned() {
        return bits.pendingByteAligned();
    }

    int remainingBits() {
        return bits.remainingBits();
    }

    int bitPosition() {
        return bits.bitPosition();
    }
}
