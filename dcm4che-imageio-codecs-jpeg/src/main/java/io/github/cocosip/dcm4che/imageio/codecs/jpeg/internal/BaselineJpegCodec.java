package io.github.cocosip.dcm4che.imageio.codecs.jpeg.internal;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;

public final class BaselineJpegCodec {
    private BaselineJpegCodec() {
    }

    public static byte[] encode(JpegFrame frame) throws IOException {
        return encode(frame, JpegSampling.SF444, 0);
    }

    public static byte[] encode(JpegFrame frame, JpegSampling sampling) throws IOException {
        return encode(frame, sampling, 0);
    }

    public static byte[] encode(JpegFrame frame, JpegSampling sampling, int restartInterval)
            throws IOException {
        if (frame.precision() != 8) {
            throw new JpegException("JPEG Baseline requires 8-bit samples");
        }
        return encode(frame, 0xc0, sampling, restartInterval, -1.0f);
    }

    public static byte[] encode(JpegFrame frame, JpegSampling sampling, float quality)
            throws IOException {
        if (frame.precision() != 8) {
            throw new JpegException("JPEG Baseline requires 8-bit samples");
        }
        return encode(frame, 0xc0, sampling, 0, quality);
    }

    public static byte[] encode(JpegFrame frame, JpegSampling sampling, int restartInterval,
            float quality) throws IOException {
        if (frame.precision() != 8) {
            throw new JpegException("JPEG Baseline requires 8-bit samples");
        }
        return encode(frame, 0xc0, sampling, restartInterval, quality);
    }

    public static byte[] encode(JpegFrame frame, int restartInterval) throws IOException {
        return encode(frame, JpegSampling.SF444, restartInterval);
    }

    static byte[] encodeExtended(JpegFrame frame) throws IOException {
        return encodeExtended(frame, JpegSampling.SF444, 0);
    }

    static byte[] encodeExtended(JpegFrame frame, int restartInterval) throws IOException {
        return encodeExtended(frame, JpegSampling.SF444, restartInterval);
    }

    static byte[] encodeExtended(JpegFrame frame, JpegSampling sampling, int restartInterval)
            throws IOException {
        if (frame.precision() < 8 || frame.precision() > 12) {
            throw new JpegException("JPEG Extended requires 8-12 bit samples");
        }
        return encode(frame, 0xc1, sampling, restartInterval, -1.0f);
    }

    static byte[] encodeExtended(JpegFrame frame, JpegSampling sampling, float quality)
            throws IOException {
        if (frame.precision() < 8 || frame.precision() > 12) {
            throw new JpegException("JPEG Extended requires 8-12 bit samples");
        }
        return encode(frame, 0xc1, sampling, 0, quality);
    }

    static byte[] encodeExtended(JpegFrame frame, JpegSampling sampling, int restartInterval,
            float quality) throws IOException {
        if (frame.precision() < 8 || frame.precision() > 12) {
            throw new JpegException("JPEG Extended requires 8-12 bit samples");
        }
        return encode(frame, 0xc1, sampling, restartInterval, quality);
    }

    static byte[] encodeSequential(JpegFrame frame, int frameMarker, JpegSampling sampling,
            int restartInterval) throws IOException {
        if (frameMarker != 0xc0 && frameMarker != 0xc1 && frameMarker != 0xc5) {
            throw new IllegalArgumentException("unsupported sequential JPEG frame marker");
        }
        if (frameMarker == 0xc5 && frame.precision() != 8) {
            throw new JpegException("differential sequential JPEG requires 8-bit samples");
        }
        return encode(frame, frameMarker, sampling, restartInterval, -1.0f);
    }

