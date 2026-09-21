package io.github.cocosip.dcm4che.imageio.codecs.jpeg.internal;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;

/** Progressive DCT Huffman JPEG codec for 8-bit 1x1 monochrome/RGB frames. */
public final class ProgressiveJpegCodec {
    private ProgressiveJpegCodec() {
    }

    public static byte[] encode(JpegFrame frame) throws IOException {
        return encode(frame, JpegSampling.SF444);
    }

    public static byte[] encode(JpegFrame frame, JpegSampling sampling) throws IOException {
        return encode(frame, sampling, 0xc2, -1.0f);
    }

    public static byte[] encode(JpegFrame frame, JpegSampling sampling, float quality)
            throws IOException {
        return encode(frame, sampling, 0xc2, quality);
    }

    /** Encodes the default progressive scan script with a DRI/RST interval. */
    public static byte[] encodeWithRestart(JpegFrame frame, JpegSampling sampling,
            float quality, int restartInterval) throws IOException {
        if (restartInterval < 0 || restartInterval > 0xffff) {
            throw new IllegalArgumentException("JPEG restart interval must fit in 16 bits");
        }
        return encode(frame, sampling, 0xc2, quality, restartInterval);
    }

    /** Encodes a caller-supplied legal progressive scan script. */
    public static byte[] encode(JpegFrame frame, JpegSampling sampling, float quality,
            int restartInterval, JpegScanScript... scans) throws IOException {
        if (restartInterval < 0 || restartInterval > 0xffff) {
            throw new IllegalArgumentException("JPEG restart interval must fit in 16 bits");
        }
        if (scans == null || scans.length == 0) {
            throw new IllegalArgumentException("progressive JPEG scan script is empty");
        }
        return encode(frame, sampling, 0xc2, quality, restartInterval, scans);
    }

    static byte[] encode(JpegFrame frame, JpegSampling sampling, int frameMarker)
            throws IOException {
        return encode(frame, sampling, frameMarker, -1.0f);
    }

    static byte[] encode(JpegFrame frame, JpegSampling sampling, int frameMarker,
            float quality)
            throws IOException {
        return encode(frame, sampling, frameMarker, quality, 0);
    }

    private static byte[] encode(JpegFrame frame, JpegSampling sampling, int frameMarker,
            float quality, int restartInterval)
            throws IOException {
        return encode(frame, sampling, frameMarker, quality, restartInterval,
                defaultScanScript(frame.components()));
    }

    private static byte[] encode(JpegFrame frame, JpegSampling sampling, int frameMarker,
            float quality, int restartInterval, JpegScanScript[] scans)
            throws IOException {
        if (frame.precision() != 8 || (frame.components() != 1 && frame.components() != 3)) {
            throw new JpegException("Progressive JPEG requires 8-bit monochrome or RGB samples");
        }
        if (frameMarker != 0xc2 && frameMarker != 0xc6) {
            throw new IllegalArgumentException("unsupported progressive JPEG frame marker");
        }
        if (frame.components() == 1 && sampling != JpegSampling.SF444) {
            throw new JpegException("JPEG monochrome sampling must be 1x1");
        }
        validateScanScript(frame.components(), scans);
        int[] luminanceQuantization = JpegTables.luminanceQuantization(quality);
        int[] chrominanceQuantization = JpegTables.chrominanceQuantization(quality);
        int[][][] coefficients = createCoefficients(frame, sampling,
                luminanceQuantization, chrominanceQuantization);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        MemoryCacheImageOutputStream output = new MemoryCacheImageOutputStream(bytes);
        output.write(0xff);
        output.write(0xd8);
        writeQuantization(output, 0, luminanceQuantization);
        if (frame.components() == 3) {
            writeQuantization(output, 1, chrominanceQuantization);
        }
        writeFrameHeader(output, frame, sampling, frameMarker);
        writeHuffmanTables(output, frame.components());
        if (restartInterval != 0) {
            JpegMarkerWriter.write(output, 0xdd, new byte[] {
                    (byte) (restartInterval >>> 8), (byte) restartInterval});
        }
        for (JpegScanScript scan : scans) {
            writeScanHeader(output, scan);
            encodeScan(output, coefficients, frame, sampling, scan, restartInterval);
        }
        output.write(0xff);
        output.write(0xd9);
        output.flush();
        return bytes.toByteArray();
    }

    private static JpegScanScript[] defaultScanScript(int components) {
        if (components == 1) {
            return new JpegScanScript[] {
                JpegScanScript.dcFirst(0), JpegScanScript.acFirst(0, 1, 63)};
        }
        return new JpegScanScript[] {
            JpegScanScript.dcFirst(0, 1, 2),
            JpegScanScript.acFirst(0, 1, 63),
            JpegScanScript.acFirst(1, 1, 63),
            JpegScanScript.acFirst(2, 1, 63)
        };
    }

