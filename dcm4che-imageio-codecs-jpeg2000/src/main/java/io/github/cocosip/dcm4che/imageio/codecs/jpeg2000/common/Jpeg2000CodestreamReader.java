package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common;

import java.io.IOException;

import javax.imageio.stream.ImageInputStream;

public final class Jpeg2000CodestreamReader {
    private final Jpeg2000Input input;
    private final Jpeg2000Limits limits;

    public Jpeg2000CodestreamReader(
            ImageInputStream stream,
            long frameLength,
            Jpeg2000Limits limits) throws IOException {
        this.input = new Jpeg2000Input(stream, frameLength, limits);
        this.limits = limits;
    }

    public Jpeg2000MarkerSegment readNext() throws IOException {
        long offset = input.position();
        int prefix = input.readUnsignedByte("marker prefix");
        if (prefix != 0xff) {
            throw new Jpeg2000Exception(
                    "JPEG 2000 marker prefix 0xFF was not found at offset " + offset);
        }

        int marker = input.readUnsignedByte("marker code");
        if (marker == 0 || marker == 0xff) {
            throw new Jpeg2000Exception(
                    "Invalid JPEG 2000 marker code " + Jpeg2000Marker.name(marker) + " at offset " + offset);
        }
        if (!Jpeg2000Marker.hasLength(marker)) {
            return new Jpeg2000MarkerSegment(marker, offset, new byte[0]);
        }

        int length = input.readUnsignedShort(Jpeg2000Marker.name(marker) + " marker length");
        if (length < 2) {
            throw new Jpeg2000Exception(
                    "JPEG 2000 " + Jpeg2000Marker.name(marker) + " marker at offset " + offset
                            + " has invalid length " + length);
        }
        int payloadLength = length - 2;
        try {
            limits.requireMarkerPayloadLength(payloadLength);
        } catch (Jpeg2000Exception error) {
            throw new Jpeg2000Exception(
                    "JPEG 2000 " + Jpeg2000Marker.name(marker) + " marker at offset " + offset
                            + " has invalid " + error.getMessage().substring("JPEG 2000 ".length()),
                    error);
        }
        if (payloadLength > input.remaining()) {
            throw new Jpeg2000Exception(
                    "JPEG 2000 " + Jpeg2000Marker.name(marker) + " marker at offset " + offset
                            + " declares " + payloadLength + " payload bytes beyond the frame boundary");
        }
        return new Jpeg2000MarkerSegment(
                marker,
                offset,
                input.readFully(payloadLength, Jpeg2000Marker.name(marker) + " marker payload"));
    }

    public byte[] readRaw(int length, String context) throws IOException {
        return input.readFully(length, context);
    }

    public boolean startsWith(byte[] signature) throws IOException {
        if (signature == null) {
            throw new NullPointerException("signature");
        }
        if (signature.length > input.remaining()) {
            return false;
        }
        byte[] actual = input.peek(signature.length, "codestream signature");
        for (int index = 0; index < signature.length; index++) {
            if (actual[index] != signature[index]) {
                return false;
            }
        }
        return true;
    }

    public long position() throws IOException {
        return input.position();
    }

    public long remaining() throws IOException {
        return input.remaining();
    }
}
