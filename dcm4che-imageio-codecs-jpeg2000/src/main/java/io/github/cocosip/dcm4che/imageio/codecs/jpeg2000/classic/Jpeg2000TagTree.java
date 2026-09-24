package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.classic;

import java.util.Arrays;

import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Exception;

/** Mutable classic JPEG 2000 inclusion or zero-bit-plane tag tree. */
final class Jpeg2000TagTree {
    private static final int UNKNOWN = Integer.MAX_VALUE;
    private static final int MAX_NODES = 1 << 24;

    private final int width;
    private final int height;
    private final int levels;
    private final int[] levelWidths;
    private final int[][] values;
    private final int[][] lows;
    private final boolean[][] known;

    Jpeg2000TagTree(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("JPEG 2000 tag-tree dimensions must be positive");
        }
        this.width = width;
        this.height = height;

        int levelCount = 1;
        int levelWidth = width;
        int levelHeight = height;
        long nodeCount = 0;
        while (true) {
            nodeCount += (long) levelWidth * levelHeight;
            if (nodeCount > MAX_NODES) {
                throw new IllegalArgumentException("JPEG 2000 tag-tree exceeds the node limit");
            }
            if (levelWidth == 1 && levelHeight == 1) {
                break;
            }
            levelCount++;
            levelWidth = (levelWidth + 1) >>> 1;
            levelHeight = (levelHeight + 1) >>> 1;
        }
        levels = levelCount;
        levelWidths = new int[levels];
        values = new int[levels][];
        lows = new int[levels][];
        known = new boolean[levels][];

        levelWidth = width;
        levelHeight = height;
        for (int level = 0; level < levels; level++) {
            levelWidths[level] = levelWidth;
            int size = levelWidth * levelHeight;
            values[level] = new int[size];
            Arrays.fill(values[level], UNKNOWN);
            lows[level] = new int[size];
            known[level] = new boolean[size];
            levelWidth = (levelWidth + 1) >>> 1;
            levelHeight = (levelHeight + 1) >>> 1;
        }
    }

    void setValue(int x, int y, int value) {
        requireLeaf(x, y);
        if (value < 0 || value > 65535) {
            throw new IllegalArgumentException("JPEG 2000 tag-tree value must be 0..65535");
        }
        int px = x;
        int py = y;
        for (int level = 0; level < levels; level++) {
            int index = py * levelWidths[level] + px;
            if (values[level][index] <= value) {
                break;
            }
            values[level][index] = value;
            px >>>= 1;
            py >>>= 1;
        }
    }

    void encode(Jpeg2000PacketBitWriter writer, int x, int y, int threshold) {
        if (writer == null) {
            throw new NullPointerException("writer");
        }
        requireLeaf(x, y);
        requireThreshold(threshold);
        int[] indices = path(x, y);
        int low = 0;
        for (int level = levels - 1; level >= 0; level--) {
            int index = indices[level];
            if (low > lows[level][index]) {
                lows[level][index] = low;
            } else {
                low = lows[level][index];
            }
            while (low < threshold) {
                if (low >= values[level][index]) {
                    if (!known[level][index]) {
                        writer.writeBit(1);
                        known[level][index] = true;
                    }
                    break;
                }
                writer.writeBit(0);
                low++;
            }
            lows[level][index] = low;
        }
    }

    boolean decode(Jpeg2000PacketBitReader reader, int x, int y, int threshold)
            throws Jpeg2000Exception {
        if (reader == null) {
            throw new NullPointerException("reader");
        }
        requireLeaf(x, y);
        requireThreshold(threshold);
        int[] indices = path(x, y);
        int low = 0;
        int leafValue = UNKNOWN;
        for (int level = levels - 1; level >= 0; level--) {
            int index = indices[level];
            if (low > lows[level][index]) {
                lows[level][index] = low;
            } else {
                low = lows[level][index];
            }
            while (low < threshold && low < values[level][index]) {
                if (reader.readBit() != 0) {
                    values[level][index] = low;
                } else {
                    low++;
                }
            }
            lows[level][index] = low;
            if (level == 0) {
                leafValue = values[level][index];
            }
        }
        return leafValue < threshold;
    }

    int decodeValue(Jpeg2000PacketBitReader reader, int x, int y)
            throws Jpeg2000Exception {
        for (int threshold = 1; threshold <= 64; threshold++) {
            if (decode(reader, x, y, threshold)) {
                return threshold - 1;
            }
        }
        throw new Jpeg2000Exception("JPEG 2000 zero-bit-plane tag-tree value exceeds 63");
    }

    private int[] path(int x, int y) {
        int[] indices = new int[levels];
        int px = x;
        int py = y;
        for (int level = 0; level < levels; level++) {
            indices[level] = py * levelWidths[level] + px;
            px >>>= 1;
            py >>>= 1;
        }
        return indices;
    }

    private void requireLeaf(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            throw new IllegalArgumentException("JPEG 2000 tag-tree leaf is outside the tree");
        }
    }

    private static void requireThreshold(int threshold) {
        if (threshold <= 0 || threshold > 65536) {
            throw new IllegalArgumentException("JPEG 2000 tag-tree threshold must be 1..65536");
        }
    }
}
