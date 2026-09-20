package io.github.cocosip.dcm4che.imageio.codecs.jpeg.internal;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;

/** JPEG Lossless Process 14 predictive Huffman codec. */
public final class LosslessJpegCodec {
    private static final HuffmanTable DEFAULT_HUFFMAN = createDefaultHuffman();

    private LosslessJpegCodec() {
    }

    public static byte[] encode(JpegFrame frame, int predictor) throws IOException {
        return encode(frame, predictor, 0, 0);
    }

    public static byte[] encode(JpegFrame frame, int predictor, int restartInterval)
            throws IOException {
        return encode(frame, predictor, restartInterval, 0);
    }

    public static byte[] encode(JpegFrame frame, int predictor, int restartInterval,
            int pointTransform) throws IOException {
        return encode(frame, predictor, restartInterval, pointTransform, 0xc3);
    }

    static byte[] encode(JpegFrame frame, int predictor, int restartInterval,
            int pointTransform, int frameMarker) throws IOException {
        validateFrame(frame, predictor, restartInterval, pointTransform);
        int transformedPrecision = frame.precision() - pointTransform;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        MemoryCacheImageOutputStream output = new MemoryCacheImageOutputStream(bytes);
        output.write(0xff);
        output.write(0xd8);
        if (frameMarker != 0xc3 && frameMarker != 0xc7) {
            throw new IllegalArgumentException("unsupported lossless JPEG frame marker");
        }
        JpegMarkerWriter.write(output, frameMarker, frameHeader(frame));
        JpegMarkerWriter.write(output, 0xc4, huffmanDefinition());
        if (restartInterval != 0) {
            JpegMarkerWriter.write(output, 0xdd, new byte[] {
                    (byte) (restartInterval >>> 8), (byte) restartInterval});
        }
        JpegMarkerWriter.write(output, 0xda, scanHeader(frame.components(), predictor,
                pointTransform));

        BitWriter bits = new BitWriter(output);
        int[] reconstructed = new int[frame.width() * frame.height() * frame.components()];
        int restartStartMcu = 0;
        int restartIndex = 0;
        for (int y = 0; y < frame.height(); y++) {
            for (int x = 0; x < frame.width(); x++) {
                int mcuIndex = y * frame.width() + x;
                if (restartInterval != 0 && mcuIndex != 0
                        && mcuIndex % restartInterval == 0) {
                    bits.flush();
                    output.write(0xff);
                    output.write(0xd0 + restartIndex);
                    restartIndex = (restartIndex + 1) & 7;
                    restartStartMcu = mcuIndex;
                }
                for (int component = 0; component < frame.components(); component++) {
                    int index = indexOf(frame.width(), frame.components(), x, y, component);
                    int sample = frame.sample(x, y, component) >>> pointTransform;
                    int difference = normalizeDifference(sample
                            - predict(reconstructed, frame.width(), frame.components(), x, y,
                                    component, transformedPrecision, predictor, restartStartMcu));
                    int category = category(difference);
                    HuffmanCodec.encodeSymbol(bits, DEFAULT_HUFFMAN, category);
                    if (category > 0 && category != 16) {
                        writeAmplitude(bits, difference, category);
                    }
                    reconstructed[index] = sample;
                }
            }
        }
        bits.flush();
        output.write(0xff);
        output.write(0xd9);
        output.flush();
        return bytes.toByteArray();
    }

    public static JpegFrame decode(byte[] data) throws IOException {
        return decode(data, 0);
    }

    public static JpegFrame decode(byte[] data, int expectedPredictor) throws IOException {
        return decode(data, expectedPredictor, 0xc3);
    }

