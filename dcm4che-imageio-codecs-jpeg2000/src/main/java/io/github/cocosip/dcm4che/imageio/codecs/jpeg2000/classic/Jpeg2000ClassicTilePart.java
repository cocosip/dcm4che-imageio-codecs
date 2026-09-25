package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.classic;

import java.util.Arrays;

import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000StartOfTileSegment;

/** Immutable tile-part header and bounded SOD payload. */
public final class Jpeg2000ClassicTilePart {
    private final Jpeg2000StartOfTileSegment startOfTile;
    private final byte[] data;
    private final byte[] packedHeaders;

    Jpeg2000ClassicTilePart(Jpeg2000StartOfTileSegment startOfTile, byte[] data) {
        this(startOfTile, data, new byte[0]);
    }

    Jpeg2000ClassicTilePart(Jpeg2000StartOfTileSegment startOfTile,
            byte[] data, byte[] packedHeaders) {
        if (startOfTile == null) {
            throw new NullPointerException("startOfTile");
        }
        if (data == null) {
            throw new NullPointerException("data");
        }
        this.startOfTile = startOfTile;
        this.data = Arrays.copyOf(data, data.length);
        this.packedHeaders = Arrays.copyOf(packedHeaders, packedHeaders.length);
    }

    public Jpeg2000StartOfTileSegment startOfTile() {
        return startOfTile;
    }

    public byte[] data() {
        return Arrays.copyOf(data, data.length);
    }

    public byte[] packedHeaders() {
        return Arrays.copyOf(packedHeaders, packedHeaders.length);
    }
}
