package io.github.cocosip.dcm4che.imageio.codecs.jpeg.internal;

/**
 * JPEG sampling factors for the supported three-component sequential paths.
 * The first component is treated as the full-resolution component and the
 * remaining components are reduced relative to the largest sampling grid.
 */
public enum JpegSampling {
    SF444(1, 1, new int[] {1, 1, 1}, new int[] {1, 1, 1}),
    SF422(2, 1, new int[] {2, 1, 1}, new int[] {1, 1, 1}),
    SF420(2, 2, new int[] {2, 1, 1}, new int[] {2, 1, 1});

    private final int maxHorizontal;
    private final int maxVertical;
    private final int[] horizontal;
    private final int[] vertical;

    JpegSampling(int maxHorizontal, int maxVertical, int[] horizontal, int[] vertical) {
        this.maxHorizontal = maxHorizontal;
        this.maxVertical = maxVertical;
        this.horizontal = horizontal;
        this.vertical = vertical;
    }

    int maxHorizontal() {
        return maxHorizontal;
    }

    int maxVertical() {
        return maxVertical;
    }

    int horizontal(int component) {
        return horizontal[component];
    }

    int vertical(int component) {
        return vertical[component];
    }

    int componentWidth(int width, int component) {
        return (width * horizontal(component) + maxHorizontal - 1) / maxHorizontal;
    }

    int componentHeight(int height, int component) {
        return (height * vertical(component) + maxVertical - 1) / maxVertical;
    }

    int factorByte(int component) {
        return (horizontal(component) << 4) | vertical(component);
    }

    static JpegSampling fromFactors(int[] horizontal, int[] vertical) throws JpegException {
        if (horizontal.length != 3 || vertical.length != 3) {
            throw new JpegException("JPEG sampling requires three component factors");
        }
        for (JpegSampling sampling : values()) {
            boolean match = true;
            for (int component = 0; component < 3; component++) {
                match &= sampling.horizontal(component) == horizontal[component]
                        && sampling.vertical(component) == vertical[component];
            }
            if (match) {
                return sampling;
            }
        }
        throw new JpegException("unsupported JPEG sampling factors");
    }
}
