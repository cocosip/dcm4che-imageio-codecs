package io.github.cocosip.dcm4che.imageio.codecs.jpeg.internal;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;

/** Progressive DCT Huffman JPEG codec for 8-bit 1x1 monochrome/RGB frames. */
public final class ProgressiveJpegCodec {
    private ProgressiveJpegCodec() {
    }

    public static byte[] encode(JpegFrame frame) throws IOException {
        if (frame.precision() != 8 || (frame.components() != 1 && frame.components() != 3)) {
            throw new JpegException("Progressive JPEG requires 8-bit monochrome or RGB samples");
        }
        int blocksX = (frame.width() + 7) / 8;
        int blocksY = (frame.height() + 7) / 8;
        int[][] coefficients = createCoefficients(frame, blocksX, blocksY);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        MemoryCacheImageOutputStream output = new MemoryCacheImageOutputStream(bytes);
        output.write(0xff);
        output.write(0xd8);
        writeQuantization(output, 0, JpegTables.standardLuminanceQuantization());
        if (frame.components() == 3) {
            writeQuantization(output, 1, JpegTables.standardChrominanceQuantization());
        }
        writeFrameHeader(output, frame);
        writeHuffmanTables(output, frame.components());
        writeScanHeader(output, frame.components(), 0, 0, 0, 0);
        encodeDcScan(output, coefficients, blocksX, blocksY, frame.components());
        writeScanHeader(output, frame.components(), 1, 63, 0, 0);
        encodeAcScan(output, coefficients, blocksX, blocksY, frame.components());
        output.write(0xff);
        output.write(0xd9);
        output.flush();
        return bytes.toByteArray();
    }

    public static JpegFrame decode(byte[] data) throws IOException {
        if (data == null || data.length < 4) {
            throw new JpegException("Progressive JPEG frame is truncated");
        }
        MemoryCacheImageInputStream input = new MemoryCacheImageInputStream(
                new ByteArrayInputStream(data));
        if (input.read() != 0xff || input.read() != 0xd8) {
            throw new JpegException("Progressive JPEG frame does not start with SOI");
        }
        Map<Integer, QuantizationTable> quant = new HashMap<Integer, QuantizationTable>();
        Map<Integer, HuffmanTable> huffman = new HashMap<Integer, HuffmanTable>();
        int width = 0;
        int height = 0;
        int components = 0;
        int precision = 0;
        int[] componentIds = null;
        int[] componentQuant = null;
        int[][] coefficients = null;
        JpegMarkerReader markers = new JpegMarkerReader(input);
        boolean seenScan = false;
        while (true) {
            JpegMarker marker = markers.next();
            int code = marker.code();
            byte[] payload = marker.payload();
            if (code == 0xdb) {
                parseQuantization(payload, quant);
            } else if (code == 0xc4) {
                parseHuffman(payload, huffman);
            } else if (code == 0xc2) {
                FrameHeader frame = parseFrameHeader(payload);
                width = frame.width;
                height = frame.height;
                precision = frame.precision;
                components = frame.components;
                componentIds = frame.componentIds;
                componentQuant = frame.quantization;
                coefficients = new int[((width + 7) / 8) * ((height + 7) / 8)
                        * components][64];
            } else if (code == 0xda) {
                if (coefficients == null || !quant.containsKey(0) || !huffman.containsKey(0)) {
                    throw new JpegException("Progressive JPEG scan is missing frame or tables");
                }
                ScanHeader scan = parseScanHeader(payload, componentIds, components);
                byte[] entropy = JpegMarkerReader.readEntropyBytes(input);
                decodeScan(scan, entropy, coefficients, width, height, components, huffman);
                seenScan = true;
            } else if ((code >= 0xe0 && code <= 0xef) || code == 0xfe) {
                // APPn and COM metadata are not part of the progressive coefficient state.
            } else if (code == 0xd9) {
                if (!seenScan || coefficients == null) {
                    throw new JpegException("Progressive JPEG is missing scan data");
                }
                return reconstruct(width, height, components, precision, coefficients,
                        componentQuant, quant);
            } else {
                throw new JpegException("unsupported progressive JPEG marker: 0x"
                        + Integer.toHexString(code));
            }
        }
    }

