package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.htj2k;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000ProgressionOrder;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000SizeSegment;

/** Validated outer structure; entropy and packet decoding are separate stages. */
public final class Htj2kCodestream {
    private final Jpeg2000SizeSegment size;
    private final Jpeg2000ProgressionOrder progression;
    private final boolean reversible;
    private final boolean multipleComponentTransform;
    private final int tilePartCount;
    private final long logicalLength;
    private final byte[] codingPayload;
    private final byte[] quantizationPayload;
    private final List<TilePart> tileParts;

    Htj2kCodestream(Jpeg2000SizeSegment size, Jpeg2000ProgressionOrder progression,
            boolean reversible, boolean multipleComponentTransform, int tilePartCount,
            long logicalLength, byte[] codingPayload, byte[] quantizationPayload,
            List<TilePart> tileParts) {
        this.size = size;
        this.progression = progression;
        this.reversible = reversible;
        this.multipleComponentTransform = multipleComponentTransform;
        this.tilePartCount = tilePartCount;
        this.logicalLength = logicalLength;
        this.codingPayload = codingPayload.clone();
        this.quantizationPayload = quantizationPayload.clone();
        this.tileParts = Collections.unmodifiableList(new ArrayList<TilePart>(tileParts));
    }

    public Jpeg2000SizeSegment size() {
        return size;
    }

    public Jpeg2000ProgressionOrder progression() {
        return progression;
    }

    public boolean reversible() {
        return reversible;
    }

    public boolean multipleComponentTransform() {
        return multipleComponentTransform;
    }

    public int tilePartCount() {
        return tilePartCount;
    }

    public long logicalLength() {
        return logicalLength;
    }

    byte[] codingPayload() {
        return codingPayload.clone();
    }

    byte[] quantizationPayload() {
        return quantizationPayload.clone();
    }

    List<TilePart> tileParts() {
        return tileParts;
    }

    static final class TilePart {
        final int tileIndex;
        final int partIndex;
        final int dataOffset;
        final int dataLength;

        TilePart(int tileIndex, int partIndex, int dataOffset, int dataLength) {
            this.tileIndex = tileIndex;
            this.partIndex = partIndex;
            this.dataOffset = dataOffset;
            this.dataLength = dataLength;
        }
    }
}