    private static byte[] encode(JpegFrame frame, int frameMarker, JpegSampling sampling,
            int restartInterval, float quality)
            throws IOException {
        if (restartInterval < 0 || restartInterval > 0xffff) {
            throw new IllegalArgumentException("JPEG restart interval must fit in 16 bits");
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        MemoryCacheImageOutputStream output = new MemoryCacheImageOutputStream(bytes);
        output.write(0xff);
        output.write(0xd8);
        int[] luminanceQuantization = JpegTables.luminanceQuantization(quality);
        int[] chrominanceQuantization = JpegTables.chrominanceQuantization(quality);
        writeQuantization(output, 0, luminanceQuantization);
        if (frame.components() == 3) {
            writeQuantization(output, 1, chrominanceQuantization);
        }
        writeFrameHeader(output, frame, frameMarker, sampling);
        writeHuffmanTables(output, frame.components());
        if (restartInterval != 0) {
            JpegMarkerWriter.write(output, 0xdd, new byte[] {
                    (byte) (restartInterval >>> 8), (byte) restartInterval});
        }
        writeScanHeader(output, frame.components());
        BitWriter bits = new BitWriter(output);
        HuffmanTable[] dc = {JpegTables.standardLuminanceDc(), JpegTables.standardChrominanceDc()};
        HuffmanTable[] ac = {JpegTables.standardLuminanceAc(), JpegTables.standardChrominanceAc()};
        QuantizationTable[] quant = {
            QuantizationTable.of(luminanceQuantization),
            QuantizationTable.of(chrominanceQuantization)
        };
        int[] previousDc = new int[frame.components()];
        int restartIndex = 0;
        int mcusX = (frame.width() + sampling.maxHorizontal() * 8 - 1)
                / (sampling.maxHorizontal() * 8);
        int mcusY = (frame.height() + sampling.maxVertical() * 8 - 1)
                / (sampling.maxVertical() * 8);
        int mcuIndex = 0;
        for (int mcuY = 0; mcuY < mcusY; mcuY++) {
            for (int mcuX = 0; mcuX < mcusX; mcuX++) {
                if (restartInterval != 0 && mcuIndex != 0 && mcuIndex % restartInterval == 0) {
                    bits.flush();
                    output.write(0xff);
                    output.write(0xd0 + restartIndex);
                    restartIndex = (restartIndex + 1) & 7;
                    previousDc = new int[frame.components()];
                }
                for (int component = 0; component < frame.components(); component++) {
                    for (int blockY = 0; blockY < sampling.vertical(component); blockY++) {
                        for (int blockX = 0; blockX < sampling.horizontal(component); blockX++) {
                            int componentBlockX = mcuX * sampling.horizontal(component) + blockX;
                            int componentBlockY = mcuY * sampling.vertical(component) + blockY;
                            encodeBlock(bits, frame, componentBlockX, componentBlockY, component,
                                    sampling, quant[component == 0 ? 0 : 1],
                                    dc[component == 0 ? 0 : 1], ac[component == 0 ? 0 : 1],
                                    previousDc, component);
                        }
                    }
                }
                mcuIndex++;
            }
        }
        bits.flush();
        output.write(0xff);
        output.write(0xd9);
        output.flush();
        return bytes.toByteArray();
    }

    public static JpegFrame decode(byte[] data) throws IOException {
        return decode(data, 0xc0, false);
    }

    static JpegFrame decodeExtended(byte[] data) throws IOException {
        return decode(data, 0xc1, true);
    }

    static JpegFrame decodeSequential(byte[] data, int frameMarker) throws IOException {
        if (frameMarker != 0xc0 && frameMarker != 0xc1 && frameMarker != 0xc5) {
            throw new IllegalArgumentException("unsupported sequential JPEG frame marker");
        }
        return decode(data, frameMarker, frameMarker == 0xc1);
    }

    private static JpegFrame decode(byte[] data, int expectedFrameMarker, boolean extended)
            throws IOException {
        if (data == null || data.length < 4) {
            throw new JpegException("JPEG frame is truncated");
        }
        MemoryCacheImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(data));
        if (input.read() != 0xff || input.read() != 0xd8) {
            throw new JpegException("JPEG frame does not start with SOI");
        }
        Map<Integer, QuantizationTable> quant = new HashMap<Integer, QuantizationTable>();
        Map<Integer, HuffmanTable> huffman = new HashMap<Integer, HuffmanTable>();
        int width = 0;
        int height = 0;
        int precision = 0;
        int components = 0;
        int[] componentIds = null;
        JpegSampling sampling = null;
        int restartInterval = 0;
        boolean scanSeen = false;
        JpegMarkerReader markers = new JpegMarkerReader(input);
        while (!scanSeen) {
            JpegMarker marker = markers.next();
            int code = marker.code();
            byte[] payload = marker.payload();
            if (code == 0xdb) {
                parseQuantization(payload, quant);
            } else if (code == 0xc4) {
                parseHuffman(payload, huffman);
            } else if (code == 0xdd) {
                if (payload.length != 2) {
                    throw new JpegException("invalid JPEG restart interval");
                }
                restartInterval = u16(payload, 0);
            } else if (code == expectedFrameMarker) {
                precision = payload.length < 1 ? 0 : payload[0] & 0xff;
                if (payload.length < 6 || (extended ? precision < 8 || precision > 12
                        : precision != 8)) {
                    throw new JpegException(extended
                            ? "JPEG Extended requires 8-12 bit SOF1"
                            : "JPEG Baseline requires 8-bit SOF0");
                }
                height = u16(payload, 1);
                width = u16(payload, 3);
                components = payload[5] & 0xff;
                if (width == 0 || height == 0 || (components != 1 && components != 3)
                        || payload.length != 6 + components * 3) {
                    throw new JpegException("invalid JPEG SOF0 frame header");
                }
                componentIds = new int[components];
                int[] horizontal = new int[components];
                int[] vertical = new int[components];
                for (int i = 0; i < components; i++) {
                    int offset = 6 + i * 3;
                    componentIds[i] = payload[offset] & 0xff;
                    horizontal[i] = (payload[offset + 1] >>> 4) & 0x0f;
                    vertical[i] = payload[offset + 1] & 0x0f;
                    if (horizontal[i] == 0 || vertical[i] == 0) {
                        throw new JpegException("JPEG sampling factors must be non-zero");
                    }
                }
                if (components == 1) {
                    if (horizontal[0] != 1 || vertical[0] != 1) {
                        throw new JpegException("JPEG monochrome sampling must be 1x1");
                    }
                    sampling = JpegSampling.SF444;
                } else {
                    sampling = JpegSampling.fromFactors(horizontal, vertical);
                }
            } else if (code == 0xda) {
                if (components == 0 || componentIds == null) {
                    throw new JpegException("JPEG scan appears before SOF0");
                }
                ScanHeader scan = parseScan(payload, componentIds, components);
                ScanData entropy = readScan(input);
                JpegFrame frame = decodeScan(width, height, components, precision, quant, huffman,
                        sampling, scan, restartInterval, entropy);
                JpegMarker end = markers.next();
                if (end.code() != 0xd9) {
                    throw new JpegException("JPEG frame does not end with EOI");
                }
                scanSeen = true;
                return frame;
            } else if (code == 0xc0 || code == 0xc1 || code == 0xc2 || code == 0xc3
                    || code == 0xc9 || code == 0xca || code == 0xcb) {
                throw new JpegException("unsupported JPEG marker: 0x" + Integer.toHexString(code));
            } else if (code == 0xd8 || code == 0xd9 || (code >= 0xd0 && code <= 0xd7)) {
                throw new JpegException("unexpected JPEG marker: 0x" + Integer.toHexString(code));
            }
        }
        throw new JpegException("JPEG scan is missing");
    }