    private static int[][] createCoefficients(JpegFrame frame, int blocksX, int blocksY) {
        int[][] coefficients = new int[blocksX * blocksY * frame.components()][64];
        QuantizationTable[] quant = {
            QuantizationTable.of(JpegTables.standardLuminanceQuantization()),
            QuantizationTable.of(JpegTables.standardChrominanceQuantization())};
        for (int by = 0; by < blocksY; by++) {
            for (int bx = 0; bx < blocksX; bx++) {
                for (int component = 0; component < frame.components(); component++) {
                    double[] block = new double[64];
                    for (int y = 0; y < 8; y++) {
                        for (int x = 0; x < 8; x++) {
                            int sourceX = Math.min(frame.width() - 1, bx * 8 + x);
                            int sourceY = Math.min(frame.height() - 1, by * 8 + y);
                            block[y * 8 + x] = frame.sample(sourceX, sourceY, component) - 128;
                        }
                    }
                    double[] transformed = JpegDct.forward(block);
                    int[] target = coefficients[indexOfBlock(bx, by, blocksX, frame.components(), component)];
                    for (int i = 0; i < 64; i++) {
                        target[i] = (int) Math.round(
                                transformed[i] / quant[component == 0 ? 0 : 1].get(
                                        JpegZigZag.positionOf(i)));
                    }
                }
            }
        }
        return coefficients;
    }

    private static void encodeDcScan(ImageOutputStream output, int[][] coefficients,
            int blocksX, int blocksY, int components) throws IOException {
        BitWriter bits = new BitWriter(output);
        HuffmanTable[] dc = {JpegTables.standardLuminanceDc(), JpegTables.standardChrominanceDc()};
        int[] previous = new int[components];
        for (int by = 0; by < blocksY; by++) {
            for (int bx = 0; bx < blocksX; bx++) {
                for (int component = 0; component < components; component++) {
                    int value = coefficients[indexOfBlock(bx, by, blocksX, components, component)][0];
                    int difference = value - previous[component];
                    previous[component] = value;
                    int size = category(difference);
                    HuffmanCodec.encodeSymbol(bits, dc[component == 0 ? 0 : 1], size);
                    writeAmplitude(bits, difference, size);
                }
            }
        }
        bits.flush();
    }

    private static void encodeAcScan(ImageOutputStream output, int[][] coefficients,
            int blocksX, int blocksY, int components) throws IOException {
        BitWriter bits = new BitWriter(output);
        HuffmanTable[] ac = {JpegTables.standardLuminanceAc(), JpegTables.standardChrominanceAc()};
        for (int by = 0; by < blocksY; by++) {
            for (int bx = 0; bx < blocksX; bx++) {
                for (int component = 0; component < components; component++) {
                    int[] block = coefficients[indexOfBlock(bx, by, blocksX, components, component)];
                    int run = 0;
                    for (int zig = 1; zig < 64; zig++) {
                        int value = block[JpegZigZag.ORDER[zig]];
                        if (value == 0) {
                            run++;
                            continue;
                        }
                        while (run >= 16) {
                            HuffmanCodec.encodeSymbol(bits, ac[component == 0 ? 0 : 1], 0xf0);
                            run -= 16;
                        }
                        int size = category(value);
                        HuffmanCodec.encodeSymbol(bits, ac[component == 0 ? 0 : 1],
                                (run << 4) | size);
                        writeAmplitude(bits, value, size);
                        run = 0;
                    }
                    if (run != 0) {
                        HuffmanCodec.encodeSymbol(bits, ac[component == 0 ? 0 : 1], 0);
                    }
                }
            }
        }
        bits.flush();
    }

