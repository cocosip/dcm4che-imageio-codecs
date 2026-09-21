package io.github.cocosip.dcm4che.imageio.codecs.jpeg.internal;

import java.util.Arrays;

/** Immutable description of one ISO JPEG progressive scan. */
public final class JpegScanScript {
    private final int[] components;
    private final int spectralStart;
    private final int spectralEnd;
    private final int successiveHigh;
    private final int successiveLow;

    public JpegScanScript(int[] components, int spectralStart, int spectralEnd,
            int successiveHigh, int successiveLow) {
        if (components == null || components.length == 0 || components.length > 4) {
            throw new IllegalArgumentException("JPEG scan must select one to four components");
        }
        this.components = Arrays.copyOf(components, components.length);
        this.spectralStart = spectralStart;
        this.spectralEnd = spectralEnd;
        this.successiveHigh = successiveHigh;
        this.successiveLow = successiveLow;
        for (int i = 0; i < this.components.length; i++) {
            if (this.components[i] < 0 || this.components[i] > 3) {
                throw new IllegalArgumentException("JPEG scan component index is out of range");
            }
            for (int j = 0; j < i; j++) {
                if (this.components[i] == this.components[j]) {
                    throw new IllegalArgumentException("JPEG scan components must be unique");
                }
            }
        }
        if (spectralStart < 0 || spectralStart > 63 || spectralEnd < 0
                || spectralEnd > 63 || spectralStart > spectralEnd) {
            throw new IllegalArgumentException("invalid JPEG spectral selection");
        }
        if (successiveHigh < 0 || successiveHigh > 13 || successiveLow < 0
                || successiveLow > 13) {
            throw new IllegalArgumentException("invalid JPEG successive approximation");
        }
        if (spectralStart == 0 && spectralEnd != 0) {
            throw new IllegalArgumentException("DC scan must use Ss=Se=0");
        }
        if (spectralStart != 0 && components.length != 1) {
            throw new IllegalArgumentException("AC scan must select exactly one component");
        }
        if (successiveHigh != 0 && successiveHigh != successiveLow + 1) {
            throw new IllegalArgumentException("JPEG refinement scan must lower Ah by one");
        }
    }

    public static JpegScanScript dcFirst(int... components) {
        return new JpegScanScript(components, 0, 0, 0, 0);
    }

    public static JpegScanScript dcRefinement(int al, int... components) {
        return new JpegScanScript(components, 0, 0, al + 1, al);
    }

    public static JpegScanScript acFirst(int component, int ss, int se) {
        return new JpegScanScript(new int[] {component}, ss, se, 0, 0);
    }

    public static JpegScanScript acRefinement(int component, int ss, int se, int al) {
        return new JpegScanScript(new int[] {component}, ss, se, al + 1, al);
    }

    public int[] components() {
        return Arrays.copyOf(components, components.length);
    }

    public int spectralStart() {
        return spectralStart;
    }

    public int spectralEnd() {
        return spectralEnd;
    }

    public int successiveHigh() {
        return successiveHigh;
    }

    public int successiveLow() {
        return successiveLow;
    }
}
