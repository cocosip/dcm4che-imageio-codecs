package io.github.cocosip.dcm4che.imageio.codecs.jpeg.internal;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;

/** Arithmetic-coded JPEG frame codec for the sequential, progressive, and lossless SOF families. */
public final class ArithmeticJpegCodec {
    private ArithmeticJpegCodec() {
    }

    public static byte[] encodeSequential(JpegFrame frame) throws IOException {
        return encodeSequential(frame, 0);
    }

    public static byte[] encodeSequential(JpegFrame frame, int restartInterval)
            throws IOException {
        return encodeDct(frame, 0xc9, restartInterval, 0, 1, 5);
    }

    public static byte[] encodeSequential(JpegFrame frame, int restartInterval,
            int dcL, int dcU, int acK) throws IOException {
        return encodeDct(frame, 0xc9, restartInterval, dcL, dcU, acK);
    }

    public static byte[] encodeProgressive(JpegFrame frame) throws IOException {
        return encodeProgressive(frame, 0, 1, 5);
    }

    /** Encodes SOF10 using caller-selected DAC DC L/U and AC Kx conditioning. */
    public static byte[] encodeProgressive(JpegFrame frame, int dcL, int dcU, int acK)
            throws IOException {
        validateDctFrame(frame);
        validateConditioning(dcL, dcU, acK);
        QuantizationTable[] quant = {
            QuantizationTable.of(JpegTables.standardLuminanceQuantization()),
            QuantizationTable.of(JpegTables.standardChrominanceQuantization())};
        int blocksX = (frame.width() + 7) / 8;
        int blocksY = (frame.height() + 7) / 8;
        int[][][] coefficients = computeCoefficients(frame, quant, blocksX, blocksY);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        MemoryCacheImageOutputStream output = new MemoryCacheImageOutputStream(bytes);
        output.write(0xff);
        output.write(0xd8);
        writeQuantization(output, 0, JpegTables.standardLuminanceQuantization());
        if (frame.components() == 3) {
            writeQuantization(output, 1, JpegTables.standardChrominanceQuantization());
        }
        writeFrameHeader(output, frame, 0xca);
        JpegMarkerWriter.write(output, 0xcc,
                new byte[] {0, (byte) ((dcU << 4) | dcL), 0x10, (byte) acK});
        JpegMarkerWriter.write(output, 0xda, scanHeader(frame.components(), 0, 0, 0, 0));
        output.write(JpegArithmeticDct.encodeDc(coefficients, blocksX, blocksY,
                frame.components(), dcL, dcU));
        for (int component = 0; component < frame.components(); component++) {
            JpegMarkerWriter.write(output, 0xda,
                    scanHeader(component + 1, component, 1, 63, 0));
            output.write(JpegArithmeticDct.encodeAc(coefficients, blocksX, blocksY, component,
                    acK));
        }
        output.write(0xff);
        output.write(0xd9);
        output.flush();
        return bytes.toByteArray();
    }

    public static byte[] encodeLossless(JpegFrame frame) throws IOException {
        return encodeLossless(frame, 1, 0);
    }

    public static byte[] encodeLossless(JpegFrame frame, int predictor) throws IOException {
        return encodeLossless(frame, predictor, 0);
    }

    public static byte[] encodeLossless(JpegFrame frame, int predictor, int restartInterval)
            throws IOException {
        return encodeLossless(frame, predictor, restartInterval, 0, 1);
    }

    /** Encodes SOF11 with caller-selected DAC DC conditioning values. */
    public static byte[] encodeLossless(JpegFrame frame, int predictor, int restartInterval,
            int dcL, int dcU) throws IOException {
        return encodeLossless(frame, 0xcb, predictor, restartInterval, dcL, dcU);
    }

    static byte[] encodeDifferentialSequential(JpegFrame frame) throws IOException {
        return encodeDct(frame, 0xcd);
    }

