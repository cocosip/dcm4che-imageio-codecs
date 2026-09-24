package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.classic;

/** JPEG 2000 Part 1 probability state transitions (Table C.2). */
final class Jpeg2000MqStateTable {
    private static final int[] QE = {
            0x5601, 0x3401, 0x1801, 0x0ac1, 0x0521, 0x0221, 0x5601, 0x5401,
            0x4801, 0x3801, 0x3001, 0x2401, 0x1c01, 0x1601, 0x5601, 0x5401,
            0x5101, 0x4801, 0x3801, 0x3401, 0x3001, 0x2801, 0x2401, 0x2201,
            0x1c01, 0x1801, 0x1601, 0x1401, 0x1201, 0x1101, 0x0ac1, 0x09c1,
            0x08a1, 0x0521, 0x0441, 0x02a1, 0x0221, 0x0141, 0x0111, 0x0085,
            0x0049, 0x0025, 0x0015, 0x0009, 0x0005, 0x0001, 0x5601
    };

    private static final int[] NMPS = {
            1, 2, 3, 4, 5, 38, 7, 8, 9, 10, 11, 12, 13, 29, 15, 16,
            17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32,
            33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 45, 46
    };

    private static final int[] NLPS = {
            1, 6, 9, 12, 29, 33, 6, 14, 14, 14, 17, 18, 20, 21, 14, 14,
            15, 16, 17, 18, 19, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29,
            30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 46
    };

    private static final boolean[] SWITCH_MPS = {
            true, false, false, false, false, false, true, false,
            false, false, false, false, false, false, true, false,
            false, false, false, false, false, false, false, false,
            false, false, false, false, false, false, false, false,
            false, false, false, false, false, false, false, false,
            false, false, false, false, false, false, false
    };

    private Jpeg2000MqStateTable() {
    }

    static int size() {
        return QE.length;
    }

    static int qe(int state) {
        return QE[state];
    }

    static int nmps(int state) {
        return NMPS[state];
    }

    static int nlps(int state) {
        return NLPS[state];
    }

    static boolean switchMps(int state) {
        return SWITCH_MPS[state];
    }
}
