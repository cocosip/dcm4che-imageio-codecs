package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.classic;

/** One JPEG 2000 Tier-1 coding pass boundary. */
public final class Jpeg2000EbcotPass {
    public enum Type {
        SIGNIFICANCE_PROPAGATION,
        MAGNITUDE_REFINEMENT,
        CLEANUP
    }

    private final Type type;
    private final int bitPlane;
    private final int byteLength;

    Jpeg2000EbcotPass(Type type, int bitPlane, int byteLength) {
        this.type = type;
        this.bitPlane = bitPlane;
        this.byteLength = byteLength;
    }

    public Type type() {
        return type;
    }

    public int bitPlane() {
        return bitPlane;
    }

    public int byteLength() {
        return byteLength;
    }
}
