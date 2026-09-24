package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common;

public final class Jpeg2000ComponentQuantizationSegment {
    private final int componentIndex;
    private final Jpeg2000QuantizationSegment quantization;
    private final byte[] payload;

    private Jpeg2000ComponentQuantizationSegment(
            int componentIndex,
            Jpeg2000QuantizationSegment quantization,
            byte[] payload) {
        this.componentIndex = componentIndex;
        this.quantization = quantization;
        this.payload = Jpeg2000Payload.copy(payload);
    }

    public static Jpeg2000ComponentQuantizationSegment parse(
            Jpeg2000MarkerSegment segment,
            Jpeg2000SizeSegment size,
            Jpeg2000CodingStyleSegment codingStyle) throws Jpeg2000Exception {
        Jpeg2000Payload.requireMarker(segment, Jpeg2000Marker.QCC);
        if (size == null) {
            throw new NullPointerException("size");
        }
        byte[] payload = segment.payload();
        int indexBytes = size.components().size() < 257 ? 1 : 2;
        Jpeg2000Payload.requireRange(payload, 0, indexBytes + 1, "QCC");
        int component = indexBytes == 1
                ? payload[0] & 0xff
                : Jpeg2000Payload.unsignedShort(payload, 0, "QCC component index");
        if (component >= size.components().size()) {
            throw new Jpeg2000Exception("JPEG 2000 QCC component index is outside the SIZ component range");
        }
        byte[] quantizationPayload = new byte[payload.length - indexBytes];
        System.arraycopy(payload, indexBytes, quantizationPayload, 0, quantizationPayload.length);
        return new Jpeg2000ComponentQuantizationSegment(
                component,
                Jpeg2000QuantizationSegment.parsePayload(quantizationPayload, codingStyle, "QCC"),
                payload);
    }

    public int componentIndex() {
        return componentIndex;
    }

    public Jpeg2000QuantizationSegment quantization() {
        return quantization;
    }

    public byte[] payload() {
        return Jpeg2000Payload.copy(payload);
    }
}
