package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common;

public final class Jpeg2000ComponentCodingStyleSegment {
    private final int componentIndex;
    private final byte[] payload;

    private Jpeg2000ComponentCodingStyleSegment(int componentIndex, byte[] payload) {
        this.componentIndex = componentIndex;
        this.payload = Jpeg2000Payload.copy(payload);
    }

    public static Jpeg2000ComponentCodingStyleSegment parse(
            Jpeg2000MarkerSegment segment,
            Jpeg2000SizeSegment size) throws Jpeg2000Exception {
        Jpeg2000Payload.requireMarker(segment, Jpeg2000Marker.COC);
        if (size == null) {
            throw new NullPointerException("size");
        }
        byte[] payload = segment.payload();
        int indexBytes = size.components().size() < 257 ? 1 : 2;
        Jpeg2000Payload.requireRange(payload, 0, indexBytes + 6, "COC");
        int component = indexBytes == 1
                ? payload[0] & 0xff
                : Jpeg2000Payload.unsignedShort(payload, 0, "COC component index");
        if (component >= size.components().size()) {
            throw new Jpeg2000Exception("JPEG 2000 COC component index is outside the SIZ component range");
        }
        int flags = payload[indexBytes] & 0xff;
        if ((flags & ~1) != 0) {
            throw new Jpeg2000Exception("JPEG 2000 COC coding-style flags are invalid");
        }
        Jpeg2000CodingStyleSegment.parseStyle(payload, indexBytes + 1, (flags & 1) != 0, "COC");
        return new Jpeg2000ComponentCodingStyleSegment(component, payload);
    }

    public int componentIndex() {
        return componentIndex;
    }

    public byte[] payload() {
        return Jpeg2000Payload.copy(payload);
    }
}
