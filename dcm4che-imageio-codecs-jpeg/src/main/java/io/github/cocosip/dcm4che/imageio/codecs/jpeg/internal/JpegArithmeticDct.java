package io.github.cocosip.dcm4che.imageio.codecs.jpeg.internal;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Annex F arithmetic coding of a sequential DCT coefficient stream. */
final class JpegArithmeticDct {
    private JpegArithmeticDct() {
    }

    static byte[] encode(int[][][] coefficients, int blocksX, int blocksY, int components)
            throws IOException {
        return encode(coefficients, blocksX, blocksY, components, 0, 1, 5);
    }

    static byte[] encode(int[][][] coefficients, int blocksX, int blocksY, int components,
            int dcL, int dcU, int acK) throws IOException {
        JpegQmCoder.Encoder coder = new JpegQmCoder.Encoder();
        int[] dcStats = new int[64];
        int[] acStats = new int[256];
        int[] fixed = {113};
        int[] previous = new int[components];
        int[] dcContext = new int[components];
        for (int by = 0; by < blocksY; by++) {
            for (int bx = 0; bx < blocksX; bx++) {
                for (int component = 0; component < components; component++) {
                    int[] block = coefficients[component][by * blocksX + bx];
                    encodeDc(coder, dcStats, previous, dcContext, component, block[0], dcL, dcU);
                    encodeAc(coder, acStats, fixed, block, acK);
                }
            }
        }
        return coder.finish();
    }

    static Encoded encodeSegments(int[][][] coefficients, int blocksX, int blocksY,
            int components, int restartInterval) throws IOException {
        return encodeSegments(coefficients, blocksX, blocksY, components, restartInterval,
                0, 1, 5);
    }

    static Encoded encodeSegments(int[][][] coefficients, int blocksX, int blocksY,
            int components, int restartInterval, int dcL, int dcU, int acK) throws IOException {
        List<byte[]> segments = new ArrayList<byte[]>();
        List<Integer> restartMarkers = new ArrayList<Integer>();
        int total = blocksX * blocksY;
        int start = 0;
        int restart = 0;
        while (start < total) {
            int end = restartInterval == 0 ? total : Math.min(total, start + restartInterval);
            JpegQmCoder.Encoder coder = new JpegQmCoder.Encoder();
            int[] dcStats = new int[64];
            int[] acStats = new int[256];
            int[] fixed = {113};
            int[] previous = new int[components];
            int[] dcContext = new int[components];
            for (int blockIndex = start; blockIndex < end; blockIndex++) {
                for (int component = 0; component < components; component++) {
                    encodeDc(coder, dcStats, previous, dcContext, component,
                            coefficients[component][blockIndex][0], dcL, dcU);
                    encodeAc(coder, acStats, fixed, coefficients[component][blockIndex], acK);
                }
            }
            segments.add(coder.finish());
            start = end;
            if (start < total) {
                restartMarkers.add(0xd0 + restart);
                restart = (restart + 1) & 7;
            }
        }
        return new Encoded(segments, restartMarkers);
    }

    static byte[] encodeDc(int[][][] coefficients, int blocksX, int blocksY, int components)
            throws IOException {
        return encodeDc(coefficients, blocksX, blocksY, components, 0, 1);
    }

    static byte[] encodeDc(int[][][] coefficients, int blocksX, int blocksY, int components,
            int dcL, int dcU) throws IOException {
        JpegQmCoder.Encoder coder = new JpegQmCoder.Encoder();
        int[] dcStats = new int[64];
        int[] previous = new int[components];
        int[] dcContext = new int[components];
        for (int by = 0; by < blocksY; by++) {
            for (int bx = 0; bx < blocksX; bx++) {
                for (int component = 0; component < components; component++) {
                    encodeDc(coder, dcStats, previous, dcContext, component,
                            coefficients[component][by * blocksX + bx][0], dcL, dcU);
                }
            }
        }
        return coder.finish();
    }

    static byte[] encodeAc(int[][][] coefficients, int blocksX, int blocksY, int component)
            throws IOException {
        return encodeAc(coefficients, blocksX, blocksY, component, 5);
    }

