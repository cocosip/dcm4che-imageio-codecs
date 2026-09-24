package io.github.cocosip.dcm4che.imageio.codecs.jpegls.internal;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import javax.imageio.stream.MemoryCacheImageInputStream;

/**
 * Pure-Java JPEG-LS frame codec.
 *
 * <p>Samples are represented as non-negative {@code int} values in the
 * JPEG-LS code domain. This is intentional for unsigned 16-bit DICOM data:
 * values range from {@code 0} through {@code 65535} and must not be carried
 * through a signed Java {@code short}. The ImageIO adapter masks unused high
 * bits before calling this class.</p>
 */
public final class JpegLsFrameCodec {
    private static final class EncodedScan {
        private final JpegLsScanHeader header;
        private final JpegLsTraits traits;
        private final java.util.Map<Integer, JpegLsMappingTable> mappingTables;
        private final long restartInterval;
        private final java.util.List<byte[]> intervals;

        private EncodedScan(JpegLsScanHeader header, JpegLsTraits traits, long restartInterval,
                java.util.Map<Integer, JpegLsMappingTable> mappingTables,
                java.util.List<byte[]> intervals) {
            this.header = header;
            this.traits = traits;
            this.mappingTables = java.util.Collections.unmodifiableMap(
                    new java.util.HashMap<Integer, JpegLsMappingTable>(mappingTables));
            this.restartInterval = restartInterval;
            this.intervals = intervals;
        }
    }

    public static final class DecodedFrame {
        public static final class MappedComponent {
            private final int component;
            private final int tableId;
            private final int entryWidth;
            private final byte[] bytes;

            private MappedComponent(int component, int tableId, int entryWidth, byte[] bytes) {
                this.component = component;
                this.tableId = tableId;
                this.entryWidth = entryWidth;
                this.bytes = bytes.clone();
            }

            public int component() { return component; }
            public int tableId() { return tableId; }
            public int entryWidth() { return entryWidth; }
            public byte[] bytes() { return bytes.clone(); }
        }

        private final int width;
        private final int height;
        private final int precision;
        private final int components;
        private final JpegLsInterleaveMode interleaveMode;
        private final int nearLossless;
        private final int[] samples;
        private final java.util.List<MappedComponent> mappedComponents;

        private DecodedFrame(int width, int height, int precision, int components,
                JpegLsInterleaveMode interleaveMode, int nearLossless, int[] samples,
                java.util.List<MappedComponent> mappedComponents) {
            this.width = width;
            this.height = height;
            this.precision = precision;
            this.components = components;
            this.interleaveMode = interleaveMode;
            this.nearLossless = nearLossless;
            this.samples = samples;
            this.mappedComponents = java.util.Collections.unmodifiableList(
                    new java.util.ArrayList<MappedComponent>(mappedComponents));
        }

        public int width() { return width; }
        public int height() { return height; }
        public int precision() { return precision; }
        public int components() { return components; }
        public int interleaveMode() { return interleaveMode.value(); }
        public int nearLossless() { return nearLossless; }
        /**
         * Returns reconstructed JPEG-LS sample codes. For unsigned 16-bit
         * data, values are in the inclusive range {@code 0..65535}; DICOM
         * little-endian byte packing is handled by the adapter boundary, not
         * by this codestream core.
         */
        public int[] samples() { return samples.clone(); }
        public boolean hasMappedOutput() { return !mappedComponents.isEmpty(); }
        public java.util.List<MappedComponent> mappedComponents() { return mappedComponents; }
    }

    private JpegLsFrameCodec() {
    }

    /**
     * Encodes one JPEG-LS frame from non-negative sample codes.
     *
     * @param precision sample precision in bits; 16-bit unsigned samples use
     *                 {@code int} values up to {@code 65535}
     */
    public static byte[] encode(int width, int height, int precision, int components,
            int nearLossless, int[] samples) throws IOException {
        return encode(width, height, precision, components, nearLossless,
                components == 1 ? JpegLsInterleaveMode.NONE : JpegLsInterleaveMode.SAMPLE,
                0, samples, java.util.Collections.<Integer, JpegLsMappingTable>emptyMap(),
                java.util.Collections.<Integer, Integer>emptyMap());
    }

