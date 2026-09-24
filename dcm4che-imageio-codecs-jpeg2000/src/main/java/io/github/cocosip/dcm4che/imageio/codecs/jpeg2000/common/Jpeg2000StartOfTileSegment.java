package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common;

public final class Jpeg2000StartOfTileSegment {
    private final int tileIndex;
    private final long tilePartLength;
    private final int tilePartIndex;
    private final int tilePartCount;
    private final byte[] payload;

    private Jpeg2000StartOfTileSegment(
            int tileIndex,
            long tilePartLength,
            int tilePartIndex,
            int tilePartCount,
            byte[] payload) {
        this.tileIndex = tileIndex;
        this.tilePartLength = tilePartLength;
        this.tilePartIndex = tilePartIndex;
        this.tilePartCount = tilePartCount;
        this.payload = Jpeg2000Payload.copy(payload);
    }

    public static Jpeg2000StartOfTileSegment parse(
            Jpeg2000MarkerSegment segment,
            Jpeg2000SizeSegment size) throws Jpeg2000Exception {
        Jpeg2000Payload.requireMarker(segment, Jpeg2000Marker.SOT);
        byte[] payload = segment.payload();
        if (payload.length != 8) {
            throw new Jpeg2000Exception("JPEG 2000 SOT payload length must be 8");
        }
        int tile = Jpeg2000Payload.unsignedShort(payload, 0, "SOT tile index");
        if (tile >= size.tileCount()) {
            throw new Jpeg2000Exception("JPEG 2000 SOT tile index is outside the SIZ tile range");
        }
        long length = Jpeg2000Payload.unsignedInt(payload, 2, "SOT tile-part length");
        if (length != 0 && length < 14) {
            throw new Jpeg2000Exception("JPEG 2000 SOT tile-part length must be zero or at least 14");
        }
        int part = payload[6] & 0xff;
        int count = payload[7] & 0xff;
        if (count != 0 && part >= count) {
            throw new Jpeg2000Exception("JPEG 2000 SOT tile-part index is outside its declared count");
        }
        return new Jpeg2000StartOfTileSegment(tile, length, part, count, payload);
    }

    public int tileIndex() {
        return tileIndex;
    }

    public long tilePartLength() {
        return tilePartLength;
    }

    public int tilePartIndex() {
        return tilePartIndex;
    }

    public int tilePartCount() {
        return tilePartCount;
    }

    public byte[] payload() {
        return Jpeg2000Payload.copy(payload);
    }
}
