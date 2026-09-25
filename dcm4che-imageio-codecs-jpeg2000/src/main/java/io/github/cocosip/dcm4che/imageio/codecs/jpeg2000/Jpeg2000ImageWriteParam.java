package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000;

import java.util.Arrays;

import javax.imageio.IIOException;
import javax.imageio.ImageWriteParam;

import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000ProgressionOrder;

/** Syntax-specific JPEG 2000 transform and quality-layer policy. */
public final class Jpeg2000ImageWriteParam extends ImageWriteParam {
    private static final double[] DEFAULT_LEVELS = {1280, 640, 320, 160, 80, 40, 20, 10, 5};

    private final boolean losslessSyntax;
    private boolean irreversible;
    private double rate = 20;
    private double[] rateLevels = DEFAULT_LEVELS.clone();
    private double targetRatio;
    private int numLayers = 1;
    private boolean includeFinalLosslessLayer;
    private boolean encodeSignedAsUnsigned;
    private Jpeg2000ProgressionOrder progressionOrder = Jpeg2000ProgressionOrder.LRCP;
    private boolean explicitRate;
    private boolean explicitQuality;

    public Jpeg2000ImageWriteParam(boolean losslessSyntax) {
        this.losslessSyntax = losslessSyntax;
        irreversible = !losslessSyntax;
        includeFinalLosslessLayer = losslessSyntax;
        canWriteCompressed = true;
        compressionTypes = new String[] {"JPEG2000"};
        compressionMode = MODE_EXPLICIT;
        setCompressionType("JPEG2000");
    }

    @Override
    public void setCompressionQuality(float quality) {
        super.setCompressionQuality(quality);
        explicitQuality = true;
    }

    public boolean isIrreversible() {
        return irreversible;
    }

    public void setIrreversible(boolean irreversible) {
        if (losslessSyntax && irreversible) {
            throw new IllegalArgumentException("JPEG 2000 Lossless requires reversible 5/3 coding");
        }
        this.irreversible = irreversible;
    }

    public double getRate() {
        return rate;
    }

    public void setRate(double rate) {
        if (!Double.isFinite(rate) || rate < 0) {
            throw new IllegalArgumentException("JPEG 2000 rate must be finite and non-negative");
        }
        this.rate = rate;
        explicitRate = true;
    }

    public double[] getRateLevels() {
        return rateLevels.clone();
    }

    public void setRateLevels(double... levels) {
        if (levels == null) {
            throw new NullPointerException("levels");
        }
        if (levels.length > 65534) {
            throw new IllegalArgumentException("JPEG 2000 rate level count exceeds 65534");
        }
        double previous = Double.POSITIVE_INFINITY;
        for (double level : levels) {
            if (!Double.isFinite(level) || level <= 0 || level >= previous) {
                throw new IllegalArgumentException("JPEG 2000 rate levels must be positive and descending");
            }
            previous = level;
        }
        rateLevels = levels.clone();
        explicitRate = true;
    }

    public double getTargetRatio() {
        return targetRatio;
    }

    public void setTargetRatio(double targetRatio) {
        if (!Double.isFinite(targetRatio) || (targetRatio != 0 && targetRatio <= 1)) {
            throw new IllegalArgumentException("JPEG 2000 target ratio must be zero or greater than one");
        }
        this.targetRatio = targetRatio;
    }

    public int getNumLayers() {
        return numLayers;
    }

    public void setNumLayers(int numLayers) {
        if (numLayers < 1 || numLayers > 65535) {
            throw new IllegalArgumentException("JPEG 2000 layer count is outside 1..65535");
        }
        this.numLayers = numLayers;
    }

    public boolean isIncludeFinalLosslessLayer() {
        return includeFinalLosslessLayer;
    }

    public void setIncludeFinalLosslessLayer(boolean includeFinalLosslessLayer) {
        this.includeFinalLosslessLayer = includeFinalLosslessLayer;
    }

    public boolean isEncodeSignedAsUnsigned() {
        return encodeSignedAsUnsigned;
    }

    public void setEncodeSignedAsUnsigned(boolean encodeSignedAsUnsigned) {
        this.encodeSignedAsUnsigned = encodeSignedAsUnsigned;
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

    public double[] resolveLayerRatios(int bitsStored, int bitsAllocated) throws IIOException {
        if (getCompressionMode() == MODE_DISABLED) {
            throw new IIOException("JPEG 2000 compression cannot be disabled");
        }
        if (losslessSyntax != includeFinalLosslessLayer) {
            throw new IIOException("JPEG 2000 final lossless layer conflicts with transfer syntax");
        }
        if (bitsStored < 1 || bitsAllocated < bitsStored) {
            throw new IIOException("JPEG 2000 sample precision is invalid");
        }
        if (targetRatio > 1) {
            int count = numLayers + (includeFinalLosslessLayer ? 1 : 0);
            if (count > 65535) {
                throw new IIOException("JPEG 2000 quality layer count exceeds 65535");
            }
            double[] ratios = new double[count];
            for (int i = 0; i < numLayers; i++) {
                ratios[i] = Math.scalb(targetRatio, numLayers - i - 1);
                if (!Double.isFinite(ratios[i])) {
                    throw new IIOException("JPEG 2000 derived layer ratio overflows");
                }
            }
            return ratios;
        }
        if (explicitQuality && !explicitRate) {
            double ratio = 1.0 / Math.max(0.001, getCompressionQuality());
            return includeFinalLosslessLayer ? new double[] {ratio, 0} : new double[] {ratio};
        }
        double[] ratios = new double[rateLevels.length + 2];
        int count = 0;
        for (double level : rateLevels) {
            if (level > rate) {
                ratios[count++] = level;
            }
        }
        ratios[count++] = rate * bitsStored / bitsAllocated;
        if (includeFinalLosslessLayer && ratios[count - 1] > 0) {
            ratios[count++] = 0;
        }
        if (count > 65535) {
            throw new IIOException("JPEG 2000 quality layer count exceeds 65535");
        }
        return Arrays.copyOf(ratios, count);
    }
}