    public static byte[] encode(int width, int height, int precision, int components,
            int nearLossless, int interleaveMode, int[] samples) throws IOException {
        return encode(width, height, precision, components, nearLossless, interleaveMode, 0, samples,
                java.util.Collections.<io.github.cocosip.dcm4che.imageio.codecs.jpegls.JpegLsMappingTable>emptyList(),
                java.util.Collections.<Integer, Integer>emptyMap());
    }

    public static byte[] encode(int width, int height, int precision, int components,
            int nearLossless, int interleaveMode, int restartInterval, int[] samples)
            throws IOException {
        if (restartInterval < 0) throw new JpegLsException("negative JPEG-LS restart interval");
        return encode(width, height, precision, components, nearLossless,
                JpegLsInterleaveMode.fromValue(interleaveMode), restartInterval, samples,
                java.util.Collections.<Integer, JpegLsMappingTable>emptyMap(),
                java.util.Collections.<Integer, Integer>emptyMap());
    }

    public static byte[] encode(int width, int height, int precision, int components,
            int nearLossless, int interleaveMode, int restartInterval, int[] samples,
            java.util.List<io.github.cocosip.dcm4che.imageio.codecs.jpegls.JpegLsMappingTable> tables,
            java.util.Map<Integer, Integer> componentMappingTableSelectors) throws IOException {
        if (tables == null || componentMappingTableSelectors == null) {
            throw new JpegLsException("JPEG-LS mapping arguments must not be null");
        }
        java.util.Map<Integer, JpegLsMappingTable> internalTables =
                new java.util.HashMap<Integer, JpegLsMappingTable>();
        for (io.github.cocosip.dcm4che.imageio.codecs.jpegls.JpegLsMappingTable table : tables) {
            if (table == null) throw new JpegLsException("JPEG-LS mapping table must not be null");
            JpegLsMappingTable internal = JpegLsMappingTable.from(table);
            if (internalTables.put(internal.tableId(), internal) != null) {
                throw new JpegLsException("duplicate JPEG-LS mapping table: " + internal.tableId());
            }
        }
        return encode(width, height, precision, components, nearLossless,
                JpegLsInterleaveMode.fromValue(interleaveMode), restartInterval, samples,
                internalTables, componentMappingTableSelectors);
    }

    private static byte[] encode(int width, int height, int precision, int components,
            int nearLossless, JpegLsInterleaveMode interleaveMode, int restartInterval,
            int[] samples, java.util.Map<Integer, JpegLsMappingTable> mappingTables,
            java.util.Map<Integer, Integer> componentMappingTableSelectors) throws IOException {
        if ((components != 1 && components != 3) || precision < 2 || precision > 16
                || width <= 0 || height <= 0 || width > 0xffff || height > 0xffff) {
            throw new JpegLsException("unsupported JPEG-LS frame profile");
        }
        if (components == 1 && interleaveMode != JpegLsInterleaveMode.NONE) {
            throw new JpegLsException("invalid JPEG-LS interleave mode for component count");
        }
        int maximum = (1 << precision) - 1;
        if (nearLossless < 0 || nearLossless > Math.min(255, maximum / 2)) {
            throw new JpegLsException("invalid JPEG-LS near-lossless value");
        }
        if (samples == null) {
            throw new JpegLsException("JPEG-LS samples must not be null");
        }
        long expectedSamples = (long) width * height * components;
        if (expectedSamples > Integer.MAX_VALUE || samples.length != (int) expectedSamples) {
            throw new JpegLsException("JPEG-LS sample count mismatch");
        }
        for (int sample : samples) {
            if (sample < 0 || sample > maximum) throw new JpegLsException("sample outside JPEG-LS precision");
        }
        validateMappingTablesForEncode(mappingTables, componentMappingTableSelectors,
                maximum, components);
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
        for (JpegLsMappingTable table : mappingTables.values()) {
            for (byte[] payload : JpegLsMappingTableSegments.encode(table)) {
                writeSegment(data, 0xf8, payload);
            }
        }
        if (restartInterval != 0) writeSegment(data, 0xdd, restartPayload(restartInterval));
        if (components > 1 && interleaveMode == JpegLsInterleaveMode.NONE) {
            for (int component = 0; component < components; component++) {
                writeSegment(data, 0xda, singleComponentSosPayload(
                        component + 1, nearLossless, componentMappingTableSelectors));
                data.write(encodeEntropy(width, height, 1, traits,
                        JpegLsInterleaveMode.NONE, restartInterval,
                        extractComponent(samples, components, component)));
            }
        } else {
            writeSegment(data, 0xda, sosPayload(components, nearLossless, interleaveMode,
                    componentMappingTableSelectors));
            data.write(encodeEntropy(width, height, components, traits, interleaveMode,
                    restartInterval, samples));
        }
        data.writeShort(0xffd9);
        return output.toByteArray();
    }

