package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.htj2k;

import javax.imageio.IIOException;

/** HT quad VLC lookup; the cleanup pass controls interleaving with UVLC. */
final class Htj2kVlcEncoder {
    private static final int[][] ENCODE = new int[2][2048];

    static {
        build(Htj2kVlcTables.FIRST_ROW, ENCODE[0]);
        build(Htj2kVlcTables.OTHER_ROWS, ENCODE[1]);
    }

    private Htj2kVlcEncoder() {
    }

    static int codeword(boolean firstRow, int context, int rho, int emb)
            throws IIOException {
        if (context < 0 || context > 7 || rho < 0 || rho > 15 || emb < 0 || emb > 15
                || (emb & ~rho) != 0 || (context == 0 && rho == 0)) {
            throw new IIOException("Invalid HTJ2K VLC context, significance, or EMB pattern");
        }
        int entry = ENCODE[firstRow ? 0 : 1][(context << 8) | (rho << 4) | emb];
        if (entry == 0) {
            throw new IIOException("Unsupported HTJ2K VLC symbol for context " + context);
        }
        return entry;
    }

    static int code(int entry) {
        return entry >>> 8;
    }

    static int codeLength(int entry) {
        return (entry >>> 4) & 0xf;
    }

    static int ek(int entry) {
        return entry & 0xf;
    }

    private static void build(int[][] source, int[] target) {
        for (int index = 0; index < target.length; index++) {
            int context = index >>> 8;
            int rho = (index >>> 4) & 0xf;
            int emb = index & 0xf;
            if ((emb & ~rho) != 0 || (context == 0 && rho == 0)) {
                continue;
            }
            int best = -1;
            int bestWeight = -1;
            for (int row = 0; row < source.length; row++) {
                int[] item = source[row];
                if (item[0] != context || item[1] != rho || item[2] != (emb == 0 ? 0 : 1)) {
                    continue;
                }
                if (emb == 0) {
                    best = row;
                    break;
                }
                if ((emb & item[3]) == item[4]) {
                    int weight = Integer.bitCount(item[3]);
                    if (weight >= bestWeight) {
                        best = row;
                        bestWeight = weight;
                    }
                }
            }
            if (best >= 0) {
                int[] item = source[best];
                target[index] = (item[5] << 8) | (item[6] << 4) | item[3];
            }
        }
    }
}