    static byte[] encodeAc(int[][][] coefficients, int blocksX, int blocksY, int component,
            int acK) throws IOException {
        JpegQmCoder.Encoder coder = new JpegQmCoder.Encoder();
        int[] acStats = new int[256];
        int[] fixed = {113};
        for (int by = 0; by < blocksY; by++) {
            for (int bx = 0; bx < blocksX; bx++) {
                encodeAc(coder, acStats, fixed,
                        coefficients[component][by * blocksX + bx], acK);
            }
        }
        return coder.finish();
    }

    static int[][][] decode(byte[] entropy, int blocksX, int blocksY, int components)
            throws IOException {
        return decode(entropy, blocksX, blocksY, components, 0, 1, 5);
    }

    static int[][][] decode(byte[] entropy, int blocksX, int blocksY, int components,
            int dcL, int dcU, int acK) throws IOException {
        JpegQmCoder.Decoder coder = new JpegQmCoder.Decoder(entropy);
        int[] dcStats = new int[64];
        int[] acStats = new int[256];
        int[] fixed = {113};
        int[] previous = new int[components];
        int[] dcContext = new int[components];
        int[][][] coefficients = new int[components][blocksX * blocksY][64];
        for (int by = 0; by < blocksY; by++) {
            for (int bx = 0; bx < blocksX; bx++) {
                for (int component = 0; component < components; component++) {
                    int[] block = coefficients[component][by * blocksX + bx];
                    int offset = dcContext[component];
                    if (coder.decode(dcStats, offset) != 0) {
                        int sign = coder.decode(dcStats, offset + 1);
                        offset += 2 + sign;
                        int magnitude = coder.decode(dcStats, offset);
                        if (magnitude != 0) {
                            offset = 20;
                            while (coder.decode(dcStats, offset) != 0) {
                                if ((magnitude <<= 1) == 0x8000) {
                                    throw new JpegException("arithmetic DC magnitude overflow");
                                }
                                offset++;
                            }
                        }
                        if (magnitude < (1 << dcL >> 1)) {
                            dcContext[component] = 0;
                        } else if (magnitude > (1 << dcU >> 1)) {
                            dcContext[component] = 12 + sign * 4;
                        } else {
                            dcContext[component] = 4 + sign * 4;
                        }
                        int value = magnitude;
                        offset += 14;
                        int mask = magnitude;
                        while ((mask >>>= 1) != 0) {
                            int bit = coder.decode(dcStats, offset++);
                            if (bit != 0) {
                                value |= mask;
                            }
                        }
                        value++;
                        if (sign != 0) {
                            value = -value;
                        }
                        previous[component] += value;
                    } else {
                        dcContext[component] = 0;
                    }
                    block[0] = previous[component];
                    decodeAc(coder, acStats, fixed, block, acK);
                }
            }
        }
        return coefficients;
    }

    static int[][][] decodeSegments(List<byte[]> segments, int blocksX, int blocksY,
            int components, int restartInterval, List<Integer> restartMarkers) throws IOException {
        return decodeSegments(segments, blocksX, blocksY, components, restartInterval,
                restartMarkers, 0, 1, 5);
    }

    static int[][][] decodeSegments(List<byte[]> segments, int blocksX, int blocksY,
            int components, int restartInterval, List<Integer> restartMarkers,
            int dcL, int dcU, int acK) throws IOException {
        if (segments == null || segments.isEmpty()) {
            throw new JpegException("arithmetic DCT scan has no entropy data");
        }
        int[][][] coefficients = new int[components][blocksX * blocksY][64];
        int total = blocksX * blocksY;
        int blockIndex = 0;
        int restart = 0;
        for (int segmentIndex = 0; segmentIndex < segments.size() && blockIndex < total;
                segmentIndex++) {
            int end = restartInterval == 0 ? total
                    : Math.min(total, blockIndex + restartInterval);
            JpegQmCoder.Decoder coder = new JpegQmCoder.Decoder(segments.get(segmentIndex));
            int[] dcStats = new int[64];
            int[] acStats = new int[256];
            int[] fixed = {113};
            int[] previous = new int[components];
            int[] dcContext = new int[components];
            while (blockIndex < end) {
                for (int component = 0; component < components; component++) {
                    decodeDc(coder, dcStats, previous, dcContext, component,
                            coefficients[component][blockIndex], dcL, dcU);
                    decodeAc(coder, acStats, fixed, coefficients[component][blockIndex], acK);
                }
                blockIndex++;
            }
            if (restartInterval != 0 && blockIndex < total) {
                if (restartMarkers == null || segmentIndex >= restartMarkers.size()
                        || restartMarkers.get(segmentIndex) != 0xd0 + restart) {
                    throw new JpegException("arithmetic DCT restart marker sequence is invalid");
                }
                restart = (restart + 1) & 7;
            }
        }
        if (blockIndex != total || (restartInterval == 0 && restartMarkers != null
                && !restartMarkers.isEmpty())) {
            throw new JpegException("arithmetic DCT scan does not match restart interval");
        }
        return coefficients;
    }