    private static void validateScanScript(int components, JpegScanScript[] scans)
            throws JpegException {
        int[][] levels = new int[components][64];
        for (int component = 0; component < components; component++) {
            for (int coefficient = 0; coefficient < levels[component].length; coefficient++) {
                levels[component][coefficient] = -1;
            }
        }
        for (JpegScanScript scan : scans) {
            for (int component : scan.components()) {
                if (component >= components) {
                    throw new JpegException("progressive JPEG scan component is missing");
                }
                for (int zig = scan.spectralStart(); zig <= scan.spectralEnd(); zig++) {
                    int coefficient = JpegZigZag.ORDER[zig];
                    int current = levels[component][coefficient];
                    if (scan.successiveHigh() == 0) {
                        if (current >= 0) {
                            throw new JpegException(
                                    "progressive JPEG coefficient band is repeated");
                        }
                    } else if (current != scan.successiveHigh()) {
                        throw new JpegException(
                                "progressive JPEG refinement has no matching first scan");
                    }
                    levels[component][coefficient] = scan.successiveLow();
                }
            }
        }
        for (int component = 0; component < components; component++) {
            for (int coefficient : levels[component]) {
                if (coefficient < 0) {
                    throw new JpegException(
                            "progressive JPEG scan script does not cover all coefficients");
                }
            }
        }
    }

    public static JpegFrame decode(byte[] data) throws IOException {
        return decode(data, 0xc2);
    }

