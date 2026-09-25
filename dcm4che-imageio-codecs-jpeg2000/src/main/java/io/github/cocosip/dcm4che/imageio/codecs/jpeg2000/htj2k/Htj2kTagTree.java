package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.htj2k;

import java.util.Arrays;

import javax.imageio.IIOException;

/** Mutable HT packet inclusion or missing-bitplane tree for one precinct band. */
final class Htj2kTagTree {
    private static final int UNKNOWN = Integer.MAX_VALUE;

    private final int width;
    private final int height;
    private final int[] widths;
    private final int[][] values;
    private final int[][] lows;
    private final boolean[][] known;

    Htj2kTagTree(int width, int height) {
        if (width <= 0 || height <= 0 || (long) width * height > 4096) {
            throw new IllegalArgumentException("Invalid HT tag-tree dimensions");
        }
        this.width = width;
        this.height = height;
        int count = 1;
        int w = width;
        int h = height;
        while (w != 1 || h != 1) {
            w = (w + 1) >>> 1;
            h = (h + 1) >>> 1;
            count++;
        }
        widths = new int[count];
        values = new int[count][];
        lows = new int[count][];
        known = new boolean[count][];
        w = width;
        h = height;
        for (int level = 0; level < count; level++) {
            widths[level] = w;
            values[level] = new int[w * h];
            Arrays.fill(values[level], UNKNOWN);
            lows[level] = new int[w * h];
            known[level] = new boolean[w * h];
            w = (w + 1) >>> 1;
            h = (h + 1) >>> 1;
        }
    }

    void set(int x, int y, int value) {
        requireLeaf(x, y);
        if (value < 0 || value > 63) {
            throw new IllegalArgumentException("HT tag-tree value must be 0..63");
        }
        for (int level = 0; level < widths.length; level++) {
            int index = y * widths[level] + x;
            values[level][index] = Math.min(values[level][index], value);
            x >>>= 1;
            y >>>= 1;
        }
    }

    void primeRootKnownZero() {
        int root = widths.length - 1;
        values[root][0] = 0;
        known[root][0] = true;
    }

    void encode(Htj2kPacketBits.Writer output, int x, int y, int threshold) {
        requireLeaf(x, y);
        requireThreshold(threshold);
        int[] path = path(x, y);
        int low = 0;
        for (int level = widths.length - 1; level >= 0; level--) {
            int index = path[level];
            low = Math.max(low, lows[level][index]);
            while (low < threshold) {
                if (low >= values[level][index]) {
                    if (!known[level][index]) {
                        output.bit(1);
                        known[level][index] = true;
                    }
                    break;
                }
                output.bit(0);
                low++;
            }
            lows[level][index] = low;
        }
    }

    boolean decode(Htj2kPacketBits.Reader input, int x, int y, int threshold)
            throws IIOException {
        requireLeaf(x, y);
        requireThreshold(threshold);
        int[] path = path(x, y);
        int low = 0;
        for (int level = widths.length - 1; level >= 0; level--) {
            int index = path[level];
            low = Math.max(low, lows[level][index]);
            while (low < threshold && low < values[level][index]) {
                if (input.bit() != 0) {
                    values[level][index] = low;
                } else {
                    low++;
                }
            }
            lows[level][index] = low;
        }
        return values[0][path[0]] < threshold;
    }

    int decodeValue(Htj2kPacketBits.Reader input, int x, int y) throws IIOException {
        for (int threshold = 1; threshold <= 31; threshold++) {
            if (decode(input, x, y, threshold)) {
                return threshold - 1;
            }
        }
        throw new IIOException("HT missing-bitplane tag-tree value exceeds 30");
    }

    private int[] path(int x, int y) {
        int[] path = new int[widths.length];
        for (int level = 0; level < widths.length; level++) {
            path[level] = y * widths[level] + x;
            x >>>= 1;
            y >>>= 1;
        }
        return path;
    }

    private void requireLeaf(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            throw new IllegalArgumentException("HT tag-tree leaf is outside the grid");
        }
    }

    private static void requireThreshold(int threshold) {
        if (threshold < 1 || threshold > 64) {
            throw new IllegalArgumentException("HT tag-tree threshold must be 1..64");
        }
    }
}
