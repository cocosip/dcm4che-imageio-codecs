package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.classic;

import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Exception;

/** Classic JPEG 2000 Tier-1 EBCOT code-block decoder. */
public final class Jpeg2000EbcotDecoder {
    private final int width;
    private final int height;
    private final int orientation;
    private final int style;
    private final int stride;
    private final int[] values;
    private final int[] flags;
    private final Jpeg2000MqDecoder mq;
    private int bitPlane;

    private Jpeg2000EbcotDecoder(Jpeg2000EbcotEncodedBlock encoded, byte[] data)
            throws Jpeg2000Exception {
        width = encoded.width();
        height = encoded.height();
        orientation = encoded.orientation();
        style = encoded.codeBlockStyle();
        stride = width + 2;
        values = new int[stride * (height + 2)];
        flags = new int[stride * (height + 2)];
        mq = new Jpeg2000MqDecoder(data, Jpeg2000EbcotEncoder.CONTEXT_COUNT);
        resetContexts();
    }

    public static int[] decode(Jpeg2000EbcotEncodedBlock encoded, int passCount)
            throws Jpeg2000Exception {
        if (encoded == null) {
            throw new NullPointerException("encoded");
        }
        if (passCount < 0) {
            throw new IllegalArgumentException("JPEG 2000 EBCOT pass count cannot be negative");
        }
        validateGeometryAndStyle(encoded);
        if (encoded.passes().isEmpty()) {
            if (encoded.maxBitPlane() != -1 || encoded.data().length != 0) {
                throw new Jpeg2000Exception("Invalid JPEG 2000 zero code-block metadata");
            }
            return new int[encoded.width() * encoded.height()];
        }
        if (passCount > encoded.passes().size()) {
            throw new Jpeg2000Exception("JPEG 2000 EBCOT pass count exceeds available pass metadata");
        }
        if (passCount == 0) {
            return new int[encoded.width() * encoded.height()];
        }

        byte[] data = encoded.data();
        int requiredLength = validatePasses(encoded, passCount, data.length);
        Jpeg2000EbcotDecoder decoder = new Jpeg2000EbcotDecoder(
                encoded, java.util.Arrays.copyOf(data, requiredLength));
        return decoder.decodePasses(passCount, encoded.maxBitPlane());
    }

    private static void validateGeometryAndStyle(Jpeg2000EbcotEncodedBlock encoded)
            throws Jpeg2000Exception {
        if (encoded.width() <= 0 || encoded.height() <= 0
                || encoded.width() > 1024 || encoded.height() > 1024
                || (long) encoded.width() * encoded.height() > 4096) {
            throw new Jpeg2000Exception("Invalid JPEG 2000 EBCOT code-block dimensions");
        }
        if (encoded.orientation() < 0 || encoded.orientation() > 3) {
            throw new Jpeg2000Exception("Invalid JPEG 2000 EBCOT orientation");
        }
        if ((encoded.codeBlockStyle() & ~Jpeg2000EbcotEncoder.SUPPORTED_CODE_BLOCK_STYLES) != 0) {
            throw new Jpeg2000Exception(String.format(
                    "Unsupported JPEG 2000 code-block style 0x%02x",
                    encoded.codeBlockStyle()));
        }
    }

    private static int validatePasses(
            Jpeg2000EbcotEncodedBlock encoded, int passCount, int payloadLength)
            throws Jpeg2000Exception {
        if (encoded.maxBitPlane() < 0 || encoded.maxBitPlane() > 30) {
            throw new Jpeg2000Exception("Invalid JPEG 2000 EBCOT maximum bit-plane");
        }
        int previousLength = 0;
        int bitPlane = encoded.maxBitPlane();
        int passType = 2;
        for (int index = 0; index < passCount; index++) {
            Jpeg2000EbcotPass pass = encoded.passes().get(index);
            Jpeg2000EbcotPass.Type expectedType = passType == 0
                    ? Jpeg2000EbcotPass.Type.SIGNIFICANCE_PROPAGATION
                    : passType == 1
                    ? Jpeg2000EbcotPass.Type.MAGNITUDE_REFINEMENT
                    : Jpeg2000EbcotPass.Type.CLEANUP;
            if (pass == null || pass.type() != expectedType || pass.bitPlane() != bitPlane) {
                throw new Jpeg2000Exception("Invalid JPEG 2000 EBCOT pass sequence at pass " + index);
            }
            if (pass.byteLength() < previousLength || pass.byteLength() > payloadLength) {
                throw new Jpeg2000Exception("Invalid JPEG 2000 EBCOT byte boundary at pass " + index);
            }
            previousLength = pass.byteLength();
            if (passType == 2) {
                passType = 0;
                bitPlane--;
            } else {
                passType++;
            }
        }
        if (previousLength < 2) {
            throw new Jpeg2000Exception("JPEG 2000 MQ pass boundary requires at least two bytes");
        }
        return previousLength;
    }