    private static void decodeScan(ScanHeader scan, byte[] entropy, int[][] coefficients,
            int width, int height, int components, Map<Integer, HuffmanTable> huffman)
            throws IOException {
        int blocksX = (width + 7) / 8;
        int blocksY = (height + 7) / 8;
        MemoryCacheImageInputStream entropyInput = new MemoryCacheImageInputStream(
                new ByteArrayInputStream(entropy));
        BitReader bits = new BitReader(entropyInput);
        int[] previous = new int[components];
        ProgressiveState state = new ProgressiveState();
        for (int by = 0; by < blocksY; by++) {
            for (int bx = 0; bx < blocksX; bx++) {
                for (int selector = 0; selector < scan.components.length; selector++) {
                    int component = scan.components[selector];
                    int[] block = coefficients[indexOfBlock(bx, by, blocksX, components, component)];
                    if (scan.ss == 0 && scan.se == 0) {
                        if (scan.ah == 0) {
                            HuffmanTable dc = requireTable(huffman, scan.dcTables[selector]);
                            int size = HuffmanCodec.decodeSymbol(bits, dc);
                            int difference = readAmplitude(bits, size) << scan.al;
                            block[0] = previous[component] + difference;
                            previous[component] = block[0];
                        } else {
                            if (scan.ah != scan.al + 1) {
                                throw new JpegException("invalid progressive JPEG DC refinement point");
                            }
                            if (bits.readBits(1) != 0) {
                                block[0] = refineCoefficient(block[0], scan.al);
                            }
                        }
                    } else {
                        HuffmanTable ac = requireTable(huffman, 0x10 | scan.acTables[selector]);
                        if (scan.ah == 0) {
                            decodeAcFirst(bits, ac, block, scan, state);
                        } else {
                            if (scan.ah != scan.al + 1) {
                                throw new JpegException("invalid progressive JPEG AC refinement point");
                            }
                            decodeAcRefinement(bits, ac, block, scan, state);
                        }
                    }
                }
            }
        }
        if (state.eobRun != 0) {
            throw new JpegException("progressive JPEG EOB run exceeds scan");
        }
    }

    private static HuffmanTable requireTable(Map<Integer, HuffmanTable> huffman, int id)
            throws IOException {
        HuffmanTable table = huffman.get(id);
        if (table == null) {
            throw new JpegException("progressive JPEG scan references missing Huffman table");
        }
        return table;
    }

    private static void decodeAcFirst(BitReader bits, HuffmanTable ac, int[] block,
            ScanHeader scan, ProgressiveState state) throws IOException {
        if (state.eobRun != 0) {
            state.eobRun--;
            return;
        }
        int zig = scan.ss;
        while (zig <= scan.se) {
            int symbol = HuffmanCodec.decodeSymbol(bits, ac);
            int run = symbol >>> 4;
            int size = symbol & 0x0f;
            if (size == 0) {
                if (run == 0) {
                    return;
                }
                if (run == 15) {
                    zig += 16;
                    continue;
                }
                state.eobRun = 1 << run;
                if (run != 0) {
                    state.eobRun += bits.readBits(run);
                }
                state.eobRun--;
                return;
            }
            zig += run;
            if (zig > scan.se) {
                throw new JpegException("invalid progressive JPEG AC run");
            }
            block[JpegZigZag.ORDER[zig++]] = readAmplitude(bits, size) << scan.al;
        }
    }

