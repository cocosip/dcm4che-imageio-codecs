package io.github.cocosip.dcm4che.imageio.codecs.jpegls.internal;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import javax.imageio.stream.MemoryCacheImageInputStream;

public final class JpegLsFrameCodec {
    private static final class EncodedScan {
        private final JpegLsScanHeader header;
        private final long restartInterval;
        private final java.util.List<byte[]> intervals;

        private EncodedScan(JpegLsScanHeader header, long restartInterval,
                java.util.List<byte[]> intervals) {
            this.header = header;
            this.restartInterval = restartInterval;
            this.intervals = intervals;
        }
    }

    public static final class DecodedFrame {
        private final int width;
        private final int height;
        private final int precision;
        private final int components;
        private final JpegLsInterleaveMode interleaveMode;
        private final int nearLossless;
        private final int[] samples;

        private DecodedFrame(int width, int height, int precision, int components,
                JpegLsInterleaveMode interleaveMode, int nearLossless, int[] samples) {
            this.width = width;
            this.height = height;
            this.precision = precision;
            this.components = components;
            this.interleaveMode = interleaveMode;
            this.nearLossless = nearLossless;
            this.samples = samples;
        }

        public int width() { return width; }
        public int height() { return height; }
        public int precision() { return precision; }
        public int components() { return components; }
        public int interleaveMode() { return interleaveMode.value(); }
        public int nearLossless() { return nearLossless; }
        public int[] samples() { return samples.clone(); }
    }

    private JpegLsFrameCodec() {
    }

    public static byte[] encode(int width, int height, int precision, int components,
            int nearLossless, int[] samples) throws IOException {
        return encode(width, height, precision, components, nearLossless,
                components == 1 ? JpegLsInterleaveMode.NONE : JpegLsInterleaveMode.SAMPLE,
                0, samples);
    }

    public static byte[] encode(int width, int height, int precision, int components,
            int nearLossless, int interleaveMode, int[] samples) throws IOException {
        return encode(width, height, precision, components, nearLossless, interleaveMode, 0, samples);
    }

    public static byte[] encode(int width, int height, int precision, int components,
            int nearLossless, int interleaveMode, int restartInterval, int[] samples)
            throws IOException {
        if (restartInterval < 0) throw new JpegLsException("negative JPEG-LS restart interval");
        return encode(width, height, precision, components, nearLossless,
                JpegLsInterleaveMode.fromValue(interleaveMode), restartInterval, samples);
    }

    private static byte[] encode(int width, int height, int precision, int components,
            int nearLossless, JpegLsInterleaveMode interleaveMode, int restartInterval,
            int[] samples) throws IOException {
        if ((components != 1 && components != 3) || precision < 2 || precision > 16
                || width <= 0 || height <= 0) {
            throw new JpegLsException("unsupported JPEG-LS frame profile");
        }
        if (components == 1 && interleaveMode != JpegLsInterleaveMode.NONE) {
            throw new JpegLsException("invalid JPEG-LS interleave mode for component count");
        }
        int maximum = (1 << precision) - 1;
        if (nearLossless < 0 || nearLossless > Math.min(255, maximum / 2)) {
            throw new JpegLsException("invalid JPEG-LS near-lossless value");
        }
        long expectedSamples = (long) width * height * components;
        if (expectedSamples > Integer.MAX_VALUE || samples.length != (int) expectedSamples) {
            throw new JpegLsException("JPEG-LS sample count mismatch");
        }
        for (int sample : samples) {
            if (sample < 0 || sample > maximum) throw new JpegLsException("sample outside JPEG-LS precision");
        }
        JpegLsPresetCodingParameters preset = JpegLsPresetCodingParameters.defaults(precision, nearLossless);
        JpegLsTraits traits = JpegLsTraits.create(preset.maximumSampleValue(), nearLossless,
                preset.resetValue(), preset.threshold1(), preset.threshold2(), preset.threshold3());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(output);
        data.writeShort(0xffd8);
        writeSegment(data, 0xf7, sofPayload(precision, height, width, components));
        if (nearLossless != 0) {
            writeSegment(data, 0xf8, presetPayload(preset));
        }
        if (restartInterval != 0) writeSegment(data, 0xdd, restartPayload(restartInterval));
        if (components > 1 && interleaveMode == JpegLsInterleaveMode.NONE) {
            for (int component = 0; component < components; component++) {
                writeSegment(data, 0xda, singleComponentSosPayload(
                        component + 1, nearLossless));
                data.write(encodeEntropy(width, height, 1, traits,
                        JpegLsInterleaveMode.NONE, restartInterval,
                        extractComponent(samples, components, component)));
            }
        } else {
            writeSegment(data, 0xda, sosPayload(components, nearLossless, interleaveMode));
            data.write(encodeEntropy(width, height, components, traits, interleaveMode,
                    restartInterval, samples));
        }
        data.writeShort(0xffd9);
        return output.toByteArray();
    }