    private static ScanData readScan(ImageInputStream input) throws IOException {
        List<byte[]> segments = new ArrayList<byte[]>();
        List<Integer> restartMarkers = new ArrayList<Integer>();
        ByteArrayOutputStream segment = new ByteArrayOutputStream();
        while (true) {
            int value = input.read();
            if (value < 0) {
                throw new JpegException("truncated JPEG scan");
            }
            if (value != 0xff) {
                segment.write(value);
                continue;
            }
            int next = input.read();
            if (next < 0) {
                throw new JpegException("truncated JPEG scan marker");
            }
            if (next == 0) {
                segment.write(0xff);
                segment.write(0);
            } else if (next >= 0xd0 && next <= 0xd7) {
                segments.add(segment.toByteArray());
                segment = new ByteArrayOutputStream();
                restartMarkers.add(next);
            } else {
                input.seek(input.getStreamPosition() - 2);
                segments.add(segment.toByteArray());
                return new ScanData(segments, restartMarkers);
            }
        }
    }

    private static void writeQuantization(ImageOutputStream output, int id, int[] values) throws IOException {
        byte[] payload = new byte[65];
        payload[0] = (byte) id;
        for (int i = 0; i < 64; i++) {
            payload[i + 1] = (byte) values[i];
        }
        JpegMarkerWriter.write(output, 0xdb, payload);
    }

    private static void writeFrameHeader(ImageOutputStream output, JpegFrame frame, int marker,
            JpegSampling sampling)
            throws IOException {
        byte[] payload = new byte[6 + frame.components() * 3];
        payload[0] = (byte) frame.precision();
        payload[1] = (byte) (frame.height() >>> 8);
        payload[2] = (byte) frame.height();
        payload[3] = (byte) (frame.width() >>> 8);
        payload[4] = (byte) frame.width();
        payload[5] = (byte) frame.components();
        for (int i = 0; i < frame.components(); i++) {
            payload[6 + i * 3] = (byte) (i + 1);
            payload[7 + i * 3] = (byte) (frame.components() == 1
                    ? 0x11 : sampling.factorByte(i));
            payload[8 + i * 3] = (byte) (i == 0 ? 0 : 1);
        }
        JpegMarkerWriter.write(output, marker, payload);
    }

