package io.github.cocosip.dcm4che.imageio.codecs.jpegls.internal;

final class JpegLsRunModeScanner {
    private static final int[] RUN_INDEX_BITS = {
        0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3,
        4, 4, 5, 5, 6, 6, 7, 7, 8, 9, 10, 11, 12, 13, 14, 15
    };

    private final JpegLsTraits traits;
    private final JpegLsRunModeContext[] contexts;
    private int runIndex;

    JpegLsRunModeScanner(JpegLsTraits traits) {
        this.traits = traits;
        this.contexts = new JpegLsRunModeContext[] {
            new JpegLsRunModeContext(0, traits.range()), new JpegLsRunModeContext(1, traits.range())
        };
    }

    void encodeRunLength(JpegLsGolombWriter writer, int runLength, boolean endOfLine)
            throws JpegLsException {
        if (runLength < 0) throw new JpegLsException("negative JPEG-LS run length");
        while (runLength >= (1 << RUN_INDEX_BITS[runIndex])) {
            writer.writeBit(1);
            runLength -= 1 << RUN_INDEX_BITS[runIndex];
            incrementRunIndex();
        }
        if (endOfLine) {
            if (runLength != 0) writer.writeBit(1);
        } else {
            writer.writeBits(runLength, RUN_INDEX_BITS[runIndex] + 1);
        }
    }

    int decodeRunLength(JpegLsGolombReader reader, int remaining) throws JpegLsException {
        int runLength = 0;
        while (reader.readBit() == 1) {
            int count = Math.min(1 << RUN_INDEX_BITS[runIndex], remaining - runLength);
            runLength += count;
            if (count == (1 << RUN_INDEX_BITS[runIndex])) incrementRunIndex();
            if (runLength >= remaining) return remaining;
        }
        if (RUN_INDEX_BITS[runIndex] > 0) runLength += reader.readBits(RUN_INDEX_BITS[runIndex]);
        if (runLength > remaining) throw new JpegLsException("JPEG-LS run exceeds line length");
        return runLength;
    }

    void encodeRunInterruption(JpegLsGolombWriter writer, int contextIndex, int errorValue)
            throws JpegLsException {
        JpegLsRunModeContext context = contexts[contextIndex];
        int parameter = context.golombParameter();
        boolean map = context.computeMap(errorValue, parameter);
        int mapped = 2 * Math.abs(errorValue) - (contextIndex == 1 ? 1 : 0);
        if (map) mapped--;
        writer.writeMapped(mapped, parameter,
                traits.limit() - RUN_INDEX_BITS[runIndex] - 1, traits.quantizedBitsPerPixel());
        context.update(errorValue, mapped, traits.resetThreshold());
    }

    int decodeRunInterruption(JpegLsGolombReader reader, int contextIndex) throws JpegLsException {
        JpegLsRunModeContext context = contexts[contextIndex];
        int parameter = context.golombParameter();
        int mapped = reader.readMapped(parameter,
                traits.limit() - RUN_INDEX_BITS[runIndex] - 1, traits.quantizedBitsPerPixel());
        int error = context.computeErrorValue(mapped + (contextIndex == 1 ? 1 : 0), parameter);
        context.update(error, mapped, traits.resetThreshold());
        return error;
    }

    void decrementRunIndex() {
        if (runIndex > 0) runIndex--;
    }

    private void incrementRunIndex() {
        if (runIndex < RUN_INDEX_BITS.length - 1) runIndex++;
    }
}