    static final class Encoded {
        final List<byte[]> segments;
        final List<Integer> restartMarkers;

        Encoded(List<byte[]> segments, List<Integer> restartMarkers) {
            this.segments = segments;
            this.restartMarkers = restartMarkers;
        }
    }

    static int[][][] decodeDc(byte[] entropy, int blocksX, int blocksY, int components)
            throws IOException {
        return decodeDc(entropy, blocksX, blocksY, components, 0, 1);
    }

    static int[][][] decodeDc(byte[] entropy, int blocksX, int blocksY, int components,
            int dcL, int dcU) throws IOException {
        JpegQmCoder.Decoder coder = new JpegQmCoder.Decoder(entropy);
        int[] dcStats = new int[64];
        int[] previous = new int[components];
        int[] dcContext = new int[components];
        int[][][] coefficients = new int[components][blocksX * blocksY][64];
        for (int by = 0; by < blocksY; by++) {
            for (int bx = 0; bx < blocksX; bx++) {
                for (int component = 0; component < components; component++) {
                    decodeDc(coder, dcStats, previous, dcContext, component,
                            coefficients[component][by * blocksX + bx], dcL, dcU);
                }
            }
        }
        return coefficients;
    }

    static void decodeAc(byte[] entropy, int blocksX, int blocksY, int component,
            int[][][] coefficients) throws IOException {
        decodeAc(entropy, blocksX, blocksY, component, coefficients, 5);
    }

    static void decodeAc(byte[] entropy, int blocksX, int blocksY, int component,
            int[][][] coefficients, int acK) throws IOException {
        JpegQmCoder.Decoder coder = new JpegQmCoder.Decoder(entropy);
        int[] acStats = new int[256];
        int[] fixed = {113};
        for (int by = 0; by < blocksY; by++) {
            for (int bx = 0; bx < blocksX; bx++) {
                decodeAc(coder, acStats, fixed,
                        coefficients[component][by * blocksX + bx], acK);
            }
        }
    }

    private static void encodeDc(JpegQmCoder.Encoder coder, int[] dcStats, int[] previous,
            int[] dcContext, int component, int value, int dcL, int dcU) {
        int difference = value - previous[component];
        int offset = dcContext[component];
        if (difference == 0) {
            coder.encode(dcStats, offset, 0);
            dcContext[component] = 0;
            return;
        }
        previous[component] = value;
        coder.encode(dcStats, offset, 1);
        int sign = difference < 0 ? 1 : 0;
        int magnitude = Math.abs(difference);
        coder.encode(dcStats, offset + 1, sign);
        offset += 2 + sign;
        int remaining = magnitude - 1;
        int m = 0;
        if (remaining != 0) {
            coder.encode(dcStats, offset, 1);
            m = 1;
            int probe = remaining;
            offset = 20;
            while ((probe >>>= 1) != 0) {
                coder.encode(dcStats, offset++, 1);
                m <<= 1;
            }
        }
        coder.encode(dcStats, offset, 0);
        if (m < (1 << dcL >> 1)) {
            dcContext[component] = 0;
        } else if (m > (1 << dcU >> 1)) {
            dcContext[component] = 12 + sign * 4;
        } else {
            dcContext[component] = 4 + sign * 4;
        }
        offset += 14;
        while ((m >>>= 1) != 0) {
            coder.encode(dcStats, offset++, (m & remaining) != 0 ? 1 : 0);
        }
    }

