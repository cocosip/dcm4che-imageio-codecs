package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common;

import java.io.IOException;

import javax.imageio.stream.ImageOutputStream;

public final class Jpeg2000CodestreamWriter {
    private final Jpeg2000Output output;
    private final Jpeg2000Limits limits;

    public Jpeg2000CodestreamWriter(ImageOutputStream stream, Jpeg2000Limits limits) throws IOException {
        this.output = new Jpeg2000Output(stream, limits);
        this.limits = limits;
    }

    public void writeStandalone(int marker) throws IOException {
        requireKnownMarker(marker);
        if (Jpeg2000Marker.hasLength(marker)) {
            throw new IllegalArgumentException(
                    "JPEG 2000 " + Jpeg2000Marker.name(marker) + " marker requires a segment payload");
        }
        writeMarkerPrefix(marker);
    }

    public void writeSegment(int marker, byte[] payload) throws IOException {
        requireKnownMarker(marker);
        if (payload == null) {
            throw new NullPointerException("payload");
        }
        if (!Jpeg2000Marker.hasLength(marker)) {
            throw new IllegalArgumentException(
                    "Standalone JPEG 2000 " + Jpeg2000Marker.name(marker) + " marker cannot have a payload");
        }
        limits.requireMarkerPayloadLength(payload.length);
        writeMarkerPrefix(marker);
        output.writeShort(payload.length + 2);
        output.write(payload);
    }

    public void writeRaw(byte[] payload) throws IOException {
        output.write(payload);
    }

    public long position() throws IOException {
        return output.position();
    }

    private void writeMarkerPrefix(int marker) throws IOException {
        output.writeByte(0xff);
        output.writeByte(marker);
    }

    private static void requireKnownMarker(int marker) {
        if (!Jpeg2000Marker.isKnown(marker)) {
            throw new IllegalArgumentException("Unknown JPEG 2000 marker " + Jpeg2000Marker.name(marker));
        }
    }
}
