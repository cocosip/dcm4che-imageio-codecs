package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common;

import java.nio.charset.StandardCharsets;

public final class Jpeg2000CommentSegment {
    private final int registration;
    private final byte[] payload;

    private Jpeg2000CommentSegment(int registration, byte[] payload) {
        this.registration = registration;
        this.payload = Jpeg2000Payload.copy(payload);
    }

    public static Jpeg2000CommentSegment parse(Jpeg2000MarkerSegment segment) throws Jpeg2000Exception {
        Jpeg2000Payload.requireMarker(segment, Jpeg2000Marker.COM);
        byte[] payload = segment.payload();
        if (payload.length < 2) {
            throw new Jpeg2000Exception("JPEG 2000 COM payload is too short");
        }
        int registration = Jpeg2000Payload.unsignedShort(payload, 0, "COM registration");
        if (registration > 1) {
            throw new Jpeg2000Exception("JPEG 2000 COM registration value is invalid");
        }
        return new Jpeg2000CommentSegment(registration, payload);
    }

    public int registration() {
        return registration;
    }

    public String text() {
        return new String(payload, 2, payload.length - 2, StandardCharsets.ISO_8859_1);
    }

    public byte[] payload() {
        return Jpeg2000Payload.copy(payload);
    }
}
