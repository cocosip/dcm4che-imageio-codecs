package io.github.cocosip.dcm4che.imageio.codecs.jpegls.internal;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.stream.ImageInputStream;

final class JpegLsHeaderReader {
    private final JpegLsMarkerReader markerReader;

    JpegLsHeaderReader(ImageInputStream input) {
        this.markerReader = new JpegLsMarkerReader(input);
    }

    JpegLsHeader read() throws IOException {
        JpegLsMarker marker = markerReader.next();
        if (marker.code() != JpegLsMarker.SOI) {
            throw new JpegLsException("JPEG-LS codestream must start with SOI");
        }

        JpegLsFrameHeader frame = null;
        byte[] pendingOversize = null;
        JpegLsPresetCodingParameters rawPreset = null;
        Map<Integer, JpegLsMappingTable> mappingTables =
                new HashMap<Integer, JpegLsMappingTable>();
        JpegLsMappingTableParser mappingParser = new JpegLsMappingTableParser();
        long restartInterval = 0;
        Integer colorTransform = null;
        while (true) {
            marker = markerReader.next();
            boolean mappingContinuation = marker.code() == JpegLsMarker.LSE
                    && marker.payload().length > 0
                    && (marker.payload()[0] & 0xff) == 3;
            if (mappingParser.isActive() && !mappingContinuation) {
                JpegLsMappingTable table = mappingParser.finishSyntax();
                mappingTables.put(table.tableId(), table);
            }
            if (marker.isApplicationSegment() || marker.code() == JpegLsMarker.COM) {
                if (marker.code() == 0xe8) {
                    colorTransform = parseColorTransform(marker.payload(), colorTransform);
                }
                continue;
            }
            switch (marker.code()) {
                case JpegLsMarker.SOI:
                    throw new JpegLsException("duplicate JPEG-LS SOI marker");
                case JpegLsMarker.SOF55:
                    if (frame != null) {
                        throw new JpegLsException("duplicate JPEG-LS SOF55 marker");
                    }
                    frame = JpegLsFrameHeader.parse(marker.payload());
                    if (pendingOversize != null) {
                        frame.applyOversize(pendingOversize);
                    }
                    break;
                case JpegLsMarker.SOS:
                    if (frame == null) {
                        throw new JpegLsException("JPEG-LS SOS marker precedes SOF55");
                    }
                    if (frame.width() == 0) {
                        throw new JpegLsException(
                                "unresolved zero JPEG-LS width requires an oversize dimension segment");
                    }
                    JpegLsScanHeader scan = JpegLsScanHeader.parse(marker.payload(), frame);
                    JpegLsPresetCodingParameters preset = rawPreset == null
                            ? JpegLsPresetCodingParameters.defaults(frame.precision(), scan.nearLossless())
                            : rawPreset.resolve(frame.precision(), scan.nearLossless());
                    validateMappingTables(mappingTables, scan, preset.maximumSampleValue());
                    int transform = colorTransform == null ? 0 : colorTransform.intValue();
                    JpegLsColorTransform.validate(transform, frame.componentCount(), frame.precision());
                    return new JpegLsHeader(frame, scan, preset, mappingTables,
                            restartInterval, transform);
                case JpegLsMarker.EOI:
                    throw new JpegLsException("JPEG-LS EOI marker precedes SOS");
                case JpegLsMarker.LSE:
                    byte[] payload = marker.payload();
                    if (payload.length == 0) {
                        throw new JpegLsException("empty JPEG-LS LSE segment");
                    }
                    int type = payload[0] & 0xff;
                    if (type == 1) {
                        rawPreset = JpegLsPresetCodingParameters.parse(payload);
                    } else if (type == 2 || type == 3) {
                        mappingParser.accept(payload);
                    } else if (type == 4) {
                        if (pendingOversize != null) {
                            throw new JpegLsException("duplicate JPEG-LS oversize dimension segment");
                        }
                        pendingOversize = payload;
                        if (frame != null) {
                            frame.applyOversize(payload);
                        }
                    } else {
                        throw new JpegLsException("unsupported JPEG-LS LSE type: " + type);
                    }
                    break;
                case JpegLsMarker.DNL:
                    throw new JpegLsException("JPEG-LS DNL marker is not implemented yet");
                case JpegLsMarker.DRI:
                    restartInterval = parseRestartInterval(marker.payload());
                    break;
                default:
                    if (marker.isRestartMarker()) {
                        throw new JpegLsException("JPEG-LS restart marker appears before scan data");
                    }
                    throw new JpegLsException(String.format(
                            "unsupported JPEG-LS marker: 0xFF%02X", marker.code()));
            }
        }
    }

    private static long parseRestartInterval(byte[] payload) throws JpegLsException {
        if (payload.length < 2 || payload.length > 4) {
            throw new JpegLsException("JPEG-LS DRI payload must contain 2, 3, or 4 bytes");
        }
        long value = 0;
        for (byte item : payload) value = value << 8 | item & 0xffL;
        return value;
    }

    private static Integer parseColorTransform(byte[] payload, Integer previous)
            throws JpegLsException {
        if (payload.length != 5 || payload[0] != 'm' || payload[1] != 'r'
                || payload[2] != 'f' || payload[3] != 'x') {
            return previous;
        }
        int transform = payload[4] & 0xff;
        if (transform == 4 || transform == 5) {
            throw new JpegLsException("recognized but unsupported JPEG-LS mrfx transform: "
                    + transform);
        }
        if (transform > 5) {
            throw new JpegLsException("invalid JPEG-LS mrfx transform: " + transform);
        }
        if (previous != null && previous.intValue() != transform) {
            throw new JpegLsException("conflicting JPEG-LS mrfx transform declarations");
        }
        return Integer.valueOf(transform);
    }

    private static void validateMappingTables(Map<Integer, JpegLsMappingTable> mappingTables,
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
}