    private static void decodeAcRefinement(BitReader bits, HuffmanTable ac, int[] block,
            ScanHeader scan, ProgressiveState state) throws IOException {
        int point = 1 << scan.al;
        int zig = scan.ss;
        if (state.eobRun != 0) {
            refineExistingCoefficients(bits, block, zig, scan.se, point);
            state.eobRun--;
            return;
        }
        while (zig <= scan.se) {
            int symbol = HuffmanCodec.decodeSymbol(bits, ac);
            int run = symbol >>> 4;
            int size = symbol & 0x0f;
            if (size == 0) {
                if (run == 15) {
                    int zeros = 16;
                    while (zig <= scan.se && zeros > 0) {
                        int index = JpegZigZag.ORDER[zig++];
                        if (block[index] == 0) {
                            zeros--;
                        } else {
                            block[index] = refineCoefficient(block[index], scan.al,
                                    bits.readBits(1));
                        }
                    }
                    if (zeros != 0) {
                        throw new JpegException("invalid progressive JPEG AC refinement run");
                    }
                    continue;
                }
                state.eobRun = 1 << run;
                if (run != 0) {
                    state.eobRun += bits.readBits(run);
                }
                refineExistingCoefficients(bits, block, zig, scan.se, point);
                state.eobRun--;
                return;
            }
            if (size != 1) {
                throw new JpegException("invalid progressive JPEG AC refinement size");
            }
            int zeros = run;
            while (zig <= scan.se) {
                int index = JpegZigZag.ORDER[zig++];
                if (block[index] == 0) {
                    if (zeros == 0) {
                        int sign = bits.readBits(1);
                        block[index] = sign == 0 ? -point : point;
                        break;
                    }
                    zeros--;
                } else {
                    block[index] = refineCoefficient(block[index], scan.al, bits.readBits(1));
                }
            }
            if (zeros != 0) {
                throw new JpegException("invalid progressive JPEG AC refinement run");
            }
        }
    }

    private static void refineExistingCoefficients(BitReader bits, int[] block, int from, int to,
            int point) throws IOException {
        for (int zig = from; zig <= to; zig++) {
            int index = JpegZigZag.ORDER[zig];
            if (block[index] != 0) {
                block[index] = refineCoefficient(block[index], point, bits.readBits(1));
            }
        }
    }

    private static int refineCoefficient(int coefficient, int al) throws IOException {
        return refineCoefficient(coefficient, 1 << al, 1);
    }

    private static int refineCoefficient(int coefficient, int point, int bit) {
        if (bit == 0) {
            return coefficient;
        }
        return coefficient < 0 ? coefficient - point : coefficient + point;
    }

    private static JpegFrame reconstruct(int width, int height, int components, int precision,
            int[][] coefficients, int[] componentQuant, Map<Integer, QuantizationTable> quant)
            throws IOException {
        int blocksX = (width + 7) / 8;
        int[] samples = new int[width * height * components];
        for (int by = 0; by < (height + 7) / 8; by++) {
            for (int bx = 0; bx < blocksX; bx++) {
                for (int component = 0; component < components; component++) {
                    int tableId = componentQuant[component];
                    QuantizationTable table = quant.get(tableId);
                    if (table == null) {
                        throw new JpegException("progressive JPEG references missing quantization table");
                    }
                    int[] block = coefficients[indexOfBlock(bx, by, blocksX, components, component)];
                    double[] restored = new double[64];
                    for (int i = 0; i < 64; i++) {
                        restored[i] = block[i] * table.get(JpegZigZag.positionOf(i));
                    }
                    double[] pixels = JpegDct.inverse(restored);
                    for (int y = 0; y < 8 && by * 8 + y < height; y++) {
                        for (int x = 0; x < 8 && bx * 8 + x < width; x++) {
                            int value = (int) Math.round(pixels[y * 8 + x] + (1 << (precision - 1)));
                            samples[((by * 8 + y) * width + bx * 8 + x) * components + component]
                                    = Math.max(0, Math.min((1 << precision) - 1, value));
                        }
                    }
                }
            }
        }
        return JpegFrame.of(width, height, components, samples, precision);
    }

    private static FrameHeader parseFrameHeader(byte[] payload) throws IOException {
        if (payload.length < 6 || (payload[0] & 0xff) != 8) {
            throw new JpegException("Progressive JPEG requires 8-bit SOF2");
        }
        int height = u16(payload, 1);
        int width = u16(payload, 3);
        int components = payload[5] & 0xff;
        if (width == 0 || height == 0 || (components != 1 && components != 3)
                || payload.length != 6 + components * 3) {
            throw new JpegException("invalid progressive JPEG SOF2");
        }
        int[] ids = new int[components];
        int[] quant = new int[components];
        for (int i = 0; i < components; i++) {
            int offset = 6 + i * 3;
            ids[i] = payload[offset] & 0xff;
            if (payload[offset + 1] != 0x11) {
                throw new JpegException("progressive JPEG sampling factors other than 1x1 are unsupported");
            }
            quant[i] = payload[offset + 2] & 0x0f;
        }
        return new FrameHeader(width, height, components, ids, quant);
    }

