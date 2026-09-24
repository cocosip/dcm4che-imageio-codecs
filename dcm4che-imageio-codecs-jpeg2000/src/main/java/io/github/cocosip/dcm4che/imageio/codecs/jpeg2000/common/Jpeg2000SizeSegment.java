package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Jpeg2000SizeSegment {
    private final int capabilities;
    private final long referenceGridWidth;
    private final long referenceGridHeight;
    private final long imageOffsetX;
    private final long imageOffsetY;
    private final long tileWidth;
    private final long tileHeight;
    private final long tileOffsetX;
    private final long tileOffsetY;
    private final List<Component> components;
    private final int tileCount;
    private final byte[] payload;

    private Jpeg2000SizeSegment(
            int capabilities,
            long referenceGridWidth,
            long referenceGridHeight,
            long imageOffsetX,
            long imageOffsetY,
            long tileWidth,
            long tileHeight,
            long tileOffsetX,
            long tileOffsetY,
            List<Component> components,
            int tileCount,
            byte[] payload) {
        this.capabilities = capabilities;
        this.referenceGridWidth = referenceGridWidth;
        this.referenceGridHeight = referenceGridHeight;
        this.imageOffsetX = imageOffsetX;
        this.imageOffsetY = imageOffsetY;
        this.tileWidth = tileWidth;
        this.tileHeight = tileHeight;
        this.tileOffsetX = tileOffsetX;
        this.tileOffsetY = tileOffsetY;
        this.components = Collections.unmodifiableList(new ArrayList<Component>(components));
        this.tileCount = tileCount;
        this.payload = Jpeg2000Payload.copy(payload);
    }

    public static Jpeg2000SizeSegment parse(
            Jpeg2000MarkerSegment segment,
            Jpeg2000Limits limits) throws Jpeg2000Exception {
        Jpeg2000Payload.requireMarker(segment, Jpeg2000Marker.SIZ);
        if (limits == null) {
            throw new NullPointerException("limits");
        }
        byte[] payload = segment.payload();
        if (payload.length < 39) {
            throw new Jpeg2000Exception("JPEG 2000 SIZ payload is too short");
        }
        int componentCount = Jpeg2000Payload.unsignedShort(payload, 34, "SIZ component count");
        if (componentCount == 0 || payload.length != 36 + componentCount * 3) {
            throw new Jpeg2000Exception("JPEG 2000 SIZ component payload length is invalid");
        }

        long width = Jpeg2000Payload.unsignedInt(payload, 2, "SIZ Xsiz");
        long height = Jpeg2000Payload.unsignedInt(payload, 6, "SIZ Ysiz");
        long offsetX = Jpeg2000Payload.unsignedInt(payload, 10, "SIZ XOsiz");
        long offsetY = Jpeg2000Payload.unsignedInt(payload, 14, "SIZ YOsiz");
        long tileWidth = Jpeg2000Payload.unsignedInt(payload, 18, "SIZ XTsiz");
        long tileHeight = Jpeg2000Payload.unsignedInt(payload, 22, "SIZ YTsiz");
        long tileOffsetX = Jpeg2000Payload.unsignedInt(payload, 26, "SIZ XTOsiz");
        long tileOffsetY = Jpeg2000Payload.unsignedInt(payload, 30, "SIZ YTOsiz");
        validateGeometry(width, height, offsetX, offsetY, tileWidth, tileHeight, tileOffsetX, tileOffsetY);

        List<Component> components = new ArrayList<Component>(componentCount);
        int componentOffset = 36;
        for (int index = 0; index < componentCount; index++) {
            int ssiz = payload[componentOffset] & 0xff;
            int precision = (ssiz & 0x7f) + 1;
            int separationX = payload[componentOffset + 1] & 0xff;
            int separationY = payload[componentOffset + 2] & 0xff;
            if (precision > 38) {
                throw new Jpeg2000Exception(
                        "JPEG 2000 SIZ component " + index + " precision " + precision + " exceeds Part 1");
            }
            if (separationX != 1 || separationY != 1) {
                throw new Jpeg2000Exception(
                        "JPEG 2000 SIZ component subsampling is unsupported for component " + index);
            }
            components.add(new Component(index, precision, (ssiz & 0x80) != 0, separationX, separationY));
            componentOffset += 3;
        }

        long tilesX = ceilingDivide(width - tileOffsetX, tileWidth);
        long tilesY = ceilingDivide(height - tileOffsetY, tileHeight);
        long tileCountLong = checkedMultiply(tilesX, tilesY, "SIZ tile count");
        int tileCount = limits.requireTileCount(tileCountLong);
        limits.checkedSampleBufferBytes(width - offsetX, height - offsetY, componentCount, 1);
        return new Jpeg2000SizeSegment(
                Jpeg2000Payload.unsignedShort(payload, 0, "SIZ Rsiz"),
                width,
                height,
                offsetX,
                offsetY,
                tileWidth,
                tileHeight,
                tileOffsetX,
                tileOffsetY,
                components,
                tileCount,
                payload);
    }

    private static void validateGeometry(
            long width,
            long height,
            long offsetX,
            long offsetY,
            long tileWidth,
            long tileHeight,
            long tileOffsetX,
            long tileOffsetY) throws Jpeg2000Exception {
        if (width <= offsetX || height <= offsetY) {
            throw new Jpeg2000Exception("JPEG 2000 SIZ image bounds are invalid");
        }
        if (tileWidth == 0 || tileHeight == 0) {
            throw new Jpeg2000Exception("JPEG 2000 SIZ tile dimensions must be non-zero");
        }
        if (tileOffsetX > offsetX || tileOffsetY > offsetY
                || tileWidth <= offsetX - tileOffsetX
                || tileHeight <= offsetY - tileOffsetY) {
            throw new Jpeg2000Exception("JPEG 2000 SIZ tile origin does not cover the image origin");
        }
    }

    private static long ceilingDivide(long value, long divisor) {
        return 1 + (value - 1) / divisor;
    }

    private static long checkedMultiply(long left, long right, String context) throws Jpeg2000Exception {
        if (left > Long.MAX_VALUE / right) {
            throw new Jpeg2000Exception("JPEG 2000 " + context + " overflow");
        }
        return left * right;
    }

    public int capabilities() {
        return capabilities;
    }

    public long referenceGridWidth() {
        return referenceGridWidth;
    }

    public long referenceGridHeight() {
        return referenceGridHeight;
    }

    public long imageOffsetX() {
        return imageOffsetX;
    }

    public long imageOffsetY() {
        return imageOffsetY;
    }

    public long tileWidth() {
        return tileWidth;
    }

    public long tileHeight() {
        return tileHeight;
    }

    public long tileOffsetX() {
        return tileOffsetX;
    }

    public long tileOffsetY() {
        return tileOffsetY;
    }

    public List<Component> components() {
        return components;
    }

    public int tileCount() {
        return tileCount;
    }

    public byte[] payload() {
        return Jpeg2000Payload.copy(payload);
    }

    public static final class Component {
        private final int index;
        private final int precision;
        private final boolean signed;
        private final int separationX;
        private final int separationY;

        private Component(int index, int precision, boolean signed, int separationX, int separationY) {
            this.index = index;
            this.precision = precision;
            this.signed = signed;
            this.separationX = separationX;
            this.separationY = separationY;
        }

        public int index() {
            return index;
        }

        public int precision() {
            return precision;
        }

        public boolean signed() {
            return signed;
        }

        public int separationX() {
            return separationX;
        }

        public int separationY() {
            return separationY;
        }
    }
}