    static byte[] encodeDifferentialProgressive(JpegFrame frame) throws IOException {
        return encodeDct(frame, 0xce);
    }

    static byte[] encodeDifferentialLossless(JpegFrame frame) throws IOException {
        return encodeLossless(frame, 0xcf, 1, 0);
    }

    public static JpegFrame decode(byte[] data) throws IOException {
        if (data == null || data.length < 4) {
            throw new JpegException("arithmetic JPEG frame is truncated");
        }
        MemoryCacheImageInputStream input = new MemoryCacheImageInputStream(
                new ByteArrayInputStream(data));
        if (input.read() != 0xff || input.read() != 0xd8) {
            throw new JpegException("arithmetic JPEG frame does not start with SOI");
        }
        Map<Integer, QuantizationTable> quant = new HashMap<Integer, QuantizationTable>();
        int width = 0;
        int height = 0;
        int precision = 0;
        int components = 0;
        int frameMarker = 0;
        int restartInterval = 0;
        Conditioning conditioning = new Conditioning(0, 1, 5);
        int[][][] progressiveCoefficients = null;
        int progressiveBlocksX = 0;
        int progressiveBlocksY = 0;
        boolean progressiveScanSeen = false;
        boolean progressiveDcSeen = false;
        boolean[] progressiveAcSeen = null;
        boolean dacSeen = false;
        JpegMarkerReader markers = new JpegMarkerReader(input);
        while (true) {
            JpegMarker marker = markers.next();
            int code = marker.code();
            byte[] payload = marker.payload();
            if (code == 0xdb) {
                parseQuantization(payload, quant);
            } else if (isArithmeticFrame(code)) {
                if (payload.length < 6 || payload.length != 6 + (payload[5] & 0xff) * 3) {
                    throw new JpegException("invalid arithmetic JPEG frame header");
                }
                frameMarker = code;
                precision = payload[0] & 0xff;
                height = u16(payload, 1);
                width = u16(payload, 3);
                components = payload[5] & 0xff;
                if (width == 0 || height == 0 || (components != 1 && components != 3)) {
                    throw new JpegException("invalid arithmetic JPEG dimensions/components");
                }
                for (int component = 0; component < components; component++) {
                    if (payload[7 + component * 3] != 0x11) {
                        throw new JpegException("arithmetic JPEG sampling other than 1x1 is unsupported");
                    }
                }
            } else if (code == 0xcc) {
                conditioning = parseArithmeticConditioning(payload);
                dacSeen = true;
            } else if (code == 0xdd) {
                if (payload.length != 2) {
                    throw new JpegException("invalid arithmetic JPEG restart interval");
                }
                restartInterval = u16(payload, 0);
            } else if (code == 0xda) {
                if (frameMarker == 0 || !dacSeen
                        || (isDct(frameMarker) && !hasQuantization(quant, components))) {
                    throw new JpegException("arithmetic JPEG scan is missing frame or tables");
                }
                ScanHeader scan = parseScan(payload, components, !isDct(frameMarker));
                if (isDct(frameMarker) && frameMarker != 0xca
                        && scan.componentIds.length != components) {
                    throw new JpegException("arithmetic sequential scan must include all components");
                }
                EntropySegments entropy = readEntropySegments(input);
                if (isDct(frameMarker)) {
                    if (frameMarker == 0xca && (entropy.segments.size() != 1
                            || !entropy.restartMarkers.isEmpty())) {
                        throw new JpegException("arithmetic DCT restart segments are not supported");
                    }
                    if (frameMarker == 0xca) {
                        if (progressiveCoefficients == null) {
                            progressiveBlocksX = (width + 7) / 8;
                            progressiveBlocksY = (height + 7) / 8;
                            progressiveCoefficients = new int[components]
                                    [progressiveBlocksX * progressiveBlocksY][64];
                            progressiveAcSeen = new boolean[components];
                        }
                        if (scan.ss == 0 && scan.se == 0 && scan.ah == 0 && scan.al == 0
                                && !progressiveDcSeen && scan.componentIds.length == components) {
                            progressiveCoefficients = JpegArithmeticDct.decodeDc(
                                    entropy.segments.get(0), progressiveBlocksX,
                                    progressiveBlocksY, components, conditioning.dcL,
                                    conditioning.dcU);
                            progressiveDcSeen = true;
                        } else if (scan.ss == 1 && scan.se == 63 && scan.ah == 0 && scan.al == 0
                                && scan.componentIds.length == 1 && progressiveDcSeen
                                && !progressiveAcSeen[scan.componentIds[0] - 1]) {
                            JpegArithmeticDct.decodeAc(entropy.segments.get(0), progressiveBlocksX,
                                    progressiveBlocksY, scan.componentIds[0] - 1,
                                    progressiveCoefficients, conditioning.acK);
                            progressiveAcSeen[scan.componentIds[0] - 1] = true;
                        } else {
                            throw new JpegException("unsupported arithmetic progressive scan");
                        }
                        progressiveScanSeen = true;
                        continue;
                    }
                    return decodeDctEntropy(width, height, components, precision, quant, scan,
                            entropy, restartInterval, conditioning);
                }
                return JpegArithmeticLossless.decode(width, height, components, precision,
                        scan.predictor, restartInterval, entropy.segments,
                        entropy.restartMarkers, conditioning.dcL, conditioning.dcU);
            } else if (code == 0xd9) {
                if (frameMarker == 0xca && progressiveScanSeen && progressiveDcSeen
                        && allSeen(progressiveAcSeen) && progressiveCoefficients != null) {
                    return decodeDctCoefficients(width, height, components, precision, quant,
                            progressiveCoefficients, progressiveBlocksX, progressiveBlocksY);
                }
                throw new JpegException("arithmetic JPEG frame has no scan data");
            } else if (code == 0xd8 || (code >= 0xd0 && code <= 0xd7)) {
                throw new JpegException("unexpected arithmetic JPEG marker: 0x"
                        + Integer.toHexString(code));
            }
        }
    }