    private int[] decodePasses(int passCount, int maxBitPlane) throws Jpeg2000Exception {
        bitPlane = maxBitPlane;
        int passType = 2;
        for (int pass = 0; bitPlane >= 0 && pass < passCount; pass++) {
            if (passType == 0 || (passType == 2 && pass == 0)) {
                clearVisited();
            }
            if (passType == 0) {
                decodeSignificancePropagation();
            } else if (passType == 1) {
                decodeMagnitudeRefinement();
            } else {
                decodeCleanup();
                if ((style & 0x20) != 0) {
                    int segmentationSymbol = 0;
                    for (int bit = 0; bit < 4; bit++) {
                        segmentationSymbol = (segmentationSymbol << 1)
                                | mq.decode(Jpeg2000EbcotEncoder.CONTEXT_UNIFORM);
                    }
                    if (segmentationSymbol != 0x0a) {
                        throw new Jpeg2000Exception(
                                "Invalid JPEG 2000 EBCOT segmentation symbol");
                    }
                }
            }
            if ((style & 0x02) != 0) {
                resetContexts();
            }
            if (passType == 2) {
                passType = 0;
                bitPlane--;
            } else {
                passType++;
            }
        }
        int[] result = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                result[y * width + x] = values[index(x, y)];
            }
        }
        return result;
    }

    private void decodeSignificancePropagation() throws Jpeg2000Exception {
        for (int stripe = 0; stripe < height; stripe += 4) {
            for (int x = 0; x < width; x++) {
                for (int dy = 0; dy < 4 && stripe + dy < height; dy++) {
                    int y = stripe + dy;
                    int index = index(x, y);
                    int state = flags[index];
                    if ((state & Jpeg2000EbcotEncoder.SIGNIFICANT) != 0
                            || (state & Jpeg2000EbcotEncoder.SIG_NEIGHBORS) == 0) {
                        continue;
                    }
                    flags[index] |= Jpeg2000EbcotEncoder.VISITED;
                    if (mq.decode(zeroContext(state)) != 0) {
                        setSignificant(x, y, index);
                    }
                }
            }
        }
    }

    private void decodeMagnitudeRefinement() throws Jpeg2000Exception {
        for (int stripe = 0; stripe < height; stripe += 4) {
            for (int x = 0; x < width; x++) {
                for (int dy = 0; dy < 4 && stripe + dy < height; dy++) {
                    int y = stripe + dy;
                    int index = index(x, y);
                    int state = flags[index];
                    if ((state & Jpeg2000EbcotEncoder.SIGNIFICANT) == 0
                            || (state & Jpeg2000EbcotEncoder.VISITED) != 0) {
                        continue;
                    }
                    if (mq.decode(Jpeg2000EbcotEncoder.magnitudeContext(state)) != 0) {
                        int delta = 1 << bitPlane;
                        values[index] += (state & Jpeg2000EbcotEncoder.SIGN) != 0 ? -delta : delta;
                    }
                    flags[index] |= Jpeg2000EbcotEncoder.REFINED;
                }
            }
        }
    }

    private void decodeCleanup() throws Jpeg2000Exception {
        for (int stripe = 0; stripe < height; stripe += 4) {
            for (int x = 0; x < width; x++) {
                boolean run = stripe + 3 < height;
                if (run) {
                    for (int dy = 0; dy < 4; dy++) {
                        int state = flags[index(x, stripe + dy)];
                        if ((state & (Jpeg2000EbcotEncoder.VISITED
                                | Jpeg2000EbcotEncoder.SIGNIFICANT
                                | Jpeg2000EbcotEncoder.SIG_NEIGHBORS)) != 0) {
                            run = false;
                            break;
                        }
                    }
                }
                int runPosition = -1;
                if (run && mq.decode(Jpeg2000EbcotEncoder.CONTEXT_RUN_LENGTH) == 0) {
                    continue;
                }
                if (run) {
                    runPosition = (mq.decode(Jpeg2000EbcotEncoder.CONTEXT_UNIFORM) << 1)
                            | mq.decode(Jpeg2000EbcotEncoder.CONTEXT_UNIFORM);
                }
                for (int dy = 0; dy < 4 && stripe + dy < height; dy++) {
                    if (run && dy < runPosition) {
                        continue;
                    }
                    int y = stripe + dy;
                    int index = index(x, y);
                    int state = flags[index];
                    if ((state & (Jpeg2000EbcotEncoder.VISITED
                            | Jpeg2000EbcotEncoder.SIGNIFICANT)) != 0) {
                        flags[index] &= ~Jpeg2000EbcotEncoder.VISITED;
                        continue;
                    }
                    int significant = run && dy == runPosition ? 1 : mq.decode(zeroContext(state));
                    if (significant != 0) {
                        setSignificant(x, y, index);
                    }
                    flags[index] &= ~Jpeg2000EbcotEncoder.VISITED;
                }
            }
        }
    }

    private void setSignificant(int x, int y, int index) throws Jpeg2000Exception {
        int lookup = Jpeg2000EbcotEncoder.signLookup(flags[index]);
        int sign = mq.decode(Jpeg2000EbcotEncoder.SIGN_CONTEXTS[lookup])
                ^ Jpeg2000EbcotEncoder.SIGN_PREDICTIONS[lookup];
        int value = 1 << bitPlane;
        if (sign != 0) {
            flags[index] |= Jpeg2000EbcotEncoder.SIGN;
            value = -value;
        }
        values[index] = value;
        flags[index] |= Jpeg2000EbcotEncoder.SIGNIFICANT;
        updateNeighbors(x, y, index);
    }

    private int zeroContext(int state) {
        int generator = orientation == 1 ? 2 : orientation == 2 ? 1 : orientation;
        return Jpeg2000EbcotEncoder.ZERO_CONTEXTS[generator][Jpeg2000EbcotEncoder.zeroLookup(state)];
    }

    private void updateNeighbors(int x, int y, int index) {
        boolean negative = (flags[index] & Jpeg2000EbcotEncoder.SIGN) != 0;
        if (!isCausalBoundary(y)) {
            mark(index(x, y - 1), Jpeg2000EbcotEncoder.SIG_S,
                    negative ? Jpeg2000EbcotEncoder.SIGN_S : 0);
            flags[index(x - 1, y - 1)] |= Jpeg2000EbcotEncoder.SIG_SE;
            flags[index(x + 1, y - 1)] |= Jpeg2000EbcotEncoder.SIG_SW;
        }
        mark(index(x, y + 1), Jpeg2000EbcotEncoder.SIG_N,
                negative ? Jpeg2000EbcotEncoder.SIGN_N : 0);
        mark(index(x - 1, y), Jpeg2000EbcotEncoder.SIG_E,
                negative ? Jpeg2000EbcotEncoder.SIGN_E : 0);
        mark(index(x + 1, y), Jpeg2000EbcotEncoder.SIG_W,
                negative ? Jpeg2000EbcotEncoder.SIGN_W : 0);
        flags[index(x - 1, y + 1)] |= Jpeg2000EbcotEncoder.SIG_NE;
        flags[index(x + 1, y + 1)] |= Jpeg2000EbcotEncoder.SIG_NW;
    }

    private void mark(int index, int significant, int sign) {
        flags[index] |= significant | sign;
    }

    private boolean isCausalBoundary(int y) {
        return (style & 0x08) != 0 && (y & 3) == 0;
    }

    private void clearVisited() {
        for (int i = 0; i < flags.length; i++) {
            flags[i] &= ~Jpeg2000EbcotEncoder.VISITED;
        }
    }

    private void resetContexts() {
        mq.resetContexts();
        mq.setContextState(Jpeg2000EbcotEncoder.CONTEXT_UNIFORM, 46);
        mq.setContextState(Jpeg2000EbcotEncoder.CONTEXT_RUN_LENGTH, 3);
        mq.setContextState(0, 4);
    }

    private int index(int x, int y) {
        return (y + 1) * stride + x + 1;
    }
}
