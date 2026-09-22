package io.github.cocosip.dcm4che.imageio.codecs.jpegls.internal;

import java.util.HashSet;
import java.util.Set;

final class JpegLsScanHeader {
    static final class Component {
        private final int selector;
        private final int mappingTableSelector;

        private Component(int selector, int mappingTableSelector) {
            this.selector = selector;
            this.mappingTableSelector = mappingTableSelector;
        }

        int selector() {
            return selector;
        }

        int mappingTableSelector() {
            return mappingTableSelector;
        }
    }

    private final Component[] components;
    private final int nearLossless;
    private final JpegLsInterleaveMode interleaveMode;
    private final int pointTransform;

    private JpegLsScanHeader(Component[] components, int nearLossless,
            JpegLsInterleaveMode interleaveMode, int pointTransform) {
        this.components = components;
        this.nearLossless = nearLossless;
        this.interleaveMode = interleaveMode;
        this.pointTransform = pointTransform;
    }

    static JpegLsScanHeader parse(byte[] payload, JpegLsFrameHeader frame) throws JpegLsException {
        if (payload.length < 4) {
            throw new JpegLsException("truncated JPEG-LS SOS segment");
        }
        int componentCount = unsigned(payload[0]);
        if (componentCount == 0
                || componentCount != 1 && componentCount != frame.componentCount()) {
            throw new JpegLsException("invalid JPEG-LS scan component count: " + componentCount);
        }
        if (payload.length != 1 + componentCount * 2 + 3) {
            throw new JpegLsException("invalid JPEG-LS SOS segment length");
        }

        Component[] components = new Component[componentCount];
        Set<Integer> selectors = new HashSet<Integer>();
        int offset = 1;
        for (int i = 0; i < componentCount; i++) {
            int selector = unsigned(payload[offset++]);
            int mappingTableSelector = unsigned(payload[offset++]);
            if (!frame.containsComponent(selector)) {
                throw new JpegLsException("unknown JPEG-LS scan component selector: " + selector);
            }
            if (!selectors.add(selector)) {
                throw new JpegLsException("duplicate JPEG-LS scan component selector: " + selector);
            }
            components[i] = new Component(selector, mappingTableSelector);
        }

        int nearLossless = unsigned(payload[offset++]);
        JpegLsInterleaveMode interleaveMode = JpegLsInterleaveMode.fromValue(unsigned(payload[offset++]));
        int pointTransform = unsigned(payload[offset]);
        if (componentCount == 1 && interleaveMode != JpegLsInterleaveMode.NONE) {
            throw new JpegLsException("single-component JPEG-LS scan must use no interleave");
        }
        if (componentCount > 1 && interleaveMode == JpegLsInterleaveMode.NONE) {
            throw new JpegLsException("multi-component JPEG-LS scan must be interleaved");
        }
        if (pointTransform != 0) {
            throw new JpegLsException("unsupported JPEG-LS point transform: " + pointTransform);
        }
        return new JpegLsScanHeader(components, nearLossless, interleaveMode, pointTransform);
    }

    int componentCount() {
        return components.length;
    }

    Component component(int index) {
        return components[index];
    }

    int nearLossless() {
        return nearLossless;
    }

    JpegLsInterleaveMode interleaveMode() {
        return interleaveMode;
    }

    int pointTransform() {
        return pointTransform;
    }

    private static int unsigned(byte value) {
        return value & 0xff;
    }
}