    private static byte[] encodeDct(JpegFrame frame, int frameMarker) throws IOException {
        return encodeDct(frame, frameMarker, 0, 0, 1, 5);
    }

    private static byte[] encodeDct(JpegFrame frame, int frameMarker, int restartInterval)
            throws IOException {
        return encodeDct(frame, frameMarker, restartInterval, 0, 1, 5);
    }

    private static byte[] encodeDct(JpegFrame frame, int frameMarker, int restartInterval,
            int dcL, int dcU, int acK)
            throws IOException {
        validateDctFrame(frame);
        if (restartInterval < 0 || restartInterval > 0xffff) {
            throw new JpegException("arithmetic JPEG restart interval must fit in 16 bits");
        }
        validateConditioning(dcL, dcU, acK);
        QuantizationTable[] quant = {
            QuantizationTable.of(JpegTables.standardLuminanceQuantization()),
            QuantizationTable.of(JpegTables.standardChrominanceQuantization())};
        int blocksX = (frame.width() + 7) / 8;
        int blocksY = (frame.height() + 7) / 8;
        int[][][] coefficients = computeCoefficients(frame, quant, blocksX, blocksY);
        JpegArithmeticDct.Encoded entropy = JpegArithmeticDct.encodeSegments(coefficients,
                blocksX, blocksY, frame.components(), restartInterval, dcL, dcU, acK);
        return wrap(frame, frameMarker, quant, restartInterval, entropy.segments,
                entropy.restartMarkers, frame.components() == 3, dcL, dcU, acK);
    }