    private static void decodeDc(JpegQmCoder.Decoder coder, int[] dcStats, int[] previous,
            int[] dcContext, int component, int[] block, int dcL, int dcU) throws IOException {
        int offset = dcContext[component];
        if (coder.decode(dcStats, offset) == 0) {
            dcContext[component] = 0;
            block[0] = previous[component];
            return;
        }
        int sign = coder.decode(dcStats, offset + 1);
        offset += 2 + sign;
        int m = coder.decode(dcStats, offset);
        if (m != 0) {
            offset = 20;
            while (coder.decode(dcStats, offset) != 0) {
                if ((m <<= 1) == 0x8000) {
                    throw new JpegException("arithmetic DC magnitude overflow");
                }
                offset++;
            }
        }
        if (m < (1 << dcL >> 1)) {
            dcContext[component] = 0;
        } else if (m > (1 << dcU >> 1)) {
            dcContext[component] = 12 + sign * 4;
        } else {
            dcContext[component] = 4 + sign * 4;
        }
        int value = m;
        offset += 14;
        int mask = m;
        while ((mask >>>= 1) != 0) {
            if (coder.decode(dcStats, offset++) != 0) {
                value |= mask;
            }
        }
        value++;
        previous[component] += sign == 0 ? value : -value;
        block[0] = previous[component];
    }

    private static void encodeAc(JpegQmCoder.Encoder coder, int[] stats, int[] fixed, int[] block,
            int acK) {
        int last = 0;
        for (int zig = 63; zig > 0; zig--) {
            int value = block[JpegZigZag.ORDER[zig]];
            if (value != 0) {
                last = zig;
                break;
            }
        }
        int k = 1;
        while (k <= last) {
            int base = 3 * (k - 1);
            coder.encode(stats, base, 0);
            int value;
            do {
                value = block[JpegZigZag.ORDER[k++]];
                if (value == 0) {
                    coder.encode(stats, base + 1, 0);
                    base += 3;
                }
            } while (value == 0 && k <= last);
            if (value == 0) {
                break;
            }
            int coefficient = k - 1;
            coder.encode(stats, base + 1, 1);
            coder.encode(fixed, 0, value < 0 ? 1 : 0);
            value = Math.abs(value);
            int magnitude = 0;
            int remaining = value - 1;
            int magnitudeContext = base + 2;
            if (remaining != 0) {
                coder.encode(stats, magnitudeContext, 1);
                magnitude = 1;
                int probe = remaining;
                if ((probe >>>= 1) != 0) {
                    coder.encode(stats, magnitudeContext, 1);
                    magnitude <<= 1;
                    magnitudeContext = coefficient <= acK ? 189 : 217;
                    while ((probe >>>= 1) != 0) {
                        coder.encode(stats, magnitudeContext++, 1);
                        magnitude <<= 1;
                    }
                }
            }
            coder.encode(stats, magnitudeContext, 0);
            magnitudeContext += 14;
            while ((magnitude >>>= 1) != 0) {
                coder.encode(stats, magnitudeContext++, (magnitude & remaining) != 0 ? 1 : 0);
            }
        }
        if (k <= 63) {
            coder.encode(stats, 3 * (k - 1), 1);
        }
    }

    private static void decodeAc(JpegQmCoder.Decoder coder, int[] stats, int[] fixed, int[] block,
            int acK) throws IOException {
        for (int k = 1; k <= 63; k++) {
            int base = 3 * (k - 1);
            if (coder.decode(stats, base) != 0) {
                break;
            }
            while (coder.decode(stats, base + 1) == 0) {
                base += 3;
                if (++k > 63) {
                    throw new JpegException("arithmetic AC spectral overflow");
                }
            }
            int sign = coder.decode(fixed, 0);
            base += 2;
            int magnitudeContext = base;
            int magnitude = coder.decode(stats, magnitudeContext);
            if (magnitude != 0 && coder.decode(stats, magnitudeContext) != 0) {
                magnitude <<= 1;
                magnitudeContext = k <= acK ? 189 : 217;
                while (coder.decode(stats, magnitudeContext) != 0) {
                    if ((magnitude <<= 1) == 0x8000) {
                        throw new JpegException("arithmetic AC magnitude overflow");
                    }
                    magnitudeContext++;
                }
            }
            int value = magnitude;
            magnitudeContext += 14;
            int mask = magnitude;
            while ((mask >>>= 1) != 0) {
                int bit = coder.decode(stats, magnitudeContext++);
                if (bit != 0) {
                    value |= mask;
                }
            }
            value++;
            block[JpegZigZag.ORDER[k]] = sign == 0 ? value : -value;
        }
    }
}
