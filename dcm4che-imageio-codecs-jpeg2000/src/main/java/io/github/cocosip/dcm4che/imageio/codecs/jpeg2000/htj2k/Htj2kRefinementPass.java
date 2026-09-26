package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.htj2k;

import javax.imageio.IIOException;

/** HT significance propagation and magnitude refinement after cleanup. */
final class Htj2kRefinementPass {
    private Htj2kRefinementPass() {
    }

    static Htj2kCodeBlock decode(Htj2kCodeBlock cleanup, int missingMsbs,
            int passes, byte[] refinement) throws IIOException {
        int shift = cleanup.kmax() - 1 - missingMsbs;
        if (shift < 0 || shift > 28 || passes < 1 || passes > 3
                || refinement == null || refinement.length >= 2047
                || (passes > 1 && (shift == 0 || refinement.length == 0))
                || (passes == 1 && refinement.length != 0)) {
            throw new IIOException("Invalid HTJ2K refinement bitplane or segment length");
        }
        int width = cleanup.width();
        int height = cleanup.height();
        int[] result = new int[width * height];
        boolean[] original = new boolean[result.length];
        boolean[] significant = new boolean[result.length];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = y * width + x;
                int value = cleanup.coefficient(x, y);
                original[index] = significant[index] = value != 0;
                long magnitude = (Math.abs((long) value) << shift)
                        + (value != 0 && shift > 0 ? 1L << (shift - 1) : 0);
                if (magnitude >= (1L << cleanup.kmax())) {
                    throw new IIOException("HTJ2K cleanup magnitude exceeds its bitplane bounds");
                }
                result[index] = value < 0 ? -(int) magnitude : (int) magnitude;
            }
        }
        if (passes > 1) {
            Htj2kForwardBitStream.Reader spp = new Htj2kForwardBitStream.Reader(
                    refinement, 0, refinement.length, false, "SPP");
            // Significance symbols precede the signs in each four-column stripe group.
            for (int top = 0; top < height; top += 4) {
                for (int left = 0; left < width; left += 4) {
                    int[] discovered = new int[16];
                    int count = 0;
                    for (int x = left; x < Math.min(left + 4, width); x++) {
                        for (int y = top; y < Math.min(top + 4, height); y++) {
                            int index = y * width + x;
                            if (!significant[index]
                                    && hasNeighbor(significant, width, height, x, y)
                                    && spp.readBit() != 0) {
                                significant[index] = true;
                                discovered[count++] = index;
                            }
                        }
                    }
                    int magnitude = (1 << (shift - 1))
                            + (shift > 1 ? 1 << (shift - 2) : 0);
                    for (int i = 0; i < count; i++) {
                        result[discovered[i]] = spp.readBit() == 0 ? magnitude : -magnitude;
                    }
                }
            }
        }
        if (passes == 3) {
            Htj2kReverseBitStream.Reader mrp = new Htj2kReverseBitStream.Reader(
                    refinement, 0, refinement.length, "MRP");
            mrp.initializeRefinement();
            for (int top = 0; top < height; top += 4) {
                for (int x = 0; x < width; x++) {
                    for (int y = top; y < Math.min(top + 4, height); y++) {
                        int index = y * width + x;
                        if (original[index]) {
                            int magnitude = Math.abs(result[index]) - (1 << (shift - 1))
                                    + (mrp.readBit() << (shift - 1))
                                    + (shift > 1 ? 1 << (shift - 2) : 0);
                            result[index] = result[index] < 0 ? -magnitude : magnitude;
                        }
                    }
                }
            }
        }
        return new Htj2kCodeBlock(width, height, cleanup.kmax(), result);
    }

    private static boolean hasNeighbor(boolean[] significant, int width,
            int height, int x, int y) {
        for (int ny = Math.max(0, y - 1); ny <= Math.min(height - 1, y + 1); ny++) {
            for (int nx = Math.max(0, x - 1); nx <= Math.min(width - 1, x + 1); nx++) {
                if (significant[ny * width + nx]) {
                    return true;
                }
            }
        }
        return false;
    }
}
