package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.classic;

import java.util.ArrayList;
import java.util.List;

import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Exception;

/** Classic JPEG 2000 Tier-1 EBCOT code-block encoder. */
public final class Jpeg2000EbcotEncoder {
    static final int SUPPORTED_CODE_BLOCK_STYLES = 0x02 | 0x08 | 0x20;
    static final int SIGNIFICANT = 1;
    static final int REFINED = 2;
    static final int VISITED = 4;
    static final int SIG_N = 1 << 5;
    static final int SIG_S = 1 << 6;
    static final int SIG_W = 1 << 7;
    static final int SIG_E = 1 << 8;
    static final int SIG_NW = 1 << 9;
    static final int SIG_NE = 1 << 10;
    static final int SIG_SW = 1 << 11;
    static final int SIG_SE = 1 << 12;
    static final int SIG_NEIGHBORS = SIG_N | SIG_S | SIG_W | SIG_E | SIG_NW | SIG_NE | SIG_SW | SIG_SE;
    static final int SIGN = 1 << 13;
    static final int SIGN_N = 1 << 14;
    static final int SIGN_S = 1 << 15;
    static final int SIGN_W = 1 << 16;
    static final int SIGN_E = 1 << 17;

    static final int CONTEXT_RUN_LENGTH = 17;
    static final int CONTEXT_UNIFORM = 18;
    static final int CONTEXT_COUNT = 19;
    static final int[][] ZERO_CONTEXTS = buildZeroContexts();
    static final int[] SIGN_CONTEXTS = buildSignContexts();
    static final int[] SIGN_PREDICTIONS = buildSignPredictions();

    private final int width;
    private final int height;
    private final int orientation;
    private final int codeBlockStyle;
    private final int stride;
    private final int[] values;
    private final int[] flags;
    private Jpeg2000MqCoder mq;
    private int bitPlane;