    static JpegFrame decode(byte[] data, int expectedFrameMarker) throws IOException {
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
        JpegSampling sampling = null;
        int[][][] coefficients = null;
        int restartInterval = 0;
        ProgressiveCoefficientState coefficientState = null;
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
            } else if (code == 0xdd) {
                if (payload.length != 2) {
                    throw new JpegException("invalid progressive JPEG restart interval");
                }
                restartInterval = u16(payload, 0);
            } else if (code == expectedFrameMarker) {
                FrameHeader frame = parseFrameHeader(payload);
                width = frame.width;
                height = frame.height;
                precision = frame.precision;
                components = frame.components;
                componentIds = frame.componentIds;
                componentQuant = frame.quantization;
                sampling = frame.sampling;
                coefficients = createEmptyCoefficients(width, height, components, sampling);
                coefficientState = new ProgressiveCoefficientState(width, height, components,
                        sampling);
            } else if (code == 0xda) {
                if (coefficients == null || !quant.containsKey(0) || !huffman.containsKey(0)) {
                    throw new JpegException("Progressive JPEG scan is missing frame or tables");
                }
                ScanHeader scan = parseScanHeader(payload, componentIds, components);
                coefficientState.validate(scan);
                ScanData entropy = readScan(input);
                decodeScan(scan, entropy, coefficients, width, height, components, sampling,
                        huffman, restartInterval);
                coefficientState.apply(scan);
                seenScan = true;
            } else if ((code >= 0xe0 && code <= 0xef) || code == 0xfe) {
                // APPn and COM metadata are not part of the progressive coefficient state.
            } else if (code == 0xd9) {
                if (!seenScan || coefficients == null) {
                    throw new JpegException("Progressive JPEG is missing scan data");
                }
                coefficientState.requireComplete();
                return reconstruct(width, height, components, precision, coefficients,
                        componentQuant, sampling, quant);
            } else {
                throw new JpegException("unsupported progressive JPEG marker: 0x"
                        + Integer.toHexString(code));
            }
        }
    }

    private static int[][][] createCoefficients(JpegFrame frame, JpegSampling sampling,
            int[] luminanceQuantization, int[] chrominanceQuantization) {
        int[][][] coefficients = createEmptyCoefficients(frame.width(), frame.height(),
                frame.components(), sampling);
        QuantizationTable[] quant = {
            QuantizationTable.of(luminanceQuantization),
            QuantizationTable.of(chrominanceQuantization)};
        for (int mcuY = 0; mcuY < mcusY(frame.height(), sampling); mcuY++) {
            for (int mcuX = 0; mcuX < mcusX(frame.width(), sampling); mcuX++) {
                for (int component = 0; component < frame.components(); component++) {
                    for (int blockY = 0; blockY < sampling.vertical(component); blockY++) {
                        for (int blockX = 0; blockX < sampling.horizontal(component); blockX++) {
                            int bx = mcuX * sampling.horizontal(component) + blockX;
                            int by = mcuY * sampling.vertical(component) + blockY;
                    double[] block = new double[64];
                    int componentWidth = sampling.componentWidth(frame.width(), component);
                    int componentHeight = sampling.componentHeight(frame.height(), component);
                    for (int y = 0; y < 8; y++) {
                        for (int x = 0; x < 8; x++) {
                            int componentX = Math.min(componentWidth - 1, bx * 8 + x);
                            int componentY = Math.min(componentHeight - 1, by * 8 + y);
                            block[y * 8 + x] = componentSample(frame, componentX, componentY,
                                    component, sampling) - 128;
                        }
                    }
                    double[] transformed = JpegDct.forward(block);
                    int[] target = coefficients[component][indexOfBlock(bx, by, frame.width(),
                            frame.height(), sampling, component)];
                    for (int i = 0; i < 64; i++) {
                        target[i] = (int) Math.round(
                                transformed[i] / quant[component == 0 ? 0 : 1].get(
                                        JpegZigZag.positionOf(i)));
                    }
                        }
                    }
                }
            }
        }
        return coefficients;
    }

    private static int[][][] createEmptyCoefficients(int width, int height, int components,
            JpegSampling sampling) {
        int[][][] coefficients = new int[components][][];
        for (int component = 0; component < components; component++) {
            int blocksX = blocksX(width, sampling, component);
            int blocksY = blocksY(height, sampling, component);
            coefficients[component] = new int[blocksX * blocksY][64];
        }
        return coefficients;
    }

    private static void encodeScan(ImageOutputStream output, int[][][] coefficients,
            JpegFrame frame, JpegSampling sampling, JpegScanScript scan,
            int restartInterval) throws IOException {
        HuffmanTable[] dc = {JpegTables.standardLuminanceDc(), JpegTables.standardChrominanceDc()};
        HuffmanTable[] ac = {JpegTables.standardLuminanceAc(), JpegTables.standardChrominanceAc()};
        int[] previous = new int[frame.components()];
        int mcuWidth = scanMcuWidth(frame.width(), sampling, scan);
        int mcuHeight = scanMcuHeight(frame.height(), sampling, scan);
        int restartIndex = 0;
        int mcuIndex = 0;
        BitWriter bits = new BitWriter(output);
        for (int mcuY = 0; mcuY < mcuHeight; mcuY++) {
            for (int mcuX = 0; mcuX < mcuWidth; mcuX++) {
                if (restartInterval != 0 && mcuIndex != 0
                        && mcuIndex % restartInterval == 0) {
                    bits.flush();
                    output.write(0xff);
                    output.write(0xd0 + restartIndex);
                    restartIndex = (restartIndex + 1) & 7;
                    previous = new int[frame.components()];
                    bits = new BitWriter(output);
                }
                for (int selector = 0; selector < scan.components().length; selector++) {
                    int component = scan.components()[selector];
                    int blockCountY = scan.components().length == 1
                            ? 1 : sampling.vertical(component);
                    int blockCountX = scan.components().length == 1
                            ? 1 : sampling.horizontal(component);
                    for (int blockY = 0; blockY < blockCountY; blockY++) {
                        for (int blockX = 0; blockX < blockCountX; blockX++) {
                            int bx = scan.components().length == 1
                                    ? mcuX : mcuX * sampling.horizontal(component) + blockX;
                            int by = scan.components().length == 1
                                    ? mcuY : mcuY * sampling.vertical(component) + blockY;
                            int[] block = coefficients[component][indexOfBlock(bx, by,
                                    frame.width(), frame.height(), sampling, component)];
                            if (scan.spectralStart() == 0) {
                                encodeDcProgressive(bits, block, component, scan, previous, dc);
                            } else if (scan.successiveHigh() == 0) {
                                encodeAcFirst(bits, block, scan, ac[component == 0 ? 0 : 1]);
                            } else {
                                encodeAcRefinement(bits, block, scan,
                                        ac[component == 0 ? 0 : 1]);
                            }
                        }
                    }
                }
                mcuIndex++;
            }
        }
        bits.flush();
    }

    private static void encodeDcProgressive(BitWriter bits, int[] block, int component,
            JpegScanScript scan, int[] previous, HuffmanTable[] dc) throws IOException {
        if (scan.successiveHigh() == 0) {
            int value = successiveValue(block[0], scan.successiveLow());
            int difference = value - previous[component];
            previous[component] = value;
            int size = category(difference);
            HuffmanCodec.encodeSymbol(bits, dc[component == 0 ? 0 : 1], size);
            writeAmplitude(bits, difference, size);
        } else {
            bits.writeBits((Math.abs(block[0]) >>> scan.successiveLow()) & 1, 1);
        }
    }

    private static void encodeAcFirst(BitWriter bits, int[] block, JpegScanScript scan,
            HuffmanTable ac) throws IOException {
        int run = 0;
        for (int zig = scan.spectralStart(); zig <= scan.spectralEnd(); zig++) {
            int value = successiveValue(block[JpegZigZag.ORDER[zig]], scan.successiveLow());
            if (value == 0) {
                run++;
                continue;
            }
            while (run >= 16) {
                HuffmanCodec.encodeSymbol(bits, ac, 0xf0);
                run -= 16;
            }
            int size = category(value);
            HuffmanCodec.encodeSymbol(bits, ac, (run << 4) | size);
            writeAmplitude(bits, value, size);
            run = 0;
        }
        if (run != 0) {
            HuffmanCodec.encodeSymbol(bits, ac, 0);
        }
    }

    private static void encodeAcRefinement(BitWriter bits, int[] block, JpegScanScript scan,
            HuffmanTable ac) throws IOException {
        int point = 1 << scan.successiveLow();
        int firstPoint = 1 << scan.successiveHigh();
        int run = 0;
        List<Integer> pendingCorrectionBits = new ArrayList<Integer>();
        for (int zig = scan.spectralStart(); zig <= scan.spectralEnd(); zig++) {
            int coefficient = block[JpegZigZag.ORDER[zig]];
            int magnitude = Math.abs(coefficient);
            if (magnitude >= firstPoint) {
                // Coefficients present in the first scan contribute one correction bit.
                int correction = (magnitude >>> scan.successiveLow()) & 1;
                if (run == 0) {
                    bits.writeBits(correction, 1);
                } else {
                    pendingCorrectionBits.add(correction);
                }
            } else if (magnitude >= point) {
                // A coefficient that first becomes non-zero in this refinement scan.
                while (run >= 16) {
                    HuffmanCodec.encodeSymbol(bits, ac, 0xf0);
                    run -= 16;
                    for (int correction : pendingCorrectionBits) {
                        bits.writeBits(correction, 1);
                    }
                    pendingCorrectionBits.clear();
                }
                HuffmanCodec.encodeSymbol(bits, ac, (run << 4) | 1);
                for (int correction : pendingCorrectionBits) {
                    bits.writeBits(correction, 1);
                }
                pendingCorrectionBits.clear();
                bits.writeBits(coefficient < 0 ? 0 : 1, 1);
                run = 0;
            } else {
                run++;
            }
        }
        if (run != 0) {
            HuffmanCodec.encodeSymbol(bits, ac, 0);
            for (int correction : pendingCorrectionBits) {
                bits.writeBits(correction, 1);
            }
        }
    }

    private static void encodeDcScan(ImageOutputStream output, int[][][] coefficients,
            JpegFrame frame, JpegSampling sampling, int restartInterval) throws IOException {
        HuffmanTable[] dc = {JpegTables.standardLuminanceDc(), JpegTables.standardChrominanceDc()};
        int[] previous = new int[frame.components()];
        int restartIndex = 0;
        int mcuIndex = 0;
        BitWriter bits = new BitWriter(output);
        for (int mcuY = 0; mcuY < mcusY(frame.height(), sampling); mcuY++) {
            for (int mcuX = 0; mcuX < mcusX(frame.width(), sampling); mcuX++) {
                if (restartInterval != 0 && mcuIndex != 0
                        && mcuIndex % restartInterval == 0) {
                    bits.flush();
                    output.write(0xff);
                    output.write(0xd0 + restartIndex);
                    restartIndex = (restartIndex + 1) & 7;
                    previous = new int[frame.components()];
                    bits = new BitWriter(output);
                }
                for (int component = 0; component < frame.components(); component++) {
                    for (int blockY = 0; blockY < sampling.vertical(component); blockY++) {
                        for (int blockX = 0; blockX < sampling.horizontal(component); blockX++) {
                            int bx = mcuX * sampling.horizontal(component) + blockX;
                            int by = mcuY * sampling.vertical(component) + blockY;
                    int value = coefficients[component][indexOfBlock(bx, by, frame.width(),
                            frame.height(), sampling, component)][0];
                    int difference = value - previous[component];
                    previous[component] = value;
                    int size = category(difference);
                    HuffmanCodec.encodeSymbol(bits, dc[component == 0 ? 0 : 1], size);
                    writeAmplitude(bits, difference, size);
                        }
                    }
                }
                mcuIndex++;
            }
        }
        bits.flush();
    }

    private static void encodeAcScan(ImageOutputStream output, int[][][] coefficients,
            JpegFrame frame, JpegSampling sampling, int restartInterval) throws IOException {
        HuffmanTable[] ac = {JpegTables.standardLuminanceAc(), JpegTables.standardChrominanceAc()};
        int restartIndex = 0;
        int mcuIndex = 0;
        BitWriter bits = new BitWriter(output);
        for (int mcuY = 0; mcuY < mcusY(frame.height(), sampling); mcuY++) {
            for (int mcuX = 0; mcuX < mcusX(frame.width(), sampling); mcuX++) {
                if (restartInterval != 0 && mcuIndex != 0
                        && mcuIndex % restartInterval == 0) {
                    bits.flush();
                    output.write(0xff);
                    output.write(0xd0 + restartIndex);
                    restartIndex = (restartIndex + 1) & 7;
                    bits = new BitWriter(output);
                }
                for (int component = 0; component < frame.components(); component++) {
                    for (int blockY = 0; blockY < sampling.vertical(component); blockY++) {
                        for (int blockX = 0; blockX < sampling.horizontal(component); blockX++) {
                            int bx = mcuX * sampling.horizontal(component) + blockX;
                            int by = mcuY * sampling.vertical(component) + blockY;
                    int[] block = coefficients[component][indexOfBlock(bx, by, frame.width(),
                            frame.height(), sampling, component)];
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
                mcuIndex++;
            }
        }
        bits.flush();
    }

    private static void decodeScan(ScanHeader scan, ScanData entropy, int[][][] coefficients,
            int width, int height, int components, JpegSampling sampling,
            Map<Integer, HuffmanTable> huffman, int restartInterval)
            throws IOException {
        int totalMcu = scanMcuWidth(width, sampling, scan)
                * scanMcuHeight(height, sampling, scan);
        int mcu = 0;
        int restartIndex = 0;
        for (int segmentIndex = 0; segmentIndex < entropy.entropySegments.size(); segmentIndex++) {
            int segmentEnd = restartInterval == 0
                    ? totalMcu : Math.min(totalMcu, mcu + restartInterval);
            MemoryCacheImageInputStream entropyInput = new MemoryCacheImageInputStream(
                    new ByteArrayInputStream(entropy.entropySegments.get(segmentIndex)));
            BitReader bits = new BitReader(entropyInput);
            int[] previous = new int[components];
            ProgressiveState state = new ProgressiveState();
            while (mcu < segmentEnd) {
                int mcuWidth = scanMcuWidth(width, sampling, scan);
                int mcuY = mcu / mcuWidth;
                int mcuX = mcu % mcuWidth;
                for (int selector = 0; selector < scan.components.length; selector++) {
                    int component = scan.components[selector];
                    int blockCountY = scan.components.length == 1
                            ? 1 : sampling.vertical(component);
                    int blockCountX = scan.components.length == 1
                            ? 1 : sampling.horizontal(component);
                    for (int blockY = 0; blockY < blockCountY; blockY++) {
                        for (int blockX = 0; blockX < blockCountX; blockX++) {
                            int bx = scan.components.length == 1
                                    ? mcuX : mcuX * sampling.horizontal(component) + blockX;
                            int by = scan.components.length == 1
                                    ? mcuY : mcuY * sampling.vertical(component) + blockY;
                            int[] block = coefficients[component][indexOfBlock(bx, by, width, height,
                                    sampling, component)];
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
                mcu++;
            }
            if (state.eobRun != 0) {
                throw new JpegException("progressive JPEG EOB run exceeds scan");
            }
            if (restartInterval != 0 && mcu < totalMcu) {
                if (segmentIndex >= entropy.restartMarkers.size()
                        || entropy.restartMarkers.get(segmentIndex) != 0xd0 + restartIndex) {
                    throw new JpegException("progressive JPEG restart marker sequence is invalid");
                }
                restartIndex = (restartIndex + 1) & 7;
            }
        }
        if (mcu != totalMcu || (restartInterval == 0 && !entropy.restartMarkers.isEmpty())) {
            throw new JpegException("progressive JPEG scan does not match restart interval");
        }
    }

    private static ScanData readScan(MemoryCacheImageInputStream input) throws IOException {
        List<byte[]> segments = new ArrayList<byte[]>();
        List<Integer> restartMarkers = new ArrayList<Integer>();
        ByteArrayOutputStream segment = new ByteArrayOutputStream();
        while (true) {
            int value = input.read();
            if (value < 0) {
                throw new JpegException("truncated progressive JPEG scan");
            }
            if (value != 0xff) {
                segment.write(value);
                continue;
            }
            int next = input.read();
            if (next < 0) {
                throw new JpegException("truncated progressive JPEG scan marker");
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
            int currentIndex = JpegZigZag.ORDER[zig];
            if (block[currentIndex] != 0) {
                block[currentIndex] = refineCoefficient(block[currentIndex], scan.al,
                        bits.readBits(1));
                zig++;
                continue;
            }
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
            int[][][] coefficients, int[] componentQuant, JpegSampling sampling,
            Map<Integer, QuantizationTable> quant) throws IOException {
        int[][] planes = new int[components][];
        int[] componentWidths = new int[components];
        int[] componentHeights = new int[components];
        for (int component = 0; component < components; component++) {
            componentWidths[component] = sampling.componentWidth(width, component);
            componentHeights[component] = sampling.componentHeight(height, component);
            planes[component] = new int[componentWidths[component] * componentHeights[component]];
        }
        for (int mcuY = 0; mcuY < mcusY(height, sampling); mcuY++) {
            for (int mcuX = 0; mcuX < mcusX(width, sampling); mcuX++) {
                for (int component = 0; component < components; component++) {
                    int tableId = componentQuant[component];
                    QuantizationTable table = quant.get(tableId);
                    if (table == null) {
                        throw new JpegException("progressive JPEG references missing quantization table");
                    }
                    for (int blockY = 0; blockY < sampling.vertical(component); blockY++) {
                        for (int blockX = 0; blockX < sampling.horizontal(component); blockX++) {
                            int bx = mcuX * sampling.horizontal(component) + blockX;
                            int by = mcuY * sampling.vertical(component) + blockY;
                            int[] block = coefficients[component][indexOfBlock(bx, by, width,
                                    height, sampling, component)];
                            double[] restored = new double[64];
                            for (int i = 0; i < 64; i++) {
                                restored[i] = block[i] * table.get(JpegZigZag.positionOf(i));
                            }
                            double[] pixels = JpegDct.inverse(restored);
                            int componentX = bx * 8;
                            int componentY = by * 8;
                            for (int y = 0; y < 8 && componentY + y < componentHeights[component]; y++) {
                                for (int x = 0; x < 8 && componentX + x < componentWidths[component]; x++) {
                                    int value = (int) Math.round(pixels[y * 8 + x]
                                            + (1 << (precision - 1)));
                                    planes[component][(componentY + y) * componentWidths[component]
                                            + componentX + x] = Math.max(0,
                                            Math.min((1 << precision) - 1, value));
                                }
                            }
                        }
                    }
                }
            }
        }
        int[] samples = new int[width * height * components];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int offset = (y * width + x) * components;
                for (int component = 0; component < components; component++) {
                    int componentX = Math.min(componentWidths[component] - 1,
                            x * sampling.horizontal(component) / sampling.maxHorizontal());
                    int componentY = Math.min(componentHeights[component] - 1,
                            y * sampling.vertical(component) / sampling.maxVertical());
                    samples[offset + component] = planes[component][componentY
                            * componentWidths[component] + componentX];
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
        int[] horizontal = new int[components];
        int[] vertical = new int[components];
        for (int i = 0; i < components; i++) {
            int offset = 6 + i * 3;
            ids[i] = payload[offset] & 0xff;
            horizontal[i] = (payload[offset + 1] >>> 4) & 0x0f;
            vertical[i] = payload[offset + 1] & 0x0f;
            if (horizontal[i] == 0 || vertical[i] == 0) {
                throw new JpegException("progressive JPEG sampling factors must be non-zero");
            }
            quant[i] = payload[offset + 2] & 0x0f;
        }
        JpegSampling sampling = components == 1
                ? requireMonochromeSampling(horizontal, vertical)
                : JpegSampling.fromFactors(horizontal, vertical);
        return new FrameHeader(width, height, components, ids, quant, sampling);
    }

    private static JpegSampling requireMonochromeSampling(int[] horizontal, int[] vertical)
            throws IOException {
        if (horizontal[0] != 1 || vertical[0] != 1) {
            throw new JpegException("progressive JPEG monochrome sampling must be 1x1");
        }
        return JpegSampling.SF444;
    }

    private static ScanHeader parseScanHeader(byte[] payload, int[] componentIds, int components)
            throws IOException {
        if (payload.length < 4 || (payload[0] & 0xff) == 0
                || payload.length != 4 + (payload[0] & 0xff) * 2) {
            throw new JpegException("invalid progressive JPEG SOS header");
        }
        int count = payload[0] & 0xff;
        if (count > components) {
            throw new JpegException("progressive JPEG scan selects too many components");
        }
        int[] selected = new int[count];
        int[] dc = new int[count];
        int[] ac = new int[count];
        for (int i = 0; i < count; i++) {
            int id = payload[1 + i * 2] & 0xff;
            selected[i] = componentIndex(componentIds, id);
            for (int previous = 0; previous < i; previous++) {
                if (selected[previous] == selected[i]) {
                    throw new JpegException("progressive JPEG scan repeats a component");
                }
            }
            int tables = payload[2 + i * 2] & 0xff;
            dc[i] = tables >>> 4;
            ac[i] = tables & 0x0f;
        }
        int ss = payload[payload.length - 3] & 0xff;
        int se = payload[payload.length - 2] & 0xff;
        int ahAl = payload[payload.length - 1] & 0xff;
        if (ss > se || se > 63 || (ss == 0 && se != 0)
                || (ss != 0 && count != 1)) {
            throw new JpegException("invalid progressive JPEG spectral selection");
        }
        if ((ahAl >>> 4) > 13 || (ahAl & 0x0f) > 13
                || ((ahAl >>> 4) != 0 && (ahAl >>> 4) != (ahAl & 0x0f) + 1)) {
            throw new JpegException("invalid progressive JPEG successive approximation");
        }
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

    private static void writeFrameHeader(ImageOutputStream output, JpegFrame frame,
            JpegSampling sampling, int frameMarker)
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
            payload[7 + i * 3] = (byte) (frame.components() == 1
                    ? 0x11 : sampling.factorByte(i));
            payload[8 + i * 3] = (byte) (i == 0 ? 0 : 1);
        }
        JpegMarkerWriter.write(output, frameMarker, payload);
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

    private static void writeScanHeader(ImageOutputStream output, JpegScanScript scan)
            throws IOException {
        int[] components = scan.components();
        byte[] payload = new byte[4 + components.length * 2];
        payload[0] = (byte) components.length;
        for (int i = 0; i < components.length; i++) {
            int component = components[i];
            payload[1 + i * 2] = (byte) (component + 1);
            payload[2 + i * 2] = (byte) (component == 0 ? 0 : 0x11);
        }
        payload[payload.length - 3] = (byte) scan.spectralStart();
        payload[payload.length - 2] = (byte) scan.spectralEnd();
        payload[payload.length - 1] = (byte) ((scan.successiveHigh() << 4)
                | scan.successiveLow());
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

    private static int mcusX(int width, JpegSampling sampling) {
        return (width + sampling.maxHorizontal() * 8 - 1)
                / (sampling.maxHorizontal() * 8);
    }

    private static int mcusY(int height, JpegSampling sampling) {
        return (height + sampling.maxVertical() * 8 - 1)
                / (sampling.maxVertical() * 8);
    }

    private static int scanMcuWidth(int width, JpegSampling sampling, JpegScanScript scan) {
        if (scan.components().length == 1) {
            return blocksX(width, sampling, scan.components()[0]);
        }
        return mcusX(width, sampling);
    }

    private static int scanMcuWidth(int width, JpegSampling sampling, ScanHeader scan) {
        if (scan.components.length == 1) {
            return blocksX(width, sampling, scan.components[0]);
        }
        return mcusX(width, sampling);
    }

    private static int scanMcuHeight(int height, JpegSampling sampling, JpegScanScript scan) {
        if (scan.components().length == 1) {
            return blocksY(height, sampling, scan.components()[0]);
        }
        return mcusY(height, sampling);
    }

    private static int scanMcuHeight(int height, JpegSampling sampling, ScanHeader scan) {
        if (scan.components.length == 1) {
            return blocksY(height, sampling, scan.components[0]);
        }
        return mcusY(height, sampling);
    }

    private static int blocksX(int width, JpegSampling sampling, int component) {
        return (sampling.componentWidth(width, component) + 7) / 8;
    }

    private static int blocksY(int height, JpegSampling sampling, int component) {
        return (sampling.componentHeight(height, component) + 7) / 8;
    }

    private static int indexOfBlock(int bx, int by, int width, int height,
            JpegSampling sampling, int component) {
        return by * blocksX(width, sampling, component) + bx;
    }

    private static int componentSample(JpegFrame frame, int componentX, int componentY,
            int component, JpegSampling sampling) {
        int xStart = componentX * sampling.maxHorizontal() / sampling.horizontal(component);
        int xEnd = ((componentX + 1) * sampling.maxHorizontal()
                + sampling.horizontal(component) - 1) / sampling.horizontal(component);
        int yStart = componentY * sampling.maxVertical() / sampling.vertical(component);
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

    private static int u16(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xff) << 8) | (bytes[offset + 1] & 0xff);
    }

    private static final class ProgressiveState {
        private int eobRun;
    }

    private static final class ScanData {
        private final List<byte[]> entropySegments;
        private final List<Integer> restartMarkers;

        private ScanData(List<byte[]> entropySegments, List<Integer> restartMarkers) {
            this.entropySegments = entropySegments;
            this.restartMarkers = restartMarkers;
        }
    }

    private static final class ProgressiveCoefficientState {
        private final int[][] levels;
        private final int width;
        private final int height;
        private final JpegSampling sampling;

        private ProgressiveCoefficientState(int width, int height, int components,
                JpegSampling sampling) {
            this.width = width;
            this.height = height;
            this.sampling = sampling;
            this.levels = new int[components][];
            for (int component = 0; component < components; component++) {
                int count = blocksX(width, sampling, component)
                        * blocksY(height, sampling, component);
                this.levels[component] = new int[count * 64];
                for (int i = 0; i < this.levels[component].length; i++) {
                    this.levels[component][i] = -1;
                }
            }
        }

        private void validate(ScanHeader scan) throws IOException {
            for (int component : scan.components) {
                int mcuWidth = scanMcuWidth(width, sampling, scan);
                int mcuHeight = scanMcuHeight(height, sampling, scan);
                for (int mcuY = 0; mcuY < mcuHeight; mcuY++) {
                    for (int mcuX = 0; mcuX < mcuWidth; mcuX++) {
                        int blockCountY = scan.components.length == 1
                                ? 1 : sampling.vertical(component);
                        int blockCountX = scan.components.length == 1
                                ? 1 : sampling.horizontal(component);
                        for (int blockY = 0; blockY < blockCountY; blockY++) {
                            for (int blockX = 0; blockX < blockCountX; blockX++) {
                                int bx = scan.components.length == 1
                                        ? mcuX : mcuX * sampling.horizontal(component) + blockX;
                                int by = scan.components.length == 1
                                        ? mcuY : mcuY * sampling.vertical(component) + blockY;
                                int block = indexOfBlock(bx, by, width, height, sampling, component);
                                int start = scan.ss == 0 && scan.se == 0 ? 0 : scan.ss;
                                int end = scan.ss == 0 && scan.se == 0 ? 0 : scan.se;
                                for (int zig = start; zig <= end; zig++) {
                                    int index = block * 64 + JpegZigZag.ORDER[zig];
                                    int current = levels[component][index];
                                    if (scan.ah == 0) {
                                        if (current >= 0) {
                                            throw new JpegException(
                                                    "progressive JPEG coefficient band is repeated");
                                        }
                                    } else if (current != scan.ah) {
                                        throw new JpegException(
                                                "progressive JPEG refinement has no matching first scan");
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        private void apply(ScanHeader scan) {
            for (int component : scan.components) {
                int mcuWidth = scanMcuWidth(width, sampling, scan);
                int mcuHeight = scanMcuHeight(height, sampling, scan);
                for (int mcuY = 0; mcuY < mcuHeight; mcuY++) {
                    for (int mcuX = 0; mcuX < mcuWidth; mcuX++) {
                        int blockCountY = scan.components.length == 1
                                ? 1 : sampling.vertical(component);
                        int blockCountX = scan.components.length == 1
                                ? 1 : sampling.horizontal(component);
                        for (int blockY = 0; blockY < blockCountY; blockY++) {
                            for (int blockX = 0; blockX < blockCountX; blockX++) {
                                int bx = scan.components.length == 1
                                        ? mcuX : mcuX * sampling.horizontal(component) + blockX;
                                int by = scan.components.length == 1
                                        ? mcuY : mcuY * sampling.vertical(component) + blockY;
                                int block = indexOfBlock(bx, by, width, height, sampling, component);
                                int start = scan.ss == 0 && scan.se == 0 ? 0 : scan.ss;
                                int end = scan.ss == 0 && scan.se == 0 ? 0 : scan.se;
                                for (int zig = start; zig <= end; zig++) {
                                    levels[component][block * 64 + JpegZigZag.ORDER[zig]]
                                            = scan.al;
                                }
                            }
                        }
                    }
                }
            }
        }

        private void requireComplete() throws IOException {
            for (int[] component : levels) {
                for (int level : component) {
                    if (level < 0) {
                        throw new JpegException("progressive JPEG is missing coefficient scans");
                    }
                }
            }
        }
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

    private static int successiveValue(int value, int low) {
        int magnitude = Math.abs(value) >>> low;
        return value < 0 ? -magnitude : magnitude;
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
        private final JpegSampling sampling;

        private FrameHeader(int width, int height, int components, int[] componentIds,
                int[] quantization, JpegSampling sampling) {
            this.width = width;
            this.height = height;
            this.precision = 8;
            this.components = components;
            this.componentIds = componentIds;
            this.quantization = quantization;
            this.sampling = sampling;
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