    private static void writeHuffmanTables(ImageOutputStream output, int components) throws IOException {
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

    private static void writeScanHeader(ImageOutputStream output, int components) throws IOException {
        byte[] payload = new byte[4 + components * 2];
        payload[0] = (byte) components;
        for (int i = 0; i < components; i++) {
            payload[1 + i * 2] = (byte) (i + 1);
            payload[2 + i * 2] = (byte) (i == 0 ? 0 : 0x11);
        }
        payload[payload.length - 3] = 0;
        payload[payload.length - 2] = 63;
        payload[payload.length - 1] = 0;
        JpegMarkerWriter.write(output, 0xda, payload);
    }

    private static void encodeBlock(BitWriter bits, JpegFrame frame, int blockX, int blockY,
            int component, JpegSampling sampling, QuantizationTable quant, HuffmanTable dc,
            HuffmanTable ac, int[] previousDc, int componentIndex) throws IOException {
        double[] block = new double[64];
        int componentWidth = sampling.componentWidth(frame.width(), component);
        int componentHeight = sampling.componentHeight(frame.height(), component);
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                int componentX = Math.min(componentWidth - 1, blockX * 8 + x);
                int componentY = Math.min(componentHeight - 1, blockY * 8 + y);
                block[y * 8 + x] = componentSample(frame, componentX, componentY, component,
                        sampling) - (1 << (frame.precision() - 1));
            }
        }
        double[] transformed = JpegDct.forward(block);
        int[] coefficients = new int[64];
        for (int i = 0; i < 64; i++) {
            coefficients[i] = (int) Math.round(transformed[i] / quant.get(JpegZigZag.positionOf(i)));
        }
        int dcValue = coefficients[0];
        int difference = dcValue - previousDc[componentIndex];
        previousDc[componentIndex] = dcValue;
        int dcCategory = category(difference);
        HuffmanCodec.encodeSymbol(bits, dc, dcCategory);
        writeAmplitude(bits, difference, dcCategory);
        int run = 0;
        for (int zig = 1; zig < 64; zig++) {
            int coefficient = coefficients[indexOf(zig)];
            if (coefficient == 0) {
                run++;
                continue;
            }
            while (run >= 16) {
                HuffmanCodec.encodeSymbol(bits, ac, 0xf0);
                run -= 16;
            }
            int size = category(coefficient);
            HuffmanCodec.encodeSymbol(bits, ac, (run << 4) | size);
            writeAmplitude(bits, coefficient, size);
            run = 0;
        }
        if (run != 0) {
            HuffmanCodec.encodeSymbol(bits, ac, 0);
        }
    }

    private static JpegFrame decodeScan(int width, int height, int components, int precision,
            Map<Integer, QuantizationTable> quant, Map<Integer, HuffmanTable> huffman,
            JpegSampling sampling, ScanHeader scan, int restartInterval, ScanData entropy)
            throws IOException {
        if (width == 0 || height == 0 || !scanSeenTables(quant, huffman, components)) {
            throw new JpegException("JPEG scan is missing required tables");
        }
        int[][] planes = new int[components][];
        int[] componentWidths = new int[components];
        int[] componentHeights = new int[components];
        for (int component = 0; component < components; component++) {
            componentWidths[component] = sampling.componentWidth(width, component);
            componentHeights[component] = sampling.componentHeight(height, component);
            planes[component] = new int[componentWidths[component] * componentHeights[component]];
        }
        int[] previousDc = new int[components];
        int mcusX = (width + sampling.maxHorizontal() * 8 - 1)
                / (sampling.maxHorizontal() * 8);
        int mcusY = (height + sampling.maxVertical() * 8 - 1)
                / (sampling.maxVertical() * 8);
        int totalMcu = mcusX * mcusY;
        int mcu = 0;
        int restartIndex = 0;
        for (int segmentIndex = 0; segmentIndex < entropy.entropySegments.size(); segmentIndex++) {
            int segmentEnd = restartInterval == 0
                    ? totalMcu
                    : Math.min(totalMcu, mcu + restartInterval);
            MemoryCacheImageInputStream entropyInput = new MemoryCacheImageInputStream(
                    new ByteArrayInputStream(entropy.entropySegments.get(segmentIndex)));
            BitReader bits = new BitReader(entropyInput);
            while (mcu < segmentEnd) {
                int mcuY = mcu / mcusX;
                int mcuX = mcu % mcusX;
                for (int component = 0; component < components; component++) {
                    int table = component == 0 ? 0 : 1;
                    for (int blockY = 0; blockY < sampling.vertical(component); blockY++) {
                        for (int blockX = 0; blockX < sampling.horizontal(component); blockX++) {
                            int[] block = decodeBlock(bits, quant.get(table), huffman.get(table),
                                    huffman.get(table == 0 ? 0x10 : 0x11), previousDc, component);
                            double[] dequantized = new double[64];
                            for (int i = 0; i < 64; i++) {
                                dequantized[i] = block[i]
                                        * quant.get(table).get(JpegZigZag.positionOf(i));
                            }
                            double[] restored = JpegDct.inverse(dequantized);
                            int componentX = (mcuX * sampling.horizontal(component) + blockX) * 8;
                            int componentY = (mcuY * sampling.vertical(component) + blockY) * 8;
                            for (int y = 0; y < 8 && componentY + y < componentHeights[component]; y++) {
                                for (int x = 0; x < 8 && componentX + x < componentWidths[component]; x++) {
                                    int value = (int) Math.round(restored[y * 8 + x]
                                            + (1 << (precision - 1)));
                                    value = Math.max(0, Math.min((1 << precision) - 1, value));
                                    planes[component][(componentY + y) * componentWidths[component]
                                            + componentX + x] = value;
                                }
                            }
                        }
                    }
                }
                mcu++;
            }
            if (restartInterval != 0 && mcu < totalMcu) {
                if (segmentIndex >= entropy.restartMarkers.size()
                        || entropy.restartMarkers.get(segmentIndex) != 0xd0 + restartIndex) {
                    throw new JpegException("JPEG restart marker sequence is invalid");
                }
                restartIndex = (restartIndex + 1) & 7;
                previousDc = new int[components];
            }
        }
        if (mcu != totalMcu || (restartInterval == 0 && !entropy.restartMarkers.isEmpty())) {
            throw new JpegException("JPEG scan does not match restart interval");
        }
        int[] samples = new int[width * height * components];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int sampleOffset = (y * width + x) * components;
                for (int component = 0; component < components; component++) {
                    int componentX = Math.min(componentWidths[component] - 1,
                            (x * sampling.horizontal(component)) / sampling.maxHorizontal());
                    int componentY = Math.min(componentHeights[component] - 1,
                            (y * sampling.vertical(component)) / sampling.maxVertical());
                    samples[sampleOffset + component] = planes[component][componentY
                            * componentWidths[component] + componentX];
                }
            }
        }
        return JpegFrame.of(width, height, components, samples, precision);
    }

    private static boolean scanSeenTables(Map<Integer, QuantizationTable> quant,
            Map<Integer, HuffmanTable> huffman, int components) {
        if (!quant.containsKey(0) || !huffman.containsKey(0) || !huffman.containsKey(0x10)) {
            return false;
        }
        return components == 1 || (quant.containsKey(1) && huffman.containsKey(1)
                && huffman.containsKey(0x11));
    }

    private static int[] decodeBlock(BitReader bits, QuantizationTable quant,
            HuffmanTable dc, HuffmanTable ac, int[] previousDc, int component) throws IOException {
        int[] coefficients = new int[64];
        int category = HuffmanCodec.decodeSymbol(bits, dc);
        int difference = readAmplitude(bits, category);
        coefficients[0] = previousDc[component] + difference;
        previousDc[component] = coefficients[0];
        int zig = 1;
        while (zig < 64) {
            int symbol = HuffmanCodec.decodeSymbol(bits, ac);
            if (symbol == 0) {
                break;
            }
            if (symbol == 0xf0) {
                zig += 16;
                continue;
            }
            zig += symbol >>> 4;
            if (zig >= 64) {
                throw new JpegException("JPEG AC run exceeds block");
            }
            int size = symbol & 0x0f;
            if (size == 0) {
                throw new JpegException("invalid JPEG AC symbol");
            }
            coefficients[indexOf(zig++)] = readAmplitude(bits, size);
        }
        return coefficients;
    }

    private static void parseQuantization(byte[] payload, Map<Integer, QuantizationTable> tables) {
        int offset = 0;
        while (offset < payload.length) {
            int specification = payload[offset++] & 0xff;
            if ((specification >>> 4) != 0 || offset + 64 > payload.length) {
                throw new IllegalArgumentException("unsupported JPEG quantization table");
            }
            int[] values = new int[64];
            for (int i = 0; i < 64; i++) {
                values[i] = payload[offset++] & 0xff;
            }
            tables.put(specification & 0x0f, QuantizationTable.of(values));
        }
    }

    private static void parseHuffman(byte[] payload, Map<Integer, HuffmanTable> tables) {
        int offset = 0;
        while (offset < payload.length) {
            int specification = payload[offset++] & 0xff;
            if (offset + 16 > payload.length) {
                throw new IllegalArgumentException("truncated JPEG Huffman table");
            }
            int[] counts = new int[16];
            int total = 0;
            for (int i = 0; i < counts.length; i++) {
                counts[i] = payload[offset++] & 0xff;
                total += counts[i];
            }
            if (offset + total > payload.length) {
                throw new IllegalArgumentException("truncated JPEG Huffman values");
            }
            int[] values = new int[total];
            for (int i = 0; i < total; i++) {
                values[i] = payload[offset++] & 0xff;
            }
            tables.put(specification, HuffmanTable.fromDefinition(counts, values));
        }
    }

    private static ScanHeader parseScan(byte[] payload, int[] componentIds, int components) {
        if (payload.length != 4 + components * 2 || (payload[0] & 0xff) != components) {
            throw new IllegalArgumentException("invalid JPEG SOS header");
        }
        for (int i = 0; i < components; i++) {
            if ((payload[1 + i * 2] & 0xff) != componentIds[i]) {
                throw new IllegalArgumentException("JPEG SOS component order is unsupported");
            }
        }
        int ss = payload[payload.length - 3] & 0xff;
        int se = payload[payload.length - 2] & 0xff;
        int ahAl = payload[payload.length - 1] & 0xff;
        if (ss != 0 || se != 63 || ahAl != 0) {
            throw new IllegalArgumentException("unsupported JPEG scan parameters");
        }
        return new ScanHeader();
    }

    private static int u16(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xff) << 8) | (bytes[offset + 1] & 0xff);
    }

    private static int category(int value) {
        int absolute = Math.abs(value);
        int category = 0;
        while (absolute != 0) {
            category++;
            absolute >>>= 1;
        }
        return category;
    }

    private static void writeAmplitude(BitWriter bits, int value, int size) throws IOException {
        if (size == 0) {
            return;
        }
        int encoded = value < 0 ? value + (1 << size) - 1 : value;
        bits.writeBits(encoded, size);
    }

    private static int readAmplitude(BitReader bits, int size) throws IOException {
        if (size == 0) {
            return 0;
        }
        int value = bits.readBits(size);
        return (value & (1 << (size - 1))) != 0 ? value : value - ((1 << size) - 1);
    }

    private static int indexOf(int zigZagIndex) {
        return JpegZigZag.ORDER[zigZagIndex];
    }

    private static final class ScanData {
        private final List<byte[]> entropySegments;
        private final List<Integer> restartMarkers;

        private ScanData(List<byte[]> entropySegments, List<Integer> restartMarkers) {
            this.entropySegments = entropySegments;
            this.restartMarkers = restartMarkers;
        }
    }

    private static int componentSample(JpegFrame frame, int componentX, int componentY,
            int component, JpegSampling sampling) {
        int xStart = (componentX * sampling.maxHorizontal()) / sampling.horizontal(component);
        int xEnd = ((componentX + 1) * sampling.maxHorizontal()
                + sampling.horizontal(component) - 1) / sampling.horizontal(component);
        int yStart = (componentY * sampling.maxVertical()) / sampling.vertical(component);
        int yEnd = ((componentY + 1) * sampling.maxVertical()
                + sampling.vertical(component) - 1) / sampling.vertical(component);
        xStart = Math.min(frame.width() - 1, xStart);
        xEnd = Math.min(frame.width(), Math.max(xStart + 1, xEnd));
        yStart = Math.min(frame.height() - 1, yStart);
        yEnd = Math.min(frame.height(), Math.max(yStart + 1, yEnd));
        long total = 0;
        int count = 0;
        for (int y = yStart; y < yEnd; y++) {
            for (int x = xStart; x < xEnd; x++) {
                total += frame.sample(x, y, component);
                count++;
            }
        }
        return (int) ((total + count / 2) / count);
    }

    private static final class ScanHeader {
    }
}
