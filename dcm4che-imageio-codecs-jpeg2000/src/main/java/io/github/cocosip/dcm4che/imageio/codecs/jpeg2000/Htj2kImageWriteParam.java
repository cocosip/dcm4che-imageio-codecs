package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000;

import javax.imageio.ImageWriteParam;

import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000ProgressionOrder;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.htj2k.Htj2kFrameCodec;

/** Write controls for one syntax-bound HTJ2K frame. */
public final class Htj2kImageWriteParam extends ImageWriteParam {
    private final String transferSyntaxUid;
    private Jpeg2000ProgressionOrder progressionOrder = Jpeg2000ProgressionOrder.RPCL;
    private double targetRatio;
    private int numLayers = 1;

    public Htj2kImageWriteParam(String transferSyntaxUid) {
        if (!Htj2kFrameCodec.LOSSLESS_UID.equals(transferSyntaxUid)
                && !Htj2kFrameCodec.LOSSLESS_RPCL_UID.equals(transferSyntaxUid)
                && !Htj2kFrameCodec.LOSSY_UID.equals(transferSyntaxUid)) {
            throw new IllegalArgumentException("Unsupported HTJ2K transfer syntax");
        }
        this.transferSyntaxUid = transferSyntaxUid;
        canWriteCompressed = true;
        compressionTypes = new String[] {"HTJ2K"};
        compressionMode = MODE_EXPLICIT;
        setCompressionType("HTJ2K");
    }

    public Jpeg2000ProgressionOrder getProgressionOrder() {
        return progressionOrder;
    }

    public void setProgressionOrder(Jpeg2000ProgressionOrder progressionOrder) {
        if (progressionOrder == null) {
            throw new NullPointerException("progressionOrder");
        }
        this.progressionOrder = progressionOrder;
    }

    public double getTargetRatio() {
        return targetRatio;
    }

    public void setTargetRatio(double targetRatio) {
        if (!Double.isFinite(targetRatio) || (targetRatio != 0 && targetRatio <= 1)) {
            throw new IllegalArgumentException("HTJ2K target ratio must be zero or greater than one");
        }
        this.targetRatio = targetRatio;
    }

    public int getNumLayers() {
        return numLayers;
    }

    public void setNumLayers(int numLayers) {
        if (numLayers != 1) {
            throw new IllegalArgumentException("HTJ2K compatibility profile requires one layer");
        }
        this.numLayers = numLayers;
    }

    boolean matches(String uid) {
        return transferSyntaxUid.equals(uid);
    }
}