    public static DecodedFrame decode(byte[] bytes) throws IOException {
        if (bytes == null) {
            throw new JpegLsException("JPEG-LS frame must not be null");
        }
        MemoryCacheImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes));
        JpegLsHeader header = new JpegLsHeaderReader(input).read();
        java.util.List<EncodedScan> scans = new java.util.ArrayList<EncodedScan>();
        JpegLsScanHeader currentScan = header.scan();
        JpegLsPresetCodingParameters rawPreset = header.rawPresetCodingParameters();
        JpegLsPresetCodingParameters currentPreset = header.presetCodingParameters();
        JpegLsTraits currentTraits = traits(currentPreset, currentScan);
        java.util.Map<Integer, JpegLsMappingTable> mappingTables =
                new java.util.HashMap<Integer, JpegLsMappingTable>(header.mappingTables());
        JpegLsMappingTableParser mappingParser = new JpegLsMappingTableParser();
        long currentRestartInterval = header.restartInterval();
        Integer colorTransform = Integer.valueOf(header.colorTransform());
        ByteArrayOutputStream entropy = new ByteArrayOutputStream();
        java.util.List<byte[]> intervals = new java.util.ArrayList<byte[]>();
        int expectedRestart = 0;
        boolean scanFinalized = false;
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
                finishMapping(mappingParser, mappingTables);
                if (!scanFinalized) {
                    intervals.add(entropy.toByteArray());
                    scans.add(new EncodedScan(currentScan, currentTraits, currentRestartInterval,
                            mappingTables, intervals));
                }
                break;
            }
            if (next == 0xda) {
                finishMapping(mappingParser, mappingTables);
                if (!scanFinalized) {
                    intervals.add(entropy.toByteArray());
                    scans.add(new EncodedScan(currentScan, currentTraits, currentRestartInterval,
                            mappingTables, intervals));
                }
                currentScan = JpegLsScanHeader.parse(readSegmentPayload(input), header.frame());
                currentPreset = rawPreset == null
                        ? JpegLsPresetCodingParameters.defaults(header.frame().precision(),
                                currentScan.nearLossless())
                        : rawPreset.resolve(header.frame().precision(), currentScan.nearLossless());
                validateMappingTables(mappingTables, currentScan, currentPreset.maximumSampleValue());
                currentTraits = traits(currentPreset, currentScan);
                entropy = new ByteArrayOutputStream();
                intervals = new java.util.ArrayList<byte[]>();
                expectedRestart = 0;
                scanFinalized = false;
                continue;
            }
            if (next == 0xdc) {
                header.frame().applyDnl(readSegmentPayload(input));
                continue;
            }
            if (next == JpegLsMarker.LSE) {
                if (!scanFinalized) {
                    intervals.add(entropy.toByteArray());
                    finishMapping(mappingParser, mappingTables);
                    scans.add(new EncodedScan(currentScan, currentTraits, currentRestartInterval,
                            mappingTables, intervals));
                    entropy = new ByteArrayOutputStream();
                    intervals = new java.util.ArrayList<byte[]>();
                    scanFinalized = true;
                }
                byte[] payload = readSegmentPayload(input);
                if (payload.length == 0) throw new JpegLsException("empty JPEG-LS LSE segment");
                int type = payload[0] & 0xff;
                if (type == 1) {
                    rawPreset = JpegLsPresetCodingParameters.parse(payload);
                } else if (type == 2 || type == 3) {
                    mappingParser.accept(payload);
                } else if (type == 4) {
                    header.frame().applyOversize(payload);
                } else {
                    throw new JpegLsException("unsupported JPEG-LS LSE type: " + type);
                }
                continue;
            }
            if (next == JpegLsMarker.DRI) {
                if (!scanFinalized) {
                    intervals.add(entropy.toByteArray());
                    finishMapping(mappingParser, mappingTables);
                    scans.add(new EncodedScan(currentScan, currentTraits, currentRestartInterval,
                            mappingTables, intervals));
                    entropy = new ByteArrayOutputStream();
                    intervals = new java.util.ArrayList<byte[]>();
                    scanFinalized = true;
                }
                currentRestartInterval = JpegLsHeaderReader.parseRestartInterval(
                        readSegmentPayload(input));
                continue;
            }
            if ((next >= 0xe0 && next <= 0xef) || next == JpegLsMarker.COM) {
                if (!scanFinalized) {
                    intervals.add(entropy.toByteArray());
                    finishMapping(mappingParser, mappingTables);
                    scans.add(new EncodedScan(currentScan, currentTraits, currentRestartInterval,
                            mappingTables, intervals));
                    entropy = new ByteArrayOutputStream();
                    intervals = new java.util.ArrayList<byte[]>();
                    scanFinalized = true;
                }
                byte[] payload = readSegmentPayload(input);
                if (next == 0xe8) {
                    colorTransform = JpegLsHeaderReader.parseColorTransform(payload, colorTransform);
                    JpegLsColorTransform.validate(colorTransform.intValue(),
                            header.frame().componentCount(), header.frame().precision());
                }
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
        DecodedSamples decoded = decodeScans(width, height, header, scans);
        int[] samples = decoded.samples;
        JpegLsColorTransform.applyInverse(samples, colorTransform.intValue(),
                header.frame().precision());
        int nearLossless = 0;
        for (EncodedScan scan : scans) {
            nearLossless = Math.max(nearLossless, scan.header.nearLossless());
        }
        return new DecodedFrame(width, height, header.frame().precision(),
                header.frame().componentCount(), header.scan().interleaveMode(),
                nearLossless, samples, decoded.mappedComponents);
    }

    private static final class DecodedSamples {
        private final int[] samples;
        private final java.util.List<DecodedFrame.MappedComponent> mappedComponents;

        private DecodedSamples(int[] samples,
                java.util.List<DecodedFrame.MappedComponent> mappedComponents) {
            this.samples = samples;
            this.mappedComponents = mappedComponents;
        }
    }

    private static DecodedSamples decodeScans(int width, int height, JpegLsHeader header,
            java.util.List<EncodedScan> scans) throws IOException {
        if (scans.size() == 1 && scans.get(0).header.componentCount()
                == header.frame().componentCount()) {
            EncodedScan scan = scans.get(0);
            int[] samples = decodeEntropy(width, height, header.frame().componentCount(), scan.traits,
                    scan.header.interleaveMode(), scan.restartInterval, scan.intervals);
            return new DecodedSamples(samples,
                    mappedComponents(scan, samples, header.frame().componentCount(), header.frame()));
        }
        int components = header.frame().componentCount();
        int sampleCount = checkedSampleCount(width, height, components);
        int[] samples = new int[sampleCount];
        java.util.List<DecodedFrame.MappedComponent> mapped =
                new java.util.ArrayList<DecodedFrame.MappedComponent>();
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
            int[] componentSamples = decodeEntropy(width, height, 1, scan.traits,
                    JpegLsInterleaveMode.NONE, scan.restartInterval, scan.intervals);
            for (int pixel = 0; pixel < componentSamples.length; pixel++) {
                samples[pixel * components + component] = componentSamples[pixel];
            }
            mapped.addAll(mappedComponents(scan, componentSamples, 1, component, null));
            decoded[component] = true;
        }
        for (boolean value : decoded) {
            if (!value) throw new JpegLsException("missing JPEG-LS component scan");
        }
        return new DecodedSamples(samples, mapped);
    }

    private static java.util.List<DecodedFrame.MappedComponent> mappedComponents(EncodedScan scan,
            int[] samples, int componentCount, JpegLsFrameHeader frame) throws JpegLsException {
        java.util.List<DecodedFrame.MappedComponent> mapped =
                new java.util.ArrayList<DecodedFrame.MappedComponent>();
        for (int i = 0; i < scan.header.componentCount(); i++) {
            int tableId = scan.header.component(i).mappingTableSelector();
            if (tableId == 0) continue;
            int component = componentIndex(frame, scan.header.component(i).selector());
            mapped.add(mappedComponent(scan.mappingTables, tableId, component, samples,
                    componentCount, i));
        }
        return mapped;
    }

    private static java.util.List<DecodedFrame.MappedComponent> mappedComponents(EncodedScan scan,
            int[] samples, int componentCount, int component, JpegLsFrameHeader ignored)
            throws JpegLsException {
        java.util.List<DecodedFrame.MappedComponent> mapped =
                new java.util.ArrayList<DecodedFrame.MappedComponent>();
        int tableId = scan.header.component(0).mappingTableSelector();
        if (tableId != 0) {
            mapped.add(mappedComponent(scan.mappingTables, tableId, component, samples,
                    componentCount, 0));
        }
        return mapped;
    }

    private static DecodedFrame.MappedComponent mappedComponent(
            java.util.Map<Integer, JpegLsMappingTable> mappingTables, int tableId, int component,
            int[] samples, int componentCount, int sampleComponent) throws JpegLsException {
        JpegLsMappingTable table = mappingTables.get(tableId);
        if (table == null) throw new JpegLsException("missing JPEG-LS mapping table: " + tableId);
        long length = (long) samples.length / componentCount * table.entryWidth();
        if (length > Integer.MAX_VALUE) {
            throw new JpegLsException("JPEG-LS mapped output exceeds Java array limits");
        }
        byte[] bytes = new byte[(int) length];
        int offset = 0;
        for (int pixel = 0; pixel < samples.length / componentCount; pixel++) {
            byte[] entry = table.entry(samples[pixel * componentCount + sampleComponent]);
            System.arraycopy(entry, 0, bytes, offset, entry.length);
            offset += entry.length;
        }
        return new DecodedFrame.MappedComponent(component, tableId, table.entryWidth(), bytes);
    }

    private static int componentIndex(JpegLsFrameHeader frame, int selector)
            throws JpegLsException {
        for (int component = 0; component < frame.componentCount(); component++) {
            if (frame.component(component).identifier() == selector) return component;
        }
        throw new JpegLsException("unknown JPEG-LS component selector: " + selector);
    }

    private static JpegLsTraits traits(JpegLsPresetCodingParameters preset,
            JpegLsScanHeader scan) throws JpegLsException {
        return JpegLsTraits.create(preset.maximumSampleValue(), scan.nearLossless(),
                preset.resetValue(), preset.threshold1(), preset.threshold2(), preset.threshold3());
    }

    private static void finishMapping(JpegLsMappingTableParser parser,
            java.util.Map<Integer, JpegLsMappingTable> mappingTables) throws JpegLsException {
        if (parser.isActive()) {
            JpegLsMappingTable table = parser.finishSyntax();
            mappingTables.put(table.tableId(), table);
        }
    }

    private static void validateMappingTables(java.util.Map<Integer, JpegLsMappingTable> mappingTables,
            JpegLsScanHeader scan, int maximumSampleValue) throws JpegLsException {
        for (JpegLsMappingTable table : mappingTables.values()) {
            if (table.entryCount() != maximumSampleValue + 1L) {
                throw new JpegLsException("JPEG-LS mapping table entry count " + table.entryCount()
                        + " does not match " + ((long) maximumSampleValue + 1L));
            }
        }
        for (int i = 0; i < scan.componentCount(); i++) {
            int selector = scan.component(i).mappingTableSelector();
            if (selector != 0 && !mappingTables.containsKey(selector)) {
                throw new JpegLsException("missing JPEG-LS mapping table: " + selector);
            }
        }
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
        checkedSampleCount(width, height, components);
        if (restartInterval == 0) {
            if (intervals.size() != 1) throw new JpegLsException("unexpected JPEG-LS restart data");
            return new JpegLsScanCodec(width, height, components, traits, mode).decode(intervals.get(0));
        }
        long expectedIntervalsLong = ((long) height - 1L) / restartInterval + 1L;
        if (expectedIntervalsLong > Integer.MAX_VALUE) {
            throw new JpegLsException("JPEG-LS restart interval count exceeds Java limits");
        }
        int expectedIntervals = (int) expectedIntervalsLong;
        if (intervals.size() != expectedIntervals) {
            throw new JpegLsException("JPEG-LS restart marker count does not match DRI: expected "
                    + expectedIntervals + ", got " + intervals.size());
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

    private static int checkedSampleCount(int width, int height, int components)
            throws JpegLsException {
        long count = (long) width * height * components;
        if (count <= 0 || count > Integer.MAX_VALUE) {
            throw new JpegLsException("JPEG-LS decoded sample count exceeds Java array limits");
        }
        return (int) count;
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

    private static void validateMappingTablesForEncode(
            java.util.Map<Integer, JpegLsMappingTable> mappingTables,
            java.util.Map<Integer, Integer> selectors, int maximum, int components)
            throws JpegLsException {
        for (JpegLsMappingTable table : mappingTables.values()) {
            if (table.entryCount() != (long) maximum + 1L) {
                throw new JpegLsException("JPEG-LS mapping table entry count " + table.entryCount()
                        + " does not match " + ((long) maximum + 1L));
            }
        }
        for (java.util.Map.Entry<Integer, Integer> selector : selectors.entrySet()) {
            Integer component = selector.getKey();
            Integer table = selector.getValue();
            if (component == null || component < 1 || component > components) {
                throw new JpegLsException("invalid JPEG-LS component mapping selector: " + component);
            }
            if (table == null || !mappingTables.containsKey(table)) {
                throw new JpegLsException("missing JPEG-LS mapping table: " + table);
            }
        }
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
            JpegLsInterleaveMode interleaveMode,
            java.util.Map<Integer, Integer> componentMappingTableSelectors) {
        byte[] payload = new byte[1 + components * 2 + 3];
        payload[0] = (byte) components;
        for (int component = 0; component < components; component++) {
            payload[1 + component * 2] = (byte) (component + 1);
            Integer tableId = componentMappingTableSelectors.get(component + 1);
            payload[2 + component * 2] = (byte) (tableId == null ? 0 : tableId.intValue());
        }
        int offset = 1 + components * 2;
        payload[offset] = (byte) nearLossless;
        payload[offset + 1] = (byte) interleaveMode.value();
        payload[offset + 2] = 0;
        return payload;
    }

    private static byte[] singleComponentSosPayload(int selector, int nearLossless,
            java.util.Map<Integer, Integer> componentMappingTableSelectors) {
        Integer tableId = componentMappingTableSelectors.get(selector);
        return new byte[] {1, (byte) selector, (byte) (tableId == null ? 0 : tableId.intValue()),
                (byte) nearLossless, 0, 0};
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
