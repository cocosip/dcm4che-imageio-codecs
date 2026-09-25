package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.htj2k;

import javax.imageio.IIOException;

/** HT quad VLC reverse lookup from seven low-order bits and quad context. */
final class Htj2kVlcDecoder {
    private static final int[][] DECODE = new int[2][1024];

    static {
        build(Htj2kVlcTables.FIRST_ROW, DECODE[0]);
        build(Htj2kVlcTables.OTHER_ROWS, DECODE[1]);
    }

    private Htj2kVlcDecoder() {
    }

    static int symbol(boolean firstRow, int context, int nextSevenBits) throws IIOException {
        if (context < 0 || context > 7 || nextSevenBits < 0 || nextSevenBits > 127) {
            throw new IIOException("Invalid HTJ2K VLC context or lookahead bits");
        }
        int entry = DECODE[firstRow ? 0 : 1][(context << 7) | nextSevenBits];
        if (entry == 0) {
            throw new IIOException("Invalid HTJ2K VLC codeword for context " + context);
        }
        return entry;
    }

    static int codeLength(int entry) {
        return entry & 7;
    }

    static int rho(int entry) {
        return (entry >>> 4) & 0xf;
    }

    static int uOffset(int entry) {
        return (entry >>> 3) & 1;
    }

    static int e1(int entry) {
        return (entry >>> 8) & 0xf;
    }

    static int ek(int entry) {
        return (entry >>> 12) & 0xf;
    }

    private static void build(int[][] source, int[] target) {
        for (int index = 0; index < target.length; index++) {
            int context = index >>> 7;
            int code = index & 0x7f;
            for (int[] item : source) {
                int mask = (1 << item[6]) - 1;
                if (item[0] == context && item[5] == (code & mask)) {
                    target[index] = (item[1] << 4) | (item[2] << 3)
                            | (item[3] << 12) | (item[4] << 8) | item[6];
                }
            }
        }
    }
}
