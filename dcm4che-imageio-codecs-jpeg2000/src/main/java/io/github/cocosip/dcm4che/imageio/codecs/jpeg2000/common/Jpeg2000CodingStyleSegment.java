package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common;

public final class Jpeg2000CodingStyleSegment {
    private final int flags;
    private final Jpeg2000ProgressionOrder progressionOrder;
    private final int qualityLayers;
    private final boolean multipleComponentTransform;
    private final Style style;
    private final byte[] payload;

    private Jpeg2000CodingStyleSegment(
            int flags,
            Jpeg2000ProgressionOrder progressionOrder,
            int qualityLayers,
            boolean multipleComponentTransform,
            Style style,
            byte[] payload) {
        this.flags = flags;
        this.progressionOrder = progressionOrder;
        this.qualityLayers = qualityLayers;
        this.multipleComponentTransform = multipleComponentTransform;
        this.style = style;
        this.payload = Jpeg2000Payload.copy(payload);
    }

    public static Jpeg2000CodingStyleSegment parse(
            Jpeg2000MarkerSegment segment,
            Jpeg2000Limits limits) throws Jpeg2000Exception {
        Jpeg2000Payload.requireMarker(segment, Jpeg2000Marker.COD);
        byte[] payload = segment.payload();
        if (payload.length < 10) {
            throw new Jpeg2000Exception("JPEG 2000 COD payload is too short");
        }
        int flags = payload[0] & 0xff;
        if ((flags & ~0x07) != 0) {
            throw new Jpeg2000Exception("JPEG 2000 COD coding-style flags are invalid");
        }
        Jpeg2000ProgressionOrder order = Jpeg2000ProgressionOrder.fromCode(payload[1] & 0xff);
        int layers = Jpeg2000Payload.unsignedShort(payload, 2, "COD layer count");
        limits.requireQualityLayerCount(layers);
        int mct = payload[4] & 0xff;
        if (mct > 1) {
            throw new Jpeg2000Exception("JPEG 2000 COD MCT value is invalid");
        }
        Style style = parseStyle(payload, 5, (flags & 1) != 0, "COD");
        return new Jpeg2000CodingStyleSegment(flags, order, layers, mct != 0, style, payload);
    }

    static Style parseStyle(byte[] payload, int offset, boolean hasPrecinctSizes, String context)
            throws Jpeg2000Exception {
        Jpeg2000Payload.requireRange(payload, offset, 5, context);
        int levels = payload[offset] & 0xff;
        if (levels > 32) {
            throw new Jpeg2000Exception("JPEG 2000 " + context + " decomposition level count is invalid");
        }
        int widthExponent = payload[offset + 1] & 0xff;
        int heightExponent = payload[offset + 2] & 0xff;
        if (widthExponent > 8 || heightExponent > 8 || widthExponent + heightExponent > 8) {
            throw new Jpeg2000Exception("JPEG 2000 " + context + " code-block dimensions are invalid");
        }
        int codeBlockStyle = payload[offset + 3] & 0xff;
        if ((codeBlockStyle & ~0x3f) != 0) {
            throw new Jpeg2000Exception("JPEG 2000 " + context + " code-block style is invalid for classic coding");
        }
        int transformation = payload[offset + 4] & 0xff;
        if (transformation > 1) {
            throw new Jpeg2000Exception("JPEG 2000 " + context + " transformation is invalid");
        }
        int precinctCount = hasPrecinctSizes ? levels + 1 : 0;
        int expectedLength = offset + 5 + precinctCount;
        if (payload.length != expectedLength) {
            throw new Jpeg2000Exception("JPEG 2000 " + context + " precinct payload length is invalid");
        }
        byte[] precincts = new byte[precinctCount];
        System.arraycopy(payload, offset + 5, precincts, 0, precinctCount);
        return new Style(
                levels,
                1 << (widthExponent + 2),
                1 << (heightExponent + 2),
                codeBlockStyle,
                transformation,
                precincts);
    }

    public boolean hasPrecinctSizes() {
        return (flags & 1) != 0;
    }

    public boolean hasStartOfPacketMarkers() {
        return (flags & 2) != 0;
    }

    public boolean hasEndOfPacketHeaderMarkers() {
        return (flags & 4) != 0;
    }

    public Jpeg2000ProgressionOrder progressionOrder() {
        return progressionOrder;
    }

    public int qualityLayers() {
        return qualityLayers;
    }

    public boolean multipleComponentTransform() {
        return multipleComponentTransform;
    }

    public int decompositionLevels() {
        return style.decompositionLevels;
    }

    public int codeBlockWidth() {
        return style.codeBlockWidth;
    }

    public int codeBlockHeight() {
        return style.codeBlockHeight;
    }

    public int codeBlockStyle() {
        return style.codeBlockStyle;
    }

    public int transformation() {
        return style.transformation;
    }

    public byte[] precinctSizes() {
        return Jpeg2000Payload.copy(style.precinctSizes);
    }

    public int precinctWidth(int resolution) {
        return precinctDimension(resolution, false);
    }

    public int precinctHeight(int resolution) {
        return precinctDimension(resolution, true);
    }

    private int precinctDimension(int resolution, boolean height) {
        if (resolution < 0 || resolution > style.decompositionLevels) {
            throw new IllegalArgumentException("JPEG 2000 resolution index is outside COD range");
        }
        if (style.precinctSizes.length == 0) {
            return 1 << 15;
        }
        int packed = style.precinctSizes[resolution] & 0xff;
        int exponent = height ? packed >>> 4 : packed & 0x0f;
        return 1 << exponent;
    }

    public byte[] payload() {
        return Jpeg2000Payload.copy(payload);
    }

    static final class Style {
        private final int decompositionLevels;
        private final int codeBlockWidth;
        private final int codeBlockHeight;
        private final int codeBlockStyle;
        private final int transformation;
        private final byte[] precinctSizes;

        private Style(
                int decompositionLevels,
                int codeBlockWidth,
                int codeBlockHeight,
                int codeBlockStyle,
                int transformation,
                byte[] precinctSizes) {
            this.decompositionLevels = decompositionLevels;
            this.codeBlockWidth = codeBlockWidth;
            this.codeBlockHeight = codeBlockHeight;
            this.codeBlockStyle = codeBlockStyle;
            this.transformation = transformation;
            this.precinctSizes = Jpeg2000Payload.copy(precinctSizes);
        }
    }
}
