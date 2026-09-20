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

/** JPEG Lossless Process 14 predictive Huffman codec. */
public final class LosslessJpegCodec {
    private static final HuffmanTable DEFAULT_HUFFMAN = createDefaultHuffman();

    private LosslessJpegCodec() {
    }

    public static byte[] encode(JpegFrame frame, int predictor) throws IOException {
        validateFrame(frame, predictor);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        MemoryCacheImageOutputStream output = new MemoryCacheImageOutputStream(bytes);
        output.write(0xff);
        output.write(0xd8);
        JpegMarkerWriter.write(output, 0xc3, frameHeader(frame));
        JpegMarkerWriter.write(output, 0xc4, huffmanDefinition());
        JpegMarkerWriter.write(output, 0xda, scanHeader(frame.components(), predictor));

        BitWriter bits = new BitWriter(output);
        int[] reconstructed = new int[frame.width() * frame.height() * frame.components()];
        for (int y = 0; y < frame.height(); y++) {
            for (int x = 0; x < frame.width(); x++) {
                for (int component = 0; component < frame.components(); component++) {
                    int index = indexOf(frame.width(), frame.components(), x, y, component);
                    int sample = frame.sample(x, y, component);
                    int difference = normalizeDifference(sample
                            - predict(reconstructed, frame.width(), frame.components(), x, y,
                                    component, frame.precision(), predictor));
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
        ParsedFrame parsed = parse(data);
        int[] samples = new int[parsed.width * parsed.height * parsed.components];
        MemoryCacheImageInputStream entropyInput = new MemoryCacheImageInputStream(
                new ByteArrayInputStream(parsed.entropy));
        BitReader bits = new BitReader(entropyInput);
        for (int y = 0; y < parsed.height; y++) {
            for (int x = 0; x < parsed.width; x++) {
                for (int component = 0; component < parsed.components; component++) {
                    int category = HuffmanCodec.decodeSymbol(bits, parsed.huffman);
                    if (category > parsed.precision + 1 || category == 16 && parsed.precision < 16) {
                        throw new JpegException("invalid JPEG Lossless difference category");
                    }
                    int difference = category == 0 ? 0
                            : category == 16 ? 1 << 15
                            : readAmplitude(bits, category);
                    int prediction = predict(samples, parsed.width, parsed.components, x, y,
                            component, parsed.precision, parsed.predictor);
                    int sample = normalizeSample(prediction + difference, parsed.precision);
                    if (sample < 0 || sample > (1 << parsed.precision) - 1) {
                        throw new JpegException("JPEG Lossless sample is outside precision range");
                    }
                    samples[indexOf(parsed.width, parsed.components, x, y, component)] = sample;
                }
            }
        }
        return JpegFrame.of(parsed.width, parsed.height, parsed.components, samples,
                parsed.precision);
    }

    private static void validateFrame(JpegFrame frame, int predictor) {
        if (frame == null) {
            throw new NullPointerException("frame");
        }
        if (frame.precision() < 8 || frame.precision() > 16) {
            throw new IllegalArgumentException("JPEG Lossless precision must be between 8 and 16 bits");
        }
        if (predictor < 1 || predictor > 7) {
            throw new IllegalArgumentException("JPEG Lossless predictor must be between 1 and 7");
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

    private static byte[] scanHeader(int components, int predictor) {
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

    private static ParsedFrame parse(byte[] data) throws IOException {
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
        while (true) {
            JpegMarker marker = markers.next();
            int code = marker.code();
            byte[] payload = marker.payload();
            if (code == 0xc3) {
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
            } else if (code == 0xda) {
                if (componentIds == null || !huffman.containsKey(0)) {
                    throw new JpegException("JPEG Lossless scan is missing SOF3 or DHT");
                }
                int predictor = parseScan(payload, componentIds, components);
                byte[] entropy = JpegMarkerReader.readEntropyBytes(input);
                JpegMarker end = markers.next();
                if (end.code() != 0xd9) {
                    throw new JpegException("JPEG Lossless frame does not end with EOI");
                }
                return new ParsedFrame(width, height, precision, components, predictor,
                        huffman.get(0), entropy);
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

    private static int parseScan(byte[] payload, int[] componentIds, int components) {
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
        if (predictor < 1 || predictor > 7 || payload[payload.length - 2] != 0
                || payload[payload.length - 1] != 0) {
            throw new IllegalArgumentException("unsupported JPEG Lossless scan parameters");
        }
        return predictor;
    }

    private static int predict(int[] samples, int width, int components, int x, int y,
            int component, int precision, int predictor) {
        if (x == 0 && y == 0) {
            return 1 << (precision - 1);
        }
        int left = x == 0 ? 0 : samples[indexOf(width, components, x - 1, y, component)];
        int above = y == 0 ? 0 : samples[indexOf(width, components, x, y - 1, component)];
        if (y == 0) {
            return left;
        }
        if (x == 0) {
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

    private static final class ParsedFrame {
        private final int width;
        private final int height;
        private final int precision;
        private final int components;
        private final int predictor;
        private final HuffmanTable huffman;
        private final byte[] entropy;

        private ParsedFrame(int width, int height, int precision, int components, int predictor,
                HuffmanTable huffman, byte[] entropy) {
            this.width = width;
            this.height = height;
            this.precision = precision;
            this.components = components;
            this.predictor = predictor;
            this.huffman = huffman;
            this.entropy = entropy;
        }
    }
}
