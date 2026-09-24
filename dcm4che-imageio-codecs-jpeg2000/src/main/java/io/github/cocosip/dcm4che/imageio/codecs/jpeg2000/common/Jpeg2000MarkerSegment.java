package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common;

import java.util.Arrays;

public final class Jpeg2000MarkerSegment {
    private final int marker;
    private final long offset;
    private final byte[] payload;

    public Jpeg2000MarkerSegment(int marker, long offset, byte[] payload) {
        if (marker < 0 || marker > 0xff) {
            throw new IllegalArgumentException("JPEG 2000 marker code must fit in one byte");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("JPEG 2000 marker offset cannot be negative");
        }
        if (payload == null) {
            throw new NullPointerException("payload");
        }
        this.marker = marker;
        this.offset = offset;
        this.payload = Arrays.copyOf(payload, payload.length);
    }

    public int marker() {
        return marker;
    }

    public long offset() {
        return offset;
    }

    public boolean hasPayload() {
        return payload.length != 0;
    }

    public byte[] payload() {
        return Arrays.copyOf(payload, payload.length);
    }
}
