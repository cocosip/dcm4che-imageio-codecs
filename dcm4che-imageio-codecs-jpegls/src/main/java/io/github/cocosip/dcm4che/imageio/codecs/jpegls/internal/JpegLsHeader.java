package io.github.cocosip.dcm4che.imageio.codecs.jpegls.internal;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

final class JpegLsHeader {
    private final JpegLsFrameHeader frame;
    private final JpegLsScanHeader scan;
    private final JpegLsPresetCodingParameters rawPresetCodingParameters;
    private final JpegLsPresetCodingParameters presetCodingParameters;
    private final Map<Integer, JpegLsMappingTable> mappingTables;
    private final long restartInterval;
    private final int colorTransform;

    JpegLsHeader(JpegLsFrameHeader frame, JpegLsScanHeader scan,
            JpegLsPresetCodingParameters presetCodingParameters,
            JpegLsPresetCodingParameters rawPresetCodingParameters,
            Map<Integer, JpegLsMappingTable> mappingTables, long restartInterval,
            int colorTransform) {
        this.frame = frame;
        this.scan = scan;
        this.presetCodingParameters = presetCodingParameters;
        this.rawPresetCodingParameters = rawPresetCodingParameters;
        this.mappingTables = Collections.unmodifiableMap(
                new HashMap<Integer, JpegLsMappingTable>(mappingTables));
        this.restartInterval = restartInterval;
        this.colorTransform = colorTransform;
    }

    JpegLsFrameHeader frame() {
        return frame;
    }

    JpegLsScanHeader scan() {
        return scan;
    }

    JpegLsPresetCodingParameters presetCodingParameters() {
        return presetCodingParameters;
    }

    JpegLsPresetCodingParameters rawPresetCodingParameters() {
        return rawPresetCodingParameters;
    }

    Map<Integer, JpegLsMappingTable> mappingTables() {
        return mappingTables;
    }

    JpegLsMappingTable mappingTable(int tableId) {
        return mappingTables.get(tableId);
    }

    long restartInterval() {
        return restartInterval;
    }

    int colorTransform() {
        return colorTransform;
    }
}