    private static int[][][] computeCoefficients(JpegFrame frame, QuantizationTable[] quant,
            int blocksX, int blocksY) {
        int[][][] coefficients = new int[frame.components()][blocksX * blocksY][64];
        for (int by = 0; by < blocksY; by++) {
            for (int bx = 0; bx < blocksX; bx++) {
                for (int component = 0; component < frame.components(); component++) {
                    double[] block = new double[64];
                    for (int y = 0; y < 8; y++) {
                        for (int x = 0; x < 8; x++) {
                            int sourceX = Math.min(frame.width() - 1, bx * 8 + x);
                            int sourceY = Math.min(frame.height() - 1, by * 8 + y);
                            block[y * 8 + x] = frame.sample(sourceX, sourceY, component)
                                    - (1 << (frame.precision() - 1));
                        }
                    }
                    double[] transformed = JpegDct.forward(block);
                    int[] target = coefficients[component][by * blocksX + bx];
                    for (int i = 0; i < 64; i++) {
                        target[i] = (int) Math.round(transformed[i]
                                / quant[component == 0 ? 0 : 1].get(JpegZigZag.positionOf(i)));
                    }
                }
            }
        }
        return coefficients;
    }

    private static void validateDctFrame(JpegFrame frame) throws IOException {
        if (frame == null || (frame.components() != 1 && frame.components() != 3)
                || frame.precision() < 8 || frame.precision() > 12) {
            throw new JpegException("arithmetic DCT JPEG requires unsigned 8-12 bit mono/RGB samples");
        }
    }

    private static byte[] encodeLossless(JpegFrame frame, int frameMarker, int predictor,
            int restartInterval) throws IOException {
        return encodeLossless(frame, frameMarker, predictor, restartInterval, 0, 1);
    }

    private static byte[] encodeLossless(JpegFrame frame, int frameMarker, int predictor,
            int restartInterval, int dcL, int dcU) throws IOException {
        JpegArithmeticLossless.Encoded entropy = JpegArithmeticLossless.encode(frame, predictor,
                restartInterval, dcL, dcU);
        return wrapLossless(frame, frameMarker, predictor, restartInterval,
                entropy.segments, entropy.restartMarkers, dcL, dcU);
    }

    private static byte[] wrap(JpegFrame frame, int frameMarker, QuantizationTable[] quant,
            byte[] entropy, boolean color) throws IOException {
        List<byte[]> segments = new ArrayList<byte[]>();
        segments.add(entropy);
        return wrap(frame, frameMarker, quant, 0, segments, new ArrayList<Integer>(), color,
                0, 1, 5);
    }

