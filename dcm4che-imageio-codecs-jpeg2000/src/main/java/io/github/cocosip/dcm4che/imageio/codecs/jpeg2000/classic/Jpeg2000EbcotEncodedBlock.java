package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.classic;

import java.util.Collections;
import java.util.List;

/** Immutable Tier-1 code-block payload and pass metadata. */
public final class Jpeg2000EbcotEncodedBlock {
    private final int width;
    private final int height;
    private final int orientation;
    private final int codeBlockStyle;
    private final int maxBitPlane;
    private final byte[] data;
    private final List<Jpeg2000EbcotPass> passes;

    Jpeg2000EbcotEncodedBlock(
            int width,
            int height,
            int orientation,
            int codeBlockStyle,
            int maxBitPlane,
            byte[] data,
            List<Jpeg2000EbcotPass> passes) {
        this.width = width;
        this.height = height;
        this.orientation = orientation;
        this.codeBlockStyle = codeBlockStyle;
        this.maxBitPlane = maxBitPlane;
        this.data = data.clone();
        this.passes = Collections.unmodifiableList(new java.util.ArrayList<Jpeg2000EbcotPass>(passes));
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int orientation() {
        return orientation;
    }

    public int codeBlockStyle() {
        return codeBlockStyle;
    }

    public int maxBitPlane() {
        return maxBitPlane;
    }

    public byte[] data() {
        return data.clone();
    }

    public List<Jpeg2000EbcotPass> passes() {
        return passes;
    }
}