    static JpegFrame decode(byte[] data, int expectedPredictor, int expectedFrameMarker)
            throws IOException {
        ParsedFrame parsed = parse(data, expectedFrameMarker);
        if (expectedPredictor != 0 && parsed.predictor != expectedPredictor) {
            throw new JpegException("JPEG Lossless predictor does not match the requested process");
        }
        int[] samples = new int[parsed.width * parsed.height * parsed.components];
        int[] transformedSamples = new int[samples.length];
        int totalMcu = parsed.width * parsed.height;
        int mcu = 0;
        int restartStartMcu = 0;
        int restartIndex = 0;
        int transformedPrecision = parsed.precision - parsed.pointTransform;
        for (int segmentIndex = 0; segmentIndex < parsed.entropySegments.size(); segmentIndex++) {
            int segmentEnd = parsed.restartInterval == 0
                    ? totalMcu
                    : Math.min(totalMcu, mcu + parsed.restartInterval);
            MemoryCacheImageInputStream entropyInput = new MemoryCacheImageInputStream(
                    new ByteArrayInputStream(parsed.entropySegments.get(segmentIndex)));
            BitReader bits = new BitReader(entropyInput);
            while (mcu < segmentEnd) {
                int y = mcu / parsed.width;
                int x = mcu % parsed.width;
                for (int component = 0; component < parsed.components; component++) {
                    int category = HuffmanCodec.decodeSymbol(bits, parsed.huffman);
                    if (category > transformedPrecision + 1
                            || category == 16 && transformedPrecision < 16) {
                        throw new JpegException("invalid JPEG Lossless difference category");
                    }
                    int difference = category == 0 ? 0
                            : category == 16 ? 1 << 15
                            : readAmplitude(bits, category);
                    int prediction = predict(transformedSamples, parsed.width, parsed.components, x, y,
                            component, transformedPrecision, parsed.predictor, restartStartMcu);
                    int transformedSample = normalizeSample(prediction + difference,
                            transformedPrecision);
                    int sample = transformedSample << parsed.pointTransform;
                    if (sample < 0 || sample > (1 << parsed.precision) - 1) {
                        throw new JpegException("JPEG Lossless sample is outside precision range");
                    }
                    int index = indexOf(parsed.width, parsed.components, x, y, component);
                    transformedSamples[index] = transformedSample;
                    samples[index] = sample;
                }
                mcu++;
            }
            if (parsed.restartInterval != 0 && mcu < totalMcu) {
                if (segmentIndex >= parsed.restartMarkers.size()
                        || parsed.restartMarkers.get(segmentIndex) != 0xd0 + restartIndex) {
                    throw new JpegException("JPEG Lossless restart marker sequence is invalid");
                }
                restartIndex = (restartIndex + 1) & 7;
                restartStartMcu = mcu;
            }
        }
        if (mcu != totalMcu || (parsed.restartInterval == 0 && !parsed.restartMarkers.isEmpty())) {
            throw new JpegException("JPEG Lossless scan does not match restart interval");
        }
        return JpegFrame.of(parsed.width, parsed.height, parsed.components, samples,
                parsed.precision);
    }

    private static void validateFrame(JpegFrame frame, int predictor, int restartInterval,
            int pointTransform) {
        if (frame == null) {
            throw new NullPointerException("frame");
        }
        if (frame.precision() < 8 || frame.precision() > 16) {
            throw new IllegalArgumentException("JPEG Lossless precision must be between 8 and 16 bits");
        }
        if (predictor < 1 || predictor > 7) {
            throw new IllegalArgumentException("JPEG Lossless predictor must be between 1 and 7");
        }
        if (restartInterval < 0 || restartInterval > 0xffff) {
            throw new IllegalArgumentException("JPEG Lossless restart interval must fit in 16 bits");
        }
        if (pointTransform < 0 || pointTransform >= frame.precision()) {
            throw new IllegalArgumentException("JPEG Lossless point transform must be below precision");
        }
    }

