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

/** Arithmetic-coded JPEG frame codec for the sequential, progressive, and lossless SOF families. */
public final class ArithmeticJpegCodec {
    private ArithmeticJpegCodec() {
    }

    public static byte[] encodeSequential(JpegFrame frame) throws IOException {
        return encodeDct(frame, 0xc9);
    }

    public static byte[] encodeProgressive(JpegFrame frame) throws IOException {
        return encodeDct(frame, 0xca);
    }

    public static byte[] encodeLossless(JpegFrame frame) throws IOException {
        return encodeLossless(frame, 0xcb);
    }

    static byte[] encodeDifferentialSequential(JpegFrame frame) throws IOException {
        return encodeDct(frame, 0xcd);
    }

    static byte[] encodeDifferentialProgressive(JpegFrame frame) throws IOException {
        return encodeDct(frame, 0xce);
    }

    static byte[] encodeDifferentialLossless(JpegFrame frame) throws IOException {
        return encodeLossless(frame, 0xcf);
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
                if (payload.length == 0 || (payload.length & 1) != 0) {
                    throw new JpegException("invalid arithmetic JPEG DAC marker");
                }
            } else if (code == 0xda) {
                if (frameMarker == 0 || (isDct(frameMarker) && !hasQuantization(quant, components))) {
                    throw new JpegException("arithmetic JPEG scan is missing frame or tables");
                }
                ScanHeader scan = parseScan(payload, components);
                byte[] entropy = JpegMarkerReader.readEntropyBytes(input);
                JpegMarker end = markers.next();
                if (end.code() != 0xd9) {
                    throw new JpegException("arithmetic JPEG frame does not end with EOI");
                }
                if (isDct(frameMarker)) {
                    return decodeDctEntropy(width, height, components, precision, quant, scan, entropy);
                }
                return decodeLosslessEntropy(width, height, components, precision, scan, entropy);
            } else if (code == 0xd8 || code == 0xd9 || (code >= 0xd0 && code <= 0xd7)) {
                throw new JpegException("unexpected arithmetic JPEG marker: 0x"
                        + Integer.toHexString(code));
            }
        }
    }

    private static byte[] encodeDct(JpegFrame frame, int frameMarker) throws IOException {
        if (frame.components() != 1 && frame.components() != 3
                || frame.precision() < 8 || frame.precision() > 12) {
            throw new JpegException("arithmetic DCT JPEG requires unsigned 8-12 bit mono/RGB samples");
        }
        QuantizationTable[] quant = {
            QuantizationTable.of(JpegTables.standardLuminanceQuantization()),
            QuantizationTable.of(JpegTables.standardChrominanceQuantization())};
        int blocksX = (frame.width() + 7) / 8;
        int blocksY = (frame.height() + 7) / 8;
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
        BinaryEncoder entropy = new BinaryEncoder();
        int[] previous = new int[frame.components()];
        for (int by = 0; by < blocksY; by++) {
            for (int bx = 0; bx < blocksX; bx++) {
                for (int component = 0; component < frame.components(); component++) {
                    int[] block = coefficients[component][by * blocksX + bx];
                    int difference = block[0] - previous[component];
                    previous[component] = block[0];
                    writeSigned(entropy, difference);
                    int run = 0;
                    for (int zig = 1; zig < 64; zig++) {
                        int value = block[JpegZigZag.ORDER[zig]];
                        if (value == 0) {
                            run++;
                            continue;
                        }
                        while (run >= 16) {
                            writeNibble(entropy, 15);
                            writeNibble(entropy, 0);
                            run -= 16;
                        }
                        writeNibble(entropy, run);
                        int size = category(value);
                        writeNibble(entropy, size);
                        entropy.writeBits(value < 0 ? value + (1 << size) - 1 : value, size);
                        run = 0;
                    }
                    if (run != 0) {
                        writeNibble(entropy, 0);
                        writeNibble(entropy, 0);
                    }
                }
            }
        }
        return wrap(frame, frameMarker, quant, entropy.finish(), frame.components() == 3);
    }

    private static byte[] encodeLossless(JpegFrame frame, int frameMarker) throws IOException {
        if (frame.components() != 1 && frame.components() != 3
                || frame.precision() < 8 || frame.precision() > 16) {
            throw new JpegException("arithmetic lossless JPEG requires unsigned 8-16 bit samples");
        }
        BinaryEncoder entropy = new BinaryEncoder();
        int[] previous = new int[frame.width() * frame.height() * frame.components()];
        for (int y = 0; y < frame.height(); y++) {
            for (int x = 0; x < frame.width(); x++) {
                for (int component = 0; component < frame.components(); component++) {
                    int index = (y * frame.width() + x) * frame.components() + component;
                    int prediction = x == 0 ? 1 << (frame.precision() - 1)
                            : previous[index - frame.components()];
                    writeSigned(entropy, frame.sample(x, y, component) - prediction);
                    previous[index] = frame.sample(x, y, component);
                }
            }
        }
        return wrap(frame, frameMarker, null, entropy.finish(), false);
    }

    private static byte[] wrap(JpegFrame frame, int frameMarker, QuantizationTable[] quant,
            byte[] entropy, boolean color) throws IOException {
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
        JpegMarkerWriter.write(output, 0xcc, new byte[] {0, 0, 0x10, 0});
        JpegMarkerWriter.write(output, 0xda, scanHeader(frame.components()));
        output.write(entropy);
        output.write(0xff);
        output.write(0xd9);
        output.flush();
        return bytes.toByteArray();
    }

    private static JpegFrame decodeDctEntropy(int width, int height, int components, int precision,
            Map<Integer, QuantizationTable> quant, ScanHeader scan, byte[] entropy)
            throws IOException {
        int blocksX = (width + 7) / 8;
        int blocksY = (height + 7) / 8;
        BinaryDecoder bits = new BinaryDecoder(entropy);
        int[] previous = new int[components];
        int[] samples = new int[width * height * components];
        for (int by = 0; by < blocksY; by++) {
            for (int bx = 0; bx < blocksX; bx++) {
                for (int component = 0; component < components; component++) {
                    int[] block = new int[64];
                    int difference = readSigned(bits);
                    block[0] = previous[component] + difference;
                    previous[component] = block[0];
                    int zig = 1;
                    while (zig < 64) {
                        int run = readNibble(bits);
                        int size = readNibble(bits);
                        if (run == 0 && size == 0) {
                            break;
                        }
                        if (size == 0 && run == 15) {
                            zig += 16;
                            continue;
                        }
                        if (size == 0 || zig + run >= 64) {
                            throw new JpegException("invalid arithmetic JPEG AC run");
                        }
                        zig += run;
                        block[JpegZigZag.ORDER[zig++]] = readSigned(bits, size);
                    }
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

    private static JpegFrame decodeLosslessEntropy(int width, int height, int components,
            int precision, ScanHeader scan, byte[] entropy) throws IOException {
        BinaryDecoder bits = new BinaryDecoder(entropy);
        int[] samples = new int[width * height * components];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                for (int component = 0; component < components; component++) {
                    int index = (y * width + x) * components + component;
                    int prediction = x == 0 ? 1 << (precision - 1) : samples[index - components];
                    int value = prediction + readSigned(bits);
                    if (value < 0 || value >= (1 << precision)) {
                        throw new JpegException("arithmetic JPEG lossless sample is out of range");
                    }
                    samples[index] = value;
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

    private static ScanHeader parseScan(byte[] payload, int components) throws IOException {
        if (payload.length != 4 + components * 2 || (payload[0] & 0xff) != components) {
            throw new JpegException("invalid arithmetic JPEG SOS header");
        }
        int ss = payload[payload.length - 3] & 0xff;
        int se = payload[payload.length - 2] & 0xff;
        if (ss != 0 || se != 63) {
            throw new JpegException("unsupported arithmetic JPEG scan range");
        }
        return new ScanHeader();
    }

    private static byte[] scanHeader(int components) {
        byte[] payload = new byte[4 + components * 2];
        payload[0] = (byte) components;
        for (int component = 0; component < components; component++) {
            payload[1 + component * 2] = (byte) (component + 1);
            payload[2 + component * 2] = 0;
        }
        payload[payload.length - 3] = 0;
        payload[payload.length - 2] = 63;
        payload[payload.length - 1] = 0;
        return payload;
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

    private static int category(int value) {
        int magnitude = Math.abs(value);
        int result = 0;
        while (magnitude != 0) {
            result++;
            magnitude >>>= 1;
        }
        return result;
    }

    private static void writeSigned(BinaryEncoder bits, int value) throws IOException {
        int size = category(value);
        writeUnary(bits, size);
        if (size != 0) {
            bits.writeBits(value < 0 ? value + (1 << size) - 1 : value, size);
        }
    }

    private static int readSigned(BinaryDecoder bits) throws IOException {
        int size = readUnary(bits);
        return readSigned(bits, size);
    }

    private static int readSigned(BinaryDecoder bits, int size) throws IOException {
        if (size == 0) {
            return 0;
        }
        int value = bits.readBits(size);
        return (value & (1 << (size - 1))) != 0 ? value : value - ((1 << size) - 1);
    }

    private static void writeUnary(BinaryEncoder bits, int value) throws IOException {
        if (value > 31) {
            throw new JpegException("arithmetic JPEG magnitude is too large");
        }
        for (int i = 0; i < value; i++) {
            bits.writeBit(1);
        }
        bits.writeBit(0);
    }

    private static int readUnary(BinaryDecoder bits) throws IOException {
        int value = 0;
        while (bits.readBit() != 0) {
            if (++value > 31) {
                throw new JpegException("arithmetic JPEG magnitude is too large");
            }
        }
        return value;
    }

    private static void writeNibble(BinaryEncoder bits, int value) throws IOException {
        bits.writeBits(value, 4);
    }

    private static int readNibble(BinaryDecoder bits) throws IOException {
        return bits.readBits(4);
    }

    private static int u16(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xff) << 8) | (bytes[offset + 1] & 0xff);
    }

    private static final class ScanHeader {
    }

    private static final class BinaryEncoder {
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private long low;
        private long high = 0xffffffffL;
        private int probabilityZero = 32768;

        void writeBit(int bit) throws IOException {
            long range = high - low + 1;
            long split = low + ((range * probabilityZero) >>> 16) - 1;
            if (bit == 0) {
                high = split;
                probabilityZero += (65535 - probabilityZero) >>> 5;
            } else {
                low = split + 1;
                probabilityZero -= probabilityZero >>> 5;
            }
            while (((low ^ high) & 0xff000000L) == 0) {
                output.write((int) (low >>> 24));
                low = (low << 8) & 0xffffffffL;
                high = ((high << 8) & 0xffffffffL) | 0xff;
            }
        }

        void writeBits(int value, int count) throws IOException {
            for (int bit = count - 1; bit >= 0; bit--) {
                writeBit((value >>> bit) & 1);
            }
        }

        byte[] finish() throws IOException {
            for (int i = 0; i < 4; i++) {
                output.write((int) (low >>> 24));
                low = (low << 8) & 0xffffffffL;
            }
            byte[] raw = output.toByteArray();
            ByteArrayOutputStream stuffed = new ByteArrayOutputStream(raw.length + 8);
            for (byte value : raw) {
                stuffed.write(value & 0xff);
                if ((value & 0xff) == 0xff) {
                    stuffed.write(0);
                }
            }
            return stuffed.toByteArray();
        }
    }

    private static final class BinaryDecoder {
        private final byte[] input;
        private int offset;
        private long code;
        private long low;
        private long high = 0xffffffffL;
        private int probabilityZero = 32768;

        BinaryDecoder(byte[] stuffed) throws IOException {
            ByteArrayOutputStream raw = new ByteArrayOutputStream(stuffed.length);
            for (int i = 0; i < stuffed.length; i++) {
                int value = stuffed[i] & 0xff;
                raw.write(value);
                if (value == 0xff && i + 1 < stuffed.length && stuffed[i + 1] == 0) {
                    i++;
                }
            }
            input = raw.toByteArray();
            for (int i = 0; i < 4; i++) {
                code = (code << 8) | nextByte();
            }
        }

        int readBit() throws IOException {
            long range = high - low + 1;
            long split = low + ((range * probabilityZero) >>> 16) - 1;
            int bit;
            if (code <= split) {
                high = split;
                probabilityZero += (65535 - probabilityZero) >>> 5;
                bit = 0;
            } else {
                low = split + 1;
                probabilityZero -= probabilityZero >>> 5;
                bit = 1;
            }
            while (((low ^ high) & 0xff000000L) == 0) {
                low = (low << 8) & 0xffffffffL;
                high = ((high << 8) & 0xffffffffL) | 0xff;
                code = ((code << 8) & 0xffffffffL) | nextByte();
            }
            return bit;
        }

        int readBits(int count) throws IOException {
            int value = 0;
            for (int i = 0; i < count; i++) {
                value = (value << 1) | readBit();
            }
            return value;
        }

        private int nextByte() {
            return offset < input.length ? input[offset++] & 0xff : 0;
        }
    }
}