    private static ScanHeader parseScanHeader(byte[] payload, int[] componentIds, int components)
            throws IOException {
        if (payload.length < 4 || (payload[0] & 0xff) == 0
                || payload.length != 4 + (payload[0] & 0xff) * 2) {
            throw new JpegException("invalid progressive JPEG SOS header");
        }
        int count = payload[0] & 0xff;
        int[] selected = new int[count];
        int[] dc = new int[count];
        int[] ac = new int[count];
        for (int i = 0; i < count; i++) {
            int id = payload[1 + i * 2] & 0xff;
            selected[i] = componentIndex(componentIds, id);
            int tables = payload[2 + i * 2] & 0xff;
            dc[i] = tables >>> 4;
            ac[i] = tables & 0x0f;
        }
        int ss = payload[payload.length - 3] & 0xff;
        int se = payload[payload.length - 2] & 0xff;
        int ahAl = payload[payload.length - 1] & 0xff;
        return new ScanHeader(selected, dc, ac, ss, se, ahAl >>> 4, ahAl & 0x0f);
    }

    private static int componentIndex(int[] ids, int id) throws IOException {
        for (int i = 0; i < ids.length; i++) {
            if (ids[i] == id) {
                return i;
            }
        }
        throw new JpegException("progressive JPEG scan references unknown component");
    }

    private static void writeQuantization(ImageOutputStream output, int id, int[] values)
            throws IOException {
        byte[] payload = new byte[65];
        payload[0] = (byte) id;
        for (int i = 0; i < 64; i++) {
            payload[i + 1] = (byte) values[i];
        }
        JpegMarkerWriter.write(output, 0xdb, payload);
    }

    private static void writeFrameHeader(ImageOutputStream output, JpegFrame frame)
            throws IOException {
        byte[] payload = new byte[6 + frame.components() * 3];
        payload[0] = 8;
        payload[1] = (byte) (frame.height() >>> 8);
        payload[2] = (byte) frame.height();
        payload[3] = (byte) (frame.width() >>> 8);
        payload[4] = (byte) frame.width();
        payload[5] = (byte) frame.components();
        for (int i = 0; i < frame.components(); i++) {
            payload[6 + i * 3] = (byte) (i + 1);
            payload[7 + i * 3] = 0x11;
            payload[8 + i * 3] = (byte) (i == 0 ? 0 : 1);
        }
        JpegMarkerWriter.write(output, 0xc2, payload);
    }