    private static byte[] frameHeader(JpegFrame frame) {
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
            payload[8 + component * 3] = 0;
        }
        return payload;
    }

    private static byte[] scanHeader(int components, int predictor, int pointTransform) {
        byte[] payload = new byte[4 + components * 2];
        payload[0] = (byte) components;
        for (int component = 0; component < components; component++) {
            payload[1 + component * 2] = (byte) (component + 1);
            payload[2 + component * 2] = 0;
        }
        payload[payload.length - 3] = (byte) predictor;
        payload[payload.length - 2] = (byte) pointTransform;
        payload[payload.length - 1] = 0;
        return payload;
    }

    private static byte[] huffmanDefinition() {
        byte[] payload = new byte[1 + 16 + 17];
        payload[0] = 0;
        payload[1 + 7] = 17;
        for (int i = 0; i < 17; i++) {
            payload[17 + i] = (byte) i;
        }
        return payload;
    }

    private static HuffmanTable createDefaultHuffman() {
        int[] counts = new int[16];
        counts[7] = 17;
        int[] values = new int[17];
        for (int i = 0; i < values.length; i++) {
            values[i] = i;
        }
        return HuffmanTable.fromDefinition(counts, values);
    }

    private static ParsedFrame parse(byte[] data, int expectedFrameMarker) throws IOException {
        if (data == null || data.length < 4) {
            throw new JpegException("JPEG Lossless frame is truncated");
        }
        MemoryCacheImageInputStream input = new MemoryCacheImageInputStream(
                new ByteArrayInputStream(data));
        if (input.read() != 0xff || input.read() != 0xd8) {
            throw new JpegException("JPEG Lossless frame does not start with SOI");
        }
        JpegMarkerReader markers = new JpegMarkerReader(input);
        Map<Integer, HuffmanTable> huffman = new HashMap<Integer, HuffmanTable>();
        int width = 0;
        int height = 0;
        int precision = 0;
        int components = 0;
        int[] componentIds = null;
        int restartInterval = 0;
        while (true) {
            JpegMarker marker = markers.next();
            int code = marker.code();
            byte[] payload = marker.payload();
            if (code == expectedFrameMarker) {
                if (payload.length < 6) {
                    throw new JpegException("truncated JPEG Lossless SOF3");
                }
                precision = payload[0] & 0xff;
                height = u16(payload, 1);
                width = u16(payload, 3);
                components = payload[5] & 0xff;
                if (precision < 8 || precision > 16 || width == 0 || height == 0
                        || (components != 1 && components != 3)
                        || payload.length != 6 + components * 3) {
                    throw new JpegException("invalid JPEG Lossless SOF3");
                }
                componentIds = new int[components];
                for (int i = 0; i < components; i++) {
                    int offset = 6 + i * 3;
                    componentIds[i] = payload[offset] & 0xff;
                    if (payload[offset + 1] != 0x11 || payload[offset + 2] != 0) {
                        throw new JpegException("unsupported JPEG Lossless component sampling");
                    }
                }
            } else if (code == 0xc4) {
                parseHuffman(payload, huffman);
            } else if (code == 0xdd) {
                if (payload.length != 2) {
                    throw new JpegException("invalid JPEG Lossless restart interval");
                }
                restartInterval = u16(payload, 0);
            } else if (code == 0xda) {
                if (componentIds == null || !huffman.containsKey(0)) {
                    throw new JpegException("JPEG Lossless scan is missing SOF3 or DHT");
                }
                ScanParameters scanParameters = parseScan(payload, componentIds, components);
                if (scanParameters.pointTransform >= precision) {
                    throw new JpegException("JPEG Lossless point transform exceeds precision");
                }
                ScanData scan = readScan(input);
                JpegMarker end = markers.next();
                if (end.code() != 0xd9) {
                    throw new JpegException("JPEG Lossless frame does not end with EOI");
                }
                return new ParsedFrame(width, height, precision, components,
                        scanParameters.predictor, scanParameters.pointTransform, huffman.get(0),
                        restartInterval, scan.entropySegments,
                        scan.restartMarkers);
            } else if ((code >= 0xe0 && code <= 0xef) || code == 0xfe) {
                // APPn and COM metadata are not part of the predictive scan.
            } else if (code == 0xd8 || code == 0xd9 || (code >= 0xd0 && code <= 0xd7)) {
                throw new JpegException("unexpected JPEG Lossless marker: 0x"
                        + Integer.toHexString(code));
            } else {
                throw new JpegException("unsupported JPEG Lossless marker: 0x"
                        + Integer.toHexString(code));
            }
        }
    }

    private static ScanData readScan(ImageInputStream input) throws IOException {
        List<byte[]> segments = new ArrayList<byte[]>();
        List<Integer> restartMarkers = new ArrayList<Integer>();
        ByteArrayOutputStream segment = new ByteArrayOutputStream();
        while (true) {
            int value = input.read();
            if (value < 0) {
                throw new JpegException("truncated JPEG Lossless scan");
            }
            if (value != 0xff) {
                segment.write(value);
                continue;
            }
            int next = input.read();
            if (next < 0) {
                throw new JpegException("truncated JPEG Lossless scan marker");
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

    private static void parseHuffman(byte[] payload, Map<Integer, HuffmanTable> tables) {
        int offset = 0;
        while (offset < payload.length) {
            int specification = payload[offset++] & 0xff;
            if ((specification >>> 4) != 0 || offset + 16 > payload.length) {
                throw new IllegalArgumentException("unsupported JPEG Lossless Huffman table");
            }
            int[] counts = new int[16];
            int total = 0;
            for (int i = 0; i < counts.length; i++) {
                counts[i] = payload[offset++] & 0xff;
                total += counts[i];
            }
            if (offset + total > payload.length) {
                throw new IllegalArgumentException("truncated JPEG Lossless Huffman values");
            }
            int[] values = new int[total];
            for (int i = 0; i < total; i++) {
                values[i] = payload[offset++] & 0xff;
                if (values[i] > 16) {
                    throw new IllegalArgumentException("invalid JPEG Lossless Huffman category");
                }
            }
            tables.put(specification & 0x0f, HuffmanTable.fromDefinition(counts, values));
        }
    }

    private static ScanParameters parseScan(byte[] payload, int[] componentIds, int components) {
        if (payload.length != 4 + components * 2 || (payload[0] & 0xff) != components) {
            throw new IllegalArgumentException("invalid JPEG Lossless SOS header");
        }
        for (int i = 0; i < components; i++) {
            if ((payload[1 + i * 2] & 0xff) != componentIds[i]
                    || payload[2 + i * 2] != 0) {
                throw new IllegalArgumentException("unsupported JPEG Lossless scan components");
            }
        }
        int predictor = payload[payload.length - 3] & 0xff;
        int pointTransform = payload[payload.length - 2] & 0xff;
        if (predictor < 1 || predictor > 7 || payload[payload.length - 1] != 0
                || pointTransform >= 16) {
            throw new IllegalArgumentException("unsupported JPEG Lossless scan parameters");
        }
        return new ScanParameters(predictor, pointTransform);
    }

    private static int predict(int[] samples, int width, int components, int x, int y,
            int component, int precision, int predictor, int restartStartMcu) {
        int mcu = y * width + x;
        boolean hasLeft = x > 0 && mcu - 1 >= restartStartMcu;
        boolean hasAbove = y > 0 && mcu - width >= restartStartMcu;
        if (!hasLeft && !hasAbove) {
            return 1 << (precision - 1);
        }
        int left = hasLeft ? samples[indexOf(width, components, x - 1, y, component)] : 0;
        int above = hasAbove ? samples[indexOf(width, components, x, y - 1, component)] : 0;
        if (!hasAbove) {
            return left;
        }
        if (!hasLeft) {
            return above;
        }
        int upperLeft = samples[indexOf(width, components, x - 1, y - 1, component)];
        switch (predictor) {
            case 1: return left;
            case 2: return above;
            case 3: return upperLeft;
            case 4: return left + above - upperLeft;
            case 5: return left + ((above - upperLeft) >> 1);
            case 6: return above + ((left - upperLeft) >> 1);
            case 7: return (left + above) >> 1;
            default: throw new IllegalArgumentException("invalid JPEG Lossless predictor");
        }
    }

    private static int normalizeDifference(int difference) {
        return (short) difference;
    }

    private static int normalizeSample(int sample, int precision) {
        int modulus = 1 << precision;
        sample %= modulus;
        return sample < 0 ? sample + modulus : sample;
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
        int encoded = value < 0 ? value + (1 << size) - 1 : value;
        bits.writeBits(encoded, size);
    }

    private static int readAmplitude(BitReader bits, int size) throws IOException {
        int value = bits.readBits(size);
        return (value & (1 << (size - 1))) != 0 ? value : value - ((1 << size) - 1);
    }

    private static int indexOf(int width, int components, int x, int y, int component) {
        return ((y * width) + x) * components + component;
    }

    private static int u16(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xff) << 8) | (bytes[offset + 1] & 0xff);
    }

    private static final class ScanData {
        private final List<byte[]> entropySegments;
        private final List<Integer> restartMarkers;

        private ScanData(List<byte[]> entropySegments, List<Integer> restartMarkers) {
            this.entropySegments = entropySegments;
            this.restartMarkers = restartMarkers;
        }
    }

    private static final class ScanParameters {
        private final int predictor;
        private final int pointTransform;

        private ScanParameters(int predictor, int pointTransform) {
            this.predictor = predictor;
            this.pointTransform = pointTransform;
        }
    }

    private static final class ParsedFrame {
        private final int width;
        private final int height;
        private final int precision;
        private final int components;
        private final int predictor;
        private final int pointTransform;
        private final HuffmanTable huffman;
        private final int restartInterval;
        private final List<byte[]> entropySegments;
        private final List<Integer> restartMarkers;

        private ParsedFrame(int width, int height, int precision, int components, int predictor,
                int pointTransform, HuffmanTable huffman, int restartInterval,
                List<byte[]> entropySegments,
                List<Integer> restartMarkers) {
            this.width = width;
            this.height = height;
            this.precision = precision;
            this.components = components;
            this.predictor = predictor;
            this.pointTransform = pointTransform;
            this.huffman = huffman;
            this.restartInterval = restartInterval;
            this.entropySegments = entropySegments;
            this.restartMarkers = restartMarkers;
        }
    }
}