    public static DecodedFrame decode(byte[] bytes) throws IOException {
        MemoryCacheImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes));
        JpegLsHeader header = new JpegLsHeaderReader(input).read();
        java.util.List<EncodedScan> scans = new java.util.ArrayList<EncodedScan>();
        JpegLsScanHeader currentScan = header.scan();
        long currentRestartInterval = header.restartInterval();
        ByteArrayOutputStream entropy = new ByteArrayOutputStream();
        java.util.List<byte[]> intervals = new java.util.ArrayList<byte[]>();
        int expectedRestart = 0;
        while (true) {
            int value = input.read();
            if (value < 0) throw new JpegLsException("JPEG-LS frame has no EOI marker");
            if (value != 0xff) {
                entropy.write(value);
                continue;
            }
            int next = input.read();
            if (next < 0) throw new JpegLsException("truncated JPEG-LS entropy marker");
            if (next == 0) {
                entropy.write(0xff);
                entropy.write(0);
                continue;
            }
            if (next == 0xd9) {
                intervals.add(entropy.toByteArray());
                scans.add(new EncodedScan(currentScan, currentRestartInterval, intervals));
                break;
            }
            if (next == 0xda) {
                intervals.add(entropy.toByteArray());
                scans.add(new EncodedScan(currentScan, currentRestartInterval, intervals));
                currentScan = JpegLsScanHeader.parse(readSegmentPayload(input), header.frame());
                currentRestartInterval = header.restartInterval();
                entropy = new ByteArrayOutputStream();
                intervals = new java.util.ArrayList<byte[]>();
                expectedRestart = 0;
                continue;
            }
            if (next == 0xdc) {
                header.frame().applyDnl(readSegmentPayload(input));
                continue;
            }
            if (next >= 0xd0 && next <= 0xd7) {
                if (currentRestartInterval == 0) {
                    throw new JpegLsException("JPEG-LS restart marker has no DRI");
                }
                if (next != 0xd0 + expectedRestart) {
                    throw new JpegLsException("unexpected JPEG-LS restart marker sequence");
                }
                intervals.add(entropy.toByteArray());
                entropy.reset();
                expectedRestart = expectedRestart + 1 & 7;
                continue;
            }
            // JPEG-LS bit stuffing reserves the most significant bit after an FF
            // byte.  The following byte is therefore ordinary entropy data when
            // that bit is zero (for example FF 7D), unlike byte-oriented JPEG
            // stuffing which uses an explicit FF 00 pair.
            if ((next & 0x80) == 0) {
                entropy.write(0xff);
                entropy.write(next);
                continue;
            }
            throw new JpegLsException(String.format("unexpected JPEG-LS marker in scan: 0xFF%02X", next));
        }
        if (input.read() >= 0) throw new JpegLsException("trailing data after JPEG-LS EOI");
        if (header.frame().width() > Integer.MAX_VALUE || header.frame().height() > Integer.MAX_VALUE) {
            throw new JpegLsException("JPEG-LS frame dimensions exceed Java array limits");
        }
        int width = (int) header.frame().width();
        int height = (int) header.frame().height();
        JpegLsPresetCodingParameters preset = header.presetCodingParameters();
        JpegLsTraits traits = JpegLsTraits.create(preset.maximumSampleValue(), header.scan().nearLossless(),
                preset.resetValue(), preset.threshold1(), preset.threshold2(), preset.threshold3());
        int[] samples = decodeScans(width, height, header, traits, scans);
        JpegLsColorTransform.applyInverse(samples, header.colorTransform(),
                header.frame().precision());
        return new DecodedFrame(width, height, header.frame().precision(),
                header.frame().componentCount(), header.scan().interleaveMode(),
                header.scan().nearLossless(), samples);
    }

    private static int[] decodeScans(int width, int height, JpegLsHeader header,
            JpegLsTraits traits, java.util.List<EncodedScan> scans) throws IOException {
        if (scans.size() == 1 && scans.get(0).header.componentCount()
                == header.frame().componentCount()) {
            EncodedScan scan = scans.get(0);
            return decodeEntropy(width, height, header.frame().componentCount(), traits,
                    scan.header.interleaveMode(), scan.restartInterval, scan.intervals);
        }
        int components = header.frame().componentCount();
        int[] samples = new int[width * height * components];
        boolean[] decoded = new boolean[components];
        for (EncodedScan scan : scans) {
            if (scan.header.componentCount() != 1
                    || scan.header.interleaveMode() != JpegLsInterleaveMode.NONE) {
                throw new JpegLsException(
                        "JPEG-LS multi-scan requires one non-interleaved component per scan");
            }
            int component = componentIndex(header.frame(), scan.header.component(0).selector());
            if (decoded[component]) {
                throw new JpegLsException("duplicate JPEG-LS component scan");
            }
            int[] componentSamples = decodeEntropy(width, height, 1, traits,
                    JpegLsInterleaveMode.NONE, scan.restartInterval, scan.intervals);
            for (int pixel = 0; pixel < componentSamples.length; pixel++) {
                samples[pixel * components + component] = componentSamples[pixel];
            }
            decoded[component] = true;
        }
        for (boolean value : decoded) {
            if (!value) throw new JpegLsException("missing JPEG-LS component scan");
        }
        return samples;
    }

    private static int componentIndex(JpegLsFrameHeader frame, int selector)
            throws JpegLsException {
        for (int component = 0; component < frame.componentCount(); component++) {
            if (frame.component(component).identifier() == selector) return component;
        }
        throw new JpegLsException("unknown JPEG-LS component selector: " + selector);
    }

    private static byte[] readSegmentPayload(MemoryCacheImageInputStream input)
            throws IOException {
        int length = input.readUnsignedShort();
        if (length < 2) throw new JpegLsException("invalid JPEG-LS marker length");
        byte[] payload = new byte[length - 2];
        input.readFully(payload);
        return payload;
    }

    private static byte[] encodeEntropy(int width, int height, int components,
            JpegLsTraits traits, JpegLsInterleaveMode mode, int restartInterval, int[] samples)
            throws IOException {
        if (restartInterval == 0 || restartInterval >= height) {
            return new JpegLsScanCodec(width, height, components, traits, mode).encode(samples);
        }
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        int firstLine = 0;
        int restart = 0;
        while (firstLine < height) {
            int lineCount = Math.min(restartInterval, height - firstLine);
            int[] intervalSamples = sliceLines(samples, width, components, firstLine, lineCount);
            result.write(new JpegLsScanCodec(width, lineCount, components, traits, mode)
                    .encode(intervalSamples));
            firstLine += lineCount;
            if (firstLine < height) {
                result.write(0xff);
                result.write(0xd0 + restart);
                restart = restart + 1 & 7;
            }
        }
        return result.toByteArray();
    }

    private static int[] decodeEntropy(int width, int height, int components,
            JpegLsTraits traits, JpegLsInterleaveMode mode, long restartInterval,
            java.util.List<byte[]> intervals) throws IOException {
        if (restartInterval == 0) {
            if (intervals.size() != 1) throw new JpegLsException("unexpected JPEG-LS restart data");
            return new JpegLsScanCodec(width, height, components, traits, mode).decode(intervals.get(0));
        }
        int expectedIntervals = (int) ((height + restartInterval - 1) / restartInterval);
        if (intervals.size() != expectedIntervals) {
            throw new JpegLsException("JPEG-LS restart marker count does not match DRI");
        }
        int[] samples = new int[width * height * components];
        int firstLine = 0;
        for (byte[] interval : intervals) {
            int lineCount = (int) Math.min(restartInterval, height - firstLine);
            int[] decoded = new JpegLsScanCodec(width, lineCount, components, traits, mode)
                    .decode(interval);
            System.arraycopy(decoded, 0, samples, firstLine * width * components, decoded.length);
            firstLine += lineCount;
        }
        return samples;
    }

    private static int[] sliceLines(int[] samples, int width, int components,
            int firstLine, int lineCount) {
        int offset = firstLine * width * components;
        int length = lineCount * width * components;
        int[] result = new int[length];
        System.arraycopy(samples, offset, result, 0, length);
        return result;
    }

    private static byte[] restartPayload(int restartInterval) {
        if (restartInterval <= 0xffff) {
            return new byte[] {(byte) (restartInterval >>> 8), (byte) restartInterval};
        }
        if (restartInterval <= 0xffffff) {
            return new byte[] {(byte) (restartInterval >>> 16), (byte) (restartInterval >>> 8),
                    (byte) restartInterval};
        }
        return new byte[] {(byte) (restartInterval >>> 24), (byte) (restartInterval >>> 16),
                (byte) (restartInterval >>> 8), (byte) restartInterval};
    }

    private static byte[] sofPayload(int precision, int height, int width, int components) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(output);
        data.writeByte(precision);
        data.writeShort(height);
        data.writeShort(width);
        data.writeByte(components);
        for (int component = 0; component < components; component++) {
            data.writeByte(component + 1);
            data.writeByte(0x11);
            data.writeByte(0);
        }
        return output.toByteArray();
    }

    private static byte[] sosPayload(int components, int nearLossless,
            JpegLsInterleaveMode interleaveMode) {
        byte[] payload = new byte[1 + components * 2 + 3];
        payload[0] = (byte) components;
        for (int component = 0; component < components; component++) {
            payload[1 + component * 2] = (byte) (component + 1);
            payload[2 + component * 2] = 0;
        }
        int offset = 1 + components * 2;
        payload[offset] = (byte) nearLossless;
        payload[offset + 1] = (byte) interleaveMode.value();
        payload[offset + 2] = 0;
        return payload;
    }

    private static byte[] singleComponentSosPayload(int selector, int nearLossless) {
        return new byte[] {1, (byte) selector, 0, (byte) nearLossless, 0, 0};
    }

    private static int[] extractComponent(int[] samples, int components, int component) {
        int[] result = new int[samples.length / components];
        for (int pixel = 0; pixel < result.length; pixel++) {
            result[pixel] = samples[pixel * components + component];
        }
        return result;
    }

    private static byte[] presetPayload(JpegLsPresetCodingParameters preset) {
        return new byte[] {
            1,
            (byte) (preset.maximumSampleValue() >>> 8), (byte) preset.maximumSampleValue(),
            (byte) (preset.threshold1() >>> 8), (byte) preset.threshold1(),
            (byte) (preset.threshold2() >>> 8), (byte) preset.threshold2(),
            (byte) (preset.threshold3() >>> 8), (byte) preset.threshold3(),
            (byte) (preset.resetValue() >>> 8), (byte) preset.resetValue()
        };
    }

    private static void writeSegment(DataOutputStream output, int marker, byte[] payload) throws IOException {
        output.writeByte(0xff);
        output.writeByte(marker);
        output.writeShort(payload.length + 2);
        output.write(payload);
    }
}