    private static byte[] wrap(JpegFrame frame, int frameMarker, QuantizationTable[] quant,
            int restartInterval, List<byte[]> segments, List<Integer> restartMarkers,
            boolean color, int dcL, int dcU, int acK) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        MemoryCacheImageOutputStream output = new MemoryCacheImageOutputStream(bytes);
        output.write(0xff);
        output.write(0xd8);
        if (quant != null) {
            writeQuantization(output, 0, JpegTables.standardLuminanceQuantization());
            if (color) {
                writeQuantization(output, 1, JpegTables.standardChrominanceQuantization());
            }
        }
        writeFrameHeader(output, frame, frameMarker);
        if (restartInterval != 0) {
            JpegMarkerWriter.write(output, 0xdd,
                    new byte[] {(byte) (restartInterval >>> 8), (byte) restartInterval});
        }
        JpegMarkerWriter.write(output, 0xcc,
                new byte[] {0, (byte) ((dcU << 4) | dcL), 0x10, (byte) acK});
        JpegMarkerWriter.write(output, 0xda, scanHeader(frame.components()));
        for (int i = 0; i < segments.size(); i++) {
            output.write(segments.get(i));
            if (i < restartMarkers.size()) {
                output.write(0xff);
                output.write(restartMarkers.get(i));
            }
        }
        output.write(0xff);
        output.write(0xd9);
        output.flush();
        return bytes.toByteArray();
    }

    private static JpegFrame decodeDctEntropy(int width, int height, int components, int precision,
            Map<Integer, QuantizationTable> quant, ScanHeader scan, EntropySegments entropy,
            int restartInterval, Conditioning conditioning)
            throws IOException {
        int blocksX = (width + 7) / 8;
        int blocksY = (height + 7) / 8;
        int[][][] coefficients = restartInterval == 0
                ? JpegArithmeticDct.decode(entropy.segments.get(0), blocksX, blocksY, components,
                        conditioning.dcL, conditioning.dcU, conditioning.acK)
                : JpegArithmeticDct.decodeSegments(entropy.segments, blocksX, blocksY,
                        components, restartInterval, entropy.restartMarkers,
                        conditioning.dcL, conditioning.dcU, conditioning.acK);
        return decodeDctCoefficients(width, height, components, precision, quant, coefficients,
                blocksX, blocksY);
    }

    private static JpegFrame decodeDctCoefficients(int width, int height, int components,
            int precision, Map<Integer, QuantizationTable> quant, int[][][] coefficients,
            int blocksX, int blocksY) throws IOException {
        int[] samples = new int[width * height * components];
        for (int by = 0; by < blocksY; by++) {
            for (int bx = 0; bx < blocksX; bx++) {
                for (int component = 0; component < components; component++) {
                    int[] block = coefficients[component][by * blocksX + bx];
                    QuantizationTable table = quant.get(component == 0 ? 0 : 1);
                    if (table == null) {
                        throw new JpegException("arithmetic JPEG scan references missing quantization table");
                    }
                    double[] restored = new double[64];
                    for (int i = 0; i < 64; i++) {
                        restored[i] = block[i] * table.get(JpegZigZag.positionOf(i));
                    }
                    double[] pixels = JpegDct.inverse(restored);
                    for (int y = 0; y < 8 && by * 8 + y < height; y++) {
                        for (int x = 0; x < 8 && bx * 8 + x < width; x++) {
                            int value = (int) Math.round(pixels[y * 8 + x]
                                    + (1 << (precision - 1)));
                            samples[((by * 8 + y) * width + bx * 8 + x) * components + component]
                                    = Math.max(0, Math.min((1 << precision) - 1, value));
                        }
                    }
                }
            }
        }
        return JpegFrame.of(width, height, components, samples, precision);
    }

    private static boolean isArithmeticFrame(int marker) {
        return marker == 0xc9 || marker == 0xca || marker == 0xcb
                || marker == 0xcd || marker == 0xce || marker == 0xcf;
    }

    private static boolean isDct(int marker) {
        return marker == 0xc9 || marker == 0xca || marker == 0xcd || marker == 0xce;
    }

    private static boolean hasQuantization(Map<Integer, QuantizationTable> quant, int components) {
        return quant.containsKey(0) && (components == 1 || quant.containsKey(1));
    }

    private static void parseQuantization(byte[] payload, Map<Integer, QuantizationTable> tables) {
        int offset = 0;
        while (offset < payload.length) {
            int specification = payload[offset++] & 0xff;
            if ((specification >>> 4) != 0 || offset + 64 > payload.length) {
                throw new IllegalArgumentException("invalid arithmetic JPEG quantization table");
            }
            int[] values = new int[64];
            for (int i = 0; i < 64; i++) {
                values[i] = payload[offset++] & 0xff;
            }
            tables.put(specification & 0x0f, QuantizationTable.of(values));
        }
    }

    private static Conditioning parseArithmeticConditioning(byte[] payload) throws IOException {
        if (payload.length == 0 || (payload.length & 1) != 0) {
            throw new JpegException("invalid arithmetic JPEG DAC marker");
        }
        Conditioning result = new Conditioning(0, 1, 5);
        for (int offset = 0; offset < payload.length; offset += 2) {
            int table = payload[offset] & 0xff;
            int value = payload[offset + 1] & 0xff;
            int id = table & 0x0f;
            if (id > 3) {
                throw new JpegException("invalid arithmetic JPEG DAC table id");
            }
            if ((table & 0xf0) == 0) {
                int l = value & 0x0f;
                int u = value >>> 4;
                if (id != 0 || l > u || u > 15) {
                    throw new JpegException("invalid arithmetic JPEG DC conditioning");
                }
                result = new Conditioning(l, u, result.acK);
            } else if ((table & 0xf0) != 0x10) {
                throw new JpegException("invalid arithmetic JPEG DAC table class");
            } else {
                if (id != 0 || (value & 0xf0) != 0) {
                    throw new JpegException("invalid arithmetic JPEG AC conditioning");
                }
                result = new Conditioning(result.dcL, result.dcU, value & 0x0f);
            }
        }
        validateConditioning(result.dcL, result.dcU, result.acK);
        return result;
    }

    private static void validateConditioning(int dcL, int dcU, int acK) throws IOException {
        if (dcL < 0 || dcL > dcU || dcU > 15 || acK < 0 || acK > 63) {
            throw new JpegException("invalid arithmetic JPEG conditioning parameters");
        }
    }

    private static ScanHeader parseScan(byte[] payload, int components, boolean lossless)
            throws IOException {
        int scanComponents = payload.length < 1 ? 0 : payload[0] & 0xff;
        if (scanComponents < 1 || scanComponents > components
                || payload.length != 4 + scanComponents * 2) {
            throw new JpegException("invalid arithmetic JPEG SOS header");
        }
        int[] componentIds = new int[scanComponents];
        for (int component = 0; component < scanComponents; component++) {
            componentIds[component] = payload[1 + component * 2] & 0xff;
            if (componentIds[component] < 1 || componentIds[component] > components) {
                throw new JpegException("invalid arithmetic JPEG scan component");
            }
        }
        int ss = payload[payload.length - 3] & 0xff;
        int se = payload[payload.length - 2] & 0xff;
        int ahAl = payload[payload.length - 1] & 0xff;
        if ((lossless ? (ss < 1 || ss > 7 || se != 0) : (ss > se || se > 63))
                || (ahAl & 0x0f) != 0) {
            throw new JpegException("unsupported arithmetic JPEG scan range");
        }
        for (int component = 0; component < scanComponents; component++) {
            int selector = payload[2 + component * 2] & 0xff;
            int expected = 0;
            if (selector != expected) {
                throw new JpegException("unsupported arithmetic JPEG conditioning table");
            }
        }
        return new ScanHeader(componentIds, ss, se, ahAl >>> 4, ahAl & 0x0f);
    }

    private static byte[] scanHeader(int components) {
        return scanHeader(components, 0, 0, 63, 0);
    }

    private static byte[] scanHeader(int components, int componentOffset,
            int ss, int se, int ahAl) {
        byte[] payload = new byte[4 + components * 2];
        payload[0] = (byte) components;
        for (int component = 0; component < components; component++) {
            payload[1 + component * 2] = (byte) (componentOffset + component + 1);
            payload[2 + component * 2] = 0;
        }
        payload[payload.length - 3] = (byte) ss;
        payload[payload.length - 2] = (byte) se;
        payload[payload.length - 1] = (byte) ahAl;
        return payload;
    }

    private static byte[] losslessScanHeader(int components, int predictor) {
        byte[] payload = new byte[4 + components * 2];
        payload[0] = (byte) components;
        for (int component = 0; component < components; component++) {
            payload[1 + component * 2] = (byte) (component + 1);
            payload[2 + component * 2] = 0;
        }
        payload[payload.length - 3] = (byte) predictor;
        payload[payload.length - 2] = 0;
        payload[payload.length - 1] = 0;
        return payload;
    }

    private static byte[] wrapLossless(JpegFrame frame, int frameMarker, int predictor,
            int restartInterval, List<byte[]> segments, List<Integer> restartMarkers,
            int dcL, int dcU)
            throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        MemoryCacheImageOutputStream output = new MemoryCacheImageOutputStream(bytes);
        output.write(0xff);
        output.write(0xd8);
        writeFrameHeader(output, frame, frameMarker);
        if (restartInterval != 0) {
            JpegMarkerWriter.write(output, 0xdd,
                    new byte[] {(byte) (restartInterval >>> 8), (byte) restartInterval});
        }
        JpegMarkerWriter.write(output, 0xcc, new byte[] {0, (byte) ((dcU << 4) | dcL)});
        JpegMarkerWriter.write(output, 0xda, losslessScanHeader(frame.components(), predictor));
        for (int i = 0; i < segments.size(); i++) {
            output.write(segments.get(i));
            if (i < restartMarkers.size()) {
                output.write(0xff);
                output.write(restartMarkers.get(i));
            }
        }
        output.write(0xff);
        output.write(0xd9);
        output.flush();
        return bytes.toByteArray();
    }

    private static EntropySegments readEntropySegments(ImageInputStream input) throws IOException {
        List<byte[]> segments = new ArrayList<byte[]>();
        List<Integer> restartMarkers = new ArrayList<Integer>();
        ByteArrayOutputStream segment = new ByteArrayOutputStream();
        while (true) {
            int value = input.read();
            if (value < 0) {
                throw new JpegException("truncated arithmetic JPEG scan");
            }
            if (value != 0xff) {
                segment.write(value);
                continue;
            }
            int next = input.read();
            if (next < 0) {
                throw new JpegException("truncated arithmetic JPEG scan marker");
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
                return new EntropySegments(segments, restartMarkers);
            }
        }
    }

    private static void writeFrameHeader(ImageOutputStream output, JpegFrame frame, int marker)
            throws IOException {
        byte[] payload = new byte[6 + frame.components() * 3];
        payload[0] = (byte) frame.precision();
        payload[1] = (byte) (frame.height() >>> 8);
        payload[2] = (byte) frame.height();
        payload[3] = (byte) (frame.width() >>> 8);
        payload[4] = (byte) frame.width();
        payload[5] = (byte) frame.components();
        for (int component = 0; component < frame.components(); component++) {
            payload[6 + component * 3] = (byte) (component + 1);
            payload[7 + component * 3] = 0x11;
            payload[8 + component * 3] = (byte) (component == 0 ? 0 : 1);
        }
        JpegMarkerWriter.write(output, marker, payload);
    }

    private static void writeQuantization(ImageOutputStream output, int id, int[] values)
            throws IOException {
        byte[] payload = new byte[65];
        payload[0] = (byte) id;
        for (int i = 0; i < values.length; i++) {
            payload[i + 1] = (byte) values[i];
        }
        JpegMarkerWriter.write(output, 0xdb, payload);
    }

    private static int u16(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xff) << 8) | (bytes[offset + 1] & 0xff);
    }

    private static boolean allSeen(boolean[] values) {
        if (values == null) {
            return false;
        }
        for (boolean value : values) {
            if (!value) {
                return false;
            }
        }
        return true;
    }

    private static final class ScanHeader {
        private final int[] componentIds;
        private final int ss;
        private final int se;
        private final int predictor;
        private final int pointTransform;
        private final int ah;
        private final int al;

        private ScanHeader(int[] componentIds, int ss, int se, int successiveHigh,
                int successiveLow) {
            this.componentIds = componentIds;
            this.ss = ss;
            this.se = se;
            this.predictor = ss;
            this.pointTransform = successiveLow;
            this.ah = successiveHigh;
            this.al = successiveLow;
        }
    }

    private static final class EntropySegments {
        private final List<byte[]> segments;
        private final List<Integer> restartMarkers;

        private EntropySegments(List<byte[]> segments, List<Integer> restartMarkers) {
            this.segments = segments;
            this.restartMarkers = restartMarkers;
        }
    }

    private static final class Conditioning {
        private final int dcL;
        private final int dcU;
        private final int acK;

        private Conditioning(int dcL, int dcU, int acK) {
            this.dcL = dcL;
            this.dcU = dcU;
            this.acK = acK;
        }
    }

}
