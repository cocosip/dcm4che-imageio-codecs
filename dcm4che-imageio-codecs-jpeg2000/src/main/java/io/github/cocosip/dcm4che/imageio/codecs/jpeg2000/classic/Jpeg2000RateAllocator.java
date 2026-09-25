package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.classic;

import java.util.ArrayList;
import java.util.List;

import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Exception;

/** Greedy PCRD-style allocation over measured code-block pass distortion slopes. */
public final class Jpeg2000RateAllocator {
    private Jpeg2000RateAllocator() {
    }

    public static int[][] allocate(List<Block> blocks, double[] ratios,
            long uncompressedBytes) throws Jpeg2000Exception {
        if (blocks == null || ratios == null || ratios.length == 0
                || uncompressedBytes <= 0) {
            throw new IllegalArgumentException("JPEG 2000 rate allocation input is invalid");
        }
        if (ratios.length > 65535) {
            throw new Jpeg2000Exception("JPEG 2000 layer count exceeds 65535");
        }
        List<double[]> slopes = new ArrayList<double[]>(blocks.size());
        for (Block block : blocks) {
            slopes.add(measureSlopes(block));
        }
        int[][] targets = new int[blocks.size()][ratios.length];
        int[] selected = new int[blocks.size()];
        long used = 0;
        for (int layer = 0; layer < ratios.length; layer++) {
            double ratio = ratios[layer];
            if (!Double.isFinite(ratio) || ratio < 0) {
                throw new Jpeg2000Exception("JPEG 2000 layer ratio is invalid");
            }
            long budget = ratio == 0 ? Long.MAX_VALUE
                    : Math.max(0L, (long) Math.floor(uncompressedBytes / ratio));
            if (budget < used) {
                budget = used;
            }
            while (true) {
                int best = -1;
                double bestSlope = -1;
                for (int index = 0; index < blocks.size(); index++) {
                    Block block = blocks.get(index);
                    if (selected[index] >= block.encoded.passes().size()) {
                        continue;
                    }
                    int before = selected[index] == 0 ? 0
                            : block.encoded.passes().get(selected[index] - 1).byteLength();
                    int after = block.encoded.passes().get(selected[index]).byteLength();
                    if (after - before > budget - used) {
                        continue;
                    }
                    double slope = slopes.get(index)[selected[index]];
                    if (slope > bestSlope) {
                        best = index;
                        bestSlope = slope;
                    }
                }
                if (best < 0) {
                    break;
                }
                Block block = blocks.get(best);
                int before = selected[best] == 0 ? 0
                        : block.encoded.passes().get(selected[best] - 1).byteLength();
                used += block.encoded.passes().get(selected[best]).byteLength() - before;
                selected[best]++;
            }
            for (int index = 0; index < blocks.size(); index++) {
                targets[index][layer] = selected[index];
            }
        }
        return targets;
    }

    private static double[] measureSlopes(Block block) throws Jpeg2000Exception {
        int passCount = block.encoded.passes().size();
        double[] slopes = new double[passCount];
        double previousDistortion = distortion(block.original, null);
        int previousLength = 0;
        for (int pass = 1; pass <= passCount; pass++) {
            int[] reconstruction = Jpeg2000EbcotDecoder.decode(block.encoded, pass);
            double currentDistortion = distortion(block.original, reconstruction);
            int length = block.encoded.passes().get(pass - 1).byteLength();
            int delta = length - previousLength;
            double improvement = Math.max(0, previousDistortion - currentDistortion);
            slopes[pass - 1] = delta == 0
                    ? (improvement > 0 ? Double.POSITIVE_INFINITY : 0)
                    : improvement / delta;
            previousDistortion = currentDistortion;
            previousLength = length;
        }
        return slopes;
    }

    private static double distortion(int[] original, int[] reconstructed) {
        double error = 0;
        for (int i = 0; i < original.length; i++) {
            double difference = original[i] - (reconstructed == null ? 0 : reconstructed[i]);
            error += difference * difference;
        }
        return error;
    }

    public static final class Block {
        private final Jpeg2000EbcotEncodedBlock encoded;
        private final int[] original;

        public Block(Jpeg2000EbcotEncodedBlock encoded, int[] original) {
            if (encoded == null || original == null
                    || original.length != encoded.width() * encoded.height()) {
                throw new IllegalArgumentException("JPEG 2000 rate allocation block is invalid");
            }
            this.encoded = encoded;
            this.original = original.clone();
        }
    }
}