    private static void writeHuffmanTables(ImageOutputStream output, int components)
            throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        writeHuffmanDefinition(bytes, 0x00, JpegTables.luminanceDcBits(), JpegTables.luminanceDcValues());
        writeHuffmanDefinition(bytes, 0x10, JpegTables.luminanceAcBits(), JpegTables.luminanceAcValues());
        if (components == 3) {
            writeHuffmanDefinition(bytes, 0x01, JpegTables.chrominanceDcBits(), JpegTables.chrominanceDcValues());
            writeHuffmanDefinition(bytes, 0x11, JpegTables.chrominanceAcBits(), JpegTables.chrominanceAcValues());
        }
        JpegMarkerWriter.write(output, 0xc4, bytes.toByteArray());
    }

    private static void writeHuffmanDefinition(ByteArrayOutputStream output, int id,
            int[] counts, int[] values) {
        output.write(id);
        for (int count : counts) {
            output.write(count);
        }
        for (int value : values) {
            output.write(value);
        }
    }

    private static void writeScanHeader(ImageOutputStream output, int components, int ss, int se,
            int ah, int al) throws IOException {
        byte[] payload = new byte[4 + components * 2];
        payload[0] = (byte) components;
        for (int i = 0; i < components; i++) {
            payload[1 + i * 2] = (byte) (i + 1);
            payload[2 + i * 2] = (byte) (i == 0 ? 0 : 0x11);
        }
        payload[payload.length - 3] = (byte) ss;
        payload[payload.length - 2] = (byte) se;
        payload[payload.length - 1] = (byte) ((ah << 4) | al);
        JpegMarkerWriter.write(output, 0xda, payload);
    }

    private static void parseQuantization(byte[] payload, Map<Integer, QuantizationTable> tables)
            throws IOException {
        int offset = 0;
        while (offset < payload.length) {
            int specification = payload[offset++] & 0xff;
            if ((specification >>> 4) != 0 || offset + 64 > payload.length) {
                throw new JpegException("unsupported progressive JPEG quantization table");
            }
            int[] values = new int[64];
            for (int i = 0; i < 64; i++) {
                values[i] = payload[offset++] & 0xff;
            }
            tables.put(specification & 0x0f, QuantizationTable.of(values));
        }
    }

    private static void parseHuffman(byte[] payload, Map<Integer, HuffmanTable> tables)
            throws IOException {
        int offset = 0;
        while (offset < payload.length) {
            int specification = payload[offset++] & 0xff;
            if (offset + 16 > payload.length) {
                throw new JpegException("truncated progressive JPEG Huffman table");
            }
            int[] counts = new int[16];
            int total = 0;
            for (int i = 0; i < counts.length; i++) {
                counts[i] = payload[offset++] & 0xff;
                total += counts[i];
            }
            if (offset + total > payload.length) {
                throw new JpegException("truncated progressive JPEG Huffman values");
            }
            int[] values = new int[total];
            for (int i = 0; i < total; i++) {
                values[i] = payload[offset++] & 0xff;
            }
            tables.put(specification, HuffmanTable.fromDefinition(counts, values));
        }
    }

    private static int indexOfBlock(int bx, int by, int blocksX, int components, int component) {
        return ((by * blocksX) + bx) * components + component;
    }

    private static int u16(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xff) << 8) | (bytes[offset + 1] & 0xff);
    }

    private static final class ProgressiveState {
        private int eobRun;
    }

    private static int category(int value) {
        int magnitude = Math.abs(value);
        int result = 0;
        while (magnitude != 0) {
            result++;
            magnitude >>>= 1;
        }
        return result;
    }

    private static void writeAmplitude(BitWriter bits, int value, int size) throws IOException {
        if (size == 0) {
            return;
        }
        bits.writeBits(value < 0 ? value + (1 << size) - 1 : value, size);
    }

    private static int readAmplitude(BitReader bits, int size) throws IOException {
        if (size == 0) {
            return 0;
        }
        int value = bits.readBits(size);
        return (value & (1 << (size - 1))) != 0 ? value : value - ((1 << size) - 1);
    }

    private static final class FrameHeader {
        private final int width;
        private final int height;
        private final int precision;
        private final int components;
        private final int[] componentIds;
        private final int[] quantization;

        private FrameHeader(int width, int height, int components, int[] componentIds,
                int[] quantization) {
            this.width = width;
            this.height = height;
            this.precision = 8;
            this.components = components;
            this.componentIds = componentIds;
            this.quantization = quantization;
        }
    }

    private static final class ScanHeader {
        private final int[] components;
        private final int[] dcTables;
        private final int[] acTables;
        private final int ss;
        private final int se;
        private final int ah;
        private final int al;

        private ScanHeader(int[] components, int[] dcTables, int[] acTables,
                int ss, int se, int ah, int al) {
            this.components = components;
            this.dcTables = dcTables;
            this.acTables = acTables;
            this.ss = ss;
            this.se = se;
            this.ah = ah;
            this.al = al;
        }
    }
}
