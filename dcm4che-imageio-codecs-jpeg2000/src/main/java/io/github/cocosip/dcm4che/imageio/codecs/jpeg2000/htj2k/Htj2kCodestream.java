package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.htj2k;

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

    Htj2kCodestream(Jpeg2000SizeSegment size, Jpeg2000ProgressionOrder progression,
            boolean reversible, boolean multipleComponentTransform, int tilePartCount,
            long logicalLength) {
        this.size = size;
        this.progression = progression;
        this.reversible = reversible;
        this.multipleComponentTransform = multipleComponentTransform;
        this.tilePartCount = tilePartCount;
        this.logicalLength = logicalLength;
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
}
