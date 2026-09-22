package io.github.cocosip.dcm4che.imageio.codecs.jpegls.internal;

import java.util.HashSet;
import java.util.Set;

final class JpegLsFrameHeader {
    static final class Component {
        private final int identifier;

        private Component(int identifier) {
            this.identifier = identifier;
        }

        int identifier() {
            return identifier;
        }
    }

    private final int precision;
    private final JpegLsDimensions dimensions;
    private final Component[] components;

    private JpegLsFrameHeader(int precision, int height, int width, Component[] components) {
        this.precision = precision;
        this.dimensions = JpegLsDimensions.fromFrame(height, width);
        this.components = components;
    }

    static JpegLsFrameHeader parse(byte[] payload) throws JpegLsException {
        if (payload.length < 6) {
            throw new JpegLsException("truncated JPEG-LS SOF55 segment");
        }

        int precision = unsigned(payload[0]);
        int height = unsignedShort(payload, 1);
        int width = unsignedShort(payload, 3);
        int componentCount = unsigned(payload[5]);
        if (precision < 2 || precision > 16) {
            throw new JpegLsException("invalid JPEG-LS sample precision: " + precision);
        }
        if (componentCount == 0) {
            throw new JpegLsException("JPEG-LS frame has no components");
        }
        if (payload.length != 6 + componentCount * 3) {
            throw new JpegLsException("invalid JPEG-LS SOF55 segment length");
        }

        Component[] components = new Component[componentCount];
        Set<Integer> identifiers = new HashSet<Integer>();
        int offset = 6;
        for (int i = 0; i < componentCount; i++) {
            int identifier = unsigned(payload[offset++]);
            int sampling = unsigned(payload[offset++]);
            int quantizationSelector = unsigned(payload[offset++]);
            if (!identifiers.add(identifier)) {
                throw new JpegLsException("duplicate JPEG-LS frame component identifier: " + identifier);
            }
            if (sampling != 0x11) {
                throw new JpegLsException("unsupported JPEG-LS sampling factors: " + sampling);
            }
            if (quantizationSelector != 0) {
                throw new JpegLsException(
                        "invalid JPEG-LS quantization table selector: " + quantizationSelector);
            }
            components[i] = new Component(identifier);
        }
        return new JpegLsFrameHeader(precision, height, width, components);
    }

    int precision() {
        return precision;
    }

    long height() {
        return dimensions.height();
    }

    long width() {
        return dimensions.width();
    }

    int componentCount() {
        return components.length;
    }

    Component component(int index) {
        return components[index];
    }

    boolean containsComponent(int identifier) {
        for (Component component : components) {
            if (component.identifier == identifier) {
                return true;
            }
        }
        return false;
    }

    void applyOversize(byte[] payload) throws JpegLsException {
        dimensions.applyOversize(payload);
    }

    void applyDnl(byte[] payload) throws JpegLsException {
        dimensions.applyDnl(payload);
    }

    private static int unsigned(byte value) {
        return value & 0xff;
    }

    private static int unsignedShort(byte[] values, int offset) {
        return unsigned(values[offset]) << 8 | unsigned(values[offset + 1]);
    }
}
