package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.classic;

import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Exception;

/** Standard JPEG 2000 MQ arithmetic decoder for classic EBCOT code-blocks. */
public final class Jpeg2000MqDecoder {
    private final byte[] data;
    private final int[] contexts;
    private int position;
    private int a;
    private int c;
    private int ct;

    public Jpeg2000MqDecoder(byte[] data, int contextCount) throws Jpeg2000Exception {
        if (data == null || data.length == 0) {
            throw new Jpeg2000Exception("JPEG 2000 MQ entropy requires at least one byte");
        }
        if (contextCount <= 0) {
            throw new IllegalArgumentException("JPEG 2000 MQ context count must be positive");
        }
        for (int index = 1; index < data.length; index++) {
            if ((data[index - 1] & 0xff) == 0xff && (data[index] & 0xff) > 0x8f) {
                throw new Jpeg2000Exception(
                        "Invalid JPEG 2000 MQ byte stuffing after 0xff at byte " + (index - 1));
            }
        }
        this.data = new byte[data.length + 2];
        System.arraycopy(data, 0, this.data, 0, data.length);
        this.data[data.length] = (byte) 0xff;
        this.data[data.length + 1] = (byte) 0xff;
        this.contexts = new int[contextCount];
        a = 0x8000;
        c = (this.data[0] & 0xff) << 16;
        byteIn();
        c <<= 7;
        ct -= 7;
        a = 0x8000;
    }

    public int decode(int context) throws Jpeg2000Exception {
        requireContext(context);
        int cx = contexts[context];
        int state = cx & 0x7f;
        int mps = cx >>> 7;
        int qe = Jpeg2000MqStateTable.qe(state);
        a -= qe;

        if ((c >>> 16) < qe) {
            int bit;
            if (a < qe) {
                a = qe;
                bit = mps;
                contexts[context] = Jpeg2000MqStateTable.nmps(state) | (mps << 7);
            } else {
                a = qe;
                bit = 1 - mps;
                int nextMps = Jpeg2000MqStateTable.switchMps(state) ? 1 - mps : mps;
                contexts[context] = Jpeg2000MqStateTable.nlps(state) | (nextMps << 7);
            }
            renormalize();
            return bit;
        }

        c -= qe << 16;
        if ((a & 0x8000) != 0) {
            return mps;
        }

        int bit;
        if (a < qe) {
            bit = 1 - mps;
            int nextMps = Jpeg2000MqStateTable.switchMps(state) ? 1 - mps : mps;
            contexts[context] = Jpeg2000MqStateTable.nlps(state) | (nextMps << 7);
        } else {
            bit = mps;
            contexts[context] = Jpeg2000MqStateTable.nmps(state) | (mps << 7);
        }
        renormalize();
        return bit;
    }

    public void setContextState(int context, int state) {
        requireContext(context);
        if (state < 0 || state > 0xff || (state & 0x7f) >= Jpeg2000MqStateTable.size()) {
            throw new IllegalArgumentException("invalid JPEG 2000 MQ context state");
        }
        contexts[context] = state;
    }

    public void resetContexts() {
        java.util.Arrays.fill(contexts, 0);
    }

    private void renormalize() throws Jpeg2000Exception {
        while (a < 0x8000) {
            if (ct == 0) {
                byteIn();
            }
            a <<= 1;
            c <<= 1;
            ct--;
        }
    }

    private void byteIn() throws Jpeg2000Exception {
        if (position + 1 >= data.length) {
            throw new Jpeg2000Exception("Truncated JPEG 2000 MQ entropy during byte refill");
        }
        int current = data[position] & 0xff;
        int next = data[position + 1] & 0xff;
        if (current == 0xff) {
            if (next > 0x8f) {
                c += 0xff00;
                ct = 8;
            } else {
                position++;
                c += next << 9;
                ct = 7;
            }
        } else {
            position++;
            c += next << 8;
            ct = 8;
        }
    }

    private void requireContext(int context) {
        if (context < 0 || context >= contexts.length) {
            throw new IllegalArgumentException("invalid JPEG 2000 MQ context: " + context);
        }
    }
}
