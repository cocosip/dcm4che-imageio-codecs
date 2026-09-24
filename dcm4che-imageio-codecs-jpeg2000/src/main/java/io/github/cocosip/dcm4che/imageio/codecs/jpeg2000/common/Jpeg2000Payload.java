package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common;

import java.util.Arrays;

final class Jpeg2000Payload {
    private Jpeg2000Payload() {
    }

    static void requireMarker(Jpeg2000MarkerSegment segment, int marker) throws Jpeg2000Exception {
        if (segment == null) {
            throw new NullPointerException("segment");
        }
        if (segment.marker() != marker) {
            throw new Jpeg2000Exception(
                    "JPEG 2000 " + Jpeg2000Marker.name(marker) + " marker expected at offset "
                            + segment.offset() + ", found " + Jpeg2000Marker.name(segment.marker()));
        }
    }

    static int unsignedShort(byte[] bytes, int offset, String context) throws Jpeg2000Exception {
        requireRange(bytes, offset, 2, context);
        return ((bytes[offset] & 0xff) << 8) | (bytes[offset + 1] & 0xff);
    }

    static long unsignedInt(byte[] bytes, int offset, String context) throws Jpeg2000Exception {
        requireRange(bytes, offset, 4, context);
        return ((long) (bytes[offset] & 0xff) << 24)
                | ((long) (bytes[offset + 1] & 0xff) << 16)
                | ((long) (bytes[offset + 2] & 0xff) << 8)
                | (bytes[offset + 3] & 0xffL);
    }

    static void requireRange(byte[] bytes, int offset, int length, String context)
            throws Jpeg2000Exception {
        if (bytes == null) {
            throw new NullPointerException("bytes");
        }
        if (offset < 0 || length < 0 || offset > bytes.length - length) {
            throw new Jpeg2000Exception("JPEG 2000 " + context + " payload is truncated");
        }
    }

    static byte[] copy(byte[] bytes) {
        return Arrays.copyOf(bytes, bytes.length);
    }
}
