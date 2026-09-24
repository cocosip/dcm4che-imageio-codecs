package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.classic;

import java.util.Arrays;

/** Standard JPEG 2000 MQ arithmetic encoder for classic EBCOT code-blocks. */
public final class Jpeg2000MqCoder {
    private final int[] contexts;
    private byte[] output = new byte[32];
    private int outputLength;
    private int bytePosition;
    private int a = 0x8000;
    private int c;
    private int ct = 12;
    private boolean flushed;

    public Jpeg2000MqCoder(int contextCount) {
        if (contextCount <= 0) {
            throw new IllegalArgumentException("JPEG 2000 MQ context count must be positive");
        }
        contexts = new int[contextCount];
        resetState();
    }

    public void encode(int bit, int context) {
        requireContext(context);
        if ((bit & ~1) != 0) {
            throw new IllegalArgumentException("JPEG 2000 MQ symbol must be 0 or 1");
        }
        if (flushed) {
            throw new IllegalStateException("JPEG 2000 MQ encoder is already flushed");
        }

        int cx = contexts[context];
        int state = cx & 0x7f;
        int mps = cx >>> 7;
        int qe = Jpeg2000MqStateTable.qe(state);
        if (bit == mps) {
            a -= qe;
            if ((a & 0x8000) == 0) {
                if (a < qe) {
                    a = qe;
                } else {
                    c += qe;
                }
                contexts[context] = Jpeg2000MqStateTable.nmps(state) | (mps << 7);
                renormalize();
            } else {
                c += qe;
            }
        } else {
            a -= qe;
            if (a < qe) {
                c += qe;
            } else {
                a = qe;
            }
            int nextMps = Jpeg2000MqStateTable.switchMps(state) ? 1 - mps : mps;
            contexts[context] = Jpeg2000MqStateTable.nlps(state) | (nextMps << 7);
            renormalize();
        }
    }

    public void setContextState(int context, int state) {
        requireContext(context);
        if (state < 0 || state > 0xff) {
            throw new IllegalArgumentException("JPEG 2000 MQ context state must fit one byte");
        }
        if ((state & 0x7f) >= Jpeg2000MqStateTable.size()) {
            throw new IllegalArgumentException("JPEG 2000 MQ context probability state is invalid");
        }
        contexts[context] = state;
    }

    public void resetContexts() {
        Arrays.fill(contexts, 0);
    }

    public byte[] flush() {
        if (!flushed) {
            int temp = c + a;
            c |= 0xffff;
            if (c >= temp) {
                c -= 0x8000;
            }
            c <<= ct;
            byteOut();
            c <<= ct;
            byteOut();
            if (currentByte() != 0xff) {
                bytePosition++;
            }
            flushed = true;
        }
        int length = bytePosition - 1;
        if (length <= 0) {
            return new byte[0];
        }
        byte[] encoded = new byte[length];
        System.arraycopy(output, 1, encoded, 0, length);
        return encoded;
    }

    public int currentLength() {
        return Math.max(0, bytePosition - 1);
    }

    int passLengthEstimate() {
        return bytePosition + 2;
    }

    public void reset() {
        Arrays.fill(contexts, 0);
        resetState();
    }

    private void resetState() {
        outputLength = 1;
        output[0] = 0;
        bytePosition = 0;
        a = 0x8000;
        c = 0;
        ct = 12;
        flushed = false;
    }

    private void renormalize() {
        while (a < 0x8000) {
            a <<= 1;
            c <<= 1;
            if (--ct == 0) {
                byteOut();
            }
        }
    }

    private void byteOut() {
        ensure(bytePosition);
        if (currentByte() == 0xff) {
            bytePosition++;
            ensure(bytePosition);
            setCurrentByte(c >>> 20);
            c &= 0xfffff;
            ct = 7;
            return;
        }
        if ((c & 0x08000000) == 0) {
            bytePosition++;
            ensure(bytePosition);
            setCurrentByte(c >>> 19);
            c &= 0x7ffff;
            ct = 8;
            return;
        }
        setCurrentByte(currentByte() + 1);
        if (currentByte() == 0xff) {
            c &= 0x07ffffff;
            bytePosition++;
            ensure(bytePosition);
            setCurrentByte(c >>> 20);
            c &= 0xfffff;
            ct = 7;
            return;
        }
        bytePosition++;
        ensure(bytePosition);
        setCurrentByte(c >>> 19);
        c &= 0x7ffff;
        ct = 8;
    }

    private int currentByte() {
        return output[bytePosition] & 0xff;
    }

    private void setCurrentByte(int value) {
        output[bytePosition] = (byte) value;
    }

    private void ensure(int index) {
        if (index < outputLength) {
            return;
        }
        int newLength = output.length;
        while (newLength <= index) {
            newLength *= 2;
        }
        output = Arrays.copyOf(output, newLength);
        outputLength = index + 1;
    }

    private void requireContext(int context) {
        if (context < 0 || context >= contexts.length) {
            throw new IllegalArgumentException("invalid JPEG 2000 MQ context: " + context);
        }
    }
}