    public Jpeg2000EbcotEncoder(int width, int height, int orientation, int codeBlockStyle) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("JPEG 2000 code-block dimensions must be positive");
        }
        if (width > 1024 || height > 1024 || (long) width * height > 4096) {
            throw new IllegalArgumentException(
                    "JPEG 2000 Part 1 code-block dimensions exceed the 4096-sample limit");
        }
        if (orientation < 0 || orientation > 3) {
            throw new IllegalArgumentException("JPEG 2000 code-block orientation must be 0..3");
        }
        if (codeBlockStyle < 0 || codeBlockStyle > 0xff
                || (codeBlockStyle & ~SUPPORTED_CODE_BLOCK_STYLES) != 0) {
            throw new IllegalArgumentException(String.format(
                    "Unsupported JPEG 2000 code-block style 0x%02x; only RESET, VSC, and SEGMARK are supported",
                    codeBlockStyle));
        }
        this.width = width;
        this.height = height;
        this.orientation = orientation;
        this.codeBlockStyle = codeBlockStyle;
        stride = width + 2;
        values = new int[stride * (height + 2)];
        flags = new int[stride * (height + 2)];
    }

    public Jpeg2000EbcotEncodedBlock encode(int[] coefficients, int passCount) throws Jpeg2000Exception {
        if (coefficients == null || coefficients.length != width * height) {
            throw new IllegalArgumentException("JPEG 2000 code-block coefficient length does not match geometry");
        }
        if (passCount <= 0) {
            throw new IllegalArgumentException("JPEG 2000 EBCOT pass count must be positive");
        }
        java.util.Arrays.fill(values, 0);
        java.util.Arrays.fill(flags, 0);
        int max = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int value = coefficients[y * width + x];
                if (value == Integer.MIN_VALUE) {
                    throw new Jpeg2000Exception(
                            "JPEG 2000 EBCOT cannot represent Integer.MIN_VALUE coefficient magnitude");
                }
                values[index(x, y)] = value;
                long magnitude = Math.abs((long) value);
                if (magnitude > max) {
                    max = magnitude > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) magnitude;
                }
            }
        }
        int maxBitPlane = max == 0 ? -1 : 31 - Integer.numberOfLeadingZeros(max);
        if (maxBitPlane < 0) {
            return new Jpeg2000EbcotEncodedBlock(width, height, orientation, codeBlockStyle,
                    -1, new byte[0], new ArrayList<Jpeg2000EbcotPass>());
        }

        mq = new Jpeg2000MqCoder(CONTEXT_COUNT);
        resetContexts();
        List<Jpeg2000EbcotPass> passes = new ArrayList<Jpeg2000EbcotPass>();
        int passType = 2;
        bitPlane = maxBitPlane;
        int pass = 0;
        while (bitPlane >= 0 && pass < passCount) {
            if (passType == 0 || (passType == 2 && pass == 0)) {
                clearVisited();
            }
            if (passType == 0) {
                encodeSignificancePropagation();
            } else if (passType == 1) {
                encodeMagnitudeRefinement();
            } else {
                encodeCleanup();
                if ((codeBlockStyle & 0x20) != 0) {
                    mq.encode(1, CONTEXT_UNIFORM);
                    mq.encode(0, CONTEXT_UNIFORM);
                    mq.encode(1, CONTEXT_UNIFORM);
                    mq.encode(0, CONTEXT_UNIFORM);
                }
            }
            // A pass may need the pending arithmetic byte and up to two termination bytes.
            int length = mq.passLengthEstimate();
            passes.add(new Jpeg2000EbcotPass(type(passType), bitPlane, length));
            if ((codeBlockStyle & 0x02) != 0) {
                resetContexts();
            }
            pass++;
            if (passType == 2) {
                passType = 0;
                bitPlane--;
            } else {
                passType++;
            }
        }
        byte[] data = mq.flush();
        for (int i = 0; i < passes.size(); i++) {
            Jpeg2000EbcotPass passInfo = passes.get(i);
            int length = Math.min(data.length, passInfo.byteLength());
            if (i > 0) {
                length = Math.max(length, passes.get(i - 1).byteLength());
            }
            passes.set(i, new Jpeg2000EbcotPass(passInfo.type(), passInfo.bitPlane(), length));
        }
        Jpeg2000EbcotPass last = passes.get(passes.size() - 1);
        passes.set(passes.size() - 1,
                new Jpeg2000EbcotPass(last.type(), last.bitPlane(), data.length));
        return new Jpeg2000EbcotEncodedBlock(width, height, orientation, codeBlockStyle,
                maxBitPlane, data, passes);
    }

    private Jpeg2000EbcotPass.Type type(int passType) {
        return passType == 0 ? Jpeg2000EbcotPass.Type.SIGNIFICANCE_PROPAGATION
                : passType == 1 ? Jpeg2000EbcotPass.Type.MAGNITUDE_REFINEMENT
                : Jpeg2000EbcotPass.Type.CLEANUP;
    }

    private void encodeSignificancePropagation() {
        for (int stripe = 0; stripe < height; stripe += 4) {
            for (int x = 0; x < width; x++) {
                for (int dy = 0; dy < 4 && stripe + dy < height; dy++) {
                    int y = stripe + dy;
                    int index = index(x, y);
                    int state = flags[index];
                    if ((state & SIGNIFICANT) != 0 || (state & SIG_NEIGHBORS) == 0) {
                        continue;
                    }
                    boolean significant = isSignificant(index);
                    mq.encode(significant ? 1 : 0, zeroContext(state));
                    flags[index] |= VISITED;
                    if (significant) {
                        setSignificant(x, y, index);
                    }
                }
            }
        }
    }

    private void encodeMagnitudeRefinement() {
        for (int stripe = 0; stripe < height; stripe += 4) {
            for (int x = 0; x < width; x++) {
                for (int dy = 0; dy < 4 && stripe + dy < height; dy++) {
                    int y = stripe + dy;
                    int index = index(x, y);
                    int state = flags[index];
                    if ((state & SIGNIFICANT) == 0 || (state & VISITED) != 0) {
                        continue;
                    }
                    mq.encode(magnitudeBit(index), magnitudeContext(state));
                    flags[index] |= REFINED;
                }
            }
        }
    }

    private void encodeCleanup() {
        for (int stripe = 0; stripe < height; stripe += 4) {
            for (int x = 0; x < width; x++) {
                boolean run = stripe + 3 < height;
                int runPosition = -1;
                if (run) {
                    for (int dy = 0; dy < 4; dy++) {
                        int state = flags[index(x, stripe + dy)];
                        if ((state & (VISITED | SIGNIFICANT | SIG_NEIGHBORS)) != 0) {
                            run = false;
                            break;
                        }
                        if (runPosition < 0 && isSignificant(index(x, stripe + dy))) {
                            runPosition = dy;
                        }
                    }
                }
                if (run) {
                    mq.encode(runPosition >= 0 ? 1 : 0, CONTEXT_RUN_LENGTH);
                    if (runPosition < 0) {
                        continue;
                    }
                    mq.encode((runPosition >>> 1) & 1, CONTEXT_UNIFORM);
                    mq.encode(runPosition & 1, CONTEXT_UNIFORM);
                }
                for (int dy = 0; dy < 4 && stripe + dy < height; dy++) {
                    if (run && dy < runPosition) {
                        continue;
                    }
                    int y = stripe + dy;
                    int index = index(x, y);
                    int state = flags[index];
                    if ((state & (VISITED | SIGNIFICANT)) != 0) {
                        flags[index] &= ~VISITED;
                        continue;
                    }
                    boolean significant = run && dy == runPosition;
                    if (!significant) {
                        significant = isSignificant(index);
                        mq.encode(significant ? 1 : 0, zeroContext(state));
                    }
                    if (significant) {
                        setSignificant(x, y, index);
                    }
                    flags[index] &= ~VISITED;
                }
            }
        }
    }

    private void setSignificant(int x, int y, int index) {
        int sign = values[index] < 0 ? 1 : 0;
        int lookup = signLookup(flags[index]);
        mq.encode(sign ^ SIGN_PREDICTIONS[lookup], SIGN_CONTEXTS[lookup]);
        if (sign != 0) {
            flags[index] |= SIGN;
        }
        flags[index] |= SIGNIFICANT;
        updateNeighbors(x, y, index);
    }

    private boolean isSignificant(int index) {
        long magnitude = Math.abs((long) values[index]);
        return ((magnitude >>> bitPlane) & 1L) != 0;
    }

    private int magnitudeBit(int index) {
        long magnitude = Math.abs((long) values[index]);
        return (int) ((magnitude >>> bitPlane) & 1L);
    }

    private void updateNeighbors(int x, int y, int index) {
        boolean negative = (flags[index] & SIGN) != 0;
        if (!isCausalBoundary(y)) {
            mark(index(x, y - 1), SIG_S, negative ? SIGN_S : 0);
            flags[index(x - 1, y - 1)] |= SIG_SE;
            flags[index(x + 1, y - 1)] |= SIG_SW;
        }
        mark(index(x, y + 1), SIG_N, negative ? SIGN_N : 0);
        mark(index(x - 1, y), SIG_E, negative ? SIGN_E : 0);
        mark(index(x + 1, y), SIG_W, negative ? SIGN_W : 0);
        flags[index(x - 1, y + 1)] |= SIG_NE;
        flags[index(x + 1, y + 1)] |= SIG_NW;
    }

    private void mark(int index, int significant, int sign) {
        flags[index] |= significant | sign;
    }

    private boolean isCausalBoundary(int y) {
        return (codeBlockStyle & 0x08) != 0 && (y & 3) == 0;
    }

    private void clearVisited() {
        for (int i = 0; i < flags.length; i++) {
            flags[i] &= ~VISITED;
        }
    }

    private void resetContexts() {
        mq.resetContexts();
        mq.setContextState(CONTEXT_UNIFORM, 46);
        mq.setContextState(CONTEXT_RUN_LENGTH, 3);
        mq.setContextState(0, 4);
    }

    private int index(int x, int y) {
        return (y + 1) * stride + x + 1;
    }

    private int zeroContext(int state) {
        int generator = orientation == 1 ? 2 : orientation == 2 ? 1 : orientation;
        return ZERO_CONTEXTS[generator][zeroLookup(state)];
    }

    static int magnitudeContext(int state) {
        return (state & REFINED) != 0 ? 16 : (state & SIG_NEIGHBORS) != 0 ? 15 : 14;
    }

    static int zeroLookup(int state) {
        int value = 0;
        if ((state & SIG_NW) != 0) value |= 1;
        if ((state & SIG_N) != 0) value |= 1 << 1;
        if ((state & SIG_NE) != 0) value |= 1 << 2;
        if ((state & SIG_W) != 0) value |= 1 << 3;
        if ((state & SIG_E) != 0) value |= 1 << 5;
        if ((state & SIG_SW) != 0) value |= 1 << 6;
        if ((state & SIG_S) != 0) value |= 1 << 7;
        if ((state & SIG_SE) != 0) value |= 1 << 8;
        return value;
    }

    static int signLookup(int state) {
        int value = 0;
        if ((state & SIG_W) != 0) value |= 1 << 3;
        if ((state & SIG_W) != 0 && (state & SIGN_W) != 0) value |= 1;
        if ((state & SIG_N) != 0) value |= 1 << 1;
        if ((state & SIG_N) != 0 && (state & SIGN_N) != 0) value |= 1 << 4;
        if ((state & SIG_E) != 0) value |= 1 << 5;
        if ((state & SIG_E) != 0 && (state & SIGN_E) != 0) value |= 1 << 2;
        if ((state & SIG_S) != 0) value |= 1 << 7;
        if ((state & SIG_S) != 0 && (state & SIGN_S) != 0) value |= 1 << 6;
        return value;
    }

    private static int[][] buildZeroContexts() {
        int[][] result = new int[4][512];
        for (int orientation = 0; orientation < 4; orientation++) {
            for (int lookup = 0; lookup < 512; lookup++) {
                result[orientation][lookup] = initZeroContext(lookup, orientation);
            }
        }
        return result;
    }

    private static int initZeroContext(int value, int orientation) {
        int horizontal = bit(value, 3) + bit(value, 5);
        int vertical = bit(value, 1) + bit(value, 7);
        int diagonal = bit(value, 0) + bit(value, 2) + bit(value, 8) + bit(value, 6);
        if (orientation == 2) {
            int temp = horizontal;
            horizontal = vertical;
            vertical = temp;
        }
        if (orientation == 3) {
            int hv = horizontal + vertical;
            return diagonal == 0 ? (hv == 0 ? 0 : hv == 1 ? 1 : 2)
                    : diagonal == 1 ? (hv == 0 ? 3 : hv == 1 ? 4 : 5)
                    : diagonal == 2 ? (hv == 0 ? 6 : 7) : 8;
        }
        if (horizontal == 0) {
            return vertical == 0 ? (diagonal == 0 ? 0 : diagonal == 1 ? 1 : 2)
                    : vertical == 1 ? 3 : 4;
        }
        return horizontal == 1 ? (vertical == 0 ? (diagonal == 0 ? 5 : 6) : 7) : 8;
    }

    private static int[] buildSignContexts() {
        int[] result = new int[256];
        for (int i = 0; i < result.length; i++) {
            result[i] = 9 + initSignContext(i);
        }
        return result;
    }

    private static int[] buildSignPredictions() {
        int[] result = new int[256];
        for (int i = 0; i < result.length; i++) {
            int horizontal = Math.min(positive(i, 5, 2) + positive(i, 3, 0), 1)
                    - Math.min(negative(i, 5, 2) + negative(i, 3, 0), 1);
            int vertical = Math.min(positive(i, 1, 4) + positive(i, 7, 6), 1)
                    - Math.min(negative(i, 1, 4) + negative(i, 7, 6), 1);
            result[i] = horizontal == 0 && vertical == 0 ? 0
                    : horizontal > 0 || (horizontal == 0 && vertical > 0) ? 0 : 1;
        }
        return result;
    }

    private static int initSignContext(int value) {
        int horizontal = Math.min(positive(value, 5, 2) + positive(value, 3, 0), 1)
                - Math.min(negative(value, 5, 2) + negative(value, 3, 0), 1);
        int vertical = Math.min(positive(value, 1, 4) + positive(value, 7, 6), 1)
                - Math.min(negative(value, 1, 4) + negative(value, 7, 6), 1);
        if (horizontal < 0) {
            horizontal = -horizontal;
            vertical = -vertical;
        }
        return horizontal == 0 ? (vertical == 0 ? 0 : 1)
                : vertical == -1 ? 2 : vertical == 0 ? 3 : 4;
    }

    private static int bit(int value, int bit) {
        return (value >>> bit) & 1;
    }

    private static int positive(int value, int sigBit, int signBit) {
        return ((value & (1 << sigBit)) != 0 && (value & (1 << signBit)) == 0) ? 1 : 0;
    }

    private static int negative(int value, int sigBit, int signBit) {
        return ((value & (1 << sigBit)) != 0 && (value & (1 << signBit)) != 0) ? 1 : 0;
    }
}
